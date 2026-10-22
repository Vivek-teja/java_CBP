package Main;

import controller.IntersectionController;
import controller.TrafficController;
import gui.TrafficSimulationFrame;
import javax.swing.SwingUtilities;
import models.Lane;
import models.Road;
import models.Vehicle;
import strategy.DensityBasedTimingStrategy;
import strategy.FixedTimingStrategy;
import strategy.SignalTimingStrategy;

public class MainApp {

    // Safely parses a string input into an integer; handles NumberFormatException
    public static int parseVehicleCount(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            System.out.println("Invalid vehicle count: " + input);
            System.out.println("Please enter a valid integer.\n");
            return 0;
        }
    }

    // Helper method to populate a lane with vehicles
    public static Lane createPopulatedLane(int laneId, int vehicleCount) {
        Lane lane = new Lane(laneId);
        for (int i = 1; i <= vehicleCount; i++) {
            lane.vehicles.add(new Vehicle(laneId * 100 + i, "Car"));
        }
        return lane;
    }

    // Helper method to create and populate the 4-way intersection (North, South, East, West)
    public static Road[] createIntersectionRoads() {
        Road north = new Road(1, "North");
        north.addLane(createPopulatedLane(1, 3));
        north.addLane(createPopulatedLane(2, 2)); // Total = 5 vehicles

        Road south = new Road(2, "South");
        south.addLane(new Lane(3));
        south.addLane(new Lane(4)); // Total = 0 vehicles

        Road east = new Road(3, "East");
        east.addLane(createPopulatedLane(5, 7));
        east.addLane(createPopulatedLane(6, 8)); // Total = 15 vehicles

        Road west = new Road(4, "West");
        west.addLane(createPopulatedLane(7, 4));
        west.addLane(new Lane(8)); // Total = 4 vehicles

        return new Road[] { north, south, east, west };
    }

    public static void main(String[] args) {

        System.out.println("Traffic Management System\n");

        TrafficController controller = new IntersectionController("City Center 4-Way Junction");
        SignalTimingStrategy fixedStrategy = new FixedTimingStrategy();
        SignalTimingStrategy densityStrategy = new DensityBasedTimingStrategy();

        // Initialize 4-way intersection (North, South, East, West with 2 lanes each)
        Road[] intersectionRoads = createIntersectionRoads();

        // Print road direction, lane counts, total vehicles, and assigned green-light duration
        for (Road road : intersectionRoads) {
            System.out.println(road.getDirection() + " Road:");
            for (int i = 0; i < road.lanes.size(); i++) {
                System.out.println("  Lane " + (i + 1) + ": " + road.lanes.get(i).vehicles.size() + " vehicles");
            }
            System.out.println("  Total vehicles: " + road.getTotalVehicles());
            System.out.println("  Fixed timing: " + fixedStrategy.calculateGreenTime(road) + " seconds");

            // Selected active operational strategy is Density-Based
            controller.controlTraffic(road, densityStrategy);
            System.out.println("  Assigned green time (Density-based): " + road.getSignal().getDuration() + " seconds");
            System.out.println();
        }

        // Launch the graphical Swing Bird's-Eye View
        SwingUtilities.invokeLater(() -> {
            TrafficSimulationFrame frame = new TrafficSimulationFrame(
                intersectionRoads[0], intersectionRoads[1], intersectionRoads[2], intersectionRoads[3]
            );
            frame.setVisible(true);
        });
    }
}
