package threading;

import java.util.Random;

/**
 * Basic Week 5 Multithreading demonstration for Traffic Management System.
 * The Producer-Consumer pattern separates data production from data processing.
 */
public class TrafficThreading {

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
