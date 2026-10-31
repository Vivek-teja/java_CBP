# MySQL Database Integration Guide
## Intelligent Traffic Management System Based on Vehicle Density
**Course-Based Project (CBP) | Unified Software Engineering (USE)**

---

### 1. Overview
This project upgrades the Java Swing traffic simulation into an integrated **Java + MySQL + R Data Visualization** system.
MySQL serves as the persistent source of truth for:
- Every individual vehicle and its unique readable vehicle number (`V001`, `V002`, ...).
- Simulation run sessions (`simulation_runs`).
- Intersection approach geometry (`roads` and `lanes`).
- Vehicle lifecycle events (`vehicle_events`).
- Signal phase transitions and green durations (`signal_events`).
- Second-by-second intersection telemetry snapshots (`traffic_snapshots`).
- Emergency ambulance priority preemption logs (`emergency_events`).

---

### 2. How to Start MySQL on macOS
On macOS (via Homebrew), start or verify the MySQL service:

```bash
# Check service status
brew services list | grep mysql

# Start MySQL if stopped
brew services start mysql

# Or start manually via mysqld
mysql.server start
```

To test connectivity from the terminal:
```bash
mysql -u root -e "SHOW DATABASES;"
```

---

### 3. Database Schema Setup
The dedicated database is named `traffic_management_db`. The full schema script is stored in:
`R_Analytics/database/schema.sql`

To initialize or verify the schema:
```bash
mysql -u root < R_Analytics/database/schema.sql
```

The script uses `CREATE DATABASE IF NOT EXISTS` and `CREATE TABLE IF NOT EXISTS`, ensuring existing data is never destroyed.

#### Schema Table Architecture:
| Table Name | Primary Key | Description |
|---|---|---|
| `simulation_runs` | `run_id` | Stores each simulation session, timing strategy, speed, and status |
| `roads` | `road_id` | Stores North, South, East, West approaches |
| `lanes` | `lane_id` | Stores approach lanes associated with each road |
| `vehicles` | `vehicle_id` | Stores every vehicle with its unique `vehicle_number`, status, and timestamps |
| `vehicle_events` | `event_id` | Discrete event logs (`VEHICLE_ADDED`, `VEHICLE_ENTERED`, `VEHICLE_EXITED`) |
| `signal_events` | `signal_event_id` | Records every signal phase change and assigned green duration |
| `traffic_snapshots` | `snapshot_id` | Second-by-second approach telemetry (queue count, length, throughput) |
| `emergency_events` | `emergency_event_id`| Lifecycle metrics of ambulance priority preemption |

---

### 4. JDBC Driver & Configuration
1. **Connector JAR:**
   The project includes the official MySQL Connector/J driver in `lib/`:
   `lib/mysql-connector-j-9.2.0.jar`
   Configured in Eclipse `.classpath` as a library dependency.

2. **Configuration File (`database.properties`):**
   Credentials are kept external to prevent leaking passwords:
   ```properties
   db.url=jdbc:mysql://localhost:3306/traffic_management_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
   db.user=root
   db.password=
   db.driver=com.mysql.cj.jdbc.Driver
   r.script.path=/usr/local/bin/Rscript
   ```
   *Note:* `database.properties` is ignored by git in `.gitignore`. A template is provided in `database.properties.example`.

3. **Environment Variable Overrides:**
   You can also configure connection parameters via environment variables:
   - `DB_URL`
   - `DB_USER`
   - `DB_PASSWORD`
   - `RSCRIPT_PATH`

---

### 5. How Java Writes Data to MySQL
All database writes are executed **asynchronously** via `analytics.TrafficDataLogger`:
- Operates on a dedicated single-thread daemon executor (`Traffic-Database-Writer`).
- **EDT Safety:** Database I/O never blocks the Swing Event Dispatch Thread or the simulation animation clock.
- **Fault-Tolerant Safe Mode:** If MySQL is temporarily offline or unreachable, the simulation gracefully continues in in-memory mode without crashing.
- **Individual Records:** Every vehicle added generates an individual row in `vehicles` with an atomic readable number (e.g. `V001`, `V002`).

---

### 6. Viewing Simulation History & Exporting Data
In the application:
1. Click **`📊 Open Traffic Analytics`** in the top control panel.
2. The **Traffic Analytics Dashboard** opens.
3. Use the **Simulation Session** dropdown or the **Simulation Session History** tab to view past runs.
4. Click **`💾 Export Database Data`** to export a clean CSV file of all vehicle records for external reporting.
