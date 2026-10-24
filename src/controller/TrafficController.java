package controller;

import models.Road;
import strategy.SignalTimingStrategy;

/**
 * Interface defining traffic-control operations at an intersection.
 * Represents a contract for managing traffic flow and signal timing.
 */
public interface TrafficController {

    /**
     * Controls traffic on a specified road using a chosen signal timing strategy.
     * Calculates the required green-light duration and updates the signal.
     */
    void controlTraffic(Road road, SignalTimingStrategy strategy);

    /**
     * Checks lanes on a road and handles empty lane conditions.
     */
    void checkLanes(Road road);

    /**
     * Calculates and displays average vehicles per lane, handling zero-lane conditions.
     */
    void displayAverageVehicles(Road road);

    /**
     * Sets signal phases ensuring conflicting directions are not green simultaneously.
     * Either North/South are green, or East/West are green.
     */
    void applyPhase(String activeAxis, Road north, Road south, Road east, Road west, SignalTimingStrategy strategy);

    /**
     * Applies a yellow clearance phase to the active axis while holding the
     * perpendicular axis at red to ensure clearance safety.
     */
    void applyYellowPhase(String activeAxis, Road north, Road south, Road east, Road west, int yellowDuration);

    /**
     * Updates the remaining countdown on the active axis signals.
     */
    void updateSignalCountdown(String activeAxis, Road north, Road south, Road east, Road west, int remainingSeconds);

    /**
     * Displays the current operating status and info of the controller.
     */
    void displayStatus();
}
