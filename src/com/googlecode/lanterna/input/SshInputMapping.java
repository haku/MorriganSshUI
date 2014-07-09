package com.googlecode.lanterna.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class SshInputMapping extends KeyMappingProfile {

	private static final List<CharacterPattern> PATTERNS = new ArrayList<CharacterPattern>(
			Arrays.asList(
					new CharacterPattern[] {
							new BasicCharacterPattern(new Key(Key.Kind.Enter), '\r')
					}));

	@Override
	Collection<CharacterPattern> getPatterns () {
		return PATTERNS;
	}

}
