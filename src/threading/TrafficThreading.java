package threading;

import java.util.Objects;
import java.util.Random;
import models.TrafficSimulationModel;
import models.VehicleArrivalEvent;

/**
 * Multithreading and Producer-Consumer coordination for the Traffic Management System.
 * Separates dynamic vehicle arrival generation (background producer) from simulation
 * physics and rendering (Swing Event Dispatch Thread).
 */
public class TrafficThreading {

    // =========================================================================
    // Phase 3: Background Dynamic Vehicle Arrival Producer
    // =========================================================================

    /**
     * Named daemon producer thread ("Traffic-Vehicle-Producer") that generates
     * VehicleArrivalEvents at uniform random intervals (1,000–3,000 ms) and submits
     * them to the model's bounded event queue.
     *
     * Invariant: The producer NEVER touches Road, Lane, Vehicle, TrafficSignal, or Swing
     * components. All mutation of simulation state occurs strictly on the EDT during tick().
     */
    public static class TrafficEventProducer implements Runnable {

        public static final long DEFAULT_MIN_INTERVAL_MS = 1000L;
        public static final long DEFAULT_MAX_INTERVAL_MS = 3000L;

        private final TrafficSimulationModel model;
        private final Random random;
        private final long minIntervalMs;
        private final long maxIntervalMs;

        private volatile boolean running = false;
        private Thread workerThread;

        public TrafficEventProducer(TrafficSimulationModel model) {
            this(model, new Random(), DEFAULT_MIN_INTERVAL_MS, DEFAULT_MAX_INTERVAL_MS);
        }

        public TrafficEventProducer(TrafficSimulationModel model, long seed) {
            this(model, new Random(seed), DEFAULT_MIN_INTERVAL_MS, DEFAULT_MAX_INTERVAL_MS);
        }

        public TrafficEventProducer(TrafficSimulationModel model, Random random, long minIntervalMs, long maxIntervalMs) {
            this.model = Objects.requireNonNull(model, "TrafficSimulationModel cannot be null");
            this.random = Objects.requireNonNull(random, "Random source cannot be null");
            if (minIntervalMs < 0 || maxIntervalMs < minIntervalMs) {
                throw new IllegalArgumentException("Invalid interval range: [" + minIntervalMs + ".." + maxIntervalMs + "]");
            }
            this.minIntervalMs = minIntervalMs;
            this.maxIntervalMs = maxIntervalMs;
        }

        /**
         * Starts the background producer daemon thread if not already active.
         */
        public synchronized void start() {
            if (running && workerThread != null && workerThread.isAlive()) {
                return;
            }
            running = true;
            workerThread = new Thread(this, "Traffic-Vehicle-Producer");
            workerThread.setDaemon(true);
            workerThread.start();
        }

        /**
         * Gracefully and promptly stops the producer thread by signaling termination,
         * interrupting any active sleep/wait, and joining with a bounded timeout.
         *
         * @param timeoutMs maximum milliseconds to wait for thread termination
         * @return true if thread stopped cleanly; false if thread failed to exit within timeout
         */
        public synchronized boolean stop(long timeoutMs) {
            running = false;
            if (workerThread != null) {
                workerThread.interrupt();
                try {
                    workerThread.join(timeoutMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return !workerThread.isAlive();
            }
            return true;
        }

        /**
         * Stops the producer with default 1,500ms bounded timeout.
         */
        public void stop() {
            stop(1500L);
        }

        public boolean isRunning() {
            return running && workerThread != null && workerThread.isAlive();
        }

        public Thread getWorkerThread() {
            return workerThread;
        }

        public TrafficSimulationModel getModel() {
            return model;
        }

        public long getMinIntervalMs() {
            return minIntervalMs;
        }

        public long getMaxIntervalMs() {
            return maxIntervalMs;
        }

        /**
         * Synchronous helper for deterministic test execution:
         * Generates and enqueues a single arrival event without waiting.
         *
         * @return the generated VehicleArrivalEvent
         */
        public VehicleArrivalEvent produceSingleArrival() {
            int targetRoadId = random.nextInt(4) + 1;
            long seqId = model.nextSequenceId();
            long genToken = model.getCurrentGenerationToken();
            VehicleArrivalEvent event = new VehicleArrivalEvent(seqId, targetRoadId, "Car", genToken);
            model.enqueueArrival(event);
            return event;
        }

        /**
         * Synchronous helper for deterministic test execution on a targeted road approach:
         *
         * @param targetRoadId road ID (1=North, 2=South, 3=East, 4=West)
         * @return the generated VehicleArrivalEvent
         */
        public VehicleArrivalEvent produceSingleArrival(int targetRoadId) {
            long seqId = model.nextSequenceId();
            long genToken = model.getCurrentGenerationToken();
            VehicleArrivalEvent event = new VehicleArrivalEvent(seqId, targetRoadId, "Car", genToken);
            model.enqueueArrival(event);
            return event;
        }

        @Override
        public void run() {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    // Sample arrival cadence uniformly from [minIntervalMs..maxIntervalMs]
                    long interval;
                    if (maxIntervalMs <= minIntervalMs) {
                        interval = minIntervalMs;
                    } else {
                        long range = (maxIntervalMs - minIntervalMs) + 1;
                        interval = minIntervalMs + (Math.abs(random.nextLong()) % range);
                    }

                    if (interval > 0) {
                        Thread.sleep(interval);
                    }

                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    // Approach selection: uniformly North(1), South(2), East(3), West(4)
                    int targetRoadId = random.nextInt(4) + 1;
                    long seqId = model.nextSequenceId();
                    long genToken = model.getCurrentGenerationToken();

                    VehicleArrivalEvent event = new VehicleArrivalEvent(seqId, targetRoadId, "Car", genToken);
                    model.enqueueArrival(event);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    // =========================================================================
    // Legacy Educational Demos (Preserved for compatibility)
    // =========================================================================

    // Part A: Thread generating 5 random numbers, printing square (even) or cube (odd)
    public static void runRandomNumberDemo() {
        Thread thread = new Thread(() -> {
            Random random = new Random();
            for (int i = 0; i < 5; i++) {
                int num = random.nextInt(10) + 1;
                System.out.println("Random Number: " + num);

                if (num % 2 == 0) {
                    System.out.println("Square: " + (num * num));
                } else {
                    System.out.println("Cube: " + (num * num * num));
                }

                try {
                    Thread.sleep(1000); // 1-second delay
                } catch (InterruptedException e) {
                    System.out.println("Thread interrupted.");
                }
            }
        });

        thread.start();
        try {
            thread.join(); // Wait for thread to finish
        } catch (InterruptedException e) {
            System.out.println("Thread interrupted.");
        }
    }

    // Part B: Simple Producer-Consumer demonstrating data generation and processing
    public static void runProducerConsumerDemo() {
        int vehicleCount = 5;

        // Producer: represents a traffic sensor generating vehicle count
        Thread producer = new Thread(() -> 
            System.out.println("Producer: Generated vehicle count " + vehicleCount)
        );

        // Consumer: represents a traffic component processing the vehicle count
        Thread consumer = new Thread(() -> 
            System.out.println("Consumer: Processed vehicle count " + vehicleCount)
        );

        producer.start();
        try {
            producer.join(); // Producer finishes first
            consumer.start();
            consumer.join(); // Consumer processes the data
        } catch (InterruptedException e) {
            System.out.println("Thread interrupted.");
        }
    }
}
