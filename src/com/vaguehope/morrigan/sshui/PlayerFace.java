package com.vaguehope.morrigan.sshui;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.ActionListDialog;
import com.googlecode.lanterna.gui.dialog.MessageBox;
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
import com.vaguehope.morrigan.util.TimeHelper;
import com.vaguehope.sqlitewrapper.DbException;

public class PlayerFace extends DefaultFace {

	private static final String HELP_TEXT =
			" <space>\tplay / pause\n" +
			"<ctrl>+c\tstop\n" +
			"       i\tseek\n" +
			"       n\tnext track\n" +
			"       o\tplayback order\n" +
			"       /\tsearch DB\n" +
			"       r\trefresh playing item's tags\n" +
			"       T\topen tag editor for playing item\n" +
			"       g\tgo to top of list\n" +
			"       G\tgo to end of list\n" +
			"<delete>\tremove from queue\n" +
			"       K\tmove to top of queue\n" +
			"       k\tmove up in queue\n" +
			"       j\tmove down in queue\n" +
			"       J\tmove to bottom of queue\n" +
			"       t\topen tag editor for queue item\n" +
			"       f\tfull screen\n" +
			"       q\tback a page\n" +
			"       h\tthis help text";

	private static final long DATA_REFRESH_MILLIS = 500L;

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final Player player;
	private final DbHelper dbHelper;

