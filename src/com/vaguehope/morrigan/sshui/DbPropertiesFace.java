package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.ILocalMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.morrigan.sshui.util.LastActionMessage;
import com.vaguehope.morrigan.tasks.MorriganTask;

public class DbPropertiesFace extends DefaultFace {

	private static final String HELP_TEXT =
			"       g\tgo to top of list\n" +
			"       G\tgo to end of list\n" +
			"       r\trefresh\n" +
			"       n\tadd new source\n" +
			"<delete>\tremove source\n" +
			"       u\trescan sources\n" +
			"       q\tback a page\n" +
			"       h\tthis help text";

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final IMixedMediaDb db;

	private final LastActionMessage lastActionMessage = new LastActionMessage();
	private final AtomicReference<File> savedInitialDir = new AtomicReference<File>();

	private List<String> sources;
	private Object selectedItem;
	private int queueScrollTop = 0;
	private int pageSize = 1;

	public DbPropertiesFace (final FaceNavigation navigation, final MnContext mnContext, final IMixedMediaDb db) throws MorriganException {
		super(navigation);
		this.navigation = navigation;
		this.mnContext = mnContext;
		this.db = db;
		this.savedInitialDir.set(new File(System.getProperty("user.home")));
		refreshData();
	}

	private void refreshData () throws MorriganException {
		this.sources = this.db.getSources();
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
			case Delete:
				removeSource(gui);
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
					case 'n':
						askAddSource(gui);
						return true;
					case 'u':
						rescanSources();
						return true;
					default:
				}
			default:
				return super.onInput(k, gui);
		}
	}

	private void menuMove (final KeyStroke k, final int distance) {
		this.selectedItem = MenuHelper.moveListSelection(this.selectedItem,
				k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.PageUp
						? VDirection.UP
						: VDirection.DOWN,
				distance,
				this.sources);
	}

	private void menuMoveEnd (final VDirection direction) {
		if (this.sources == null || this.sources.size() < 1) return;
		switch (direction) {
			case UP:
				this.selectedItem = this.sources.get(0);
				break;
			case DOWN:
				this.selectedItem = this.sources.get(this.sources.size() - 1);
				break;
			default:
		}
	}

	private void askAddSource (final WindowBasedTextGUI gui) throws MorriganException {
		final File dir = DirDialog.show(gui, "Add Source", "Add", this.savedInitialDir);
		if (dir != null) {
			this.db.addSource(dir.getAbsolutePath());
			refreshData();
		}
	}

	private void removeSource (final WindowBasedTextGUI gui) throws MorriganException {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof String && this.sources != null) {
			final int i = this.sources.indexOf(this.selectedItem);
			final String source = (String) this.selectedItem;
			if (MessageDialog.showMessageDialog(gui, "Remove Source", source, MessageDialogButton.Yes, MessageDialogButton.No) != MessageDialogButton.Yes) return;
			this.db.removeSource(source);
			refreshData();
			if (i >= this.sources.size()) { // Last item was deleted.
				this.selectedItem = this.sources.get(this.sources.size() - 1);
			}
			else if (i >= 0) {
				this.selectedItem = this.sources.get(i);
			}
		}
	}

	private void rescanSources () {
		if (this.db instanceof ILocalMixedMediaDb) {
			final MorriganTask task = this.mnContext.getMediaFactory().getLocalMixedMediaDbUpdateTask((ILocalMixedMediaDb) this.db);
			if (task != null) {
				this.mnContext.getAsyncTasksRegister().scheduleTask(task);
				this.lastActionMessage.setLastActionMessage("Update started.");
			}
			else {
				this.lastActionMessage.setLastActionMessage("Unable to start update, one may already be in progress.");
			}
		}
		else {
			this.lastActionMessage.setLastActionMessage("Do not know how to refresh: " + this.db);
		}
	}

	@Override
	public void writeScreen (final Screen scr, final TextGraphics tg) {
		if (this.db != null) {
			writeDbPropsToScreen(scr, tg);
		}
		else {
			tg.putString(0, 0, "Unable to show null db.");
		}
	}

	private void writeDbPropsToScreen (final Screen scr, final TextGraphics tg) {
		int l = 0;
		tg.putString(0, l++, String.format("DB %s:", this.db.getListName()));
		this.lastActionMessage.drawLastActionMessage(tg, l++);

		this.pageSize = scr.getTerminalSize().getRows() - l;
		final int selI = this.sources.indexOf(this.selectedItem);
		if (selI >= 0) {
			if (selI - this.queueScrollTop >= this.pageSize) {
				this.queueScrollTop = selI - this.pageSize + 1;
			}
			else if (selI < this.queueScrollTop) {
				this.queueScrollTop = selI;
			}
		}

		for (int i = this.queueScrollTop; i < this.sources.size(); i++) {
			if (i > this.queueScrollTop + this.pageSize) break;
			final String item = this.sources.get(i);
			if (item.equals(this.selectedItem)) {
				tg.putString(1, l++, String.valueOf(item), SGR.REVERSE);
			}
			else {
				tg.putString(1, l++, String.valueOf(item));
			}
		}
	}
}
