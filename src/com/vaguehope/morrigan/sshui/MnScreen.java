package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.gui.DefaultBackgroundRenderer;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.player.LocalPlayer;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.term.SshScreen;
import com.vaguehope.morrigan.util.TimeHelper;

public class MnScreen extends SshScreen {

	private static final Logger LOG = LoggerFactory.getLogger(MnScreen.class);

	private final MnContext mnContext;
	private GUIScreen gui;

	private List<Player> players;
	private Object selectedItem;

	private List<MediaListReference> dbs;

	public MnScreen (final String name, final MnContext mnContext, final Environment env, final Terminal terminal, final ExitCallback callback) {
		super(name, env, terminal, callback);
		this.mnContext = mnContext;
	}

	@Override
	protected void initScreen (final Screen scr) {
		scr.setCursorPosition(null);
		this.gui = new GUIScreen(scr);
		this.gui.setBackgroundRenderer(new DefaultBackgroundRenderer("Morrigan desu~"));
	}

	@Override
	protected boolean onInput (final Key k) {
		LOG.info("kind={} char={}", k.getKind(), Character.getNumericValue(k.getCharacter()));

		if (k.getKind() == Kind.NormalKey && k.getCharacter() == 'q') {
			scheduleQuit();
			return false; // We are quitting.  Do not try and update UI.
		}
		return onKey(k);
	}

	private boolean onKey (final Key k) {
		switch (k.getKind()) {
			case ArrowUp:
			case ArrowDown:
				menuMove(k);
				return true;
			case Enter:
				menuEnter();
				return true;
			case NormalKey:
				switch (k.getCharacter()) {
					case '\n':
						menuEnter();
						return true;
					case ' ':
						menuClick();
						return true;
					default:
				}
			default:
				return false;
		}
	}

	private void menuMove (final Key k) {
		final int limit = sizeOf(this.players) + sizeOf(this.dbs);

		int i = -1;
		if (this.selectedItem != null) {
			i = this.players.indexOf(this.selectedItem);
			if (i < 0) {
				i = this.dbs.indexOf(this.selectedItem);
				if (i >= 0) i += sizeOf(this.players);
			}
		}

		if (i < 0) {
			i = 0;
		}
		else if (k.getKind() == Kind.ArrowUp) {
			i--;
			if (i < 0) i = 0;
		}
		else {
			i++;
			if (i >= limit) i = limit - 1;
		}

		if (i < sizeOf(this.players)) {
			this.selectedItem = listGet(this.players, i);
		}
		else {
			this.selectedItem = listGet(this.dbs, i - sizeOf(this.players));
		}
	}

	private void menuClick () {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof Player) {
			((Player) this.selectedItem).pausePlaying();
		}
		else if (this.selectedItem instanceof MediaListReference) {
			MessageBox.showMessageBox(this.gui, "TODO", "Clicked: " + this.selectedItem);
		}
		else {
			MessageBox.showMessageBox(this.gui, "Error", "Unknown type: " + this.selectedItem);
		}
	}

	private void menuEnter () {
		if (this.selectedItem == null) return;
		MessageBox.showMessageBox(this.gui, "TODO", "Enter: " + this.selectedItem);
	}

	@Override
	protected void writeScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, "Players");
		l = printPlayers(w, l);
		l++;
		w.drawString(0, l++, "DBs");
		l = printDbs(w, l);
	}

	private int printPlayers (final ScreenWriter w, final int initialLine) {
		int l = initialLine;
		this.players = asList(this.mnContext.getPlayerReader().getPlayers());
		if (this.players.size() > 0) {
			for (final Player p : this.players) {
				final String line = String.format("%s\t%s %s %s", p.getId(), p.getName(), playerStateMsg(p), playingItemTitle(p));
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

	private static String playerStateMsg (final Player p) {
		final StringBuilder msg = new StringBuilder();
		switch (p.getPlayState()) {
			case PLAYING:
				msg.append("Playing");
				break;
			case PAUSED:
				msg.append("Paused");
				break;
			case LOADING:
				msg.append("Loading");
				break;
			case STOPPED:
				msg.append("Stopped");
				break;
			default:
				msg.append("Unknown");
				break;
		}

		final long currentPosition = p.getCurrentPosition();
		if (currentPosition >= 0) {
			final int currentTrackDuration = p.getCurrentTrackDuration();
			msg.append(" ").append(TimeHelper.formatTimeSeconds(currentPosition));
			if (currentTrackDuration > 0) {
				msg.append(" of ").append(TimeHelper.formatTimeSeconds(currentTrackDuration));
			}
		}

		if (p instanceof LocalPlayer && ((LocalPlayer) p).isProxy()) msg.append(" @ ").append(p.getName());
		msg.append(".");

		return msg.toString();
	}

	private static String playingItemTitle (final Player p) {
		final PlayItem currentItem = p.getCurrentItem();
		return currentItem != null && currentItem.hasTrack() ? currentItem.getTrack().getTitle() : "";
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

	private static int sizeOf (final Collection<?> c) {
		return c != null ? c.size() : 0;
	}

	private static <T> List<T> asList (final Collection<T> c) {
		if (c instanceof List<?>) return (List<T>) c;
		return new ArrayList<T>(c);
	}

	private static <T> T listGet (final List<T> list, final int i) {
		if (list == null) return null;
		return list.get(i);
	}

}
