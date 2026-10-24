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
            road.getSignal().setColor(TrafficSignal.GREEN);
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

    // Sets signal phases ensuring conflicting directions are not green simultaneously.
    // Coordinates a shared phase duration taking the maximum of the two active roads.
    @Override
    public void applyPhase(String activeAxis, Road north, Road south, Road east, Road west, SignalTimingStrategy strategy) {
        if ("NORTH_SOUTH".equalsIgnoreCase(activeAxis)) {
            // Coordinate shared green duration for the North-South axis
            int greenDuration = Math.max(strategy.calculateGreenTime(north), strategy.calculateGreenTime(south));

            north.getSignal().setColor(TrafficSignal.GREEN);
            north.getSignal().setDuration(greenDuration);

            south.getSignal().setColor(TrafficSignal.GREEN);
            south.getSignal().setDuration(greenDuration);

            east.getSignal().setColor(TrafficSignal.RED);
            east.getSignal().setDuration(0);

            west.getSignal().setColor(TrafficSignal.RED);
            west.getSignal().setDuration(0);
        } else {
            // Coordinate shared green duration for the East-West axis
            int greenDuration = Math.max(strategy.calculateGreenTime(east), strategy.calculateGreenTime(west));

            north.getSignal().setColor(TrafficSignal.RED);
            north.getSignal().setDuration(0);

            south.getSignal().setColor(TrafficSignal.RED);
            south.getSignal().setDuration(0);

            east.getSignal().setColor(TrafficSignal.GREEN);
            east.getSignal().setDuration(greenDuration);

            west.getSignal().setColor(TrafficSignal.GREEN);
            west.getSignal().setDuration(greenDuration);
        }
    }

    // Applies yellow clearance interval to the active axis while perpendicular axis stays RED.
    @Override
    public void applyYellowPhase(String activeAxis, Road north, Road south, Road east, Road west, int yellowDuration) {
        if ("NORTH_SOUTH".equalsIgnoreCase(activeAxis)) {
            north.getSignal().setColor(TrafficSignal.YELLOW);
            north.getSignal().setDuration(yellowDuration);

            south.getSignal().setColor(TrafficSignal.YELLOW);
            south.getSignal().setDuration(yellowDuration);

            east.getSignal().setColor(TrafficSignal.RED);
            east.getSignal().setDuration(0);

            west.getSignal().setColor(TrafficSignal.RED);
            west.getSignal().setDuration(0);
        } else {
            north.getSignal().setColor(TrafficSignal.RED);
            north.getSignal().setDuration(0);

            south.getSignal().setColor(TrafficSignal.RED);
            south.getSignal().setDuration(0);

            east.getSignal().setColor(TrafficSignal.YELLOW);
            east.getSignal().setDuration(yellowDuration);

            west.getSignal().setColor(TrafficSignal.YELLOW);
            west.getSignal().setDuration(yellowDuration);
        }
    }

    // Updates live countdown value on the active axis signals.
    @Override
    public void updateSignalCountdown(String activeAxis, Road north, Road south, Road east, Road west, int remainingSeconds) {
        if ("NORTH_SOUTH".equalsIgnoreCase(activeAxis)) {
            north.getSignal().setDuration(remainingSeconds);
            south.getSignal().setDuration(remainingSeconds);
        } else {
            east.getSignal().setDuration(remainingSeconds);
            west.getSignal().setDuration(remainingSeconds);
        }
    }

    // Implements interface method: displays controller operational status
    @Override
    public void displayStatus() {
        System.out.println("Traffic Controller active at: " + intersectionName);
    }
}
