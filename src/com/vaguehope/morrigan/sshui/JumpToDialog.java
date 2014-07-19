package com.vaguehope.morrigan.sshui;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.Border.Invisible;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.Window;
import com.googlecode.lanterna.gui.component.AbstractListBox;
import com.googlecode.lanterna.gui.component.Button;
import com.googlecode.lanterna.gui.component.EmptySpace;
import com.googlecode.lanterna.gui.component.Label;
import com.googlecode.lanterna.gui.component.Panel;
import com.googlecode.lanterna.gui.component.TextBox;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.terminal.TerminalPosition;
import com.googlecode.lanterna.terminal.TerminalSize;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.MediaTag;
import com.vaguehope.morrigan.model.media.MediaTagType;
import com.vaguehope.morrigan.player.Player;
import com.vaguehope.morrigan.sshui.Face.FaceNavigation;
import com.vaguehope.sqlitewrapper.DbException;

public class JumpToDialog extends Window {

	private static final int WIDTH = 60;
	private static final int HEIGHT = 14;
	private static final int MAX_RESULTS = 50;
	private static final Logger LOG = LoggerFactory.getLogger(JumpToDialog.class);

	private final IMixedMediaDb db;

	private final Label lblMsgs;
	private final SearchTextBox txtSearch;
	private final MediaItemListBox lstResults;
	private final Label lblTags;

	private volatile boolean alive = true;
	private IMediaTrack result;

	public JumpToDialog (final IMixedMediaDb db, final FaceNavigation navigation, final MnContext mnContext, final Player player) {
		super(db.getListName());
		this.db = db;

		this.lblMsgs = new Label("");
		addComponent(this.lblMsgs);

		this.txtSearch = new SearchTextBox(WIDTH, this);
		addComponent(this.txtSearch);

		this.lstResults = new MediaItemListBox(new TerminalSize(WIDTH, HEIGHT), this);
		addComponent(this.lstResults);

		this.lblTags = new Label("", WIDTH);
		addComponent(this.lblTags);

		final Panel cancelPanel = new Panel(new Invisible(), Panel.Orientation.HORISONTAL);
		cancelPanel.addComponent(new EmptySpace(WIDTH - 20, 1)); // FIXME magic numbers.
		cancelPanel.addComponent(new Button("Open", new Action() {
			@Override
			public void doAction () {
				try {
					navigation.startFace(new DbFace(navigation, mnContext, db, player, JumpToDialog.this.txtSearch.getText()));
					close();
				}
				catch (DbException e) {
					MessageBox.showMessageBox(getOwner(), "Error opening DB page.", e.toString());
				}
			}
		}));
		cancelPanel.addComponent(new Button("Close", new Action() {
			@Override
			public void doAction () {
				close();
			}
		}));
		addComponent(cancelPanel);

		setSearchResults(null); // Init msgs.
	}

	@Override
	public void close () {
		this.alive = false;
		super.close();
	}

	@Override
	protected void onClosed () {
		this.alive = false;
		super.onClosed();
	}

	protected boolean isAlive () {
		return this.alive;
	}

	private static final AtomicInteger BG_THREAD_NUMBER = new AtomicInteger(0);
	private SearchRunner searchRunner;

	/**
	 * Only call on UI thread.
	 */
	protected void requestSearch () {
		if (this.searchRunner == null) {
			this.searchRunner = new SearchRunner(this);
			final Thread t = new Thread(this.searchRunner, String.format("jtbg-%s", BG_THREAD_NUMBER.getAndIncrement()));
			t.setDaemon(true);
			t.start();
		}
		this.searchRunner.requestSearch();
	}

	/**
	 * Only call on UI thread.
	 */
	protected void requestTags (final IMediaTrack item) {
		if (this.searchRunner == null) return;
		this.searchRunner.requestTags(item);
	}

	private static class SearchRunner implements Runnable {

		private final JumpToDialog dlg;
		private final BlockingQueue<Object> queue;

		public SearchRunner (final JumpToDialog dlg) {
			this.dlg = dlg;
			this.queue = new LinkedBlockingQueue<Object>(1);
		}

		public void requestSearch () {
			this.queue.offer(Boolean.TRUE);
		}

		public void requestTags (final IMediaTrack item) {
			if (item == null) return;
			this.queue.offer(item);
		}

		@Override
		public void run () {
			try {
				runAndThrow();
			}
			catch (final Exception e) { // NOSONAR report all errors to user.
				this.dlg.getOwner().runInEventThread(new Action() {
					@Override
					public void doAction () {
						MessageBox.showMessageBox(SearchRunner.this.dlg.getOwner(), "Error running search", e.toString());
					}
				});
			}
			LOG.info("BG search thread done.");
		}

