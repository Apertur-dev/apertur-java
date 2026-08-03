package ca.apertur.sdk;

import ca.apertur.sdk.resource.Destinations;
import ca.apertur.sdk.resource.Encryption;
import ca.apertur.sdk.resource.Events;
import ca.apertur.sdk.resource.Keys;
import ca.apertur.sdk.resource.Polling;
import ca.apertur.sdk.resource.Sessions;
import ca.apertur.sdk.resource.Stats;
import ca.apertur.sdk.resource.Upload;
import ca.apertur.sdk.resource.Uploads;
import ca.apertur.sdk.resource.Webhooks;

/**
 * Main entry point for the Apertur Java SDK.
 *
 * <p>Create an instance with an API key or full configuration, then access
 * API resources through the accessor methods.
 *
 * <pre>{@code
 * // Shorthand — API key only
 * Apertur client = new Apertur("aptr_live_...");
 *
 * // Full configuration
 * AperturConfig config = new AperturConfig.Builder()
 *     .apiKey("aptr_live_...")
 *     .build();
 * Apertur client = new Apertur(config);
 *
 * // Use resources
 * Map<String, Object> session = client.sessions().create(Map.of("label", "My shoot"));
 * }</pre>
 */
public final class Apertur {

    private final AperturConfig config;
    private final AperturHttpClient httpClient;
    private final String env;

    private final Sessions sessions;
    private final Upload upload;
    private final Uploads uploads;
    private final Polling polling;
    private final Destinations destinations;
    private final Keys keys;
    private final Webhooks webhooks;
    private final Encryption encryption;
    private final Stats stats;
    private final Events events;

    /**
     * Creates a client with the given configuration.
     *
     * @param config the client configuration
     */
    public Apertur(AperturConfig config) {
        this.config = config;
        this.env = config.getEnv();
        this.httpClient = new AperturHttpClient(config.getBaseUrl(), config.getApiKey(), config.getOauthToken(), config.getSigningSecret());

        this.sessions = new Sessions(httpClient);
        this.upload = new Upload(httpClient);
        this.uploads = new Uploads(httpClient);
        this.polling = new Polling(httpClient);
        this.destinations = new Destinations(httpClient);
        this.keys = new Keys(httpClient);
        this.webhooks = new Webhooks(httpClient);
        this.encryption = new Encryption(httpClient);
        this.stats = new Stats(httpClient);
        this.events = new Events(config.getBaseUrl());
    }

    /**
     * Creates a client with the given API key, using default settings.
     * The environment and base URL are auto-detected from the key prefix.
     *
     * @param apiKey the API key (prefixed {@code aptr_} or {@code aptr_test_})
     */
    public Apertur(String apiKey) {
        this(new AperturConfig.Builder().apiKey(apiKey).build());
    }

    /**
     * Returns the environment this client targets ({@code "live"} or {@code "test"}).
     *
     * @return the environment string
     */
    public String getEnv() {
        return env;
    }

    /**
     * Returns the underlying configuration.
     *
     * @return the configuration
     */
    public AperturConfig getConfig() {
        return config;
    }

    /**
     * Upload session management: create, get, update, list, and more.
     *
     * @return the sessions resource
     */
    public Sessions sessions() {
        return sessions;
    }

    /**
     * Image upload (plain and encrypted).
     *
     * @return the upload resource
     */
    public Upload upload() {
        return upload;
    }

    /**
     * Upload listing and history.
     *
     * @return the uploads resource
     */
    public Uploads uploads() {
        return uploads;
    }

    /**
     * Long-polling for new images in a session.
     *
     * @return the polling resource
     */
    public Polling polling() {
        return polling;
    }

    /**
     * Destination management for a project.
     *
     * @return the destinations resource
     */
    public Destinations destinations() {
        return destinations;
    }

    /**
     * API key management for a project.
     *
     * @return the keys resource
     */
    public Keys keys() {
        return keys;
    }

    /**
     * Event webhook management for a project.
     *
     * @return the webhooks resource
     */
    public Webhooks webhooks() {
        return webhooks;
    }

    /**
     * Encryption key retrieval.
     *
     * @return the encryption resource
     */
    public Encryption encryption() {
        return encryption;
    }

    /**
     * Account statistics.
     *
     * @return the stats resource
     */
    public Stats stats() {
        return stats;
    }

    /**
     * Real-time session events over Socket.IO.
     *
     * @return the events resource
     */
    public Events events() {
        return events;
    }
}
