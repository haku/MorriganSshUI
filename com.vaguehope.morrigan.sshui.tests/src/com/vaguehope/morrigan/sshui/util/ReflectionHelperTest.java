package com.vaguehope.morrigan.sshui.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ReflectionHelperTest {

	@Test
	public void itDoesSomething() throws Exception {
		assertEquals("desu", ReflectionHelper.readField(new Example(), "foobar"));
	}

	private static class Example {
		private final String foobar = "desu";
	}

}
