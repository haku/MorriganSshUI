package com.vaguehope.morrigan.sshui.term;

import com.googlecode.lanterna.gui.Theme;
import com.googlecode.lanterna.terminal.Terminal.Color;

public class DarkTheme extends Theme {
	public static final Theme INSTANCE = new DarkTheme();

	public DarkTheme () {
		super();
		setDefinition(Category.DIALOG_AREA, new Definition(Color.DEFAULT, Color.DEFAULT, false));

		setDefinition(Category.BORDER, new Definition(Color.BLACK, Color.DEFAULT, true));
		setDefinition(Category.RAISED_BORDER, new Definition(Color.WHITE, Color.DEFAULT, true));

		setDefinition(Category.TEXTBOX, new Definition(Color.DEFAULT, Color.DEFAULT, true));
		setDefinition(Category.TEXTBOX_FOCUSED, new Definition(Color.DEFAULT, Color.DEFAULT, true));

		setDefinition(Category.BUTTON_ACTIVE, new Definition(Color.WHITE, Color.BLUE, true));
		setDefinition(Category.BUTTON_LABEL_ACTIVE, new Definition(Color.YELLOW, Color.BLUE, true));

		setDefinition(Category.BUTTON_INACTIVE, new Definition(Color.DEFAULT, Color.DEFAULT, false));
		setDefinition(Category.BUTTON_LABEL_INACTIVE, new Definition(Color.DEFAULT, Color.DEFAULT, true));
	}
}
