package com.vaguehope.morrigan.sshui.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class Quietly {

	private Quietly () {
		throw new AssertionError();
	}

	public static boolean await (final CountDownLatch latch, final long timeout, final TimeUnit unit) {
		try {
			return latch.await(timeout, unit);
		}
		catch (final InterruptedException e) {
			return false;
		}
	}

	public static void sleep (final long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (final InterruptedException e) {}
	}

	public static void close (final Closeable c) {
		try {
			c.close();
		}
		catch (final IOException e) {}
	}

}
