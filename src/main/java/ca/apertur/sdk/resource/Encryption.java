package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.util.Map;

/**
 * Encryption key retrieval.
 *
 * <p>Fetch the server's RSA public key for use with encrypted image uploads.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 *
 * Map<String, Object> serverKey = client.encryption().getServerKey();
 * String publicKeyPem = (String) serverKey.get("publicKey");
 * }</pre>
 */
public final class Encryption {

    private final AperturHttpClient http;

    /**
     * Creates a new Encryption resource.
     *
     * @param http the HTTP client
     */
    public Encryption(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Retrieves the server's RSA public key for encrypted uploads.
     *
     * @return a map containing the {@code publicKey} (PEM-encoded)
     */
    public Map<String, Object> getServerKey() {
        return http.request("GET", "/api/v1/encryption/server-key", null);
    }
}
