package models;

import controller.IntersectionController;
import controller.TrafficController;
import java.util.ArrayList;
import java.util.List;
import strategy.DensityBasedTimingStrategy;
import strategy.FixedTimingStrategy;
import strategy.SignalTimingStrategy;

/**
 * Deterministic core simulation model for the Traffic Management System.
 * Coordinates traffic signals, phase state machine, countdowns, and vehicle
 * movement through the 4-way intersection without any dependencies on Swing,
 * UI components, timers, or system clocks.
 */
public class TrafficSimulationModel {

    // Intersection Roads
    private Road northRoad;
    private Road southRoad;
    private Road eastRoad;
    private Road westRoad;

    // Traffic Controller & Timing Strategies
    private TrafficController controller;
    private SignalTimingStrategy fixedStrategy;
    private SignalTimingStrategy densityStrategy;
    private SignalTimingStrategy activeStrategy;

    // Phase State Machine
    private String currentAxis = "NORTH_SOUTH";
    private String currentSignalState = TrafficSignal.GREEN; // GREEN or YELLOW
    private int phaseDensityDuration = 35;
    private int phaseFixedDuration = 30;
    private int currentGreenDuration = 35;
    private int remainingSeconds;
    private int elapsedGreenSeconds = 0;
    public static final int YELLOW_DURATION = 3;

    // Simulation metrics & geometry (pixels)
    public static final double DEFAULT_SPEED = 40.0;
    public static final double DEFAULT_LENGTH = 26.0;
    public static final double DEFAULT_MIN_SPACING = 6.0;

    // Approach and Intersection Distance Thresholds
    // North/South: stop at 280, clear intersection at 400, exit road at 706
    public static final double NS_STOP_DISTANCE = 280.0;
    public static final double NS_CLEAR_DISTANCE = 400.0;
    public static final double NS_EXIT_DISTANCE = 706.0;

    // East/West: stop at 358, clear intersection at 478, exit road at 862
    public static final double EW_STOP_DISTANCE = 358.0;
    public static final double EW_CLEAR_DISTANCE = 478.0;
    public static final double EW_EXIT_DISTANCE = 862.0;

    private long totalTicks = 0;

    public TrafficSimulationModel(Road north, Road south, Road east, Road west) {
        this.northRoad = north;
        this.southRoad = south;
        this.eastRoad = east;
        this.westRoad = west;

        this.controller = new IntersectionController("City Center 4-Way Junction");
        this.fixedStrategy = new FixedTimingStrategy();
        this.densityStrategy = new DensityBasedTimingStrategy();
        this.activeStrategy = this.densityStrategy;

        // Position initial vehicle queues
        initializeVehicleQueues();

        // Calculate initial green phase duration and apply state
        this.phaseDensityDuration = getCalculatedGreenDuration(currentAxis, densityStrategy);
        this.phaseFixedDuration = getCalculatedGreenDuration(currentAxis, fixedStrategy);
        this.currentGreenDuration = (activeStrategy instanceof FixedTimingStrategy) ? phaseFixedDuration : phaseDensityDuration;
        applyCurrentState();
    }

    /**
     * Initializes vehicle positions on each road approach in queue order.
     * Lead vehicle is placed at the stop line; following vehicles are spaced safely behind.
     */
    public void initializeVehicleQueues() {
        positionQueue(northRoad, NS_STOP_DISTANCE);
        positionQueue(southRoad, NS_STOP_DISTANCE);
        positionQueue(eastRoad, EW_STOP_DISTANCE);
        positionQueue(westRoad, EW_STOP_DISTANCE);
    }

    private void positionQueue(Road road, double stopDistance) {
        if (road == null) return;
        List<Vehicle> list = road.getAllVehicles();
        if (list.isEmpty()) return;

        double currentPos = stopDistance;
        for (int i = 0; i < list.size(); i++) {
            Vehicle v = list.get(i);
            v.setDistance(currentPos);
            v.setEnteredIntersection(false);
            v.setClearedIntersection(false);
            v.setExited(false);
            currentPos -= (v.getLength() + v.getMinSpacing());
        }
    }

    /**
     * Advances the simulation state by exactly one deterministic tick.
     * 1. Moves vehicles according to signals and conflict clearance.
     * 2. Updates signal countdown and triggers phase transitions.
     * 3. Cleans up vehicles that have completely exited.
     */
    public void tick() {
        // 1. Advance vehicle movements
        moveVehicles();

        // 2. Advance signal timing / countdown
        advanceSignalTiming();

        // 3. Remove vehicles that have reached the exit boundary
        cleanupExitedVehicles();

        totalTicks++;
    }

    /**
     * Advances vehicle positions along each approach.
     */
    private void moveVehicles() {
        moveVehiclesOnRoad(northRoad, NS_STOP_DISTANCE, NS_CLEAR_DISTANCE, NS_EXIT_DISTANCE, "EAST_WEST");
        moveVehiclesOnRoad(southRoad, NS_STOP_DISTANCE, NS_CLEAR_DISTANCE, NS_EXIT_DISTANCE, "EAST_WEST");
        moveVehiclesOnRoad(eastRoad, EW_STOP_DISTANCE, EW_CLEAR_DISTANCE, EW_EXIT_DISTANCE, "NORTH_SOUTH");
        moveVehiclesOnRoad(westRoad, EW_STOP_DISTANCE, EW_CLEAR_DISTANCE, EW_EXIT_DISTANCE, "NORTH_SOUTH");
    }

