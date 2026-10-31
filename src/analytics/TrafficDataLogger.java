package analytics;

import database.DatabaseConnection;
import database.SimulationDAO;
import database.TrafficAnalyticsDAO;
import database.VehicleDAO;
import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import models.Lane;
import models.Road;
import models.TrafficSimulationModel;
import models.Vehicle;

/**
 * High-performance, asynchronous background data logger.
 * Decouples the simulation physics and Swing Event Dispatch Thread (EDT)
 * from database I/O so that MySQL operations never degrade animation or cause UI stalls.
 *
 * If MySQL is unavailable, actions are safely logged or bypassed without throwing exceptions.
 */
public class TrafficDataLogger {

    private static final TrafficDataLogger INSTANCE = new TrafficDataLogger();

    public static TrafficDataLogger getInstance() {
        return INSTANCE;
    }

    private final SimulationDAO simulationDAO = new SimulationDAO();
    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final TrafficAnalyticsDAO analyticsDAO = new TrafficAnalyticsDAO();

    // Single-thread daemon executor for ordered, sequential database updates
    private final ExecutorService dbExecutor;

    private volatile int currentRunId = -1;
    private volatile boolean sessionActive = false;

    // Cumulative exit throughput counters per road direction for the active run
    private final Map<String, AtomicInteger> throughputMap = new ConcurrentHashMap<>();

    // Active emergency event tracking
    private volatile int activeEmergencyEventId = -1;
    private volatile Timestamp emergencyRequestTime = null;

    // Cache to prevent duplicate signal event logging
    private String lastSignalState = "";
    private String lastActivePhase = "";

    public TrafficDataLogger() {
        this.throughputMap.put("North", new AtomicInteger(0));
        this.throughputMap.put("South", new AtomicInteger(0));
        this.throughputMap.put("East", new AtomicInteger(0));
        this.throughputMap.put("West", new AtomicInteger(0));

        this.dbExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "Traffic-Database-Writer");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public int getCurrentRunId() {
        return currentRunId;
    }

    public boolean isSessionActive() {
        return sessionActive;
    }

    /**
     * Initializes a new simulation run session in MySQL and persists initial road/lane vehicles.
     */
    public void startSession(String strategy, double speed, String scenarioId,
                             Road north, Road south, Road east, Road west) {
        // Reset throughput
        throughputMap.get("North").set(0);
        throughputMap.get("South").set(0);
        throughputMap.get("East").set(0);
        throughputMap.get("West").set(0);

        if (!DatabaseConnection.isAvailable()) {
            System.out.println("[TrafficDataLogger] Notice: MySQL unavailable. Session running in offline mode.");
            currentRunId = 1;
            sessionActive = true;
            return;
        }

        try {
            int runId = simulationDAO.createSimulationRun(strategy, speed, scenarioId);
            if (runId > 0) {
                currentRunId = runId;
                sessionActive = true;
                System.out.println("[TrafficDataLogger] Started new Simulation Run #" + runId);
            } else {
                currentRunId = 1;
            }
        } catch (Exception e) {
            System.err.println("[TrafficDataLogger] Error creating run: " + e.getMessage());
            currentRunId = 1;
        }

        final int runId = currentRunId;
        dbExecutor.submit(() -> {
            try {
                // Insert initial vehicles from all roads
                registerInitialVehicles(north, runId);
                registerInitialVehicles(south, runId);
                registerInitialVehicles(east, runId);
                registerInitialVehicles(west, runId);
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error registering initial vehicles: " + e.getMessage());
            }
        });
    }

    private void registerInitialVehicles(Road road, int runId) {
        if (road == null || road.lanes == null) return;
        for (Lane lane : road.lanes) {
            if (lane == null || lane.vehicles == null) continue;
            for (Vehicle v : lane.vehicles) {
                v.setRoadDirection(road.getDirection());
                v.setLaneId(lane.laneId);
                v.setRunId(runId);
                vehicleDAO.insertVehicle(v, runId, lane.laneId, road.getDirection());
            }
        }
    }

    /**
     * Logs the addition of a new vehicle (dynamic arrival, UI button click, or prompt).
     */
    public void logVehicleAdded(Vehicle v, String roadDirection, int laneId) {
        if (v == null) return;
        v.setRoadDirection(roadDirection);
        v.setLaneId(laneId);
        v.setRunId(currentRunId);

        if (!DatabaseConnection.isAvailable() || currentRunId <= 0) return;

        dbExecutor.submit(() -> {
            try {
                vehicleDAO.insertVehicle(v, currentRunId, laneId, roadDirection);
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging vehicle added: " + e.getMessage());
            }
        });
    }

    /**
     * Logs vehicle entering the intersection box.
     */
    public void logVehicleEntered(Vehicle v) {
        if (v == null || !DatabaseConnection.isAvailable() || currentRunId <= 0) return;

        dbExecutor.submit(() -> {
            try {
                vehicleDAO.updateVehicleStatus(v.getDbId(), "ENTERED_INTERSECTION",
                        v.getEntryTime(), null, v.getDistance());
                vehicleDAO.recordVehicleEvent(v.getDbId(), currentRunId, "VEHICLE_ENTERED",
                        v.getRoadDirection(), "Vehicle entered intersection from " + v.getRoadDirection());
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging vehicle entered: " + e.getMessage());
            }
        });
    }

