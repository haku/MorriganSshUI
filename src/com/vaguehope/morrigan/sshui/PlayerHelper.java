package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

}
