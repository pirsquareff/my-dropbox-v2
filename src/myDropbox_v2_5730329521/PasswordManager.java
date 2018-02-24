package myDropbox_v2_5730329521;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;

import org.apache.commons.codec.binary.Base64;

public class PasswordManager {
    private static final int iterations = 20 * 1000;
    private static final int saltLength = 32;
    private static final int desiredKeyLength = 256;

    /**
     * Computes a salted PBKDF2 hash of given plaintext password
     */
    public static String getSaltedHash(String password) throws Exception {
        // Generate salt
        byte[] salt = SecureRandom.getInstance("SHA1PRNG").generateSeed(saltLength);
        // Store the salt with the password
        return Base64.encodeBase64String(salt) + "$" + hash(password, salt);
    }

    /**
     * Checks whether given plaintext password corresponds
     * to a stored salted hash of the password.
     */
    public static boolean check(String password, String stored) throws Exception {
        String[] saltAndPass = stored.split("\\$");
        if (saltAndPass.length != 2) {
            throw new IllegalStateException(
                    "The stored password should be in 'salt$hash' form.");
        }
        String hashOfInput = hash(password, Base64.decodeBase64(saltAndPass[0]));
        return hashOfInput.equals(saltAndPass[1]);
    }

    private static String hash(String password, byte[] salt) throws Exception {
        if (password == null || password.length() == 0)
            throw new IllegalArgumentException("Empty password is not supported.");
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        SecretKey key = f.generateSecret(new PBEKeySpec(
                password.toCharArray(), salt, iterations, desiredKeyLength)
        );
        return Base64.encodeBase64String(key.getEncoded());
    }
}