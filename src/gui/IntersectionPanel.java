package gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import javax.swing.JPanel;
import models.Road;

/**
 * Custom Swing JPanel that renders a bird's-eye view of a 4-way intersection
 * using a simple two-way road layout (one incoming lane, one outgoing lane,
 * and a central divider between them).
 */
public class IntersectionPanel extends JPanel {

    private Road northRoad;
    private Road southRoad;
    private Road eastRoad;
    private Road westRoad;

    // Palette
    private static final Color GRASS_COLOR = new Color(38, 120, 58);
    private static final Color ROAD_COLOR = new Color(50, 53, 58);
    private static final Color YELLOW_DIVIDER = new Color(245, 195, 35);
    private static final Color ARROW_COLOR = new Color(225, 230, 235, 170);
    private static final Color STOP_LINE_COLOR = new Color(250, 250, 250);
    private static final Color HOUSING_COLOR = new Color(25, 25, 25);
    private static final Color CARD_BG = new Color(20, 24, 30, 215);

    // Car Colors for variety
    private static final Color[] CAR_COLORS = {
        new Color(40, 120, 220),  // Blue
        new Color(230, 75, 60),   // Red
        new Color(240, 180, 40),  // Yellow
        new Color(45, 180, 100),  // Green
        new Color(170, 90, 210),  // Purple
        new Color(220, 110, 40)   // Orange
    };

    public IntersectionPanel(Road north, Road south, Road east, Road west) {
        this.northRoad = north;
        this.southRoad = south;
        this.eastRoad = east;
        this.westRoad = west;
        setBackground(GRASS_COLOR);
    }

