package controller;

import exceptions.NoVehiclesException;
import models.Lane;
import models.Road;
import models.TrafficSignal;
import strategy.SignalTimingStrategy;

/**
 * Concrete implementation of the TrafficController interface.
 * Coordinates traffic on roads at an intersection using a given timing strategy.
 */
public class IntersectionController implements TrafficController {

    private String intersectionName;

    // Constructor
    public IntersectionController(String intersectionName) {
        this.intersectionName = intersectionName;
    }

    public String getIntersectionName() {
        return intersectionName;
    }

    // Controls traffic for a road using the strategy and outputs calculated green duration
    @Override
    public void controlTraffic(Road road, SignalTimingStrategy strategy) {
        int greenTime = strategy.calculateGreenTime(road);
        if (road.getSignal() != null) {
            road.getSignal().setColor("GREEN");
            road.getSignal().setDuration(greenTime);
        }
    }

    // Checks lanes and prints a short message if any lane is empty (NoVehiclesException)
    @Override
    public void checkLanes(Road road) {
        if (road.lanes.isEmpty()) {
            return;
        }

        if (road.getTotalVehicles() == 0) {
            try {
                throw new NoVehiclesException("No vehicles in the lanes.");
            } catch (NoVehiclesException e) {
                System.out.println(e.getMessage());
            }
            return;
        }

        for (Lane lane : road.lanes) {
            try {
                if (lane.vehicles.isEmpty()) {
                    throw new NoVehiclesException("Lane " + lane.laneId + ": No vehicles waiting.");
                }
            } catch (NoVehiclesException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    // Displays average vehicles per lane; handles ArithmeticException if road has zero lanes
    @Override
    public void displayAverageVehicles(Road road) {
        try {
            int avg = road.getAverageVehiclesPerLane();
            System.out.println("Average vehicles per lane: " + avg);
        } catch (ArithmeticException e) {
            System.out.println("Average vehicles per lane cannot be calculated: no lanes available.");
        }
    }

    // Sets signal phases ensuring conflicting directions are not green simultaneously
    @Override
    public void applyPhase(String activeAxis, Road north, Road south, Road east, Road west, SignalTimingStrategy strategy) {
        if ("NORTH_SOUTH".equalsIgnoreCase(activeAxis)) {
            // North-South green phase, East-West red phase
            north.getSignal().setColor("GREEN");
            north.getSignal().setDuration(strategy.calculateGreenTime(north));

            south.getSignal().setColor("GREEN");
            south.getSignal().setDuration(strategy.calculateGreenTime(south));

            east.getSignal().setColor("RED");
            east.getSignal().setDuration(0);

            west.getSignal().setColor("RED");
            west.getSignal().setDuration(0);
        } else {
            // East-West green phase, North-South red phase
            north.getSignal().setColor("RED");
            north.getSignal().setDuration(0);

            south.getSignal().setColor("RED");
            south.getSignal().setDuration(0);

            east.getSignal().setColor("GREEN");
            east.getSignal().setDuration(strategy.calculateGreenTime(east));

            west.getSignal().setColor("GREEN");
            west.getSignal().setDuration(strategy.calculateGreenTime(west));
        }
    }

    // Implements interface method: displays controller operational status
    @Override
    public void displayStatus() {
        System.out.println("Traffic Controller active at: " + intersectionName);
    }
}