    /**
     * Logs vehicle completing intersection clearance and exiting.
     */
    public void logVehicleExited(Vehicle v) {
        if (v == null) return;

        String dir = v.getRoadDirection();
        if (dir != null && throughputMap.containsKey(dir)) {
            throughputMap.get(dir).incrementAndGet();
        }

        if (!DatabaseConnection.isAvailable() || currentRunId <= 0) return;

        dbExecutor.submit(() -> {
            try {
                vehicleDAO.updateVehicleStatus(v.getDbId(), "EXITED",
                        v.getEntryTime(), v.getExitTime(), v.getDistance());
                vehicleDAO.recordVehicleEvent(v.getDbId(), currentRunId, "VEHICLE_EXITED",
                        v.getRoadDirection(), "Vehicle completed exit from " + v.getRoadDirection());
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging vehicle exited: " + e.getMessage());
            }
        });
    }

    /**
     * Logs a traffic signal phase/state change.
     */
    public void logSignalTransition(String activePhase, String roadDirection, String signalState,
                                    int allocatedGreen, int remainingSeconds) {
        if (!DatabaseConnection.isAvailable() || currentRunId <= 0) return;

        // Debounce identical repeated calls
        if (signalState.equals(lastSignalState) && activePhase.equals(lastActivePhase)) {
            return;
        }
        lastSignalState = signalState;
        lastActivePhase = activePhase;

        dbExecutor.submit(() -> {
            try {
                analyticsDAO.recordSignalEvent(currentRunId, activePhase, roadDirection,
                        signalState, allocatedGreen, remainingSeconds);
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging signal event: " + e.getMessage());
            }
        });
    }

    /**
     * Logs periodic second-by-second traffic snapshots for all 4 intersection approaches.
     */
    public void logTickSnapshot(TrafficSimulationModel model) {
        if (model == null || !DatabaseConnection.isAvailable() || currentRunId <= 0) return;

        int runId = currentRunId;
        String activePhase = model.getCurrentAxis();
        int allocatedGreen = model.getCurrentGreenDuration();
        int remainingSeconds = model.getRemainingSeconds();

        Road north = model.getNorthRoad();
        Road south = model.getSouthRoad();
        Road east = model.getEastRoad();
        Road west = model.getWestRoad();

        int nWait = model.getWaitingVehicleCount(north);
        int sWait = model.getWaitingVehicleCount(south);
        int eWait = model.getWaitingVehicleCount(east);
        int wWait = model.getWaitingVehicleCount(west);

        double nQueue = calculateQueueLength(north);
        double sQueue = calculateQueueLength(south);
        double eQueue = calculateQueueLength(east);
        double wQueue = calculateQueueLength(west);

        int nThru = throughputMap.get("North").get();
        int sThru = throughputMap.get("South").get();
        int eThru = throughputMap.get("East").get();
        int wThru = throughputMap.get("West").get();

        dbExecutor.submit(() -> {
            try {
                analyticsDAO.insertTrafficSnapshot(runId, "North", 1, nWait, nQueue,
                        north.getSignal().isGreen() ? "GREEN" : (north.getSignal().isYellow() ? "YELLOW" : "RED"),
                        activePhase, allocatedGreen, remainingSeconds, nThru);

                analyticsDAO.insertTrafficSnapshot(runId, "South", 3, sWait, sQueue,
                        south.getSignal().isGreen() ? "GREEN" : (south.getSignal().isYellow() ? "YELLOW" : "RED"),
                        activePhase, allocatedGreen, remainingSeconds, sThru);

                analyticsDAO.insertTrafficSnapshot(runId, "East", 5, eWait, eQueue,
                        east.getSignal().isGreen() ? "GREEN" : (east.getSignal().isYellow() ? "YELLOW" : "RED"),
                        activePhase, allocatedGreen, remainingSeconds, eThru);

                analyticsDAO.insertTrafficSnapshot(runId, "West", 7, wWait, wQueue,
                        west.getSignal().isGreen() ? "GREEN" : (west.getSignal().isYellow() ? "YELLOW" : "RED"),
                        activePhase, allocatedGreen, remainingSeconds, wThru);
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging tick snapshots: " + e.getMessage());
            }
        });
    }

    private double calculateQueueLength(Road road) {
        if (road == null || road.lanes == null) return 0.0;
        double maxLen = 0.0;
        for (Lane lane : road.lanes) {
            int count = 0;
            for (Vehicle v : lane.vehicles) {
                if (!v.isEnteredIntersection()) {
                    count++;
                }
            }
            double len = count * 32.0; // Body + minSpacing
            if (len > maxLen) maxLen = len;
        }
        return maxLen;
    }

