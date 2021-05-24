package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.config.Config;
import com.vaguehope.morrigan.model.media.MediaFactoryTracker;
import com.vaguehope.morrigan.player.PlayerReaderTracker;
import com.vaguehope.morrigan.sshui.ssh.MnPasswordAuthenticator;
import com.vaguehope.morrigan.sshui.ssh.UserPublickeyAuthenticator;
import com.vaguehope.morrigan.tasks.AsyncTasksRegisterTracker;
import com.vaguehope.morrigan.util.DaemonThreadFactory;

public class Activator implements BundleActivator {

	private static final int SSHD_PORT = 14022; // TODO make config.
	// can be DSA/RSA/EC (http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator)
	private static final String HOSTKEY_NAME = "hostkey.ser";
	private static final long IDLE_TIMEOUT = 24 * 60 * 60 * 1000L; // A day.
	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

	private SshServer sshd;
	private MnCommandFactory mnCommandFactory;

	private PlayerReaderTracker playerReaderTracker;
	private MediaFactoryTracker mediaFactoryTracker;
	private AsyncTasksRegisterTracker asyncTasksRegisterTracker;
	private ExecutorService unreliableEs;

	@Override
	public void start (final BundleContext context) throws IOException, GeneralSecurityException {
		if (this.sshd != null) throw new IllegalStateException("Already started.");

		final File hostKey = new File(Config.getConfigDir(), HOSTKEY_NAME);
		LOG.info("Host key: {}", hostKey.getAbsolutePath());

		this.playerReaderTracker = new PlayerReaderTracker(context);
		this.mediaFactoryTracker = new MediaFactoryTracker(context);
		this.asyncTasksRegisterTracker = new AsyncTasksRegisterTracker(context);

		this.unreliableEs = new ThreadPoolExecutor(0, 1,
				1L, TimeUnit.MINUTES,
				new LinkedBlockingQueue<Runnable>(1),
				new DaemonThreadFactory("sshbg"),
				new ThreadPoolExecutor.DiscardOldestPolicy());

		final MnContext mnContext = new MnContext(
				this.playerReaderTracker, this.mediaFactoryTracker, this.asyncTasksRegisterTracker,
				new UserPrefs(), this.unreliableEs);
		this.mnCommandFactory = new MnCommandFactory(mnContext);

		this.sshd = SshServer.setUpDefaultServer();
		this.sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey.toPath()));
		this.sshd.setShellFactory(this.mnCommandFactory);
		this.sshd.setPasswordAuthenticator(new MnPasswordAuthenticator());
		this.sshd.setPublickeyAuthenticator(new UserPublickeyAuthenticator());
		this.sshd.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT, String.valueOf(IDLE_TIMEOUT));

		IOException bindFail = null;
		for (int i = 0; i < 10; i++) {
			try {
				this.sshd.setPort(SSHD_PORT + i);
				this.sshd.start();
				bindFail = null;
				break;
			}
			catch (final BindException e) {
				LOG.warn("Failed to bind to port {} ({}), trying a higher port...", this.sshd.getPort(), e.toString());
				bindFail = e;
			}
		}
		if (bindFail != null) {
			LOG.error("Abandonded search for port to bind to.");
			throw bindFail;
		}

		LOG.info("sshUI ready on port {}.", Integer.valueOf(this.sshd.getPort()));
	}

	@Override
	public void stop (final BundleContext context) throws InterruptedException {
		if (this.sshd != null) {
			try {
				this.sshd.stop();
			} catch (IOException e) {
				throw new IllegalStateException("Failed to stop SSHd.", e);
			}
			this.sshd = null;
		}

		if (this.mnCommandFactory != null) {
			this.mnCommandFactory.shutdown();
			this.mnCommandFactory = null;
		}

		this.asyncTasksRegisterTracker.dispose();
		this.mediaFactoryTracker.dispose();
		this.playerReaderTracker.dispose();

		if (this.unreliableEs != null) {
			this.unreliableEs.shutdownNow();
			this.unreliableEs = null;
		}

		LOG.info("sshUI stopped.");
	}

}
