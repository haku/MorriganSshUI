package com.vaguehope.morrigan.sshui.term;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TerminalFacade;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.sshui.util.Quietly;

public abstract class SshScreen implements Runnable {

	private static final long PRINT_CYCLE_NANOS = TimeUnit.MILLISECONDS.toNanos(500L);
	private static final long WIPE_CYCLE_NANOS = TimeUnit.SECONDS.toNanos(5L);
	private static final long SHUTDOWN_TIMEOUT_MILLIS = TimeUnit.SECONDS.toNanos(5L);

	private static final Logger LOG = LoggerFactory.getLogger(SshScreen.class);

	private final String name;
	private final Environment env;
	private final Terminal terminal;
	private final ExitCallback callback;

	private final Screen screen;
	private final ScreenWriter screenWriter;

	private volatile boolean alive = true;
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);
	private boolean inited = false;
	private long lastPrint = 0L;
	private long lastWipe = 0L;

	public SshScreen (final String name, final Environment env, final Terminal terminal, final ExitCallback callback) {
		this.name = name;
		this.env = env;
		this.terminal = terminal;
		this.callback = callback;
		this.screen = TerminalFacade.createScreen(this.terminal);
		this.screenWriter = new ScreenWriter(this.screen);
	}

	public void stopAndJoin (final String reason) {
		scheduleQuit(reason);
		Quietly.await(this.shutdownLatch, SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
	}

	protected void scheduleQuit (final String reason) {
		if (this.alive) LOG.info("Killing session {}: {} ...", this.name, reason);
		this.alive = false;
	}

	protected Environment getEnv () {
		return this.env;
	}

	private void init () {
		if (!this.inited) {
			this.inited = true; // Only try once.
			this.screen.startScreen();
			initScreen(this.screen);
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
		catch (final Throwable t) {
			LOG.error("Session error.", t);
			scheduleQuit("session error");
		}
		finally {
			this.screen.stopScreen();
			this.terminal.flush(); // Workaround as stopScreen() does not trigger flush().
			this.callback.onExit(0, "baibai!");
			LOG.info("Session destroyed: {}", this.name);
			this.shutdownLatch.countDown();
		}
	}

	private void tick () {
		final long now = System.nanoTime();
		if (readInput() || now - this.lastPrint > PRINT_CYCLE_NANOS) {

			// FIXME this is a hack for unicode characters not clearing.
			boolean completeRefresh = false;
			if (now - this.lastWipe > WIPE_CYCLE_NANOS) {
				completeRefresh = true;
				this.lastWipe = now;
			}

			printScreen(completeRefresh);
			this.lastPrint = now;
		}
	}

	private boolean readInput () {
		boolean changed = false;
		Key k;
		while ((k = this.terminal.readInput()) != null) {
			changed = onInput(k) || changed;
		}
		return changed;
	}

	protected void printScreen (final boolean completeRefresh) {
		if (this.screen.resizePending()) {
			this.screenWriter.fillScreen(' ');
			this.screen.refresh();
		}
		else if (completeRefresh) {
			this.screen.completeRefresh();
		}

		this.screen.clear();
		writeScreen(this.screen, this.screenWriter);
		this.screen.refresh();
	}

	/**
	 * Return true if screen needs redrawing.
	 */
	protected abstract boolean onInput (Key k);

	protected abstract void initScreen (Screen scr);

	protected abstract void writeScreen (Screen scr, ScreenWriter w);

}
