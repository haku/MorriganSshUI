package com.vaguehope.morrigan.sshui.term;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.Signal;
import org.apache.sshd.server.SignalListener;

import com.googlecode.lanterna.input.SshInputMapping;
import com.googlecode.lanterna.terminal.TerminalSize;
import com.googlecode.lanterna.terminal.text.UnixTerminal;
import com.googlecode.lanterna.terminal.text.UnixTerminalSizeQuerier;

public class SshTerminal extends UnixTerminal implements SignalListener {

	public SshTerminal (final InputStream terminalInput, final OutputStream terminalOutput, final Charset terminalCharset, final Environment env) {
		super(terminalInput, terminalOutput, terminalCharset, new SshTerminalSizeQuerier(env));
		addInputProfile(new SshInputMapping());
		env.addSignalListener(this, Signal.WINCH);
	}

	@Override
	public void signal (final Signal signal) {
		switch (signal) {
			case WINCH:
				notifyResized(getTerminalSize());
				break;
			default:
		}
	}

	public void notifyResized (final TerminalSize size) {
		onResized(size.getColumns(), size.getRows());
	}

	private static class SshTerminalSizeQuerier implements UnixTerminalSizeQuerier {

		private final Environment env;

		public SshTerminalSizeQuerier (final Environment env) {
			this.env = env;
		}

		@Override
		public TerminalSize queryTerminalSize () {
			final String colsStr = this.env.getEnv().get(Environment.ENV_COLUMNS);
			final int cols = colsStr != null ? Integer.parseInt(colsStr) : 80;
			final String linesStr = this.env.getEnv().get(Environment.ENV_LINES);
			final int rows = linesStr != null ? Integer.parseInt(linesStr) : 22;
			return new TerminalSize(cols, rows);
		}

	}

}
