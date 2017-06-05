package com.googlecode.lanterna.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
