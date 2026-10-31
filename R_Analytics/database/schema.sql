-- =============================================================================
-- Database Schema for Intelligent Traffic Management System CBP
-- Integrated Java + MySQL + R Data Visualization
-- =============================================================================

CREATE DATABASE IF NOT EXISTS traffic_management_db;
USE traffic_management_db;

-- 1. Simulation Runs Table: tracks each application simulation session
CREATE TABLE IF NOT EXISTS simulation_runs (
    run_id INT AUTO_INCREMENT PRIMARY KEY,
    start_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP NULL,
    selected_strategy VARCHAR(100) NOT NULL,
    selected_speed DOUBLE NOT NULL,
    simulation_status VARCHAR(50) DEFAULT 'RUNNING',
    scenario_id VARCHAR(100) DEFAULT 'DEFAULT_SCENARIO',
    INDEX idx_sim_status (simulation_status),
    INDEX idx_sim_start (start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Roads Table: stores the four intersection approaches
CREATE TABLE IF NOT EXISTS roads (
    road_id INT PRIMARY KEY,
    direction VARCHAR(20) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Lanes Table: stores lanes associated with each road approach
CREATE TABLE IF NOT EXISTS lanes (
    lane_id INT PRIMARY KEY,
    road_id INT NOT NULL,
    lane_name VARCHAR(50) NOT NULL,
    FOREIGN KEY (road_id) REFERENCES roads(road_id) ON DELETE CASCADE,
    INDEX idx_lane_road (road_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Vehicles Table: stores individual vehicle records and states
CREATE TABLE IF NOT EXISTS vehicles (
    vehicle_id INT AUTO_INCREMENT PRIMARY KEY,
    vehicle_number VARCHAR(50) NOT NULL,
    run_id INT NOT NULL,
    lane_id INT NULL,
    vehicle_type VARCHAR(50) NOT NULL DEFAULT 'Car',
    is_emergency BOOLEAN DEFAULT FALSE,
    arrival_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    intersection_entry_time TIMESTAMP NULL,
    intersection_exit_time TIMESTAMP NULL,
    vehicle_speed DOUBLE DEFAULT 50.0,
    current_status VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    current_position DOUBLE DEFAULT 0.0,
    FOREIGN KEY (run_id) REFERENCES simulation_runs(run_id) ON DELETE CASCADE,
    FOREIGN KEY (lane_id) REFERENCES lanes(lane_id) ON DELETE SET NULL,
    INDEX idx_vehicles_run (run_id),
    INDEX idx_vehicles_number (vehicle_number),
    INDEX idx_vehicles_status (current_status),
    INDEX idx_vehicles_type (vehicle_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Vehicle Events Table: history of discrete vehicle state transitions
CREATE TABLE IF NOT EXISTS vehicle_events (
    event_id INT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id INT NOT NULL,
    run_id INT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    road_direction VARCHAR(20) NOT NULL,
    description VARCHAR(255) NULL,
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    FOREIGN KEY (run_id) REFERENCES simulation_runs(run_id) ON DELETE CASCADE,
    INDEX idx_veh_events_run (run_id),
    INDEX idx_veh_events_type (event_type),
    INDEX idx_veh_events_time (event_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Signal Events Table: tracks traffic signal transitions and timings
CREATE TABLE IF NOT EXISTS signal_events (
    signal_event_id INT AUTO_INCREMENT PRIMARY KEY,
    run_id INT NOT NULL,
    active_phase VARCHAR(50) NOT NULL,
    road_direction VARCHAR(50) NOT NULL,
    signal_state VARCHAR(20) NOT NULL,
    allocated_green_seconds INT NOT NULL,
    remaining_seconds INT NOT NULL,
    event_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (run_id) REFERENCES simulation_runs(run_id) ON DELETE CASCADE,
    INDEX idx_sig_events_run (run_id),
    INDEX idx_sig_events_phase (active_phase),
    INDEX idx_sig_events_time (event_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Traffic Snapshots Table: periodic second-by-second intersection telemetry
CREATE TABLE IF NOT EXISTS traffic_snapshots (
    snapshot_id INT AUTO_INCREMENT PRIMARY KEY,
    run_id INT NOT NULL,
    simulation_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    road_direction VARCHAR(20) NOT NULL,
    lane_id INT NULL,
    waiting_vehicle_count INT NOT NULL,
    queue_length DOUBLE NOT NULL,
    signal_state VARCHAR(20) NOT NULL,
    active_phase VARCHAR(50) NOT NULL,
    allocated_green_seconds INT NOT NULL,
    remaining_seconds INT NOT NULL,
    vehicle_throughput INT NOT NULL DEFAULT 0,
    FOREIGN KEY (run_id) REFERENCES simulation_runs(run_id) ON DELETE CASCADE,
    FOREIGN KEY (lane_id) REFERENCES lanes(lane_id) ON DELETE SET NULL,
    INDEX idx_snapshots_run_time (run_id, simulation_timestamp),
    INDEX idx_snapshots_road (road_direction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. Emergency Events Table: tracks emergency vehicle preemption lifecycle
CREATE TABLE IF NOT EXISTS emergency_events (
    emergency_event_id INT AUTO_INCREMENT PRIMARY KEY,
    run_id INT NOT NULL,
    vehicle_id INT NULL,
    detection_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    priority_request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    priority_granted_time TIMESTAMP NULL,
    emergency_exit_time TIMESTAMP NULL,
    priority_duration INT DEFAULT 0,
    affected_phase VARCHAR(50) NULL,
    FOREIGN KEY (run_id) REFERENCES simulation_runs(run_id) ON DELETE CASCADE,
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(vehicle_id) ON DELETE SET NULL,
    INDEX idx_emg_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed baseline Roads if not already present
INSERT IGNORE INTO roads (road_id, direction) VALUES
(1, 'North'),
(2, 'South'),
(3, 'East'),
(4, 'West');

-- Seed baseline Lanes for 4-way intersection (both 1..8 and 11..42 numbering used in application)
INSERT IGNORE INTO lanes (lane_id, road_id, lane_name) VALUES
(1, 1, 'N1 (North Left Lane)'),
(2, 1, 'N2 (North Right Lane)'),
(3, 2, 'S1 (South Left Lane)'),
(4, 2, 'S2 (South Right Lane)'),
(5, 3, 'E1 (East Left Lane)'),
(6, 3, 'E2 (East Right Lane)'),
(7, 4, 'W1 (West Left Lane)'),
(8, 4, 'W2 (West Right Lane)'),
(11, 1, 'N1 (North Approach Lane 1)'),
(12, 1, 'N2 (North Approach Lane 2)'),
(21, 2, 'S1 (South Approach Lane 1)'),
(22, 2, 'S2 (South Approach Lane 2)'),
(31, 3, 'E1 (East Approach Lane 1)'),
(32, 3, 'E2 (East Approach Lane 2)'),
(41, 4, 'W1 (West Approach Lane 1)'),
(42, 4, 'W2 (West Approach Lane 2)');
