package models;

import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents an individual vehicle operating within the traffic simulation.
 * Each vehicle retains a globally unique readable vehicle number (e.g. V001, V002),
 * tracks its lifecycle states (WAITING, ENTERED_INTERSECTION, EXITED), and persists
 * its timestamps and coordinates for MySQL database storage and R analytics.
 */
public class Vehicle {

    // Global atomic generator for readable vehicle numbers (V001, V002, ...)
    private static final AtomicInteger VEHICLE_NUMBER_COUNTER = new AtomicInteger(0);

    public int vehicleId;
    public String type;

    // Database & persistent identification fields
    private int dbId;                          // Auto-generated MySQL primary key
    private String vehicleNumber;              // Readable identifier: "V001", "V002", etc.
    private int runId;                         // Associated simulation run ID
    private int laneId;                        // Current lane ID
    private String laneName;                   // Readable lane name (e.g. "N1", "N2")
    private String roadDirection;              // "North", "South", "East", "West"
    private String currentStatus;              // "WAITING", "ENTERED_INTERSECTION", "EXITED"
    private Timestamp arrivalTime;             // Queue arrival timestamp
    private Timestamp entryTime;               // Intersection box entry timestamp
    private Timestamp exitTime;                // Intersection complete exit timestamp

    // Simulation state tracking
    private double distance;           // Position along travel path (pixels)
    private double speed;              // Distance moved per simulation tick
    private double length;             // Vehicle body length (pixels)
    private double minSpacing;         // Minimum safe following distance (pixels)
    private boolean enteredIntersection;
    private boolean clearedIntersection;
    private boolean exited;

    /**
     * Generates the next sequential unique readable vehicle number.
     */
    public static String nextVehicleNumber() {
        return String.format("V%03d", VEHICLE_NUMBER_COUNTER.incrementAndGet());
    }

    /**
     * Resets the vehicle number counter (for testing or clean simulation restarts).
     */
    public static void resetVehicleNumberCounter(int start) {
        VEHICLE_NUMBER_COUNTER.set(start);
    }

    public static int getCurrentVehicleNumberCount() {
        return VEHICLE_NUMBER_COUNTER.get();
    }

    // Baseline constructor (preserves 100% existing code compatibility)
    public Vehicle(int vehicleId, String type) {
        this(vehicleId, type, 0.0);
    }

    // Overloaded constructor with initial distance
    public Vehicle(int vehicleId, String type, double initialDistance) {
        this(vehicleId, nextVehicleNumber(), type, initialDistance);
    }

    // Overloaded constructor with explicit vehicle number
    public Vehicle(int vehicleId, String vehicleNumber, String type, double initialDistance) {
        this.vehicleId = vehicleId;
        this.vehicleNumber = (vehicleNumber != null && !vehicleNumber.trim().isEmpty())
                ? vehicleNumber.trim()
                : nextVehicleNumber();
        this.type = (type != null && !type.trim().isEmpty()) ? type.trim() : "Car";
        this.distance = initialDistance;
        this.speed = 40.0;
        this.length = 26.0;
        this.minSpacing = 6.0;
        this.enteredIntersection = false;
        this.clearedIntersection = false;
        this.exited = false;
        this.currentStatus = "WAITING";
        this.arrivalTime = new Timestamp(System.currentTimeMillis());
        this.roadDirection = "North";
        this.laneId = 1;
        this.laneName = "N1";
    }

    public int getVehicleId() {
        return vehicleId;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public void setVehicleNumber(String vehicleNumber) {
        this.vehicleNumber = vehicleNumber;
    }

    public int getDbId() {
        return dbId;
    }

    public void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public int getRunId() {
        return runId;
    }

    public void setRunId(int runId) {
        this.runId = runId;
    }

    public int getLaneId() {
        return laneId;
    }

    public void setLaneId(int laneId) {
        this.laneId = laneId;
    }

    public String getLaneName() {
        return laneName;
    }

    public void setLaneName(String laneName) {
        this.laneName = laneName;
    }

    public String getRoadDirection() {
        return roadDirection;
    }

    public void setRoadDirection(String roadDirection) {
        this.roadDirection = roadDirection;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public Timestamp getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Timestamp arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Timestamp getEntryTime() {
        return entryTime;
    }

    public void setEntryTime(Timestamp entryTime) {
        this.entryTime = entryTime;
    }

    public Timestamp getExitTime() {
        return exitTime;
    }

    public void setExitTime(Timestamp exitTime) {
        this.exitTime = exitTime;
    }

    public boolean isEmergency() {
        return "Emergency".equalsIgnoreCase(type)
                || "Ambulance".equalsIgnoreCase(type)
                || "FireTruck".equalsIgnoreCase(type);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
        if (enteredIntersection && !"EXITED".equals(currentStatus)) {
            this.currentStatus = "ENTERED_INTERSECTION";
            if (this.entryTime == null) {
                this.entryTime = new Timestamp(System.currentTimeMillis());
            }
        }
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
        if (exited) {
            this.currentStatus = "EXITED";
            if (this.exitTime == null) {
                this.exitTime = new Timestamp(System.currentTimeMillis());
            }
        }
    }

    @Override
    public String toString() {
        return String.format("%s (%s) [%s] - %s", vehicleNumber, type, currentStatus, roadDirection);
    }
}
