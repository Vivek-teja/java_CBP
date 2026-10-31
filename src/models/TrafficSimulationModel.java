package models;

import controller.IntersectionController;
import controller.TrafficController;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
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

    // Emergency Vehicle Priority state
    private volatile boolean emergencyActive = false;
    private volatile int emergencyTargetRoadId = -1;
    private volatile Vehicle activeEmergencyVehicle = null;

    // Simulation metrics & geometry (pixels)
    public static final double DEFAULT_SPEED = 40.0;
    public static final double DEFAULT_LENGTH = 26.0;
    public static final double DEFAULT_MIN_SPACING = 6.0;

    // Dynamic vehicle speed & intelligent actuated stop controls
    private double vehicleSpeed = 50.0;
    private boolean autoStopOnEmpty = true;
    private boolean speedBasedTimingEnabled = true;
    private boolean intersectionIdle = false;

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

    // Phase 3: Bounded pending event queue and thread-safe counters
    public static final int PENDING_QUEUE_CAPACITY = 50;
    public static final int MAX_ARRIVALS_PER_TICK = 2;
    private final BlockingQueue<VehicleArrivalEvent> arrivalQueue = new ArrayBlockingQueue<>(PENDING_QUEUE_CAPACITY);
    private final AtomicLong sequenceGenerator = new AtomicLong(0);
    private final AtomicLong queueOverflowRejections = new AtomicLong(0);
    private final AtomicLong roadCapacityRejections = new AtomicLong(0);
    private final AtomicLong totalArrivalsConsumed = new AtomicLong(0);
    private volatile long currentGenerationToken = 1;

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

        // Initialize persistent MySQL database logging session
        analytics.TrafficDataLogger.getInstance().startSession(
            activeStrategy.getStrategyName(), this.vehicleSpeed, "DEFAULT_SCENARIO",
            northRoad, southRoad, eastRoad, westRoad
        );
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
            v.setSpeed(this.vehicleSpeed);
            v.setDistance(currentPos);
            v.setEnteredIntersection(false);
            v.setClearedIntersection(false);
            v.setExited(false);
            currentPos -= (v.getLength() + v.getMinSpacing());
        }
    }

    /**
     * Advances the simulation state by exactly one deterministic tick.
     * 1. Drains and processes up to 2 pending dynamic vehicle arrivals on EDT.
     * 2. Moves vehicles according to signals and conflict clearance.
     * 3. Updates signal countdown and triggers phase transitions.
     * 4. Cleans up vehicles that have completely exited.
     */
    public void tick() {
        if (intersectionIdle) {
            // Check if any vehicles were added to wake up the intersection
            if (getWaitingVehicleCount("NORTH_SOUTH") > 0 || getWaitingVehicleCount("EAST_WEST") > 0) {
                intersectionIdle = false;
                if (getWaitingVehicleCount(currentAxis) == 0) {
                    currentAxis = currentAxis.equals("NORTH_SOUTH") ? "EAST_WEST" : "NORTH_SOUTH";
                }
                currentSignalState = TrafficSignal.GREEN;
                elapsedGreenSeconds = 0;
                currentGreenDuration = getCalculatedGreenDuration(currentAxis, activeStrategy);
                if (currentGreenDuration <= 0) currentGreenDuration = 6;
                remainingSeconds = currentGreenDuration;
                controller.applyPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, activeStrategy);
            } else {
                return; // Nothing to move or cycle when all lanes are empty
            }
        }

        // 1. Ingest pending dynamic vehicle arrivals (max 2 per tick)
        consumePendingArrivals();

        // 2. Advance vehicle movements
        moveVehicles();

        // 3. Monitor active emergency vehicle status
        if (emergencyActive && activeEmergencyVehicle != null) {
            if (activeEmergencyVehicle.isClearedIntersection() || activeEmergencyVehicle.isExited()) {
                // Emergency vehicle has cleared the intersection safely
                analytics.TrafficDataLogger.getInstance().logEmergencyCleared(activeEmergencyVehicle);
                emergencyActive = false;
                activeEmergencyVehicle = null;
                emergencyTargetRoadId = -1;
            } else if (TrafficSignal.GREEN.equals(currentSignalState) && remainingSeconds <= 2) {
                // Extend green window to prevent emergency vehicle being trapped by red signal
                remainingSeconds = 6;
                controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
            }
        }

        // 4. Advance signal timing / countdown
        advanceSignalTiming();

        // 5. Remove vehicles that have reached the exit boundary
        cleanupExitedVehicles();

        // 6. Record periodic second-by-second analytics snapshot in MySQL
        analytics.TrafficDataLogger.getInstance().logTickSnapshot(this);

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
        if (road == null || road.lanes == null) return;

        boolean isGreen = road.getSignal() != null && road.getSignal().isGreen();

        for (Lane lane : road.lanes) {
            if (lane == null || lane.vehicles == null || lane.vehicles.isEmpty()) continue;

            // Maintain invariant: vehicles are strictly sorted descending by distance along the approach
            lane.vehicles.sort((v1, v2) -> Double.compare(v2.getDistance(), v1.getDistance()));

            for (int i = 0; i < lane.vehicles.size(); i++) {
                Vehicle v = lane.vehicles.get(i);
                double curr = v.getDistance();
                double target = curr + v.getSpeed();

                // Constraint 1: Safe following distance behind car ahead in the same lane
                if (i > 0) {
                    Vehicle ahead = lane.vehicles.get(i - 1);
                    double maxAllowedBehind = ahead.getDistance() - (ahead.getLength() + v.getMinSpacing());
                    if (target > maxAllowedBehind) {
                        target = Math.max(curr, maxAllowedBehind);
                    }
                }

                // Constraint 2: Stop line & signal constraint
                // Only vehicles that have NOT yet entered the intersection stop on RED or YELLOW
                if (!v.isEnteredIntersection()) {
                    if (!isGreen) {
                        target = Math.min(target, stopDist);
                    }
                }

                // Update distance
                v.setDistance(target);

                // Update intersection status
                if (target > stopDist && target < clearDist) {
                    if (!v.isEnteredIntersection()) {
                        v.setEnteredIntersection(true);
                        analytics.TrafficDataLogger.getInstance().logVehicleEntered(v);
                    }
                } else if (target >= clearDist && target < exitDist) {
                    v.setClearedIntersection(true);
                } else if (target >= exitDist) {
                    if (!v.isExited()) {
                        v.setExited(true);
                        analytics.TrafficDataLogger.getInstance().logVehicleExited(v);
                    }
                }
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

            // Intelligent queue clearance & actuated stop ("if no vehicles are at the lane just stops")
            // Apply minimum green guarantee of 3 seconds so green does not cut off abruptly
            if (autoStopOnEmpty && !emergencyActive && elapsedGreenSeconds >= 3) {
                int waitingThisAxis = getWaitingVehicleCount(currentAxis);
                boolean occupied = isIntersectionOccupiedByAxis(currentAxis);

                // If no vehicles are waiting at this lane and any vehicles inside junction have cleared
                if (waitingThisAxis == 0 && !occupied) {
                    String conflictingAxis = currentAxis.equals("NORTH_SOUTH") ? "EAST_WEST" : "NORTH_SOUTH";
                    int waitingConflicting = getWaitingVehicleCount(conflictingAxis);

                    if (waitingConflicting > 0) {
                        // Conflicting axis has waiting vehicles: immediately stop green and transition!
                        transitionToNextPhase();
                        return;
                    } else {
                        // Conflicting axis also has 0 vehicles: all lanes empty, rest stopped!
                        intersectionIdle = true;
                        remainingSeconds = 0;
                        controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, 0);
                        return;
                    }
                }
            }

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
            analytics.TrafficDataLogger.getInstance().logSignalTransition(
                currentAxis,
                "NORTH_SOUTH".equals(currentAxis) ? "North-South" : "East-West",
                "YELLOW",
                currentGreenDuration,
                remainingSeconds
            );
        } else {
            // After yellow clearance, if an emergency vehicle is active, route green to the emergency axis
            if (emergencyActive && emergencyTargetRoadId > 0) {
                currentAxis = (emergencyTargetRoadId == 1 || emergencyTargetRoadId == 2) ? "NORTH_SOUTH" : "EAST_WEST";
            } else {
                String candidateAxis = currentAxis.equals("NORTH_SOUTH") ? "EAST_WEST" : "NORTH_SOUTH";
                if (autoStopOnEmpty) {
                    int waitingCandidate = getWaitingVehicleCount(candidateAxis);
                    int waitingCurrent = getWaitingVehicleCount(currentAxis);
                    if (waitingCandidate > 0) {
                        currentAxis = candidateAxis;
                    } else if (waitingCurrent > 0) {
                        currentAxis = currentAxis; // Keep axis with waiting vehicles
                    } else {
                        // All lanes empty! Rest stopped!
                        intersectionIdle = true;
                        currentAxis = candidateAxis;
                        currentSignalState = TrafficSignal.GREEN;
                        currentGreenDuration = 0;
                        remainingSeconds = 0;
                        controller.applyPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, activeStrategy);
                        controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, 0);
                        analytics.TrafficDataLogger.getInstance().logSignalTransition(
                            currentAxis,
                            "NORTH_SOUTH".equals(currentAxis) ? "North-South" : "East-West",
                            "GREEN",
                            0,
                            0
                        );
                        return;
                    }
                } else {
                    currentAxis = candidateAxis;
                }
            }
            intersectionIdle = false;
            currentSignalState = TrafficSignal.GREEN;
            elapsedGreenSeconds = 0;
            phaseDensityDuration = getCalculatedGreenDuration(currentAxis, densityStrategy);
            phaseFixedDuration = getCalculatedGreenDuration(currentAxis, fixedStrategy);
            currentGreenDuration = (activeStrategy instanceof FixedTimingStrategy) ? phaseFixedDuration : phaseDensityDuration;
            if (emergencyActive) {
                currentGreenDuration = Math.max(currentGreenDuration, 25);
            }
            controller.applyPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, activeStrategy);

            remainingSeconds = currentGreenDuration;
            analytics.TrafficDataLogger.getInstance().logSignalTransition(
                currentAxis,
                "NORTH_SOUTH".equals(currentAxis) ? "North-South" : "East-West",
                "GREEN",
                currentGreenDuration,
                remainingSeconds
            );
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
            analytics.TrafficDataLogger.getInstance().logSignalTransition(
                currentAxis,
                "NORTH_SOUTH".equals(currentAxis) ? "North-South" : "East-West",
                "GREEN",
                currentGreenDuration,
                remainingSeconds
            );
        } else {
            controller.applyYellowPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
            analytics.TrafficDataLogger.getInstance().logSignalTransition(
                currentAxis,
                "NORTH_SOUTH".equals(currentAxis) ? "North-South" : "East-West",
                "YELLOW",
                currentGreenDuration,
                remainingSeconds
            );
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
     * Incorporates vehicle speed and queue headway clearance when speedBasedTimingEnabled is true.
     */
    public int getCalculatedGreenDuration(String axis, SignalTimingStrategy strategy) {
        if (strategy instanceof FixedTimingStrategy) {
            return strategy.calculateGreenTime(northRoad);
        }
        if (speedBasedTimingEnabled) {
            if ("NORTH_SOUTH".equalsIgnoreCase(axis)) {
                return Math.max(calculateSpeedBasedGreenDuration(northRoad), calculateSpeedBasedGreenDuration(southRoad));
            } else {
                return Math.max(calculateSpeedBasedGreenDuration(eastRoad), calculateSpeedBasedGreenDuration(westRoad));
            }
        } else {
            if ("NORTH_SOUTH".equalsIgnoreCase(axis)) {
                return Math.max(strategy.calculateGreenTime(northRoad), strategy.calculateGreenTime(southRoad));
            } else {
                return Math.max(strategy.calculateGreenTime(eastRoad), strategy.calculateGreenTime(westRoad));
            }
        }
    }

    /**
     * Calculates required green duration strictly based on vehicle speed,
     * intersection clearance geometry, and queued headway spacing.
     */
    public int calculateSpeedBasedGreenDuration(Road road) {
        if (road == null) return 0;
        int waiting = getWaitingVehicleCount(road);
        if (waiting == 0) return 0;

        double speed = vehicleSpeed;
        double clearanceDist = (road == northRoad || road == southRoad)
                ? (NS_CLEAR_DISTANCE - NS_STOP_DISTANCE)
                : (EW_CLEAR_DISTANCE - EW_STOP_DISTANCE); // 120px
        int clearanceSec = (int) Math.ceil(clearanceDist / speed); // ~3s at 50px/s
        double headwayDist = DEFAULT_LENGTH + DEFAULT_MIN_SPACING; // 32px
        double headwaySec = Math.max(1.0, (headwayDist / speed) * 1.5); // ~1.2s per vehicle
        int queueSec = (int) Math.ceil(waiting * headwaySec);

        int total = clearanceSec + queueSec;
        return Math.max(4, Math.min(45, total));
    }

    /**
     * Returns the count of vehicles currently waiting behind the stop line on a road.
     */
    public int getWaitingVehicleCount(Road road) {
        if (road == null || road.lanes == null) return 0;
        double stopDist = (road == northRoad || road == southRoad) ? NS_STOP_DISTANCE : EW_STOP_DISTANCE;
        int count = 0;
        for (Lane lane : road.lanes) {
            if (lane == null || lane.vehicles == null) continue;
            for (Vehicle v : lane.vehicles) {
                if (!v.isEnteredIntersection() && v.getDistance() <= stopDist) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Returns the total count of vehicles currently waiting behind the stop line along an axis.
     */
    public int getWaitingVehicleCount(String axis) {
        if ("NORTH_SOUTH".equalsIgnoreCase(axis)) {
            return getWaitingVehicleCount(northRoad) + getWaitingVehicleCount(southRoad);
        } else {
            return getWaitingVehicleCount(eastRoad) + getWaitingVehicleCount(westRoad);
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
            if (v.getDistance() > stopDist && (v.getDistance() - v.getLength()) < clearDist) {
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
     * Adds a vehicle to a road, placing it in approach queue order.
     * Ensures newly arriving vehicles are queued behind waiting approach traffic,
     * and NEVER placed behind vehicles that have already passed the stop line.
     */
    public void addVehicle(Road road, Vehicle vehicle) {
        if (road == null || vehicle == null) return;
        Lane targetLane = resolveTargetLane(road);
        double stopDist = (road == northRoad || road == southRoad) ? NS_STOP_DISTANCE : EW_STOP_DISTANCE;

        // Find the rearmost vehicle waiting behind the stop line on the incoming approach
        Vehicle lastWaiting = null;
        for (Vehicle existing : targetLane.vehicles) {
            if (existing.getDistance() <= stopDist) {
                if (lastWaiting == null || existing.getDistance() < lastWaiting.getDistance()) {
                    lastWaiting = existing;
                }
            }
        }

        double pos;
        if (lastWaiting == null) {
            // No vehicles currently waiting on the incoming approach: queue at the stop line
            pos = stopDist;
        } else {
            // Queue safely behind the rearmost waiting vehicle on the approach
            pos = lastWaiting.getDistance() - (lastWaiting.getLength() + vehicle.getMinSpacing());
        }
        vehicle.setDistance(pos);
        vehicle.setEnteredIntersection(false);
        vehicle.setClearedIntersection(false);
        vehicle.setExited(false);
        vehicle.setRoadDirection(road.getDirection());
        vehicle.setLaneId(targetLane.laneId);
        targetLane.vehicles.add(vehicle);

        analytics.TrafficDataLogger.getInstance().logVehicleAdded(vehicle, road.getDirection(), targetLane.laneId);

        // Keep lane vehicles strictly sorted descending by distance
        targetLane.vehicles.sort((v1, v2) -> Double.compare(v2.getDistance(), v1.getDistance()));
    }

    // =========================================================================
    // Phase 3: Thread-Safe Dynamic Vehicle Arrivals & Concurrency Controls
    // =========================================================================

    /**
     * Resolves the target receiving lane for vehicle arrivals on an approach road.
     * Default policy assigns arrivals to the approach lane (Lane 1 / index 0).
     *
     * @param road target approach road
     * @return designated receiving Lane instance
     */
    public Lane resolveTargetLane(Road road) {
        if (road == null) return null;
        if (road.lanes == null || road.lanes.isEmpty()) {
            road.addLane(new Lane(road.roadId * 10 + 1));
        }
        return road.lanes.get(0);
    }

    /**
     * Evaluates available entrance clearance using the target lane's vehicles.
     * Evaluates true if the lane has no vehicles, or if the tail vehicle has cleared
     * the entrance by more than its length + minimum following headway spacing:
     * (lastVehicle.getDistance() - (length + minSpacing)) > 0.
     *
     * @param lane target approach lane
     * @return true if safe entry is guaranteed, false if lane entrance is blocked/unsafe
     */
    public boolean canSafelyEnterLane(Lane lane) {
        if (lane == null) return false;
        if (lane.vehicles == null || lane.vehicles.isEmpty()) {
            return true;
        }
        Vehicle last = lane.vehicles.get(lane.vehicles.size() - 1);
        double safeThreshold = last.getDistance() - (last.getLength() + last.getMinSpacing());
        return safeThreshold > 0.0;
    }

    /**
     * Checks if a vehicle can safely enter the target road approach at distance 0.0.
     * Resolves the designated receiving lane and evaluates its entrance clearance.
     *
     * @param road target approach road
     * @return true if safe entry is guaranteed, false if road entrance is blocked/unsafe
     */
    public boolean canSafelyEnterRoad(Road road) {
        if (road == null) return false;
        Lane targetLane = resolveTargetLane(road);
        return canSafelyEnterLane(targetLane);
    }

    public boolean canSafelyEnterRoad(Road road, int laneIndex) {
        if (road == null || road.lanes == null || laneIndex < 0 || laneIndex >= road.lanes.size()) {
            return false;
        }
        return canSafelyEnterLane(road.lanes.get(laneIndex));
    }

    public boolean canSafelyEnterRoad(Road road, Lane targetLane) {
        return canSafelyEnterLane(targetLane);
    }

    /**
     * Submits an arrival event to the bounded queue in a non-blocking, thread-safe manner.
     * If the queue is at capacity (50 events), rejects the newest event and increments
     * the queue-overflow rejection counter.
     * If the event has a stale generation token, it is rejected immediately.
     *
     * @param event the immutable arrival event to enqueue
     * @return true if enqueued successfully, false if rejected due to overflow or stale token
     */
    public boolean enqueueArrival(VehicleArrivalEvent event) {
        if (event == null) {
            return false;
        }
        if (event.getGenerationToken() != this.currentGenerationToken) {
            return false;
        }
        boolean offered = arrivalQueue.offer(event);
        if (!offered) {
            queueOverflowRejections.incrementAndGet();
        }
        return offered;
    }

    /**
     * Drains up to maxDrain pending arrival events from the bounded queue on the EDT.
     * Validates generation tokens, road IDs, and entrance tail clearance before safely
     * instantiating and adding a Vehicle.
     *
     * @param maxDrain maximum number of events to process in this cycle (default 2)
     * @return number of events processed (both added and road-capacity rejected)
     */
    public int consumePendingArrivals(int maxDrain) {
        int processedThisTick = 0;
        while (processedThisTick < maxDrain && !arrivalQueue.isEmpty()) {
            VehicleArrivalEvent event = arrivalQueue.poll();
            if (event == null) {
                break;
            }

            // 1. Generation token validation: reject stale events from prior runs/restarts
            if (event.getGenerationToken() != this.currentGenerationToken) {
                continue;
            }

            // 2. Road identification
            Road targetRoad = getRoadById(event.getTargetRoadId());
            if (targetRoad == null) {
                continue;
            }

            // 3. Vehicle configuration validation
            String vType = event.getVehicleType();
            if (vType == null || vType.trim().isEmpty()) {
                vType = "Car";
            }

            // 4. Resolve the target lane that will receive this arrival
            Lane targetLane = resolveTargetLane(targetRoad);

            // 5. Safe entry spacing check at consumption time on the exact target lane
            if (!canSafelyEnterLane(targetLane)) {
                roadCapacityRejections.incrementAndGet();
                processedThisTick++;
                totalArrivalsConsumed.incrementAndGet();
                continue;
            }

            // 6. Model-owned safe vehicle insertion on simulation thread (EDT) into targetLane
            int vId = (int) (event.getSequenceId() & 0x7FFFFFFF);
            Vehicle vehicle = new Vehicle(vId, vType, 0.0);
            vehicle.setRoadDirection(targetRoad.getDirection());
            vehicle.setLaneId(targetLane.laneId);
            targetLane.vehicles.add(vehicle);
            analytics.TrafficDataLogger.getInstance().logVehicleAdded(vehicle, targetRoad.getDirection(), targetLane.laneId);

            processedThisTick++;
            totalArrivalsConsumed.incrementAndGet();
        }
        return processedThisTick;
    }

    /**
     * Drains up to 2 pending arrival events (MAX_ARRIVALS_PER_TICK) per simulation tick.
     *
     * @return number of events processed
     */
    public int consumePendingArrivals() {
        return consumePendingArrivals(MAX_ARRIVALS_PER_TICK);
    }

    /**
     * Resolves an approach road by its unique roadId (1 = North, 2 = South, 3 = East, 4 = West).
     *
     * @param roadId road identity
     * @return matching Road instance or null if not found
     */
    public Road getRoadById(int roadId) {
        switch (roadId) {
            case 1: return northRoad;
            case 2: return southRoad;
            case 3: return eastRoad;
            case 4: return westRoad;
            default:
                if (northRoad != null && northRoad.roadId == roadId) return northRoad;
                if (southRoad != null && southRoad.roadId == roadId) return southRoad;
                if (eastRoad != null && eastRoad.roadId == roadId) return eastRoad;
                if (westRoad != null && westRoad.roadId == roadId) return westRoad;
                return null;
        }
    }

    public long nextSequenceId() {
        return sequenceGenerator.incrementAndGet();
    }

    public long getCurrentGenerationToken() {
        return currentGenerationToken;
    }

    public void resetGenerationToken() {
        this.currentGenerationToken++;
        this.arrivalQueue.clear();
    }

    public void setGenerationToken(long token) {
        this.currentGenerationToken = token;
        this.arrivalQueue.clear();
    }

    public int getPendingQueueSize() {
        return arrivalQueue.size();
    }

    public long getQueueOverflowRejections() {
        return queueOverflowRejections.get();
    }

    public long getRoadCapacityRejections() {
        return roadCapacityRejections.get();
    }

    public long getTotalArrivalsConsumed() {
        return totalArrivalsConsumed.get();
    }

    public BlockingQueue<VehicleArrivalEvent> getArrivalQueue() {
        return arrivalQueue;
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

    // =========================================================================
    // Emergency Vehicle Priority Handling
    // =========================================================================

    /**
     * Triggers priority preemption for an emergency vehicle on the designated road approach.
     * Safely clears perpendicular traffic via a 3-second yellow clearance phase before granting
     * green right-of-way to the emergency vehicle approach.
     *
     * @param roadId ID of the road receiving the emergency vehicle (1=North, 2=South, 3=East, 4=West)
     * @return the newly spawned emergency vehicle
     */
    public Vehicle triggerEmergencyPriority(int roadId) {
        Road targetRoad = getRoadById(roadId);
        if (targetRoad == null) {
            targetRoad = northRoad;
            roadId = 1;
        }

        String targetAxis = (roadId == 1 || roadId == 2) ? "NORTH_SOUTH" : "EAST_WEST";

        // Spawn emergency vehicle (Ambulance)
        Vehicle amb = new Vehicle((int) nextSequenceId(), "Ambulance", 0.0);
        Lane targetLane = resolveTargetLane(targetRoad);
        amb.setRoadDirection(targetRoad.getDirection());
        amb.setLaneId(targetLane.laneId);
        targetLane.vehicles.add(amb);

        this.emergencyActive = true;
        this.emergencyTargetRoadId = roadId;
        this.activeEmergencyVehicle = amb;

        // Record emergency priority preemption event in MySQL
        analytics.TrafficDataLogger.getInstance().logEmergencyDispatched(amb, roadId, currentAxis);

        // If the emergency axis is already GREEN, ensure sufficient green duration
        if (currentAxis.equalsIgnoreCase(targetAxis)) {
            if (TrafficSignal.GREEN.equals(currentSignalState)) {
                remainingSeconds = Math.max(remainingSeconds, 20);
                controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
            }
        } else {
            // Conflicting perpendicular axis is currently active.
            // Safety invariant: MUST initiate 3-tick yellow clearance before switching to emergency axis
            if (TrafficSignal.GREEN.equals(currentSignalState)) {
                currentSignalState = TrafficSignal.YELLOW;
                remainingSeconds = YELLOW_DURATION;
                controller.applyYellowPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
            }
        }
        return amb;
    }

    public boolean isEmergencyActive() { return emergencyActive; }
    public int getEmergencyTargetRoadId() { return emergencyTargetRoadId; }
    public Vehicle getActiveEmergencyVehicle() { return activeEmergencyVehicle; }

    // =========================================================================
    // Dynamic Vehicle Management & Speed-Based Actuated Controls
    // =========================================================================

    /**
     * Adds a newly spawned vehicle to the designated road approach,
     * assigning it to the least congested lane and waking up an idle intersection if needed.
     *
     * @param roadId road ID (1=North, 2=South, 3=East, 4=West)
     * @return the newly instantiated and queued vehicle
     */
    public Vehicle addVehicleToRoad(int roadId) {
        Road road = getRoadById(roadId);
        if (road == null) return null;

        if (road.lanes == null || road.lanes.isEmpty()) {
            road.addLane(new Lane(road.roadId * 10 + 1));
        }

        // Balance across lanes: pick lane with fewest vehicles
        Lane targetLane = road.lanes.get(0);
        for (Lane l : road.lanes) {
            if (l.vehicles.size() < targetLane.vehicles.size()) {
                targetLane = l;
            }
        }

        int nextId = (int) nextSequenceId();
        Vehicle v = new Vehicle(nextId, "Car", 0.0);
        v.setSpeed(this.vehicleSpeed);

        double stopDist = (road == northRoad || road == southRoad) ? NS_STOP_DISTANCE : EW_STOP_DISTANCE;

        // Find the rearmost vehicle waiting behind the stop line on the incoming approach
        Vehicle lastWaiting = null;
        for (Vehicle existing : targetLane.vehicles) {
            if (existing.getDistance() <= stopDist) {
                if (lastWaiting == null || existing.getDistance() < lastWaiting.getDistance()) {
                    lastWaiting = existing;
                }
            }
        }

        double pos;
        if (lastWaiting == null) {
            // No vehicles currently waiting on the incoming approach: queue at the stop line
            pos = stopDist;
        } else {
            // Queue safely behind the rearmost waiting vehicle on the approach
            pos = lastWaiting.getDistance() - (lastWaiting.getLength() + v.getMinSpacing());
        }
        v.setDistance(pos);
        v.setEnteredIntersection(false);
        v.setClearedIntersection(false);
        v.setExited(false);
        v.setRoadDirection(road.getDirection());
        v.setLaneId(targetLane.laneId);
        targetLane.vehicles.add(v);

        // Record persistent vehicle registration in MySQL
        analytics.TrafficDataLogger.getInstance().logVehicleAdded(v, road.getDirection(), targetLane.laneId);

        // Keep lane vehicles strictly sorted descending by distance
        targetLane.vehicles.sort((v1, v2) -> Double.compare(v2.getDistance(), v1.getDistance()));

        // Wake up intersection if it was resting stopped/idle because no vehicles were present
        if (intersectionIdle) {
            intersectionIdle = false;
            String roadAxis = (roadId == 1 || roadId == 2) ? "NORTH_SOUTH" : "EAST_WEST";
            currentAxis = roadAxis;
            currentSignalState = TrafficSignal.GREEN;
            elapsedGreenSeconds = 0;
            currentGreenDuration = getCalculatedGreenDuration(currentAxis, activeStrategy);
            if (currentGreenDuration <= 0) currentGreenDuration = 6;
            remainingSeconds = currentGreenDuration;
            controller.applyPhase(currentAxis, northRoad, southRoad, eastRoad, westRoad, activeStrategy);
        } else if (TrafficSignal.GREEN.equals(currentSignalState)) {
            String roadAxis = (roadId == 1 || roadId == 2) ? "NORTH_SOUTH" : "EAST_WEST";
            if (currentAxis.equalsIgnoreCase(roadAxis)) {
                // If on the currently green axis, adjust green time if needed to service new vehicle
                int needed = getCalculatedGreenDuration(currentAxis, activeStrategy);
                if (remainingSeconds < (needed - elapsedGreenSeconds)) {
                    currentGreenDuration = needed;
                    remainingSeconds = Math.max(remainingSeconds, currentGreenDuration - elapsedGreenSeconds);
                    controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, remainingSeconds);
                }
            }
        }
        return v;
    }

    /**
     * Clears all vehicles from all lanes on all roads, resetting intersection into stopped/idle state.
     */
    public void clearAllVehicles() {
        if (northRoad != null && northRoad.lanes != null) for (Lane l : northRoad.lanes) l.vehicles.clear();
        if (southRoad != null && southRoad.lanes != null) for (Lane l : southRoad.lanes) l.vehicles.clear();
        if (eastRoad != null && eastRoad.lanes != null) for (Lane l : eastRoad.lanes) l.vehicles.clear();
        if (westRoad != null && westRoad.lanes != null) for (Lane l : westRoad.lanes) l.vehicles.clear();
        arrivalQueue.clear();
        intersectionIdle = true;
        remainingSeconds = 0;
        controller.updateSignalCountdown(currentAxis, northRoad, southRoad, eastRoad, westRoad, 0);
    }

    public double getVehicleSpeed() {
        return vehicleSpeed;
    }

    public void setVehicleSpeed(double speed) {
        this.vehicleSpeed = Math.max(10.0, speed);
        updateSpeedsOnRoad(northRoad, this.vehicleSpeed);
        updateSpeedsOnRoad(southRoad, this.vehicleSpeed);
        updateSpeedsOnRoad(eastRoad, this.vehicleSpeed);
        updateSpeedsOnRoad(westRoad, this.vehicleSpeed);
    }

    private void updateSpeedsOnRoad(Road road, double speed) {
        if (road == null || road.lanes == null) return;
        for (Lane l : road.lanes) {
            if (l.vehicles == null) continue;
            for (Vehicle v : l.vehicles) {
                v.setSpeed(speed);
            }
        }
    }

    public boolean isAutoStopOnEmpty() {
        return autoStopOnEmpty;
    }

    public void setAutoStopOnEmpty(boolean autoStop) {
        this.autoStopOnEmpty = autoStop;
    }

    public boolean isSpeedBasedTimingEnabled() {
        return speedBasedTimingEnabled;
    }

    public void setSpeedBasedTimingEnabled(boolean enabled) {
        this.speedBasedTimingEnabled = enabled;
    }

    public boolean isIntersectionIdle() {
        return intersectionIdle;
    }
}
