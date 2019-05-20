package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.morrigan.sshui.util.LastActionMessage;
import com.vaguehope.morrigan.tasks.AsyncTask;
import com.vaguehope.sqlitewrapper.DbException;

public class HomeFace extends DefaultFace {

	private static final String HELP_TEXT =
			"       g\tgo to top of list\n" +
			"       G\tgo to end of list\n" +
			" <space>\tplay / pause selected player or play DB\n" +
			"       n\tcreate new DB\n" +
			"       e\tenqueue DB\n" +
			"       /\tsearch DB\n" +
			"<ctrl>+c\tcancel task\n" +
			"       q\tback a page\n" +
			"       h\tthis help text";

	private static final long DATA_REFRESH_MILLIS = 500L;

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final DbHelper dbHelper;

	private final LastActionMessage lastActionMessage = new LastActionMessage();
	private final AtomicReference<String> savedSearchTerm = new AtomicReference<String>();

	private long lastDataRefresh = 0;
	private List<Player> players;
	private List<AsyncTask> tasks;
	private List<MediaListReference> dbs;
	private Object selectedItem;

	public HomeFace (final FaceNavigation actions, final MnContext mnContext) {
		super(actions);
		this.navigation = actions;
		this.mnContext = mnContext;
		this.dbHelper = new DbHelper(this.navigation, mnContext, null, this.lastActionMessage, null);
	}

	private void refreshData () {
		this.players = asList(this.mnContext.getPlayerReader().getPlayers());
		this.tasks = this.mnContext.getAsyncTasksRegister().tasks();
		this.dbs = asList(this.mnContext.getMediaFactory().getAllLocalMixedMediaDbs());
	}

	private void refreshStaleData () {
		final long now = System.nanoTime();
		if (now - this.lastDataRefresh > TimeUnit.MILLISECONDS.toNanos(DATA_REFRESH_MILLIS)) {
			refreshData();
			this.lastDataRefresh = now;
		}
	}

