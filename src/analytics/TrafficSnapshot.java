package analytics;

import java.sql.Timestamp;

/**
 * Value object representing a point-in-time observation of an intersection approach.
 */
public class TrafficSnapshot {

    private final int runId;
    private final Timestamp timestamp;
    private final String roadDirection;
    private final Integer laneId;
    private final int waitingVehicleCount;
    private final double queueLength;
    private final String signalState;
    private final String activePhase;
    private final int allocatedGreenSeconds;
    private final int remainingSeconds;
    private final int vehicleThroughput;

    public TrafficSnapshot(int runId, Timestamp timestamp, String roadDirection, Integer laneId,
                           int waitingVehicleCount, double queueLength, String signalState,
                           String activePhase, int allocatedGreenSeconds, int remainingSeconds,
                           int vehicleThroughput) {
        this.runId = runId;
        this.timestamp = timestamp;
        this.roadDirection = roadDirection;
        this.laneId = laneId;
        this.waitingVehicleCount = waitingVehicleCount;
        this.queueLength = queueLength;
        this.signalState = signalState;
        this.activePhase = activePhase;
        this.allocatedGreenSeconds = allocatedGreenSeconds;
        this.remainingSeconds = remainingSeconds;
        this.vehicleThroughput = vehicleThroughput;
    }

    public int getRunId() { return runId; }
    public Timestamp getTimestamp() { return timestamp; }
    public String getRoadDirection() { return roadDirection; }
    public Integer getLaneId() { return laneId; }
    public int getWaitingVehicleCount() { return waitingVehicleCount; }
    public double getQueueLength() { return queueLength; }
    public String getSignalState() { return signalState; }
    public String getActivePhase() { return activePhase; }
    public int getAllocatedGreenSeconds() { return allocatedGreenSeconds; }
    public int getRemainingSeconds() { return remainingSeconds; }
    public int getVehicleThroughput() { return vehicleThroughput; }
}
