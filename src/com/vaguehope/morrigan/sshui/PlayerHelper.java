package com.vaguehope.morrigan.sshui;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.DurationData;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.MediaTag;
import com.vaguehope.morrigan.player.LocalPlayer;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerQueue;
import com.vaguehope.morrigan.util.TimeHelper;

public class PlayerHelper {

	private static final Logger LOG = LoggerFactory.getLogger(PlayerHelper.class);

	public static String playerStateMsg (final Player p) {
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

	public static String playingItemTitle (final Player p) {
		final PlayItem currentItem = p.getCurrentItem();
		return currentItem != null && currentItem.hasTrack() ? currentItem.getTrack().getTitle() : "";
	}

	public static String summariseTags (final Player player) {
		final PlayItem playItem = player.getCurrentItem();
		if (playItem != null && playItem.hasTrack()) {
			final IMediaTrackList<? extends IMediaTrack> list = player.getCurrentList();
			if (list != null) {
				try {
					final List<MediaTag> tags = list.getTags(playItem.getTrack()); // TODO cache this?
					return join(tags, ", ");
				}
				catch (final MorriganException e) {
					LOG.warn("Failed to read tags: " + playItem, e);
					return "(tags unavailable)";
				}
			}
		}
		return "";
	}

	public static String queueSummary (final PlayerQueue queue) {
		final int size = queue.getQueueList().size();
		if (size == 0) return "Queue is empty.";
		final DurationData d = queue.getQueueTotalDuration();
		return String.format("Queue: %s items totaling %s%s.",
				size,
				d.isComplete() ? "" : "more than ",
				TimeHelper.formatTimeSeconds(d.getDuration()));
	}

	public static String dbSummary (final IMixedMediaDb db) {
		final StringBuilder msg = new StringBuilder();
		msg.append(db.getCount());
		msg.append(" items totaling ");
		final DurationData d = db.getTotalDuration();
		if (!d.isComplete()) {
			msg.append("more than ");
		}
		msg.append(TimeHelper.formatTimeSeconds(d.getDuration()));
		msg.append(".");
		return msg.toString();
	}

	public static String sortSummary (final IMixedMediaDb db) {
		IDbColumn col = db.getSort();
		return String.format("%s, %s.",
				col != null ? col.getHumanName() : "(unknown)",
				db.getSortDirection());
	}

	private static String join (final Collection<?> arr, final String sep) {
		final StringBuilder s = new StringBuilder();
		for (final Object obj : arr) {
			if (s.length() > 0) s.append(sep);
			s.append(obj.toString());
		}
		return s.toString();
	}

}
