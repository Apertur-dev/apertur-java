package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;
import ca.apertur.sdk.Crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Image upload operations (plain and encrypted).
 *
 * <p>Supports uploading images from a {@link Path}, {@code byte[]}, or {@link InputStream}.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 *
 * // Upload from file path
 * Map<String, Object> result = client.upload().image(uuid, Path.of("/tmp/photo.jpg"), Map.of(
 *     "filename", "photo.jpg",
 *     "mimeType", "image/jpeg"
 * ));
 *
 * // Encrypted upload
 * Map<String, Object> serverKey = client.encryption().getServerKey();
 * byte[] fileData = Files.readAllBytes(Path.of("/tmp/photo.jpg"));
 * Map<String, Object> result = client.upload().imageEncrypted(
 *     uuid, fileData, (String) serverKey.get("publicKey"), Map.of(
 *         "filename", "photo.jpg",
 *         "mimeType", "image/jpeg"
 *     )
 * );
 * }</pre>
 */
public final class Upload {

    private final AperturHttpClient http;

    /**
     * Creates a new Upload resource.
     *
     * @param http the HTTP client
     */
    public Upload(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Uploads an image from a file path.
     *
     * @param uuid    the session UUID
     * @param file    the path to the image file
     * @param options upload options (e.g. filename, mimeType, source, password)
     * @return the upload result
     */
    public Map<String, Object> image(String uuid, Path file, Map<String, Object> options) {
        try {
            byte[] fileData = Files.readAllBytes(file);
            String filename = getOption(options, "filename", file.getFileName().toString());
            return doMultipartUpload(uuid, fileData, filename, options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads an image from a byte array.
     *
     * @param uuid     the session UUID
     * @param file     the image data bytes
     * @param filename the filename
     * @param options  upload options (e.g. mimeType, source, password)
     * @return the upload result
     */
    public Map<String, Object> image(String uuid, byte[] file, String filename, Map<String, Object> options) {
        return doMultipartUpload(uuid, file, filename, options);
    }

    /**
     * Uploads an image from an input stream.
     *
     * @param uuid     the session UUID
     * @param file     the image data stream
     * @param filename the filename
     * @param options  upload options (e.g. mimeType, source, password)
     * @return the upload result
     */
    public Map<String, Object> image(String uuid, InputStream file, String filename, Map<String, Object> options) {
        try {
            byte[] fileData = readAllBytes(file);
            return doMultipartUpload(uuid, fileData, filename, options);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read input stream: " + e.getMessage(), e);
        }
    }

    /**
     * Uploads an encrypted image. The image data is encrypted client-side using AES-256-GCM
     * with the AES key wrapped via RSA-OAEP using the server's public key.
     *
     * @param uuid      the session UUID
     * @param fileData  the raw image bytes to encrypt
     * @param publicKey the PEM-encoded RSA public key from the server
     * @param options   upload options (e.g. filename, mimeType, source, password)
     * @return the upload result
     */
    public Map<String, Object> imageEncrypted(String uuid, byte[] fileData, String publicKey, Map<String, Object> options) {
        Map<String, String> encrypted = Crypto.encryptImage(fileData, publicKey);

        String filename = getOption(options, "filename", "image.jpg");
        // Caller-provided source only — the server validates against the
        // ImageSource enum and rejects unknown values (e.g. "sdk") with a 400.
        String source = getOption(options, "source", null);
        String password = getOption(options, "password", null);

        // The server's "default" encryption mode expects a multipart/form-data
        // upload whose "file" part CONTENT is the JSON-serialized encrypted
        // envelope (camelCase keys), which it parses and RSA-OAEP-SHA256
        // decrypts. Sending a JSON request body instead yields a 500.
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("encryptedKey", encrypted.get("encryptedKey"));
        envelope.put("iv", encrypted.get("iv"));
        envelope.put("encryptedData", encrypted.get("encryptedData"));
        envelope.put("algorithm", encrypted.get("algorithm"));

        String envelopeJson;
        try {
            envelopeJson = http.getObjectMapper().writeValueAsString(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize encrypted payload: " + e.getMessage(), e);
        }
        byte[] envelopeBytes = envelopeJson.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Map<String, String> fields = new LinkedHashMap<>();
        if (source != null) {
            fields.put("source", source);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Aptr-Encrypted", "default");
        if (password != null) {
            headers.put("x-session-password", password);
        }

        return http.requestMultipart(
                "/api/v1/upload/" + uuid + "/images",
                envelopeBytes,
                filename + ".enc",
                "application/octet-stream",
                fields,
                headers);
    }

    private Map<String, Object> doMultipartUpload(String uuid, byte[] fileData, String filename, Map<String, Object> options) {
        String mimeType = getOption(options, "mimeType", "image/jpeg");
        String source = getOption(options, "source", null);
        String password = getOption(options, "password", null);

        Map<String, String> fields = new LinkedHashMap<>();
        if (source != null) {
            fields.put("source", source);
        }

        Map<String, String> headers = new LinkedHashMap<>();
        if (password != null) {
            headers.put("x-session-password", password);
        }

        return http.requestMultipart("/api/v1/upload/" + uuid + "/images", fileData, filename, mimeType, fields, headers);
    }

    private static String getOption(Map<String, Object> options, String key, String defaultValue) {
        if (options != null && options.containsKey(key)) {
            Object val = options.get(key);
            return val != null ? val.toString() : defaultValue;
        }
        return defaultValue;
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int bytesRead;
        while ((bytesRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
        }
        return buffer.toByteArray();
    }
}
