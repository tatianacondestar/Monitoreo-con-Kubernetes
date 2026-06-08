package com.gymapp.booking.model;

import java.time.LocalDateTime;

public class Booking {
    private String id;
    private String membershipId;   // debe existir en gym-service
    private String memberName;
    private String className;      // ej: "Yoga", "Spinning", "CrossFit"
    private LocalDateTime scheduledAt;
    private String status;         // CONFIRMED, CANCELLED, PENDING

    public Booking() {}

    public Booking(String id, String membershipId, String memberName,
                   String className, LocalDateTime scheduledAt, String status) {
        this.id = id;
        this.membershipId = membershipId;
        this.memberName = memberName;
        this.className = className;
        this.scheduledAt = scheduledAt;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMembershipId() { return membershipId; }
    public void setMembershipId(String membershipId) { this.membershipId = membershipId; }

    public String getMemberName() { return memberName; }
    public void setMemberName(String memberName) { this.memberName = memberName; }

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
