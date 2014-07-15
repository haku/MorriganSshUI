package com.vaguehope.morrigan.sshui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class DefaultFace implements Face {

	private static final Logger LOG = LoggerFactory.getLogger(DefaultFace.class);

	@Override
	public void onFaceResult (final Object result) throws Exception {
		LOG.warn("Face returned value that was not used: {}", result);
	}

}
