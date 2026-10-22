package models;

import java.util.ArrayList;

public class Road {

    public int roadId;
    private String direction;
    public ArrayList<Lane> lanes;
    private TrafficSignal signal;

    // Constructor with roadId (preserves existing code compatibility)
    public Road(int roadId) {
        this(roadId, "Road " + roadId);
    }

    // Overloaded constructor with roadId and direction name
    public Road(int roadId, String direction) {
        this.roadId = roadId;
        this.direction = direction;
        this.lanes = new ArrayList<>();
        this.signal = new TrafficSignal("RED", 0);
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public TrafficSignal getSignal() {
        return signal;
    }

    public void setSignal(TrafficSignal signal) {
        this.signal = signal;
    }

    // Method to add a lane to the road
    public void addLane(Lane lane) {
        lanes.add(lane);
    }

    // Method to get total vehicles in this road
    public int getTotalVehicles() {
        int total = 0;

        for (Lane lane : lanes) {
            total += lane.vehicles.size();
        }

        return total;
    }

    // Method to calculate average vehicles per lane
    // Throws ArithmeticException if lanes.size() == 0 (division by zero)
    public int getAverageVehiclesPerLane() throws ArithmeticException {
        return getTotalVehicles() / lanes.size();
    }
}
