package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.vaguehope.morrigan.model.media.MediaListReference;

public class DbFace implements Face {

	private final FaceNavigation navigation;
	private final MediaListReference listReference;

	public DbFace (final FaceNavigation navigation, final MediaListReference listReference) {
		this.navigation = navigation;
		this.listReference = listReference;
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) {

		// TODO
		// - help screen.

		switch (k.getKind()) {
			case NormalKey:
				switch (k.getCharacter()) {
					case 'q':
						return this.navigation.backOneLevel();
					default:
				}
			default:
				return false;
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, "TODO: " + this.listReference);
	}

}
