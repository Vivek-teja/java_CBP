package strategy;

import models.Road;

/**
 * Concrete subclass of SignalTimingStrategy.
 * Dynamically computes green-light duration based on vehicle count.
 */
public class DensityBasedTimingStrategy extends SignalTimingStrategy {

    // Constructor initializes strategy name via superclass constructor
    public DensityBasedTimingStrategy() {
        super("Density-based green time");
    }

    // Overridden method: calculates dynamic duration using vehicle density
    // Formula: greenTime = min(60, max(15, 10 + totalVehicles * 5))
    @Override
    public int calculateGreenTime(Road road) {
        int totalVehicles = road.getTotalVehicles();
        int calculatedTime = 10 + (totalVehicles * 5);
        int greenTime = Math.min(60, Math.max(15, calculatedTime));
        return greenTime;
    }
}
