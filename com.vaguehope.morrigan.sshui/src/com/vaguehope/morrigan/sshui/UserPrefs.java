package com.vaguehope.morrigan.sshui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Properties;

import com.vaguehope.morrigan.config.Config;
import com.vaguehope.morrigan.sshui.util.Quietly;

public class UserPrefs {

	private static final String PREF_FILE_NAME = "sshui.properties";

	private final Properties properties = new Properties();
	private final File prefsFile;
	private final Object[] lock = new Object[] {};
	private boolean cached = false;

	public UserPrefs () {
		this.prefsFile = new File(new File(Config.getConfigDir()), PREF_FILE_NAME);
	}

	public void putValue (final String subject, final String object, final int value) throws IOException {
		putValue(subject, object, String.valueOf(value));
	}

	public void putValue (final String subject, final String object, final String value) throws IOException {
		putValue(keyFor(subject, object), value);
	}

	public int getIntValue (final String subject, final String object, final int defaultValue) throws IOException {
		final String raw = getValue(subject, object, null);
		if (raw == null) return defaultValue;
		try {
			return Integer.parseInt(raw);
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public String getValue (final String subject, final String object, final String defaultValue) throws IOException {
		return getValue(keyFor(subject, object), defaultValue);
	}

	private static String keyFor(final String subject, final String object) {
		return String.format("%s.%s", subject, object);
	}

	private void putValue (final String key, final String value) throws IOException {
		synchronized (this.lock) {
			readFile();
			this.properties.put(key, value);
			writeFile();
		}
	}

	private String getValue (final String key, final String defaultValue) throws IOException {
		synchronized (this.lock) {
			readFileIfNeeded();
			return this.properties.getProperty(key, defaultValue);
		}
	}

	private void readFileIfNeeded () throws IOException {
		synchronized (this.lock) {
			if (!this.cached) readFile();
		}
	}

	private void writeFile () throws IOException {
		synchronized (this.lock) {
			final Writer writer = new OutputStreamWriter(new FileOutputStream(this.prefsFile), "UTF-8");
			try {
				this.properties.store(writer, "");
			}
			finally {
				Quietly.close(writer);
			}
			this.cached = false;
		}
	}

	private void readFile () throws IOException {
		synchronized (this.lock) {
			if (this.prefsFile.exists()) {
				final Reader reader = new InputStreamReader(new FileInputStream(this.prefsFile), "UTF-8");
				try {
					this.properties.load(reader);
				}
				finally {
					Quietly.close(reader);
				}
			}
			else {
				this.properties.clear();
			}
			this.cached = true;
		}
	}

}
