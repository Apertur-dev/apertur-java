package ca.apertur.sdk;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

/**
 * Static utility for encrypting image data using AES-256-GCM with RSA-OAEP key wrapping.
 *
 * <p>The encrypted payload is suitable for use with the Apertur encrypted upload endpoint.
 * The server's RSA public key can be obtained via {@link ca.apertur.sdk.resource.Encryption#getServerKey()}.
 */
public final class Crypto {

    private static final int AES_KEY_SIZE = 256;
    private static final int GCM_IV_SIZE = 12;
    private static final int GCM_TAG_BITS = 128;

    private Crypto() {
    }

    /**
     * Encrypts image data using AES-256-GCM and wraps the AES key with RSA-OAEP (SHA-256).
     *
     * <p>The returned map contains the following base64-encoded fields:
     * <ul>
     *   <li>{@code encryptedKey} -- the AES key wrapped with RSA-OAEP</li>
     *   <li>{@code iv} -- the 12-byte GCM initialization vector</li>
     *   <li>{@code encryptedData} -- the ciphertext with the appended GCM auth tag</li>
     *   <li>{@code algorithm} -- always {@code "RSA-OAEP+AES-256-GCM"}</li>
     * </ul>
     *
     * @param imageData    the raw image bytes to encrypt
     * @param publicKeyPem the PEM-encoded RSA public key from the server
     * @return a map with the encrypted payload fields (all base64-encoded)
     */
    public static Map<String, String> encryptImage(byte[] imageData, String publicKeyPem) {
        try {
            // Generate random AES-256 key
            SecureRandom random = new SecureRandom();
            byte[] aesKeyBytes = new byte[AES_KEY_SIZE / 8];
            random.nextBytes(aesKeyBytes);
            SecretKeySpec aesKey = new SecretKeySpec(aesKeyBytes, "AES");

            // Generate random 12-byte IV
            byte[] iv = new byte[GCM_IV_SIZE];
            random.nextBytes(iv);

            // Encrypt image with AES-256-GCM
            Cipher aesCipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            aesCipher.init(Cipher.ENCRYPT_MODE, aesKey, gcmSpec);
            byte[] encryptedData = aesCipher.doFinal(imageData);
            // Note: Java's GCM mode appends the auth tag to the ciphertext automatically

            // Parse RSA public key from PEM
            PublicKey rsaPublicKey = parsePublicKey(publicKeyPem);

            // Wrap AES key with RSA-OAEP (SHA-256)
            Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            OAEPParameterSpec oaepParams = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT
            );
            rsaCipher.init(Cipher.WRAP_MODE, rsaPublicKey, oaepParams);
            byte[] wrappedKey = rsaCipher.wrap(aesKey);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("encryptedKey", Base64.getEncoder().encodeToString(wrappedKey));
            result.put("iv", Base64.getEncoder().encodeToString(iv));
            result.put("encryptedData", Base64.getEncoder().encodeToString(encryptedData));
            result.put("algorithm", "RSA-OAEP+AES-256-GCM");
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt image: " + e.getMessage(), e);
        }
    }

    private static PublicKey parsePublicKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }
}
