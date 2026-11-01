# =============================================================================
# Intelligent Traffic Management System — R Shiny Live Interactive Dashboard
# Real-Time Visual Analytics Powered by MySQL traffic_management_db & R Shiny
# =============================================================================

suppressPackageStartupMessages({
  library(shiny)
  library(bslib)
  library(DBI)
  library(RMariaDB)
  library(ggplot2)
  library(dplyr)
  library(tidyr)
  library(scales)
})

# -----------------------------------------------------------------------------
# Database Configuration Helper
# -----------------------------------------------------------------------------
get_db_config <- function() {
  cfg <- list(
    host = "127.0.0.1",
    port = 3306,
    dbname = "traffic_management_db",
    user = "root",
    password = ""
  )
  
  prop_candidates <- c("database.properties", "../database.properties")
  for (pf in prop_candidates) {
    if (file.exists(pf)) {
      lines <- readLines(pf, warn = FALSE)
      for (line in lines) {
        line <- trimws(line)
        if (nchar(line) > 0 && !startsWith(line, "#")) {
          parts <- strsplit(line, "=", fixed = TRUE)[[1]]
          if (length(parts) >= 2) {
            key <- trimws(parts[1])
            val <- trimws(paste(parts[-1], collapse = "="))
            if (key == "db.user") cfg$user <- val
            if (key == "db.password") cfg$password <- val
          }
        }
      }
      break
    }
  }
  return(cfg)
}

safe_db_query <- function(sql, default = data.frame()) {
  cfg <- get_db_config()
  con <- tryCatch({
    dbConnect(
      RMariaDB::MariaDB(),
      dbname = cfg$dbname,
      user = cfg$user,
      password = cfg$password,
      host = cfg$host,
      port = cfg$port
    )
  }, error = function(e) {
    return(NULL)
  })
  
  if (is.null(con)) return(default)
  on.exit(dbDisconnect(con), add = TRUE)
  
  res <- tryCatch({
    dbGetQuery(con, sql)
  }, error = function(e) {
    default
  })
  return(res)
}

# Theme for ggplot charts
theme_shiny_cbp <- function() {
  theme_minimal(base_family = "sans") +
    theme(
      plot.title = element_text(face = "bold", size = 13, color = "#0f172a", hjust = 0.5, margin = margin(b = 6)),
      plot.subtitle = element_text(size = 9.5, color = "#475569", hjust = 0.5, margin = margin(b = 10)),
      plot.caption = element_text(size = 8, color = "#94a3b8", hjust = 1, margin = margin(t = 6)),
      axis.title = element_text(face = "bold", size = 10, color = "#1e293b"),
      axis.text = element_text(size = 9, color = "#334155"),
      panel.grid.major = element_line(color = "#e2e8f0", linewidth = 0.35),
      panel.grid.minor = element_blank(),
      plot.background = element_rect(fill = "#ffffff", color = NA),
      panel.background = element_rect(fill = "#f8fafc", color = "#cbd5e1", linewidth = 0.5),
      legend.position = "top",
      legend.title = element_text(face = "bold", size = 9),
      legend.text = element_text(size = 8.5)
    )
}

road_colors <- c("North" = "#2563eb", "South" = "#10b981", "East" = "#f59e0b", "West" = "#ef4444")
phase_colors <- c("NORTH_SOUTH" = "#0ea5e9", "EAST_WEST" = "#f97316")

