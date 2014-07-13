package com.vaguehope.morrigan.sshui;

import java.util.ArrayList;
import java.util.List;

import com.googlecode.lanterna.gui.Action;
import com.googlecode.lanterna.gui.GUIScreen;
import com.googlecode.lanterna.gui.dialog.ActionListDialog;
import com.googlecode.lanterna.gui.dialog.MessageBox;
import com.googlecode.lanterna.input.Key;
import com.googlecode.lanterna.input.Key.Kind;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.ScreenCharacterStyle;
import com.googlecode.lanterna.screen.ScreenWriter;
import com.vaguehope.morrigan.model.db.IDbColumn;
import com.vaguehope.morrigan.model.exceptions.MorriganException;
import com.vaguehope.morrigan.model.media.IMediaItemStorageLayer.SortDirection;
import com.vaguehope.morrigan.model.media.IMixedMediaDb;
import com.vaguehope.morrigan.model.media.IMixedMediaItem;
import com.vaguehope.morrigan.model.media.MediaListReference;
import com.vaguehope.morrigan.sshui.MenuHelper.VDirection;
import com.vaguehope.sqlitewrapper.DbException;

public class DbFace implements Face {

	private final FaceNavigation navigation;
	private final MnContext mnContext;
	private final MediaListReference listReference;
	private final IMixedMediaDb db;

	private List<IMixedMediaItem> mediaItems;
	private int selectedItemIndex;
	private int queueScrollTop = 0;
	private int pageSize = 1;

	public DbFace (final FaceNavigation navigation, final MnContext mnContext, final MediaListReference listReference) throws DbException, MorriganException {
		this.navigation = navigation;
		this.mnContext = mnContext;
		this.listReference = listReference;

		if (listReference.getType() == MediaListReference.MediaListType.LOCALMMDB) {
			this.db = mnContext.getMediaFactory().getLocalMixedMediaDb(listReference.getIdentifier());
			this.db.read();
		}
		else {
			this.db = null;
		}
	}

	@Override
	public boolean onInput (final Key k, final GUIScreen gui) {

		// TODO
		// - help screen.

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
			case Enter:
				menuEnter(gui);
				return true;
			case NormalKey:
				switch (k.getCharacter()) {
					case 'q':
						return this.navigation.backOneLevel();
					case ' ':
						menuClick(gui);
						return true;
					case 'g':
						menuMoveEnd(VDirection.UP);
						return true;
					case 'G':
						menuMoveEnd(VDirection.DOWN);
						return true;
					case 'o':
					case 's':
						askSortColumn(gui);
						return true;
					default:
				}
			default:
				return false;
		}
	}

	private void menuMove (final Key k, final int distance) {
		this.selectedItemIndex = MenuHelper.moveListSelectionIndex(this.selectedItemIndex,
				k.getKind() == Kind.ArrowUp || k.getKind() == Kind.PageUp
						? VDirection.UP
						: VDirection.DOWN,
				distance,
				this.mediaItems);
	}

	private void menuMoveEnd (final VDirection direction) {
		if (this.mediaItems == null || this.mediaItems.size() < 1) return;
		switch (direction) {
			case UP:
				this.selectedItemIndex = 0;
				break;
			case DOWN:
				this.selectedItemIndex = this.mediaItems.size() - 1;
				break;
			default:
		}
	}

	private void menuClick (final GUIScreen gui) {
		if (this.selectedItemIndex < 0) return;
		MessageBox.showMessageBox(gui, "TODO", "Click: " + this.selectedItemIndex);
	}

	private void menuEnter (final GUIScreen gui) {
		if (this.selectedItemIndex < 0) return;
		MessageBox.showMessageBox(gui, "TODO", "Enter: " + this.selectedItemIndex);
	}

	private void askSortColumn (final GUIScreen gui) {
		final List<IDbColumn> cols = this.db.getDbLayer().getMediaTblColumns();
		final List<Action> actions = new ArrayList<Action>();
		for (final IDbColumn col : cols) {
			if (col.getHumanName() != null) {
				actions.add(new SortColumnAction(this.db, col, SortDirection.ASC));
				actions.add(new SortColumnAction(this.db, col, SortDirection.DESC));
			}
		}
		ActionListDialog.showActionListDialog(gui, "Sort Order", "Current: " + PlayerHelper.sortSummary(this.db),
				actions.toArray(new Action[actions.size()]));
	}

	@Override
	public void writeScreen (final Screen scr, final ScreenWriter w) {
		if (this.db != null) {
			writeDbToScreen(scr, w);
		}
		else {
			w.drawString(0, 0, "Unable to show " + this.listReference);
		}
	}

	private void writeDbToScreen (final Screen scr, final ScreenWriter w) {
		int l = 0;
		w.drawString(0, l++, String.format("DB %s: %s   %s",
				this.db.getListName(), PlayerHelper.dbSummary(this.db), PlayerHelper.sortSummary(this.db)));

		this.mediaItems = this.db.getMediaItems();

		this.pageSize = scr.getTerminalSize().getRows() - l;
		if (this.selectedItemIndex >= 0) {
			if (this.selectedItemIndex - this.queueScrollTop >= this.pageSize) {
				this.queueScrollTop = this.selectedItemIndex - this.pageSize + 1;
			}
			else if (this.selectedItemIndex < this.queueScrollTop) {
				this.queueScrollTop = this.selectedItemIndex;
			}
		}

		for (int i = this.queueScrollTop; i < this.mediaItems.size(); i++) {
			if (i > this.queueScrollTop + this.pageSize) break;
			final IMixedMediaItem item = this.mediaItems.get(i);
			if (i == this.selectedItemIndex) {
				w.drawString(1, l++, String.valueOf(item), ScreenCharacterStyle.Reverse);
			}
			else {
				w.drawString(1, l++, String.valueOf(item));
			}
		}
	}

	private static class SortColumnAction implements Action {

		private final IMixedMediaDb db;
		private final IDbColumn col;
		private final SortDirection direction;

		public SortColumnAction (final IMixedMediaDb db, final IDbColumn col, final SortDirection direction) {
			this.db = db;
			this.col = col;
			this.direction = direction;
		}

		@Override
		public String toString () {
			return String.format("%s %s", this.col.getHumanName(), this.direction);
		}

		@Override
		public void doAction () {
			try {
				this.db.setSort(this.col, this.direction);
			}
			catch (final MorriganException e) {
				// TODO Auto-generated catch block
				throw new RuntimeException(e);
			}
		}

	}

}
