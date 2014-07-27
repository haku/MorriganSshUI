package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.ActionListDialog;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal.Color;
import com.googlecode.lanterna.terminal.TerminalSize;
import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaItemStorageLayer.SortDirection;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMixedMediaItem;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.JumpToDialog.JumpResult;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
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
			"      e\tenqueue item\n" +
			"      E\tenqueue DB\n" +
			"      t\ttag editor\n" +
			"      v\tselect\n" +
			"      w\tcopy file\n" +
			"      d\ttoggle enabled\n" +
			"      r\trefresh query\n" +
			"     f6\tDB properties\n" +
			"      q\tback a page\n" +
			"      h\tthis help text";

	private static final long LAST_ACTION_MESSAGE_DURATION_MILLIS = 5000L;

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final IMixedMediaDb db;
	private final Player defaultPlayer;

	private final AtomicReference<File> savedInitialDir = new AtomicReference<File>();

	private final TextGuiUtils textGuiUtils = new TextGuiUtils();
	private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	private final Set<IMixedMediaItem> selectedItems = new HashSet<IMixedMediaItem>();
	private final AtomicReference<String> savedSearchTerm = new AtomicReference<String>();

	private List<IMixedMediaItem> mediaItems;
	private int selectedItemIndex = -1;
	private int scrollTop = 0;
	private int pageSize = 1;
	private String lastActionMessage = null;
	private long lastActionMessageTime = 0;
	private String itemDetailsBar = "";
	private IMixedMediaItem itemDetailsBarItem;

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final MediaListReference listReference) throws DbException, MorriganException {
		this.navigation = navigation;
		this.mnContext = mnContext;

		if (listReference.getType() == MediaListReference.MediaListType.LOCALMMDB) {
			this.db = mnContext.getMediaFactory().getLocalMixedMediaDb(listReference.getIdentifier());
			this.db.read();
			refreshData();
		}
		else {
			this.db = null;
		}

		this.defaultPlayer = null;
	}

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final IMixedMediaDb db, final Player defaultPlayer) throws MorriganException {
		this.navigation = navigation;
		this.mnContext = mnContext;
		this.db = db;
		this.defaultPlayer = defaultPlayer;
		refreshData();
	}

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final IMixedMediaDb db, final Player defaultPlayer, final IMediaTrack revealItem) throws MorriganException {
		this(navigation, mnContext, db, defaultPlayer);
		revealItem(revealItem);
	}

	private void refreshData () throws MorriganException {
		this.db.read();
		this.mediaItems = this.db.getMediaItems();
	}

	protected void setLastActionMessage (final String lastActionMessage) {
		this.lastActionMessage = lastActionMessage;
		this.lastActionMessageTime = System.currentTimeMillis();
	}

	private void updateItemDetailsBar () throws MorriganException {
		if (this.selectedItems.size() > 0) {
			this.itemDetailsBar = String.format("%s selected.", this.selectedItems.size());
			this.itemDetailsBarItem = null;
		}
		else {
			final IMixedMediaItem item = getSelectedItem();
			if (this.itemDetailsBarItem != null && this.itemDetailsBarItem.equals(item)) return;
			this.itemDetailsBarItem = item;
			this.itemDetailsBar = PrintingThingsHelper.summariseItemTags(this.db, item);
		}
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) throws DbException, MorriganException {

		// TODO
		// - jump back all searches? (Q) (go back to last player / non search Face?).

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
			case F6:
				this.navigation.startFace(new DbPropertiesFace(this.navigation, this.mnContext, this.db));
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
			default:
				return false;
		}
	}

	private void menuMove (final Key k, final int distance) throws MorriganException {
		this.selectedItemIndex = MenuHelper.moveListSelectionIndex(this.selectedItemIndex,
				k.getKind() == Kind.ArrowUp || k.getKind() == Kind.PageUp
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

	private void revealItem (final IMediaTrack track) throws MorriganException {
		final int i = this.mediaItems.indexOf(track);
		if (i >= 0) {
			setSelectedItem(i);
		}
		else {
			setLastActionMessage("Item not in view: " + track); // TODO open new DbFace here?
		}
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

	private Player getPlayer (final GUIScreen gui, final String title) {
		if (this.defaultPlayer != null) return this.defaultPlayer;
		final Collection<Player> players = this.mnContext.getPlayerReader().getPlayers();
		if (players == null || players.size() < 1) {
			return null;
		}
		else if (players.size() == 1) {
			return players.iterator().next();
		}
		else {
			final AtomicReference<Player> ret = new AtomicReference<Player>();
			final List<Action> actions = new ArrayList<Action>();
			for (final Player player : players) {
				actions.add(new Action() {
					@Override
					public String toString () {
						return player.getName();
					}

					@Override
					public void doAction () {
						ret.set(player);
					}
				});
			}
			ActionListDialog.showActionListDialog(gui, title, "Select player",
					actions.toArray(new Action[actions.size()]));
			return ret.get();
		}
	}

	private void enqueueDb (final GUIScreen gui) {
		final Player player = getPlayer(gui, "Enqueue DB");
		if (player != null) enqueuePlayItem(new PlayItem(this.db, null), player);
	}

	protected void enqueuePlayItem (final PlayItem playItem, final Player player) {
		player.getQueue().addToQueue(playItem);
		// TODO protect against long item names?
		setLastActionMessage(String.format("Enqueued %s in %s.", playItem, player.getName()));
	}

	private void enqueueSelection (final GUIScreen gui) {
		enqueueItems(gui, getSelectedItems());
	}

	private void enqueueItems (final GUIScreen gui, final List<? extends IMediaTrack> tracks) {
		final Player player = getPlayer(gui, String.format("Enqueue %s items", tracks.size()));
		if (player == null) return;
		PlayerHelper.enqueueAll(this.db, tracks, player);
		setLastActionMessage(String.format("Enqueued %s items in %s.", tracks.size(), player.getName()));
	}

	private void shuffleAndEnqueue (final GUIScreen gui, final List<? extends IMediaTrack> tracks) {
		final Player player = getPlayer(gui, "Shuffle and enqueue");
		if (player != null) PlayerHelper.shuffleAndEnqueue(this.db, tracks, player);
	}

	private void showEditTagsForSelectedItem (final GUIScreen gui) throws MorriganException {
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

	private void askExportSelection (final GUIScreen gui) {
		this.savedInitialDir.compareAndSet(null, new File(System.getProperty("user.home")));
		final List<IMixedMediaItem> items = getSelectedItems();
		if (items.size() < 1) return;
		final File dir = DirDialog.show(gui, String.format("Export %s tracks", items.size()), "Export", this.savedInitialDir);
		if (dir == null) return;
		final MorriganTask task = this.mnContext.getMediaFactory().getMediaFileCopyTask(this.db, items, dir);
		this.mnContext.getAsyncTasksRegister().scheduleTask(task);
		setLastActionMessage(String.format("Started copying %s tracks ...", items.size()));
	}

	private void toggleEnabledSelection () throws MorriganException {
		final List<IMixedMediaItem> items = getSelectedItems();
		for (final IMixedMediaItem item : items) {
			this.db.setItemEnabled(item, !item.isEnabled());
		}
		setLastActionMessage(String.format("Toggled enabled on %s items.", items.size()));
	}

	private void askSortColumn (final GUIScreen gui) {
		final List<IDbColumn> cols = this.db.getDbLayer().getMediaTblColumns();
		final List<Action> actions = new ArrayList<Action>();
		for (final IDbColumn col : cols) {
			if (col.getHumanName() != null) {
				actions.add(new SortColumnAction(this.db, col, SortDirection.ASC));
				actions.add(new SortColumnAction(this.db, col, SortDirection.DESC));
			}
		}
		ActionListDialog.showActionListDialog(gui, "Sort Order", "Current: " + PrintingThingsHelper.sortSummary(this.db),
				actions.toArray(new Action[actions.size()]));
	}

	private void askSearch (final GUIScreen gui) throws DbException, MorriganException {
		final JumpResult res = JumpToDialog.show(gui, this.db, this.savedSearchTerm);
		if (res == null) return;
		switch (res.getType()) {
			case ENQUEUE:
				enqueueItems(gui, res.getTracks());
				break;
			case REVEAL:
				revealItem(res.getTrack());
				break;
			case SHUFFLE_AND_ENQUEUE:
				shuffleAndEnqueue(gui, res.getTracks());
				break;
			case OPEN_VIEW:
				this.navigation.startFace(new DbFace(this.navigation, this.mnContext,
						this.mnContext.getMediaFactory().getLocalMixedMediaDb(this.db.getDbPath(), res.getText()),
						this.defaultPlayer));
				break;
			default:
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		if (this.db != null) {
			writeDbToScreen(scr, w);
		}
		else {
			w.drawString(0, 0, "Unable to show null db.");
		}
	}

	private static final ScreenCharacterStyle[] UNSELECTED = new ScreenCharacterStyle[] {};
	private static final ScreenCharacterStyle[] SELECTED = new ScreenCharacterStyle[] { ScreenCharacterStyle.Reverse };

	private void writeDbToScreen (final Screen scr, final ScreenWriter w) {
		final TerminalSize terminalSize = scr.getTerminalSize();
		int l = 0;

		w.drawString(0, l++, String.format("DB %s: %s   %s",
				this.db.getListName(),
				PrintingThingsHelper.dbSummary(this.db),
				PrintingThingsHelper.sortSummary(this.db)));

		if (this.lastActionMessage != null && System.currentTimeMillis() - this.lastActionMessageTime > LAST_ACTION_MESSAGE_DURATION_MILLIS) {
			this.lastActionMessage = null;
		}
		if (this.lastActionMessage != null && this.lastActionMessage.length() > 0) {
			w.drawString(0, l++, String.format(">> %s", this.lastActionMessage));
		}
		else {
			l++;
		}

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
			final ScreenCharacterStyle[] style = i == this.selectedItemIndex ? SELECTED : UNSELECTED;
			final String name = String.valueOf(item);
			w.drawString(1, l, name, style);

			final ScreenCharacterStyle[] flagStyle = this.selectedItems.contains(item) ? SELECTED : UNSELECTED;
			if (item.isMissing()) {
				scr.putString(0, l, "m", Color.YELLOW, Color.DEFAULT, flagStyle);
				scr.putString(name.length() + 2, l, "(missing)", Color.YELLOW, Color.DEFAULT);
			}
			else if (!item.isEnabled()) {
				scr.putString(0, l, "d", Color.RED, Color.DEFAULT, flagStyle);
				scr.putString(name.length() + 2, l, "(disabled)", Color.RED, Color.DEFAULT);
			}
			else if (flagStyle.length > 0) {
				scr.putString(0, l, ">", Color.DEFAULT, Color.DEFAULT, flagStyle);
			}

			// Last played column.
			if (item.getDateLastPlayed() != null) {
				final String lastPlayed = String.format(" %s", this.dateFormat.format(item.getDateLastPlayed()));
				w.drawString(colRightLastPlayed - lastPlayed.length(), l, lastPlayed, style);
			}

			// Play count column.
			if (item.getStartCount() > 0 || item.getEndCount() > 0) {
				final String counts = String.format("%4s/%-3s", item.getStartCount(), item.getEndCount());
				w.drawString(colRightPlayCount - counts.length(), l, counts, style);
			}

			// Duration column.
			final String dur = formatTimeSecondsLeftPadded(item.getDuration());
			w.drawString(colRightDuration - dur.length(), l, dur, style);

			l++;
		}

		this.textGuiUtils.drawTextRowWithBg(scr, terminalSize.getRows() - 1, this.itemDetailsBar, Color.WHITE, Color.BLUE, ScreenCharacterStyle.Bold);
		scr.putString(terminalSize.getColumns() - 3, terminalSize.getRows() - 1,
				PrintingThingsHelper.scrollSummary(this.mediaItems.size(), this.pageSize, this.scrollTop),
				Color.WHITE, Color.BLUE, ScreenCharacterStyle.Bold);
	}

	private static String formatTimeSecondsLeftPadded (final long seconds) {
		if (seconds >= 3600) {
			return String.format(" %d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, (seconds % 60));
		}
		return String.format(" %4d:%02d", (seconds % 3600) / 60, (seconds % 60));
	}

	private static class SortColumnAction implements Action {

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
		public void doAction () {
			try {
				this.db.setSort(this.col, this.direction);
			}
			catch (final MorriganException e) {
				throw new IllegalStateException(e);
			}
		}

	}

}
