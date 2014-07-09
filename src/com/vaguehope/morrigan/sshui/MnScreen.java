package com.vaguehope.morrigan.sshui;

import java.util.Collection;

import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.player.LocalPlayer;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.term.SshScreen;
import com.vaguehope.morrigan.util.TimeHelper;

public class MnScreen extends SshScreen {

	private final MnContext mnContext;

	public MnScreen (final String name, final MnContext mnContext, final Environment env, final Terminal terminal, final ExitCallback callback) {
		super(name, env, terminal, callback);
		this.mnContext = mnContext;
	}

	@Override
	protected boolean readInput (final Key k) {
		if (k.getKind() == Kind.NormalKey) {
			if (k.getCharacter() == 'q') {
				scheduleQuit();
				return false; // We are quitting.  Do not try and update UI.
			}
			return true;
		}
		return false;
	}

	@Override
	protected void writeScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, "Players");
		l += printPlayers(w, l);
		l++;
		w.drawString(0, l++, "DBs");
		l += printDbs(w, l);
	}

	private int printPlayers (final ScreenWriter w, final int initialLine) {
		int l = initialLine;
		final Collection<Player> players = this.mnContext.getPlayerReader().getPlayers();
		if (players.size() > 0) {
			for (final Player p : players) {
				w.drawString(1, l++, String.format("%s\t%s %s %s", p.getId(), p.getName(), playerStateMsg(p), playingItemTitle(p)));
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
		final Collection<MediaListReference> dbs = this.mnContext.getMediaFactory().getAllLocalMixedMediaDbs();
		if (dbs.size() > 0) {
			for (final MediaListReference db : dbs) {
				w.drawString(1, l++, db.getTitle());
			}
		}
		else {
			w.drawString(1, l++, "(no DBs)");
		}
		return l;
	}

}
