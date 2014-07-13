package com.vaguehope.morrigan.sshui;

import java.util.Deque;
import java.util.LinkedList;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import com.googlecode.lanterna.gui.DefaultBackgroundRenderer;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.sshui.Face.FaceNavigation;
import com.vaguehope.morrigan.sshui.term.SshScreen;

public class MnScreen extends SshScreen implements FaceNavigation {

	private final Deque<Face> faces = new LinkedList<Face>();
	private final MnContext mnContext;
	private GUIScreen gui;

	public MnScreen (final String name, final MnContext mnContext, final Environment env, final Terminal terminal, final ExitCallback callback) {
		super(name, env, terminal, callback);
		this.mnContext = mnContext;
	}

	@Override
	protected void initScreen (final Screen scr) {
		scr.setCursorPosition(null);
		this.gui = new GUIScreen(scr);
		this.gui.setBackgroundRenderer(new DefaultBackgroundRenderer("Morrigan desu~"));
		startFace(new HomeFace(this, this.mnContext));
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
			scheduleQuit();
			return false;
		}
		this.faces.removeLast();
		return true;
	}

	@Override
	protected boolean onInput (final Key k) {
		return activeFace().onInput(k, this.gui);
	}

	@Override
	protected void writeScreen (final Screen scr, final ScreenWriter w) {
		activeFace().writeScreen(scr, w);
	}

}
