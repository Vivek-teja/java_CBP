package gui;

import analytics.RAnalyticsRunner;
import analytics.TrafficDataLogger;
import database.DatabaseConnection;
import database.SimulationDAO;
import database.TrafficAnalyticsDAO;
import database.VehicleDAO;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Integrated Traffic Analytics Dashboard for the Java CBP.
 * Connects directly to MySQL for vehicle records and telemetry,
 * and renders R-generated ggplot2 visual graphs asynchronously.
 *
 * Provides both an all-in-one Grid View (showing all 8 graphs simultaneously)
 * and a high-resolution Single Graph Inspector for deep analysis.
 */
public class TrafficAnalyticsDashboard extends JFrame implements RAnalyticsRunner.RGraphListener {

    private static final String[] GRAPH_TITLES = {
        "Graph 1: Vehicle Distribution by Road",
        "Graph 2: Allocated Green Time by Phase",
        "Graph 3: Waiting Queue Length Timeline",
        "Graph 4: Traffic Signal State Timeline",
        "Graph 5: Vehicle Throughput by Road",
        "Graph 6: Average Waiting Time by Approach",
        "Graph 7: Emergency Vehicle Priority Analysis",
        "Graph 8: Strategy Comparison (Fixed vs Density)"
    };

    private static final String[] GRAPH_FILES = {
        "graph1_vehicle_distribution.png",
        "graph2_green_time_allocation.png",
        "graph3_queue_length_timeline.png",
        "graph4_signal_state_timeline.png",
        "graph5_vehicle_throughput.png",
        "graph6_waiting_time.png",
        "graph7_emergency_analysis.png",
        "graph8_strategy_comparison.png"
    };

    private static final String[] GRAPH_DESCRIPTIONS = {
        "Waiting vehicle count on North, South, East, West approaches retrieved directly from MySQL vehicles table.",
        "Average and maximum green-light seconds assigned by Java controller across signal phases stored in signal_events.",
        "Second-by-second accumulation and dissipation of waiting queues recorded in traffic_snapshots table.",
        "Timeline sequence of green right-of-way, yellow clearance transitions, and red intervals.",
        "Cumulative count of vehicles that completed transit through the intersection and exited safely.",
        "Average duration (in seconds) between queue arrival and intersection box entry per approach road.",
        "Preemption duration and phase extension metrics when emergency ambulance priority is triggered.",
        "Comparative evaluation of fixed-time vs density-based dynamic timing strategies across simulation sessions."
    };

    private final SimulationDAO simulationDAO = new SimulationDAO();
    private final VehicleDAO vehicleDAO = new VehicleDAO();
    private final TrafficAnalyticsDAO analyticsDAO = new TrafficAnalyticsDAO();

    private JComboBox<SimulationDAO.SimulationRunRecord> runCombo;
    private JLabel totalVehiclesCard;
    private JLabel waitingVehiclesCard;
    private JLabel exitedVehiclesCard;
    private JLabel avgWaitCard;
    private JLabel emergencyCard;
    private JLabel statusBanner;
    private JProgressBar progressBar;

    // View switching between All 8 Graphs Grid and Inspector
    private CardLayout graphCardLayout;
    private JPanel graphViewContainer;
    private final JLabel[] gridImageLabels = new JLabel[8];
    private JLabel inspectorImageLabel;
    private JLabel inspectorDescLabel;
    private JComboBox<String> inspectorCombo;
    private int selectedInspectorIndex = 0;
    private JButton gridViewBtn;
    private JButton inspectorViewBtn;

    private JTable vehicleTable;
    private DefaultTableModel vehicleTableModel;
    private JTextField searchField;

    private JTable historyTable;
    private DefaultTableModel historyTableModel;

    private int activeRunId = 1;

