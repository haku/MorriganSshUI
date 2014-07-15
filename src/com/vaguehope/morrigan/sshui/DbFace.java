package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.ActionListDialog;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.gui.dialog.TextInputDialog;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaItemStorageLayer.SortDirection;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMixedMediaItem;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.sqlitewrapper.DbException;

public class DbFace extends DefaultFace {

	private static final String HELP_TEXT =
			"      g\tgo to top of list\n" +
			"      G\tgo to end of list\n" +
			"      /\tsearch DB\n" +
			"      o\tsort order\n" +
			"      e\tenqueue item\n" +
			"      r\trefresh query\n" +
			"     f6\tDB properties\n" +
			"      h\tthis help text";

	private static final int MAX_SEARCH_RESULTS = 200;
	private static final long LAST_ACTION_MESSAGE_DURATION_MILLIS = 5000L;

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final IMixedMediaDb db;
	private final Player defaultPlayer;
	private final String searchTerm;

	private List<IMixedMediaItem> mediaItems;
	private int selectedItemIndex = -1;
	private int queueScrollTop = 0;
	private int pageSize = 1;
	private String lastActionMessage = null;
	private long lastActionMessageTime = 0;

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final MediaListReference listReference) throws DbException, MorriganException {
		this(navigation, mnContext, listReference, null);
	}

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final MediaListReference listReference, final String searchTerm) throws DbException, MorriganException {
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
		this.searchTerm = searchTerm;
	}

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final IMixedMediaDb db, final Player defaultPlayer, final String searchTerm) throws DbException {
		this.navigation = navigation;
		this.mnContext = mnContext;
		this.db = db;
		this.defaultPlayer = defaultPlayer;
		this.searchTerm = searchTerm;
		refreshData();
	}

	private void refreshData () throws DbException {
		if (this.searchTerm != null) {
			this.mediaItems = this.db.simpleSearch(this.searchTerm, MAX_SEARCH_RESULTS);
		}
		else {
			this.mediaItems = this.db.getMediaItems();
		}
	}

	protected void setLastActionMessage (final String lastActionMessage) {
		this.lastActionMessage = lastActionMessage;
		this.lastActionMessageTime = System.currentTimeMillis();
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
					case 'e':
						enqueueItem(gui);
						return true;
					case 'o':
						askSortColumn(gui);
						return true;
					case '/':
					case 's':
					case 'f':
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

	private void menuMove (final Key k, final int distance) {
		this.selectedItemIndex = MenuHelper.moveListSelectionIndex(this.selectedItemIndex,
				k.getKind() == Kind.ArrowUp || k.getKind() == Kind.PageUp
						? VDirection.UP
						: VDirection.DOWN,
				distance,
				this.mediaItems);
	}

	private void menuMoveEnd (final VDirection direction) {
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
	}

	private IMixedMediaItem getSelectedItem () {
		if (this.selectedItemIndex < 0) return null;
		return this.mediaItems.get(this.selectedItemIndex);
	}

	private void enqueueItem (final GUIScreen gui) {
		final IMixedMediaItem item = getSelectedItem();
		if (item == null) return;

		if (this.defaultPlayer != null) {
			enqueueItem(item, this.defaultPlayer);
			return;
		}

		final Collection<Player> players = this.mnContext.getPlayerReader().getPlayers();
		if (players == null || players.size() < 1) {
			MessageBox.showMessageBox(gui, "Players", "No players available.");
		}
		else if (players.size() == 1) {
			enqueueItem(item, players.iterator().next());
		}
		else {
			final List<Action> actions = new ArrayList<Action>();
			for (final Player player : players) {
				actions.add(new Action() {
					@Override
					public String toString () {
						return player.getName();
					}

					@Override
					public void doAction () {
						enqueueItem(item, player);
					}
				});
			}
			ActionListDialog.showActionListDialog(gui, "Enqueue", "Select player",
					actions.toArray(new Action[actions.size()]));
		}
	}

	protected void enqueueItem (final IMixedMediaItem item, final Player player) {
		player.getQueue().addToQueue(new PlayItem(this.db, item));
		// TODO protect against long item names?
		setLastActionMessage(String.format("Enqueued %s in %s.", item, player.getName()));
	}

	private void askSortColumn (final GUIScreen gui) {
		if (this.searchTerm != null) return; // TODO Sort search results.

		final List<IDbColumn> cols = this.db.getDbLayer().getMediaTblColumns();
		final List<Action> actions = new ArrayList<Action>();
		for (final IDbColumn col : cols) {
			if (col.getHumanName() != null) {
				actions.add(new SortColumnAction(this.db, col, SortDirection.ASC));
				actions.add(new SortColumnAction(this.db, col, SortDirection.DESC));
			}
		}
		ActionListDialog.showActionListDialog(gui, "Sort Order", "Current: " + PlayerHelper.sortSummary(this.db),
				actions.toArray(new Action[actions.size()]));
	}

	private void askSearch (final GUIScreen gui) throws DbException {
		final String term = TextInputDialog.showTextInputBox(gui, "Search", "",
				this.searchTerm != null ? this.searchTerm : "", 50);
		if (term != null) {
			this.navigation.startFace(new DbFace(this.navigation, this.mnContext, this.db, this.defaultPlayer, term));
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		if (this.db != null) {
			writeDbToScreen(scr, w);
		}
		else {
			w.drawString(0, 0, "Unable to show " + this.db.getListName());
		}
	}

	private void writeDbToScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;

		if (this.searchTerm != null) {
			w.drawString(0, l++, String.format("DB %s: search '%s'",
					this.db.getListName(), this.searchTerm));
		}
		else {
			w.drawString(0, l++, String.format("DB %s: %s   %s",
					this.db.getListName(), PlayerHelper.dbSummary(this.db), PlayerHelper.sortSummary(this.db)));
		}

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
		if (this.selectedItemIndex >= 0) {
			if (this.selectedItemIndex - this.queueScrollTop >= this.pageSize) {
				this.queueScrollTop = this.selectedItemIndex - this.pageSize + 1;
			}
			else if (this.selectedItemIndex < this.queueScrollTop) {
				this.queueScrollTop = this.selectedItemIndex;
			}
		}

		for (int i = this.queueScrollTop; i < this.mediaItems.size(); i++) {
			if (i > this.queueScrollTop + this.pageSize) break;
			final IMixedMediaItem item = this.mediaItems.get(i);
			if (i == this.selectedItemIndex) {
				w.drawString(1, l++, String.valueOf(item), ScreenCharacterStyle.Reverse);
			}
			else {
				w.drawString(1, l++, String.valueOf(item));
			}
		}
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
