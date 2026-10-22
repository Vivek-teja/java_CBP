package exceptions;

/**
 * Custom checked exception thrown when an operation is performed
 * on a lane or road that has no waiting vehicles.
 */
public class NoVehiclesException extends Exception {

    // Constructor accepting a descriptive error message
    public NoVehiclesException(String message) {
        super(message);
    }
}
