package me.ayosynk.stuff.migration;

import java.sql.Timestamp;

/**
 * Represents a normalized punishment record parsed from other systems
 * ready for batch insertion into the database.
 */
public record ImportedPunishment(
    String uuid,
    String ipAddress,
    String punisherUuid,
    String type,
    String reason,
    Timestamp startTime,
    Timestamp endTime,
    boolean active
) {}
