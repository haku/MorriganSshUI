package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.gui.dialog.TextInputDialog;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.sqlitewrapper.DbException;

public class HomeFace extends DefaultFace {

	private static final String HELP_TEXT =
			"      g\tgo to top of list\n" +
			"      G\tgo to end of list\n" +
			"<space>\tplay / pause selected player\n" +
			"      n\tcreate new DB\n" +
			"      q\tback a page\n" +
			"      h\tthis help text";

	private static final long DATA_REFRESH_MILLIS = 500L;

	private final FaceNavigation navigation;
	private final MnContext mnContext;

	private long lastDataRefresh = 0;
	private List<Player> players;
	private List<String> tasks;
	private List<MediaListReference> dbs;
	private Object selectedItem;


	public HomeFace (final FaceNavigation actions, final MnContext mnContext) {
		this.navigation = actions;
		this.mnContext = mnContext;
	}

	private void refreshData () {
		this.players = asList(this.mnContext.getPlayerReader().getPlayers());
		this.tasks = Arrays.asList(this.mnContext.getAsyncTasksRegister().reportIndiviually());
		this.dbs = asList(this.mnContext.getMediaFactory().getAllLocalMixedMediaDbs());
	}

	private void refreshStaleData() {
		final long now = System.nanoTime();
		if (now - this.lastDataRefresh > TimeUnit.MILLISECONDS.toNanos(DATA_REFRESH_MILLIS)) {
			refreshData();
			this.lastDataRefresh = now;
		}
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) throws DbException, MorriganException {

		// TODO
		// - New DB.
		// - Scrolling.

		switch (k.getKind()) {
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
					case ' ':
						menuClick(gui);
						return true;
					case 'n':
						askNewDb(gui);
						return true;
					default:
				}
			default:
				//LOG.info("kind={} c={} a={} char={}", k.getKind(), k.isCtrlPressed(), k.isAltPressed(), String.valueOf((int) k.getCharacter()));
				return false;
		}
	}

	private void menuMove (final Key k, final int distance) {
		this.selectedItem = MenuHelper.moveListSelection(this.selectedItem,
				k.getKind() == Kind.ArrowUp ? VDirection.UP : VDirection.DOWN,
				distance,
				this.players, this.dbs);
	}

	private void menuMoveEnd (final VDirection direction) {
		switch (direction) {
			case UP:
				this.selectedItem = MenuHelper.listGet(this.players, 0);
				break;
			case DOWN:
				this.selectedItem = MenuHelper.listGet(this.dbs, MenuHelper.sizeOf(this.dbs) - 1);
				break;
			default:
		}
	}

	private void menuClick (final GUIScreen gui) {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof Player) {
			((Player) this.selectedItem).pausePlaying();
		}
		else if (this.selectedItem instanceof MediaListReference) {
//			MessageBox.showMessageBox(gui, "TODO", "Clicked: " + this.selectedItem);
		}
		else {
			MessageBox.showMessageBox(gui, "Error", "Unknown type: " + this.selectedItem);
		}
	}

	private void menuEnter (final GUIScreen gui) throws DbException, MorriganException {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof Player) {
			this.navigation.startFace(new PlayerFace(this.navigation, this.mnContext, (Player) this.selectedItem));
		}
		else if (this.selectedItem instanceof MediaListReference) {
			this.navigation.startFace(new DbFace(this.navigation, this.mnContext, (MediaListReference) this.selectedItem));
		}
		else {
			MessageBox.showMessageBox(gui, "TODO", "Enter: " + this.selectedItem);
		}
	}

	private void askNewDb (final GUIScreen gui) throws MorriganException {
		final String name = TextInputDialog.showTextInputBox(gui, "New DB", "Enter name:", "", 50);
		if (name != null && name.length() > 0) {
			this.mnContext.getMediaFactory().createLocalMixedMediaDb(name);
			refreshData();
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		refreshStaleData();

		int l = 0;
		w.drawString(0, l++, "Players");
		l = printPlayers(w, l);
		l++;

		if(MenuHelper.sizeOf(this.tasks) > 0) {
			w.drawString(0, l++, "Background Tasks");
			l = printTasks(w, l);
			l++;
		}

		w.drawString(0, l++, "DBs");
		l = printDbs(w, l);
	}

	private int printPlayers (final ScreenWriter w, final int initialLine) {
		int l = initialLine;
		if (this.players.size() > 0) {
			for (final Player p : this.players) {
				if (p.isDisposed()) continue;
				final String line = String.format("%s\t%s %s %s",
						p.getId(), p.getName(), PlayerHelper.playerStateMsg(p), PlayerHelper.playingItemTitle(p));
				if (p.equals(this.selectedItem)) {
					w.drawString(1, l++, line, ScreenCharacterStyle.Reverse);
				}
				else {
					w.drawString(1, l++, line);
				}
			}
		}
		else {
			w.drawString(1, l++, "(no players)");
		}
		return l;
	}

	private int printTasks (final ScreenWriter w, final int initialLine) {
		int l = initialLine;
		for (final String task : this.tasks) {
			for (final String line : task.split("\\r?\\n")) {
				w.drawString(1, l++, line);
			}
		}
		return l;
	}

	private int printDbs (final ScreenWriter w, final int initialLine) {
		int l = initialLine;
		if (this.dbs.size() > 0) {
			for (final MediaListReference db : this.dbs) {
				if (db.equals(this.selectedItem)) {
					w.drawString(1, l++, db.getTitle(), ScreenCharacterStyle.Reverse);
				}
				else {
					w.drawString(1, l++, db.getTitle());
				}
			}
		}
		else {
			w.drawString(1, l++, "(no DBs)");
		}
		return l;
	}

	private static <T> List<T> asList (final Collection<T> c) {
		if (c instanceof List<?>) return (List<T>) c;
		return new ArrayList<T>(c);
	}

}
