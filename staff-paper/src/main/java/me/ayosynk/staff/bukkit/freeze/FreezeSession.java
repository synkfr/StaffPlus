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

    public FreezeSession(@NotNull UUID targetUuid, @Nullable UUID staffUuid, @NotNull String staffName, @NotNull String reason, @NotNull Location freezeLocation) {
        this.targetUuid = targetUuid;
        this.staffUuid = staffUuid;
        this.staffName = staffName;
        this.reason = reason;
        this.frozenAt = System.currentTimeMillis();
        this.freezeLocation = freezeLocation;
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
}
