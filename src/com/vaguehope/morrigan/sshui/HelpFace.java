package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;

public class HelpFace implements Face {

	private final FaceNavigation navigation;
	private final String helpText;

	public HelpFace (final FaceNavigation navigation, final String helpText) {
		this.navigation = navigation;
		this.helpText = helpText;
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) throws Exception {
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
		for (final String line : this.helpText.split("\n")) {
			w.drawString(0, l++, line);
		}
	}

}
