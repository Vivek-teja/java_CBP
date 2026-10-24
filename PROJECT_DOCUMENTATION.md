# Traffic Management System Based on Vehicle Density
## Comprehensive Based Project (CBP) Documentation

**Academic Level:** Second-Year B.Tech Computer Science & Engineering (CSE)  
**Development Environment:** Eclipse IDE (JavaSE-25 standard Java Project)  
**Repository:** [https://github.com/Vivek-teja/java_CBP](https://github.com/Vivek-teja/java_CBP)

---

## 1. Executive Summary & Project Objective

In traditional traffic management, traffic signals operate on **fixed-time cycles** (e.g., 30 seconds for every road), regardless of whether ten cars or zero cars are waiting. This causes unnecessary congestion on busy roads and empty green lights on vacant roads.

The **Traffic Management System Based on Vehicle Density** is a modular Java application that models a four-way intersection (**North, South, East, West**) and dynamically calculates green-light durations based on real-time vehicle counts waiting on each road.

### Key Highlights
- **Dynamic Timing**: High-density roads receive longer green lights (up to 60s); low-density roads receive minimum green lights (15s).
- **Conflict Prevention**: Intersecting roads (North–South vs. East–West) are coordinated into non-conflicting phases so opposing streams never turn green simultaneously.
- **2D Bird's-Eye-View Graphical Interface**: A custom Java Swing visualizer showing a realistic two-way road layout with central dividers, lane arrows, stop lines, stationary cars, and signal indicators.
- **Syllabus Concepts Demonstrated**: Abstract classes, inheritance, method overriding, runtime polymorphism, interfaces, checked & unchecked exception handling, and multithreading (Producer–Consumer and thread coordination).

---

## 2. Complete Implementation Timeline & Features

| Stage / Week | Focus Area | What Was Implemented |
| :--- | :--- | :--- |
| **Week 1–2** | **Core Data Models** | Encapsulated real-world entities: `Vehicle`, `Lane`, `Road`, and `TrafficSignal`. |
| **Week 3 (Part A)** | **Abstract Classes & Polymorphism** | Abstract class `SignalTimingStrategy` with concrete subclasses `FixedTimingStrategy` (30s) and `DensityBasedTimingStrategy` (15s–60s). |
| **Week 3 (Part B)** | **Interfaces** | `TrafficController` interface defining the operational contract, implemented by `IntersectionController`. |
| **Week 4** | **Exception Handling** | • `NumberFormatException`: Checked input parsing for vehicle counts.<br>• `ArithmeticException`: Handled division by zero when calculating lane averages on 0-lane roads.<br>• `NoVehiclesException`: Custom checked exception skipping green time for empty lanes without dropping the entire road. |
| **Week 5** | **Multithreading** | • `TrafficThreading.runRandomNumberDemo()`: Thread generating random numbers every 1s, computing squares for evens and cubes for odds (stops after 5).<br>• `TrafficThreading.runProducerConsumerDemo()`: Decoupled sensor data production from controller processing. |
| **Final Phase (CBP)** | **4-Way Intersection & Swing 2D GUI** | • 4 roads (North, South, East, West), each with 2 lanes.<br>• Phase controller ensuring mutual exclusivity.<br>• Custom Swing GUI (`IntersectionPanel` & `TrafficSimulationFrame`) with live strategy switching and phase toggles. |

---

## 3. Package & File Structure

```text
src/
├── Main/
│   └── MainApp.java                    # Entry point: prints summary and launches GUI
├── controller/
│   ├── TrafficController.java          # Interface defining operational contract
│   └── IntersectionController.java     # Concrete controller coordinating phases
├── exceptions/
│   └── NoVehiclesException.java        # Custom checked exception
├── gui/
│   ├── IntersectionPanel.java          # Custom JPanel rendering 2D bird's-eye view
│   └── TrafficSimulationFrame.java     # Swing JFrame window with interactive controls
├── models/
│   ├── Vehicle.java                    # Vehicle representation (ID, type)
│   ├── Lane.java                       # Lane containing an ArrayList of vehicles
│   ├── Road.java                       # Two-way road representation with lanes & signal
│   └── TrafficSignal.java              # Signal state (RED/GREEN) and duration
├── strategy/
│   ├── SignalTimingStrategy.java       # Abstract timing strategy
│   ├── FixedTimingStrategy.java        # Concrete 30s strategy
│   └── DensityBasedTimingStrategy.java # Concrete dynamic density strategy
├── threading/
│   └── TrafficThreading.java           # Multithreading & Producer-Consumer demos
└── module-info.java                    # Java module descriptor (requires java.desktop)
```

---

## 4. Class-by-Class Technical Breakdown

### A. Model Classes (`package models`)

#### 1. `Vehicle.java`
- **Fields**: `public int vehicleId;`, `public String type;`
- **Constructor**: `Vehicle(int vehicleId, String type)`
- **Role**: Represents an individual motor vehicle (Car, Bus, Bike, Truck) waiting at the junction.

#### 2. `Lane.java`
- **Fields**: `public int laneId;`, `public ArrayList<Vehicle> vehicles;`
- **Constructor**: `Lane(int laneId)` (initializes empty `ArrayList<Vehicle>`)
- **Role**: Models a single physical lane of traffic. Uses `ArrayList` to dynamically store vehicles.

#### 3. `Road.java`
- **Fields**: `public int roadId;`, `private String direction;`, `public ArrayList<Lane> lanes;`, `private TrafficSignal signal;`
- **Constructors**:
  - `Road(int roadId)`: Defaults direction name to `"Road " + roadId`.
  - `Road(int roadId, String direction)`: Overloaded constructor initializing direction and default RED signal.
- **Key Methods**:
  - `addLane(Lane lane)`: Appends a lane to the road.
  - `getTotalVehicles()`: Loops through all lanes and sums up `lane.vehicles.size()`.
  - `getAverageVehiclesPerLane()`: Computes `getTotalVehicles() / lanes.size()`. Throws `ArithmeticException` if `lanes.size() == 0`.

#### 4. `TrafficSignal.java`
- **Fields**: `private String color;`, `private int duration;`
- **Constructors**:
  - `TrafficSignal()`: Defaults to `"RED"`, `0`.
  - `TrafficSignal(String color, int duration)`: Parameterized constructor.
- **Methods**: Standard getters/setters (`getColor`, `setColor`, `getDuration`, `setDuration`) and `displaySignal()`.

---

### B. Strategy Pattern Classes (`package strategy`)

#### 1. `SignalTimingStrategy.java` (Abstract Class)
- **Field**: `protected String strategyName;`
- **Constructor**: `SignalTimingStrategy(String strategyName)`
- **Abstract Method**: `public abstract int calculateGreenTime(Road road);`
- **Concept**: Represents a template for timing algorithms. Cannot be instantiated directly.

#### 2. `FixedTimingStrategy.java` (Concrete Subclass)
- **Extends**: `SignalTimingStrategy`
- **Constructor**: Initializes name as `"Fixed green time"`, duration as `30` seconds.
- **Overridden Method**:
  ```java
  @Override
  public int calculateGreenTime(Road road) {
      return fixedDuration; // Always returns 30s regardless of vehicle count
  }
  ```

#### 3. `DensityBasedTimingStrategy.java` (Concrete Subclass)
- **Extends**: `SignalTimingStrategy`
- **Formula Implemented**:
  $$\text{GreenTime} = \min(60, \max(15, 10 + (\text{totalVehicles} \times 5)))$$
- **Overridden Method**:
  ```java
  @Override
  public int calculateGreenTime(Road road) {
      int totalVehicles = road.getTotalVehicles();
      int calculatedTime = 10 + (totalVehicles * 5);
      return Math.min(60, Math.max(15, calculatedTime));
  }
  ```
  - **Base Time**: 10 seconds.
  - **Time per Vehicle**: 5 seconds.
  - **Lower Bound**: Minimum 15 seconds (prevents lights from flashing green too briefly when 0 cars are present).
  - **Upper Bound**: Maximum 60 seconds (prevents starvation of waiting traffic on other roads during heavy congestion).

---

### C. Controller Architecture (`package controller`)

#### 1. `TrafficController.java` (Interface)
Defines the operational contract:
- `void controlTraffic(Road road, SignalTimingStrategy strategy);`
- `void checkLanes(Road road);`
- `void displayAverageVehicles(Road road);`
- `void applyPhase(String activeAxis, Road north, Road south, Road east, Road west, SignalTimingStrategy strategy);`
- `void displayStatus();`

#### 2. `IntersectionController.java` (Concrete Implementation)
- **Field**: `private String intersectionName;`
- **Key Methods**:
  - `controlTraffic(Road, SignalTimingStrategy)`: Calculates green time via strategy and assigns it to `road.getSignal()`.
  - `checkLanes(Road)`: Scans lanes; if a lane has 0 vehicles, throws and handles custom `NoVehiclesException`.
  - `displayAverageVehicles(Road)`: Handles `ArithmeticException` if lanes are zero.
  - `applyPhase(...)`: Coordinates signals into two non-conflicting phases:
    - **Phase 1 (`NORTH_SOUTH`)**: North & South are **GREEN**; East & West are **RED** (0s).
    - **Phase 2 (`EAST_WEST`)**: East & West are **GREEN**; North & South are **RED** (0s).

---

### D. Exception Handling (`package exceptions`)

#### `NoVehiclesException.java` (Custom Checked Exception)
- **Extends**: `java.lang.Exception`
- **Constructor**: `public NoVehiclesException(String message) { super(message); }`
- **Mechanism**:
  - Thrown when an individual lane has 0 vehicles waiting.
  - Caught per-lane inside the controller:
    ```java
    try {
        if (lane.vehicles.isEmpty()) {
            throw new NoVehiclesException("Lane " + lane.laneId + ": No vehicles waiting.");
        }
    } catch (NoVehiclesException e) {
        System.out.println(e.getMessage());
    }
    ```
  - **Critical Rule**: Catching it at the lane level allows the loop to continue. The road-level green time is still calculated for other occupied lanes.

---

### E. Multithreading & Concurrency (`package threading`)

#### `TrafficThreading.java`
Contains two standalone multithreading demonstrations:
1. **`runRandomNumberDemo()`**:
   - Spawns a worker thread using `Thread` and a lambda `Runnable`.
   - Generates a random number every 1000ms (`Thread.sleep(1000)`).
   - If even, prints its square; if odd, prints its cube.
   - Terminates cleanly after 5 numbers; main thread calls `join()`.
2. **`runProducerConsumerDemo()`**:
   - Demonstrates the Producer–Consumer pattern:
     - **Producer Thread**: Represents a traffic sensor generating a vehicle count (`5`).
     - **Consumer Thread**: Represents the traffic controller processing that vehicle count.
   - Uses `thread.join()` to guarantee the producer produces data before the consumer reads it.
   - Code Comment: `// The Producer-Consumer pattern separates data production from data processing.`

---

### F. Graphical User Interface (`package gui`)

#### 1. `IntersectionPanel.java` (Custom Drawing)
- Extends `JPanel` and overrides `paintComponent(Graphics g)` using `Graphics2D`.
- **Road Layout**:
  - Renders a **two-way road system** (North, South, East, West).
  - Each road has **one incoming lane** and **one outgoing lane**, separated by a solid double-yellow central divider.
- **Pavement Markings**:
  - Solid white stop lines across the incoming lanes.
  - Pavement directional arrows ($\downarrow$, $\uparrow$, $\leftarrow$, $\rightarrow$) indicating the flow of traffic.
- **Vehicles**:
  - Drawn as small, colored rounded rectangles with front and rear windshields.
  - Positioned inside the incoming approach lane, queuing up before the stop line.
  - Exactly reflects the vehicle counts of the model:
    - **North**: 5 vehicles
    - **South**: 0 vehicles
    - **East**: 15 vehicles
    - **West**: 4 vehicles
- **Signals**:
  - Miniature signal housings at the curb of each incoming lane with glowing **Green** or **Red** lamps.
- **Live Info Cards**:
  - Semi-transparent cards in each corner displaying Road Name, Waiting Vehicles, and Signal State (`GREEN (35s)` or `RED (Stopped)`).

#### 2. `TrafficSimulationFrame.java` (Main Window)
- Extends `JFrame`.
- **Controls**:
  - `JComboBox` to switch between **Density-Based Timing** and **Fixed Timing (30s)** in real time.
  - **"Toggle Signal Phase (N-S / E-W)"** button to toggle green phases.
  - **"Refresh Timings"** button.
- **Status Bar**: Displays the currently active phase and strategy at the bottom.
- Zero timing formulas are duplicated; queries `SignalTimingStrategy` directly.

---

## 5. Execution Output & Verification

### A. Console Output (`MainApp.java`)

When running `MainApp.java`, the console prints this concise, natural summary:

```text
Traffic Management System

North Road:
  Lane 1: 3 vehicles
  Lane 2: 2 vehicles
  Total vehicles: 5
  Fixed timing: 30 seconds
  Assigned green time (Density-based): 35 seconds

South Road:
  Lane 1: 0 vehicles
  Lane 2: 0 vehicles
  Total vehicles: 0
  Fixed timing: 30 seconds
  Assigned green time (Density-based): 15 seconds

East Road:
  Lane 1: 7 vehicles
  Lane 2: 8 vehicles
  Total vehicles: 15
  Fixed timing: 30 seconds
  Assigned green time (Density-based): 60 seconds

West Road:
  Lane 1: 4 vehicles
  Lane 2: 0 vehicles
  Total vehicles: 4
  Fixed timing: 30 seconds
  Assigned green time (Density-based): 30 seconds
```

### B. Timing Comparison Table

| Road Direction | Waiting Vehicles | Fixed Timing Strategy | Density-Based Strategy | Calculation Breakdown |
| :--- | :---: | :---: | :---: | :--- |
| **North Road** | 5 | 30 seconds | **35 seconds** | $10 + (5 \times 5) = 35\text{s}$ |
| **South Road** | 0 | 30 seconds | **15 seconds** | $10 + (0 \times 5) = 10 \rightarrow \max(15, 10) = 15\text{s}$ (Lower Bound) |
| **East Road** | 15 | 30 seconds | **60 seconds** | $10 + (15 \times 5) = 85 \rightarrow \min(60, 85) = 60\text{s}$ (Upper Bound) |
| **West Road** | 4 | 30 seconds | **30 seconds** | $10 + (4 \times 5) = 30\text{s}$ |

---

## 6. How to Run the Application in Eclipse

1. Open **Eclipse IDE**.
2. Go to **File → Open Projects from File System...**, browse to `/Users/mabbu/eclipse-workspace/java_CBP`, and click **Finish**.
3. In **Package Explorer**, expand `java_CBP → src → Main`.
4. Right-click [`MainApp.java`](file:///Users/mabbu/eclipse-workspace/java_CBP/src/Main/MainApp.java) $\rightarrow$ **Run As → Java Application**.
   - The road summary prints in the Eclipse Console.
   - The interactive Swing Bird's-Eye-View window opens automatically.
5. In the Swing window:
   - Change the dropdown to **Fixed Timing (30s)** and observe the green times update.
   - Click **Toggle Signal Phase** to switch green lights from North–South to East–West.

---

## 7. Viva & Lab Observation Reference Guide

### Q1: Why did you use an Abstract Class for `SignalTimingStrategy` but an Interface for `TrafficController`?
> **Answer:**
> - `SignalTimingStrategy` is an **abstract class** because all timing algorithms share common state (`protected String strategyName`) and a constructor, while enforcing an abstract method `calculateGreenTime(Road)`. They have an *"is-a"* relationship (*FixedTimingStrategy is a SignalTimingStrategy*).
> - `TrafficController` is an **interface** because it represents a pure contract of operational capabilities (`controlTraffic`, `applyPhase`) without any common state. It has a *"can-do"* relationship (*IntersectionController can control traffic*).

### Q2: How does your project demonstrate Runtime Polymorphism?
> **Answer:**
> We declare a superclass reference variable:
> ```java
> SignalTimingStrategy activeStrategy;
> ```
> At runtime, it can hold either a `FixedTimingStrategy` or a `DensityBasedTimingStrategy` object. When calling:
> ```java
> int greenTime = activeStrategy.calculateGreenTime(road);
> ```
> Java's JVM uses **Dynamic Method Dispatch** to execute the subclass's overridden calculation method at runtime based on the actual object assigned.

### Q3: What exceptions are handled in your project?
> **Answer:**
> 1. **`NumberFormatException`**: An unchecked exception caught when parsing vehicle count inputs from strings (e.g., `"eight"`).
> 2. **`ArithmeticException`**: An unchecked runtime exception that occurs when performing integer division `totalVehicles / lanes.size()` if the number of lanes is zero.
> 3. **`NoVehiclesException`**: A custom checked exception extending `Exception`. It is thrown when an individual lane has zero vehicles waiting, allowing the system to skip green allocation for that lane without discarding the rest of the road.

### Q4: How does the Producer–Consumer pattern apply to traffic management?
> **Answer:**
> It decouples **data production** from **data processing**:
> - The **Producer** represents traffic road sensors or cameras generating vehicle counts.
> - The **Consumer** represents the signal controller receiving those counts to calculate green-light durations.
> This separation ensures that sensor input delays or hardware changes do not crash or stall signal-timing calculations.

### Q5: How do you prevent conflicting directions from turning green at the same time?
> **Answer:**
> In [`IntersectionController.java`](file:///Users/mabbu/eclipse-workspace/java_CBP/src/controller/IntersectionController.java), the `applyPhase()` method groups roads into orthogonal axes:
> - In **Phase 1**, North and South are set to `GREEN`, while East and West are forced to `RED` with duration 0.
> - In **Phase 2**, East and West are set to `GREEN`, while North and South are forced to `RED` with duration 0.
> Conflicting perpendicular streams are never green simultaneously.
