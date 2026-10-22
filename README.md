# Traffic Management System Based on Vehicle Density

A Java Comprehensive Based Project (CBP) modeling an intelligent four-way intersection that dynamically assigns traffic signal green-light durations based on real-time vehicle density. Built with standard Eclipse Java and an interactive 2D Bird's-Eye-View Swing graphical interface.

---

## 🚦 Features

- **Object-Oriented Traffic Modeling**: Clean encapsulation of `Vehicle`, `Lane`, `Road`, and `TrafficSignal` entities.
- **Dynamic & Fixed Timing Strategies**:
  - **Fixed Timing**: Standard constant 30-second duration.
  - **Density-Based Timing**: Dynamically computed duration using $\min(60, \max(15, 10 + \text{totalVehicles} \times 5))$.
- **Non-Conflicting Signal Phases**:
  - Phase 1: North–South Green (East–West Red).
  - Phase 2: East–West Green (North–South Red).
  - Ensures conflicting directions are never green simultaneously.
- **Robust Exception Handling**:
  - `NumberFormatException` when parsing vehicle count inputs.
  - `ArithmeticException` when calculating lane averages on 0-lane roads.
  - `NoVehiclesException` (Custom checked exception) handling empty lanes without discarding the entire road.
- **Multithreading & Concurrency**:
  - Random number generation with parity-based square/cube calculation.
  - Decoupled Producer–Consumer traffic data stream simulation.
- **2D Bird's-Eye-View Swing GUI**:
  - Interactive window displaying North, South, East, and West two-way roads with central dividers.
  - Stationary vehicle rendering reflecting real vehicle counts.
  - Real-time traffic signal indicators and corner status cards.
  - Live controls to switch timing strategies and toggle signal phases.

---

## 🛠️ Project Structure

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
│   ├── Vehicle.java                    # Vehicle representation
│   ├── Lane.java                       # Lane containing vehicles
│   ├── Road.java                       # Two-way road representation
│   └── TrafficSignal.java              # Signal state (RED/GREEN) and duration
├── strategy/
│   ├── SignalTimingStrategy.java       # Abstract timing strategy
│   ├── FixedTimingStrategy.java        # Concrete 30s strategy
│   └── DensityBasedTimingStrategy.java # Concrete dynamic density strategy
├── threading/
│   └── TrafficThreading.java           # Multithreading & Producer-Consumer demos
└── module-info.java                    # Java module configuration (requires java.desktop)
```

---

## 🚀 How to Run in Eclipse

1. Open **Eclipse IDE**.
2. Select **File → Open Projects from File System...** and choose this project directory (`java_CBP`).
3. In **Package Explorer**, expand `java_CBP → src → Main` and open `MainApp.java`.
4. Right-click `MainApp.java` → **Run As → Java Application**.
   - The console will display the road summary.
   - The interactive 2D Swing GUI will open.

---

## 👨‍💻 Author

- B.Tech CSE (2nd Year)