    public TrafficAnalyticsDashboard() {
        super("Traffic Management System — Integrated MySQL & R Analytics Dashboard");
        this.activeRunId = TrafficDataLogger.getInstance().getCurrentRunId();
        if (this.activeRunId <= 0) this.activeRunId = 1;

        initComponents();

        setSize(1220, 880);
        setMinimumSize(new Dimension(1000, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Initial data load and initial R graph generation
        refreshData();
        triggerRGeneration();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(248, 250, 252));

        // 1. Header & Top Control Bar
        JPanel topContainer = new JPanel();
        topContainer.setLayout(new BoxLayout(topContainer, BoxLayout.Y_AXIS));
        topContainer.setBackground(new Color(30, 41, 59));

        // Title banner
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setOpaque(false);
        titlePanel.setBorder(BorderFactory.createEmptyBorder(10, 16, 8, 16));

        JLabel titleLabel = new JLabel("TRAFFIC MANAGEMENT ANALYTICS DASHBOARD");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLabel.setForeground(Color.WHITE);

        statusBanner = new JLabel("MySQL: Connected | R Engine: Ready");
        statusBanner.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusBanner.setForeground(new Color(148, 163, 184));

        titlePanel.add(titleLabel, BorderLayout.WEST);
        titlePanel.add(statusBanner, BorderLayout.EAST);

        // Toolbar
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        toolbar.setBackground(new Color(15, 23, 42));
        toolbar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(51, 65, 85)));

        JLabel runLabel = new JLabel("Simulation Session:");
        runLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        runLabel.setForeground(new Color(226, 232, 240));

        runCombo = new JComboBox<>();
        runCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        runCombo.setPreferredSize(new Dimension(320, 28));
        runCombo.addActionListener(e -> onRunSelected());

        JButton refreshBtn = createStyledButton("🔄 Refresh Dashboard", new Color(37, 99, 235), Color.WHITE);
        refreshBtn.addActionListener(e -> {
            refreshData();
            triggerRGeneration();
        });

        JButton exportBtn = createStyledButton("💾 Export Database Data", new Color(16, 185, 129), Color.WHITE);
        exportBtn.addActionListener(e -> exportDataToCSV());

        JButton openRprojBtn = createStyledButton("📊 Open RStudio Project", new Color(139, 92, 246), Color.WHITE);
        openRprojBtn.addActionListener(e -> openRStudioProject());

