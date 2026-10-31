#!/usr/bin/env Rscript
# =============================================================================
# Traffic Analytics Data Visualization Engine
# Generates 8 Comprehensive Graphs from MySQL traffic_management_db
# Integrated Java + MySQL + R Data Visualization CBP
# =============================================================================

suppressPackageStartupMessages({
  library(DBI)
  library(RMariaDB)
  library(ggplot2)
  library(dplyr)
  library(tidyr)
  library(scales)
})

# Parse command-line arguments: run_id
args <- commandArgs(trailingOnly = TRUE)
run_id <- if (length(args) >= 1) as.integer(args[1]) else 1

cat(sprintf("=== Running Traffic Analytics Graph Generator for Run ID: %d ===\n", run_id))

# Parse database configuration from database.properties if available
db_host <- "127.0.0.1"
db_port <- 3306
db_name <- "traffic_management_db"
db_user <- "root"
db_password <- ""

prop_file <- "database.properties"
if (file.exists(prop_file)) {
  lines <- readLines(prop_file, warn = FALSE)
  for (line in lines) {
    line <- trimws(line)
    if (nchar(line) > 0 && !startsWith(line, "#")) {
      parts <- strsplit(line, "=", fixed = TRUE)[[1]]
      if (length(parts) >= 2) {
        key <- trimws(parts[1])
        val <- trimws(paste(parts[-1], collapse = "="))
        if (key == "db.user") db_user <- val
        if (key == "db.password") db_password <- val
      }
    }
  }
}

# Connect to MySQL via RMariaDB
con <- tryCatch({
  dbConnect(
    RMariaDB::MariaDB(),
    dbname = db_name,
    user = db_user,
    password = db_password,
    host = db_host,
    port = db_port
  )
}, error = function(e) {
  stop(sprintf("Failed to connect to MySQL database '%s': %s", db_name, e$message))
})

# Ensure connection closes on exit
on.exit(dbDisconnect(con), add = TRUE)

# Prepare output directory
output_dir <- file.path("R_Analytics", "output", sprintf("run_%d", run_id))
if (!dir.exists(output_dir)) {
  dir.create(output_dir, recursive = TRUE)
}

# Common ggplot modern theme
theme_cbp <- function() {
  theme_minimal(base_family = "sans") +
    theme(
      plot.title = element_text(face = "bold", size = 14, color = "#1a2530", hjust = 0.5, margin = margin(b = 8)),
      plot.subtitle = element_text(size = 10, color = "#556575", hjust = 0.5, margin = margin(b = 12)),
      plot.caption = element_text(size = 8, color = "#8898a8", hjust = 1, margin = margin(t = 8)),
      axis.title = element_text(face = "bold", size = 11, color = "#2c3e50"),
      axis.text = element_text(size = 9, color = "#34495e"),
      panel.grid.major = element_line(color = "#e2e8f0", linewidth = 0.4),
      panel.grid.minor = element_blank(),
      plot.background = element_rect(fill = "#ffffff", color = NA),
      panel.background = element_rect(fill = "#f8fafc", color = "#cbd5e1", linewidth = 0.5),
      legend.position = "top",
      legend.title = element_text(face = "bold", size = 10),
      legend.text = element_text(size = 9)
    )
}

road_colors <- c("North" = "#2563eb", "South" = "#10b981", "East" = "#f59e0b", "West" = "#ef4444")
phase_colors <- c("NORTH_SOUTH" = "#0ea5e9", "EAST_WEST" = "#f97316")

cat(sprintf("Output destination: %s\n", output_dir))

