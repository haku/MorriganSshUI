package com.vaguehope.morrigan.sshui.ssh;

import java.io.File;
import java.io.FileNotFoundException;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidParameterSpecException;
import java.security.spec.RSAPublicKeySpec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.util.encoders.Base64;
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

	// From:
	// https://stackoverflow.com/questions/3531506/using-public-key-from-authorized-keys-with-java-security .
	// https://github.com/davidcarboni-archive/discharges-jwt/blob/master/src/main/java/uk/gov/ros/discharges/security/OpenSshPublicKey.java

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
					this.bytes = Base64.decode(part.getBytes());
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
			else if (type.startsWith("ecdsa-sha2-") &&
					(type.endsWith("nistp256") || type.endsWith("nistp384") || type.endsWith("nistp521"))) {
				// Based on RFC 5656, section 3.1 (https://tools.ietf.org/html/rfc5656#section-3.1)
				String identifier = decodeType();
				BigInteger q = decodeBigInt();
				ECPoint ecPoint = getECPoint(q, identifier);
				ECParameterSpec ecParameterSpec = getECParameterSpec(identifier);
				ECPublicKeySpec spec = new ECPublicKeySpec(ecPoint, ecParameterSpec);
				return KeyFactory.getInstance("EC").generatePublic(spec);
			}
			else {
				throw new IllegalArgumentException("unknown type in authorized_keys: " + type);
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

		/**
		 * Provides a means to get from a parsed Q value to the X and Y point values.
		 * that can be used to create and ECPoint compatible with ECPublicKeySpec.
		 *
		 * @param q          According to RFC 5656:
		 *                   "Q is the public key encoded from an elliptic curve point into an octet string"
		 * @param identifier According to RFC 5656:
		 *                   "The string [identifier] is the identifier of the elliptic curve domain parameters."
		 * @return An ECPoint suitable for creating a JCE ECPublicKeySpec.
		 */
		private ECPoint getECPoint(final BigInteger q, final String identifier) {
			String name = identifier.replace("nist", "sec") + "r1";
			ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(name);
			org.bouncycastle.math.ec.ECPoint point = ecSpec.getCurve().decodePoint(q.toByteArray());
			BigInteger x = point.getAffineXCoord().toBigInteger();
			BigInteger y = point.getAffineYCoord().toBigInteger();
			return new ECPoint(x, y);
		}

		/**
		 * Gets the curve parameters for the given key type identifier.
		 *
		 * @param identifier According to RFC 5656:
		 *                   "The string [identifier] is the identifier of the elliptic curve domain parameters."
		 * @return An ECParameterSpec suitable for creating a JCE ECPublicKeySpec.
		 */
		private ECParameterSpec getECParameterSpec(final String identifier) {
			try {
				// http://www.bouncycastle.org/wiki/pages/viewpage.action?pageId=362269#SupportedCurves(ECDSAandECGOST)-NIST(aliasesforSECcurves)
				String name = identifier.replace("nist", "sec") + "r1";
				AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
				parameters.init(new ECGenParameterSpec(name));
				return parameters.getParameterSpec(ECParameterSpec.class);
			}
			catch (InvalidParameterSpecException e) {
				throw new IllegalArgumentException("Unable to get parameter spec for identifier " + identifier, e);
			}
			catch (NoSuchAlgorithmException e) {
				throw new IllegalArgumentException("Unable to get parameter spec for identifier " + identifier, e);
			}
		}

	}

}
