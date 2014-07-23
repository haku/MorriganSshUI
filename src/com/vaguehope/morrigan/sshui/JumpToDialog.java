package com.vaguehope.morrigan.sshui;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
import com.vaguehope.morrigan.sshui.util.ReflectionHelper;
import com.vaguehope.sqlitewrapper.DbException;

public class JumpToDialog extends Window {

	private static final int WIDTH = 70;
	private static final int HEIGHT = 14;
	private static final int MAX_RESULTS = 100;
	private static final Logger LOG = LoggerFactory.getLogger(JumpToDialog.class);

	private final IMixedMediaDb db;
	private final AtomicReference<String> savedSearchTerm;

	private final Label lblMsgs;
	private final SearchTextBox txtSearch;
	private final MediaItemListBox lstResults;
	private final Label lblTags;

	private volatile boolean alive = true;
	private List<? extends IMediaTrack> searchResults;
	private JumpResult result;

	public JumpToDialog (final IMixedMediaDb db, final AtomicReference<String> savedSearchTerm) {
		super(db.getListName());
		this.db = db;
		this.savedSearchTerm = savedSearchTerm;

		this.lblMsgs = new Label("");
		addComponent(this.lblMsgs);

		this.txtSearch = new SearchTextBox(WIDTH, this);
		addComponent(this.txtSearch);

		this.lstResults = new MediaItemListBox(new TerminalSize(WIDTH, HEIGHT), this);
		addComponent(this.lstResults);

		this.lblTags = new Label("", WIDTH);
		addComponent(this.lblTags);

		final Panel btnPanel = new Panel(new Invisible(), Panel.Orientation.HORISONTAL);
		btnPanel.addComponent(new Button("Reveal", new Action() {
			@Override
			public void doAction () {
				acceptRevealResult();
			}
		}));
		btnPanel.addComponent(new Button("Enqueue All", new Action() {
			@Override
			public void doAction () {
				acceptEnqueueAllResult();
			}
		}));
		btnPanel.addComponent(new Button("Shuffle", new Action() {
			@Override
			public void doAction () {
				acceptShuffleResult();
			}
		}));
		btnPanel.addComponent(new EmptySpace(WIDTH - 58, 1)); // FIXME magic numbers.
		btnPanel.addComponent(new Button("Open", new Action() {
			@Override
			public void doAction () {
				acceptOpenResult();
			}
		}));
		btnPanel.addComponent(new Button("Close", new Action() {
			@Override
			public void doAction () {
				close();
			}
		}));
		addComponent(btnPanel);

		final Panel contentPane = (Panel) ReflectionHelper.readField(this, "contentPane");
		contentPane.addShortcut(Key.Kind.Escape, new Action() {
			@Override
			public void doAction () {
				close();
			}
		});

		setSearchResults(null); // Init msgs.
		final String term = savedSearchTerm.get();
		if (term != null) {
			this.txtSearch.setText(term);
			requestSearch();
		}
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
			final String query = dlg.getSearchText();
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

	protected String getSearchText () {
		final String text = this.txtSearch.getText();
		this.savedSearchTerm.set(text);
		return text;
	}

	/**
	 * Call on UI thread.
	 */
	protected final void setSearchResults (final List<? extends IMediaTrack> results) {
		this.searchResults = results;
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

	protected void acceptEnqueueResult () {
		final IMediaTrack track = this.lstResults.getSelectedTrack();
		if (track != null) setResult(new JumpResult(JumpType.ENQUEUE, this.txtSearch.getText(), track));
	}

	protected void acceptEnqueueAllResult () {
		if (this.searchResults != null && this.searchResults.size() > 0) {
			setResult(new JumpResult(JumpType.ENQUEUE, this.txtSearch.getText(), this.searchResults));
		}
	}

	protected void acceptOpenResult () {
		final String text = this.txtSearch.getText();
		if (text != null && text.length() > 0) setResult(new JumpResult(JumpType.OPEN_VIEW, text));
	}

	protected void acceptRevealResult () {
		final IMediaTrack track = this.lstResults.getSelectedTrack();
		if (track != null) setResult(new JumpResult(JumpType.REVEAL, this.txtSearch.getText(), track));
	}

	protected void acceptShuffleResult () {
		if (this.searchResults != null && this.searchResults.size() > 0) {
			setResult(new JumpResult(JumpType.SHUFFLE_AND_ENQUEUE, this.txtSearch.getText(), this.searchResults));
		}
	}

	private void setResult (final JumpResult res) {
		if (this.result != null) throw new IllegalStateException();
		this.result = res;
		close();
	}

	public JumpResult getResult () {
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
					this.dialog.acceptEnqueueResult();
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
					this.dialog.acceptEnqueueResult();
					return Result.EVENT_HANDLED;
				default:
					return Result.EVENT_NOT_HANDLED;
			}
		}

		private void selectedChanged () {
			this.dialog.requestTags((IMediaTrack) getSelectedItem());
		}

	}

	public enum JumpType {
		ENQUEUE,
		REVEAL,
		SHUFFLE_AND_ENQUEUE,
		OPEN_VIEW;
	}

	public static class JumpResult {

		private final JumpType type;
		private final String text;
		private final IMediaTrack track;
		private final List<? extends IMediaTrack> tracks;

		public JumpResult (final JumpType type, final String text) {
			this(type, text, null, null);
		}

		public JumpResult (final JumpType type, final String text, final IMediaTrack track) {
			this(type, text, track, null);
		}

		public JumpResult (final JumpType type, final String text, final List<? extends IMediaTrack> tracks) {
			this(type, text, null, tracks);
		}

		private JumpResult (final JumpType type, final String text, final IMediaTrack track, final List<? extends IMediaTrack> tracks) {
			if (type == null) throw new IllegalArgumentException("type not specified");
			this.type = type;
			this.track = track;
			this.text = text;
			this.tracks = tracks;
		}

		public JumpType getType () {
			return this.type;
		}

		public String getText () {
			if (this.text == null) throw new IllegalStateException("text not set.");
			return this.text;
		}

		public IMediaTrack getTrack () {
			if (this.track == null) throw new IllegalStateException("track not set.");
			return this.track;
		}

		public List<? extends IMediaTrack> getTracks () {
			if (this.tracks == null) {
				if (this.track != null) return Collections.singletonList(this.track);
				throw new IllegalStateException("tracks not set.");
			}
			return this.tracks;
		}

	}

	public static JumpResult show (final GUIScreen owner, final IMixedMediaDb db, final AtomicReference<String> savedSearchTerm) {
		final JumpToDialog dialog = new JumpToDialog(db, savedSearchTerm);
		owner.showWindow(dialog, GUIScreen.Position.CENTER);
		return dialog.getResult();
	}

}
