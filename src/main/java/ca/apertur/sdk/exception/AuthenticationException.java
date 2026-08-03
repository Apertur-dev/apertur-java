package ca.apertur.sdk.exception;

/**
 * Thrown when the API returns HTTP 401 (Unauthorized).
 *
 * <p>This typically indicates an invalid or missing API key or OAuth token.
 */
public class AuthenticationException extends AperturException {

    /**
     * Creates a new authentication exception.
     *
     * @param message the error message
     */
    public AuthenticationException(String message) {
        super(401, message != null ? message : "Authentication failed", "AUTHENTICATION_FAILED");
    }
}
