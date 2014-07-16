package com.vaguehope.morrigan.sshui;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.ActionListDialog;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.gui.dialog.TextInputDialog;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.googlecode.lanterna.terminal.Terminal.Color;
import com.googlecode.lanterna.terminal.TerminalSize;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.player.OrderHelper.PlaybackOrder;
import com.vaguehope.morrigan.player.PlayItem;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.player.PlayerQueue;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.morrigan.sshui.util.TextGuiUtils;
import com.vaguehope.sqlitewrapper.DbException;

public class PlayerFace extends DefaultFace {

	private static final String HELP_TEXT =
			" <space>\tplay / pause\n" +
			"       n\tnext track\n" +
			"       o\tplayback order\n" +
			"       /\tsearch DB\n" +
			"       r\trefresh playing item's tags\n" +
			"       g\tgo to top of list\n" +
			"       G\tgo to end of list\n" +
			"<delete>\tremove from queue\n" +
			"       K\tmove to top of queue\n" +
			"       k\tmove up in queue\n" +
			"       j\tmove down in queue\n" +
			"       J\tmove to bottom of queue\n" +
			"       h\tthis help text";

	private static final long DATA_REFRESH_MILLIS = 500L;

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final Player player;

	private final TextGuiUtils textGuiUtils = new TextGuiUtils();
	private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	private long lastDataRefresh = 0;
	private String tagSummary;
	private PlayItem tagSummaryItem;
	private List<PlayItem> queue;
	private Object selectedItem;
	private int queueScrollTop = 0;
	private int pageSize = 1;
	private String itemDetailsBar = "";
	private Object itemDetailsBarItem;

	public PlayerFace (final FaceNavigation navigation, final MnContext mnContext, final Player player) {
		this.navigation = navigation;
		this.mnContext = mnContext;
		this.player = player;
	}

	private void invalidateData () {
		this.tagSummaryItem = null;
		refreshData();
	}

	private void refreshData () {
		final PlayItem currentItem = this.player.getCurrentItem();
		if (this.tagSummaryItem == null || !this.tagSummaryItem.equals(currentItem)) {
			this.tagSummary = PlayerHelper.summariseTags(this.player);
			this.tagSummaryItem = currentItem;
		}

		this.queue = this.player.getQueue().getQueueList();
	}

	private void refreshStaleData () {
		final long now = System.nanoTime();
		if (now - this.lastDataRefresh > TimeUnit.MILLISECONDS.toNanos(DATA_REFRESH_MILLIS)) {
			refreshData();
			this.lastDataRefresh = now;
		}
	}

