# R & RStudio Data Visualization Guide
## Intelligent Traffic Management System Based on Vehicle Density
**Course-Based Project (CBP) | Unified Software Engineering (USE)**

---

### 1. Overview
The `R_Analytics/` package provides statistical data visualization for the Traffic Management System.
R directly connects to MySQL using `DBI` and `RMariaDB`, retrieves actual records for the selected `run_id`, and generates 8 high-resolution publication-quality ggplot2 charts into run-specific output folders:
`R_Analytics/output/run_<run_id>/`

---

### 2. Required R Packages & Installation
The visual engine utilizes the following CRAN packages:
- `DBI` — Database interface standard for R.
- `RMariaDB` — Fast, production-grade MySQL/MariaDB database driver.
- `ggplot2` — Declarative graphics and thematic rendering.
- `dplyr` — Data manipulation and aggregation pipelines.
- `tidyr` — Data reshaping (`pivot_longer`) for comparative charting.
- `scales` — Pretty axis scaling and label formatting.

To verify or install these packages on macOS, run the automated installation script:
```bash
Rscript R_Analytics/install_packages.R
```

---

### 3. Generated Graphs Specification

| Graph # | File Name | Description | Source Table |
|---|---|---|---|
| **Graph 1** | `graph1_vehicle_distribution.png` | Bar chart of waiting vehicles across North, South, East, West | `vehicles` (`status = 'WAITING'`) |
| **Graph 2** | `graph2_green_time_allocation.png` | Bar chart of allocated green durations by compatible phase (N-S vs E-W) | `signal_events` (`signal_state = 'GREEN'`) |
| **Graph 3** | `graph3_queue_length_timeline.png` | Multi-line time series of waiting vehicle count per approach over simulation time | `traffic_snapshots` |
| **Graph 4** | `graph4_signal_state_timeline.png` | Stepped sequence timeline of Green, Yellow clearance, and Red states | `signal_events` |
| **Graph 5** | `graph5_vehicle_throughput.png` | Cumulative completed vehicle exits by road approach | `vehicles` (`status = 'EXITED'`) |
| **Graph 6** | `graph6_waiting_time.png` | Average vehicle queue delay (seconds between arrival and intersection entry) | `vehicles` (`TIMESTAMPDIFF`) |
| **Graph 7** | `graph7_emergency_analysis.png` | Ambulance priority preemption metrics (detection, request, grant, clearance) | `emergency_events` |
| **Graph 8** | `graph8_strategy_comparison.png` | Empirical comparison of Density-Based vs Fixed-Time timing strategies | `simulation_runs` & `traffic_snapshots` |

---

### 4. How Java Triggers R Asynchronously
The Java class `analytics.RAnalyticsRunner` executes R scripts using `java.lang.ProcessBuilder`:
- **Command:** `Rscript R_Analytics/generate_graphs.R <run_id>`
- **Non-Blocking:** Executes in a dedicated background worker thread (`R-Analytics-Worker`), ensuring that the Java Swing Event Dispatch Thread (EDT) and vehicle physics never stall.
- **Controlled Refresh:** Triggered automatically approximately every 5 seconds when the dashboard is visible, or on-demand when the user clicks **`🔄 Refresh Dashboard`**.
- **Callback Notification:** On exit code 0, Java reloads the updated PNG files and displays them in the dashboard tabs.

---

### 5. Running the R Scripts Manually
You can run the graph generation script directly from the terminal for any simulation run ID:

```bash
# Generate graphs for Run #1
Rscript R_Analytics/generate_graphs.R 1

# Generate graphs for Run #4
Rscript R_Analytics/generate_graphs.R 4
```

The generated PNGs will be placed in:
`R_Analytics/output/run_1/`
`R_Analytics/output/run_4/`

---

### 6. Opening the RStudio Project
An RStudio project file is provided at:
`R_Analytics/TrafficAnalytics.Rproj`

To open the project:
1. Double-click `TrafficAnalytics.Rproj` in Finder, or
2. Inside the Java application, click **`📊 Open RStudio Project`** on the Analytics Dashboard toolbar, or
3. From the terminal:
   ```bash
   open R_Analytics/TrafficAnalytics.Rproj
   ```
Inside RStudio, you can inspect `generate_graphs.R`, query MySQL tables interactively using `DBI::dbGetQuery(con, "SELECT * FROM vehicles LIMIT 20")`, and create customized analyses.
