package com.vaguehope.morrigan.sshui.term;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.sshui.util.Quietly;

public abstract class SshScreen implements Runnable {

	private static final long PRINT_CYCLE_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);
	private static final long SHUTDOWN_TIMEOUT_MILLIS = TimeUnit.SECONDS.toNanos(5L);

	private static final Logger LOG = LoggerFactory.getLogger(SshScreen.class);

	private final String name;
	private final Environment env;
	private final Terminal terminal;
	private final ExitCallback callback;

	private final Screen screen;
	private final TextGraphics textGraphics;

	private volatile boolean alive = true;
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);
	private boolean inited = false;
	private long threadId;
	private long lastPrint = 0L;

	public SshScreen (final String name, final Environment env, final Terminal terminal, final ExitCallback callback) throws IOException {
		this.name = name;
		this.env = env;
		this.terminal = terminal;
		this.callback = callback;
		this.screen = new TerminalScreen(this.terminal);
		this.textGraphics = this.screen.newTextGraphics();
	}

	public void stopAndJoin (final String reason) {
		scheduleQuit(reason);
		if (this.threadId != Thread.currentThread().getId()) {
			Quietly.await(this.shutdownLatch, SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
		}
	}

	protected void scheduleQuit (final String reason) {
		if (this.alive) LOG.info("Killing session {}: {} ...", this.name, reason);
		this.alive = false;
	}

	protected Environment getEnv () {
		return this.env;
	}

	private void init () throws IOException {
		if (!this.inited) {
			this.inited = true; // Only try once.
			this.threadId = Thread.currentThread().getId();
			initScreen(this.screen);
			this.screen.startScreen();
			LOG.info("Session created: {}", this.name);
		}
	}

	@Override
	public void run () {
		try {
			init();
			while (this.alive) {
				tick();
				Quietly.sleep(10); // FIXME I wish terminal.readInput() used blocking-with-timeout IO.
			}
		}
		catch (final Throwable t) { // NOSONAR Report all errors and clean up session.
			LOG.error("Session error.", t);
			scheduleQuit("session error");
		}
		finally {
			try {
				this.screen.stopScreen();
				this.terminal.flush(); // Workaround as stopScreen() does not trigger flush().
			}
			catch (final IOException e) {
				LOG.warn("Failed to shutdown session cleanly.", e);
			}
			this.callback.onExit(0, "baibai!");
			LOG.info("Session destroyed: {}", this.name);
			this.shutdownLatch.countDown();
		}
	}

	private void tick () throws IOException, InterruptedException {
		final long now = System.nanoTime();
		if (readInput() || now - this.lastPrint > PRINT_CYCLE_NANOS) {
			printScreen();
			this.lastPrint = now;
		}
	}

	private boolean readInput () throws IOException, InterruptedException {
		boolean changed = false;
		KeyStroke k;
		while ((k = this.terminal.pollInput()) != null) { // Non blocking.
			changed = onInput(k) || changed;
		}
		return changed;
	}

	protected void printScreen () throws IOException {
		this.screen.doResizeIfNecessary();

		this.screen.clear();
		writeScreen(this.screen, this.textGraphics);
		this.screen.refresh();
	}

	protected void writeScreen () {
		writeScreen(this.screen, this.textGraphics);
	}

	/**
	 * Return true if screen needs redrawing.
	 */
	protected abstract boolean onInput (KeyStroke k) throws IOException, InterruptedException;

	protected abstract void initScreen (Screen scr);

	protected abstract void writeScreen (Screen scr, TextGraphics tg);

}
