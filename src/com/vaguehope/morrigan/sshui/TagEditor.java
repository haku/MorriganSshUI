package com.vaguehope.morrigan.sshui;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.Border.Invisible;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.Window;
import com.googlecode.lanterna.gui.component.AbstractListBox;
import com.googlecode.lanterna.gui.component.Button;
import com.googlecode.lanterna.gui.component.Label;
import com.googlecode.lanterna.gui.component.Panel;
import com.googlecode.lanterna.gui.component.TextBox;
import com.googlecode.lanterna.gui.dialog.DialogButtons;
import com.googlecode.lanterna.gui.dialog.DialogResult;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.terminal.TerminalPosition;
import com.googlecode.lanterna.terminal.TerminalSize;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaTrack;
import com.vaguehope.morrigan.model.media.IMediaTrackList;
import com.vaguehope.morrigan.model.media.MediaTag;
import com.vaguehope.morrigan.model.media.MediaTagClassification;
import com.vaguehope.morrigan.model.media.MediaTagType;
import com.vaguehope.morrigan.sshui.util.ReflectionHelper;

public class TagEditor extends Window {

	protected final IMediaTrackList<?> list;
	protected final IMediaTrack item;

	protected final TextBox txtNewTag;
	protected final TagListBox lstTags;

	public TagEditor (final IMediaTrackList<?> list, final IMediaTrack item) throws MorriganException {
		super(maxLength(item.getTitle(), 50)); // FIXME magic number.

		this.list = list;
		this.item = item;

		this.txtNewTag = new AddTagTextBox(50, this);
		addComponent(this.txtNewTag);

		this.lstTags = new TagListBox(new TerminalSize(50, 10), this); // FIXME hard-coded size.
		addComponent(this.lstTags);

		final Panel cancelPanel = new Panel(new Invisible(), Panel.Orientation.HORISONTAL);
		cancelPanel.addComponent(new Label("                "));
		cancelPanel.addComponent(new Button("Close", new Action() {
			@Override
			public void doAction () {
				close();
			}
		}));
		addComponent(cancelPanel);

		final Panel contentPane = (Panel) ReflectionHelper.readField(this, "contentPane");
		contentPane.addShortcut(Key.Kind.Escape, new Action() {
			@Override
			public void doAction () {
				close();
			}
		});

		this.lstTags.reloadTags();
	}

	private static class AddTagTextBox extends TextBox {

		private final TagEditor tagEditor;

		public AddTagTextBox (final int width, final TagEditor tagEditor) {
			super("", width);
			this.tagEditor = tagEditor;
		}

		@Override
		public Result keyboardInteraction (final Key key) {
			switch (key.getKind()) {
				case Enter:
					addTag();
					return Result.EVENT_HANDLED;
				default:
					return super.keyboardInteraction(key);
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
				MessageBox.showMessageBox(getGUIScreen(), "Error adding tag", e.toString());
			}
		}

	}

	private static class TagListBox extends AbstractListBox {

		private final TagEditor tagEditor;

		public TagListBox (final TerminalSize preferredSize, final TagEditor tagEditor) {
			super(preferredSize);
			this.tagEditor = tagEditor;
		}

		@Override
		protected Result unhandledKeyboardEvent (final Key key) {
			if (key.getKind() == Key.Kind.NormalKey && key.getCharacter() == 'r') {
				userRefresh();
				return Result.EVENT_HANDLED;
			}
			else if (key.getKind() == Key.Kind.Delete) {
				askDeleteTag();
				return Result.EVENT_HANDLED;
			}
			return Result.EVENT_NOT_HANDLED;
		}

		public void userRefresh () {
			try {
				reloadTags();
			}
			catch (final MorriganException e) {
				MessageBox.showMessageBox(getGUIScreen(), "Error reloading tags", e.toString());
			}
		}

		public void reloadTags () throws MorriganException {
			clearItems();
			for (final MediaTag tag : this.tagEditor.list.getTags(this.tagEditor.item)) {
				addItem(tag);
			}
		}

		private void askDeleteTag () {
			final MediaTag tag = (MediaTag) getSelectedItem();
			if (tag == null) return;
			if (MessageBox.showMessageBox(getGUIScreen(), "Delete Tag", tag.toString(), DialogButtons.YES_NO) != DialogResult.YES) return;
			try {
				this.tagEditor.list.removeTag(tag);
				this.tagEditor.txtNewTag.setText(tag.getTag());
				this.tagEditor.lstTags.reloadTags();
			}
			catch (final MorriganException e) {
				MessageBox.showMessageBox(getGUIScreen(), "Error deleting tag", e.toString());
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

	private static String maxLength (final String s, final int maxLength) {
		if (s.length() <= maxLength) return s;
		return s.substring(0, maxLength);
	}

	public static void show (final GUIScreen owner, final IMediaTrackList<?> list, final IMediaTrack item) throws MorriganException {
		owner.showWindow(new TagEditor(list, item), GUIScreen.Position.CENTER);
		owner.getScreen().setCursorPosition(null);
	}

}
