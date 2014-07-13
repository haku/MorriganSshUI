package com.vaguehope.morrigan.sshui;

import java.util.Collection;
import java.util.List;

public class MenuHelper {

	enum VDirection {
		UP, DOWN;
	}

	public static int moveListSelectionIndex (final int selectedIndex, final VDirection direction, final List<?> list) {
		final int limit = sizeOf(list);
		int i = selectedIndex;
		if (i < 0) {
			i = limit > 0 ? 0 : -1;
		}
		else if (direction == VDirection.UP) {
			i--;
			if (i < 0) i = 0;
		}
		else {
			i++;
			if (i >= limit) i = limit - 1;
		}
		return i;
	}

	/**
	 * Returns new selected item.
	 */
	public static Object moveListSelection (final Object selectedItem, final VDirection direction, final List<?>... lists) {
		final int limit = sumSizes(lists);
		int i = listOfListsIndexOf(selectedItem, lists);
		if (i < 0) {
			i = limit > 0 ? 0 : -1;
		}
		else if (direction == VDirection.UP) {
			i--;
			if (i < 0) i = 0;
		}
		else {
			i++;
			if (i >= limit) i = limit - 1;
		}
		return listOfListsGet(i, lists);
	}

	private static int sumSizes (final List<?>... lists) {
		int count = 0;
		for (final List<?> list : lists) {
			count += sizeOf(list);
		}
		return count;
	}

	private static int listOfListsIndexOf (final Object item, final List<?>... lists) {
		if (item == null) return -1;
		int offset = 0;
		for (final List<?> list : lists) {
			final int x = list != null ? list.indexOf(item) : -1;
			if (x >= 0) return offset + x;
			offset += sizeOf(list);
		}
		return -1;
	}

	private static Object listOfListsGet (final int i, final List<?>... lists) {
		if (i < 0) return null;
		int x = i;
		for (final List<?> list : lists) {
			if (x < sizeOf(list)) return listGet(list, x);
			x -= sizeOf(list);
		}
		return null;
	}

	private static int sizeOf (final Collection<?> c) {
		return c != null ? c.size() : 0;
	}

	private static <T> T listGet (final List<T> list, final int i) {
		if (list == null) return null;
		return list.get(i);
	}

}
