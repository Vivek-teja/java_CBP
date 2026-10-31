package gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import models.Lane;
import models.Road;
import models.Vehicle;

/**
 * Custom Swing JPanel that renders a bird's-eye view of a 4-way intersection
 * using a simple two-way road layout (one incoming lane, one outgoing lane,
 * and a central divider between them).
 * Dynamically displays vehicle positions from the simulation model.
 *
 * Performance-optimized: Reuses all static drawing resources (Strokes, Colors, Fonts),
 * eliminates per-frame collections allocation, and supports smooth visual interpolation
 * between simulation ticks without altering authoritative model state.
 */
@SuppressWarnings("serial")
public class IntersectionPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private Road northRoad;
    private Road southRoad;
    private Road eastRoad;
    private Road westRoad;

    // Palette (Pre-allocated static final Colors)
    private static final Color GRASS_COLOR = new Color(38, 120, 58);
    private static final Color ROAD_COLOR = new Color(50, 53, 58);
    private static final Color CENTER_BOX_COLOR = new Color(58, 62, 68);
    private static final Color YELLOW_DIVIDER = new Color(245, 195, 35);
    private static final Color ARROW_COLOR = new Color(225, 230, 235, 170);
    private static final Color STOP_LINE_COLOR = new Color(250, 250, 250);
    private static final Color HOUSING_COLOR = new Color(25, 25, 25);
    private static final Color CARD_BG = new Color(20, 24, 30, 215);
    private static final Color CARD_BORDER_COLOR = new Color(180, 190, 200, 100);
    private static final Color CARD_DETAILS_COLOR = new Color(220, 225, 230);
    private static final Color WINDSHIELD_COLOR = new Color(220, 240, 255, 190);

    // Signal Lamp Colors (Active / Dim)
    private static final Color SIGNAL_RED_ON = new Color(245, 30, 30);
    private static final Color SIGNAL_RED_OFF = new Color(60, 15, 15);
    private static final Color SIGNAL_YELLOW_ON = new Color(250, 200, 25);
    private static final Color SIGNAL_YELLOW_OFF = new Color(60, 50, 10);
    private static final Color SIGNAL_GREEN_ON = new Color(30, 240, 60);
    private static final Color SIGNAL_GREEN_OFF = new Color(10, 60, 20);

    // Status Dot Colors
    private static final Color DOT_GREEN = new Color(40, 240, 60);
    private static final Color DOT_YELLOW = new Color(250, 200, 30);
    private static final Color DOT_RED = new Color(245, 50, 50);

    // Card Text Colors
    private static final Color TEXT_GREEN = new Color(80, 230, 120);
    private static final Color TEXT_YELLOW = new Color(250, 210, 60);
    private static final Color TEXT_RED = new Color(250, 100, 100);

    // Pre-allocated Strokes
    private static final BasicStroke STROKE_DIVIDER = new BasicStroke(2.0f);
    private static final BasicStroke STROKE_LANE_DIVIDER = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[] { 10.0f, 10.0f }, 0.0f);
    private static final BasicStroke STROKE_ARROW = new BasicStroke(2.5f);
    private static final BasicStroke STROKE_STOP_LINE = new BasicStroke(3.5f);
    private static final BasicStroke STROKE_THIN = new BasicStroke(1.0f);
    private static final Color LANE_DIVIDER_COLOR = new Color(220, 225, 230, 160);

    // Pre-allocated Fonts
    private static final Font FONT_CARD_TITLE = new Font("SansSerif", Font.BOLD, 12);
    private static final Font FONT_CARD_DETAILS = new Font("SansSerif", Font.PLAIN, 11);
    private static final Font FONT_CARD_STATUS = new Font("SansSerif", Font.BOLD, 11);

    // Car Colors for variety
    private static final Color[] CAR_COLORS = {
        new Color(40, 120, 220),  // Blue
        new Color(230, 75, 60),   // Red
        new Color(240, 180, 40),  // Yellow
        new Color(45, 180, 100),  // Green
        new Color(170, 90, 210),  // Purple
        new Color(220, 110, 40)   // Orange
    };

    // Visual animation interpolation state (EDT only)
    private double interpolationAlpha = 1.0;
    private final transient Map<Integer, Double> prevDistances = new HashMap<>();

    public interface VehicleAddListener {
        void onVehicleAddRequested(int roadId);
    }
    private VehicleAddListener vehicleAddListener;

    public void setVehicleAddListener(VehicleAddListener listener) {
        this.vehicleAddListener = listener;
    }

    public IntersectionPanel(Road north, Road south, Road east, Road west) {
        this.northRoad = north;
        this.southRoad = south;
        this.eastRoad = east;
        this.westRoad = west;
        setBackground(GRASS_COLOR);

        // Click on roads or corner info cards to dynamically add vehicles
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                handleMouseClick(e.getX(), e.getY());
            }
        });
    }

    private void handleMouseClick(int mx, int my) {
        if (vehicleAddListener == null) return;
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        int halfRoad = 60;

        // 1. Check corner cards
        if (mx >= 15 && mx <= 215 && my >= 15 && my <= 95) {
            vehicleAddListener.onVehicleAddRequested(1); // North card
            return;
        }
        if (mx >= (w - 215) && mx <= (w - 15) && my >= 15 && my <= 95) {
            vehicleAddListener.onVehicleAddRequested(3); // East card
            return;
        }
        if (mx >= 15 && mx <= 215 && my >= (h - 95) && my <= (h - 15)) {
            vehicleAddListener.onVehicleAddRequested(4); // West card
            return;
        }
        if (mx >= (w - 215) && mx <= (w - 15) && my >= (h - 95) && my <= (h - 15)) {
            vehicleAddListener.onVehicleAddRequested(2); // South card
            return;
        }

        // 2. Check road approaches
        // North Road approach (top vertical)
        if (mx >= (cx - halfRoad) && mx <= (cx + halfRoad) && my >= 0 && my <= (cy - halfRoad)) {
            vehicleAddListener.onVehicleAddRequested(1);
            return;
        }
        // South Road approach (bottom vertical)
        if (mx >= (cx - halfRoad) && mx <= (cx + halfRoad) && my >= (cy + halfRoad) && my <= h) {
            vehicleAddListener.onVehicleAddRequested(2);
            return;
        }
        // East Road approach (right horizontal)
        if (mx >= (cx + halfRoad) && mx <= w && my >= (cy - halfRoad) && my <= (cy + halfRoad)) {
            vehicleAddListener.onVehicleAddRequested(3);
            return;
        }
        // West Road approach (left horizontal)
        if (mx >= 0 && mx <= (cx - halfRoad) && my >= (cy - halfRoad) && my <= (cy + halfRoad)) {
            vehicleAddListener.onVehicleAddRequested(4);
            return;
        }
    }

    public void updateRoads(Road north, Road south, Road east, Road west) {
        this.northRoad = north;
        this.southRoad = south;
        this.eastRoad = east;
        this.westRoad = west;
        repaint();
    }

    /**
     * Sets the visual interpolation fraction between ticks [0.0 .. 1.0].
     * Affects drawing only; never modifies authoritative simulation state.
     */
    public void setInterpolationAlpha(double alpha) {
        this.interpolationAlpha = Math.max(0.0, Math.min(1.0, alpha));
    }

    /**
     * Snapshots vehicle positions immediately before a model simulation tick.
     * Used by the rendering loop to interpolate movement smoothly.
     */
    public void snapshotPreviousPositions() {
        prevDistances.clear();
        snapshotRoad(northRoad);
        snapshotRoad(southRoad);
        snapshotRoad(eastRoad);
        snapshotRoad(westRoad);
    }

    private void snapshotRoad(Road road) {
        if (road == null || road.lanes == null) return;
        for (int li = 0; li < road.lanes.size(); li++) {
            Lane lane = road.lanes.get(li);
            if (lane == null || lane.vehicles == null) continue;
            for (int vi = 0; vi < lane.vehicles.size(); vi++) {
                Vehicle v = lane.vehicles.get(vi);
                if (v != null) {
                    prevDistances.put(v.vehicleId, v.getDistance());
                }
            }
        }
    }

    private double getRenderDistance(Vehicle v) {
        double currD = v.getDistance();
        Double prevD = prevDistances.get(v.vehicleId);
        if (prevD == null || prevD > currD) {
            return currD;
        }
        return prevD + interpolationAlpha * (currD - prevD);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;

        int roadWidth = 120; // 2 lanes of 60px each (1 incoming, 1 outgoing)
        int halfRoad = roadWidth / 2;

        // 1. Draw Asphalt Roads
        g2.setColor(ROAD_COLOR);
        // Vertical Road (North - South)
        g2.fillRect(cx - halfRoad, 0, roadWidth, h);
        // Horizontal Road (East - West)
        g2.fillRect(0, cy - halfRoad, w, roadWidth);

        // 2. Center Intersection Box
        g2.setColor(CENTER_BOX_COLOR);
        g2.fillRect(cx - halfRoad, cy - halfRoad, roadWidth, roadWidth);

        // 3. Central Dividers (Double Yellow Lines separating the two directions)
        drawCentralDividers(g2, cx, cy, w, h, halfRoad);

        // 3b. Lane Dividers (Dashed white lines separating parallel lanes)
        drawLaneDividers(g2, cx, cy, w, h, halfRoad);

        // 4. Directional Lane Arrows (Incoming & Outgoing)
        drawPavementArrows(g2, cx, cy, halfRoad);

        // 5. Stop Lines (across incoming approach lanes only)
        drawStopLines(g2, cx, cy, halfRoad);

        // 6. Draw Simulated Vehicles along each road approach and intersection
        drawVehicles(g2, cx, cy, halfRoad);

        // 7. Draw Traffic Signals at Stop Lines
        drawTrafficSignals(g2, cx, cy, halfRoad);

        // 8. Draw Live Info Cards in the 4 Corners
        drawInfoCards(g2, w, h);
    }

    /**
     * Draws double yellow central dividers separating incoming and outgoing lanes.
     */
    private void drawCentralDividers(Graphics2D g2, int cx, int cy, int w, int h, int halfRoad) {
        g2.setColor(YELLOW_DIVIDER);
        g2.setStroke(STROKE_DIVIDER);

        // North Road Central Divider
        g2.drawLine(cx - 2, 0, cx - 2, cy - halfRoad);
        g2.drawLine(cx + 2, 0, cx + 2, cy - halfRoad);

        // South Road Central Divider
        g2.drawLine(cx - 2, cy + halfRoad, cx - 2, h);
        g2.drawLine(cx + 2, cy + halfRoad, cx + 2, h);

        // West Road Central Divider
        g2.drawLine(0, cy - 2, cx - halfRoad, cy - 2);
        g2.drawLine(0, cy + 2, cx - halfRoad, cy + 2);

        // East Road Central Divider
        g2.drawLine(cx + halfRoad, cy - 2, w, cy - 2);
        g2.drawLine(cx + halfRoad, cy + 2, w, cy + 2);
    }

    /**
     * Draws dashed white lane dividers separating parallel lanes in the same direction.
     */
    private void drawLaneDividers(Graphics2D g2, int cx, int cy, int w, int h, int halfRoad) {
        g2.setColor(LANE_DIVIDER_COLOR);
        g2.setStroke(STROKE_LANE_DIVIDER);

        // North Road: divider between Lane 1 and Lane 2 at x = cx - 30
        if (northRoad != null && northRoad.lanes != null && northRoad.lanes.size() > 1) {
            g2.drawLine(cx - 30, 0, cx - 30, cy - halfRoad);
            g2.drawLine(cx - 30, cy + halfRoad, cx - 30, h);
        }

        // South Road: divider between Lane 1 and Lane 2 at x = cx + 30
        if (southRoad != null && southRoad.lanes != null && southRoad.lanes.size() > 1) {
            g2.drawLine(cx + 30, cy + halfRoad, cx + 30, h);
            g2.drawLine(cx + 30, 0, cx + 30, cy - halfRoad);
        }

        // West Road: divider between Lane 1 and Lane 2 at y = cy + 30
        if (westRoad != null && westRoad.lanes != null && westRoad.lanes.size() > 1) {
            g2.drawLine(0, cy + 30, cx - halfRoad, cy + 30);
            g2.drawLine(cx + halfRoad, cy + 30, w, cy + 30);
        }

        // East Road: divider between Lane 1 and Lane 2 at y = cy - 30
        if (eastRoad != null && eastRoad.lanes != null && eastRoad.lanes.size() > 1) {
            g2.drawLine(cx + halfRoad, cy - 30, w, cy - 30);
            g2.drawLine(0, cy - 30, cx - halfRoad, cy - 30);
        }
    }

    /**
     * Draws simple pavement arrows indicating traffic direction in each lane:
     * Incoming lanes point toward intersection; Outgoing lanes point away.
     */
    private void drawPavementArrows(Graphics2D g2, int cx, int cy, int halfRoad) {
        g2.setColor(ARROW_COLOR);

        // North: Incoming is West side (heading South v)
        if (northRoad != null && northRoad.lanes != null && northRoad.lanes.size() > 1) {
            drawArrow(g2, cx - 45, cy - halfRoad - 50, 0, 1);
            drawArrow(g2, cx - 15, cy - halfRoad - 50, 0, 1);
        } else {
            drawArrow(g2, cx - 30, cy - halfRoad - 50, 0, 1);
        }
        drawArrow(g2, cx + 30, cy - halfRoad - 50, 0, -1);

        // South: Incoming is East side (heading North ^)
        if (southRoad != null && southRoad.lanes != null && southRoad.lanes.size() > 1) {
            drawArrow(g2, cx + 45, cy + halfRoad + 50, 0, -1);
            drawArrow(g2, cx + 15, cy + halfRoad + 50, 0, -1);
        } else {
            drawArrow(g2, cx + 30, cy + halfRoad + 50, 0, -1);
        }
        drawArrow(g2, cx - 30, cy + halfRoad + 50, 0, 1);

        // West: Incoming is South side (heading East >)
        if (westRoad != null && westRoad.lanes != null && westRoad.lanes.size() > 1) {
            drawArrow(g2, cx - halfRoad - 50, cy + 45, 1, 0);
            drawArrow(g2, cx - halfRoad - 50, cy + 15, 1, 0);
        } else {
            drawArrow(g2, cx - halfRoad - 50, cy + 30, 1, 0);
        }
        drawArrow(g2, cx - halfRoad - 50, cy - 30, -1, 0);

        // East: Incoming is North side (heading West <)
        if (eastRoad != null && eastRoad.lanes != null && eastRoad.lanes.size() > 1) {
            drawArrow(g2, cx + halfRoad + 50, cy - 45, -1, 0);
            drawArrow(g2, cx + halfRoad + 50, cy - 15, -1, 0);
        } else {
            drawArrow(g2, cx + halfRoad + 50, cy - 30, -1, 0);
        }
        drawArrow(g2, cx + halfRoad + 50, cy + 30, 1, 0);
    }

    private void drawArrow(Graphics2D g2, int x, int y, int dx, int dy) {
        g2.setStroke(STROKE_ARROW);
        int len = 14;
        int x2 = x + dx * len;
        int y2 = y + dy * len;
        g2.drawLine(x, y, x2, y2);

        // Arrowhead rendered with coordinates without heap Polygon allocation
        if (dx == 1) { // East
            int[] xp = { x2, x2 - 6, x2 - 6 };
            int[] yp = { y2, y2 - 4, y2 + 4 };
            g2.fillPolygon(xp, yp, 3);
        } else if (dx == -1) { // West
            int[] xp = { x2, x2 + 6, x2 + 6 };
            int[] yp = { y2, y2 - 4, y2 + 4 };
            g2.fillPolygon(xp, yp, 3);
        } else if (dy == 1) { // South
            int[] xp = { x2, x2 - 4, x2 + 4 };
            int[] yp = { y2, y2 - 6, y2 - 6 };
            g2.fillPolygon(xp, yp, 3);
        } else if (dy == -1) { // North
            int[] xp = { x2, x2 - 4, x2 + 4 };
            int[] yp = { y2, y2 + 6, y2 + 6 };
            g2.fillPolygon(xp, yp, 3);
        }
    }

    /**
     * Stop lines drawn across the incoming lane only.
     */
    private void drawStopLines(Graphics2D g2, int cx, int cy, int halfRoad) {
        g2.setColor(STOP_LINE_COLOR);
        g2.setStroke(STROKE_STOP_LINE);

        // North incoming stop line (left lane, heading South)
        g2.drawLine(cx - halfRoad, cy - halfRoad, cx, cy - halfRoad);

        // South incoming stop line (right lane, heading North)
        g2.drawLine(cx, cy + halfRoad, cx + halfRoad, cy + halfRoad);

        // West incoming stop line (bottom lane, heading East)
        g2.drawLine(cx - halfRoad, cy, cx - halfRoad, cy + halfRoad);

        // East incoming stop line (top lane, heading West)
        g2.drawLine(cx + halfRoad, cy - halfRoad, cx + halfRoad, cy);
    }

    /**
     * Draws vehicles according to their dynamic simulation positions.
     * Iterates lanes directly to eliminate intermediate list allocations.
     */
    private void drawVehicles(Graphics2D g2, int cx, int cy, int halfRoad) {
        int carW = 20;
        int carL = 26;

        // North Road: Incoming on West side (heading South v)
        if (northRoad != null && northRoad.lanes != null) {
            int numLanes = northRoad.lanes.size();
            for (int li = 0; li < numLanes; li++) {
                Lane lane = northRoad.lanes.get(li);
                if (lane == null || lane.vehicles == null) continue;
                int laneCenterX = getLaneCenterNorth(li, numLanes, cx);
                int x = laneCenterX - carW / 2;
                for (int vi = 0; vi < lane.vehicles.size(); vi++) {
                    Vehicle v = lane.vehicles.get(vi);
                    double d = getRenderDistance(v);
                    int y = (int) (d - carL);
                    drawCar(g2, v, x, y, carW, carL, true);
                }
            }
        }

        // South Road: Incoming on East side (heading North ^)
        if (southRoad != null && southRoad.lanes != null) {
            int h = getHeight();
            int numLanes = southRoad.lanes.size();
            for (int li = 0; li < numLanes; li++) {
                Lane lane = southRoad.lanes.get(li);
                if (lane == null || lane.vehicles == null) continue;
                int laneCenterX = getLaneCenterSouth(li, numLanes, cx);
                int x = laneCenterX - carW / 2;
                for (int vi = 0; vi < lane.vehicles.size(); vi++) {
                    Vehicle v = lane.vehicles.get(vi);
                    double d = getRenderDistance(v);
                    int y = (int) (h - d);
                    drawCar(g2, v, x, y, carW, carL, true);
                }
            }
        }

        // West Road: Incoming on South side (heading East >)
        if (westRoad != null && westRoad.lanes != null) {
            int numLanes = westRoad.lanes.size();
            for (int li = 0; li < numLanes; li++) {
                Lane lane = westRoad.lanes.get(li);
                if (lane == null || lane.vehicles == null) continue;
                int laneCenterY = getLaneCenterWest(li, numLanes, cy);
                int y = laneCenterY - carW / 2;
                for (int vi = 0; vi < lane.vehicles.size(); vi++) {
                    Vehicle v = lane.vehicles.get(vi);
                    double d = getRenderDistance(v);
                    int x = (int) (d - carL);
                    drawCar(g2, v, x, y, carL, carW, false);
                }
            }
        }

        // East Road: Incoming on North side (heading West <)
        if (eastRoad != null && eastRoad.lanes != null) {
            int w = getWidth();
            int numLanes = eastRoad.lanes.size();
            for (int li = 0; li < numLanes; li++) {
                Lane lane = eastRoad.lanes.get(li);
                if (lane == null || lane.vehicles == null) continue;
                int laneCenterY = getLaneCenterEast(li, numLanes, cy);
                int y = laneCenterY - carW / 2;
                for (int vi = 0; vi < lane.vehicles.size(); vi++) {
                    Vehicle v = lane.vehicles.get(vi);
                    double d = getRenderDistance(v);
                    int x = (int) (w - d);
                    drawCar(g2, v, x, y, carL, carW, false);
                }
            }
        }
    }

    /**
     * Resolves the lateral X centerline coordinate for vehicles on the North approach.
     * Right-hand traffic: Lane 0 is outer curb (cx - 45), Lane 1 is inner divider (cx - 15).
     */
    public int getLaneCenterNorth(int laneIndex, int numLanes, int cx) {
        if (numLanes <= 1) {
            return cx - 30;
        }
        if (numLanes == 2) {
            return (laneIndex == 0) ? (cx - 45) : (cx - 15);
        }
        double laneWidth = 60.0 / numLanes;
        return (int) Math.round((cx - 60) + (laneIndex + 0.5) * laneWidth);
    }

    /**
     * Resolves the lateral X centerline coordinate for vehicles on the South approach.
     * Right-hand traffic: Lane 0 is outer curb (cx + 45), Lane 1 is inner divider (cx + 15).
     */
    public int getLaneCenterSouth(int laneIndex, int numLanes, int cx) {
        if (numLanes <= 1) {
            return cx + 30;
        }
        if (numLanes == 2) {
            return (laneIndex == 0) ? (cx + 45) : (cx + 15);
        }
        double laneWidth = 60.0 / numLanes;
        return (int) Math.round((cx + 60) - (laneIndex + 0.5) * laneWidth);
    }

    /**
     * Resolves the lateral Y centerline coordinate for vehicles on the West approach.
     * Right-hand traffic: Lane 0 is outer curb (cy + 45), Lane 1 is inner divider (cy + 15).
     */
    public int getLaneCenterWest(int laneIndex, int numLanes, int cy) {
        if (numLanes <= 1) {
            return cy + 30;
        }
        if (numLanes == 2) {
            return (laneIndex == 0) ? (cy + 45) : (cy + 15);
        }
        double laneWidth = 60.0 / numLanes;
        return (int) Math.round((cy + 60) - (laneIndex + 0.5) * laneWidth);
    }

    /**
     * Resolves the lateral Y centerline coordinate for vehicles on the East approach.
     * Right-hand traffic: Lane 0 is outer curb (cy - 45), Lane 1 is inner divider (cy - 15).
     */
    public int getLaneCenterEast(int laneIndex, int numLanes, int cy) {
        if (numLanes <= 1) {
            return cy - 30;
        }
        if (numLanes == 2) {
            return (laneIndex == 0) ? (cy - 45) : (cy - 15);
        }
        double laneWidth = 60.0 / numLanes;
        return (int) Math.round((cy - 60) + (laneIndex + 0.5) * laneWidth);
    }

    private void drawCar(Graphics2D g2, Vehicle v, int x, int y, int w, int h, boolean vertical) {
        if (v != null && v.isEmergency()) {
            drawAmbulance(g2, x, y, w, h, vertical);
        } else {
            Color color = CAR_COLORS[Math.abs(v != null ? v.vehicleId : 0) % CAR_COLORS.length];
            drawCarRectangle(g2, x, y, w, h, color, vertical);
        }
    }

    private void drawAmbulance(Graphics2D g2, int x, int y, int w, int h, boolean vertical) {
        // Ambulance body: crisp white with red edge
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(x, y, w, h, 6, 6);
        g2.setColor(new Color(220, 20, 20));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRoundRect(x, y, w, h, 6, 6);

        // Windshield
        g2.setColor(WINDSHIELD_COLOR);
        if (vertical) {
            g2.fillRect(x + 3, y + 4, w - 6, 4);
            g2.fillRect(x + 3, y + h - 7, w - 6, 3);
        } else {
            g2.fillRect(x + 4, y + 3, 4, h - 6);
            g2.fillRect(x + w - 7, y + 3, 3, h - 6);
        }

        // Center Red Cross
        g2.setColor(new Color(220, 20, 20));
        int cx = x + w / 2;
        int cy = y + h / 2;
        g2.fillRect(cx - 2, cy - 5, 4, 10);
        g2.fillRect(cx - 5, cy - 2, 10, 4);

        // Emergency flashing beacon (Red / Blue)
        g2.setColor(new Color(255, 30, 30));
        g2.fillOval(cx - 2, y + 2, 4, 4);
    }

    private void drawCarRectangle(Graphics2D g2, int x, int y, int w, int h, Color color, boolean vertical) {
        g2.setColor(color);
        g2.fillRoundRect(x, y, w, h, 6, 6);
        g2.setColor(Color.WHITE);
        g2.setStroke(STROKE_THIN);
        g2.drawRoundRect(x, y, w, h, 6, 6);

        // Windshields
        g2.setColor(WINDSHIELD_COLOR);
        if (vertical) {
            g2.fillRect(x + 3, y + 4, w - 6, 4);
            g2.fillRect(x + 3, y + h - 7, w - 6, 3);
        } else {
            g2.fillRect(x + 4, y + 3, 4, h - 6);
            g2.fillRect(x + w - 7, y + 3, 3, h - 6);
        }
    }

    /**
     * Traffic signals positioned right at the incoming approach curb.
     * Renders a 3-lamp signal housing (Red, Yellow, Green).
     */
    private void drawTrafficSignals(Graphics2D g2, int cx, int cy, int halfRoad) {
        // North Signal (curb at incoming stop line)
        String northColor = (northRoad != null && northRoad.getSignal() != null) ? northRoad.getSignal().getColor() : "RED";
        drawSignalBox(g2, cx - halfRoad - 26, cy - halfRoad - 54, northColor);

        // South Signal (curb at incoming stop line)
        String southColor = (southRoad != null && southRoad.getSignal() != null) ? southRoad.getSignal().getColor() : "RED";
        drawSignalBox(g2, cx + halfRoad + 6, cy + halfRoad + 4, southColor);

        // East Signal (curb at incoming stop line)
        String eastColor = (eastRoad != null && eastRoad.getSignal() != null) ? eastRoad.getSignal().getColor() : "RED";
        drawSignalBox(g2, cx + halfRoad + 6, cy - halfRoad - 30, eastColor);

        // West Signal (curb at incoming stop line)
        String westColor = (westRoad != null && westRoad.getSignal() != null) ? westRoad.getSignal().getColor() : "RED";
        drawSignalBox(g2, cx - halfRoad - 46, cy + halfRoad + 4, westColor);
    }

    private void drawSignalBox(Graphics2D g2, int x, int y, String color) {
        int boxW = 20;
        int boxH = 50;

        boolean isRed = "RED".equalsIgnoreCase(color);
        boolean isYellow = "YELLOW".equalsIgnoreCase(color);
        boolean isGreen = "GREEN".equalsIgnoreCase(color);

        // Housing
        g2.setColor(HOUSING_COLOR);
        g2.fillRoundRect(x, y, boxW, boxH, 6, 6);
        g2.setColor(Color.LIGHT_GRAY);
        g2.setStroke(STROKE_THIN);
        g2.drawRoundRect(x, y, boxW, boxH, 6, 6);

        // 1. Red Lamp (top)
        g2.setColor(isRed ? SIGNAL_RED_ON : SIGNAL_RED_OFF);
        g2.fillOval(x + 4, y + 4, 12, 12);

        // 2. Yellow Lamp (middle)
        g2.setColor(isYellow ? SIGNAL_YELLOW_ON : SIGNAL_YELLOW_OFF);
        g2.fillOval(x + 4, y + 19, 12, 12);

        // 3. Green Lamp (bottom)
        g2.setColor(isGreen ? SIGNAL_GREEN_ON : SIGNAL_GREEN_OFF);
        g2.fillOval(x + 4, y + 34, 12, 12);
    }

    private void drawInfoCards(Graphics2D g2, int w, int h) {
        g2.setFont(FONT_CARD_TITLE);

        // North Card (Top Left)
        drawRoadCard(g2, 15, 15, northRoad);

        // East Card (Top Right)
        drawRoadCard(g2, w - 215, 15, eastRoad);

        // West Card (Bottom Left)
        drawRoadCard(g2, 15, h - 90, westRoad);

        // South Card (Bottom Right)
        drawRoadCard(g2, w - 215, h - 90, southRoad);
    }

    private void drawRoadCard(Graphics2D g2, int x, int y, Road road) {
        if (road == null) return;

        int cardW = 200;
        int cardH = 82;

        g2.setColor(CARD_BG);
        g2.fillRoundRect(x, y, cardW, cardH, 10, 10);
        g2.setColor(CARD_BORDER_COLOR);
        g2.drawRoundRect(x, y, cardW, cardH, 10, 10);

        String color = (road.getSignal() != null) ? road.getSignal().getColor() : "RED";
        int duration = (road.getSignal() != null) ? road.getSignal().getDuration() : 0;
        boolean isGreen = "GREEN".equalsIgnoreCase(color);
        boolean isYellow = "YELLOW".equalsIgnoreCase(color);

        // Road Name & Status Dot
        String headingDesc;
        if ("North".equalsIgnoreCase(road.getDirection())) {
            headingDesc = "North Road (Heading South ↓)";
        } else if ("South".equalsIgnoreCase(road.getDirection())) {
            headingDesc = "South Road (Heading North ↑)";
        } else if ("East".equalsIgnoreCase(road.getDirection())) {
            headingDesc = "East Road (Heading West ←)";
        } else {
            headingDesc = "West Road (Heading East →)";
        }

        g2.setFont(FONT_CARD_TITLE);
        g2.setColor(Color.WHITE);
        g2.drawString(headingDesc, x + 10, y + 20);

        Color dotColor;
        if (isGreen) {
            dotColor = DOT_GREEN;
        } else if (isYellow) {
            dotColor = DOT_YELLOW;
        } else {
            dotColor = DOT_RED;
        }
        g2.setColor(dotColor);
        g2.fillOval(x + cardW - 22, y + 9, 11, 11);

        // Details
        boolean hasEmergency = false;
        if (road.lanes != null) {
            for (Lane l : road.lanes) {
                if (l.vehicles != null) {
                    for (Vehicle v : l.vehicles) {
                        if (v.isEmergency()) {
                            hasEmergency = true;
                            break;
                        }
                    }
                }
            }
        }

        if (hasEmergency) {
            g2.setFont(FONT_CARD_STATUS);
            g2.setColor(new Color(255, 70, 70));
            g2.drawString("[EMERGENCY PRIORITY ACTIVE]", x + 12, y + 40);
        } else {
            g2.setFont(FONT_CARD_DETAILS);
            g2.setColor(CARD_DETAILS_COLOR);
            g2.drawString("Waiting Vehicles: " + road.getTotalVehicles(), x + 12, y + 40);
        }

        String statusText;
        Color textColor;

        if (isGreen) {
            statusText = "Signal: GREEN (" + duration + "s)";
            textColor = TEXT_GREEN;
        } else if (isYellow) {
            statusText = "Signal: YELLOW (" + duration + "s)";
            textColor = TEXT_YELLOW;
        } else {
            statusText = "Signal: RED (STOP)";
            textColor = TEXT_RED;
        }

        g2.setFont(FONT_CARD_STATUS);
        g2.setColor(textColor);
        g2.drawString(statusText, x + 12, y + 58);

        // Click to add hint
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(160, 200, 240, 200));
        g2.drawString("[Click card/road to + vehicle]", x + 12, y + 74);
    }
}
