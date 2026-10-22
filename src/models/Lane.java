package models;

import java.util.ArrayList;

public class Lane {

    public int laneId;
    public ArrayList<Vehicle> vehicles;

    public Lane(int laneId) {
        this.laneId = laneId;
        this.vehicles = new ArrayList<>();
    }
}