	private final TextGuiUtils textGuiUtils = new TextGuiUtils();
	private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	private final AtomicReference<String> savedSearchTerm = new AtomicReference<String>();

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
		this.dbHelper = new DbHelper(navigation, mnContext, player, null, null);
	}

	private void invalidateData () {
		this.tagSummaryItem = null;
		refreshData();
	}

	private void refreshData () {
		final PlayItem currentItem = this.player.getCurrentItem();
		if (this.tagSummaryItem == null || !this.tagSummaryItem.equals(currentItem)) {
			this.tagSummary = PrintingThingsHelper.summariseTags(this.player);
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
		this.itemDetailsBarItem = this.selectedItem;
		if (this.selectedItem instanceof PlayItem) {
			final PlayItem playItem = (PlayItem) this.selectedItem;
			final IMediaTrack item = playItem.getTrack();
			if (item != null) {
				this.itemDetailsBar = PrintingThingsHelper.summariseItemWithPlayCounts(playItem.getList(), item, this.dateFormat);
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
					case 'c':
						if (k.isCtrlPressed()) {
							this.player.stopPlaying();
							return true;
						}
						return false;
					case 'i':
						SeekDialog.show(gui, this.player);
						return true;
					case 'n':
						this.player.nextTrack();
						return true;
					case 'o':
						askPlaybackOrder(gui);
						return true;
					case '/':
						askSearch(gui);
						return true;
					case 'r':
						invalidateData();
						return true;
					case 'T':
						showEditTagsForPlayingItem(gui);
						return true;
					case 't':
						showEditTagsForSelectedItem(gui);
						return true;
					case 'f':
						askFullScreen(gui);
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

	private void askSearch (final GUIScreen gui) throws DbException, MorriganException {
		final IMediaTrackList<? extends IMediaTrack> list = this.player.getCurrentList();
		if (list != null) {
			if (list instanceof IMixedMediaDb) {
				askJumpTo(gui, (IMixedMediaDb) list);
			}
			else {
				MessageBox.showMessageBox(gui, "TODO", "Search: " + list);
			}
		}
		else {
			MessageBox.showMessageBox(gui, "Search", "No list selected.");
		}
	}

	private void askJumpTo (final GUIScreen gui, final IMixedMediaDb db) throws DbException, MorriganException {
		this.dbHelper.askSearch(gui, db, this.savedSearchTerm);
	}

	private void showEditTagsForPlayingItem (final GUIScreen gui) throws MorriganException {
		showEditTagsForItem(gui, this.player.getCurrentItem());
	}

	private void showEditTagsForSelectedItem (final GUIScreen gui) throws MorriganException {
		if (this.selectedItem == null) return;
		if (this.selectedItem instanceof PlayItem) {
			showEditTagsForItem(gui, (PlayItem) this.selectedItem);
		}
	}

	private static void showEditTagsForItem (final GUIScreen gui, final PlayItem item) throws MorriganException {
		if (item == null || !item.isComplete()) return;
		TagEditor.show(gui, item.getList(), item.getTrack());
	}

	private void askFullScreen (final GUIScreen gui) {
		final Map<Integer, String> monitors = this.player.getMonitors();
		final Action[] actions = new Action[monitors.size()];
		int i = 0;
		for (final Entry<Integer, String> monitor : monitors.entrySet()) {
			actions[i] = new Action() {
				@Override
				public String toString () {
					return String.format("%s. %s", monitor.getKey(), monitor.getValue());
				}

				@Override
				public void doAction () {
					PlayerFace.this.player.goFullscreen(monitor.getKey());
				}
			};
			i++;
		}
		ActionListDialog.showActionListDialog(gui, "Full Screen", null, actions);
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

	private static final ScreenCharacterStyle[] UNSELECTED = new ScreenCharacterStyle[] {};
	private static final ScreenCharacterStyle[] SELECTED = new ScreenCharacterStyle[] { ScreenCharacterStyle.Reverse };

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		refreshStaleData();

		final TerminalSize terminalSize = scr.getTerminalSize();
		int l = 0;

		w.drawString(0, l++, String.format("Player %s: %s   %s   %s.",
				this.player.getId(),
				this.player.getName(),
				PrintingThingsHelper.playerStateMsg(this.player),
				PrintingThingsHelper.listTitleAndOrder(this.player)));
		w.drawString(1, l++, PrintingThingsHelper.playingItemTitle(this.player));
		drawPrgBar(w, l++, terminalSize.getColumns());
		w.drawString(1, l++, this.tagSummary);

		final PlayerQueue pq = this.player.getQueue();
		w.drawString(0, l++, PrintingThingsHelper.queueSummary(pq));

		this.pageSize = terminalSize.getRows() - l - 1;
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
			final ScreenCharacterStyle[] style = item.equals(this.selectedItem) ? SELECTED : UNSELECTED;
			w.drawString(1, l, String.valueOf(item), style);
			if (item.hasTrack()) {
				final String dur = TimeHelper.formatTimeSeconds(item.getTrack().getDuration());
				w.drawString(terminalSize.getColumns() - dur.length(), l, dur, style);
			}
			l++;
		}

		this.textGuiUtils.drawTextRowWithBg(scr, terminalSize.getRows() - 1, this.itemDetailsBar, Color.WHITE, Color.BLUE, ScreenCharacterStyle.Bold);
		scr.putString(terminalSize.getColumns() - 3, terminalSize.getRows() - 1,
				PrintingThingsHelper.scrollSummary(this.queue.size(), this.pageSize, this.queueScrollTop),
				Color.WHITE, Color.BLUE, ScreenCharacterStyle.Bold);
	}

	private String lastPrgBar = null;
	private long lastPrg = -1;

	private void drawPrgBar (final ScreenWriter w, final int l, final int screenWidth) {
		final int barWidth = screenWidth - 2;
		final int total = this.player.getCurrentTrackDuration();
		final long prg = total < 1 ? 0 : (long) ((this.player.getCurrentPosition() / (double) total) * barWidth);

		if (prg != this.lastPrg || this.lastPrgBar == null || this.lastPrgBar.length() != barWidth) {
			this.lastPrg = prg;
			if (prg > 0 && barWidth > 0) {
				final StringBuilder b = new StringBuilder(barWidth);
				b.setLength(barWidth);
				for (int i = 0; i < barWidth; i++) {
					b.setCharAt(i, i < prg ? '=' : i == prg ? '>' : ' ');
				}
				this.lastPrgBar = b.toString();
			}
			else {
				this.lastPrgBar = "";
			}
		}

		w.drawString(1, l, this.lastPrgBar);
	}

}
