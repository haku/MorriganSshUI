package com.vaguehope.morrigan.sshui.util;

import java.util.Arrays;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextColor.ANSI;
import com.googlecode.lanterna.graphics.TextGraphics;

public class TextGuiUtils {

	private String cachedBlankRow = null;

	public void drawTextWithFg (final TextGraphics tg, final int col, final int top, final String s, final TextColor.ANSI fg) {
		final TextColor oldFg = tg.getForegroundColor();
		tg.setForegroundColor(fg);
			tg.putString(col, top, s);
		tg.setForegroundColor(oldFg);
	}

	public void drawTextWithFg (final TextGraphics tg, final int col, final int top, final String s, final TextColor.ANSI fg, final SGR style, final SGR... optStyle) {
		final TextColor oldFg = tg.getForegroundColor();
		tg.setForegroundColor(fg);
		tg.putString(col, top, s, style, optStyle);
		tg.setForegroundColor(oldFg);
	}

	public void drawTextWithBg (final TextGraphics tg, final int col, final int top, final String s, final ANSI fg, final ANSI bg, final SGR style, final SGR... optStyle) {
		final TextColor oldFg = tg.getForegroundColor();
		final TextColor oldBg = tg.getBackgroundColor();
		tg.setForegroundColor(fg);
		tg.setBackgroundColor(bg);

		tg.putString(col, top, s, style, optStyle);

		tg.setForegroundColor(oldFg);
		tg.setBackgroundColor(oldBg);
	}

	public void drawTextRowWithBg (final TextGraphics tg, final int top, final String s, final TextColor.ANSI fg, final TextColor.ANSI bg) {
		drawTextRowWithBg(tg, top, s, fg, bg, null);
	}

	public void drawTextRowWithBg (final TextGraphics tg, final int top, final String s, final TextColor.ANSI fg, final TextColor.ANSI bg, final SGR style, final SGR... optStyle) {
		final TerminalSize terminalSize = tg.getSize();
		fillRow(tg, terminalSize.getColumns(), top, TextColor.ANSI.BLUE);

		final TextColor oldFg = tg.getForegroundColor();
		final TextColor oldBg = tg.getBackgroundColor();
		tg.setForegroundColor(fg);
		tg.setBackgroundColor(bg);

		tg.putString(0, top, s, style, optStyle);

		tg.setForegroundColor(oldFg);
		tg.setBackgroundColor(oldBg);
	}

	public void fillRow (final TextGraphics tg, final int width, final int top, final TextColor.ANSI colour) {
		if (this.cachedBlankRow == null || this.cachedBlankRow.length() < width) {
			this.cachedBlankRow = TextGuiUtils.fillString(' ', width);
		}

		final TextColor oldBg = tg.getBackgroundColor();
		tg.setBackgroundColor(colour);
		tg.putString(0, top, this.cachedBlankRow);
		tg.setBackgroundColor(oldBg);
	}

	private static String fillString (final char c, final int n) {
		final char[] chars = new char[n];
		Arrays.fill(chars, c);
		return new String(chars);
	}

}
