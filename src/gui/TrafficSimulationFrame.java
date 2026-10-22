package gui;

import controller.IntersectionController;
import controller.TrafficController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import models.Road;
import strategy.DensityBasedTimingStrategy;
import strategy.FixedTimingStrategy;
import strategy.SignalTimingStrategy;

/**
 * Main Swing Window for the Traffic Management System CBP.
 * Provides user controls to switch timing strategies, toggle non-conflicting
 * signal phases, and refresh the bird's-eye view visualization.
 */
public class TrafficSimulationFrame extends JFrame {

    private Road northRoad;
    private Road southRoad;
    private Road eastRoad;
    private Road westRoad;

    private TrafficController controller;
    private SignalTimingStrategy fixedStrategy;
    private SignalTimingStrategy densityStrategy;
    private SignalTimingStrategy activeStrategy;

    private String currentPhase = "NORTH_SOUTH"; // Initial non-conflicting phase

    private IntersectionPanel intersectionPanel;
    private JLabel statusLabel;
    private JComboBox<String> strategyCombo;

    public TrafficSimulationFrame(Road north, Road south, Road east, Road west) {
        super("Traffic Management System - Bird's-Eye View");

        this.northRoad = north;
        this.southRoad = south;
        this.eastRoad = east;
        this.westRoad = west;

        // Reuse existing controller and strategy classes (No duplicated timing formulas)
        this.controller = new IntersectionController("City Center 4-Way Junction");
        this.fixedStrategy = new FixedTimingStrategy();
        this.densityStrategy = new DensityBasedTimingStrategy();
        this.activeStrategy = this.densityStrategy; // Default to density-based

        initComponents();

        // Initial signal state application
        applyCurrentState();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 800);
        setLocationRelativeTo(null); // Center on screen
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // 1. Control Panel at the top
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        controlPanel.setBackground(new Color(240, 244, 248));
        controlPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 210, 220)));

        JLabel strategyLabel = new JLabel("Timing Strategy:");
        strategyLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        strategyCombo = new JComboBox<>(new String[] {
            "Density-Based Timing",
            "Fixed Timing (30s)"
        });
        strategyCombo.setFont(new Font("SansSerif", Font.PLAIN, 13));
        strategyCombo.addActionListener(e -> {
            int selected = strategyCombo.getSelectedIndex();
            activeStrategy = (selected == 0) ? densityStrategy : fixedStrategy;
            applyCurrentState();
        });

        JButton togglePhaseBtn = new JButton("Toggle Signal Phase (N-S / E-W)");
        togglePhaseBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        togglePhaseBtn.setBackground(new Color(50, 110, 200));
        togglePhaseBtn.setForeground(Color.WHITE);
        togglePhaseBtn.setFocusPainted(false);
        togglePhaseBtn.addActionListener(e -> {
            // Toggle between non-conflicting phases
            currentPhase = currentPhase.equals("NORTH_SOUTH") ? "EAST_WEST" : "NORTH_SOUTH";
            applyCurrentState();
        });

        JButton refreshBtn = new JButton("Refresh Timings");
        refreshBtn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        refreshBtn.addActionListener(e -> applyCurrentState());

        controlPanel.add(strategyLabel);
        controlPanel.add(strategyCombo);
        controlPanel.add(togglePhaseBtn);
        controlPanel.add(refreshBtn);

        // 2. Bird's-eye view panel in the center
        intersectionPanel = new IntersectionPanel(northRoad, southRoad, eastRoad, westRoad);

        // 3. Status Bar at the bottom
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 8));
        statusPanel.setBackground(new Color(25, 30, 36));

        statusLabel = new JLabel();
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.setForeground(new Color(220, 230, 240));
        statusPanel.add(statusLabel);

        add(controlPanel, BorderLayout.NORTH);
        add(intersectionPanel, BorderLayout.CENTER);
        add(statusPanel, BorderLayout.SOUTH);
    }

    /**
     * Applies the selected strategy to the current phase and refreshes GUI.
     * Enforces that North/South and East/West are never green at the same time.
     */
    private void applyCurrentState() {
        controller.applyPhase(currentPhase, northRoad, southRoad, eastRoad, westRoad, activeStrategy);

        String phaseText = currentPhase.equals("NORTH_SOUTH") 
                ? "Active Phase: NORTH-SOUTH GREEN (East-West Stopped)"
                : "Active Phase: EAST-WEST GREEN (North-South Stopped)";

        statusLabel.setText(phaseText + "  |  Active Strategy: " + activeStrategy.getStrategyName());
        intersectionPanel.updateRoads(northRoad, southRoad, eastRoad, westRoad);
    }

    /**
     * Standalone main method to test and run the GUI directly from Eclipse.
     */
    public static void main(String[] args) {
        // Reuses the model data defined in MainApp
        Road[] roads = Main.MainApp.createIntersectionRoads();

        SwingUtilities.invokeLater(() -> {
            TrafficSimulationFrame frame = new TrafficSimulationFrame(roads[0], roads[1], roads[2], roads[3]);
            frame.setVisible(true);
        });
    }
}