	@Override
	public boolean onInput (final KeyStroke k, final WindowBasedTextGUI gui) throws Exception {
		switch (k.getKeyType()) {
			case ArrowUp:
			case ArrowDown:
				menuMove(k, 1);
				return true;
			case Home:
				menuMoveEnd(VDirection.UP);
				return true;
			case End:
				menuMoveEnd(VDirection.DOWN);
				return true;
			case Enter:
				menuEnter(gui);
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
					case ' ':
						menuClick(gui);
						return true;
					case 'n':
						askNewDb(gui);
						return true;
					case 'e':
						enqueueDb(gui);
						return true;
					case '/':
						askSearch(gui);
						return true;
					case 'c':
						if (k.isCtrlDown()) {
							cancelSelectedTask();
							return true;
						}
						return false;
					default:
				}
			default:
				//LOG.info("kind={} c={} a={} char={}", k.getKind(), k.isCtrlPressed(), k.isAltPressed(), String.valueOf((int) k.getCharacter()));
				return super.onInput(k, gui);
		}
	}

	private void menuMove (final KeyStroke k, final int distance) {
		this.selectedItem = MenuHelper.moveListSelection(this.selectedItem,
				k.getKeyType() == KeyType.ArrowUp ? VDirection.UP : VDirection.DOWN,
				distance,
				this.players, this.dbs, this.tasks);
	}

	private void menuMoveEnd (final VDirection direction) {
		switch (direction) {
			case UP:
				this.selectedItem = MenuHelper.listOfListsGet(0, this.players, this.dbs, this.tasks);
				break;
			case DOWN:
				this.selectedItem = MenuHelper.listOfListsGet(
						MenuHelper.sumSizes(this.players, this.dbs, this.tasks) - 1,
						this.players, this.dbs, this.tasks);
				break;
			default:
		}
	}

	private void menuClick (final WindowBasedTextGUI gui) throws DbException, MorriganException {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof Player) {
			((Player) this.selectedItem).pausePlaying();
		}
		else if (this.selectedItem instanceof MediaListReference) {
			final Player player = getPlayer(gui, "Play DB");
			if (player != null) {
				final IMixedMediaDb db = this.dbHelper.resolveReference((MediaListReference) this.selectedItem);
				playPlayItem(new PlayItem(db, null), player);
			}
		}
		else if (this.selectedItem instanceof AsyncTask) {
			// Do nothing.
		}
		else {
			MessageDialog.showMessageDialog(gui, "Error", "Unknown type: " + this.selectedItem);
		}
	}

	private void menuEnter (final WindowBasedTextGUI gui) throws DbException, MorriganException {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof Player) {
			this.navigation.startFace(new PlayerFace(this.navigation, this.mnContext, (Player) this.selectedItem));
		}
		else if (this.selectedItem instanceof MediaListReference) {
			final IMixedMediaDb db = this.dbHelper.resolveReference((MediaListReference) this.selectedItem);
			final DbFace dbFace = new DbFace(this.navigation, this.mnContext, db, null);
			dbFace.restoreSavedScroll();
			this.navigation.startFace(dbFace);
		}
		else if (this.selectedItem instanceof AsyncTask) {
			// Do nothing.
		}
		else {
			MessageDialog.showMessageDialog(gui, "TODO", "Enter: " + this.selectedItem);
		}
	}

	private void askNewDb (final WindowBasedTextGUI gui) throws MorriganException {
		final String name = new TextInputDialogBuilder()
				.setTitle("New DB")
				.setDescription("Enter name:")
				.setTextBoxSize(new TerminalSize(50, 1))
				.build().showDialog(gui);
		if (name != null && name.length() > 0) {
			this.mnContext.getMediaFactory().createLocalMixedMediaDb(name);
			refreshData();
		}
	}

	private void enqueueDb (final WindowBasedTextGUI gui) throws DbException, MorriganException {
		if (this.selectedItem instanceof MediaListReference) {
			final Player player = getPlayer(gui, "Enqueue DB");
			if (player != null) {
				final IMixedMediaDb db = this.dbHelper.resolveReference((MediaListReference) this.selectedItem);
				enqueuePlayItem(new PlayItem(db, null), player);
			}
		}
	}

	private void askSearch (final WindowBasedTextGUI gui) throws DbException, MorriganException {
		if (this.selectedItem instanceof Player) {
			final IMediaTrackList<? extends IMediaTrack> list = ((Player) this.selectedItem).getCurrentList();
			if (list instanceof IMixedMediaDb) {
				this.dbHelper.askSearch(gui, (IMixedMediaDb) list, this.savedSearchTerm);
			}
		}
		else if (this.selectedItem instanceof MediaListReference) {
			final IMixedMediaDb db = this.dbHelper.resolveReference((MediaListReference) this.selectedItem);
			this.dbHelper.askSearch(gui, db, this.savedSearchTerm);
		}
	}

	private Player getPlayer (final WindowBasedTextGUI gui, final String title) {
		return PlayerHelper.askWhichPlayer(gui, title, null, this.mnContext.getPlayerReader().getPlayers());
	}

	protected void enqueuePlayItem (final PlayItem playItem, final Player player) {
		player.getQueue().addToQueue(playItem);
		// TODO protect against long item names?
		this.lastActionMessage.setLastActionMessage(String.format("Enqueued %s in %s.", playItem, player.getName()));
	}

	protected void playPlayItem (final PlayItem playItem, final Player player) {
		player.loadAndStartPlaying(playItem);
		// TODO protect against long item names?
		this.lastActionMessage.setLastActionMessage(String.format("Playing %s in %s.", playItem, player.getName()));
	}

	@Override
	public void writeScreen (final Screen scr, final TextGraphics tg) {
		refreshStaleData();

		int l = 0;
		tg.putString(0, l++, "Players");
		l = printPlayers(tg, l);

		this.lastActionMessage.drawLastActionMessage(tg, l++);

		tg.putString(0, l++, "DBs");
		l = printDbs(tg, l);

		if (MenuHelper.sizeOf(this.tasks) > 0) {
			l++;
			tg.putString(0, l++, "Background Tasks");
			l = printTasks(tg, l);
			l++;
		}
	}

	private int printPlayers (final TextGraphics tg, final int initialLine) {
		int l = initialLine;
		if (this.players.size() > 0) {
			for (final Player p : this.players) {
				if (p.isDisposed()) continue;
				final String line = String.format("%s %s %s",
						p.getName(), PrintingThingsHelper.playerStateMsg(p), PrintingThingsHelper.playingItemTitle(p));
				if (p.equals(this.selectedItem)) {
					tg.putString(1, l++, line, SGR.REVERSE);
				}
				else {
					tg.putString(1, l++, line);
				}
			}
		}
		else {
			tg.putString(1, l++, "(no players)");
		}
		return l;
	}

	private int printTasks (final TextGraphics tg, final int initialLine) {
		int l = initialLine;
		for (final AsyncTask task : this.tasks) {
			final String summmary = task.summary();
			for (final String line : summmary.split("\\r?\\n")) {
				if (task.equals(this.selectedItem)) {
					tg.putString(1, l++, line, SGR.REVERSE);
				}
				else {
					tg.putString(1, l++, line);
				}
			}
		}
		return l;
	}

	private int printDbs (final TextGraphics tg, final int initialLine) {
		int l = initialLine;
		if (this.dbs.size() > 0) {
			for (final MediaListReference db : this.dbs) {
				if (db.equals(this.selectedItem)) {
					tg.putString(1, l++, db.getTitle(), SGR.REVERSE);
				}
				else {
					tg.putString(1, l++, db.getTitle());
				}
			}
		}
		else {
			tg.putString(1, l++, "(no DBs)");
		}
		return l;
	}

	private void cancelSelectedTask() {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof AsyncTask) {
			final AsyncTask task = (AsyncTask) this.selectedItem;
			task.cancel();
			this.lastActionMessage.setLastActionMessage(String.format("Cancelled: %s", task.title()));
		}
	}

	private static <T> List<T> asList (final Collection<T> c) {
		if (c instanceof List<?>) return (List<T>) c;
		return new ArrayList<T>(c);
	}

}
