package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.screen.Screen;

public class HelpFace extends DefaultFace {

	private final String helpText;

	public HelpFace (final FaceNavigation navigation, final String helpText) {
		super(navigation);
		this.helpText = helpText;
	}

	@Override
	public void writeScreen (final Screen scr, final TextGraphics tg) {
		int l = 0;
		for (final String line : this.helpText.split("\n")) {
			tg.putString(0, l++, line);
		}
	}

}
