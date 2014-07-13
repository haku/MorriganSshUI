package com.vaguehope.morrigan.sshui;

import java.util.Collections;
import java.util.List;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerQueue;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;

public class PlayerFace implements Face {

	private final FaceNavigation navigation;
	private final Player player;

	private List<PlayItem> queue;
	private Object selectedItem;

	public PlayerFace (final FaceNavigation navigation, final Player player) {
		this.navigation = navigation;
		this.player = player;
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) {

		// TODO
		// - play order change.
		// - add / remove tags.
		// - help screen.
		// - Scrolling.

		switch (k.getKind()) {
			case ArrowUp:
			case ArrowDown:
				menuMove(k);
				return true;
			case Home:
				moveQueueItemEnd(VDirection.UP);
				return true;
			case End:
				moveQueueItemEnd(VDirection.DOWN);
				return true;
			case Delete:
				deleteQueueItem();
				return true;
			case NormalKey:
				switch (k.getCharacter()) {
					case 'q':
						return this.navigation.backOneLevel();
					case ' ':
						this.player.pausePlaying();
						return true;
					case 'n':
						this.player.nextTrack();
						return true;
					case 'J':
						moveQueueItemEnd(VDirection.DOWN);
						return true;
					case 'K':
						moveQueueItemEnd(VDirection.UP);
						return true;
					case 'j':
						moveQueueItem(VDirection.DOWN);
						return true;
					case 'k':
						moveQueueItem(VDirection.UP);
						return true;
					default:
				}
			default:
				return false;
		}
	}

	private void menuMove (final Key k) {
		this.selectedItem = MenuHelper.moveListSelection(this.selectedItem,
				k.getKind() == Kind.ArrowUp ? VDirection.UP : VDirection.DOWN,
				this.queue);
	}

	private void moveQueueItem (final VDirection direction) {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof PlayItem) {
			this.player.getQueue().moveInQueue(Collections.singletonList((PlayItem) this.selectedItem), direction == VDirection.DOWN);
		}
	}

	private void moveQueueItemEnd (final VDirection direction) {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof PlayItem) {
			this.player.getQueue().moveInQueueEnd(Collections.singletonList((PlayItem) this.selectedItem), direction == VDirection.DOWN);
		}
	}

	private void deleteQueueItem () {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof PlayItem) {
			this.player.getQueue().removeFromQueue((PlayItem) this.selectedItem);
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, String.format("Player %s: %s   %s",
				this.player.getId(), this.player.getName(), PlayerHelper.playerStateMsg(this.player)));
		// TODO play order show.
		w.drawString(1, l++, PlayerHelper.playingItemTitle(this.player));
		w.drawString(1, l++, PlayerHelper.summariseTags(this.player));
		final PlayerQueue pq = this.player.getQueue();
		w.drawString(0, l++, PlayerHelper.queueSummary(pq));
		this.queue = pq.getQueueList();
		for (final PlayItem item : this.queue) {
			// TODO Stop if over edge of screen.
			if (item.equals(this.selectedItem)) {
				w.drawString(1, l++, String.valueOf(item), ScreenCharacterStyle.Reverse);
			}
			else {
				w.drawString(1, l++, String.valueOf(item));
			}
		}
	}

}
