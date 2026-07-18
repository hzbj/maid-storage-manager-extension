package io.github.maidstorageextension.terminal;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

/** Password and bearer-token primitives. Raw passwords are never persisted. */
public final class TerminalPassword {
    public static final int SALT_BYTES = 16;
    public static final int TOKEN_BYTES = 32;
    public static final int ITERATIONS = 210_000;
    private static final int HASH_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TerminalPassword() {
    }

    public static byte[] salt() {
        return random(SALT_BYTES);
    }

    public static byte[] token() {
        return random(TOKEN_BYTES);
    }

    public static byte[] hashPassword(char[] password, byte[] salt) {
        if (password == null || salt == null || salt.length < 8) return new byte[0];
        KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable", exception);
        } finally {
            ((PBEKeySpec) spec).clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    public static byte[] hashToken(byte[] token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public static boolean matches(byte[] expected, byte[] actual) {
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }

    private static byte[] random(int length) {
        byte[] value = new byte[length];
        RANDOM.nextBytes(value);
        return value;
    }
}