		private void runAndThrow () throws DbException, MorriganException {
			while (this.dlg.isAlive()) {
				try {
					final Object item = this.queue.poll(15, TimeUnit.SECONDS);
					if (item == null) continue;
					if (item instanceof IMediaTrack) {
						final List<MediaTag> tags = this.dlg.db.getTags((IMediaTrack) item);
						this.dlg.getOwner().runInEventThread(new ShowTags(this.dlg, tags));
					}
					else {
						final List<? extends IMediaTrack> results = doSearch(this.dlg);
						this.dlg.getOwner().runInEventThread(new SetSearchResults(this.dlg, results));
					}
				}
				catch (final InterruptedException e) { /* ignore. */}
			}
		}

		private static List<? extends IMediaTrack> doSearch (final JumpToDialog dlg) throws DbException {
			final String query = dlg.txtSearch.getText();
			if (query == null || query.length() < 1) return null;
			return dlg.db.simpleSearch(query, MAX_RESULTS);
		}

	}

	private static class SetSearchResults implements Action {

		private final JumpToDialog dlg;
		private final List<? extends IMediaTrack> results;

		public SetSearchResults (final JumpToDialog dlg, final List<? extends IMediaTrack> results) {
			this.dlg = dlg;
			this.results = results;
		}

		@Override
		public void doAction () {
			this.dlg.setSearchResults(this.results);
		}
	}

	private static class ShowTags implements Action {

		private final JumpToDialog dlg;
		private final List<MediaTag> tags;

		public ShowTags (final JumpToDialog dlg, final List<MediaTag> tags) {
			this.dlg = dlg;
			this.tags = tags;
		}

		@Override
		public void doAction () {
			final StringBuilder s = new StringBuilder();
			for (final MediaTag tag : this.tags) {
				if (tag.getType() != MediaTagType.MANUAL) continue;
				if (s.length() > 0) s.append(", ");
				s.append(tag.getTag());
			}
			this.dlg.showTags(s.toString());
		}
	}

	/**
	 * Call on UI thread.
	 */
	protected void setSearchResults (final List<? extends IMediaTrack> results) {
		this.lstResults.setItems(results);
		if (results != null && results.size() > 0) {
			this.lblMsgs.setText(results.size() + " results.");
		}
		else if (this.txtSearch.getText().length() > 0) {
			this.lblMsgs.setText("No results for query.");
		}
		else {
			this.lblMsgs.setText("Search:");
		}
	}

	protected void showTags (final String msg) {
		this.lblTags.setText(msg);
	}

	protected void acceptResult () {
		this.result = this.lstResults.getSelectedTrack();
		close();
	}

	public IMediaTrack getResult () {
		return this.result;
	}

	private static class SearchTextBox extends TextBox {

		private final JumpToDialog dialog;

		public SearchTextBox (final int width, final JumpToDialog dialog) {
			super("", width);
			this.dialog = dialog;
		}

		@Override
		public Result keyboardInteraction (final Key key) {
			switch (key.getKind()) {
				case Enter:
					this.dialog.acceptResult();
					return Result.EVENT_HANDLED;
				case NormalKey:
				case Backspace:
				case Delete:
					this.dialog.requestSearch();
					// Fall through.
				default:
					return super.keyboardInteraction(key);
			}
		}

	}

	private static class MediaItemListBox extends AbstractListBox {

		private final JumpToDialog dialog;

		public MediaItemListBox (final TerminalSize preferredSize, final JumpToDialog dialog) {
			super(preferredSize);
			this.dialog = dialog;
		}

		public void setItems (final List<? extends IMediaTrack> items) {
			clearItems();
			if (items != null) {
				for (final IMediaTrack track : items) {
					addItem(track);
				}
				setSelectedItem(0);
			}
		}

		public IMediaTrack getSelectedTrack () {
			return (IMediaTrack) getSelectedItem();
		}

		@Override
		protected String createItemString (final int index) {
			return String.valueOf(getItemAt(index));
		}

		@Override
		public TerminalPosition getHotspot () {
			return null;
		}

		@Override
		protected void afterEnteredFocus (final FocusChangeDirection direction) {
			super.afterEnteredFocus(direction);
			selectedChanged();
		}

		@Override
		public Result keyboardInteraction (final Key key) {
			final Result result = super.keyboardInteraction(key);

			switch (key.getKind()) {
				case ArrowUp:
				case ArrowDown:
					selectedChanged();
				default:
			}

			return result;
		}

		@Override
		protected Result unhandledKeyboardEvent (final Key key) {
			switch (key.getKind()) {
				case Enter:
					this.dialog.acceptResult();
					return Result.EVENT_HANDLED;
				default:
					return Result.EVENT_NOT_HANDLED;
			}
		}

		private void selectedChanged () {
			this.dialog.requestTags((IMediaTrack) getSelectedItem());
		}

	}

	public static IMediaTrack show (final GUIScreen owner, final FaceNavigation navigation, final MnContext mnContext, final Player player, final IMixedMediaDb db) {
		final JumpToDialog dialog = new JumpToDialog(db, navigation, mnContext, player);
		owner.showWindow(dialog, GUIScreen.Position.CENTER);
		return dialog.getResult();
	}

}
