package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;

public interface Face {

	interface FaceNavigation {

		void startFace (Face face);

		/**
		 * Returns true if was not the last face.
		 * If true is returned screen needs redrawing.
		 */
		boolean backOneLevel ();

		/**
		 * Returns same as backOneLevel().
		 */
		boolean backOneLevelWithResult (Object result);

	}

	boolean onInput (KeyStroke k, WindowBasedTextGUI gui) throws Exception;

	boolean processEvents ();

	void writeScreen (Screen scr, TextGraphics tg);

	/**
	 * Called when face closed with backOneLevelWithResult();
	 */
	void onFaceResult (Object result) throws Exception; // NOSONAR throws Exception is part of API.

	/**
	 * Called once when face is closed.
	 */
	void onClose () throws Exception;

}