    private void moveVehiclesOnRoad(Road road, double stopDist, double clearDist, double exitDist, String conflictingAxis) {
        if (road == null) return;
        List<Vehicle> list = road.getAllVehicles();
        if (list.isEmpty()) return;

        boolean isGreen = road.getSignal() != null && road.getSignal().isGreen();
        boolean conflictOccupied = isIntersectionOccupiedByAxis(conflictingAxis);

        for (int i = 0; i < list.size(); i++) {
            Vehicle v = list.get(i);
            double curr = v.getDistance();
            double target = curr + v.getSpeed();

            // Constraint 1: Safe following distance behind car ahead
            if (i > 0) {
                Vehicle ahead = list.get(i - 1);
                double maxAllowedBehind = ahead.getDistance() - (ahead.getLength() + v.getMinSpacing());
                if (target > maxAllowedBehind) {
                    target = Math.max(curr, maxAllowedBehind);
                }
            }

            // Constraint 2: Stop line & signal constraint
            // If the vehicle has NOT yet entered the intersection:
            if (!v.isEnteredIntersection()) {
                if (!isGreen || conflictOccupied) {
                    // Cannot enter intersection: must stop at stop line
                    target = Math.min(target, stopDist);
                }
            }

            // Update distance
            v.setDistance(target);

            // Update intersection status
            if (target > stopDist && target < clearDist) {
                v.setEnteredIntersection(true);
            } else if (target >= clearDist && target < exitDist) {
                v.setClearedIntersection(true);
            } else if (target >= exitDist) {
                v.setExited(true);
            }
        }
    }

    /**
     * Advances the signal countdown clock and initiates phase transitions when timers expire.
     */
    private void advanceSignalTiming() {
        if (TrafficSignal.GREEN.equals(currentSignalState)) {
            elapsedGreenSeconds++;
            remainingSeconds = currentGreenDuration - elapsedGreenSeconds;

            if (remainingSeconds <= 0) {
                transitionToNextPhase();
            } else {
                controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
            }
        } else {
            // YELLOW clearance countdown
            remainingSeconds--;
            if (remainingSeconds <= 0) {
                transitionToNextPhase();
            } else {
                controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
            }
        }
    }

