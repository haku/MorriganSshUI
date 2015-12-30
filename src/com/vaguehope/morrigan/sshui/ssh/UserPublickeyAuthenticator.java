package com.vaguehope.morrigan.sshui.ssh;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import org.apache.sshd.common.util.Base64;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserPublickeyAuthenticator implements PublickeyAuthenticator {

	private static final Logger LOG = LoggerFactory.getLogger(UserPublickeyAuthenticator.class);

	private final String currentUserName;
	private final Set<PublicKey> publicKeys;

	public UserPublickeyAuthenticator () throws FileNotFoundException, GeneralSecurityException {
		this.currentUserName = System.getProperty("user.name");
		this.publicKeys = parseAuthorizedKeysFile();
		LOG.info("Found {} public keys.", this.publicKeys.size());
	}

	@Override
	public boolean authenticate (final String username, final PublicKey key, final ServerSession session) {
		return this.currentUserName.equals(username) && this.publicKeys.contains(key);
	}

	private static Set<PublicKey> parseAuthorizedKeysFile () throws FileNotFoundException, GeneralSecurityException {
		final File mnFile = new File(new File(new File(System.getProperty("user.home")), ".morrigan"), "authorized_keys");
		final File sshFile = new File(new File(new File(System.getProperty("user.home")), ".ssh"), "authorized_keys");
		if (mnFile.exists()) {
			return parseAuthorizedKeysFile(mnFile);
		}
		else if (sshFile.exists()) {
			return parseAuthorizedKeysFile(sshFile);
		}
		return Collections.emptySet();
	}

	// From https://stackoverflow.com/questions/3531506/using-public-key-from-authorized-keys-with-java-security .

	private static Set<PublicKey> parseAuthorizedKeysFile (final File file) throws FileNotFoundException, GeneralSecurityException {
		final AuthorizedKeysDecoder decoder = new AuthorizedKeysDecoder();
		final Scanner scanner = new Scanner(file).useDelimiter("\n");
		final Set<PublicKey> ret = new HashSet<PublicKey>();
		while (scanner.hasNext()) {
			ret.add(decoder.decodePublicKey(scanner.next()));
		}
		scanner.close();
		return ret;
	}

	public static class AuthorizedKeysDecoder {
		private byte[] bytes;
		private int pos;

		public PublicKey decodePublicKey (final String keyLine) throws GeneralSecurityException {
			this.bytes = null;
			this.pos = 0;

			// look for the Base64 encoded part of the line to decode
			// both ssh-rsa and ssh-dss begin with "AAAA" due to the length bytes
			for (final String part : keyLine.split(" ")) {
				if (part.startsWith("AAAA")) {
					this.bytes = Base64.decodeBase64(part.getBytes());
					break;
				}
			}
			if (this.bytes == null) {
				throw new IllegalArgumentException("no Base64 part to decode");
			}

			final String type = decodeType();
			if (type.equals("ssh-rsa")) {
				final BigInteger e = decodeBigInt();
				final BigInteger m = decodeBigInt();
				final RSAPublicKeySpec spec = new RSAPublicKeySpec(m, e);
				return KeyFactory.getInstance("RSA").generatePublic(spec);
			}
			else if (type.equals("ssh-dss")) {
				final BigInteger p = decodeBigInt();
				final BigInteger q = decodeBigInt();
				final BigInteger g = decodeBigInt();
				final BigInteger y = decodeBigInt();
				final DSAPublicKeySpec spec = new DSAPublicKeySpec(y, p, q, g);
				return KeyFactory.getInstance("DSA").generatePublic(spec);
			}
			else {
				throw new IllegalArgumentException("unknown type " + type);
			}
		}

		private String decodeType () {
			final int len = decodeInt();
			final String type = new String(this.bytes, this.pos, len);
			this.pos += len;
			return type;
		}

		private int decodeInt () {
			return ((this.bytes[this.pos++] & 0xFF) << 24) | ((this.bytes[this.pos++] & 0xFF) << 16)
					| ((this.bytes[this.pos++] & 0xFF) << 8) | (this.bytes[this.pos++] & 0xFF);
		}

		private BigInteger decodeBigInt () {
			final int len = decodeInt();
			final byte[] bigIntBytes = new byte[len];
			System.arraycopy(this.bytes, this.pos, bigIntBytes, 0, len);
			this.pos += len;
			return new BigInteger(bigIntBytes);
		}

	}

}
