package gui;

import controller.IntersectionController;
import controller.TrafficController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
    private JComboBox<String> speedCombo;
    private JCheckBox autoStopCheckbox;
    private JCheckBox autoSpawnCheck;

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
        setSize(1100, 890);
        setLocationRelativeTo(null); // Center on screen
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // 1. Control Panel at the top with two structured, descriptive rows
        JPanel controlPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        controlPanel.setBackground(new Color(240, 244, 248));
        controlPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(200, 210, 220)));

        // Row 1: Strategy, Speed, Auto-Stop, and Signal Phase Controls
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        row1.setOpaque(false);

        JLabel strategyLabel = new JLabel("Timing Strategy:");
        strategyLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        strategyCombo = new JComboBox<>(new String[] {
            "Speed-Based Density (Calculates Time from Speed & Density)",
            "Theoretical Density Formula (10s + 5s/veh)",
            "Fixed Timing (Constant 30s Duration)"
        });
        strategyCombo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        strategyCombo.setToolTipText("Selects signal timing algorithm: Speed-Based adapts to clearance speed; Theoretical uses 10+5N; Fixed uses constant 30s");
        strategyCombo.addActionListener(e -> onStrategyChanged());

        JLabel speedLabel = new JLabel("Vehicle Speed:");
        speedLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        speedCombo = new JComboBox<>(new String[] {
            "Slow (35 px/s)",
            "Normal (50 px/s)",
            "Fast (70 px/s)"
        });
        speedCombo.setSelectedIndex(1); // Normal (50 px/s)
        speedCombo.setFont(new Font("SansSerif", Font.PLAIN, 11));
        speedCombo.setToolTipText("Changes physical movement speed of vehicles across the junction and recalculates green times");
        speedCombo.addActionListener(e -> onSpeedChanged());

        autoStopCheckbox = new JCheckBox("Auto-Stop When Queue Clears", true);
        autoStopCheckbox.setFont(new Font("SansSerif", Font.BOLD, 11));
        autoStopCheckbox.setForeground(new Color(25, 120, 35));
        autoStopCheckbox.setOpaque(false);
        autoStopCheckbox.setToolTipText("Actuated Signal Control: Terminates green phase immediately once all waiting vehicles on active axis have crossed");
        autoStopCheckbox.addActionListener(e -> model.setAutoStopOnEmpty(autoStopCheckbox.isSelected()));

        JButton togglePhaseBtn = new JButton("Switch Phase (N-S / E-W)");
        togglePhaseBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        togglePhaseBtn.setForeground(new Color(20, 60, 160));
        togglePhaseBtn.setFocusPainted(false);
        togglePhaseBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 120, 210), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        togglePhaseBtn.setToolTipText("Manually toggles green right-of-way between North-South and East-West corridors");
        togglePhaseBtn.addActionListener(e -> onManualPhaseToggle());

        JButton emergencyBtn = new JButton("Dispatch Ambulance (Priority)");
        emergencyBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        emergencyBtn.setForeground(new Color(180, 20, 20));
        emergencyBtn.setFocusPainted(false);
        emergencyBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 50, 50), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        emergencyBtn.setToolTipText("Dispatches emergency vehicle on North approach, preempting conflicting traffic through yellow clearance");
        emergencyBtn.addActionListener(e -> onDispatchEmergency());

        JButton refreshBtn = new JButton("Refresh Signals");
        refreshBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        refreshBtn.setForeground(new Color(40, 50, 65));
        refreshBtn.setFocusPainted(false);
        refreshBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 175, 190), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        refreshBtn.setToolTipText("Recalculates vehicle counts and synchronizes signal phase durations");
        refreshBtn.addActionListener(e -> applyCurrentState());

        row1.add(strategyLabel);
        row1.add(strategyCombo);
        row1.add(speedLabel);
        row1.add(speedCombo);
        row1.add(autoStopCheckbox);
        row1.add(togglePhaseBtn);
        row1.add(emergencyBtn);
        row1.add(refreshBtn);

        // Row 2: Dynamic Vehicle Addition & Direction-Annotated Queue Management Controls
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        row2.setOpaque(false);

        JLabel addLabel = new JLabel("Add Vehicles:");
        addLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        addLabel.setForeground(new Color(20, 30, 45));

        JButton addNorthBtn = createAddVehicleButton("+ North (Heading South ↓)", 1, "Queues a vehicle at North approach travelling Southward into the intersection");
        JButton addSouthBtn = createAddVehicleButton("+ South (Heading North ↑)", 2, "Queues a vehicle at South approach travelling Northward into the intersection");
        JButton addEastBtn = createAddVehicleButton("+ East (Heading West ←)", 3, "Queues a vehicle at East approach travelling Westward into the intersection");
        JButton addWestBtn = createAddVehicleButton("+ West (Heading East →)", 4, "Queues a vehicle at West approach travelling Eastward into the intersection");

        JButton addRandomBtn = new JButton("+5 Random (All Roads)");
        addRandomBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        addRandomBtn.setForeground(new Color(20, 60, 160));
        addRandomBtn.setFocusPainted(false);
        addRandomBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 120, 210), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        addRandomBtn.setToolTipText("Spreads 5 new vehicles randomly across North, South, East, and West approach queues");
        addRandomBtn.addActionListener(e -> onAddRandomVehicles());

        JButton setDensityBtn = new JButton("Set Exact Count (Select Road)");
        setDensityBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        setDensityBtn.setForeground(new Color(40, 50, 65));
        setDensityBtn.setFocusPainted(false);
        setDensityBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(160, 175, 190), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        setDensityBtn.setToolTipText("Opens dialog to set an exact vehicle count for a selected approach");
        setDensityBtn.addActionListener(e -> onSetVehicleDensity());

        JButton clearAllBtn = new JButton("Clear All Vehicles (Test Idle Stop)");
        clearAllBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        clearAllBtn.setForeground(new Color(180, 20, 20));
        clearAllBtn.setFocusPainted(false);
        clearAllBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(210, 50, 50), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        clearAllBtn.setToolTipText("Removes all vehicles from all approaches to test how signals stop in idle rest when lanes are empty");
        clearAllBtn.addActionListener(e -> onClearAllVehicles());

        autoSpawnCheck = new JCheckBox("Background Traffic (Random Arrivals)", false);
        autoSpawnCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        autoSpawnCheck.setOpaque(false);
        autoSpawnCheck.setToolTipText("Enables background daemon thread to continuously spawn random vehicle arrivals");
        autoSpawnCheck.addActionListener(e -> onAutoSpawnToggled());

        row2.add(addLabel);
        row2.add(addNorthBtn);
        row2.add(addSouthBtn);
        row2.add(addEastBtn);
        row2.add(addWestBtn);
        row2.add(addRandomBtn);
        row2.add(setDensityBtn);
        row2.add(clearAllBtn);
        row2.add(autoSpawnCheck);

        controlPanel.add(row1);
        controlPanel.add(row2);

        // 2. Bird's-eye view panel in the center
        intersectionPanel = new IntersectionPanel(northRoad, southRoad, eastRoad, westRoad);
        intersectionPanel.setVehicleAddListener(roadId -> onAddVehicle(roadId));

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

    private JButton createAddVehicleButton(String label, int roadId, String tooltip) {
        JButton btn = new JButton(label);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setForeground(new Color(10, 100, 35));
        btn.setFocusPainted(false);
        btn.setToolTipText(tooltip);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(40, 150, 70), 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));
        btn.addActionListener(e -> onAddVehicle(roadId));
        return btn;
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
        if (autoSpawnCheck != null && autoSpawnCheck.isSelected()) {
            eventProducer.start();
        }

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
        if (selected == 0) {
            activeStrategy = densityStrategy;
            model.setSpeedBasedTimingEnabled(true);
        } else if (selected == 1) {
            activeStrategy = densityStrategy;
            model.setSpeedBasedTimingEnabled(false);
        } else {
            activeStrategy = fixedStrategy;
            model.setSpeedBasedTimingEnabled(false);
        }
        model.setActiveStrategy(activeStrategy);

        updateStatusBar();
        intersectionPanel.updateRoads(northRoad, southRoad, eastRoad, westRoad);
        intersectionPanel.snapshotPreviousPositions();
        lastTickNanos = System.nanoTime();
        intersectionPanel.setInterpolationAlpha(1.0);
    }

    /**
     * Handles vehicle speed dropdown changes in the simulation.
     */
    private void onSpeedChanged() {
        int idx = speedCombo.getSelectedIndex();
        double speed = 50.0;
        if (idx == 0) speed = 35.0;
        else if (idx == 1) speed = 50.0;
        else if (idx == 2) speed = 70.0;

        model.setVehicleSpeed(speed);
        applyCurrentState();
        intersectionPanel.repaint();
    }

    /**
     * Toggles the background dynamic vehicle arrival generator daemon.
     */
    private void onAutoSpawnToggled() {
        if (autoSpawnCheck.isSelected()) {
            if (eventProducer == null || !eventProducer.isRunning()) {
                eventProducer = new TrafficThreading.TrafficEventProducer(model);
                eventProducer.start();
            }
        } else {
            if (eventProducer != null) {
                eventProducer.stop(500);
            }
        }
    }

    /**
     * Dynamically adds a vehicle to a specified road approach directly in the Swing interface.
     */
    public void onAddVehicle(int roadId) {
        model.addVehicleToRoad(roadId);
        applyCurrentState();
        intersectionPanel.repaint();
    }

    /**
     * Adds 5 vehicles scattered randomly across approaches.
     */
    private void onAddRandomVehicles() {
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < 5; i++) {
            model.addVehicleToRoad(rand.nextInt(4) + 1);
        }
        applyCurrentState();
        intersectionPanel.repaint();
    }

    /**
     * Clears all vehicles from all approaches, immediately stopping signal countdowns into idle rest.
     */
    private void onClearAllVehicles() {
        model.clearAllVehicles();
        applyCurrentState();
        intersectionPanel.repaint();
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
     * Triggers priority preemption for an emergency ambulance approach.
     */
    public void onDispatchEmergency() {
        model.triggerEmergencyPriority(1); // Dispatches on North approach
        updateStatusBar();
        intersectionPanel.updateRoads(northRoad, southRoad, eastRoad, westRoad);
        intersectionPanel.snapshotPreviousPositions();
        lastTickNanos = System.nanoTime();
        intersectionPanel.setInterpolationAlpha(1.0);
    }

    /**
     * Interactively prompts user to configure vehicle density on any road approach with robust validation.
     */
    public void onSetVehicleDensity() {
        String[] roads = { "North (Road 1)", "South (Road 2)", "East (Road 3)", "West (Road 4)" };
        String roadChoice = (String) JOptionPane.showInputDialog(
                this, "Select Road approach to configure:", "Configure Road Density",
                JOptionPane.QUESTION_MESSAGE, null, roads, roads[0]);
        if (roadChoice == null) return;

        int roadId = 1;
        Road targetRoad = northRoad;
        if (roadChoice.startsWith("South")) { roadId = 2; targetRoad = southRoad; }
        else if (roadChoice.startsWith("East")) { roadId = 3; targetRoad = eastRoad; }
        else if (roadChoice.startsWith("West")) { roadId = 4; targetRoad = westRoad; }

        String input = JOptionPane.showInputDialog(this,
                "Enter vehicle count for " + targetRoad.getDirection() + " Road (0 - 30):",
                "Configure Road Density", JOptionPane.QUESTION_MESSAGE);
        if (input == null) return;

        try {
            int count = Integer.parseInt(input.trim());
            if (count < 0) throw new IllegalArgumentException("Vehicle count cannot be negative: " + count);
            if (count > 50) throw new IllegalArgumentException("Vehicle count exceeds road capacity (50): " + count);

            targetRoad.lanes.clear();
            targetRoad.addLane(Main.MainApp.createPopulatedLane(roadId * 10 + 1, count / 2));
            targetRoad.addLane(Main.MainApp.createPopulatedLane(roadId * 10 + 2, count - count / 2));
            model.initializeVehicleQueues();
            applyCurrentState();
            JOptionPane.showMessageDialog(this,
                    targetRoad.getDirection() + " Road updated to " + count + " vehicles.",
                    "Density Updated", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid input: '" + input + "' is not a valid integer.", "Input Validation Error", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Input Validation Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Updates the status bar text reflecting active axis, state, countdown, strategy, speed, and empty idle state.
     * Avoids redundant setText() calls when the text is unchanged.
     */
    private void updateStatusBar() {
        if (model.isIntersectionIdle()) {
            String text = "[STOPPED - ALL LANES EMPTY] No vehicles waiting. Click '+ North / South / East / West' or click roads to add vehicles.";
            if (!text.equals(lastStatusText)) {
                lastStatusText = text;
                statusLabel.setText(text);
            }
            return;
        }

        String phaseText;
        String currentSignalState = model.getCurrentSignalState();
        String currentAxis = model.getCurrentAxis();
        int remainingSeconds = model.getRemainingSeconds();
        int waitingCurrent = model.getWaitingVehicleCount(currentAxis);

        if (models.TrafficSignal.GREEN.equals(currentSignalState)) {
            phaseText = currentAxis.equals("NORTH_SOUTH")
                    ? "Active Phase: NORTH-SOUTH GREEN (East-West Stopped)"
                    : "Active Phase: EAST-WEST GREEN (North-South Stopped)";
        } else {
            phaseText = currentAxis.equals("NORTH_SOUTH")
                    ? "Active Phase: NORTH-SOUTH YELLOW (Clearance Phase)"
                    : "Active Phase: EAST-WEST YELLOW (Clearance Phase)";
        }

        String prefix = model.isEmergencyActive() ? "[PRIORITY: AMBULANCE IN TRANSIT] " : "";
        String speedInfo = "Speed: " + (int) model.getVehicleSpeed() + " px/s";
        String queueInfo = "Waiting: " + waitingCurrent + " veh";
        String newText = prefix + phaseText + "  |  " + queueInfo + "  |  " + speedInfo + "  |  Remaining: " + remainingSeconds + "s  |  Strategy: " + activeStrategy.getStrategyName();
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
