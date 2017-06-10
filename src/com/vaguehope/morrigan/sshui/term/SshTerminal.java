package com.vaguehope.morrigan.sshui.term;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.Signal;
import org.apache.sshd.server.SignalListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.terminal.ansi.UnixLikeTerminal;
import com.vaguehope.morrigan.sshui.ssh.SshInputMapping;

public class SshTerminal extends UnixLikeTerminal implements SignalListener {

	private static final Logger LOG = LoggerFactory.getLogger(SshTerminal.class);

	private final Environment env;

	public SshTerminal (final InputStream terminalInput, final OutputStream terminalOutput, final Charset terminalCharset, final Environment env) throws IOException {
		super(terminalInput, terminalOutput, terminalCharset, CtrlCBehaviour.TRAP);
		this.env = env;
		getInputDecoder().addProfile(new SshInputMapping());
		env.addSignalListener(this, Signal.WINCH);
	}

	@Override
	public void signal (final Signal signal) {
		switch (signal) {
			case WINCH:
				notifyResized();
				break;
			default:
		}
	}

	public void notifyResized () {
		try {
			final TerminalSize size = getTerminalSize();
			onResized(size.getColumns(), size.getRows());
		}
		catch (final IOException e) {
			LOG.warn("Failed to read terminal size after being notified of a resize.", e);
		}
	}

	@Override
	public TerminalSize findTerminalSize() throws IOException {
		final String colsStr = this.env.getEnv().get(Environment.ENV_COLUMNS);
		final int cols = colsStr != null ? Integer.parseInt(colsStr) : 80;
		final String linesStr = this.env.getEnv().get(Environment.ENV_LINES);
		final int rows = linesStr != null ? Integer.parseInt(linesStr) : 22;
		return new TerminalSize(cols, rows);
	}

	@Override
	protected void canonicalMode (final boolean enable) throws IOException {
		// Unused.
	}

	@Override
	protected void keyStrokeSignalsEnabled (final boolean enabled) throws IOException {
		// Unused.
	}

	@Override
	protected void keyEchoEnabled (final boolean enabled) throws IOException {
		// Unused.
	}

	@Override
	protected void saveTerminalSettings () throws IOException {
		// Unused.
	}

	@Override
	protected void restoreTerminalSettings () throws IOException {
		// Unused.
	}

	@Override
	protected void registerTerminalResizeListener (final Runnable onResize) throws IOException {
		// Unused.
	}

}
