package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.util.Map;

/**
 * Account statistics.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 * Map<String, Object> stats = client.stats().get();
 * }</pre>
 */
public final class Stats {

    private final AperturHttpClient http;

    /**
     * Creates a new Stats resource.
     *
     * @param http the HTTP client
     */
    public Stats(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Retrieves account statistics.
     *
     * @return the statistics data
     */
    public Map<String, Object> get() {
        return http.request("GET", "/api/v1/stats", null);
    }
}