	private void updateSelectedItemDetailsBar () throws MorriganException {
		if (this.itemDetailsBarItem != null && this.itemDetailsBarItem.equals(this.selectedItem)) return;
		if (this.selectedItem instanceof PlayItem) {
			final PlayItem playItem = (PlayItem) this.selectedItem;
			IMediaTrack item = playItem.getTrack();
			if (item != null) {
				this.itemDetailsBar = PlayerHelper.summariseItem(playItem.getList(), item, this.dateFormat);
			}
			else {
				this.itemDetailsBar = "(no track selected)";
			}
		}
		else {
			this.itemDetailsBar = "(unknown item type)";
		}
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) throws DbException, MorriganException {

		// TODO
		// - add / remove tags.

		switch (k.getKind()) {
			case ArrowUp:
			case ArrowDown:
				menuMove(k, 1);
				return true;
			case PageUp:
			case PageDown:
				menuMove(k, this.pageSize - 1);
				return true;
			case Home:
				menuMoveEnd(VDirection.UP);
				return true;
			case End:
				menuMoveEnd(VDirection.DOWN);
				return true;
			case Delete:
				deleteQueueItem();
				return true;
			case NormalKey:
				switch (k.getCharacter()) {
					case 'q':
						return this.navigation.backOneLevel();
					case 'h':
						this.navigation.startFace(new HelpFace(this.navigation, HELP_TEXT));
						return true;
					case ' ':
						this.player.pausePlaying();
						return true;
					case 'n':
						this.player.nextTrack();
						return true;
					case 'o':
						askPlaybackOrder(gui);
						return true;
					case '/':
					case 's':
					case 'f':
						askSearch(gui);
						return true;
					case 'r':
						invalidateData();
						return true;
					case 'g':
						menuMoveEnd(VDirection.UP);
						return true;
					case 'G':
						menuMoveEnd(VDirection.DOWN);
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

	private void askPlaybackOrder (final GUIScreen gui) {
		final Action[] actions = new Action[PlaybackOrder.values().length];
		int i = 0;
		for (final PlaybackOrder po : PlaybackOrder.values()) {
			actions[i] = new Action() {
				@Override
				public String toString () {
					return po.toString();
				}

				@Override
				public void doAction () {
					PlayerFace.this.player.setPlaybackOrder(po);
				}
			};
			i++;
		}
		ActionListDialog.showActionListDialog(gui, "Playback Order", "Current: " + this.player.getPlaybackOrder(), actions);
	}

	private void askSearch (final GUIScreen gui) throws DbException {
		final IMediaTrackList<? extends IMediaTrack> list = this.player.getCurrentList();
		if (list != null) {
			if (list instanceof IMixedMediaDb) {
				final String term = TextInputDialog.showTextInputBox(gui, "Search", "", "", 50);
				if (term != null) {
					this.navigation.startFace(new DbFace(this.navigation, this.mnContext, (IMixedMediaDb) list, this.player, term));
				}
			}
			else {
				MessageBox.showMessageBox(gui, "TODO", "Search: " + list);
			}
		}
		else {
			MessageBox.showMessageBox(gui, "Search", "No list selected.");
		}
	}

	private void menuMove (final Key k, final int distance) throws MorriganException {
		this.selectedItem = MenuHelper.moveListSelection(this.selectedItem,
				k.getKind() == Kind.ArrowUp || k.getKind() == Kind.PageUp
						? VDirection.UP
						: VDirection.DOWN,
				distance,
				this.queue);
		updateSelectedItemDetailsBar();
	}

	private void menuMoveEnd (final VDirection direction) throws MorriganException {
		if (this.queue == null || this.queue.size() < 1) return;
		switch (direction) {
			case UP:
				this.selectedItem = this.queue.get(0);
				break;
			case DOWN:
				this.selectedItem = this.queue.get(this.queue.size() - 1);
				break;
			default:
		}
		updateSelectedItemDetailsBar();
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
		if (this.selectedItem instanceof PlayItem && this.queue != null) {
			final int i = this.queue.indexOf(this.selectedItem);
			this.player.getQueue().removeFromQueue((PlayItem) this.selectedItem);
			if (i >= this.queue.size()) { // Last item was deleted.
				this.queue = this.player.getQueue().getQueueList();
				if (this.queue.size() > 0) {
					this.selectedItem = this.queue.get(this.queue.size() - 1);
				}
				else {
					this.selectedItem = null;
				}
			}
			else if (i >= 0) {
				this.selectedItem = this.queue.get(i);
			}
		}
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		refreshStaleData();

		final TerminalSize terminalSize = scr.getTerminalSize();
		int l = 0;

		w.drawString(0, l++, String.format("Player %s: %s   %s   %s.",
				this.player.getId(), this.player.getName(), PlayerHelper.playerStateMsg(this.player), this.player.getPlaybackOrder()));
		w.drawString(1, l++, PlayerHelper.playingItemTitle(this.player));
		w.drawString(1, l++, this.tagSummary);
		final PlayerQueue pq = this.player.getQueue();
		w.drawString(0, l++, PlayerHelper.queueSummary(pq));

		this.pageSize = terminalSize.getRows() - l;
		final int selI = this.queue.indexOf(this.selectedItem);
		if (selI >= 0) {
			if (selI - this.queueScrollTop >= this.pageSize) {
				this.queueScrollTop = selI - this.pageSize + 1;
			}
			else if (selI < this.queueScrollTop) {
				this.queueScrollTop = selI;
			}
		}

		for (int i = this.queueScrollTop; i < this.queue.size(); i++) {
			if (i >= this.queueScrollTop + this.pageSize) break;
			final PlayItem item = this.queue.get(i);
			if (item.equals(this.selectedItem)) {
				w.drawString(1, l++, String.valueOf(item), ScreenCharacterStyle.Reverse);
			}
			else {
				w.drawString(1, l++, String.valueOf(item));
			}
		}

		this.textGuiUtils.drawTextRowWithBg(scr, terminalSize.getRows() - 1, this.itemDetailsBar, Color.WHITE, Color.BLUE, ScreenCharacterStyle.Bold);
	}

}
