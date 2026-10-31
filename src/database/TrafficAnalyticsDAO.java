package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Access Object for traffic_snapshots, signal_events, and emergency_events tables in MySQL.
 */
public class TrafficAnalyticsDAO {

    /**
     * Inserts a periodic intersection observation snapshot into MySQL.
     */
    public void insertTrafficSnapshot(int runId, String roadDirection, Integer laneId,
                                      int waitingCount, double queueLength, String signalState,
                                      String activePhase, int allocatedGreen, int remainingGreen,
                                      int throughput) {
        if (runId <= 0) return;

        String sql = "INSERT INTO traffic_snapshots (run_id, simulation_timestamp, road_direction, lane_id, " +
                     "waiting_vehicle_count, queue_length, signal_state, active_phase, " +
                     "allocated_green_seconds, remaining_seconds, vehicle_throughput) " +
                     "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            ps.setString(2, roadDirection != null ? roadDirection : "Unknown");
            if (laneId != null && laneId > 0) ps.setInt(3, laneId);
            else ps.setNull(3, Types.INTEGER);

            ps.setInt(4, waitingCount);
            ps.setDouble(5, queueLength);
            ps.setString(6, signalState != null ? signalState : "RED");
            ps.setString(7, activePhase != null ? activePhase : "NORTH_SOUTH");
            ps.setInt(8, allocatedGreen);
            ps.setInt(9, remainingGreen);
            ps.setInt(10, throughput);

            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[TrafficAnalyticsDAO] Failed to insert traffic snapshot: " + e.getMessage());
        }
    }

    /**
     * Records a traffic signal phase/state transition into signal_events.
     */
    public void recordSignalEvent(int runId, String activePhase, String roadDirection,
                                  String signalState, int allocatedGreen, int remainingGreen) {
        if (runId <= 0) return;

        String sql = "INSERT INTO signal_events (run_id, active_phase, road_direction, signal_state, " +
                     "allocated_green_seconds, remaining_seconds, event_timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            ps.setString(2, activePhase != null ? activePhase : "NORTH_SOUTH");
            ps.setString(3, roadDirection != null ? roadDirection : "North-South");
            ps.setString(4, signalState != null ? signalState : "GREEN");
            ps.setInt(5, allocatedGreen);
            ps.setInt(6, remainingGreen);

            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[TrafficAnalyticsDAO] Failed to record signal event: " + e.getMessage());
        }
    }

    /**
     * Records an emergency vehicle preemption event in emergency_events.
     */
    public int recordEmergencyEvent(int runId, Integer vehicleDbId, Timestamp detectionTime,
                                    Timestamp requestTime, Timestamp grantTime, Timestamp exitTime,
                                    int duration, String affectedPhase) {
        if (runId <= 0) return -1;

        String sql = "INSERT INTO emergency_events (run_id, vehicle_id, detection_time, priority_request_time, " +
                     "priority_granted_time, emergency_exit_time, priority_duration, affected_phase) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, runId);
            if (vehicleDbId != null && vehicleDbId > 0) ps.setInt(2, vehicleDbId);
            else ps.setNull(2, Types.INTEGER);

            ps.setTimestamp(3, detectionTime != null ? detectionTime : new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(4, requestTime != null ? requestTime : new Timestamp(System.currentTimeMillis()));
            ps.setTimestamp(5, grantTime);
            ps.setTimestamp(6, exitTime);
            ps.setInt(7, duration);
            ps.setString(8, affectedPhase != null ? affectedPhase : "NORTH_SOUTH");

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[TrafficAnalyticsDAO] Failed to record emergency event: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Updates an emergency event when the vehicle exits the intersection.
     */
    public void updateEmergencyEventExit(int emergencyEventId, Timestamp exitTime, int duration) {
        if (emergencyEventId <= 0) return;
        String sql = "UPDATE emergency_events SET emergency_exit_time = ?, priority_duration = ? WHERE emergency_event_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setTimestamp(1, exitTime != null ? exitTime : new Timestamp(System.currentTimeMillis()));
            ps.setInt(2, duration);
            ps.setInt(3, emergencyEventId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[TrafficAnalyticsDAO] Failed to update emergency event exit: " + e.getMessage());
        }
    }

    /**
     * Retrieves summary statistical indicators for a specific simulation run.
     */
    public Map<String, Object> getRunSummaryMetrics(int runId) {
        Map<String, Object> metrics = new HashMap<>();
        if (runId <= 0) return metrics;

        String sql = "SELECT " +
                     "COUNT(*) AS total_vehicles, " +
                     "SUM(CASE WHEN current_status = 'WAITING' THEN 1 ELSE 0 END) AS waiting_vehicles, " +
                     "SUM(CASE WHEN current_status = 'ENTERED_INTERSECTION' THEN 1 ELSE 0 END) AS in_transit_vehicles, " +
                     "SUM(CASE WHEN current_status = 'EXITED' THEN 1 ELSE 0 END) AS exited_vehicles, " +
                     "SUM(CASE WHEN is_emergency = TRUE THEN 1 ELSE 0 END) AS emergency_vehicles, " +
                     "COALESCE(AVG(CASE WHEN intersection_entry_time IS NOT NULL AND arrival_time IS NOT NULL " +
                     "    THEN TIMESTAMPDIFF(SECOND, arrival_time, intersection_entry_time) ELSE NULL END), 0.0) AS avg_wait_seconds " +
                     "FROM vehicles WHERE run_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    metrics.put("total_vehicles", rs.getInt("total_vehicles"));
                    metrics.put("waiting_vehicles", rs.getInt("waiting_vehicles"));
                    metrics.put("in_transit_vehicles", rs.getInt("in_transit_vehicles"));
                    metrics.put("exited_vehicles", rs.getInt("exited_vehicles"));
                    metrics.put("emergency_vehicles", rs.getInt("emergency_vehicles"));
                    metrics.put("avg_wait_seconds", rs.getDouble("avg_wait_seconds"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[TrafficAnalyticsDAO] Failed to calculate run summary metrics: " + e.getMessage());
        }
        return metrics;
    }
}
