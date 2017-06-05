package com.vaguehope.morrigan.sshui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.input.KeyStroke;


public abstract class DefaultFace implements Face {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultFace.class);
	private final FaceNavigation navigation;

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

}
