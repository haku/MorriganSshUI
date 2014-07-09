package com.vaguehope.morrigan.sshui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;

import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.sshui.term.SshTerminal;

public class MnCommand implements Command, SessionAware {

	private static final AtomicInteger COUNTER = new AtomicInteger(0);

	private final MnContext mnContext;
	private final ScheduledExecutorService schEx;

	private InputStream in;
	private OutputStream out;
	private ExitCallback callback;

	private volatile MnScreen term = null;

	public MnCommand (final MnContext mnContext, final ScheduledExecutorService schEx) {
		this.mnContext = mnContext;
		this.schEx = schEx;
	}

	@Override
	public void setInputStream (final InputStream in) {
		this.in = in;
	}

	@Override
	public void setOutputStream (final OutputStream out) {
		this.out = out;
	}

	@Override
	public void setErrorStream (final OutputStream err) {/* Unused. */}

	@Override
	public void setExitCallback (final ExitCallback callback) {
		this.callback = callback;
	}

	@Override
	public void setSession (final ServerSession session) {/* Unused. */}

	@Override
	public void start (final Environment env) throws IOException {
		final Terminal terminal = new SshTerminal(this.in, this.out, Charset.forName("UTF8"), env);
		this.term = new MnScreen("mnScreen" + COUNTER.getAndIncrement(), this.mnContext, env, terminal, this.callback);
		this.term.schedule(this.schEx);
	}

	@Override
	public void destroy () {
		if (this.term == null) throw new IllegalStateException();
		this.term.stopAndJoin();
	}

}
