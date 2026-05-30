package me.ayosynk.stuff.database;

import java.sql.Timestamp;
import java.util.UUID;

public class Punishment {
    private int id;
    private final UUID uuid; // Can be null for IP ban with no associated player
    private final String ipAddress;
    private final UUID punisherUuid; // Can be null for Console
    private final Type type;
    private final String reason;
    private final Timestamp startTime;
    private final Timestamp endTime; // Can be null for permanent
    private boolean active;

    public enum Type {
        MUTE,
        BAN,
        IP_BAN,
        WARN
    }

    public Punishment(UUID uuid, String ipAddress, UUID punisherUuid, Type type, String reason, Timestamp startTime, Timestamp endTime, boolean active) {
        this.uuid = uuid;
        this.ipAddress = ipAddress;
        this.punisherUuid = punisherUuid;
        this.type = type;
        this.reason = reason;
        this.startTime = startTime;
        this.endTime = endTime;
        this.active = active;
    }

    public Punishment(int id, UUID uuid, String ipAddress, UUID punisherUuid, Type type, String reason, Timestamp startTime, Timestamp endTime, boolean active) {
        this.id = id;
        this.uuid = uuid;
        this.ipAddress = ipAddress;
        this.punisherUuid = punisherUuid;
        this.type = type;
        this.reason = reason;
        this.startTime = startTime;
        this.endTime = endTime;
        this.active = active;
    }

    public int getId() { return id; }
    public UUID getUuid() { return uuid; }
    public String getIpAddress() { return ipAddress; }
    public UUID getPunisherUuid() { return punisherUuid; }
    public Type getType() { return type; }
    public String getReason() { return reason; }
    public Timestamp getStartTime() { return startTime; }
    public Timestamp getEndTime() { return endTime; }
    public boolean isActive() { return active; }

    public void setActive(boolean active) { this.active = active; }

    public boolean isExpired() {
        if (endTime == null) return false; // Permanent
        return System.currentTimeMillis() > endTime.getTime();
    }
}
