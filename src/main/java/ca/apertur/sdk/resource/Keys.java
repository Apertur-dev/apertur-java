package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * API key management for a project.
 *
 * <p>Keys are scoped to a project and optionally restricted to specific destinations.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 * String projectId = "proj_...";
 *
 * Map<String, Object> key = client.keys().create(projectId, Map.of("label", "Mobile app key"));
 * client.keys().setDestinations((String) key.get("id"), List.of("dest_abc"), true);
 * }</pre>
 */
public final class Keys {

    private final AperturHttpClient http;

    /**
     * Creates a new Keys resource.
     *
     * @param http the HTTP client
     */
    public Keys(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Lists all API keys for a project.
     *
     * @param projectId the project ID
     * @return the keys list
     */
    public Map<String, Object> list(String projectId) {
        return http.request("GET", "/api/v1/projects/" + projectId + "/keys", null);
    }

    /**
     * Creates a new API key.
     *
     * @param projectId the project ID
     * @param options   key creation options (e.g. label)
     * @return the created key (includes the secret value)
     */
    public Map<String, Object> create(String projectId, Map<String, Object> options) {
        return http.request("POST", "/api/v1/projects/" + projectId + "/keys", options);
    }

    /**
     * Updates an existing API key.
     *
     * @param projectId the project ID
     * @param keyId     the key ID
     * @param options   the fields to update
     * @return the updated key
     */
    public Map<String, Object> update(String projectId, String keyId, Map<String, Object> options) {
        return http.request("PATCH", "/api/v1/projects/" + projectId + "/keys/" + keyId, options);
    }

    /**
     * Deletes an API key.
     *
     * @param projectId the project ID
     * @param keyId     the key ID
     */
    public void delete(String projectId, String keyId) {
        http.request("DELETE", "/api/v1/projects/" + projectId + "/keys/" + keyId, null);
    }

    /**
     * Assigns destinations to an API key and optionally enables long polling.
     *
     * @param keyId              the key ID
     * @param destinationIds     the list of destination IDs to assign
     * @param longPollingEnabled whether to enable long polling for this key
     * @return the updated key-destination mapping
     */
    public Map<String, Object> setDestinations(String keyId, List<String> destinationIds, boolean longPollingEnabled) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("destination_ids", destinationIds);
        body.put("long_polling_enabled", longPollingEnabled);
        return http.request("PUT", "/api/v1/keys/" + keyId + "/destinations", body);
    }
}
