package strategy;

import models.Road;

/**
 * Concrete subclass of SignalTimingStrategy.
 * Implements a traditional fixed-duration signal timing strategy.
 */
public class FixedTimingStrategy extends SignalTimingStrategy {

    private int fixedDuration;

    // Default constructor assigns 30 seconds as the fixed duration
    public FixedTimingStrategy() {
        super("Fixed green time");
        this.fixedDuration = 30;
    }

    // Parameterized constructor allows custom fixed duration if needed
    public FixedTimingStrategy(int fixedDuration) {
        super("Fixed green time");
        this.fixedDuration = fixedDuration;
    }

    public int getFixedDuration() {
        return fixedDuration;
    }

    // Overridden method: returns the fixed duration regardless of vehicle density
    @Override
    public int calculateGreenTime(Road road) {
        return fixedDuration;
    }
}
