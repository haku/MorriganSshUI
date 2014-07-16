package com.vaguehope.morrigan.sshui.util;

import java.util.Arrays;

import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.terminal.Terminal.Color;
import com.googlecode.lanterna.terminal.TerminalSize;

public class TextGuiUtils {

	private String cachedBlankRow = null;

	public void drawTextRowWithBg (final Screen scr, final int top, final String s, final Color fg, final Color bg, final ScreenCharacterStyle... style) {
		TerminalSize terminalSize = scr.getTerminalSize();
		fillRow(scr, terminalSize.getColumns(), top, Color.BLUE);
		scr.putString(0, terminalSize.getRows() - 1, s, fg, bg, style);
	}

	public void fillRow (final Screen scr, final int width, final int top, final Color colour) {
		if (this.cachedBlankRow == null || this.cachedBlankRow.length() < width) {
			this.cachedBlankRow = TextGuiUtils.fillString(' ', width);
		}
		scr.putString(0, top, this.cachedBlankRow, Color.DEFAULT, colour);
	}

	private static String fillString (final char c, final int n) {
		final char[] chars = new char[n];
		Arrays.fill(chars, c);
		return new String(chars);
	}

}
