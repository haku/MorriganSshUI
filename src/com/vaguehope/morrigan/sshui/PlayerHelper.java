package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;

public final class PlayerHelper {

	private PlayerHelper () {
		throw new AssertionError();
	}

	public static void shuffleAndEnqueue (final IMediaTrackList<? extends IMediaTrack> db, final List<? extends IMediaTrack> tracks, final Player player) {
		final List<IMediaTrack> shuffeledList = new ArrayList<IMediaTrack>(tracks);
		Collections.shuffle(shuffeledList);
		for (final IMediaTrack track : shuffeledList) {
			player.getQueue().addToQueue(new PlayItem(db, track));
		}
	}

}
