package ca.apertur.sdk;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static methods for verifying Apertur webhook signatures, and for signing
 * outgoing API requests.
 *
 * <p>Three verification signature schemes are supported:
 * <ul>
 *   <li>{@link #verifyWebhookSignature} -- image delivery webhooks (HMAC-SHA256, hex)</li>
 *   <li>{@link #verifyEventSignature} -- HMAC event webhooks with timestamp (hex)</li>
 *   <li>{@link #verifySvixSignature} -- Svix-signed event webhooks (base64)</li>
 * </ul>
 *
 * <p>{@link #signRequest} signs an outgoing API request with a request-specific
 * signature base (method + path + body hash), for use by the SDK's HTTP transport.
 *
 * <p>All comparisons use {@link MessageDigest#isEqual} for timing-safe comparison.
 */
public final class Signature {

    private Signature() {
    }

    /**
     * Verifies an image delivery webhook signature.
     *
     * <p>The signature header has the form {@code sha256=<hex>} and is computed as
     * {@code HMAC-SHA256(body, secret)}.
     *
     * @param body      the raw request body
     * @param signature the signature header value (e.g. {@code "sha256=abc123..."})
     * @param secret    the webhook secret
     * @return {@code true} if the signature is valid
     */
    public static boolean verifyWebhookSignature(String body, String signature, String secret) {
        try {
            byte[] expected = hmacSha256(secret.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
            String expectedHex = bytesToHex(expected);
            String sig = signature.startsWith("sha256=") ? signature.substring(7) : signature;
            if (expectedHex.length() != sig.length()) {
                return false;
            }
            return MessageDigest.isEqual(
                    hexToBytes(expectedHex),
                    hexToBytes(sig)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies an event webhook signature using the HMAC method.
     *
     * <p>The signed content is {@code "${timestamp}.${body}"}, hashed with HMAC-SHA256.
     * The signature header has the form {@code sha256=<hex>}.
     *
     * @param body      the raw request body
     * @param timestamp the timestamp header value (Unix seconds)
     * @param signature the signature header value (e.g. {@code "sha256=abc123..."})
     * @param secret    the webhook secret
     * @return {@code true} if the signature is valid
     */
    public static boolean verifyEventSignature(String body, String timestamp, String signature, String secret) {
        try {
            String signatureBase = timestamp + "." + body;
            byte[] expected = hmacSha256(secret.getBytes(StandardCharsets.UTF_8), signatureBase.getBytes(StandardCharsets.UTF_8));
            String expectedHex = bytesToHex(expected);
            String sig = signature.startsWith("sha256=") ? signature.substring(7) : signature;
            if (expectedHex.length() != sig.length()) {
                return false;
            }
            return MessageDigest.isEqual(
                    hexToBytes(expectedHex),
                    hexToBytes(sig)
            );
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Verifies an event webhook signature using the Svix method.
     *
     * <p>The signed content is {@code "${svixId}.${timestamp}.${body}"}, hashed with
     * HMAC-SHA256 using the hex-decoded secret. The signature header has the form
     * {@code v1,<base64>}.
     *
     * @param body      the raw request body
     * @param svixId    the Svix ID header value
     * @param timestamp the timestamp header value
     * @param signature the signature header value (e.g. {@code "v1,base64..."})
     * @param secret    the hex-encoded secret
     * @return {@code true} if the signature is valid
     */
    public static boolean verifySvixSignature(String body, String svixId, String timestamp, String signature, String secret) {
        try {
            String signatureBase = svixId + "." + timestamp + "." + body;
            byte[] secretBytes = hexToBytes(secret);
            byte[] expectedBytes = hmacSha256(secretBytes, signatureBase.getBytes(StandardCharsets.UTF_8));
            String expectedBase64 = Base64.getEncoder().encodeToString(expectedBytes);
            String sig = signature.startsWith("v1,") ? signature.substring(3) : signature;

            byte[] expectedDecoded = Base64.getDecoder().decode(expectedBase64);
            byte[] sigDecoded = Base64.getDecoder().decode(sig);
            if (expectedDecoded.length != sigDecoded.length) {
                return false;
            }
            return MessageDigest.isEqual(expectedDecoded, sigDecoded);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Signs an outgoing API request (HMAC SHA256 method).
     *
     * <p>Returns the headers {@code X-Aptr-Signature: sha256=<hex>} and
     * {@code X-Aptr-Timestamp: <unix seconds>}, computed as
     * {@code HMAC-SHA256("${timestamp}.${method}.${path}.${sha256hex(body)}", secret)}.
     *
     * <ul>
     *   <li>{@code method} is uppercased.</li>
     *   <li>{@code path} must be the exact request path sent to the transport,
     *       verbatim (for apertur this includes the {@code /api/v1} prefix).</li>
     *   <li>{@code body} is the exact serialized bytes/string sent as the request
     *       body; {@code null} hashes as the empty string.</li>
     * </ul>
     *
     * @param secret    the signing secret
     * @param method    the HTTP method (any case; uppercased before signing)
     * @param path      the exact request path, verbatim
     * @param body      the exact request body bytes/string sent on the wire, or {@code null}
     * @param timestamp the unix timestamp (seconds) to sign with
     * @return an immutable map containing {@code X-Aptr-Signature} and {@code X-Aptr-Timestamp}
     */
    public static Map<String, String> signRequest(String secret, String method, String path, String body, long timestamp) {
        try {
            byte[] bodyBytes = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
            String bodyHash = bytesToHex(MessageDigest.getInstance("SHA-256").digest(bodyBytes));
            String signatureBase = timestamp + "." + method.toUpperCase() + "." + path + "." + bodyHash;
            String signature = bytesToHex(hmacSha256(secret.getBytes(StandardCharsets.UTF_8), signatureBase.getBytes(StandardCharsets.UTF_8)));

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("X-Aptr-Signature", "sha256=" + signature);
            headers.put("X-Aptr-Timestamp", String.valueOf(timestamp));
            return Collections.unmodifiableMap(headers);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign request", e);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
