package me.ayosynk.staff.bukkit.freeze;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class FreezeSession {

    private final UUID targetUuid;
    private final UUID staffUuid;
    private final String staffName;
    private final String reason;
    private final long frozenAt;
    private final Location freezeLocation;
    private final String messageMode;

    public FreezeSession(@NotNull UUID targetUuid, @Nullable UUID staffUuid, @NotNull String staffName, @NotNull String reason, @NotNull Location freezeLocation) {
        this(targetUuid, staffUuid, staffName, reason, freezeLocation, "CHAT");
    }

    public FreezeSession(@NotNull UUID targetUuid, @Nullable UUID staffUuid, @NotNull String staffName, @NotNull String reason, @NotNull Location freezeLocation, @NotNull String messageMode) {
        this.targetUuid = targetUuid;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.reason = reason;
        this.frozenAt = System.currentTimeMillis();
        this.freezeLocation = freezeLocation;
        this.messageMode = messageMode != null ? messageMode : "CHAT";
    }

    public @NotNull UUID getTargetUuid() {
        return targetUuid;
    }

    public @Nullable UUID getStaffUuid() {
        return staffUuid;
    }

    public @NotNull String getStaffName() {
        return staffName;
    }

    public @NotNull String getReason() {
        return reason;
    }

    public long getFrozenAt() {
        return frozenAt;
    }

    public @NotNull Location getFreezeLocation() {
        return freezeLocation;
    }

    public @NotNull String getMessageMode() {
        return messageMode;
    }

    public boolean isChatEnabled() {
        String mode = messageMode.toUpperCase();
        return mode.contains("ALL") || mode.contains("CHAT") || (!isTitleEnabled() && !isActionBarEnabled());
    }

    public boolean isTitleEnabled() {
        String mode = messageMode.toUpperCase();
        return mode.contains("ALL") || mode.contains("TITLE");
    }

    public boolean isActionBarEnabled() {
        String mode = messageMode.toUpperCase();
        return mode.contains("ALL") || mode.contains("ACTION");
    }
}
