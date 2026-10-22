package models;

public class TrafficSignal {

    private String color;
    private int duration;

    // Default constructor
    public TrafficSignal() {
        this.color = "RED";
        this.duration = 0;
    }

    // Parameterized constructor
    public TrafficSignal(String color, int duration) {
        this.color = color;
        this.duration = duration;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public void displaySignal() {
        System.out.println(color + " signal for " + duration + " seconds");
    }
}
