package com.vaguehope.morrigan.sshui.ssh;

import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

public class FakePasswordAuthenticator implements PasswordAuthenticator {

	public FakePasswordAuthenticator () {}

	@Override
	public boolean authenticate (final String username, final String password, final ServerSession session) {
		return username != null; // TODO implement this.
	}

}
