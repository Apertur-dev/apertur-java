package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.util.Map;

/**
 * Destination management for a project.
 *
 * <p>Destinations define where uploaded images are delivered (S3, webhook, long-poll queue, etc.).
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 * String projectId = "proj_...";
 *
 * Map<String, Object> dest = client.destinations().create(projectId, Map.of(
 *     "type", "s3",
 *     "label", "Primary S3 bucket",
 *     "config", Map.of("bucket", "my-bucket", "region", "us-east-1")
 * ));
 * }</pre>
 */
public final class Destinations {

    private final AperturHttpClient http;

    /**
     * Creates a new Destinations resource.
     *
     * @param http the HTTP client
     */
    public Destinations(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Lists all destinations for a project.
     *
     * @param projectId the project ID
     * @return the destinations list
     */
    public Map<String, Object> list(String projectId) {
        return http.request("GET", "/api/v1/projects/" + projectId + "/destinations", null);
    }

    /**
     * Creates a new destination.
     *
     * @param projectId the project ID
     * @param config    the destination configuration
     * @return the created destination
     */
    public Map<String, Object> create(String projectId, Map<String, Object> config) {
        return http.request("POST", "/api/v1/projects/" + projectId + "/destinations", config);
    }

    /**
     * Updates an existing destination.
     *
     * @param projectId the project ID
     * @param destId    the destination ID
     * @param config    the fields to update
     * @return the updated destination
     */
    public Map<String, Object> update(String projectId, String destId, Map<String, Object> config) {
        return http.request("PATCH", "/api/v1/projects/" + projectId + "/destinations/" + destId, config);
    }

    /**
     * Deletes a destination.
     *
     * @param projectId the project ID
     * @param destId    the destination ID
     */
    public void delete(String projectId, String destId) {
        http.request("DELETE", "/api/v1/projects/" + projectId + "/destinations/" + destId, null);
    }

    /**
     * Triggers a test delivery for a destination.
     *
     * @param projectId the project ID
     * @param destId    the destination ID
     * @return the test result
     */
    public Map<String, Object> test(String projectId, String destId) {
        return http.request("POST", "/api/v1/projects/" + projectId + "/destinations/" + destId + "/test", null);
    }
}
