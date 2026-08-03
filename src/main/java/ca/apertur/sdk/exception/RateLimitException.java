package ca.apertur.sdk.exception;

/**
 * Thrown when the API returns HTTP 429 (Too Many Requests).
 *
 * <p>If the server included a {@code Retry-After} header, the value is available
 * via {@link #getRetryAfter()}.
 */
public class RateLimitException extends AperturException {

    private final Integer retryAfter;

    /**
     * Creates a new rate-limit exception.
     *
     * @param message    the error message
     * @param retryAfter seconds to wait before retrying, or {@code null} if not provided
     */
    public RateLimitException(String message, Integer retryAfter) {
        super(429, message != null ? message : "Rate limit exceeded", "RATE_LIMIT");
        this.retryAfter = retryAfter;
    }

    /**
     * Returns the number of seconds to wait before retrying, or {@code null}
     * if the server did not include a {@code Retry-After} header.
     *
     * @return the retry-after value in seconds
     */
    public Integer getRetryAfter() {
        return retryAfter;
    }
}
