package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractListBox;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.input.KeyStroke;
import com.vaguehope.morrigan.player.Player;

public class SeekDialog extends DialogWindow {

	public SeekDialog (final Player player) {
		super("Seek");

		final Panel p = new Panel();
		p.setLayoutManager(new GridLayout(1)
				.setLeftMarginSize(1)
				.setRightMarginSize(1));

		p.addComponent(new HopListBox(player));
		p.addComponent(new Button("Close", new Runnable() {
			@Override
			public void run () {
				close();
			}
		}));

		setComponent(p);
		setCloseWindowWithEscape(true);
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

	private static class HopListBox extends AbstractListBox<SeekHop, HopListBox> {

		private final Player player;

		public HopListBox (final Player player) {
			super(new TerminalSize(10, SeekHop.values().length));
			this.player = player;
			loadItems();
		}

		private SeekHop getSelectedHop () {
			return getSelectedItem();
		}

		@Override
		public synchronized Result handleKeyStroke(final KeyStroke key) {
			switch (key.getKeyType()) {
				case ArrowLeft:
					seekPlayerRelative(this.player, 0 - getSelectedHop().getSeconds());
					return Result.HANDLED;
				case ArrowRight:
					seekPlayerRelative(this.player, getSelectedHop().getSeconds());
					return Result.HANDLED;
				default:
					return super.handleKeyStroke(key);
			}
		}

		public final void loadItems () {
			clearItems();
			for (final SeekHop hop : SeekHop.values()) {
				addItem(hop);
			}
		}

		@Override
		protected ListItemRenderer<SeekHop, HopListBox> createDefaultListItemRenderer () {
			return new ListItemRenderer<SeekHop, HopListBox>() {
				@Override
				public int getHotSpotPositionOnLine (final int selectedIndex) {
					return -1;
				}
			};
		}

	}

	protected static void seekPlayerRelative (final Player player, final int offsetSeconds) {
		final int duration = player.getCurrentTrackDuration();
		if (duration < 1) return;

		final long targetAbsolute = player.getCurrentPosition() + offsetSeconds;
		final double targetProportional = targetAbsolute / (double) duration;
		player.seekTo(targetProportional);
	}

	public static void show (final WindowBasedTextGUI owner, final Player player) {
		new SeekDialog(player).showDialog(owner);
	}

}
