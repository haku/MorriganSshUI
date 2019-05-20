package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.gui2.AbstractBorder;
import com.googlecode.lanterna.gui2.AbstractListBox;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.CheckBoxList;
import com.googlecode.lanterna.gui2.ComboBox;
import com.googlecode.lanterna.gui2.DefaultWindowDecorationRenderer;
import com.googlecode.lanterna.gui2.GUIBackdrop;
import com.googlecode.lanterna.gui2.RadioBoxList;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.table.Table;

public class MnTheme {

	public static SimpleTheme makeTheme () {
		final SGR[] activeStyle = new SGR[] { SGR.REVERSE };
		final TextColor baseForeground = TextColor.ANSI.DEFAULT;
		final TextColor baseBackground = TextColor.ANSI.DEFAULT;
		final TextColor selectedForeground = TextColor.ANSI.DEFAULT;
		final TextColor selectedBackground = TextColor.ANSI.DEFAULT;
		TextColor editableForeground = TextColor.ANSI.DEFAULT;
		TextColor editableBackground = TextColor.ANSI.DEFAULT;
		TextColor guiBackground = TextColor.ANSI.DEFAULT;

		final SimpleTheme theme = new SimpleTheme(baseForeground, baseBackground);
		theme.getDefaultDefinition().setSelected(baseBackground, baseForeground, activeStyle);
		theme.getDefaultDefinition().setActive(selectedForeground, selectedBackground, activeStyle);

		theme.addOverride(AbstractBorder.class, baseForeground, baseBackground)
				.setSelected(baseForeground, baseBackground, activeStyle);
		theme.addOverride(AbstractListBox.class, baseForeground, baseBackground)
				.setSelected(selectedForeground, selectedBackground, activeStyle);
		theme.addOverride(Button.class, baseForeground, baseBackground)
				.setActive(selectedForeground, selectedBackground, activeStyle)
				.setSelected(selectedForeground, selectedBackground, activeStyle);
		theme.addOverride(CheckBox.class, baseForeground, baseBackground)
				.setActive(selectedForeground, selectedBackground, activeStyle)
				.setPreLight(selectedForeground, selectedBackground, activeStyle)
				.setSelected(selectedForeground, selectedBackground, activeStyle);
		theme.addOverride(CheckBoxList.class, baseForeground, baseBackground)
				.setActive(selectedForeground, selectedBackground, activeStyle);
		theme.addOverride(ComboBox.class, baseForeground, baseBackground)
				.setActive(editableForeground, editableBackground, activeStyle)
				.setPreLight(editableForeground, editableBackground);
		theme.addOverride(DefaultWindowDecorationRenderer.class, baseForeground, baseBackground)
				.setActive(baseForeground, baseBackground, new SGR[] { SGR.BOLD });
		theme.addOverride(GUIBackdrop.class, baseForeground, guiBackground );
		theme.addOverride(RadioBoxList.class, baseForeground, baseBackground)
				.setActive(selectedForeground, selectedBackground, activeStyle);
		theme.addOverride(Table.class, baseForeground, baseBackground)
				.setActive(editableForeground, editableBackground, activeStyle)
				.setSelected(baseForeground, baseBackground);
		theme.addOverride(TextBox.class, editableForeground, editableBackground)
				.setActive(editableForeground, editableBackground, activeStyle)
				.setSelected(editableForeground, editableBackground, activeStyle);

		return theme;
	}

}
