package ca.apertur.sdk;

/**
 * Configuration for the Apertur API client.
 *
 * <p>Use the {@link Builder} to construct instances:
 * <pre>{@code
 * AperturConfig config = new AperturConfig.Builder()
 *     .apiKey("aptr_live_...")
 *     .build();
 * }</pre>
 */
public final class AperturConfig {

    private static final String DEFAULT_BASE_URL = "https://api.aptr.ca";
    private static final String SANDBOX_BASE_URL = "https://sandbox.api.aptr.ca";

    private final String apiKey;
    private final String oauthToken;
    private final String baseUrl;
    private final String env;
    private final String signingSecret;

    private AperturConfig(Builder builder) {
        this.apiKey = builder.apiKey;
        this.oauthToken = builder.oauthToken;
        this.signingSecret = builder.signingSecret;

        // Detect environment from key prefix
        String token = this.apiKey != null ? this.apiKey : (this.oauthToken != null ? this.oauthToken : "");
        String detectedEnv = token.startsWith("aptr_test_") ? "test" : "live";
        this.env = builder.env != null ? builder.env : detectedEnv;

        // Auto-select sandbox URL for test keys unless baseUrl is explicitly set
        if (builder.baseUrl != null) {
            this.baseUrl = builder.baseUrl.replaceAll("/+$", "");
        } else {
            this.baseUrl = "test".equals(this.env) ? SANDBOX_BASE_URL : DEFAULT_BASE_URL;
        }
    }

    /**
     * Returns the API key, or {@code null} if not set.
     *
     * @return the API key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the OAuth token, or {@code null} if not set.
     *
     * @return the OAuth token
     */
    public String getOauthToken() {
        return oauthToken;
    }

    /**
     * Returns the resolved base URL for API requests.
     *
     * @return the base URL
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Returns the environment ({@code "live"} or {@code "test"}).
     *
     * @return the environment string
     */
    public String getEnv() {
        return env;
    }

    /**
     * Returns the request-signing secret, or {@code null} if not set.
     *
     * <p>When set, outgoing requests are automatically signed with the
     * {@code X-Aptr-Signature} / {@code X-Aptr-Timestamp} headers. This is optional
     * and backwards-compatible: requests are sent unsigned when no secret is configured.
     *
     * @return the signing secret
     */
    public String getSigningSecret() {
        return signingSecret;
    }

    /**
     * Builder for {@link AperturConfig}.
     */
    public static final class Builder {
        private String apiKey;
        private String oauthToken;
        private String baseUrl;
        private String env;
        private String signingSecret;

        /**
         * Creates a new builder.
         */
        public Builder() {
        }

        /**
         * Sets the API key. Keys are prefixed with {@code aptr_} or {@code aptr_test_}.
         *
         * @param apiKey the API key
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the OAuth bearer token.
         *
         * @param oauthToken the OAuth token
         * @return this builder
         */
        public Builder oauthToken(String oauthToken) {
            this.oauthToken = oauthToken;
            return this;
        }

        /**
         * Overrides the base URL for API requests.
         *
         * @param baseUrl the base URL (e.g. {@code "https://api.aptr.ca"})
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Explicitly sets the environment ({@code "live"} or {@code "test"}).
         * If not set, the environment is inferred from the API key prefix.
         *
         * @param env the environment string
         * @return this builder
         */
        public Builder env(String env) {
            this.env = env;
            return this;
        }

        /**
         * Sets the request-signing secret. When set, outgoing requests are
         * automatically signed with {@code X-Aptr-Signature} / {@code X-Aptr-Timestamp}
         * headers. Optional; requests are sent unsigned when omitted.
         *
         * @param signingSecret the signing secret
         * @return this builder
         */
        public Builder signingSecret(String signingSecret) {
            this.signingSecret = signingSecret;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the immutable {@link AperturConfig}
         * @throws IllegalArgumentException if neither apiKey nor oauthToken is set
         */
        public AperturConfig build() {
            if (apiKey == null && oauthToken == null) {
                throw new IllegalArgumentException("Either apiKey or oauthToken must be provided");
            }
            return new AperturConfig(this);
        }
    }
}
