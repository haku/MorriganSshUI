package com.vaguehope.morrigan.sshui.ssh;

import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import com.vaguehope.morrigan.server.ServerConfig;

public class MnPasswordAuthenticator implements PasswordAuthenticator {

	private final String currentUserName;
	private final ServerConfig config;

	public MnPasswordAuthenticator () {
		this.currentUserName = System.getProperty("user.name");
		this.config = new ServerConfig();
	}

	@Override
	public boolean authenticate (final String username, final String password, final ServerSession session) {
		return this.currentUserName.equals(username) && this.config.verifyAuth(password);
	}

}