    /**
     * Manages phase transitions:
     * GREEN -> 3-tick YELLOW clearance -> Opposite axis GREEN.
     */
    private void transitionToNextPhase() {
        if (TrafficSignal.GREEN.equals(currentSignalState)) {
            currentSignalState = TrafficSignal.YELLOW;
            remainingSeconds = YELLOW_DURATION;
            controller.applyYellowPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
        } else {
            currentAxis = currentAxis.equals("NORTH_SOUTH") ? "EAST_WEST" : "NORTH_SOUTH";
            currentSignalState = TrafficSignal.GREEN;
            elapsedGreenSeconds = 0;
            phaseDensityDuration = getCalculatedGreenDuration(currentAxis, densityStrategy);
            phaseFixedDuration = getCalculatedGreenDuration(currentAxis, fixedStrategy);
            currentGreenDuration = (activeStrategy instanceof FixedTimingStrategy) ? phaseFixedDuration : phaseDensityDuration;
            controller.applyPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, activeStrategy);

            remainingSeconds = currentGreenDuration;
        }
    }

    /**
     * Removes vehicles that have completely cleared the road from lane collections.
     */
    private void cleanupExitedVehicles() {
        cleanupRoad(northRoad);
        cleanupRoad(southRoad);
        cleanupRoad(eastRoad);
        cleanupRoad(westRoad);
    }

    private void cleanupRoad(Road road) {
        if (road == null) return;
        for (Lane lane : road.lanes) {
            lane.vehicles.removeIf(Vehicle::isExited);
        }
    }

    /**
     * Handles manual phase toggle safely:
     * During GREEN: initiates normal transition to 3-tick YELLOW clearance.
     * During YELLOW: strictly ignored to prevent cutting short or bypassing clearance.
     */
    public void manualPhaseToggle() {
        if (TrafficSignal.YELLOW.equals(currentSignalState)) {
            return;
        }
        transitionToNextPhase();
    }

    /**
     * Applies the current phase state and updates countdown fields.
     */
    public void applyCurrentState() {
        if (TrafficSignal.GREEN.equals(currentSignalState)) {
            phaseDensityDuration = getCalculatedGreenDuration(currentAxis, densityStrategy);
            phaseFixedDuration = getCalculatedGreenDuration(currentAxis, fixedStrategy);
            currentGreenDuration = (activeStrategy instanceof FixedTimingStrategy) ? phaseFixedDuration : phaseDensityDuration;
            controller.applyPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, activeStrategy);
            remainingSeconds = Math.max(0, currentGreenDuration - elapsedGreenSeconds);
            controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
        } else {
            controller.applyYellowPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
        }
    }

    /**
     * Handles strategy switching with monotonic elapsed green time preservation.
     */
    public void setActiveStrategy(SignalTimingStrategy strategy) {
        this.activeStrategy = strategy;

        if (TrafficSignal.GREEN.equals(currentSignalState)) {
            currentGreenDuration = (strategy instanceof FixedTimingStrategy) ? phaseFixedDuration : phaseDensityDuration;
            if (elapsedGreenSeconds >= currentGreenDuration) {
                remainingSeconds = 0;
            } else {
                remainingSeconds = currentGreenDuration - elapsedGreenSeconds;
            }
            controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
        }
    }

    /**
     * Calculates the shared coordinated green duration for an axis under a given strategy.
     */
    public int getCalculatedGreenDuration(String axis, SignalTimingStrategy strategy) {
        if ("NORTH_SOUTH".equalsIgnoreCase(axis)) {
            return Math.max(strategy.calculateGreenTime(northRoad), strategy.calculateGreenTime(southRoad));
        } else {
            return Math.max(strategy.calculateGreenTime(eastRoad), strategy.calculateGreenTime(westRoad));
        }
    }

    /**
     * Checks if any vehicle from any axis is currently inside the intersection box.
     */
    public boolean isIntersectionOccupied() {
        return isIntersectionOccupiedByAxis("NORTH_SOUTH") || isIntersectionOccupiedByAxis("EAST_WEST");
    }

    /**
     * Checks if any vehicle from a specified axis is currently inside the intersection box.
     */
    public boolean isIntersectionOccupiedByAxis(String axis) {
        if ("NORTH_SOUTH".equalsIgnoreCase(axis)) {
            return isRoadInIntersection(northRoad, NS_STOP_DISTANCE, NS_CLEAR_DISTANCE)
                || isRoadInIntersection(southRoad, NS_STOP_DISTANCE, NS_CLEAR_DISTANCE);
        } else {
            return isRoadInIntersection(eastRoad, EW_STOP_DISTANCE, EW_CLEAR_DISTANCE)
                || isRoadInIntersection(westRoad, EW_STOP_DISTANCE, EW_CLEAR_DISTANCE);
        }
    }

    private boolean isRoadInIntersection(Road road, double stopDist, double clearDist) {
        if (road == null) return false;
        for (Vehicle v : road.getAllVehicles()) {
            if (v.getDistance() > stopDist && v.getDistance() < clearDist) {
                return true;
            }
        }
        return false;
    }

    /**
     * Safety invariant: Returns true if conflicting (perpendicular) axes are simultaneously GREEN.
     * Must ALWAYS evaluate to false.
     */
    public boolean isPerpendicularAxisGreenSimultaneous() {
        boolean nsGreen = (northRoad != null && northRoad.getSignal().isGreen())
                       || (southRoad != null && southRoad.getSignal().isGreen());
        boolean ewGreen = (eastRoad != null && eastRoad.getSignal().isGreen())
                       || (westRoad != null && westRoad.getSignal().isGreen());
        return nsGreen && ewGreen;
    }

    /**
     * Safety invariant: Returns true if vehicles from perpendicular axes occupy the intersection simultaneously.
     * Must ALWAYS evaluate to false.
     */
    public boolean isIntersectionOccupiedByConflictingVehicles() {
        return isIntersectionOccupiedByAxis("NORTH_SOUTH") && isIntersectionOccupiedByAxis("EAST_WEST");
    }

    /**
     * Adds a vehicle to a road, placing it in queue order.
     */
    public void addVehicle(Road road, Vehicle vehicle) {
        if (road == null || vehicle == null) return;
        if (road.lanes.isEmpty()) {
            road.addLane(new Lane(road.roadId * 10 + 1));
        }
        Lane targetLane = road.lanes.get(0);
        List<Vehicle> existing = road.getAllVehicles();
        double stopDist = (road == northRoad || road == southRoad) ? NS_STOP_DISTANCE : EW_STOP_DISTANCE;
        double pos;
        if (existing.isEmpty()) {
            pos = stopDist;
        } else {
            Vehicle last = existing.get(existing.size() - 1);
            pos = last.getDistance() - (last.getLength() + vehicle.getMinSpacing());
        }
        vehicle.setDistance(pos);
        targetLane.vehicles.add(vehicle);
    }

    // Getters
    public Road getNorthRoad() { return northRoad; }
    public Road getSouthRoad() { return southRoad; }
    public Road getEastRoad() { return eastRoad; }
    public Road getWestRoad() { return westRoad; }
    public String getCurrentAxis() { return currentAxis; }
    public String getCurrentSignalState() { return currentSignalState; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public int getElapsedGreenSeconds() { return elapsedGreenSeconds; }
    public SignalTimingStrategy getActiveStrategy() { return activeStrategy; }
    public SignalTimingStrategy getFixedStrategy() { return fixedStrategy; }
    public SignalTimingStrategy getDensityStrategy() { return densityStrategy; }
    public long getTotalTicks() { return totalTicks; }
    public TrafficController getController() { return controller; }
    public int getCurrentGreenDuration() { return currentGreenDuration; }
}
