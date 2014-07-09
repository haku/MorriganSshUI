package com.vaguehope.morrigan.sshui;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.sshui.term.SshScreen;

public class DesuScreen extends SshScreen {

	private final AtomicInteger inputCounter = new AtomicInteger();

	public DesuScreen (final String name, final Environment env, final Terminal terminal, final ExitCallback callback) {
		super(name, env, terminal, callback);
	}

	@Override
	protected boolean readInput (final Key k) {
		if (k.getKind() == Kind.NormalKey) {
			if (k.getCharacter() == 'q') {
				scheduleQuit();
				return false; // We are quitting.  Do not try and update UI.
			}
			this.inputCounter.incrementAndGet();
			return true;
		}
		return false;
	}

	@Override
	protected void writeScreen (final Screen scr, final ScreenWriter w) {
		w.drawString(0, 0, "" + (System.currentTimeMillis() / 1000L)); // NOSONAR not a magic number.
		w.drawString(1, 2, "Hello desu~", ScreenCharacterStyle.Bold);
		w.drawString(1, 3, "size: " + scr.getTerminalSize()); // NOSONAR not a magic number.
		w.drawString(1, 4, "env: " + getEnv().getEnv()); // NOSONAR not a magic number.
		w.drawString(1, 5, "debug: " + this.inputCounter.get()); // NOSONAR not a magic number.
	}

}
