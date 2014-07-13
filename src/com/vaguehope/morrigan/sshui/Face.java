package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;

public interface Face {

	public interface FaceNavigation {

		void startFace (Face face);

		/**
		 * Returns true if a redraw is required.
		 */
		boolean backOneLevel ();

	}

	boolean onInput (Key k, GUIScreen gui) throws Exception;

	void writeScreen (Screen scr, ScreenWriter w);

}
