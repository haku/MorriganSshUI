package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import org.apache.sshd.SshServer;
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

public class Activator implements BundleActivator {

	private static final int SSHD_PORT = 14022; // TODO make config.
	// can be DSA/RSA/EC (http://docs.oracle.com/javase/6/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator)
	private static final String HOSTKEY_NAME = "hostkey.ser";
	private static final String HOSTKEY_TYPE = "RSA";
	private static final int HOSTKEY_LENGTH = 1024;
	private static final long IDLE_TIMEOUT = 24 * 60 * 60 * 1000L; // A day.
	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

	private SshServer sshd;
	private MnCommandFactory mnCommandFactory;

	private PlayerReaderTracker playerReaderTracker;
	private MediaFactoryTracker mediaFactoryTracker;
	private AsyncTasksRegisterTracker asyncTasksRegisterTracker;

	@Override
	public void start (final BundleContext context) throws IOException, GeneralSecurityException {
		if (this.sshd != null) throw new IllegalStateException("Already started.");

		final File hostKey = new File(new File(Config.getConfigDir()), HOSTKEY_NAME);
		LOG.info("Host key: {}", hostKey.getAbsolutePath());

		this.playerReaderTracker = new PlayerReaderTracker(context);
		this.mediaFactoryTracker = new MediaFactoryTracker(context);
		this.asyncTasksRegisterTracker = new AsyncTasksRegisterTracker(context);

		this.mnCommandFactory = new MnCommandFactory(new MnContext(this.playerReaderTracker, this.mediaFactoryTracker, this.asyncTasksRegisterTracker, new UserPrefs()));

		this.sshd = SshServer.setUpDefaultServer();
		this.sshd.setPort(SSHD_PORT);
		this.sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey.getAbsolutePath(), HOSTKEY_TYPE, HOSTKEY_LENGTH));
		this.sshd.setShellFactory(this.mnCommandFactory);
		this.sshd.setPasswordAuthenticator(new MnPasswordAuthenticator());
		this.sshd.setPublickeyAuthenticator(new UserPublickeyAuthenticator());
		this.sshd.getProperties().put(ServerFactoryManager.IDLE_TIMEOUT, String.valueOf(IDLE_TIMEOUT));
		this.sshd.start();

		LOG.info("sshUI ready on port {}.", Integer.valueOf(this.sshd.getPort()));
	}

	@Override
	public void stop (final BundleContext context) throws InterruptedException {
		if (this.sshd != null) {
			this.sshd.stop();
			this.sshd = null;
		}

		if (this.mnCommandFactory != null) {
			this.mnCommandFactory.shutdown();
			this.mnCommandFactory = null;
		}

		this.asyncTasksRegisterTracker.dispose();
		this.mediaFactoryTracker.dispose();
		this.playerReaderTracker.dispose();

		LOG.info("sshUI stopped.");
	}

}
