package com.vaguehope.morrigan.sshui;

import com.vaguehope.morrigan.model.media.MediaFactory;
import com.vaguehope.morrigan.player.PlayerReader;
import com.vaguehope.morrigan.tasks.AsyncTasksRegister;

public class MnContext {

	private final PlayerReader playerReader;
	private final MediaFactory mediaFactory;
	private final AsyncTasksRegister asyncTasksRegister;

	public MnContext (final PlayerReader playerReader, final MediaFactory mediaFactory, final AsyncTasksRegister asyncTasksRegister) {
		this.playerReader = playerReader;
		this.mediaFactory = mediaFactory;
		this.asyncTasksRegister = asyncTasksRegister;
	}

	public PlayerReader getPlayerReader () {
		return this.playerReader;
	}

	public MediaFactory getMediaFactory () {
		return this.mediaFactory;
	}

	public AsyncTasksRegister getAsyncTasksRegister () {
		return this.asyncTasksRegister;
	}

}
