package com.vaguehope.morrigan.sshui;

import java.io.IOException;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.DefaultWindowManager;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.sshui.Face.FaceNavigation;
import com.vaguehope.morrigan.sshui.term.SshScreen;
import com.vaguehope.morrigan.util.ErrorHelper;

public class MnScreen extends SshScreen implements FaceNavigation {

	private static final Logger LOG = LoggerFactory.getLogger(MnScreen.class);

	private final Deque<Face> faces = new LinkedList<Face>();
	private final MnContext mnContext;
	private WindowBasedTextGUI gui;

	public MnScreen (final String name, final MnContext mnContext, final Environment env, final Terminal terminal, final ExitCallback callback) throws IOException {
		super(name, env, terminal, callback);
		this.mnContext = mnContext;
	}

	@Override
	protected void initScreen (final Screen scr) {
		scr.setCursorPosition(null);
		this.gui = new MultiWindowTextGUI(scr,  new DefaultWindowManager(), new SelfBackground(scr));
		this.gui.setTheme(MnTheme.makeTheme());
		startFace(new HomeFace(this, this.mnContext));
	}

	private class SelfBackground extends AbstractComponent<SelfBackground> {

		private final Screen scr;

		public SelfBackground (final Screen scr) {
			this.scr = scr;
		}

		private final long REFRESH_INTERVAL = TimeUnit.MILLISECONDS.toNanos(500);
		private long lastRedrawNanos = 0L;

		/**
		 * FIXME this should follow SshScreen.tick().
		 */
		@Override
		public boolean isInvalid () {
			return System.nanoTime() - this.lastRedrawNanos > this.REFRESH_INTERVAL;
		}

		@Override
		protected ComponentRenderer<SelfBackground> createDefaultRenderer () {
			return new ComponentRenderer<SelfBackground>() {
				@Override
				public TerminalSize getPreferredSize (final SelfBackground component) {
					return SelfBackground.this.scr.getTerminalSize();
				}

				@Override
				public void drawComponent (final TextGUIGraphics graphics, final SelfBackground component) {
					SelfBackground.this.lastRedrawNanos = System.nanoTime();
					graphics.fill(' ');
					writeScreen(SelfBackground.this.scr, graphics);
				}
			};
		}
	}

	private Face activeFace () {
		return this.faces.getLast();
	}

	@Override
	public void startFace (final Face face) {
		this.faces.add(face);
	}

	@Override
	public boolean backOneLevel () {
		if (this.faces.size() <= 1) {
			scheduleQuit("user quit");
			return false;
		}
		final Face removedFace = this.faces.removeLast();
		if (removedFace != null) {
			try {
				removedFace.onClose();
			}
			catch (final Exception e) {
				LOG.error("onClose() failed.", e);
			}
		}
		return true;
	}

	@Override
	public boolean backOneLevelWithResult (final Object result) {
		final boolean ret = backOneLevel();
		if (ret) {
			try {
				this.faces.getLast().onFaceResult(result);
			}
			catch (final Exception e) {
				LOG.error("onFaceResult(" + result + ") failed.", e);
			}
		}
		return ret;
	}

	@Override
	protected boolean onInput (final KeyStroke k) throws IOException {
		try {
			return activeFace().onInput(k, this.gui);
		}
		catch (final Exception e) {
			LOG.error("Unhandled exception while processing user input.", e);
			MessageDialog.showMessageDialog(this.gui, e.getClass().getName(), ErrorHelper.getCauseTrace(e));
			return true;
		}
	}

	@Override
	protected void writeScreen (final Screen scr, final TextGraphics tg) {
		activeFace().writeScreen(scr, tg);
	}

}