    /**
     * Logs dispatch of an emergency priority vehicle.
     */
    public void logEmergencyDispatched(Vehicle amb, int roadId, String affectedPhase) {
        if (amb == null) return;
        this.emergencyRequestTime = new Timestamp(System.currentTimeMillis());

        if (!DatabaseConnection.isAvailable() || currentRunId <= 0) return;

        dbExecutor.submit(() -> {
            try {
                vehicleDAO.insertVehicle(amb, currentRunId, roadId * 10 + 1, amb.getRoadDirection());
                vehicleDAO.recordVehicleEvent(amb.getDbId(), currentRunId, "EMERGENCY_DETECTED",
                        amb.getRoadDirection(), "Emergency ambulance dispatched on Road " + roadId);

                int eventId = analyticsDAO.recordEmergencyEvent(currentRunId, amb.getDbId(),
                        emergencyRequestTime, emergencyRequestTime, emergencyRequestTime,
                        null, 0, affectedPhase);
                activeEmergencyEventId = eventId;
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging emergency dispatch: " + e.getMessage());
            }
        });
    }

    /**
     * Logs emergency vehicle completing clearance.
     */
    public void logEmergencyCleared(Vehicle amb) {
        if (amb == null || !DatabaseConnection.isAvailable() || currentRunId <= 0) return;

        Timestamp exitTime = new Timestamp(System.currentTimeMillis());
        int durationSec = 0;
        if (emergencyRequestTime != null) {
            durationSec = (int) ((exitTime.getTime() - emergencyRequestTime.getTime()) / 1000L);
        }

        final int duration = Math.max(1, durationSec);
        final int eventId = activeEmergencyEventId;

        dbExecutor.submit(() -> {
            try {
                vehicleDAO.recordVehicleEvent(amb.getDbId(), currentRunId, "EMERGENCY_CLEARED",
                        amb.getRoadDirection(), "Emergency ambulance cleared intersection in " + duration + "s");
                if (eventId > 0) {
                    analyticsDAO.updateEmergencyEventExit(eventId, exitTime, duration);
                }
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging emergency cleared: " + e.getMessage());
            }
        });
    }

    /**
     * Logs clearing of all vehicles from all approaches, updating MySQL records to EXITED
     * and writing an immediate snapshot with 0 waiting vehicles.
     */
    public void logClearAllVehicles() {
        if (!DatabaseConnection.isAvailable() || currentRunId <= 0) return;
        final int runId = currentRunId;
        dbExecutor.submit(() -> {
            try {
                vehicleDAO.markAllWaitingVehiclesAsExited(runId);
                analyticsDAO.insertTrafficSnapshot(runId, "North", 1, 0, 0.0, "RED", "ALL_RED", 0, 0, throughputMap.get("North").get());
                analyticsDAO.insertTrafficSnapshot(runId, "South", 3, 0, 0.0, "RED", "ALL_RED", 0, 0, throughputMap.get("South").get());
                analyticsDAO.insertTrafficSnapshot(runId, "East", 5, 0, 0.0, "RED", "ALL_RED", 0, 0, throughputMap.get("East").get());
                analyticsDAO.insertTrafficSnapshot(runId, "West", 7, 0, 0.0, "RED", "ALL_RED", 0, 0, throughputMap.get("West").get());
                System.out.println("[TrafficDataLogger] Cleared all vehicles in MySQL for Run #" + runId);
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error logging clear all vehicles: " + e.getMessage());
            }
        });
    }

    /**
     * Logs resetting vehicle density on a specific approach road, updating old vehicles to EXITED
     * and inserting the new vehicles into MySQL.
     */
    public void logRoadDensityReset(Road road) {
        if (road == null || !DatabaseConnection.isAvailable() || currentRunId <= 0) return;
        final int runId = currentRunId;
        final String dir = road.getDirection();
        dbExecutor.submit(() -> {
            try {
                vehicleDAO.markRoadWaitingVehiclesAsExited(runId, dir);
                registerInitialVehicles(road, runId);
                System.out.println("[TrafficDataLogger] Reset density for " + dir + " in MySQL for Run #" + runId);
            } catch (Exception e) {
                System.err.println("[TrafficDataLogger] Error resetting road density in MySQL: " + e.getMessage());
            }
        });
    }

    /**
     * Waits for all currently queued background database tasks to complete.
     */
    public void flush() {
        try {
            dbExecutor.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    /**
     * Concludes the current simulation run session.
     */
    public void endSession(String status) {
        if (!sessionActive || currentRunId <= 0) return;
        int runId = currentRunId;
        sessionActive = false;

        if (DatabaseConnection.isAvailable()) {
            dbExecutor.submit(() -> {
                simulationDAO.endSimulationRun(runId, status != null ? status : "COMPLETED");
                System.out.println("[TrafficDataLogger] Closed Simulation Run #" + runId + " (" + status + ")");
            });
        }
    }

    /**
     * Shuts down the background writer cleanly.
     */
    public void shutdown() {
        endSession("STOPPED");
        dbExecutor.shutdown();
    }
}
