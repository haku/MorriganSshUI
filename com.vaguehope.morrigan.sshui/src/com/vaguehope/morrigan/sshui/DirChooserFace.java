package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;

public class DirChooserFace extends DefaultFace {

	private static final String HELP_TEXT =
			"          g\tgo to top of list\n" +
			"          G\tgo to end of list\n" +
			"          r\trefresh\n" +
			"    <enter>\tgo into directory\n" +
			"<backspace>\tback up one level\n" +
			"    <space>\tselect item and return to previous screen\n" +
			"          q\tback a page\n" +
			"          h\tthis help text";

	private final FaceNavigation navigation;

	private File currentDir;
	private File[] fileList;
	private int selectedItemIndex = -1;
	private int scrollTop = 0;
	private int pageSize = 1;

	public DirChooserFace (final FaceNavigation navigation, final File initialDir) {
		super(navigation);
		this.navigation = navigation;
		setCurrentDir(initialDir);
	}

	private void refreshData () {
		this.fileList = this.currentDir.listFiles(new FileFilter() {
			@Override
			public boolean accept (final File file) {
				return !file.getName().startsWith(".") && file.isDirectory();
			}
		});
		Arrays.sort(this.fileList);
	}

	private void setCurrentDir (final File dir) {
		this.currentDir = dir;
		refreshData();
		this.selectedItemIndex = -1;
	}

	@Override
	public boolean onInput (final KeyStroke k, final WindowBasedTextGUI gui) throws Exception {
		switch (k.getKeyType()) {
			case ArrowUp:
			case ArrowDown:
				menuMove(k, 1);
				return true;
			case PageUp:
			case PageDown:
				menuMove(k, this.pageSize - 1);
				return true;
			case Home:
				menuMoveEnd(VDirection.UP);
				return true;
			case End:
				menuMoveEnd(VDirection.DOWN);
				return true;
			case Enter:
				clickItem();
				return true;
			case Backspace:
				gotoParentDir();
				return true;
			case Character:
				switch (k.getCharacter()) {
					case 'q':
						return this.navigation.backOneLevel();
					case 'h':
						this.navigation.startFace(new HelpFace(this.navigation, HELP_TEXT));
						return true;
					case 'g':
						menuMoveEnd(VDirection.UP);
						return true;
					case 'G':
						menuMoveEnd(VDirection.DOWN);
						return true;
					case 'r':
						refreshData();
						return true;
					case ' ':
						acceptItem();
						return true;
					default:
				}
			default:
				return super.onInput(k, gui);
		}
	}

	private void menuMove (final KeyStroke k, final int distance) {
		this.selectedItemIndex = MenuHelper.moveListSelectionIndex(this.selectedItemIndex,
				k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.PageUp
						? VDirection.UP
						: VDirection.DOWN,
				distance,
				this.fileList);
	}

	private void menuMoveEnd (final VDirection direction) {
		if (this.fileList == null || this.fileList.length < 1) return;
		switch (direction) {
			case UP:
				this.selectedItemIndex = 0;
				break;
			case DOWN:
				this.selectedItemIndex = this.fileList.length - 1;
				break;
			default:
		}
	}

	private File getSelectedItem () {
		if (this.selectedItemIndex < 0) return null;
		return this.fileList[this.selectedItemIndex];
	}

	private void clickItem () {
		final File item = getSelectedItem();
		if (item == null) return;
		setCurrentDir(item.getAbsoluteFile());
	}

	private void gotoParentDir () {
		final File parentDir = this.currentDir.getParentFile();
		if (parentDir == null) return;

		final File prevDir = this.currentDir;
		setCurrentDir(parentDir);
		this.selectedItemIndex = Arrays.binarySearch(this.fileList, prevDir);
	}

	private void acceptItem () {
		final File item = getSelectedItem();
		if (item == null) return;
		this.navigation.backOneLevelWithResult(item);
	}

	@Override
	public void writeScreen (final Screen scr, final TextGraphics tg) {
		if (this.fileList != null) {
			writeFileListToScreen(scr, tg);
		}
		else {
			tg.putString(0, 0, "Unable to show " + this.currentDir.getAbsolutePath());
		}
	}

	private void writeFileListToScreen (final Screen scr, final TextGraphics tg) {
		int l = 0;
		tg.putString(0, l++, String.format("Dir %s:", this.currentDir.getAbsolutePath()));

		this.pageSize = scr.getTerminalSize().getRows() - l;
		if (this.selectedItemIndex >= 0) {
			if (this.selectedItemIndex - this.scrollTop >= this.pageSize) {
				this.scrollTop = this.selectedItemIndex - this.pageSize + 1;
			}
			else if (this.selectedItemIndex < this.scrollTop) {
				this.scrollTop = this.selectedItemIndex;
			}
		}
		else {
			this.scrollTop = 0;
		}

		if (this.fileList.length > 0) {
			for (int i = this.scrollTop; i < this.fileList.length; i++) {
				if (i > this.scrollTop + this.pageSize) break;
				final String label = this.fileList[i].getName();
				if (i == this.selectedItemIndex) {
					tg.putString(1, l++, label, SGR.REVERSE);
				}
				else {
					tg.putString(1, l++, label);
				}
			}
		}
		else {
			tg.putString(1, l++, "(empty)");
		}

	}
}
