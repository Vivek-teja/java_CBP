package gui;

import controller.IntersectionController;
import controller.TrafficController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import models.Road;
import models.TrafficSimulationModel;
import strategy.DensityBasedTimingStrategy;
import strategy.FixedTimingStrategy;
import strategy.SignalTimingStrategy;
import threading.TrafficThreading;

/**
 * Main Swing Window for the Traffic Management System CBP.
 * Drives the deterministic TrafficSimulationModel via a 1-second Swing Timer,
 * rendering dynamic vehicle movements, phase transitions, and countdowns on the EDT.
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

    // Deterministic simulation model
    private transient TrafficSimulationModel model;

    // Background dynamic vehicle arrival producer
    private transient TrafficThreading.TrafficEventProducer eventProducer;

    // Swing timer for 1-second ticks
    private Timer simulationTimer;

    // Swing timer for ~60 FPS smooth rendering animation
    private Timer animationTimer;

    // Timestamp of the most recent simulation tick (nanoseconds)
    private long lastTickNanos = 0;

    // Cached status bar text to avoid redundant setText() calls
    private String lastStatusText = "";

    // Window disposal flag to guard background and EDT callbacks
    private volatile boolean disposed = false;

    private IntersectionPanel intersectionPanel;
    private JLabel statusLabel;
    private JComboBox<String> strategyCombo;

    public TrafficSimulationFrame(Road north, Road south, Road east, Road west) {
        super("Traffic Management System - Bird's-Eye View");

        this.northRoad = north;
        this.southRoad = south;
        this.eastRoad = east;
        this.westRoad = west;

        // Instantiate the deterministic simulation model
        this.model = new TrafficSimulationModel(northRoad, southRoad, eastRoad, westRoad);
        this.controller = model.getController();
        this.fixedStrategy = model.getFixedStrategy();
        this.densityStrategy = model.getDensityStrategy();
        this.activeStrategy = model.getActiveStrategy();

        initComponents();

        // Initial phase application & start automatic simulation timer
        applyCurrentState();
        startTimer();

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
        strategyCombo.addActionListener(e -> onStrategyChanged());

        JButton togglePhaseBtn = new JButton("Toggle Signal Phase (N-S / E-W)");
        togglePhaseBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        togglePhaseBtn.setBackground(new Color(50, 110, 200));
        togglePhaseBtn.setForeground(Color.WHITE);
        togglePhaseBtn.setFocusPainted(false);
        togglePhaseBtn.addActionListener(e -> onManualPhaseToggle());

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

        // Ensure timer is stopped cleanly when window closes
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                stopTimer();
            }
        });
    }

    /**
     * Starts the automatic 1-second simulation clock on the Event Dispatch Thread,
     * launches the smooth rendering animation timer (~60 FPS),
     * and launches the background vehicle arrival producer daemon.
     */
    private void startTimer() {
        if (disposed) {
            return;
        }
        if (simulationTimer != null && simulationTimer.isRunning()) {
            simulationTimer.stop();
        }
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        if (eventProducer != null) {
            eventProducer.stop(500);
        }
        eventProducer = new TrafficThreading.TrafficEventProducer(model);
        eventProducer.start();

        lastTickNanos = System.nanoTime();
        intersectionPanel.snapshotPreviousPositions();
        intersectionPanel.setInterpolationAlpha(0.0);

        simulationTimer = new Timer(1000, e -> onTimerTick());
        simulationTimer.start();

        animationTimer = new Timer(16, e -> onAnimationTick());
        animationTimer.start();
    }

    /**
     * Stops the simulation timer, animation timer, and background producer cleanly without leaving orphaned threads.
     */
    public void stopTimer() {
        if (simulationTimer != null && simulationTimer.isRunning()) {
            simulationTimer.stop();
        }
        if (animationTimer != null && animationTimer.isRunning()) {
            animationTimer.stop();
        }
        if (eventProducer != null) {
            eventProducer.stop(1500);
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        stopTimer();
        super.dispose();
    }

    /**
     * Called once per second by the Swing simulation Timer.
     * Advances the deterministic model and updates the display.
     */
    private void onTimerTick() {
        if (disposed) {
            return;
        }
        intersectionPanel.snapshotPreviousPositions();
        model.tick();
        lastTickNanos = System.nanoTime();
        updateStatusBar();
        intersectionPanel.setInterpolationAlpha(0.0);
        intersectionPanel.repaint();
    }

    /**
     * Called approximately every 16ms (~60 FPS) by the Swing animation Timer.
     * Interpolates vehicle rendering positions between discrete 1-second ticks.
     */
    private void onAnimationTick() {
        if (disposed || !isDisplayable()) {
            return;
        }
        long now = System.nanoTime();
        double elapsedSec = (now - lastTickNanos) / 1_000_000_000.0;
        double alpha = Math.max(0.0, Math.min(1.0, elapsedSec));
        intersectionPanel.setInterpolationAlpha(alpha);
        intersectionPanel.repaint();
    }

    /**
     * Applies current state to the model and updates the display.
     */
    public void applyCurrentState() {
        model.applyCurrentState();
        updateStatusBar();
        intersectionPanel.updateRoads(northRoad, southRoad, eastRoad, westRoad);
        intersectionPanel.snapshotPreviousPositions();
        lastTickNanos = System.nanoTime();
        intersectionPanel.setInterpolationAlpha(1.0);
    }

    /**
     * Handles strategy dropdown changes safely via the model.
     */
    private void onStrategyChanged() {
        int selected = strategyCombo.getSelectedIndex();
        activeStrategy = (selected == 0) ? densityStrategy : fixedStrategy;
        model.setActiveStrategy(activeStrategy);

        updateStatusBar();
        intersectionPanel.updateRoads(northRoad, southRoad, eastRoad, westRoad);
        intersectionPanel.snapshotPreviousPositions();
        lastTickNanos = System.nanoTime();
        intersectionPanel.setInterpolationAlpha(1.0);
    }

    /**
     * Handles manual phase toggle safely via the model.
     */
    private void onManualPhaseToggle() {
        model.manualPhaseToggle();
        updateStatusBar();
        intersectionPanel.updateRoads(northRoad, southRoad, eastRoad, westRoad);
        intersectionPanel.snapshotPreviousPositions();
        lastTickNanos = System.nanoTime();
        intersectionPanel.setInterpolationAlpha(1.0);
    }

    /**
     * Updates the status bar text reflecting active axis, state, countdown, and strategy.
     * Avoids redundant setText() calls when the text is unchanged.
     */
    private void updateStatusBar() {
        String phaseText;
        String currentSignalState = model.getCurrentSignalState();
        String currentAxis = model.getCurrentAxis();
        int remainingSeconds = model.getRemainingSeconds();

        if (models.TrafficSignal.GREEN.equals(currentSignalState)) {
            phaseText = currentAxis.equals("NORTH_SOUTH")
                    ? "Active Phase: NORTH-SOUTH GREEN (East-West Stopped)"
                    : "Active Phase: EAST-WEST GREEN (North-South Stopped)";
        } else {
            phaseText = currentAxis.equals("NORTH_SOUTH")
                    ? "Active Phase: NORTH-SOUTH YELLOW (Clearance Phase)"
                    : "Active Phase: EAST-WEST YELLOW (Clearance Phase)";
        }

        String newText = phaseText + "  |  Time Remaining: " + remainingSeconds + "s  |  Strategy: " + activeStrategy.getStrategyName();
        if (!newText.equals(lastStatusText)) {
            lastStatusText = newText;
            statusLabel.setText(newText);
        }
    }

    // Deterministic simulation & inspection helpers (Preserves backward compatibility)
    public void advanceSimulationTick() {
        onTimerTick();
        intersectionPanel.setInterpolationAlpha(1.0);
        intersectionPanel.repaint();
    }

    public void requestManualPhaseToggle() {
        onManualPhaseToggle();
    }

    public void selectStrategy(int index) {
        if (strategyCombo != null) {
            strategyCombo.setSelectedIndex(index);
        } else {
            activeStrategy = (index == 0) ? densityStrategy : fixedStrategy;
            onStrategyChanged();
        }
    }

    public int getRemainingSeconds() {
        return model.getRemainingSeconds();
    }

    public String getCurrentSignalState() {
        return model.getCurrentSignalState();
    }

    public String getCurrentAxis() {
        return model.getCurrentAxis();
    }

    public int getElapsedGreenSeconds() {
        return model.getElapsedGreenSeconds();
    }

    public Timer getSimulationTimer() {
        return simulationTimer;
    }

    public Timer getAnimationTimer() {
        return animationTimer;
    }

    public boolean isDisposed() {
        return disposed;
    }

    public TrafficSimulationModel getModel() {
        return model;
    }

    public TrafficThreading.TrafficEventProducer getEventProducer() {
        return eventProducer;
    }

    public void setEventProducer(TrafficThreading.TrafficEventProducer producer) {
        if (this.eventProducer != null) {
            this.eventProducer.stop(1500);
        }
        this.eventProducer = producer;
        if (producer != null) {
            producer.start();
        }
    }
}
