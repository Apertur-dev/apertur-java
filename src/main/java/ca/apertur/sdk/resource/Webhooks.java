package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event webhook management for a project.
 *
 * <p>Webhooks push real-time notifications to your endpoint for events such as image uploads
 * and session state changes.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 * String projectId = "proj_...";
 *
 * Map<String, Object> webhook = client.webhooks().create(projectId, Map.of(
 *     "url", "https://example.com/webhooks/apertur",
 *     "events", List.of("image.uploaded", "session.completed")
 * ));
 * }</pre>
 */
public final class Webhooks {

    private final AperturHttpClient http;

    /**
     * Creates a new Webhooks resource.
     *
     * @param http the HTTP client
     */
    public Webhooks(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Lists all webhooks for a project.
     *
     * @param projectId the project ID
     * @return the webhooks list
     */
    public Map<String, Object> list(String projectId) {
        return http.request("GET", "/api/v1/projects/" + projectId + "/webhooks", null);
    }

    /**
     * Creates a new webhook.
     *
     * @param projectId the project ID
     * @param config    the webhook configuration (e.g. url, events)
     * @return the created webhook
     */
    public Map<String, Object> create(String projectId, Map<String, Object> config) {
        return http.request("POST", "/api/v1/projects/" + projectId + "/webhooks", config);
    }

    /**
     * Updates an existing webhook.
     *
     * @param projectId the project ID
     * @param webhookId the webhook ID
     * @param config    the fields to update
     * @return the updated webhook
     */
    public Map<String, Object> update(String projectId, String webhookId, Map<String, Object> config) {
        return http.request("PATCH", "/api/v1/projects/" + projectId + "/webhooks/" + webhookId, config);
    }

    /**
     * Deletes a webhook.
     *
     * @param projectId the project ID
     * @param webhookId the webhook ID
     */
    public void delete(String projectId, String webhookId) {
        http.request("DELETE", "/api/v1/projects/" + projectId + "/webhooks/" + webhookId, null);
    }

    /**
     * Triggers a test delivery for a webhook.
     *
     * @param projectId the project ID
     * @param webhookId the webhook ID
     * @return the test result
     */
    public Map<String, Object> test(String projectId, String webhookId) {
        return http.request("POST", "/api/v1/projects/" + projectId + "/webhooks/" + webhookId + "/test", null);
    }

    /**
     * Lists delivery attempts for a webhook with pagination.
     *
     * @param projectId the project ID
     * @param webhookId the webhook ID
     * @param options   pagination options (e.g. page, limit)
     * @return the deliveries result
     */
    public Map<String, Object> deliveries(String projectId, String webhookId, Map<String, Object> options) {
        Map<String, String> qs = new LinkedHashMap<>();
        if (options != null) {
            if (options.containsKey("page")) qs.put("page", String.valueOf(options.get("page")));
            if (options.containsKey("limit")) qs.put("limit", String.valueOf(options.get("limit")));
        }
        return http.request("GET",
                "/api/v1/projects/" + projectId + "/webhooks/" + webhookId + "/deliveries" + AperturHttpClient.buildQueryString(qs),
                null);
    }

    /**
     * Retries a failed webhook delivery.
     *
     * @param projectId  the project ID
     * @param webhookId  the webhook ID
     * @param deliveryId the delivery ID to retry
     * @return the retry result
     */
    public Map<String, Object> retryDelivery(String projectId, String webhookId, String deliveryId) {
        return http.request("POST",
                "/api/v1/projects/" + projectId + "/webhooks/" + webhookId + "/deliveries/" + deliveryId + "/retry",
                null);
    }
}
