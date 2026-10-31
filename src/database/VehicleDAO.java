package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import models.Vehicle;

/**
 * Data Access Object for vehicles and vehicle_events tables in MySQL.
 */
public class VehicleDAO {

    public static class VehicleRecord {
        private final int vehicleId;
        private final String vehicleNumber;
        private final int runId;
        private final int laneId;
        private final String laneName;
        private final String roadDirection;
        private final String vehicleType;
        private final boolean isEmergency;
        private final Timestamp arrivalTime;
        private final Timestamp entryTime;
        private final Timestamp exitTime;
        private final double speed;
        private final String currentStatus;
        private final double position;

        public VehicleRecord(int vehicleId, String vehicleNumber, int runId, int laneId, String laneName,
                             String roadDirection, String vehicleType, boolean isEmergency,
                             Timestamp arrivalTime, Timestamp entryTime, Timestamp exitTime,
                             double speed, String currentStatus, double position) {
            this.vehicleId = vehicleId;
            this.vehicleNumber = vehicleNumber;
            this.runId = runId;
            this.laneId = laneId;
            this.laneName = laneName;
            this.roadDirection = roadDirection;
            this.vehicleType = vehicleType;
            this.isEmergency = isEmergency;
            this.arrivalTime = arrivalTime;
            this.entryTime = entryTime;
            this.exitTime = exitTime;
            this.speed = speed;
            this.currentStatus = currentStatus;
            this.position = position;
        }

        public int getVehicleId() { return vehicleId; }
        public String getVehicleNumber() { return vehicleNumber; }
        public int getRunId() { return runId; }
        public int getLaneId() { return laneId; }
        public String getLaneName() { return laneName; }
        public String getRoadDirection() { return roadDirection; }
        public String getVehicleType() { return vehicleType; }
        public boolean isEmergency() { return isEmergency; }
        public Timestamp getArrivalTime() { return arrivalTime; }
        public Timestamp getEntryTime() { return entryTime; }
        public Timestamp getExitTime() { return exitTime; }
        public double getSpeed() { return speed; }
        public String getCurrentStatus() { return currentStatus; }
        public double getPosition() { return position; }
    }

