package com.gymapp.gym.model;

public class Membership {
    private String id;
    private String name;
    private String type;        // BASIC, PREMIUM, VIP
    private double price;
    private int maxClassesPerMonth;
    private boolean active;

    public Membership() {}

    public Membership(String id, String name, String type, double price, int maxClassesPerMonth, boolean active) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.price = price;
        this.maxClassesPerMonth = maxClassesPerMonth;
        this.active = active;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getMaxClassesPerMonth() { return maxClassesPerMonth; }
    public void setMaxClassesPerMonth(int maxClassesPerMonth) { this.maxClassesPerMonth = maxClassesPerMonth; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