# =============================================================================
# Graph 1: Vehicle Distribution by Road
# =============================================================================
cat("Generating Graph 1: Vehicle Distribution by Road...\n")
query_g1 <- sprintf("
  SELECT r.direction AS road_direction,
         COUNT(v.vehicle_id) AS waiting_count
  FROM roads r
  LEFT JOIN lanes l ON r.road_id = l.road_id
  LEFT JOIN vehicles v ON l.lane_id = v.lane_id
                       AND v.run_id = %d
                       AND v.current_status = 'WAITING'
  GROUP BY r.direction
  ORDER BY FIELD(r.direction, 'North', 'South', 'East', 'West');
", run_id)

df_g1 <- dbGetQuery(con, query_g1)
df_g1$road_direction <- factor(df_g1$road_direction, levels = c("North", "South", "East", "West"))
df_g1$waiting_count[is.na(df_g1$waiting_count)] <- 0

p1 <- ggplot(df_g1, aes(x = road_direction, y = waiting_count, fill = road_direction)) +
  geom_col(width = 0.55, color = "#1e293b", linewidth = 0.6) +
  geom_text(aes(label = sprintf("%.0f veh", as.numeric(waiting_count))),
            vjust = -0.5, fontface = "bold", size = 4.2, color = "#0f172a") +
  scale_fill_manual(values = road_colors, guide = "none") +
  scale_y_continuous(expand = expansion(mult = c(0, 0.2)), breaks = scales::pretty_breaks(n = 6)) +
  labs(
    title = sprintf("Graph 1: Waiting Vehicle Distribution by Approach Road (Run #%d)", run_id),
    subtitle = "Actual waiting queue sizes retrieved from MySQL vehicles table",
    x = "Road Approach",
    y = "Number of Waiting Vehicles",
    caption = "Intelligent Traffic Management System | Java + MySQL + R"
  ) +
  theme_cbp()

ggsave(file.path(output_dir, "graph1_vehicle_distribution.png"), plot = p1, width = 8, height = 5, dpi = 150)

# =============================================================================
# Graph 2: Allocated Green Time by Traffic Phase
# =============================================================================
cat("Generating Graph 2: Allocated Green Time by Phase...\n")
query_g2 <- sprintf("
  SELECT active_phase,
         ROUND(AVG(allocated_green_seconds), 1) AS avg_green_sec,
         MAX(allocated_green_seconds) AS max_green_sec,
         MIN(allocated_green_seconds) AS min_green_sec,
         COUNT(*) AS phase_count
  FROM signal_events
  WHERE run_id = %d AND signal_state = 'GREEN'
  GROUP BY active_phase;
", run_id)

df_g2 <- dbGetQuery(con, query_g2)

if (nrow(df_g2) == 0) {
  # Default fallback if no signal events logged yet
  df_g2 <- data.frame(
    active_phase = c("NORTH_SOUTH", "EAST_WEST"),
    avg_green_sec = c(0, 0),
    max_green_sec = c(0, 0),
    min_green_sec = c(0, 0),
    phase_count = c(0, 0)
  )
}

p2 <- ggplot(df_g2, aes(x = active_phase, y = avg_green_sec, fill = active_phase)) +
  geom_col(width = 0.5, color = "#1e293b", linewidth = 0.6) +
  geom_text(aes(label = sprintf("%.1f s\n(n=%.0f)", as.numeric(avg_green_sec), as.numeric(phase_count))),
            vjust = -0.3, fontface = "bold", size = 4.0, color = "#0f172a") +
  scale_fill_manual(values = phase_colors, guide = "none") +
  scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = scales::pretty_breaks(n = 6)) +
  scale_x_discrete(labels = c("NORTH_SOUTH" = "North–South Phase\n(Coordinated Axis)", "EAST_WEST" = "East–West Phase\n(Coordinated Axis)")) +
  labs(
    title = sprintf("Graph 2: Average Allocated Green Duration by Traffic Phase (Run #%d)", run_id),
    subtitle = "Actual green durations assigned by Java controller and stored in signal_events",
    x = "Traffic Signal Phase",
    y = "Average Allocated Green Time (Seconds)",
    caption = "Source: signal_events table | Preserves N-S / E-W dual-ring compatibility"
  ) +
  theme_cbp()

ggsave(file.path(output_dir, "graph2_green_time_allocation.png"), plot = p2, width = 8, height = 5, dpi = 150)

# =============================================================================
# Graph 3: Queue Length over Simulation Time
# =============================================================================
cat("Generating Graph 3: Queue Length over Simulation Time...\n")
query_g3 <- sprintf("
  SELECT simulation_timestamp,
         road_direction,
         waiting_vehicle_count,
         queue_length
  FROM traffic_snapshots
  WHERE run_id = %d
  ORDER BY simulation_timestamp ASC;
", run_id)

df_g3 <- dbGetQuery(con, query_g3)

if (nrow(df_g3) > 0) {
  df_g3$simulation_timestamp <- as.POSIXct(df_g3$simulation_timestamp)
  # Calculate relative simulation seconds from start
  start_t <- min(df_g3$simulation_timestamp)
  df_g3$elapsed_sec <- as.numeric(difftime(df_g3$simulation_timestamp, start_t, units = "secs"))

  p3 <- ggplot(df_g3, aes(x = elapsed_sec, y = waiting_vehicle_count, color = road_direction)) +
    geom_line(linewidth = 1.1, alpha = 0.85) +
    geom_point(size = 2.2, alpha = 0.9) +
    scale_color_manual(values = road_colors, name = "Road Approach:") +
    scale_y_continuous(breaks = scales::pretty_breaks(n = 6)) +
    labs(
      title = sprintf("Graph 3: Waiting Queue Length over Simulation Time (Run #%d)", run_id),
      subtitle = "Second-by-second approach queue accumulation and dissipation tracking",
      x = "Elapsed Simulation Time (Seconds)",
      y = "Waiting Vehicle Count",
      caption = "Source: traffic_snapshots table"
    ) +
    theme_cbp()
} else {
  # Empty placeholder card
  p3 <- ggplot() +
    annotate("text", x = 1, y = 1, label = sprintf("No snapshots recorded yet for Run #%d.\nLet simulation advance to capture timeline data.", run_id),
             size = 5, fontface = "italic", color = "#64748b") +
    theme_void() +
    labs(title = sprintf("Graph 3: Queue Length over Time (Run #%d - Awaiting Data)", run_id))
}

ggsave(file.path(output_dir, "graph3_queue_length_timeline.png"), plot = p3, width = 8.5, height = 5, dpi = 150)

# =============================================================================
# Graph 4: Traffic Signal State Timeline
# =============================================================================
cat("Generating Graph 4: Traffic Signal State Timeline...\n")
query_g4 <- sprintf("
  SELECT signal_event_id,
         active_phase,
         signal_state,
         allocated_green_seconds,
         event_timestamp
  FROM signal_events
  WHERE run_id = %d
  ORDER BY event_timestamp ASC;
", run_id)

df_g4 <- dbGetQuery(con, query_g4)

if (nrow(df_g4) >= 2) {
  df_g4$event_timestamp <- as.POSIXct(df_g4$event_timestamp)
  start_t <- min(df_g4$event_timestamp)
  df_g4$start_sec <- as.numeric(difftime(df_g4$event_timestamp, start_t, units = "secs"))
  df_g4$end_sec <- c(df_g4$start_sec[-1], max(df_g4$start_sec) + 3) # default end for last event

  signal_palette <- c("GREEN" = "#22c55e", "YELLOW" = "#eab308", "RED" = "#ef4444")

  p4 <- ggplot(df_g4, aes(ymin = as.numeric(factor(active_phase)) - 0.35,
                          ymax = as.numeric(factor(active_phase)) + 0.35,
                          xmin = start_sec, xmax = end_sec, fill = signal_state)) +
    geom_rect(color = "#1e293b", linewidth = 0.4) +
    scale_fill_manual(values = signal_palette, name = "Signal State:") +
    scale_y_continuous(breaks = 1:length(unique(df_g4$active_phase)),
                       labels = levels(factor(df_g4$active_phase))) +
    labs(
      title = sprintf("Graph 4: Traffic Signal Phase & State Sequence Timeline (Run #%d)", run_id),
      subtitle = "Sequence of Green Right-of-Way, Yellow Clearance, and Red Transitions",
      x = "Elapsed Time (Seconds)",
      y = "Active Axis Phase",
      caption = "Source: signal_events table"
    ) +
    theme_cbp()
} else {
  p4 <- ggplot() +
    annotate("text", x = 1, y = 1, label = sprintf("Awaiting signal phase transitions for Run #%d...\nLet simulation run to record phase sequence.", run_id),
             size = 5, fontface = "italic", color = "#64748b") +
    theme_void() +
    labs(title = sprintf("Graph 4: Signal State Timeline (Run #%d - Awaiting Data)", run_id))
}

ggsave(file.path(output_dir, "graph4_signal_state_timeline.png"), plot = p4, width = 8.5, height = 5, dpi = 150)

# =============================================================================
# Graph 5: Vehicle Throughput by Road
# =============================================================================
cat("Generating Graph 5: Vehicle Throughput by Road...\n")
query_g5 <- sprintf("
  SELECT r.direction AS road_direction,
         COUNT(v.vehicle_id) AS throughput_count
  FROM roads r
  LEFT JOIN lanes l ON r.road_id = l.road_id
  LEFT JOIN vehicles v ON l.lane_id = v.lane_id
                       AND v.run_id = %d
                       AND v.current_status = 'EXITED'
  GROUP BY r.direction
  ORDER BY FIELD(r.direction, 'North', 'South', 'East', 'West');
", run_id)

df_g5 <- dbGetQuery(con, query_g5)
df_g5$road_direction <- factor(df_g5$road_direction, levels = c("North", "South", "East", "West"))
df_g5$throughput_count[is.na(df_g5$throughput_count)] <- 0

p5 <- ggplot(df_g5, aes(x = road_direction, y = throughput_count, fill = road_direction)) +
  geom_col(width = 0.55, color = "#1e293b", linewidth = 0.6) +
  geom_text(aes(label = sprintf("%.0f exited", as.numeric(throughput_count))),
            vjust = -0.5, fontface = "bold", size = 4.2, color = "#0f172a") +
  scale_fill_manual(values = road_colors, guide = "none") +
  scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = scales::pretty_breaks(n = 6)) +
  labs(
    title = sprintf("Graph 5: Total Vehicle Throughput by Approach Road (Run #%d)", run_id),
    subtitle = "Vehicles that have completely cleared and exited the intersection box",
    x = "Road Approach",
    y = "Throughput (Completed Vehicle Exits)",
    caption = "Source: vehicles table (current_status = 'EXITED')"
  ) +
  theme_cbp()

ggsave(file.path(output_dir, "graph5_vehicle_throughput.png"), plot = p5, width = 8, height = 5, dpi = 150)

# =============================================================================
# Graph 6: Average Vehicle Waiting Time
# =============================================================================
cat("Generating Graph 6: Average Waiting Time by Approach...\n")
query_g6 <- sprintf("
  SELECT r.direction AS road_direction,
         COALESCE(AVG(TIMESTAMPDIFF(SECOND, v.arrival_time, v.intersection_entry_time)), 0.0) AS avg_wait_seconds,
         COUNT(v.vehicle_id) AS sampled_vehicles
  FROM roads r
  LEFT JOIN lanes l ON r.road_id = l.road_id
  LEFT JOIN vehicles v ON l.lane_id = v.lane_id
                       AND v.run_id = %d
                       AND v.intersection_entry_time IS NOT NULL
  GROUP BY r.direction
  ORDER BY FIELD(r.direction, 'North', 'South', 'East', 'West');
", run_id)

df_g6 <- dbGetQuery(con, query_g6)
df_g6$road_direction <- factor(df_g6$road_direction, levels = c("North", "South", "East", "West"))
df_g6$avg_wait_seconds[is.na(df_g6$avg_wait_seconds)] <- 0.0

p6 <- ggplot(df_g6, aes(x = road_direction, y = avg_wait_seconds, fill = road_direction)) +
  geom_col(width = 0.55, color = "#1e293b", linewidth = 0.6) +
  geom_text(aes(label = sprintf("%.1f s\n(n=%.0f)", as.numeric(avg_wait_seconds), as.numeric(sampled_vehicles))),
            vjust = -0.3, fontface = "bold", size = 4.0, color = "#0f172a") +
  scale_fill_manual(values = road_colors, guide = "none") +
  scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = scales::pretty_breaks(n = 6)) +
  labs(
    title = sprintf("Graph 6: Average Vehicle Waiting Time by Approach (Run #%d)", run_id),
    subtitle = "Waiting Time = Duration between queue arrival and intersection box entry",
    x = "Road Approach",
    y = "Average Waiting Time (Seconds)",
    caption = "Source: vehicles table (arrival_time to intersection_entry_time interval)"
  ) +
  theme_cbp()

ggsave(file.path(output_dir, "graph6_waiting_time.png"), plot = p6, width = 8, height = 5, dpi = 150)

# =============================================================================
# Graph 7: Emergency Vehicle Priority Analysis
# =============================================================================
cat("Generating Graph 7: Emergency Vehicle Priority Analysis...\n")
query_g7 <- sprintf("
  SELECT e.emergency_event_id,
         COALESCE(v.vehicle_number, 'EMG-01') AS vehicle_number,
         e.detection_time,
         e.priority_request_time,
         e.priority_granted_time,
         e.emergency_exit_time,
         e.priority_duration,
         e.affected_phase
  FROM emergency_events e
  LEFT JOIN vehicles v ON e.vehicle_id = v.vehicle_id
  WHERE e.run_id = %d;
", run_id)

df_g7 <- dbGetQuery(con, query_g7)

if (nrow(df_g7) > 0) {
  # Plot emergency metrics
  df_g7_plot <- df_g7 %>%
    mutate(
      label = sprintf("%s\n(Phase: %s)", vehicle_number, affected_phase),
      duration = pmax(1, priority_duration)
    )

  p7 <- ggplot(df_g7_plot, aes(x = label, y = duration, fill = vehicle_number)) +
    geom_col(width = 0.4, fill = "#dc2626", color = "#7f1d1d", linewidth = 0.8) +
    geom_text(aes(label = sprintf("%.0f seconds\n(Preemption Active)", as.numeric(duration))),
              vjust = -0.4, fontface = "bold", size = 4.2, color = "#991b1b") +
    scale_y_continuous(expand = expansion(mult = c(0, 0.3)), breaks = scales::pretty_breaks(n = 6)) +
    labs(
      title = sprintf("Graph 7: Emergency Vehicle Priority Preemption Metrics (Run #%d)", run_id),
      subtitle = "Ambulance priority clearance duration through green extension & safe yellow transition",
      x = "Emergency Dispatches",
      y = "Priority Preemption Duration (Seconds)",
      caption = "Source: emergency_events table"
    ) +
    theme_cbp()
} else {
  # Clean, authentic message card when no emergency vehicles were dispatched
  p7 <- ggplot() +
    annotate("rect", xmin = 0.5, xmax = 4.5, ymin = 0.5, ymax = 3.5,
             fill = "#fef2f2", color = "#fca5a5", linewidth = 1.2) +
    annotate("text", x = 2.5, y = 2.4, label = "No Emergency Priority Events Recorded in This Session",
             size = 5.5, fontface = "bold", color = "#991b1b") +
    annotate("text", x = 2.5, y = 1.6,
             label = sprintf("Simulation Run #%d operated under standard traffic timing without ambulance preemption.\nClick 'Dispatch Ambulance (Priority)' in the Java GUI to trigger emergency priority.", run_id),
             size = 4.0, color = "#475569", hjust = 0.5) +
    xlim(0, 5) + ylim(0, 4) +
    theme_void() +
    labs(title = sprintf("Graph 7: Emergency Vehicle Priority Analysis (Run #%d)", run_id))
}

ggsave(file.path(output_dir, "graph7_emergency_analysis.png"), plot = p7, width = 8, height = 5, dpi = 150)

# =============================================================================
# Graph 8: Fixed-Time vs Density-Based Strategy Comparison
# =============================================================================
cat("Generating Graph 8: Strategy Comparison...\n")
query_g8 <- "
  SELECT s.selected_strategy,
         COUNT(DISTINCT s.run_id) AS total_runs,
         ROUND(AVG(snap.waiting_vehicle_count), 1) AS avg_waiting,
         ROUND(MAX(snap.vehicle_throughput), 1) AS avg_throughput,
         ROUND(AVG(snap.allocated_green_seconds), 1) AS avg_green_allocated
  FROM simulation_runs s
  JOIN traffic_snapshots snap ON s.run_id = snap.run_id
  GROUP BY s.selected_strategy;
"

df_g8 <- dbGetQuery(con, query_g8)

if (nrow(df_g8) >= 1) {
  df_g8$avg_waiting <- as.numeric(df_g8$avg_waiting)
  df_g8$avg_throughput <- as.numeric(df_g8$avg_throughput)
  df_g8$avg_green_allocated <- as.numeric(df_g8$avg_green_allocated)

  # Reshape for multi-metric comparison bar plot
  df_g8_long <- df_g8 %>%
    select(selected_strategy, avg_waiting, avg_throughput, avg_green_allocated) %>%
    pivot_longer(cols = c(avg_waiting, avg_throughput, avg_green_allocated),
                 names_to = "metric", values_to = "value") %>%
    mutate(
      metric_label = case_when(
        metric == "avg_waiting" ~ "Avg Waiting Queue (veh)",
        metric == "avg_throughput" ~ "Cumulative Throughput (veh)",
        metric == "avg_green_allocated" ~ "Avg Green Time (s)",
        TRUE ~ metric
      ),
      short_strategy = case_when(
        grepl("Density", selected_strategy, ignore.case = TRUE) ~ "Density-Based Dynamic",
        grepl("Fixed", selected_strategy, ignore.case = TRUE) ~ "Fixed Timing",
        TRUE ~ selected_strategy
      )
    )

  p8 <- ggplot(df_g8_long, aes(x = metric_label, y = value, fill = short_strategy)) +
    geom_col(position = position_dodge(width = 0.75), width = 0.65, color = "#1e293b", linewidth = 0.5) +
    geom_text(aes(label = sprintf("%.1f", value)),
              position = position_dodge(width = 0.75), vjust = -0.4,
              fontface = "bold", size = 3.8, color = "#0f172a") +
    scale_fill_manual(values = c("Density-Based Dynamic" = "#2563eb", "Fixed Timing" = "#64748b"), name = "Strategy:") +
    scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = scales::pretty_breaks(n = 6)) +
    labs(
      title = "Graph 8: Strategy Performance Comparison (Fixed-Time vs Density-Based)",
      subtitle = "Empirical performance metrics across simulation sessions stored in MySQL",
      x = "Performance Metric",
      y = "Metric Value",
      caption = "Source: simulation_runs and traffic_snapshots tables"
    ) +
    theme_cbp()
} else {
  p8 <- ggplot() +
    annotate("text", x = 1, y = 1, label = "Comparing strategies requires at least one completed simulation run.\nData will update automatically as simulation runs.",
             size = 4.8, fontface = "italic", color = "#64748b") +
    theme_void() +
    labs(title = "Graph 8: Strategy Comparison (Awaiting Multi-Run Data)")
}

ggsave(file.path(output_dir, "graph8_strategy_comparison.png"), plot = p8, width = 8.5, height = 5, dpi = 150)

# Cleanly disconnect from database
if (exists("con") && DBI::dbIsValid(con)) {
  DBI::dbDisconnect(con)
}

cat(sprintf("=== Successfully generated all 8 graphs in %s ===\n", output_dir))