    /**
     * Inserts an individual vehicle into MySQL, assigns its dbId, and logs VEHICLE_ADDED event.
     */
    public int insertVehicle(Vehicle v, int runId, int laneId, String roadDirection) {
        if (v == null || runId <= 0) return -1;

        String sql = "INSERT INTO vehicles (vehicle_number, run_id, lane_id, vehicle_type, is_emergency, " +
                     "arrival_time, vehicle_speed, current_status, current_position) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, v.getVehicleNumber());
            ps.setInt(2, runId);
            ps.setInt(3, laneId > 0 ? laneId : 1);
            ps.setString(4, v.getType());
            ps.setBoolean(5, v.isEmergency());
            ps.setTimestamp(6, v.getArrivalTime() != null ? v.getArrivalTime() : new Timestamp(System.currentTimeMillis()));
            ps.setDouble(7, v.getSpeed());
            ps.setString(8, v.getCurrentStatus() != null ? v.getCurrentStatus() : "WAITING");
            ps.setDouble(9, v.getDistance());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int dbId = rs.getInt(1);
                    v.setDbId(dbId);
                    v.setRunId(runId);
                    v.setLaneId(laneId);
                    v.setRoadDirection(roadDirection);

                    // Also record event
                    recordVehicleEvent(dbId, runId, "VEHICLE_ADDED", roadDirection,
                            "Vehicle " + v.getVehicleNumber() + " (" + v.getType() + ") queued at " + roadDirection);
                    return dbId;
                }
            }
        } catch (SQLException e) {
            System.err.println("[VehicleDAO] Failed to insert vehicle " + v.getVehicleNumber() + ": " + e.getMessage());
        }
        return -1;
    }

    /**
     * Batch inserts multiple vehicles (e.g. at initial setup) within a transaction for high performance.
     */
    public void insertBatchVehicles(List<Vehicle> vehicles, int runId, int laneId, String roadDirection) {
        if (vehicles == null || vehicles.isEmpty() || runId <= 0) return;

        String sql = "INSERT INTO vehicles (vehicle_number, run_id, lane_id, vehicle_type, is_emergency, " +
                     "arrival_time, vehicle_speed, current_status, current_position) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                for (Vehicle v : vehicles) {
                    ps.setString(1, v.getVehicleNumber());
                    ps.setInt(2, runId);
                    ps.setInt(3, laneId > 0 ? laneId : 1);
                    ps.setString(4, v.getType());
                    ps.setBoolean(5, v.isEmergency());
                    ps.setTimestamp(6, v.getArrivalTime() != null ? v.getArrivalTime() : new Timestamp(System.currentTimeMillis()));
                    ps.setDouble(7, v.getSpeed());
                    ps.setString(8, v.getCurrentStatus() != null ? v.getCurrentStatus() : "WAITING");
                    ps.setDouble(9, v.getDistance());
                    ps.addBatch();
                }
                ps.executeBatch();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    int i = 0;
                    while (rs.next() && i < vehicles.size()) {
                        Vehicle v = vehicles.get(i);
                        v.setDbId(rs.getInt(1));
                        v.setRunId(runId);
                        v.setLaneId(laneId);
                        v.setRoadDirection(roadDirection);
                        i++;
                    }
                }
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[VehicleDAO] Failed batch insert of vehicles: " + e.getMessage());
        }
    }

    /**
     * Updates vehicle status (WAITING -> ENTERED_INTERSECTION -> EXITED), position, and timestamps.
     */
    public void updateVehicleStatus(int dbId, String status, Timestamp entryTime, Timestamp exitTime, double position) {
        if (dbId <= 0) return;
        String sql = "UPDATE vehicles SET current_status = ?, intersection_entry_time = COALESCE(?, intersection_entry_time), " +
                     "intersection_exit_time = COALESCE(?, intersection_exit_time), current_position = ? WHERE vehicle_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            if (entryTime != null) ps.setTimestamp(2, entryTime);
            else ps.setNull(2, Types.TIMESTAMP);

            if (exitTime != null) ps.setTimestamp(3, exitTime);
            else ps.setNull(3, Types.TIMESTAMP);

            ps.setDouble(4, position);
            ps.setInt(5, dbId);

            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VehicleDAO] Failed to update vehicle status for dbId #" + dbId + ": " + e.getMessage());
        }
    }

    /**
     * Records a lifecycle event in the vehicle_events table.
     */
    public void recordVehicleEvent(int vehicleDbId, int runId, String eventType, String roadDirection, String description) {
        if (vehicleDbId <= 0 || runId <= 0) return;
        String sql = "INSERT INTO vehicle_events (vehicle_id, run_id, event_type, event_timestamp, road_direction, description) " +
                     "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, vehicleDbId);
            ps.setInt(2, runId);
            ps.setString(3, eventType);
            ps.setString(4, roadDirection != null ? roadDirection : "Unknown");
            ps.setString(5, description);

            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[VehicleDAO] Failed to record vehicle event: " + e.getMessage());
        }
    }

    /**
     * Retrieves all vehicles associated with a specific simulation run.
     */
    public List<VehicleRecord> getVehiclesByRun(int runId) {
        List<VehicleRecord> list = new ArrayList<>();
        String sql = "SELECT v.vehicle_id, v.vehicle_number, v.run_id, v.lane_id, COALESCE(l.lane_name, 'N/A') AS lane_name, " +
                     "COALESCE(r.direction, 'Unknown') AS road_direction, v.vehicle_type, v.is_emergency, " +
                     "v.arrival_time, v.intersection_entry_time, v.intersection_exit_time, v.vehicle_speed, " +
                     "v.current_status, v.current_position " +
                     "FROM vehicles v " +
                     "LEFT JOIN lanes l ON v.lane_id = l.lane_id " +
                     "LEFT JOIN roads r ON l.road_id = r.road_id " +
                     "WHERE v.run_id = ? ORDER BY v.vehicle_id ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new VehicleRecord(
                        rs.getInt("vehicle_id"),
                        rs.getString("vehicle_number"),
                        rs.getInt("run_id"),
                        rs.getInt("lane_id"),
                        rs.getString("lane_name"),
                        rs.getString("road_direction"),
                        rs.getString("vehicle_type"),
                        rs.getBoolean("is_emergency"),
                        rs.getTimestamp("arrival_time"),
                        rs.getTimestamp("intersection_entry_time"),
                        rs.getTimestamp("intersection_exit_time"),
                        rs.getDouble("vehicle_speed"),
                        rs.getString("current_status"),
                        rs.getDouble("current_position")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("[VehicleDAO] Failed to retrieve vehicles for run #" + runId + ": " + e.getMessage());
        }
        return list;
    }
}
