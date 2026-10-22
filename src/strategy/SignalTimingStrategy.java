package strategy;

import models.Road;

/**
 * Abstract class representing a generic signal-timing strategy.
 * Defines the common contract and properties for all timing algorithms.
 */
public abstract class SignalTimingStrategy {

    // Common attribute for all strategies
    protected String strategyName;

    // Constructor
    public SignalTimingStrategy(String strategyName) {
        this.strategyName = strategyName;
    }

    // Getter for strategy name
    public String getStrategyName() {
        return strategyName;
    }

    // Abstract method: must be implemented by concrete subclasses
    public abstract int calculateGreenTime(Road road);
}
