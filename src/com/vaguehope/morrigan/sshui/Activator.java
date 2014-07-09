package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.IOException;

import org.apache.sshd.SshServer;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.sshui.ssh.TestPasswordAuthenticator;

public class Activator implements BundleActivator {

	private static final int SSHD_PORT = 14022; // TODO make config.
	private static final String HOSTKEY_NAME = "hostkey.ser";
	private static final long IDLE_TIMEOUT = 24 * 60 * 60 * 1000L; // A day.
	private static final Logger LOG = LoggerFactory.getLogger(Activator.class);

	private SshServer sshd;
	private ConsoleCommandFactory consoleCommandFactory;

	@Override
	public void start (final BundleContext context) throws IOException {
		if (this.sshd != null) throw new IllegalStateException("Already started.");

		this.sshd = SshServer.setUpDefaultServer();
		this.sshd.setPort(SSHD_PORT);

		final File hostKey = new File(HOSTKEY_NAME).getAbsoluteFile();
		LOG.info("Host key: {}", hostKey.getAbsolutePath());
		this.sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey.getAbsolutePath()));

		this.sshd.setPasswordAuthenticator(new TestPasswordAuthenticator());

		this.consoleCommandFactory = new ConsoleCommandFactory();
		this.sshd.setShellFactory(this.consoleCommandFactory);

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

		if (this.consoleCommandFactory != null) {
			this.consoleCommandFactory.shutdown();
			this.consoleCommandFactory = null;
		}

		LOG.info("sshUI stopped.");
	}

}
