package com.vaguehope.morrigan.sshui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerQueue;

public class PlayerFace implements Face {

	private static final Logger LOG = LoggerFactory.getLogger(PlayerFace.class);

	private final FaceNavigation navigation;
	private final Player player;

	public PlayerFace (final FaceNavigation navigation, final Player player) {
		this.navigation = navigation;
		this.player = player;
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) {

		// TODO lots of key cmds.

		switch (k.getKind()) {
			case NormalKey:
				switch (k.getCharacter()) {
					case 'q':
						return this.navigation.backOneLevel();
					default:
				}
			default:
				LOG.info("kind={} char={}", k.getKind(), String.valueOf((int) k.getCharacter()));
				return false;
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, String.format("Player %s: %s   %s",
				this.player.getId(), this.player.getName(), PlayerHelper.playerStateMsg(this.player)));
		w.drawString(1, l++, PlayerHelper.playingItemTitle(this.player));
		w.drawString(1, l++, PlayerHelper.summariseTags(this.player));
		final PlayerQueue queue = this.player.getQueue();
		w.drawString(0, l++, PlayerHelper.queueSummary(queue));
		for (final PlayItem item : queue.getQueueList()) {
			w.drawString(1, l++, String.valueOf(item));
		}
	}

}