# -----------------------------------------------------------------------------
# User Interface (UI)
# -----------------------------------------------------------------------------
ui <- page_sidebar(
  title = "🚦 Traffic Management System — Live R Shiny Analytics",
  theme = bs_theme(
    version = 5,
    bootswatch = "flatly",
    primary = "#2563eb",
    base_font = font_google("Inter")
  ),
  
  sidebar = sidebar(
    title = "Live Control Center",
    width = 280,
    
    tags$div(
      style = "padding: 8px 12px; background: #f1f5f9; border-radius: 8px; margin-bottom: 15px; border-left: 4px solid #10b981;",
      tags$b("Status: "), tags$span(style = "color: #10b981; font-weight: bold;", "● Connected to MySQL"),
      tags$br(),
      tags$small(textOutput("last_updated_time", inline = TRUE))
    ),
    
    selectInput(
      "selected_run",
      "Simulation Session (Run ID):",
      choices = c("Latest Active Session" = "latest"),
      selected = "latest"
    ),
    
    checkboxInput("auto_track_latest", "Auto-track latest active run", value = TRUE),
    
    sliderInput(
      "refresh_sec",
      "Live Refresh Frequency:",
      min = 1,
      max = 10,
      value = 2,
      step = 1,
      post = "s"
    ),
    
    checkboxInput("live_polling", "Enable Real-Time Polling", value = TRUE),
    
    actionButton(
      "btn_manual_refresh",
      "🔄 Sync Data Now",
      class = "btn-primary w-100 mb-2"
    ),
    
    tags$hr(),
    tags$p(
      style = "font-size: 11px; color: #64748b;",
      "This dashboard queries ", tags$code("traffic_management_db"),
      " live via RMariaDB. As vehicles are added, stopped, or cleared in Java Swing, charts re-render automatically."
    )
  ),
  
  # Top Metric KPI Value Boxes
  layout_column_wrap(
    width = "180px",
    fill = FALSE,
    value_box(
      title = "Total Vehicles",
      value = textOutput("kpi_total_vehicles"),
      showcase = bsicons::bs_icon("car-front"),
      theme = "primary"
    ),
    value_box(
      title = "Currently Waiting",
      value = textOutput("kpi_waiting_vehicles"),
      showcase = bsicons::bs_icon("hourglass-split"),
      theme = "warning"
    ),
    value_box(
      title = "Throughput (Exited)",
      value = textOutput("kpi_exited_vehicles"),
      showcase = bsicons::bs_icon("check2-circle"),
      theme = "success"
    ),
    value_box(
      title = "Avg Waiting Time",
      value = textOutput("kpi_avg_wait"),
      showcase = bsicons::bs_icon("clock-history"),
      theme = "info"
    ),
    value_box(
      title = "Ambulance Preemptions",
      value = textOutput("kpi_emergency_events"),
      showcase = bsicons::bs_icon("hospital"),
      theme = "danger"
    )
  ),
  
  # Main Analytical Tabs
  navset_card_pill(
    id = "main_tabs",
    
    nav_panel(
      "📊 Live Queues & Timing",
      layout_columns(
        col_widths = c(6, 6),
        card(
          card_header(tags$b("Graph 1: Waiting Vehicle Queue Distribution")),
          plotOutput("plot_graph1", height = "360px"),
          card_footer(tags$small("Real-time waiting queue counts on approach roads from MySQL vehicles table."))
        ),
        card(
          card_header(tags$b("Graph 2: Allocated Green Time by Traffic Phase")),
          plotOutput("plot_graph2", height = "360px"),
          card_footer(tags$small("Average and maximum green durations assigned by Java controller."))
        )
      )
    ),
    
    nav_panel(
      "📈 Corridors Timeline",
      layout_columns(
        col_widths = c(6, 6),
        card(
          card_header(tags$b("Graph 3: Queue Length Timeline over Time")),
          plotOutput("plot_graph3", height = "360px"),
          card_footer(tags$small("Second-by-second accumulation and clearance from traffic_snapshots."))
        ),
        card(
          card_header(tags$b("Graph 4: Signal Phase & State Transitions")),
          plotOutput("plot_graph4", height = "360px"),
          card_footer(tags$small("Sequence timeline of Green right-of-way, Yellow clearance, and Red."))
        )
      )
    ),
    
    nav_panel(
      "🚗 Throughput & Delay",
      layout_columns(
        col_widths = c(6, 6),
        card(
          card_header(tags$b("Graph 5: Vehicle Throughput by Road")),
          plotOutput("plot_graph5", height = "360px"),
          card_footer(tags$small("Completed vehicle exits crossing the intersection clearance zone."))
        ),
        card(
          card_header(tags$b("Graph 6: Average Waiting Time by Approach")),
          plotOutput("plot_graph6", height = "360px"),
          card_footer(tags$small("Average delay duration between arrival and intersection entry."))
        )
      )
    ),
    
    nav_panel(
      "🚑 Emergency & Comparison",
      layout_columns(
        col_widths = c(6, 6),
        card(
          card_header(tags$b("Graph 7: Emergency Vehicle Priority Metrics")),
          plotOutput("plot_graph7", height = "360px"),
          card_footer(tags$small("Ambulance priority clearance duration and phase extension telemetry."))
        ),
        card(
          card_header(tags$b("Graph 8: Fixed vs Density-Based Strategy Comparison")),
          plotOutput("plot_graph8", height = "360px"),
          card_footer(tags$small("Empirical performance comparison across simulation sessions."))
        )
      )
    ),
    
    nav_panel(
      "📋 Live Vehicle Registry",
      card(
        card_header(
          tags$div(
            style = "display: flex; justify-content: space-between; align-items: center;",
            tags$b("Individual Vehicle Registry & Telemetry"),
            tags$span(style = "font-size: 12px; color: #64748b;", "Each vehicle retains unique identity (V001...)")
          )
        ),
        tableOutput("table_vehicles"),
        card_footer(tags$small("Showing the most recent vehicles for the active simulation session."))
      )
    )
  )
)

