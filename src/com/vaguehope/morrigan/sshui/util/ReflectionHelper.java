package com.vaguehope.morrigan.sshui.util;

import java.lang.reflect.Field;

public final class ReflectionHelper {

	private ReflectionHelper () {
		throw new AssertionError();
	}

	public static Object readField (final Object obj, final String fieldName) {
		final Class<?> c = obj.getClass();
		try {
			final Field f = findField(c, fieldName);
			f.setAccessible(true);
			return f.get(obj);
		}
		catch (final Exception e) {
			throw new IllegalStateException("Failed to read field '" + fieldName + "' from '" + obj + "'.", e);
		}
	}

	private static Field findField (final Class<?> rootCls, final String fieldName) {
		Class<?> cls = rootCls;
		Field field = getField(cls, fieldName);
		while (field == null && cls != Object.class) {
			cls = cls.getSuperclass();
			field = getField(cls, fieldName);
		}
		if (field == null) throw new IllegalStateException("Failed to find field '" + fieldName + "' in class '" + rootCls + "' or above.");
		return field;
	}

	private static Field getField (final Class<?> cls, final String fieldName) {
		try {
			return cls.getDeclaredField(fieldName);
		}
		catch (final NoSuchFieldException e) {
			return null;
		}
	}
}
