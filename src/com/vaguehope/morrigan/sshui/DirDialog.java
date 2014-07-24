package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.Border.Invisible;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.Window;
import com.googlecode.lanterna.gui.component.AbstractListBox;
import com.googlecode.lanterna.gui.component.Button;
import com.googlecode.lanterna.gui.component.EmptySpace;
import com.googlecode.lanterna.gui.component.Label;
import com.googlecode.lanterna.gui.component.Panel;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.terminal.TerminalPosition;
import com.googlecode.lanterna.terminal.TerminalSize;

public class DirDialog extends Window {

	private static final int WIDTH = 70;
	private static final int HEIGHT = 14;

	private final AtomicReference<File> savedInitialDir;

	private final Label lblInfo;
	private final DirListBox lstDirs;

	private File result;

	public DirDialog (final String title, final String actionLabel, final AtomicReference<File> savedInitialDir) {
		super(title);
		this.savedInitialDir = savedInitialDir;

		this.lblInfo = new Label("");
		addComponent(this.lblInfo);

		this.lstDirs = new DirListBox(new TerminalSize(WIDTH, HEIGHT), this);
		addComponent(this.lstDirs);

		final Panel btnPanel = new Panel(new Invisible(), Panel.Orientation.HORISONTAL);
		btnPanel.addComponent(new Button(actionLabel, new Action() {
			@Override
			public void doAction () {
				acceptResult();
			}
		}));
		btnPanel.addComponent(new EmptySpace(WIDTH - 18, 1)); // FIXME magic numbers.
		btnPanel.addComponent(new Button("Close", new Action() {
			@Override
			public void doAction () {
				close();
			}
		}));
		addComponent(btnPanel);

		this.lstDirs.setDir(savedInitialDir.get());
	}

	public File getResult () {
		return this.result;
	}

	protected void acceptResult () {
		final File file = this.lstDirs.getSelectedDir();
		this.result = file;
		close();
	}

	private static class DirListBox extends AbstractListBox {

		private final DirDialog dialog;

		private File currentDir;

		public DirListBox (final TerminalSize preferredSize, final DirDialog dialog) {
			super(preferredSize);
			this.dialog = dialog;
		}

		public void setDir (final File dir) {
			this.currentDir = dir;
			clearItems();
			final File[] items = dir.listFiles(new FileFilter() {
				@Override
				public boolean accept (final File file) {
					return !file.getName().startsWith(".") && file.isDirectory();
				}
			});
			Arrays.sort(items);
			for (final File item : items) {
				addItem(item);
			}
			this.dialog.lblInfo.setText(String.format("%s (%s items)", dir.getAbsolutePath(), items.length));
		}

		public File getSelectedDir () {
			return (File) getSelectedItem();
		}

		@Override
		protected String createItemString (final int index) {
			return ((File) getItemAt(index)).getName();
		}

		@Override
		public TerminalPosition getHotspot () {
			return null;
		}

		@Override
		protected Result unhandledKeyboardEvent (final Key key) {
			switch (key.getKind()) {
				case Enter:
					enterSelectedDir();
					return Result.EVENT_HANDLED;
				case Backspace:
					gotoParentDir();
					return Result.EVENT_HANDLED;
				default:
					return Result.EVENT_NOT_HANDLED;
			}
		}

		private void enterSelectedDir () {
			final File dir = getSelectedDir();
			setDir(dir);
			this.dialog.savedInitialDir.set(dir);
		}

		private void gotoParentDir () {
			final File parentDir = this.currentDir.getParentFile();
			if (parentDir == null) return;

			final File prevDir = this.currentDir;
			setDir(parentDir);
			final int prevI = indexOf(prevDir);
			if (prevI >= 0) setSelectedItem(prevI);
		}

	}

	public static File show (final GUIScreen owner, final String title, final String actionLabel, final AtomicReference<File> savedInitialDir) {
		final DirDialog dialog = new DirDialog(title, actionLabel, savedInitialDir);
		owner.showWindow(dialog, GUIScreen.Position.CENTER);
		return dialog.getResult();
	}

}