        JButton openFolderBtn = createStyledButton("📁 Open Graph Folder", new Color(71, 85, 105), Color.WHITE);
        openFolderBtn.addActionListener(e -> openOutputFolder());

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(110, 20));

        toolbar.add(runLabel);
        toolbar.add(runCombo);
        toolbar.add(refreshBtn);
        toolbar.add(exportBtn);
        toolbar.add(openRprojBtn);
        toolbar.add(openFolderBtn);
        toolbar.add(progressBar);

        topContainer.add(titlePanel);
        topContainer.add(toolbar);

        // 2. Metric KPI Cards Row
        JPanel kpiPanel = new JPanel(new GridLayout(1, 5, 10, 0));
        kpiPanel.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        kpiPanel.setBackground(new Color(241, 245, 249));

        totalVehiclesCard = new JLabel("0", SwingConstants.CENTER);
        waitingVehiclesCard = new JLabel("0", SwingConstants.CENTER);
        exitedVehiclesCard = new JLabel("0", SwingConstants.CENTER);
        avgWaitCard = new JLabel("0.0s", SwingConstants.CENTER);
        emergencyCard = new JLabel("0", SwingConstants.CENTER);

        kpiPanel.add(createKpiCard("TOTAL VEHICLES", totalVehiclesCard, new Color(37, 99, 235)));
        kpiPanel.add(createKpiCard("CURRENT WAITING", waitingVehiclesCard, new Color(234, 88, 12)));
        kpiPanel.add(createKpiCard("THROUGHPUT (EXITED)", exitedVehiclesCard, new Color(16, 185, 129)));
        kpiPanel.add(createKpiCard("AVG WAITING TIME", avgWaitCard, new Color(139, 92, 246)));
        kpiPanel.add(createKpiCard("EMERGENCY PREEMPTIONS", emergencyCard, new Color(220, 38, 38)));

        topContainer.add(kpiPanel);
        add(topContainer, BorderLayout.NORTH);

        // 3. Main Tabbed Pane (Graphs, Vehicles Table, Simulation History)
        JTabbedPane mainTabs = new JTabbedPane();
        mainTabs.setFont(new Font("SansSerif", Font.BOLD, 12));

        // Tab 1: Graphical Visualizations (R Engine)
        JPanel graphsPanel = buildGraphsTab();
        mainTabs.addTab("📊 Graphical Visualizations (R Engine)", graphsPanel);

        // Tab 2: Vehicle Records Table
        JPanel vehiclesPanel = buildVehiclesTableTab();
        mainTabs.addTab("🚗 Individual Vehicle Registry", vehiclesPanel);

        // Tab 3: Simulation Session History
        JPanel historyPanel = buildHistoryTab();
        mainTabs.addTab("📜 Simulation Session History", historyPanel);

        add(mainTabs, BorderLayout.CENTER);
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JLabel titleLbl = new JLabel(title, SwingConstants.CENTER);
        titleLbl.setFont(new Font("SansSerif", Font.BOLD, 10));
        titleLbl.setForeground(new Color(100, 116, 139));

        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        valueLabel.setForeground(accentColor);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    /**
     * Builds the Graphs tab containing both the All 8 Graphs Grid View and the Single Graph Inspector.
     */
    private JPanel buildGraphsTab() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(241, 245, 249));

        // Sub-toolbar for switching between Grid View and Inspector View
        JPanel viewBar = new JPanel(new BorderLayout());
        viewBar.setBackground(Color.WHITE);
        viewBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)
        ));

        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftButtons.setOpaque(false);

        gridViewBtn = createStyledButton("⊞ View All 8 Graphs (Grid View)", new Color(37, 99, 235), Color.WHITE);
        inspectorViewBtn = createStyledButton("🔍 Single Graph Inspector", new Color(100, 116, 139), Color.WHITE);
        JButton regenBtn = createStyledButton("🔄 Regenerate All 8 Graphs", new Color(16, 185, 129), Color.WHITE);

        gridViewBtn.addActionListener(e -> {
            graphCardLayout.show(graphViewContainer, "GRID");
            gridViewBtn.setBackground(new Color(37, 99, 235));
            inspectorViewBtn.setBackground(new Color(100, 116, 139));
        });

        inspectorViewBtn.addActionListener(e -> {
            graphCardLayout.show(graphViewContainer, "INSPECTOR");
            inspectorViewBtn.setBackground(new Color(37, 99, 235));
            gridViewBtn.setBackground(new Color(100, 116, 139));
            updateInspectorGraph();
        });

        regenBtn.addActionListener(e -> triggerRGeneration());

        leftButtons.add(gridViewBtn);
        leftButtons.add(inspectorViewBtn);
        leftButtons.add(regenBtn);

        JLabel infoLabel = new JLabel("All 8 graphs are generated directly by R & ggplot2 from MySQL data");
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        infoLabel.setForeground(new Color(100, 116, 139));

        viewBar.add(leftButtons, BorderLayout.WEST);
        viewBar.add(infoLabel, BorderLayout.EAST);

        mainPanel.add(viewBar, BorderLayout.NORTH);

        // Container with CardLayout: GRID vs INSPECTOR
        graphCardLayout = new CardLayout();
        graphViewContainer = new JPanel(graphCardLayout);

        JPanel gridPanel = buildGridOverviewPanel();
        JPanel inspectorPanel = buildInspectorPanel();

        graphViewContainer.add(gridPanel, "GRID");
        graphViewContainer.add(inspectorPanel, "INSPECTOR");

        mainPanel.add(graphViewContainer, BorderLayout.CENTER);
        return mainPanel;
    }

    /**
     * Builds the primary scrollable 2-column grid displaying ALL 8 graphs simultaneously.
     */
    private JPanel buildGridOverviewPanel() {
        JPanel grid = new JPanel(new GridLayout(4, 2, 14, 14));
        grid.setBackground(new Color(241, 245, 249));
        grid.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        for (int i = 0; i < 8; i++) {
            final int idx = i;
            JPanel card = new JPanel(new BorderLayout(0, 6));
            card.setBackground(Color.WHITE);
            card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));

            // Card Header: Title + "Zoom to Full View" button
            JPanel cardHeader = new JPanel(new BorderLayout());
            cardHeader.setOpaque(false);

            JLabel titleLbl = new JLabel(GRAPH_TITLES[i]);
            titleLbl.setFont(new Font("SansSerif", Font.BOLD, 12));
            titleLbl.setForeground(new Color(15, 23, 42));

            JButton zoomBtn = new JButton("🔍 Zoom to Full View");
            zoomBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
            zoomBtn.setForeground(new Color(37, 99, 235));
            zoomBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            zoomBtn.setFocusPainted(false);
            zoomBtn.addActionListener(e -> {
                selectedInspectorIndex = idx;
                inspectorCombo.setSelectedIndex(idx);
                graphCardLayout.show(graphViewContainer, "INSPECTOR");
                inspectorViewBtn.setBackground(new Color(37, 99, 235));
                gridViewBtn.setBackground(new Color(100, 116, 139));
                updateInspectorGraph();
            });

            cardHeader.add(titleLbl, BorderLayout.WEST);
            cardHeader.add(zoomBtn, BorderLayout.EAST);

            // Card Image Container
            gridImageLabels[i] = new JLabel("Loading visualization...", SwingConstants.CENTER);
            gridImageLabels[i].setPreferredSize(new Dimension(520, 325));
            gridImageLabels[i].setFont(new Font("SansSerif", Font.ITALIC, 12));
            gridImageLabels[i].setForeground(new Color(100, 116, 139));

            // Card Description Footer
            JLabel descLbl = new JLabel(GRAPH_DESCRIPTIONS[i]);
            descLbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
            descLbl.setForeground(new Color(100, 116, 139));

            card.add(cardHeader, BorderLayout.NORTH);
            card.add(gridImageLabels[i], BorderLayout.CENTER);
            card.add(descLbl, BorderLayout.SOUTH);

            grid.add(card);
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(null);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(scroll, BorderLayout.CENTER);
        return wrapper;
    }

    /**
     * Builds the Single Graph Inspector for deep, high-resolution viewing of any selected chart.
     */
    private JPanel buildInspectorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        // Top Navigation Bar
        JPanel navBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        navBar.setBackground(new Color(248, 250, 252));
        navBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JButton backBtn = new JButton("⬅ Back to All 8 Graphs Grid");
        backBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        backBtn.addActionListener(e -> {
            graphCardLayout.show(graphViewContainer, "GRID");
            gridViewBtn.setBackground(new Color(37, 99, 235));
            inspectorViewBtn.setBackground(new Color(100, 116, 139));
        });

        JLabel selectLbl = new JLabel("Select Graph:");
        selectLbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        inspectorCombo = new JComboBox<>(GRAPH_TITLES);
        inspectorCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        inspectorCombo.setPreferredSize(new Dimension(380, 28));
        inspectorCombo.addActionListener(e -> {
            selectedInspectorIndex = inspectorCombo.getSelectedIndex();
            updateInspectorGraph();
        });

        JButton prevBtn = new JButton("◀ Prev");
        prevBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        prevBtn.addActionListener(e -> {
            if (selectedInspectorIndex > 0) {
                selectedInspectorIndex--;
                inspectorCombo.setSelectedIndex(selectedInspectorIndex);
            }
        });

        JButton nextBtn = new JButton("Next ▶");
        nextBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        nextBtn.addActionListener(e -> {
            if (selectedInspectorIndex < GRAPH_TITLES.length - 1) {
                selectedInspectorIndex++;
                inspectorCombo.setSelectedIndex(selectedInspectorIndex);
            }
        });

        navBar.add(backBtn);
        navBar.add(Box.createHorizontalStrut(15));
        navBar.add(selectLbl);
        navBar.add(inspectorCombo);
        navBar.add(prevBtn);
        navBar.add(nextBtn);

        // Center: Full-Res Image in ScrollPane
        inspectorImageLabel = new JLabel("Loading full resolution graph...", SwingConstants.CENTER);
        inspectorImageLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        inspectorImageLabel.setForeground(new Color(100, 116, 139));

        JScrollPane scroll = new JScrollPane(inspectorImageLabel);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setBorder(null);

        // Bottom: Description & Insights Card
        JPanel descCard = new JPanel(new BorderLayout());
        descCard.setBackground(new Color(248, 250, 252));
        descCard.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(226, 232, 240)),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));

        inspectorDescLabel = new JLabel(GRAPH_DESCRIPTIONS[0]);
        inspectorDescLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        inspectorDescLabel.setForeground(new Color(51, 65, 85));
        descCard.add(inspectorDescLabel, BorderLayout.CENTER);

        panel.add(navBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(descCard, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Updates the high-resolution image in the Single Graph Inspector.
     */
    private void updateInspectorGraph() {
        if (selectedInspectorIndex < 0 || selectedInspectorIndex >= GRAPH_FILES.length) return;
        inspectorDescLabel.setText(GRAPH_DESCRIPTIONS[selectedInspectorIndex]);

        File outDir = RAnalyticsRunner.getRunOutputDirectory(activeRunId);
        File imgFile = new File(outDir, GRAPH_FILES[selectedInspectorIndex]);
        if (imgFile.exists() && imgFile.length() > 0) {
            try {
                // Read directly via ImageIO to guarantee fresh reload
                BufferedImage bImg = ImageIO.read(imgFile);
                if (bImg != null) {
                    int origW = bImg.getWidth();
                    int origH = bImg.getHeight();
                    int targetW = Math.min(origW, 1020);
                    int targetH = (int) ((double) targetW / origW * origH);
                    Image scaled = bImg.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
                    inspectorImageLabel.setIcon(new ImageIcon(scaled));
                    inspectorImageLabel.setText(null);
                }
            } catch (Exception ex) {
                inspectorImageLabel.setIcon(null);
                inspectorImageLabel.setText("Failed to load graph: " + ex.getMessage());
            }
        } else {
            inspectorImageLabel.setIcon(null);
            inspectorImageLabel.setText("Graph image not generated yet. Click '🔄 Refresh Dashboard' to run R analysis.");
        }
    }

    private JPanel buildVehiclesTableTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        // Search bar
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        searchBar.setBackground(new Color(248, 250, 252));
        searchBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)));

        JLabel searchLbl = new JLabel("Filter Vehicles:");
        searchLbl.setFont(new Font("SansSerif", Font.BOLD, 12));

        searchField = new JTextField(20);
        searchField.setFont(new Font("SansSerif", Font.PLAIN, 12));
        searchField.addActionListener(e -> filterVehicleTable());

        JButton filterBtn = new JButton("Filter");
        filterBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        filterBtn.addActionListener(e -> filterVehicleTable());

        JButton clearFilterBtn = new JButton("Clear");
        clearFilterBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        clearFilterBtn.addActionListener(e -> {
            searchField.setText("");
            loadVehicleTableData();
        });

        searchBar.add(searchLbl);
        searchBar.add(searchField);
        searchBar.add(filterBtn);
        searchBar.add(clearFilterBtn);

        // Vehicle Table
        String[] columns = {
            "Vehicle Number", "Vehicle Type", "Road Approach", "Lane",
            "Status", "Speed (px/s)", "Arrival Time", "Intersection Entry", "Exit Time", "Emergency?"
        };

        vehicleTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        vehicleTable = new JTable(vehicleTableModel);
        vehicleTable.setRowHeight(24);
        vehicleTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        vehicleTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));
        vehicleTable.getTableHeader().setBackground(new Color(241, 245, 249));

        // Center align columns
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < vehicleTable.getColumnCount(); i++) {
            vehicleTable.getColumnModel().getColumn(i).setCellRenderer(center);
        }

        JScrollPane scroll = new JScrollPane(vehicleTable);
        panel.add(searchBar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildHistoryTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);

        String[] cols = { "Run ID", "Selected Strategy", "Speed (px/s)", "Status", "Start Time", "End Time", "Scenario" };
        historyTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };

        historyTable = new JTable(historyTableModel);
        historyTable.setRowHeight(24);
        historyTable.setFont(new Font("SansSerif", Font.PLAIN, 12));
        historyTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton loadRunBtn = new JButton("Load Selected Run in Dashboard");
        loadRunBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        loadRunBtn.addActionListener(e -> {
            int row = historyTable.getSelectedRow();
            if (row >= 0) {
                int runId = (int) historyTableModel.getValueAt(row, 0);
                setSelectedRun(runId);
            }
        });
        bottomBar.add(loadRunBtn);

        panel.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(bg.darker(), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        return btn;
    }

    /**
     * Refreshes run selector, KPI metrics, vehicle table, and history table from MySQL.
     */
    public void refreshData() {
        if (!DatabaseConnection.isAvailable()) {
            statusBanner.setText("MySQL: Offline (Simulation in safe mode) | R Engine: Ready");
            statusBanner.setForeground(new Color(239, 68, 68));
            return;
        }

        statusBanner.setText("MySQL: Connected | Active Run #" + activeRunId);
        statusBanner.setForeground(new Color(34, 197, 94));

        loadRunHistoryCombo();
        loadKpiMetrics();
        loadVehicleTableData();
        loadHistoryTableData();
        loadGraphImages();
    }

    private void loadRunHistoryCombo() {
        List<SimulationDAO.SimulationRunRecord> runs = simulationDAO.getAllSimulationRuns();
        runCombo.removeAllItems();
        SimulationDAO.SimulationRunRecord toSelect = null;
        for (SimulationDAO.SimulationRunRecord r : runs) {
            runCombo.addItem(r);
            if (r.getRunId() == activeRunId) {
                toSelect = r;
            }
        }
        if (toSelect != null) {
            runCombo.setSelectedItem(toSelect);
        }
    }

    private void onRunSelected() {
        SimulationDAO.SimulationRunRecord sel = (SimulationDAO.SimulationRunRecord) runCombo.getSelectedItem();
        if (sel != null && sel.getRunId() != activeRunId) {
            activeRunId = sel.getRunId();
            refreshData();
            triggerRGeneration();
        }
    }

    public void setSelectedRun(int runId) {
        this.activeRunId = runId;
        refreshData();
        triggerRGeneration();
    }

    private void loadKpiMetrics() {
        Map<String, Object> metrics = analyticsDAO.getRunSummaryMetrics(activeRunId);
        totalVehiclesCard.setText(String.valueOf(metrics.getOrDefault("total_vehicles", 0)));
        waitingVehiclesCard.setText(String.valueOf(metrics.getOrDefault("waiting_vehicles", 0)));
        exitedVehiclesCard.setText(String.valueOf(metrics.getOrDefault("exited_vehicles", 0)));
        double wait = (double) metrics.getOrDefault("avg_wait_seconds", 0.0);
        avgWaitCard.setText(String.format("%.1fs", wait));
        emergencyCard.setText(String.valueOf(metrics.getOrDefault("emergency_vehicles", 0)));
    }

    private void loadVehicleTableData() {
        vehicleTableModel.setRowCount(0);
        List<VehicleDAO.VehicleRecord> list = vehicleDAO.getVehiclesByRun(activeRunId);
        for (VehicleDAO.VehicleRecord v : list) {
            vehicleTableModel.addRow(new Object[] {
                v.getVehicleNumber(),
                v.getVehicleType(),
                v.getRoadDirection(),
                v.getLaneName(),
                v.getCurrentStatus(),
                String.format("%.0f", v.getSpeed()),
                formatTimestamp(v.getArrivalTime()),
                formatTimestamp(v.getEntryTime()),
                formatTimestamp(v.getExitTime()),
                v.isEmergency() ? "YES" : "No"
            });
        }
    }

    private void filterVehicleTable() {
        String filter = searchField.getText().trim().toLowerCase();
        if (filter.isEmpty()) {
            loadVehicleTableData();
            return;
        }

        vehicleTableModel.setRowCount(0);
        List<VehicleDAO.VehicleRecord> list = vehicleDAO.getVehiclesByRun(activeRunId);
        for (VehicleDAO.VehicleRecord v : list) {
            boolean match = v.getVehicleNumber().toLowerCase().contains(filter)
                    || v.getVehicleType().toLowerCase().contains(filter)
                    || v.getRoadDirection().toLowerCase().contains(filter)
                    || v.getCurrentStatus().toLowerCase().contains(filter);
            if (match) {
                vehicleTableModel.addRow(new Object[] {
                    v.getVehicleNumber(),
                    v.getVehicleType(),
                    v.getRoadDirection(),
                    v.getLaneName(),
                    v.getCurrentStatus(),
                    String.format("%.0f", v.getSpeed()),
                    formatTimestamp(v.getArrivalTime()),
                    formatTimestamp(v.getEntryTime()),
                    formatTimestamp(v.getExitTime()),
                    v.isEmergency() ? "YES" : "No"
                });
            }
        }
    }

    private void loadHistoryTableData() {
        historyTableModel.setRowCount(0);
        List<SimulationDAO.SimulationRunRecord> runs = simulationDAO.getAllSimulationRuns();
        for (SimulationDAO.SimulationRunRecord r : runs) {
            historyTableModel.addRow(new Object[] {
                r.getRunId(),
                r.getStrategy(),
                String.format("%.0f", r.getSpeed()),
                r.getStatus(),
                formatTimestamp(r.getStartTime()),
                formatTimestamp(r.getEndTime()),
                r.getScenarioId()
            });
        }
    }

    private String formatTimestamp(java.sql.Timestamp ts) {
        if (ts == null) return "—";
        String s = ts.toString();
        return s.length() > 19 ? s.substring(11, 19) : s;
    }

    /**
     * Reads and displays all 8 R-generated PNG graphs using ImageIO to bypass AWT Toolkit caching.
     */
    private void loadGraphImages() {
        File outDir = RAnalyticsRunner.getRunOutputDirectory(activeRunId);
        for (int i = 0; i < 8; i++) {
            File imgFile = new File(outDir, GRAPH_FILES[i]);
            if (imgFile.exists() && imgFile.length() > 0) {
                try {
                    // MUST read fresh bytes with ImageIO to bypass AWT Toolkit image cache
                    BufferedImage bImg = ImageIO.read(imgFile);
                    if (bImg != null) {
                        int origW = bImg.getWidth();
                        int origH = bImg.getHeight();

                        // 1. Scale for Grid View card (~520px width)
                        int gridW = 520;
                        int gridH = (int) ((double) gridW / origW * origH);
                        Image gridScaled = bImg.getScaledInstance(gridW, gridH, Image.SCALE_SMOOTH);
                        gridImageLabels[i].setIcon(new ImageIcon(gridScaled));
                        gridImageLabels[i].setText(null);
                    }
                } catch (Exception ex) {
                    gridImageLabels[i].setIcon(null);
                    gridImageLabels[i].setText("Error loading graph: " + ex.getMessage());
                }
            } else {
                gridImageLabels[i].setIcon(null);
                gridImageLabels[i].setText("Awaiting R analysis for Run #" + activeRunId + "...");
            }
        }

        // Also refresh inspector if visible
        updateInspectorGraph();

        getContentPane().revalidate();
        getContentPane().repaint();
    }

    /**
     * Triggers asynchronous background execution of R script.
     */
    public void triggerRGeneration() {
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        statusBanner.setText("Generating R Graphs for Run #" + activeRunId + "...");
        RAnalyticsRunner.getInstance().generateGraphsAsync(activeRunId, this);
    }

    @Override
    public void onGraphsUpdated(int runId, File outputDir) {
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        String timeStr = new SimpleDateFormat("HH:mm:ss").format(new Date());
        statusBanner.setText("All 8 graphs updated successfully at " + timeStr + " for Run #" + runId);
        statusBanner.setForeground(new Color(34, 197, 94));
        if (runId == activeRunId) {
            loadGraphImages();
            loadKpiMetrics();
            loadVehicleTableData();
        }
    }

    @Override
    public void onGraphError(int runId, String errorMessage) {
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        statusBanner.setText("R Generation Notice: " + errorMessage);
        statusBanner.setForeground(new Color(239, 68, 68));
    }

    /**
     * Exports vehicles data of the active run to a clean CSV file.
     */
    private void exportDataToCSV() {
        JFileChooser chooser = new JFileChooser(new File("."));
        chooser.setSelectedFile(new File("traffic_run_" + activeRunId + "_vehicles.csv"));
        int res = chooser.showSaveDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File dest = chooser.getSelectedFile();
            try (FileWriter fw = new FileWriter(dest)) {
                fw.write("vehicle_number,vehicle_type,road_direction,lane_name,status,speed,arrival_time,entry_time,exit_time,is_emergency\n");
                List<VehicleDAO.VehicleRecord> list = vehicleDAO.getVehiclesByRun(activeRunId);
                for (VehicleDAO.VehicleRecord v : list) {
                    fw.write(String.format("%s,%s,%s,%s,%s,%.1f,%s,%s,%s,%s\n",
                        v.getVehicleNumber(), v.getVehicleType(), v.getRoadDirection(), v.getLaneName(),
                        v.getCurrentStatus(), v.getSpeed(),
                        v.getArrivalTime(), v.getEntryTime(), v.getExitTime(), v.isEmergency()));
                }
                JOptionPane.showMessageDialog(this, "Exported " + list.size() + " vehicle records to:\n" + dest.getAbsolutePath(),
                        "Export Successful", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * Opens the RStudio project file in RStudio on macOS.
     */
    private void openRStudioProject() {
        File rproj = new File("R_Analytics/TrafficAnalytics.Rproj");
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(rproj);
            } else {
                new ProcessBuilder("open", rproj.getAbsolutePath()).start();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "RStudio project file location:\n" + rproj.getAbsolutePath() +
                    "\n\nCould not launch automatically: " + e.getMessage(), "RStudio Project", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Opens the run-specific output folder in macOS Finder.
     */
    private void openOutputFolder() {
        File outDir = RAnalyticsRunner.getRunOutputDirectory(activeRunId);
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(outDir);
            } else {
                new ProcessBuilder("open", outDir.getAbsolutePath()).start();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Output directory:\n" + outDir.getAbsolutePath(),
                    "Graph Output Directory", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
