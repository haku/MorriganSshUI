package com.vaguehope.morrigan.sshui.ssh;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.googlecode.lanterna.input.BasicCharacterPattern;
import com.googlecode.lanterna.input.CharacterPattern;
import com.googlecode.lanterna.input.KeyDecodingProfile;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

public class SshInputMapping implements KeyDecodingProfile {

	private static final List<CharacterPattern> PATTERNS = new ArrayList<CharacterPattern>(
			Arrays.asList(
					new CharacterPattern[] {
							new BasicCharacterPattern(new KeyStroke(KeyType.Enter), '\r')
					}));

	@Override
	public Collection<CharacterPattern> getPatterns () {
		return PATTERNS;
	}

}
