package com.vaguehope.morrigan.sshui.ssh;

import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

public class TestPasswordAuthenticator implements PasswordAuthenticator {

	public TestPasswordAuthenticator () {}

	@Override
	public boolean authenticate (final String username, final String password, final ServerSession session) {
		return username != null && username.equals(password); // FIXME dodge test auth.
	}

}