    public void updateRoads(Road north, Road south, Road east, Road west) {
        this.northRoad = north;
        this.southRoad = south;
        this.eastRoad = east;
        this.westRoad = west;
        repaint();
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
        g2.setColor(new Color(58, 62, 68));
        g2.fillRect(cx - halfRoad, cy - halfRoad, roadWidth, roadWidth);

        // 3. Central Dividers (Double Yellow Lines separating the two directions)
        drawCentralDividers(g2, cx, cy, w, h, halfRoad);

        // 4. Directional Lane Arrows (Incoming & Outgoing)
        drawPavementArrows(g2, cx, cy, halfRoad);

        // 5. Stop Lines (across incoming approach lanes only)
        drawStopLines(g2, cx, cy, halfRoad);

        // 6. Draw Queued Waiting Vehicles in the Incoming Lanes
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
        g2.setStroke(new BasicStroke(2));

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
     * Draws simple pavement arrows indicating traffic direction in each lane:
     * Incoming lanes point toward intersection; Outgoing lanes point away.
     */
    private void drawPavementArrows(Graphics2D g2, int cx, int cy, int halfRoad) {
        g2.setColor(ARROW_COLOR);

        // North: Incoming is West side (x = cx - 30, heading South v)
        //        Outgoing is East side (x = cx + 30, heading North ^)
        drawArrow(g2, cx - 30, cy - halfRoad - 50, 0, 1);
        drawArrow(g2, cx + 30, cy - halfRoad - 50, 0, -1);

        // South: Incoming is East side (x = cx + 30, heading North ^)
        //        Outgoing is West side (x = cx - 30, heading South v)
        drawArrow(g2, cx + 30, cy + halfRoad + 50, 0, -1);
        drawArrow(g2, cx - 30, cy + halfRoad + 50, 0, 1);

        // West:  Incoming is South side (y = cy + 30, heading East >)
        //        Outgoing is North side (y = cy - 30, heading West <)
        drawArrow(g2, cx - halfRoad - 50, cy + 30, 1, 0);
        drawArrow(g2, cx - halfRoad - 50, cy - 30, -1, 0);

        // East:  Incoming is North side (y = cy - 30, heading West <)
        //        Outgoing is South side (y = cy + 30, heading East >)
        drawArrow(g2, cx + halfRoad + 50, cy - 30, -1, 0);
        drawArrow(g2, cx + halfRoad + 50, cy + 30, 1, 0);
    }

    private void drawArrow(Graphics2D g2, int x, int y, int dx, int dy) {
        g2.setStroke(new BasicStroke(2.5f));
        int len = 14;
        int x2 = x + dx * len;
        int y2 = y + dy * len;
        g2.drawLine(x, y, x2, y2);

        // Arrowhead
        Polygon head = new Polygon();
        if (dx == 1) { // East
            head.addPoint(x2, y2);
            head.addPoint(x2 - 6, y2 - 4);
            head.addPoint(x2 - 6, y2 + 4);
        } else if (dx == -1) { // West
            head.addPoint(x2, y2);
            head.addPoint(x2 + 6, y2 - 4);
            head.addPoint(x2 + 6, y2 + 4);
        } else if (dy == 1) { // South
            head.addPoint(x2, y2);
            head.addPoint(x2 - 4, y2 - 6);
            head.addPoint(x2 + 4, y2 - 6);
        } else if (dy == -1) { // North
            head.addPoint(x2, y2);
            head.addPoint(x2 - 4, y2 + 6);
            head.addPoint(x2 + 4, y2 + 6);
        }
        g2.fill(head);
    }

    /**
     * Stop lines drawn across the incoming lane only.
     */
    private void drawStopLines(Graphics2D g2, int cx, int cy, int halfRoad) {
        g2.setColor(STOP_LINE_COLOR);
        g2.setStroke(new BasicStroke(3.5f));

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
     * Draws vehicles queued up in the incoming lane approaching the stop line.
     */
    private void drawVehicles(Graphics2D g2, int cx, int cy, int halfRoad) {
        int carW = 20;
        int carL = 26;
        int spacing = 5;

        // North Road: Incoming on West side (x = cx - 30)
        if (northRoad != null) {
            int count = northRoad.getTotalVehicles();
            int x = cx - 30 - carW / 2;
            int startY = cy - halfRoad - 10;
            for (int i = 0; i < count; i++) {
                int y = startY - (i * (carL + spacing)) - carL;
                Color color = CAR_COLORS[i % CAR_COLORS.length];
                drawCarRectangle(g2, x, y, carW, carL, color, true);
            }
        }

        // South Road: Incoming on East side (x = cx + 30)
        if (southRoad != null) {
            int count = southRoad.getTotalVehicles();
            int x = cx + 30 - carW / 2;
            int startY = cy + halfRoad + 10;
            for (int i = 0; i < count; i++) {
                int y = startY + (i * (carL + spacing));
                Color color = CAR_COLORS[i % CAR_COLORS.length];
                drawCarRectangle(g2, x, y, carW, carL, color, true);
            }
        }

        // West Road: Incoming on South side (y = cy + 30)
        if (westRoad != null) {
            int count = westRoad.getTotalVehicles();
            int y = cy + 30 - carW / 2;
            int startX = cx - halfRoad - 10;
            for (int i = 0; i < count; i++) {
                int x = startX - (i * (carL + spacing)) - carL;
                Color color = CAR_COLORS[(i + 2) % CAR_COLORS.length];
                drawCarRectangle(g2, x, y, carL, carW, color, false);
            }
        }

        // East Road: Incoming on North side (y = cy - 30)
        // To accommodate 15 vehicles compactly, use 16px length and 4px spacing
        if (eastRoad != null) {
            int count = eastRoad.getTotalVehicles();
            int eCarL = 16;
            int eSpacing = 4;
            int y = cy - 30 - carW / 2;
            int startX = cx + halfRoad + 10;
            for (int i = 0; i < count; i++) {
                int x = startX + (i * (eCarL + eSpacing));
                Color color = CAR_COLORS[i % CAR_COLORS.length];
                drawCarRectangle(g2, x, y, eCarL, carW, color, false);
            }
        }
    }

    private void drawCarRectangle(Graphics2D g2, int x, int y, int w, int h, Color color, boolean vertical) {
        g2.setColor(color);
        g2.fillRoundRect(x, y, w, h, 6, 6);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(x, y, w, h, 6, 6);

        // Windshields
        g2.setColor(new Color(220, 240, 255, 190));
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
     */
    private void drawTrafficSignals(Graphics2D g2, int cx, int cy, int halfRoad) {
        // North Signal (curb at incoming stop line: x = cx - halfRoad - 26, y = cy - halfRoad - 46)
        boolean northGreen = northRoad != null && "GREEN".equalsIgnoreCase(northRoad.getSignal().getColor());
        drawSignalBox(g2, cx - halfRoad - 26, cy - halfRoad - 46, northGreen);

        // South Signal (curb at incoming stop line: x = cx + halfRoad + 6, y = cy + halfRoad + 6)
        boolean southGreen = southRoad != null && "GREEN".equalsIgnoreCase(southRoad.getSignal().getColor());
        drawSignalBox(g2, cx + halfRoad + 6, cy + halfRoad + 6, southGreen);

        // East Signal (curb at incoming stop line: x = cx + halfRoad + 6, y = cy - halfRoad - 26)
        boolean eastGreen = eastRoad != null && "GREEN".equalsIgnoreCase(eastRoad.getSignal().getColor());
        drawSignalBox(g2, cx + halfRoad + 6, cy - halfRoad - 26, eastGreen);

        // West Signal (curb at incoming stop line: x = cx - halfRoad - 46, y = cy + halfRoad + 6)
        boolean westGreen = westRoad != null && "GREEN".equalsIgnoreCase(westRoad.getSignal().getColor());
        drawSignalBox(g2, cx - halfRoad - 46, cy + halfRoad + 6, westGreen);
    }

    private void drawSignalBox(Graphics2D g2, int x, int y, boolean isGreen) {
        int boxW = 20;
        int boxH = 40;

        // Housing
        g2.setColor(HOUSING_COLOR);
        g2.fillRoundRect(x, y, boxW, boxH, 6, 6);
        g2.setColor(Color.LIGHT_GRAY);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(x, y, boxW, boxH, 6, 6);

        // Red Lamp
        g2.setColor(isGreen ? new Color(60, 10, 10) : new Color(245, 30, 30));
        g2.fillOval(x + 4, y + 4, 12, 12);

        // Green Lamp
        g2.setColor(isGreen ? new Color(30, 240, 60) : new Color(10, 60, 20));
        g2.fillOval(x + 4, y + 22, 12, 12);
    }

    private void drawInfoCards(Graphics2D g2, int w, int h) {
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));

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
        int cardH = 75;

        g2.setColor(CARD_BG);
        g2.fillRoundRect(x, y, cardW, cardH, 10, 10);
        g2.setColor(new Color(180, 190, 200, 100));
        g2.drawRoundRect(x, y, cardW, cardH, 10, 10);

        boolean isGreen = "GREEN".equalsIgnoreCase(road.getSignal().getColor());

        // Road Name & Status Dot
        g2.setColor(Color.WHITE);
        g2.drawString(road.getDirection() + " Road (Two-Way)", x + 12, y + 20);

        g2.setColor(isGreen ? new Color(40, 240, 60) : new Color(245, 50, 50));
        g2.fillOval(x + cardW - 22, y + 9, 11, 11);

        // Details
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(new Color(220, 225, 230));

        g2.drawString("Waiting Vehicles: " + road.getTotalVehicles(), x + 12, y + 40);

        String statusText = isGreen ? ("Signal: GREEN (" + road.getSignal().getDuration() + "s)") : "Signal: RED (Stopped)";
        g2.setColor(isGreen ? new Color(130, 255, 150) : new Color(255, 140, 140));
        g2.drawString(statusText, x + 12, y + 60);

        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
    }
}