# -----------------------------------------------------------------------------
# Server Logic
# -----------------------------------------------------------------------------
server <- function(input, output, session) {
  
  # Reactive polling timer
  autoInvalidate <- reactiveTimer(2000)
  
  observe({
    req(input$refresh_sec)
    ms <- max(1000, input$refresh_sec * 1000)
    autoInvalidate <<- reactiveTimer(ms)
  })
  
  # Populate run selector dynamically
  observe({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    runs_df <- safe_db_query(
      "SELECT run_id, selected_strategy, simulation_status, start_time 
       FROM simulation_runs ORDER BY run_id DESC LIMIT 15;"
    )
    
    if (nrow(runs_df) > 0) {
      choices <- as.character(runs_df$run_id)
      names(choices) <- sprintf("Run #%d (%s - %s)", runs_df$run_id, runs_df$selected_strategy, runs_df$simulation_status)
      choices <- c("Latest Active Session" = "latest", choices)
      
      current_sel <- isolate(input$selected_run)
      if (is.null(current_sel) || !current_sel %in% choices) {
        current_sel <- "latest"
      }
      updateSelectInput(session, "selected_run", choices = choices, selected = current_sel)
    }
  })
  
  # Resolve actual run_id
  current_run_id <- reactive({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    sel <- input$selected_run
    if (is.null(sel) || sel == "latest" || isTRUE(input$auto_track_latest)) {
      df <- safe_db_query("SELECT MAX(run_id) AS latest_id FROM simulation_runs;")
      if (nrow(df) > 0 && !is.na(df$latest_id[1])) {
        return(as.integer(df$latest_id[1]))
      }
      return(1L)
    }
    as.integer(sel)
  })
  
  # Status timestamp
  output$last_updated_time <- renderText({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    paste("Synced:", format(Sys.time(), "%H:%M:%S"))
  })
  
  # Fetch summary KPI metrics
  kpis_data <- reactive({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT 
        COUNT(v.vehicle_id) AS total_vehicles,
        SUM(CASE WHEN v.current_status = 'WAITING' THEN 1 ELSE 0 END) AS waiting_vehicles,
        SUM(CASE WHEN v.current_status = 'EXITED' THEN 1 ELSE 0 END) AS exited_vehicles,
        COALESCE(AVG(TIMESTAMPDIFF(SECOND, v.arrival_time, v.intersection_entry_time)), 0.0) AS avg_wait_seconds,
        SUM(CASE WHEN v.is_emergency = 1 THEN 1 ELSE 0 END) AS emergency_vehicles
      FROM vehicles v
      WHERE v.run_id = %d;
    ", r_id)
    
    df <- safe_db_query(sql, default = data.frame(
      total_vehicles = 0, waiting_vehicles = 0, exited_vehicles = 0, avg_wait_seconds = 0, emergency_vehicles = 0
    ))
    
    if (nrow(df) == 0 || is.na(df$total_vehicles[1])) {
      df <- data.frame(total_vehicles = 0, waiting_vehicles = 0, exited_vehicles = 0, avg_wait_seconds = 0, emergency_vehicles = 0)
    }
    df
  })
  
  output$kpi_total_vehicles <- renderText({ as.character(kpis_data()$total_vehicles[1]) })
  output$kpi_waiting_vehicles <- renderText({ as.character(kpis_data()$waiting_vehicles[1]) })
  output$kpi_exited_vehicles <- renderText({ as.character(kpis_data()$exited_vehicles[1]) })
  output$kpi_avg_wait <- renderText({ sprintf("%.1fs", as.numeric(kpis_data()$avg_wait_seconds[1])) })
  output$kpi_emergency_events <- renderText({ as.character(kpis_data()$emergency_vehicles[1]) })
  
  # ---------------------------------------------------------------------------
  # Graph 1: Live Waiting Vehicle Queue Distribution
  # ---------------------------------------------------------------------------
  output$plot_graph1 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT r.direction AS road_direction,
             COUNT(v.vehicle_id) AS waiting_count
      FROM roads r
      LEFT JOIN lanes l ON r.road_id = l.road_id
      LEFT JOIN vehicles v ON l.lane_id = v.lane_id
                           AND v.run_id = %d
                           AND v.current_status = 'WAITING'
      GROUP BY r.direction
      ORDER BY FIELD(r.direction, 'North', 'South', 'East', 'West');
    ", r_id)
    
    df <- safe_db_query(sql, default = data.frame(road_direction = c("North", "South", "East", "West"), waiting_count = 0))
    df$road_direction <- factor(df$road_direction, levels = c("North", "South", "East", "West"))
    df$waiting_count[is.na(df$waiting_count)] <- 0
    
    ggplot(df, aes(x = road_direction, y = waiting_count, fill = road_direction)) +
      geom_col(width = 0.55, color = "#1e293b", linewidth = 0.6) +
      geom_text(aes(label = sprintf("%.0f veh", as.numeric(waiting_count))),
                vjust = -0.5, fontface = "bold", size = 4.2, color = "#0f172a") +
      scale_fill_manual(values = road_colors, guide = "none") +
      scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = pretty_breaks(n = 6)) +
      labs(
        title = sprintf("Live Waiting Queue Distribution (Run #%d)", r_id),
        subtitle = paste("Updated automatically at", format(Sys.time(), "%H:%M:%S")),
        x = "Approach Road",
        y = "Waiting Vehicle Count",
        caption = "Source: vehicles table | Live R Shiny Stream"
      ) +
      theme_shiny_cbp()
  })
  
  # ---------------------------------------------------------------------------
  # Graph 2: Allocated Green Time by Traffic Phase
  # ---------------------------------------------------------------------------
  output$plot_graph2 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT active_phase,
             ROUND(AVG(allocated_green_seconds), 1) AS avg_green_sec,
             MAX(allocated_green_seconds) AS max_green_sec,
             COUNT(*) AS phase_count
      FROM signal_events
      WHERE run_id = %d AND signal_state = 'GREEN'
      GROUP BY active_phase;
    ", r_id)
    
    df <- safe_db_query(sql)
    if (nrow(df) == 0) {
      df <- data.frame(active_phase = c("NORTH_SOUTH", "EAST_WEST"), avg_green_sec = c(0, 0), phase_count = c(0, 0))
    }
    
    ggplot(df, aes(x = active_phase, y = avg_green_sec, fill = active_phase)) +
      geom_col(width = 0.5, color = "#1e293b", linewidth = 0.6) +
      geom_text(aes(label = sprintf("%.1f s\n(n=%.0f)", as.numeric(avg_green_sec), as.numeric(phase_count))),
                vjust = -0.3, fontface = "bold", size = 4.0, color = "#0f172a") +
      scale_fill_manual(values = phase_colors, guide = "none") +
      scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = pretty_breaks(n = 6)) +
      scale_x_discrete(labels = c("NORTH_SOUTH" = "North–South Phase", "EAST_WEST" = "East–West Phase")) +
      labs(
        title = sprintf("Allocated Green Duration by Phase (Run #%d)", r_id),
        subtitle = "Timing durations computed from traffic density by Java controller",
        x = "Traffic Signal Phase",
        y = "Average Green Time (Seconds)",
        caption = "Source: signal_events table"
      ) +
      theme_shiny_cbp()
  })
  
  # ---------------------------------------------------------------------------
  # Graph 3: Queue Length Timeline over Simulation Time
  # ---------------------------------------------------------------------------
  output$plot_graph3 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT simulation_timestamp, road_direction, waiting_vehicle_count, queue_length
      FROM traffic_snapshots
      WHERE run_id = %d
      ORDER BY simulation_timestamp ASC;
    ", r_id)
    
    df <- safe_db_query(sql)
    if (nrow(df) > 0) {
      df$simulation_timestamp <- as.POSIXct(df$simulation_timestamp)
      start_t <- min(df$simulation_timestamp)
      df$elapsed_sec <- as.numeric(difftime(df$simulation_timestamp, start_t, units = "secs"))
      
      ggplot(df, aes(x = elapsed_sec, y = waiting_vehicle_count, color = road_direction)) +
        geom_line(linewidth = 1.1, alpha = 0.85) +
        geom_point(size = 2.0, alpha = 0.9) +
        scale_color_manual(values = road_colors, name = "Road:") +
        scale_y_continuous(breaks = pretty_breaks(n = 6)) +
        labs(
          title = sprintf("Queue Length Dynamics over Simulation Time (Run #%d)", r_id),
          subtitle = "Second-by-second accumulation and queue clearance across approaches",
          x = "Elapsed Time (Seconds)",
          y = "Waiting Queue Size",
          caption = "Source: traffic_snapshots table"
        ) +
        theme_shiny_cbp()
    } else {
      ggplot() +
        annotate("text", x = 1, y = 1, label = "Let simulation advance in Java to record second-by-second timeline data.",
                 size = 4.5, fontface = "italic", color = "#64748b") +
        theme_void() +
        labs(title = sprintf("Queue Length Timeline (Run #%d - Awaiting Data)", r_id))
    }
  })
  
  # ---------------------------------------------------------------------------
  # Graph 4: Traffic Signal State Timeline
  # ---------------------------------------------------------------------------
  output$plot_graph4 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT active_phase, signal_state, event_timestamp
      FROM signal_events
      WHERE run_id = %d
      ORDER BY event_timestamp ASC;
    ", r_id)
    
    df <- safe_db_query(sql)
    if (nrow(df) >= 2) {
      df$event_timestamp <- as.POSIXct(df$event_timestamp)
      start_t <- min(df$event_timestamp)
      df$start_sec <- as.numeric(difftime(df$event_timestamp, start_t, units = "secs"))
      df$end_sec <- c(df$start_sec[-1], max(df$start_sec) + 3)
      
      signal_palette <- c("GREEN" = "#22c55e", "YELLOW" = "#eab308", "RED" = "#ef4444")
      
      ggplot(df, aes(ymin = as.numeric(factor(active_phase)) - 0.35,
                     ymax = as.numeric(factor(active_phase)) + 0.35,
                     xmin = start_sec, xmax = end_sec, fill = signal_state)) +
        geom_rect(color = "#1e293b", linewidth = 0.4) +
        scale_fill_manual(values = signal_palette, name = "Signal State:") +
        scale_y_continuous(breaks = 1:length(unique(df$active_phase)), labels = levels(factor(df$active_phase))) +
        labs(
          title = sprintf("Signal Phase & State Sequence Timeline (Run #%d)", r_id),
          subtitle = "Green Right-of-Way, 3s Yellow Clearance, and Red Transitions",
          x = "Elapsed Time (Seconds)",
          y = "Active Corridor Phase",
          caption = "Source: signal_events table"
        ) +
        theme_shiny_cbp()
    } else {
      ggplot() +
        annotate("text", x = 1, y = 1, label = "Awaiting signal phase transitions. Allow simulation to run in Java.",
                 size = 4.5, fontface = "italic", color = "#64748b") +
        theme_void() +
        labs(title = sprintf("Signal State Timeline (Run #%d - Awaiting Data)", r_id))
    }
  })
  
  # ---------------------------------------------------------------------------
  # Graph 5: Vehicle Throughput by Road
  # ---------------------------------------------------------------------------
  output$plot_graph5 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT r.direction AS road_direction,
             COUNT(v.vehicle_id) AS throughput_count
      FROM roads r
      LEFT JOIN lanes l ON r.road_id = l.road_id
      LEFT JOIN vehicles v ON l.lane_id = v.lane_id
                           AND v.run_id = %d
                           AND v.current_status = 'EXITED'
      GROUP BY r.direction
      ORDER BY FIELD(r.direction, 'North', 'South', 'East', 'West');
    ", r_id)
    
    df <- safe_db_query(sql, default = data.frame(road_direction = c("North", "South", "East", "West"), throughput_count = 0))
    df$road_direction <- factor(df$road_direction, levels = c("North", "South", "East", "West"))
    df$throughput_count[is.na(df$throughput_count)] <- 0
    
    ggplot(df, aes(x = road_direction, y = throughput_count, fill = road_direction)) +
      geom_col(width = 0.55, color = "#1e293b", linewidth = 0.6) +
      geom_text(aes(label = sprintf("%.0f exited", as.numeric(throughput_count))),
                vjust = -0.5, fontface = "bold", size = 4.2, color = "#0f172a") +
      scale_fill_manual(values = road_colors, guide = "none") +
      scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = pretty_breaks(n = 6)) +
      labs(
        title = sprintf("Total Vehicle Throughput by Approach Road (Run #%d)", r_id),
        subtitle = "Vehicles that cleared and exited the intersection box",
        x = "Approach Road",
        y = "Throughput (Completed Exits)",
        caption = "Source: vehicles table (current_status = 'EXITED')"
      ) +
      theme_shiny_cbp()
  })
  
  # ---------------------------------------------------------------------------
  # Graph 6: Average Waiting Time by Approach
  # ---------------------------------------------------------------------------
  output$plot_graph6 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
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
    ", r_id)
    
    df <- safe_db_query(sql, default = data.frame(road_direction = c("North", "South", "East", "West"), avg_wait_seconds = 0, sampled_vehicles = 0))
    df$road_direction <- factor(df$road_direction, levels = c("North", "South", "East", "West"))
    df$avg_wait_seconds[is.na(df$avg_wait_seconds)] <- 0
    
    ggplot(df, aes(x = road_direction, y = avg_wait_seconds, fill = road_direction)) +
      geom_col(width = 0.55, color = "#1e293b", linewidth = 0.6) +
      geom_text(aes(label = sprintf("%.1f s\n(n=%.0f)", as.numeric(avg_wait_seconds), as.numeric(sampled_vehicles))),
                vjust = -0.3, fontface = "bold", size = 4.0, color = "#0f172a") +
      scale_fill_manual(values = road_colors, guide = "none") +
      scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = pretty_breaks(n = 6)) +
      labs(
        title = sprintf("Average Vehicle Waiting Delay by Approach (Run #%d)", r_id),
        subtitle = "Duration between queue arrival and intersection entry",
        x = "Approach Road",
        y = "Average Delay (Seconds)",
        caption = "Source: vehicles table (arrival_time to entry_time interval)"
      ) +
      theme_shiny_cbp()
  })
  
  # ---------------------------------------------------------------------------
  # Graph 7: Emergency Vehicle Priority Analysis
  # ---------------------------------------------------------------------------
  output$plot_graph7 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT e.emergency_event_id, COALESCE(v.vehicle_number, 'EMG-01') AS vehicle_number,
             e.priority_duration, e.affected_phase
      FROM emergency_events e
      LEFT JOIN vehicles v ON e.vehicle_id = v.vehicle_id
      WHERE e.run_id = %d;
    ", r_id)
    
    df <- safe_db_query(sql)
    if (nrow(df) > 0) {
      df$duration <- pmax(1, as.numeric(df$priority_duration))
      df$label <- sprintf("%s\n(Phase: %s)", df$vehicle_number, df$affected_phase)
      
      ggplot(df, aes(x = label, y = duration, fill = vehicle_number)) +
        geom_col(width = 0.4, fill = "#dc2626", color = "#7f1d1d", linewidth = 0.8) +
        geom_text(aes(label = sprintf("%.0f s\n(Preemption Active)", duration)),
                  vjust = -0.4, fontface = "bold", size = 4.2, color = "#991b1b") +
        scale_y_continuous(expand = expansion(mult = c(0, 0.3)), breaks = pretty_breaks(n = 6)) +
        labs(
          title = sprintf("Emergency Vehicle Priority Preemption (Run #%d)", r_id),
          subtitle = "Ambulance priority clearance duration via green extension & yellow transition",
          x = "Emergency Dispatches",
          y = "Preemption Duration (Seconds)",
          caption = "Source: emergency_events table"
        ) +
        theme_shiny_cbp()
    } else {
      ggplot() +
        annotate("rect", xmin = 0.5, xmax = 4.5, ymin = 0.5, ymax = 3.5,
                 fill = "#fef2f2", color = "#fca5a5", linewidth = 1.0) +
        annotate("text", x = 2.5, y = 2.2, label = "No Emergency Priority Events Recorded",
                 size = 5.0, fontface = "bold", color = "#991b1b") +
        annotate("text", x = 2.5, y = 1.6,
                 label = sprintf("Session #%d is operating under normal traffic timing.\nClick 'Dispatch Ambulance (Priority)' in Java to test priority.", r_id),
                 size = 3.8, color = "#475569", hjust = 0.5) +
        xlim(0, 5) + ylim(0, 4) +
        theme_void() +
        labs(title = sprintf("Emergency Priority Analysis (Run #%d - Standard Mode)", r_id))
    }
  })
  
  # ---------------------------------------------------------------------------
  # Graph 8: Fixed-Time vs Density-Based Strategy Comparison
  # ---------------------------------------------------------------------------
  output$plot_graph8 <- renderPlot({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    sql <- "
      SELECT s.selected_strategy,
             ROUND(AVG(snap.waiting_vehicle_count), 1) AS avg_waiting,
             ROUND(MAX(snap.vehicle_throughput), 1) AS avg_throughput,
             ROUND(AVG(snap.allocated_green_seconds), 1) AS avg_green_allocated
      FROM simulation_runs s
      JOIN traffic_snapshots snap ON s.run_id = snap.run_id
      GROUP BY s.selected_strategy;
    "
    
    df <- safe_db_query(sql)
    if (nrow(df) >= 1) {
      df$avg_waiting <- as.numeric(df$avg_waiting)
      df$avg_throughput <- as.numeric(df$avg_throughput)
      df$avg_green_allocated <- as.numeric(df$avg_green_allocated)
      
      df_long <- df %>%
        select(selected_strategy, avg_waiting, avg_throughput, avg_green_allocated) %>%
        pivot_longer(cols = c(avg_waiting, avg_throughput, avg_green_allocated), names_to = "metric", values_to = "value") %>%
        mutate(
          metric_label = case_when(
            metric == "avg_waiting" ~ "Avg Waiting Queue",
            metric == "avg_throughput" ~ "Cumulative Throughput",
            metric == "avg_green_allocated" ~ "Avg Green Allocated (s)",
            TRUE ~ metric
          ),
          short_strategy = case_when(
            grepl("Density", selected_strategy, ignore.case = TRUE) ~ "Density-Based Dynamic",
            grepl("Fixed", selected_strategy, ignore.case = TRUE) ~ "Fixed Timing",
            TRUE ~ selected_strategy
          )
        )
      
      ggplot(df_long, aes(x = metric_label, y = value, fill = short_strategy)) +
        geom_col(position = position_dodge(width = 0.75), width = 0.65, color = "#1e293b", linewidth = 0.5) +
        geom_text(aes(label = sprintf("%.1f", value)),
                  position = position_dodge(width = 0.75), vjust = -0.4,
                  fontface = "bold", size = 3.8, color = "#0f172a") +
        scale_fill_manual(values = c("Density-Based Dynamic" = "#2563eb", "Fixed Timing" = "#64748b"), name = "Strategy:") +
        scale_y_continuous(expand = expansion(mult = c(0, 0.25)), breaks = pretty_breaks(n = 6)) +
        labs(
          title = "Strategy Comparison: Fixed Timing vs Density-Based",
          subtitle = "Empirical performance across multiple sessions stored in MySQL",
          x = "Performance Metric",
          y = "Metric Value",
          caption = "Source: simulation_runs & traffic_snapshots tables"
        ) +
        theme_shiny_cbp()
    } else {
      ggplot() +
        annotate("text", x = 1, y = 1, label = "Multi-run strategy comparison updates automatically as sessions complete in MySQL.",
                 size = 4.5, fontface = "italic", color = "#64748b") +
        theme_void() +
        labs(title = "Strategy Comparison (Awaiting Multi-Run Data)")
    }
  })
  
  # ---------------------------------------------------------------------------
  # Live Vehicle Registry Table
  # ---------------------------------------------------------------------------
  output$table_vehicles <- renderTable({
    input$btn_manual_refresh
    if (isTRUE(input$live_polling)) autoInvalidate()
    
    r_id <- current_run_id()
    sql <- sprintf("
      SELECT v.vehicle_number AS 'Vehicle Number',
             v.vehicle_type AS 'Type',
             COALESCE(r.direction, 'Unknown') AS 'Road',
             COALESCE(l.lane_name, 'N/A') AS 'Lane',
             v.current_status AS 'Status',
             CONCAT(ROUND(v.vehicle_speed), ' px/s') AS 'Speed',
             DATE_FORMAT(v.arrival_time, '%%H:%%i:%%s') AS 'Arrival',
             COALESCE(DATE_FORMAT(v.intersection_entry_time, '%%H:%%i:%%s'), '—') AS 'Entry',
             COALESCE(DATE_FORMAT(v.intersection_exit_time, '%%H:%%i:%%s'), '—') AS 'Exit',
             CASE WHEN v.is_emergency = 1 THEN 'YES (Ambulance)' ELSE 'No' END AS 'Priority?'
      FROM vehicles v
      LEFT JOIN lanes l ON v.lane_id = l.lane_id
      LEFT JOIN roads r ON l.road_id = r.road_id
      WHERE v.run_id = %d
      ORDER BY v.vehicle_id DESC
      LIMIT 25;
    ", r_id)
    
    safe_db_query(sql, default = data.frame(Notice = "No vehicle records found for this session."))
  }, striped = TRUE, hover = TRUE, bordered = TRUE, spacing = "s")
  
}

# Run the application
shinyApp(ui = ui, server = server)
