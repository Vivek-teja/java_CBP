package exceptions;

/**
 * Custom checked exception thrown when an operation is performed
 * on a lane or road that has no waiting vehicles.
 */
public class NoVehiclesException extends Exception {

    private static final long serialVersionUID = 1L;

    // Constructor accepting a descriptive error message
    public NoVehiclesException(String message) {
        super(message);
    }
}
