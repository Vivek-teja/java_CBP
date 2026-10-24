package models;

public class Vehicle {
    public int vehicleId;
    public String type;

    // Simulation state tracking
    private double distance;           // Position along travel path (pixels)
    private double speed;              // Distance moved per simulation tick
    private double length;             // Vehicle body length (pixels)
    private double minSpacing;         // Minimum safe following distance (pixels)
    private boolean enteredIntersection;
    private boolean clearedIntersection;
    private boolean exited;

    // Constructor (used to create objects easily, preserves baseline compatibility)
    public Vehicle(int vehicleId, String type) {
        this(vehicleId, type, 0.0);
    }

    // Overloaded constructor with initial distance
    public Vehicle(int vehicleId, String type, double initialDistance) {
        this.vehicleId = vehicleId;
        this.type = type;
        this.distance = initialDistance;
        this.speed = 40.0;
        this.length = 26.0;
        this.minSpacing = 6.0;
        this.enteredIntersection = false;
        this.clearedIntersection = false;
        this.exited = false;
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public String getType() {
        return type;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public double getLength() {
        return length;
    }

    public void setLength(double length) {
        this.length = length;
    }

    public double getMinSpacing() {
        return minSpacing;
    }

    public void setMinSpacing(double minSpacing) {
        this.minSpacing = minSpacing;
    }

    public boolean isEnteredIntersection() {
        return enteredIntersection;
    }

    public void setEnteredIntersection(boolean enteredIntersection) {
        this.enteredIntersection = enteredIntersection;
    }

    public boolean isClearedIntersection() {
        return clearedIntersection;
    }

    public void setClearedIntersection(boolean clearedIntersection) {
        this.clearedIntersection = clearedIntersection;
    }

    public boolean isExited() {
        return exited;
    }

    public void setExited(boolean exited) {
        this.exited = exited;
    }
}
