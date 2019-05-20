package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractListBox;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.input.KeyStroke;

public class DirDialog extends DialogWindow {

	private static final int WIDTH = 70;
	private static final int HEIGHT = 14;

	private final AtomicReference<File> savedInitialDir;

	private final Label lblInfo;
	private final DirListBox lstDirs;

	private File result;

	public DirDialog (final String title, final String actionLabel, final AtomicReference<File> savedInitialDir) {
		super(title);
		this.savedInitialDir = savedInitialDir;

		final Panel p = new Panel();
		p.setLayoutManager(new GridLayout(1)
				.setLeftMarginSize(1)
				.setRightMarginSize(1));

		this.lblInfo = new Label("");
		p.addComponent(this.lblInfo);

		this.lstDirs = new DirListBox(new TerminalSize(WIDTH, HEIGHT), this);
		p.addComponent(this.lstDirs);

		final Panel btnPanel = new Panel();
		btnPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));
		btnPanel.addComponent(new Button(actionLabel, new Runnable() {
			@Override
			public void run () {
				acceptResult();
			}
		}));
		btnPanel.addComponent(new EmptySpace(new TerminalSize(WIDTH - 18, 1))); // FIXME magic numbers.
		btnPanel.addComponent(new Button("Close", new Runnable() {
			@Override
			public void run () {
				close();
			}
		}));

		btnPanel.addTo(p);
		setComponent(p);

		setCloseWindowWithEscape(true);
		setHints(new HashSet<Hint>(Arrays.asList(Hint.CENTERED, Hint.MODAL)));

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

	private static class DirListBox extends AbstractListBox<File, DirListBox> {

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
			return getSelectedItem();
		}

		@Override
		protected ListItemRenderer<File, DirListBox> createDefaultListItemRenderer () {
			return new ListItemRenderer<File, DirListBox>() {
				@Override
				public int getHotSpotPositionOnLine (final int selectedIndex) {
					return -1;
				}
				@Override
				public String getLabel (final DirListBox listBox, final int index, final File item) {
					return item.getName();
				}
			};
		}

		@Override
		public synchronized Result handleKeyStroke (final KeyStroke key) {
			switch (key.getKeyType()) {
				case Enter:
					enterSelectedDir();
					return Result.HANDLED;
				case Backspace:
					gotoParentDir();
					return Result.HANDLED;
				default:
					return super.handleKeyStroke(key);
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
			if (prevI >= 0) setSelectedIndex(prevI);
		}

	}

	public static File show (final WindowBasedTextGUI owner, final String title, final String actionLabel, final AtomicReference<File> savedInitialDir) {
		final DirDialog dialog = new DirDialog(title, actionLabel, savedInitialDir);
		dialog.showDialog(owner);
		return dialog.getResult();
	}

}
