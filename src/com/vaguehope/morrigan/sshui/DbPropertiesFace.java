package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.DialogButtons;
import com.googlecode.lanterna.gui.dialog.DialogResult;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.ILocalMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.morrigan.tasks.MorriganTask;
import com.vaguehope.sqlitewrapper.DbException;

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

	private static final long LAST_ACTION_MESSAGE_DURATION_MILLIS = 5000L;

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final IMixedMediaDb db;

	private final AtomicReference<File> savedInitialDir = new AtomicReference<File>();

	private List<String> sources;
	private Object selectedItem;
	private int queueScrollTop = 0;
	private int pageSize = 1;
	private String lastActionMessage = null;
	private long lastActionMessageTime = 0;


	public DbPropertiesFace (final FaceNavigation navigation, final MnContext mnContext, final IMixedMediaDb db) throws MorriganException {
		this.navigation = navigation;
		this.mnContext = mnContext;
		this.db = db;
		this.savedInitialDir.set(new File(System.getProperty("user.home")));
		refreshData();
	}

	private void refreshData () throws MorriganException {
		this.sources = this.db.getSources();
	}

	protected void setLastActionMessage (final String lastActionMessage) {
		this.lastActionMessage = lastActionMessage;
		this.lastActionMessageTime = System.currentTimeMillis();
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) throws DbException, MorriganException {
		switch (k.getKind()) {
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
			case NormalKey:
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
				return false;
		}
	}

	private void menuMove (final Key k, final int distance) {
		this.selectedItem = MenuHelper.moveListSelection(this.selectedItem,
				k.getKind() == Kind.ArrowUp || k.getKind() == Kind.PageUp
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

	private void askAddSource (final GUIScreen gui) throws MorriganException {
		final File dir = DirDialog.show(gui, "Add Source", "Add", this.savedInitialDir);
		if (dir != null) {
			this.db.addSource(dir.getAbsolutePath());
			refreshData();
		}
	}

	private void removeSource (final GUIScreen gui) throws MorriganException {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof String && this.sources != null) {
			final int i = this.sources.indexOf(this.selectedItem);
			final String source = (String) this.selectedItem;
			if (MessageBox.showMessageBox(gui, "Remove Source", source, DialogButtons.YES_NO) != DialogResult.YES) return;
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
				setLastActionMessage("Update started.");
			}
			else {
				setLastActionMessage("Unable to start update, one may already be in progress.");
			}
		}
		else {
			setLastActionMessage("Do not know how to refresh: " + this.db);
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		if (this.db != null) {
			writeDbPropsToScreen(scr, w);
		}
		else {
			w.drawString(0, 0, "Unable to show null db.");
		}
	}

	private void writeDbPropsToScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, String.format("DB %s:", this.db.getListName()));

		if (this.lastActionMessage != null && System.currentTimeMillis() - this.lastActionMessageTime > LAST_ACTION_MESSAGE_DURATION_MILLIS) {
			this.lastActionMessage = null;
		}
		if (this.lastActionMessage != null && this.lastActionMessage.length() > 0) {
			w.drawString(0, l++, String.format(">> %s", this.lastActionMessage));
		}
		else {
			l++;
		}

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
				w.drawString(1, l++, String.valueOf(item), ScreenCharacterStyle.Reverse);
			}
			else {
				w.drawString(1, l++, String.valueOf(item));
			}
		}
	}
}
