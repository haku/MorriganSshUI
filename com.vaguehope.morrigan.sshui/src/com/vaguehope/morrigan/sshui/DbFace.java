package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialog;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialogBuilder;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaItemStorageLayer.SortDirection;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMixedMediaItem;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.morrigan.sshui.util.LastActionMessage;
import com.vaguehope.morrigan.sshui.util.TextGuiUtils;
import com.vaguehope.morrigan.tasks.MorriganTask;
import com.vaguehope.sqlitewrapper.DbException;

public class DbFace extends DefaultFace {

	private static final String HELP_TEXT =
			"      g\tgo to top of list\n" +
			"      G\tgo to end of list\n" +
			"      z\tcentre selection\n" +
			"      /\tsearch DB\n" +
			"      o\tsort order\n" +
			"      e\tenqueue item(s)\n" +
			"      E\tenqueue DB\n" +
			"<enter>\tplay item(s)\n" +
			"      t\ttag editor\n" +
			"      v\tselect\n" +
			"      x\tselect\n" +
			"      w\tcopy file(s)\n" +
			"      d\ttoggle item(s) enabled\n" +
			"      r\trefresh query\n" +
			"     f6\tDB properties\n" +
			"      q\tback a page\n" +
			"      h\tthis help text";

	private static final String PREF_SCROLL_INDEX = "dbscroll";
	private static final String PREF_SELECTED_INDEX = "dbselected";

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final IMixedMediaDb db;
	private final Player defaultPlayer;
	private final DbHelper dbHelper;

	private final TextGuiUtils textGuiUtils = new TextGuiUtils();
	private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	private final LastActionMessage lastActionMessage = new LastActionMessage();
	private final AtomicReference<File> savedInitialDir = new AtomicReference<File>();
	private final Set<IMixedMediaItem> selectedItems = new HashSet<IMixedMediaItem>();
	private final AtomicReference<String> savedSearchTerm = new AtomicReference<String>();

