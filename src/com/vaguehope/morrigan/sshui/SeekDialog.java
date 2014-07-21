package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.Window;
import com.googlecode.lanterna.gui.component.AbstractListBox;
import com.googlecode.lanterna.gui.component.Button;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.terminal.TerminalPosition;
import com.googlecode.lanterna.terminal.TerminalSize;
import com.vaguehope.morrigan.player.Player;

public class SeekDialog extends Window {

	public SeekDialog (final Player player) {
		super("Seek");
		addComponent(new HopListBox(player));
		addComponent(new Button("Close", new Action() {
			@Override
			public void doAction () {
				close();
			}
		}));
	}

	private enum SeekHop {
		FIVE(5),
		THIRTY(30),
		SIXTY(60),
		NINETY(90);

		private final int seconds;

		private SeekHop (final int seconds) {
			this.seconds = seconds;
		}

		public int getSeconds () {
			return this.seconds;
		}

		@Override
		public String toString () {
			return String.format("%1$2s seconds", this.seconds);
		}

	}

	private static class HopListBox extends AbstractListBox {

		private final Player player;

		public HopListBox (final Player player) {
			super(new TerminalSize(10, SeekHop.values().length));
			this.player = player;
			loadItems();
		}

		private SeekHop getSelectedHop () {
			return (SeekHop) getSelectedItem();
		}

		@Override
		public Result keyboardInteraction (final Key key) {
			switch (key.getKind()) {
				case ArrowLeft:
					seekPlayerRelative(this.player, 0 - getSelectedHop().getSeconds());
					return Result.EVENT_HANDLED;
				case ArrowRight:
					seekPlayerRelative(this.player, getSelectedHop().getSeconds());
					return Result.EVENT_HANDLED;
				default:
					return super.keyboardInteraction(key);
			}
		}

		public final void loadItems () {
			clearItems();
			for (final SeekHop hop : SeekHop.values()) {
				addItem(hop);
			}
		}

		@Override
		protected String createItemString (final int index) {
			return String.valueOf(getItemAt(index));
		}

		@Override
		public TerminalPosition getHotspot () {
			return null;
		}

	}

	protected static void seekPlayerRelative (final Player player, final int offsetSeconds) {
		final int duration = player.getCurrentTrackDuration();
		if (duration < 1) return;

		final long targetAbsolute = player.getCurrentPosition() + offsetSeconds;
		final double targetProportional = targetAbsolute / (double) duration;
		player.seekTo(targetProportional);
	}

	public static void show (final GUIScreen owner, final Player player) {
		owner.showWindow(new SeekDialog(player), GUIScreen.Position.CENTER);
	}

}
