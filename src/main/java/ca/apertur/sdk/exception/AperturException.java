package ca.apertur.sdk.exception;

/**
 * Base exception for all Apertur API errors.
 *
 * <p>Contains the HTTP status code and an optional machine-readable error code
 * from the API response body.
 */
public class AperturException extends RuntimeException {

    private final int statusCode;
    private final String code;

    /**
     * Creates a new API exception.
     *
     * @param statusCode the HTTP status code (0 if the request never completed)
     * @param message    the human-readable error message
     * @param code       the machine-readable error code, or {@code null}
     */
    public AperturException(int statusCode, String message, String code) {
        super(message);
        this.statusCode = statusCode;
        this.code = code;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code
     */
    public int getStatusCode() {
        return statusCode;
    }

    /**
     * Returns the machine-readable error code from the API, or {@code null}.
     *
     * @return the error code
     */
    public String getCode() {
        return code;
    }
}