	private List<IMixedMediaItem> mediaItems;
	private int selectedItemIndex = -1;
	private int scrollTop = 0;
	private int pageSize = 1;
	private String itemDetailsBar = "";
	private IMixedMediaItem itemDetailsBarItem;
	private boolean saveScrollOnClose = false;

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final IMixedMediaDb db, final Player defaultPlayer) throws MorriganException {
		super(navigation);
		this.navigation = navigation;
		this.mnContext = mnContext;
		this.db = db;
		this.defaultPlayer = defaultPlayer;
		this.dbHelper = new DbHelper(navigation, mnContext, this.defaultPlayer, this.lastActionMessage, this);
		refreshData();
	}

	@Override
	public void onClose () throws Exception {
		saveScrollIfRequired();
		super.onClose();
	}

	public void restoreSavedScroll () throws MorriganException {
		try {
			this.scrollTop = this.mnContext.getUserPrefs().getIntValue(PREF_SCROLL_INDEX, this.db.getListId(), this.scrollTop);
			this.selectedItemIndex = this.mnContext.getUserPrefs().getIntValue(PREF_SELECTED_INDEX, this.db.getListId(),
					this.scrollTop > 0 ? this.scrollTop : this.selectedItemIndex);
			this.saveScrollOnClose = true;
		}
		catch (final IOException e) {
			throw new MorriganException("Failed to read saved scroll position.", e);
		}
	}

	private void saveScrollIfRequired () throws MorriganException {
		if (!this.saveScrollOnClose) return;
		try {
			this.mnContext.getUserPrefs().putValue(PREF_SCROLL_INDEX, this.db.getListId(), this.scrollTop);
			this.mnContext.getUserPrefs().putValue(PREF_SELECTED_INDEX, this.db.getListId(), this.selectedItemIndex);
		}
		catch (final IOException e) {
			throw new MorriganException("Failed to save scroll position.", e);
		}
	}

	public void revealItem (final IMediaTrack track) throws MorriganException {
		final int i = this.mediaItems.indexOf(track);
		if (i >= 0) {
			setSelectedItem(i);
		}
		else {
			this.lastActionMessage.setLastActionMessage("Item not in view: " + track); // TODO open new DbFace here?
		}
	}

	private void refreshData () throws MorriganException {
		if (this.db == null) return;
		this.db.read();
		this.mediaItems = this.db.getMediaItems();
	}

	private void updateItemDetailsBar () {
		if (this.selectedItems.size() > 0) {
			this.itemDetailsBar = String.format("%s selected.", this.selectedItems.size());
			this.itemDetailsBarItem = null;
		}
		else {
			final IMixedMediaItem item = getSelectedItem();
			if (this.itemDetailsBarItem != null && this.itemDetailsBarItem.equals(item)) return;

			this.mnContext.getUnreliableEs().submit(new Callable<Void>() {
				@Override
				public Void call () throws MorriganException {
					final String tags = PrintingThingsHelper.summariseItemTags(DbFace.this.db, item);
					scheduleOnUiThread(new Callable<Void>() {
						@Override
						public Void call () {
							DbFace.this.itemDetailsBarItem = item;
							DbFace.this.itemDetailsBar = tags;
							return null;
						}
					});
					return null;
				}
			});
		}
	}

	@Override
	public boolean onInput (final KeyStroke k, final WindowBasedTextGUI gui) throws Exception {
		// TODO
		// - jump back all searches? (Q) (go back to last player / non search Face?).

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
			case F6:
				this.navigation.startFace(new DbPropertiesFace(this.navigation, this.mnContext, this.db));
				return true;
			case Enter:
				playSelection(gui);
				return true;
			case Character:
				switch (k.getCharacter()) {
					case 'h':
						this.navigation.startFace(new HelpFace(this.navigation, HELP_TEXT));
						return true;
					case 'g':
						menuMoveEnd(VDirection.UP);
						return true;
					case 'G':
						menuMoveEnd(VDirection.DOWN);
						return true;
					case 'z':
						centreSelection();
						return true;
					case 'e':
						enqueueSelection(gui);
						return true;
					case 'E':
						enqueueDb(gui);
						return true;
					case 't':
						showEditTagsForSelectedItem(gui);
						return true;
					case 'v':
					case 'x':
						toggleSelection();
						return true;
					case 'w':
						askExportSelection(gui);
						return true;
					case 'd':
						toggleEnabledSelection();
						return true;
					case 'o':
						askSortColumn(gui);
						return true;
					case '/':
						askSearch(gui);
						return true;
					case 'r':
						refreshData();
						return true;
					default:
				}
				//$FALL-THROUGH$
			default:
				return super.onInput(k, gui);
		}
	}

	private void menuMove (final KeyStroke k, final int distance) throws MorriganException {
		this.selectedItemIndex = MenuHelper.moveListSelectionIndex(this.selectedItemIndex,
				k.getKeyType() == KeyType.ArrowUp || k.getKeyType() == KeyType.PageUp
						? VDirection.UP
						: VDirection.DOWN,
				distance,
				this.mediaItems);
		updateItemDetailsBar();
	}

	private void menuMoveEnd (final VDirection direction) throws MorriganException {
		if (this.mediaItems == null || this.mediaItems.size() < 1) return;
		switch (direction) {
			case UP:
				this.selectedItemIndex = 0;
				break;
			case DOWN:
				this.selectedItemIndex = this.mediaItems.size() - 1;
				break;
			default:
		}
		updateItemDetailsBar();
	}

	private void centreSelection () {
		final int t = this.selectedItemIndex - (this.pageSize / 2);
		if (t >= 0) this.scrollTop = t;
	}

	private void setSelectedItem (final int index) throws MorriganException {
		this.selectedItemIndex = index;
		updateItemDetailsBar();
	}

	private IMixedMediaItem getSelectedItem () {
		if (this.selectedItemIndex < 0) return null;
		return this.mediaItems.get(this.selectedItemIndex);
	}

	private List<IMixedMediaItem> getSelectedItems () {
		if (this.selectedItems.size() > 0) {
			final List<IMixedMediaItem> ret = new ArrayList<IMixedMediaItem>();
			for (final IMixedMediaItem item : this.mediaItems) {
				if (this.selectedItems.contains(item)) ret.add(item);
			}
			return ret;
		}
		if (this.selectedItemIndex >= 0) return Collections.singletonList(this.mediaItems.get(this.selectedItemIndex));
		return Collections.emptyList();
	}

	private Player getPlayer (final WindowBasedTextGUI gui, final String title) {
		return PlayerHelper.askWhichPlayer(gui, title, this.defaultPlayer, this.mnContext.getPlayerReader().getPlayers());
	}

	private void enqueueDb (final WindowBasedTextGUI gui) {
		final Player player = getPlayer(gui, "Enqueue DB");
		if (player != null) enqueuePlayItem(new PlayItem(this.db, null), player);
	}

	protected void enqueuePlayItem (final PlayItem playItem, final Player player) {
		player.getQueue().addToQueue(playItem);
		// TODO protect against long item names?
		this.lastActionMessage.setLastActionMessage(String.format("Enqueued %s in %s.", playItem, player.getName()));
	}

	private void enqueueSelection (final WindowBasedTextGUI gui) {
		final List<IMixedMediaItem> items = getSelectedItems();
		enqueueItems(gui, items);
		if (items.size() == 1 && items.contains(getSelectedItem())) this.selectedItemIndex += 1;
	}

	private void enqueueItems (final WindowBasedTextGUI gui, final List<? extends IMediaTrack> tracks) {
		if (tracks.size() < 1) return;
		final Player player = getPlayer(gui, String.format("Enqueue %s items", tracks.size()));
		if (player == null) return;
		PlayerHelper.enqueueAll(this.db, tracks, player);
		this.lastActionMessage.setLastActionMessage(String.format("Enqueued %s items in %s.", tracks.size(), player.getName()));
	}

	private void playSelection (final WindowBasedTextGUI gui) {
		playItems(gui, getSelectedItems());
	}

	private void playItems (final WindowBasedTextGUI gui, final List<IMixedMediaItem> tracks) {
		if (tracks.size() < 1) return;
		final Player player = getPlayer(gui, String.format("Play %s items", tracks.size()));
		if (player == null) return;
		this.lastActionMessage.setLastActionMessage(String.format("Playing %s items in %s.", tracks.size(), player.getName()));
		PlayerHelper.playAll(this.db, tracks, player);
	}

	private void showEditTagsForSelectedItem (final WindowBasedTextGUI gui) throws MorriganException {
		final IMixedMediaItem item = getSelectedItem();
		if (item == null) return;
		TagEditor.show(gui, this.db, item);
	}

	private void toggleSelection () throws MorriganException {
		final IMixedMediaItem item = getSelectedItem();
		if (item == null) return;
		if (!this.selectedItems.remove(item)) this.selectedItems.add(item);
		updateItemDetailsBar();
	}

	private void askExportSelection (final WindowBasedTextGUI gui) {
		this.savedInitialDir.compareAndSet(null, new File(System.getProperty("user.home")));
		final List<IMixedMediaItem> items = getSelectedItems();
		if (items.size() < 1) return;
		final File dir = DirDialog.show(gui, String.format("Export %s tracks", items.size()), "Export", this.savedInitialDir);
		if (dir == null) return;
		final MorriganTask task = this.mnContext.getMediaFactory().getMediaFileCopyTask(this.db, items, dir);
		this.mnContext.getAsyncTasksRegister().scheduleTask(task);
		this.lastActionMessage.setLastActionMessage(String.format("Started copying %s tracks ...", items.size()));
	}

	private void toggleEnabledSelection () throws MorriganException {
		final List<IMixedMediaItem> items = getSelectedItems();
		for (final IMixedMediaItem item : items) {
			this.db.setItemEnabled(item, !item.isEnabled());
		}
		this.lastActionMessage.setLastActionMessage(String.format("Toggled enabled on %s items.", items.size()));
	}

	private void askSortColumn (final WindowBasedTextGUI gui) {
		final List<IDbColumn> cols = this.db.getDbLayer().getMediaTblColumns();
		final List<Runnable> actions = new ArrayList<Runnable>();
		for (final IDbColumn col : cols) {
			if (col.getHumanName() != null) {
				actions.add(new SortColumnAction(this.db, col, SortDirection.ASC));
				actions.add(new SortColumnAction(this.db, col, SortDirection.DESC));
			}
		}
		final ActionListDialog dlg = new ActionListDialogBuilder()
				.setTitle("Sort Order")
				.setDescription("Current: " + PrintingThingsHelper.sortSummary(this.db))
				.addActions(actions.toArray(new Runnable[actions.size()]))
				.build();
		dlg.setCloseWindowWithEscape(true);
		dlg.showDialog(gui);
	}

	private void askSearch (final WindowBasedTextGUI gui) throws DbException, MorriganException {
		this.dbHelper.askSearch(gui, this.db, this.savedSearchTerm);
	}

	@Override
	public void writeScreen (final Screen scr, final TextGraphics tg) {
		if (this.db != null) {
			writeDbToScreen(scr, tg);
		}
		else {
			tg.putString(0, 0, "Unable to show null db.");
		}
	}

	private void writeDbToScreen (final Screen scr, final TextGraphics tg) {
		final TerminalSize terminalSize = scr.getTerminalSize();
		int l = 0;

		tg.putString(0, l++, String.format("DB %s: %s   %s",
				this.db.getListName(),
				PrintingThingsHelper.dbSummary(this.db),
				PrintingThingsHelper.sortSummary(this.db)));
		this.lastActionMessage.drawLastActionMessage(tg, l++);

		this.pageSize = terminalSize.getRows() - l - 1;
		if (this.selectedItemIndex >= 0) {
			if (this.selectedItemIndex - this.scrollTop >= this.pageSize) {
				this.scrollTop = this.selectedItemIndex - this.pageSize + 1;
			}
			else if (this.selectedItemIndex < this.scrollTop) {
				this.scrollTop = this.selectedItemIndex;
			}
		}

		final int colRightDuration = terminalSize.getColumns();
		final int colRightPlayCount = colRightDuration - 8;
		final int colRightLastPlayed = colRightPlayCount - 8;

		for (int i = this.scrollTop; i < this.mediaItems.size(); i++) {
			if (i >= this.scrollTop + this.pageSize) break;

			final IMixedMediaItem item = this.mediaItems.get(i);
			final String name = String.valueOf(item);

			final boolean selectedItem = this.selectedItems.contains(item);
			if (selectedItem) {
				tg.enableModifiers(SGR.REVERSE);
			}
			else {
				tg.disableModifiers(SGR.REVERSE);
			}

			if (item.isMissing()) {
				this.textGuiUtils.drawTextWithFg(tg, 0, l, "m", TextColor.ANSI.YELLOW);
			}
			else if (!item.isEnabled()) {
				this.textGuiUtils.drawTextWithFg(tg, 0, l, "d", TextColor.ANSI.RED);
			}
			else if (selectedItem) {
				tg.putString(0, l, ">");
			}

			if (i == this.selectedItemIndex) {
				tg.enableModifiers(SGR.REVERSE);
			}
			else {
				tg.disableModifiers(SGR.REVERSE);
			}

			// Item title.
			tg.putString(1, l, name);

			// Rest of item title space if selected.
			if (i == this.selectedItemIndex) {
				for (int x = 1 + TerminalTextUtils.getColumnWidth(name); x < colRightLastPlayed; x++) {
					tg.setCharacter(x, l, ' ');
				}
			}

			// Warning labels.
			if (item.isMissing()) {
				this.textGuiUtils.drawTextWithFg(tg, name.length() + 2, l, "(missing)", TextColor.ANSI.YELLOW);
			}
			else if (!item.isEnabled()) {
				this.textGuiUtils.drawTextWithFg(tg, name.length() + 2, l, "(disabled)", TextColor.ANSI.RED);
			}

			// Last played column.
			if (item.getDateLastPlayed() != null) {
				final String lastPlayed = String.format(" %s", this.dateFormat.format(item.getDateLastPlayed()));
				tg.putString(colRightLastPlayed - lastPlayed.length(), l, lastPlayed);
			}

			// Play count column.
			if (item.getStartCount() > 0 || item.getEndCount() > 0) {
				final String counts = String.format("%4s/%-3s", item.getStartCount(), item.getEndCount());
				tg.putString(colRightPlayCount - counts.length(), l, counts);
			}

			// Duration column.
			final String dur = formatTimeSecondsLeftPadded(item.getDuration());
			tg.putString(colRightDuration - dur.length(), l, dur);

			l++;
		}
		tg.disableModifiers(SGR.REVERSE);

		this.textGuiUtils.drawTextRowWithBg(tg, terminalSize.getRows() - 1, this.itemDetailsBar, TextColor.ANSI.WHITE, TextColor.ANSI.BLUE, SGR.BOLD);
		this.textGuiUtils.drawTextWithBg(tg, terminalSize.getColumns() - 3, terminalSize.getRows() - 1,
				PrintingThingsHelper.scrollSummary(this.mediaItems.size(), this.pageSize, this.scrollTop),
				TextColor.ANSI.WHITE, TextColor.ANSI.BLUE, SGR.BOLD);
	}

	private static String formatTimeSecondsLeftPadded (final long seconds) {
		if (seconds >= 3600) {
			return String.format(" %d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
		}
		return String.format(" %4d:%02d", (seconds % 3600) / 60, (seconds % 60));
	}

	private static class SortColumnAction implements Runnable {

		private final IMixedMediaDb db;
		private final IDbColumn col;
		private final SortDirection direction;

		public SortColumnAction (final IMixedMediaDb db, final IDbColumn col, final SortDirection direction) {
			this.db = db;
			this.col = col;
			this.direction = direction;
		}

		@Override
		public String toString () {
			return String.format("%s %s", this.col.getHumanName(), this.direction);
		}

		@Override
		public void run () {
			try {
				this.db.setSort(this.col, this.direction);
			}
			catch (final MorriganException e) {
				throw new IllegalStateException(e);
			}
		}

	}

}
