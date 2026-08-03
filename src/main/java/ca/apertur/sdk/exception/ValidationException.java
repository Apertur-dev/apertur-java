package ca.apertur.sdk.exception;

/**
 * Thrown when the API returns HTTP 400 (Bad Request).
 *
 * <p>This typically indicates an invalid request payload or missing required fields.
 */
public class ValidationException extends AperturException {

    /**
     * Creates a new validation exception.
     *
     * @param message the error message
     */
    public ValidationException(String message) {
        super(400, message != null ? message : "Validation error", "VALIDATION_ERROR");
    }
}
