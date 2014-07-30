package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;

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

	boolean onInput (Key k, GUIScreen gui) throws Exception; // NOSONAR throws Exception is part of API.

	void writeScreen (Screen scr, ScreenWriter w);

	/**
	 * Called when face closed with backOneLevelWithResult();
	 */
	void onFaceResult (Object result) throws Exception; // NOSONAR throws Exception is part of API.

}
