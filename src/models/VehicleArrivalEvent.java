package models;

import java.util.Objects;

/**
 * Immutable event representing a requested dynamic vehicle arrival.
 * Contains only minimal, immutable data required to validate and safely construct
 * a vehicle on the simulation thread (EDT).
 */
public final class VehicleArrivalEvent {

    private final long sequenceId;
    private final int targetRoadId; // 1 = North, 2 = South, 3 = East, 4 = West
    private final String vehicleType;
    private final long generationToken;

    public VehicleArrivalEvent(long sequenceId, int targetRoadId, String vehicleType, long generationToken) {
        if (sequenceId <= 0) {
            throw new IllegalArgumentException("sequenceId must be positive: " + sequenceId);
        }
        if (targetRoadId < 1 || targetRoadId > 4) {
            throw new IllegalArgumentException("targetRoadId must be between 1 and 4: " + targetRoadId);
        }
        this.sequenceId = sequenceId;
        this.targetRoadId = targetRoadId;
        this.vehicleType = Objects.requireNonNull(vehicleType, "vehicleType cannot be null");
        if (generationToken <= 0) {
            throw new IllegalArgumentException("generationToken must be positive: " + generationToken);
        }
        this.generationToken = generationToken;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public int getTargetRoadId() {
        return targetRoadId;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public long getGenerationToken() {
        return generationToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VehicleArrivalEvent that = (VehicleArrivalEvent) o;
        return sequenceId == that.sequenceId &&
                targetRoadId == that.targetRoadId &&
                generationToken == that.generationToken &&
                Objects.equals(vehicleType, that.vehicleType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequenceId, targetRoadId, vehicleType, generationToken);
    }

    @Override
    public String toString() {
        return "VehicleArrivalEvent{" +
                "sequenceId=" + sequenceId +
                ", targetRoadId=" + targetRoadId +
                ", vehicleType='" + vehicleType + '\'' +
                ", generationToken=" + generationToken +
                '}';
    }
}
