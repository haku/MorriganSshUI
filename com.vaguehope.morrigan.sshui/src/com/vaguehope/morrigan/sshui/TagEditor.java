package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractListBox;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.gui2.dialogs.MessageDialog;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.model.media.MediaTag;
import com.vaguehope.morrigan.model.media.MediaTagClassification;
import com.vaguehope.morrigan.model.media.MediaTagType;

public class TagEditor extends DialogWindow {

	protected final IMediaTrackList<?> list;
	protected final IMediaTrack item;

	protected final TextBox txtNewTag;
	protected final TagListBox lstTags;

	public TagEditor (final IMediaTrackList<?> list, final IMediaTrack item) throws MorriganException {
		super(maxLength(item.getTitle(), 50)); // FIXME magic number.

		this.list = list;
		this.item = item;

		final Panel p = new Panel();
		p.setLayoutManager(new GridLayout(1)
				.setLeftMarginSize(1)
				.setRightMarginSize(1));

		this.txtNewTag = new AddTagTextBox(new TerminalSize(50, 1), this);
		p.addComponent(this.txtNewTag);

		this.lstTags = new TagListBox(new TerminalSize(50, 10), this); // FIXME hard-coded size.
		p.addComponent(this.lstTags);

		final Panel btnPanel = new Panel();
		btnPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));
		btnPanel.addComponent(new Label("                "));
		btnPanel.addComponent(new Button("Close", new Runnable() {
			@Override
			public void run () {
				close();
			}
		}));

		btnPanel.addTo(p);
		setComponent(p);

		setCloseWindowWithEscape(true);
		setHints(new HashSet<Hint>(Arrays.asList(Hint.CENTERED, Hint.MODAL)));

		this.lstTags.reloadTags();
	}

	private static class AddTagTextBox extends TextBox {

		private final TagEditor tagEditor;

		public AddTagTextBox (final TerminalSize preferredSize, final TagEditor tagEditor) {
			super(preferredSize);
			this.tagEditor = tagEditor;
		}

		@Override
		public synchronized Result handleKeyStroke (final KeyStroke key) {
			switch (key.getKeyType()) {
				case Tab:
					autocomplete();
					return Result.HANDLED;
				case Enter:
					addTag();
					resetAutocomplete(null);
					return Result.HANDLED;
				case Character:
					if (key.isCtrlDown() && key.getCharacter() == 'u') {
						setText("");
						return Result.HANDLED;
					}
					//$FALL-THROUGH$
				default:
					resetAutocomplete(null);
					return super.handleKeyStroke(key);
			}
		}

		private void addTag () {
			final String newTag = getText();
			if (newTag == null || newTag.length() < 1) return;
			try {
				this.tagEditor.list.addTag(this.tagEditor.item, newTag, MediaTagType.MANUAL, (MediaTagClassification) null);
				setText("");
				this.tagEditor.lstTags.reloadTags();
			}
			catch (final MorriganException e) {
				MessageDialog.showMessageDialog(this.tagEditor.getTextGUI(), "Error adding tag", e.toString());
			}
		}

		private List<MediaTag> suggestions;
		private int suggestionIndex;

		private void autocomplete () {
			if (this.suggestions != null && this.suggestions.size() > 0) {
				this.suggestionIndex += 1;
				if (this.suggestionIndex >= this.suggestions.size()) this.suggestionIndex = 0;
				final MediaTag tag = this.suggestions.get(this.suggestionIndex);
				setText(tag.getTag());
				setCaretPosition(tag.getTag().length());
				return;
			}

			final String input = getText();
			if (input == null || input.length() < 1) return;

			try {
				final Map<String, MediaTag> searchRes = this.tagEditor.list.tagSearch(input, 20);
				resetAutocomplete(new ArrayList<MediaTag>(searchRes.values()));

				if (searchRes.size() > 0) {
					final MediaTag tag = searchRes.values().iterator().next();
					setText(tag.getTag());
					setCaretPosition(tag.getTag().length());
				}
			}
			catch (final MorriganException e) {
				MessageDialog.showMessageDialog(this.tagEditor.getTextGUI(), "Error searching tags", e.toString());
			}
		}

		private void resetAutocomplete (final List<MediaTag> items) {
			this.suggestions = items;
			this.suggestionIndex = 0;
		}

	}

	private static class TagListBox extends AbstractListBox<MediaTag, TagListBox> {

		private final TagEditor tagEditor;

		public TagListBox (final TerminalSize preferredSize, final TagEditor tagEditor) {
			super(preferredSize);
			this.tagEditor = tagEditor;
		}

		@Override
		public synchronized Result handleKeyStroke (final KeyStroke key) {
			if (key.getKeyType() == KeyType.Character && key.getCharacter() == 'r') {
				userRefresh();
				return Result.HANDLED;
			}
			else if (key.getKeyType() == KeyType.Delete) {
				askDeleteTag();
				return Result.HANDLED;
			}
			return super.handleKeyStroke(key);
		}

		public void userRefresh () {
			try {
				reloadTags();
			}
			catch (final MorriganException e) {
				MessageDialog.showMessageDialog(this.tagEditor.getTextGUI(), "Error reloading tags", e.toString());
			}
		}

		public void reloadTags () throws MorriganException {
			clearItems();
			for (final MediaTag tag : this.tagEditor.list.getTags(this.tagEditor.item)) {
				addItem(tag);
			}
		}

		private void askDeleteTag () {
			final MediaTag tag = getSelectedItem();
			if (tag == null) return;
			if (MessageDialog.showMessageDialog(this.tagEditor.getTextGUI(), "Delete Tag", tag.toString(), MessageDialogButton.Yes, MessageDialogButton.No) != MessageDialogButton.Yes) return;
			try {
				this.tagEditor.list.removeTag(tag);
				this.tagEditor.txtNewTag.setText(tag.getTag());
				this.tagEditor.lstTags.reloadTags();
			}
			catch (final MorriganException e) {
				MessageDialog.showMessageDialog(this.tagEditor.getTextGUI(), "Error deleting tag", e.toString());
			}
		}

		@Override
		protected ListItemRenderer<MediaTag, TagListBox> createDefaultListItemRenderer () {
			return new ListItemRenderer<MediaTag, TagListBox>() {
				@Override
				public int getHotSpotPositionOnLine (final int selectedIndex) {
					return -1;
				}
			};
		}

	}

	private static String maxLength (final String s, final int maxLength) {
		if (s.length() <= maxLength) return s;
		return s.substring(0, maxLength);
	}

	public static void show (final WindowBasedTextGUI owner, final IMediaTrackList<?> list, final IMediaTrack item) throws MorriganException {
		new TagEditor(list, item).showDialog(owner);
	}

}
