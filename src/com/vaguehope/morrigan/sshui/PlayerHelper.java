package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.ActionListDialog;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerQueue;

public final class PlayerHelper {

	private PlayerHelper () {
		throw new AssertionError();
	}

	public static void shuffleAndEnqueue (final IMediaTrackList<? extends IMediaTrack> db, final List<? extends IMediaTrack> tracks, final Player player) {
		final List<IMediaTrack> shuffeledList = new ArrayList<IMediaTrack>(tracks);
		Collections.shuffle(shuffeledList);
		enqueueAll(db, shuffeledList, player);
	}

	public static void enqueueAll (final IMediaTrackList<? extends IMediaTrack> db, final List<? extends IMediaTrack> tracks, final Player player) {
		final PlayerQueue queue = player.getQueue();
		for (final IMediaTrack track : tracks) {
			queue.addToQueue(new PlayItem(db, track));
		}
	}

	public static Player askWhichPlayer (final GUIScreen gui, final String title, final Player defaultPlayer, final Collection<Player> players) {
		if (defaultPlayer != null) return defaultPlayer;
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

}
