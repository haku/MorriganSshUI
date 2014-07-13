package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.MessageBox;
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

public class HomeFace implements Face {

	private static final Logger LOG = LoggerFactory.getLogger(HomeFace.class);

	private final FaceNavigation navigation;
	private final MnContext mnContext;

	private List<Player> players;
	private List<MediaListReference> dbs;
	private Object selectedItem;

	public HomeFace (final FaceNavigation actions, final MnContext mnContext) {
		this.navigation = actions;
		this.mnContext = mnContext;
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) throws DbException, MorriganException {

		// TODO
		// - New DB.
		// - Scrolling.
		// - help screen.

		switch (k.getKind()) {
			case ArrowUp:
			case ArrowDown:
				menuMove(k, 1);
				return true;
			case Enter:
				menuEnter(gui);
				return true;
			case NormalKey:
				switch (k.getCharacter()) {
					case ' ':
						menuClick(gui);
						return true;
					case 'q':
						return this.navigation.backOneLevel();
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
			this.navigation.startFace(new PlayerFace(this.navigation, (Player) this.selectedItem));
		}
		else if (this.selectedItem instanceof MediaListReference) {
			this.navigation.startFace(new DbFace(this.navigation, this.mnContext, (MediaListReference) this.selectedItem));
		}
		else {
			MessageBox.showMessageBox(gui, "TODO", "Enter: " + this.selectedItem);
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, "Players");
		l = printPlayers(w, l);
		l++;
		w.drawString(0, l++, "DBs");
		l = printDbs(w, l);

		// TODO draw AsyncTasksRegister.
	}

	private int printPlayers (final ScreenWriter w, final int initialLine) {
		int l = initialLine;
		this.players = asList(this.mnContext.getPlayerReader().getPlayers());
		if (this.players.size() > 0) {
			for (final Player p : this.players) {
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

	private int printDbs (final ScreenWriter w, final int initialLine) {
		int l = initialLine;
		this.dbs = asList(this.mnContext.getMediaFactory().getAllLocalMixedMediaDbs());
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
