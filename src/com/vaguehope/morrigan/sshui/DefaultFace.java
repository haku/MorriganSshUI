package com.vaguehope.morrigan.sshui;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.input.KeyStroke;

public abstract class DefaultFace implements Face {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultFace.class);

	private final FaceNavigation navigation;
	private final BlockingQueue<Callable<Void>> onUiThread = new LinkedBlockingQueue<Callable<Void>>();

	public DefaultFace (final FaceNavigation navigation) {
		this.navigation = navigation;
	}

	@Override
	public boolean onInput (final KeyStroke k, final WindowBasedTextGUI gui) throws Exception {
		switch (k.getKeyType()) {
			case Escape:
				return this.navigation.backOneLevel();
			case Character:
				switch (k.getCharacter()) {
					case 'q':
						return this.navigation.backOneLevel();
					default:
				}
			default:
				return false;
		}
	}

	@Override
	public void onFaceResult (final Object result) throws Exception { // NOSONAR throws Exception is part of API.
		LOG.warn("Face returned value that was not used: {}", result);
	}

	@Override
	public void onClose () throws Exception {
		// Do nothing by default.
	}

	public void scheduleOnUiThread (final Callable<Void> callable) {
		this.onUiThread.add(callable);
	}

	@Override
	public boolean processEvents () {
		boolean ret = false;
		Callable<Void> c;
		while ((c = this.onUiThread.poll()) != null) {
			try {
				c.call();
			}
			catch (final Exception e) {
				LOG.error("Background task failed.", e);
			}
			ret = true;
		}
		return ret;
	}

}
