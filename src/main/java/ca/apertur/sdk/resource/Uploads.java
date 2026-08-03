package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Upload listing and history.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 *
 * Map<String, Object> page = client.uploads().list(Map.of("page", 1, "pageSize", 25));
 * Map<String, Object> recent = client.uploads().recent(Map.of("limit", 10));
 * }</pre>
 */
public final class Uploads {

    private final AperturHttpClient http;

    /**
     * Creates a new Uploads resource.
     *
     * @param http the HTTP client
     */
    public Uploads(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Lists uploads with pagination.
     *
     * @param params query parameters (e.g. page, pageSize)
     * @return the paginated upload list
     */
    public Map<String, Object> list(Map<String, Object> params) {
        Map<String, String> qs = new LinkedHashMap<>();
        if (params != null) {
            if (params.containsKey("page")) qs.put("page", String.valueOf(params.get("page")));
            if (params.containsKey("pageSize")) qs.put("pageSize", String.valueOf(params.get("pageSize")));
        }
        return http.request("GET", "/api/v1/uploads" + AperturHttpClient.buildQueryString(qs), null);
    }

    /**
     * Lists recent uploads.
     *
     * <p>The server returns a top-level JSON array, so this returns a {@link java.util.List}
     * of upload rows rather than a wrapping object.
     *
     * @param params query parameters (e.g. limit)
     * @return the recent uploads as a list of rows
     */
    public java.util.List<Map<String, Object>> recent(Map<String, Object> params) {
        Map<String, String> qs = new LinkedHashMap<>();
        if (params != null) {
            if (params.containsKey("limit")) qs.put("limit", String.valueOf(params.get("limit")));
        }
        return http.requestList("GET", "/api/v1/uploads/recent" + AperturHttpClient.buildQueryString(qs), null);
    }
}
