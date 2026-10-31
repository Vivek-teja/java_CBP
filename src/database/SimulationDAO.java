package database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for managing simulation_runs table in MySQL.
 */
public class SimulationDAO {

    public static class SimulationRunRecord {
        private final int runId;
        private final Timestamp startTime;
        private final Timestamp endTime;
        private final String strategy;
        private final double speed;
        private final String status;
        private final String scenarioId;

        public SimulationRunRecord(int runId, Timestamp startTime, Timestamp endTime,
                                   String strategy, double speed, String status, String scenarioId) {
            this.runId = runId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.strategy = strategy;
            this.speed = speed;
            this.status = status;
            this.scenarioId = scenarioId;
        }

        public int getRunId() { return runId; }
        public Timestamp getStartTime() { return startTime; }
        public Timestamp getEndTime() { return endTime; }
        public String getStrategy() { return strategy; }
        public double getSpeed() { return speed; }
        public String getStatus() { return status; }
        public String getScenarioId() { return scenarioId; }

        @Override
        public String toString() {
            return String.format("Run #%d | %s | Speed: %.0f px/s | %s", runId, strategy, speed, status);
        }
    }

    /**
     * Creates a new simulation session record and returns the auto-generated run_id.
     */
    public int createSimulationRun(String strategy, double speed, String scenarioId) {
        String sql = "INSERT INTO simulation_runs (start_time, selected_strategy, selected_speed, simulation_status, scenario_id) " +
                     "VALUES (CURRENT_TIMESTAMP, ?, ?, 'RUNNING', ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, strategy != null ? strategy : "Density-Based Dynamic Timing");
            ps.setDouble(2, speed > 0 ? speed : 50.0);
            ps.setString(3, scenarioId != null ? scenarioId : "DEFAULT_SCENARIO");

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("[SimulationDAO] Failed to create simulation run: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Marks a simulation run as completed or stopped.
     */
    public void endSimulationRun(int runId, String status) {
        if (runId <= 0) return;
        String sql = "UPDATE simulation_runs SET end_time = CURRENT_TIMESTAMP, simulation_status = ? WHERE run_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status != null ? status : "COMPLETED");
            ps.setInt(2, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SimulationDAO] Failed to end simulation run #" + runId + ": " + e.getMessage());
        }
    }

    /**
     * Updates runtime parameters (e.g. strategy or vehicle speed) for the current run.
     */
    public void updateRunSettings(int runId, String strategy, double speed) {
        if (runId <= 0) return;
        String sql = "UPDATE simulation_runs SET selected_strategy = ?, selected_speed = ? WHERE run_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, strategy);
            ps.setDouble(2, speed);
            ps.setInt(3, runId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SimulationDAO] Failed to update run settings: " + e.getMessage());
        }
    }

    /**
     * Retrieves all historical simulation sessions ordered by newest first.
     */
    public List<SimulationRunRecord> getAllSimulationRuns() {
        List<SimulationRunRecord> list = new ArrayList<>();
        String sql = "SELECT run_id, start_time, end_time, selected_strategy, selected_speed, simulation_status, scenario_id " +
                     "FROM simulation_runs ORDER BY run_id DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(new SimulationRunRecord(
                    rs.getInt("run_id"),
                    rs.getTimestamp("start_time"),
                    rs.getTimestamp("end_time"),
                    rs.getString("selected_strategy"),
                    rs.getDouble("selected_speed"),
                    rs.getString("simulation_status"),
                    rs.getString("scenario_id")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[SimulationDAO] Failed to retrieve simulation runs: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves a single simulation run record by its runId.
     */
    public SimulationRunRecord getSimulationRun(int runId) {
        String sql = "SELECT run_id, start_time, end_time, selected_strategy, selected_speed, simulation_status, scenario_id " +
                     "FROM simulation_runs WHERE run_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, runId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SimulationRunRecord(
                        rs.getInt("run_id"),
                        rs.getTimestamp("start_time"),
                        rs.getTimestamp("end_time"),
                        rs.getString("selected_strategy"),
                        rs.getDouble("selected_speed"),
                        rs.getString("simulation_status"),
                        rs.getString("scenario_id")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("[SimulationDAO] Failed to retrieve run #" + runId + ": " + e.getMessage());
        }
        return null;
    }
}
