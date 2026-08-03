package ca.apertur.sdk.exception;

/**
 * Thrown when the API returns HTTP 404 (Not Found).
 *
 * <p>The requested resource (session, image, project, etc.) does not exist.
 */
public class NotFoundException extends AperturException {

    /**
     * Creates a new not-found exception.
     *
     * @param message the error message
     */
    public NotFoundException(String message) {
        super(404, message != null ? message : "Not found", "NOT_FOUND");
    }
}
