package ca.apertur.sdk.resource;

import ca.apertur.sdk.AperturHttpClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Upload session management.
 *
 * <p>Sessions scope every image upload. You can create a session with optional settings,
 * retrieve it, protect it with a password, generate QR codes, and check delivery status.
 *
 * <pre>{@code
 * Apertur client = new Apertur("aptr_live_...");
 *
 * Map<String, Object> session = client.sessions().create(Map.of(
 *     "label", "Wedding reception",
 *     "maxImages", 200
 * ));
 *
 * String uuid = (String) session.get("uuid");
 * Map<String, Object> details = client.sessions().get(uuid);
 * }</pre>
 */
public final class Sessions {

    private final AperturHttpClient http;

    /**
     * Creates a new Sessions resource.
     *
     * @param http the HTTP client
     */
    public Sessions(AperturHttpClient http) {
        this.http = http;
    }

    /**
     * Creates a new upload session.
     *
     * @param options session creation options (e.g. label, password, maxImages)
     * @return the created session
     */
    public Map<String, Object> create(Map<String, Object> options) {
        return http.request("POST", "/api/v1/upload-sessions", options);
    }

    /**
     * Retrieves an upload session by UUID.
     *
     * @param uuid the session UUID
     * @return the session details
     */
    public Map<String, Object> get(String uuid) {
        return http.request("GET", "/api/v1/upload/" + uuid + "/session", null);
    }

    /**
     * Updates an upload session.
     *
     * @param uuid    the session UUID
     * @param options the fields to update
     * @return the updated session
     */
    public Map<String, Object> update(String uuid, Map<String, Object> options) {
        return http.request("PATCH", "/api/v1/upload-sessions/" + uuid, options);
    }

    /**
     * Lists sessions with pagination.
     *
     * @param params query parameters (e.g. page, pageSize)
     * @return the paginated session list
     */
    public Map<String, Object> list(Map<String, Object> params) {
        Map<String, String> qs = new LinkedHashMap<>();
        if (params != null) {
            if (params.containsKey("page")) qs.put("page", String.valueOf(params.get("page")));
            if (params.containsKey("pageSize")) qs.put("pageSize", String.valueOf(params.get("pageSize")));
        }
        return http.request("GET", "/api/v1/sessions" + AperturHttpClient.buildQueryString(qs), null);
    }

    /**
     * Lists recent sessions.
     *
     * <p>The server returns a top-level JSON array, so this returns a {@link java.util.List}
     * of session rows rather than a wrapping object.
     *
     * @param params query parameters (e.g. limit)
     * @return the recent sessions as a list of rows
     */
    public java.util.List<Map<String, Object>> recent(Map<String, Object> params) {
        Map<String, String> qs = new LinkedHashMap<>();
        if (params != null) {
            if (params.containsKey("limit")) qs.put("limit", String.valueOf(params.get("limit")));
        }
        return http.requestList("GET", "/api/v1/sessions/recent" + AperturHttpClient.buildQueryString(qs), null);
    }

    /**
     * Generates a QR code image for the upload session.
     *
     * @param uuid    the session UUID
     * @param options QR code options (e.g. format, size, style, fg, bg, borderSize, borderColor)
     * @return the QR code image bytes
     */
    public byte[] qr(String uuid, Map<String, Object> options) {
        Map<String, String> qs = new LinkedHashMap<>();
        if (options != null) {
            for (Map.Entry<String, Object> entry : options.entrySet()) {
                qs.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return http.requestRaw("GET", "/api/v1/upload-sessions/" + uuid + "/qr" + AperturHttpClient.buildQueryString(qs));
    }

    /**
     * Verifies a password for a password-protected session.
     *
     * @param uuid     the session UUID
     * @param password the password to verify
     * @return the verification result
     */
    public Map<String, Object> verifyPassword(String uuid, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", password);
        return http.request("POST", "/api/v1/upload/" + uuid + "/verify-password", body);
    }

    /**
     * Shares a session's upload link via email or SMS.
     *
     * @param uuid      the session UUID
     * @param channel   the delivery channel, {@code "email"} or {@code "sms"}
     * @param recipient the recipient address (email) or phone number (SMS)
     * @return the share result (shape: {@code { ok, channel, recipient, short_url }})
     */
    public Map<String, Object> share(String uuid, String channel, String recipient) {
        return share(uuid, channel, recipient, null, null);
    }

    /**
     * Shares a session's upload link via email or SMS, with optional SMS consent
     * confirmation and a note to include in the message.
     *
     * @param uuid        the session UUID
     * @param channel     the delivery channel, {@code "email"} or {@code "sms"}
     * @param recipient   the recipient address (email) or phone number (SMS)
     * @param smsConsent  whether SMS consent was confirmed, or {@code null} to omit
     * @param note        an optional note to include in the message, or {@code null} to omit
     * @return the share result (shape: {@code { ok, channel, recipient, short_url }})
     */
    public Map<String, Object> share(String uuid, String channel, String recipient, Boolean smsConsent, String note) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("recipient", recipient);
        if (smsConsent != null) {
            body.put("sms_consent", smsConsent);
        }
        if (note != null) {
            body.put("note", note);
        }
        return http.request("POST", "/api/v1/upload-sessions/" + uuid + "/share", body);
    }

    /**
     * Returns the delivery status for a session.
     *
     * <p>The response map has the shape
     * {@code { status, files: [...], lastChanged }} where {@code status} is one of
     * {@code pending | active | completed | expired} and {@code lastChanged} is an
     * ISO 8601 timestamp.
     *
     * @param uuid the session UUID
     * @return the delivery status snapshot
     */
    public Map<String, Object> deliveryStatus(String uuid) {
        return deliveryStatus(uuid, null);
    }

    /**
     * Returns the delivery status for a session, optionally long-polling for changes.
     *
     * <p>When {@code pollFrom} is provided the server holds the response for up to 5 minutes
     * until {@code lastChanged} advances past the supplied timestamp. This overload applies a
     * 6-minute per-request timeout so the server releases first under the happy path.
     *
     * @param uuid     the session UUID
     * @param pollFrom ISO 8601 timestamp to long-poll from, or {@code null} for an immediate snapshot
     * @return the delivery status (shape: {@code { status, files, lastChanged }})
     */
    public Map<String, Object> deliveryStatus(String uuid, String pollFrom) {
        String path = "/api/v1/upload-sessions/" + uuid + "/delivery-status";
        if (pollFrom != null && !pollFrom.isEmpty()) {
            path += "?pollFrom=" + URLEncoder.encode(pollFrom, StandardCharsets.UTF_8);
            return http.request("GET", path, null, Collections.emptyMap(), Duration.ofMinutes(6));
        }
        return http.request("GET", path, null);
    }
}
