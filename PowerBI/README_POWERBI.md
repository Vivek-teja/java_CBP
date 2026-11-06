# Power BI Analytics Suite: Intelligent Traffic Management System

This directory contains the Power BI reporting assets, tabular semantic models, project configurations, and live dataset exports for the **Intelligent Traffic Management System Based on Vehicle Density**.

---

## 📁 Artifact Directory Contents

| Artifact | Type | Description |
|---|---|---|
| **[`TrafficManagementAnalytics.pbix`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/TrafficManagementAnalytics.pbix)** | Power BI Report | Full Power BI report archive containing report layout, TMSL semantic model, DAX measures, and visualizations. |
| **[`TrafficManagementAnalytics.pbit`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/TrafficManagementAnalytics.pbit)** | Power BI Template | Official Microsoft Power BI Template archive. Double-click to open in Power BI Desktop, refresh against MySQL, and save as `.pbix`. |
| **[`TrafficManagementAnalytics.pbip`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/TrafficManagementAnalytics.pbip)** | Power BI Project | Modern text-based Power BI Project file (Git version-controlled format). |
| **[`TrafficManagementAnalytics.Report/`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/TrafficManagementAnalytics.Report)** | Report Definition | Contains `definition.pbir` and `report.json` with multi-page visual layout configurations. |
| **[`TrafficManagementAnalytics.SemanticModel/`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/TrafficManagementAnalytics.SemanticModel)** | Semantic Model | Contains `definition.pbism` and `model.bim` (TMSL 1550 tabular schema with relationships & DAX measures). |
| **[`data/`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/data)** | Exported CSV Snapshots | Live exported CSV snapshots of all 8 MySQL database tables (over 5,000+ records). |
| **[`export_data_for_powerbi.py`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/export_data_for_powerbi.py)** | Python Utility | Automated script to re-export fresh simulation data from MySQL `traffic_management_db` to `data/*.csv`. |
| **[`generate_powerbi_assets.py`](file:///Users/mabbu/eclipse-workspace/java_CBP/PowerBI/generate_powerbi_assets.py)** | Build Script | Rebuilds the `.pbix`, `.pbit`, and `.pbip` archives from the current database state. |

---

## 📊 Dashboard Visualizations & Pages

The Power BI report is organized into **three dedicated interactive analytical pages**:

### Page 1: Intersection Overview & Queue Analytics
* **KPI Summary Cards**:
  * **Total Vehicles Simulated**: Cumulative vehicle count across all sessions.
  * **Waiting in Queues**: Active vehicles currently queued at the intersection.
  * **Completed Vehicles (Exited)**: Total vehicles successfully cleared through the intersection.
  * **Emergency Preemptions**: Total emergency vehicles granted green signal priority.
  * **Total Simulation Sessions**: Number of independent simulation runs recorded.
* **Approach Road Queue Distribution** (`columnChart`): Average waiting vehicle count categorized by direction (**North**, **South**, **East**, **West**).
* **Fleet Composition Breakdown** (`pieChart`): Proportional distribution of vehicle types (**Car**, **Bus**, **Bike**, **Ambulance**).
* **Queue Dynamics Timeline** (`lineChart`): Temporal evolution of queue lengths throughout the simulation.
* **Active Vehicle Identity Ledger** (`tableEx`): Granular record of each vehicle's unique identifier (`V001`, `V002`), type, approach direction, and lifecycle status (`WAITING`, `ENTERED_INTERSECTION`, `EXITED`).

### Page 2: Signal Timing & Strategy Performance
* **KPI Summary Cards**:
  * **Total Signal Transition Events**: Number of phase and state transitions logged.
  * **Average Green Phase Duration**: Mean green light allocation in seconds.
  * **Total Vehicle Throughput**: Aggregate cleared vehicle count.
* **Allocated Green Duration by Approach Phase** (`barChart`): Comparison of green time allocated to North-South vs East-West phases.
* **Traffic Density & Queue Length** (`columnChart`): Peak queue accumulation observed across approach roads.
* **Signal Timing Evolution** (`lineChart`): Adaptive adjustment of green durations over simulation cycles under Density-Based timing.
* **Signal Phase Control History** (`tableEx`): Chronological log of phase transitions, remaining green counts, and signal states (`RED`, `YELLOW`, `GREEN`).

### Page 3: Emergency Vehicle Priority & Delays
* **KPI Summary Cards**:
  * **Emergency Preemptions Triggered**: Total priority requests processed.
  * **Avg Emergency Clearance Duration**: Mean time required to clear an emergency vehicle through the intersection.
  * **Max Priority Hold Duration**: Peak green signal extension granted for emergency preemption.
* **Emergency Preemption Duration per Incident** (`barChart`): Clearance duration for each individual emergency vehicle dispatch.
* **Average Speed by Fleet Category** (`columnChart`): Speed profiles across Cars, Buses, Bikes, and Emergency Ambulances.
* **Emergency Audit Trail** (`tableEx`): Comprehensive audit log with timestamps for detection, priority grant, intersection clearance, and duration.

---

## 🗄️ Semantic Data Model & Relationships

The model implements a star/snowflake schema centered around `simulation_runs`:

```
               [roads]
                  │
                  ▼
   [lanes] ◄──────┘
      │
      ▼
  [vehicles] ──────► [simulation_runs] ◄────── [traffic_snapshots]
      │                      ▲
      │                      │
      ▼                      │
[emergency_events] ──────────┤
                             │
                      [signal_events]
```

### Key DAX Measures
```dax
Total Vehicles = COUNTROWS('vehicles')
Waiting Vehicles = CALCULATE(COUNTROWS('vehicles'), 'vehicles'[current_status] = "WAITING")
In Intersection Vehicles = CALCULATE(COUNTROWS('vehicles'), 'vehicles'[current_status] = "ENTERED_INTERSECTION")
Exited Vehicles = CALCULATE(COUNTROWS('vehicles'), 'vehicles'[current_status] = "EXITED")
Emergency Vehicles = CALCULATE(COUNTROWS('vehicles'), 'vehicles'[is_emergency] = 1)
Avg Vehicle Speed = AVERAGE('vehicles'[vehicle_speed])

Peak Queue Length = MAX('traffic_snapshots'[queue_length])
Avg Waiting Count = AVERAGE('traffic_snapshots'[waiting_vehicle_count])
Avg Green Seconds = AVERAGE('traffic_snapshots'[allocated_green_seconds])
Total Vehicle Throughput = MAX('traffic_snapshots'[vehicle_throughput])

Total Signal Transitions = COUNTROWS('signal_events')
Emergency Preemption Count = COUNTROWS('emergency_events')
Avg Emergency Clearance Seconds = AVERAGE('emergency_events'[priority_duration])
Max Priority Duration = MAX('emergency_events'[priority_duration])
Total Simulation Runs = COUNTROWS('simulation_runs')
```

---

## 🚀 How to Open and Use

### Option 1: Double-Click `.pbit` or `.pbix` in Power BI Desktop (Recommended)
1. In Windows (or Windows VM on Mac via Parallels / VMware Fusion):
   - Double-click **`TrafficManagementAnalytics.pbit`** (or `TrafficManagementAnalytics.pbix`).
2. Power BI Desktop opens the file, reads the visual layouts, and connects to the data sources.
3. If connecting to live MySQL: Ensure MySQL Server is reachable at `localhost:3306` with user `root` (or enter credentials when prompted).
4. Click **File > Save As** to save the active report as a `.pbix` file.

### Option 2: Open Modern Power BI Project (`.pbip`)
1. In Power BI Desktop:
   - Click **File > Open report > Browse reports**.
   - Select **`TrafficManagementAnalytics.pbip`**.
2. Power BI Desktop opens the report and binds it to the local `TrafficManagementAnalytics.SemanticModel`.

### Option 3: Offline / CSV-Based Data Loading
If the host machine does not have the MySQL ODBC Connector installed:
1. Open Power BI Desktop.
2. Click **Home > Get Data > Text/CSV**.
3. Select the CSV files from the **`PowerBI/data/`** directory:
   - `vehicles.csv`
   - `traffic_snapshots.csv`
   - `signal_events.csv`
   - `emergency_events.csv`
   - `simulation_runs.csv`
   - `roads.csv`
   - `lanes.csv`
4. The tables will load with complete historical simulation records.

### Refreshing Data from MySQL
To refresh the CSV datasets after running new simulations in Eclipse:
```bash
python3 PowerBI/export_data_for_powerbi.py
```
This automatically updates all CSV files in `PowerBI/data/` with the latest records from `traffic_management_db`.
