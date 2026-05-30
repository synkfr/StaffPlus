package me.ayosynk.stuff.database;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.config.PluginConfig;
import me.ayosynk.stuff.utils.SchedulerUtils;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final StuffPlugin plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(StuffPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        PluginConfig config = plugin.getPluginConfig();
        HikariConfig hikariConfig = new HikariConfig();

        if (config.getStorageType().equalsIgnoreCase("mysql")) {
            hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort() + "/" + config.getMysqlDatabase());
            hikariConfig.setUsername(config.getMysqlUsername());
            hikariConfig.setPassword(config.getMysqlPassword());
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            hikariConfig.addDataSourceProperty("useSSL", String.valueOf(config.isMysqlUseSsl()));
            hikariConfig.setMaximumPoolSize(config.getMysqlPoolSize());
        } else {
            // SQLite
            File dbFile = new File(plugin.getDataFolder(), "database.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1);
        }

        hikariConfig.setPoolName("StuffPool");
        this.dataSource = new HikariDataSource(hikariConfig);

        setupTables();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private void setupTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            // Players Table
            stmt.execute("CREATE TABLE IF NOT EXISTS stuff_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(16) NOT NULL, " +
                    "ip_address VARCHAR(45) NOT NULL, " +
                    "last_seen TIMESTAMP NOT NULL" +
                    ")");

            // Punishments Table (using clean standard primary key)
            String storage = plugin.getPluginConfig().getStorageType();
            String query;
            if (storage.equalsIgnoreCase("mysql")) {
                query = "CREATE TABLE IF NOT EXISTS stuff_punishments (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "uuid VARCHAR(36), " +
                        "ip_address VARCHAR(45), " +
                        "punisher_uuid VARCHAR(36), " +
                        "type VARCHAR(10) NOT NULL, " +
                        "reason TEXT NOT NULL, " +
                        "start_time TIMESTAMP NOT NULL, " +
                        "end_time TIMESTAMP NULL, " +
                        "active BOOLEAN NOT NULL" +
                        ")";
            } else {
                query = "CREATE TABLE IF NOT EXISTS stuff_punishments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "uuid VARCHAR(36), " +
                        "ip_address VARCHAR(45), " +
                        "punisher_uuid VARCHAR(36), " +
                        "type VARCHAR(10) NOT NULL, " +
                        "reason TEXT NOT NULL, " +
                        "start_time TIMESTAMP NOT NULL, " +
                        "end_time TIMESTAMP, " +
                        "active BOOLEAN NOT NULL" +
                        ")";
            }
            stmt.execute(query);
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not setup database tables: " + e.getMessage());
        }
    }

    /**
     * Saves or updates a player's registry record in the database.
     */
    public CompletableFuture<Void> savePlayer(UUID uuid, String username, String ip) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            boolean isMysql = plugin.getPluginConfig().getStorageType().equalsIgnoreCase("mysql");
            String query;
            if (isMysql) {
                query = "INSERT INTO stuff_players (uuid, username, ip_address, last_seen) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE username = VALUES(username), ip_address = VALUES(ip_address), last_seen = VALUES(last_seen)";
            } else {
                query = "INSERT INTO stuff_players (uuid, username, ip_address, last_seen) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, ip_address = excluded.ip_address, last_seen = excluded.last_seen";
            }

            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, ip);
                ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not save player record for " + username + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Queries a player's UUID by their username (case insensitive).
     */
    public CompletableFuture<UUID> getPlayerUuidByName(String username) {
        CompletableFuture<UUID> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "SELECT uuid FROM stuff_players WHERE LOWER(username) = LOWER(?) LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(UUID.fromString(rs.getString("uuid")));
                    } else {
                        future.complete(null);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error looking up UUID for username " + username + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Queries a player's last recorded username by their UUID.
     */
    public CompletableFuture<String> getPlayerNameByUuid(UUID uuid) {
        CompletableFuture<String> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "SELECT username FROM stuff_players WHERE uuid = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(rs.getString("username"));
                    } else {
                        future.complete(null);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error looking up username for UUID " + uuid + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Returns a list of all player names ever registered.
     */
    public CompletableFuture<List<String>> getAllRegisteredNames() {
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            List<String> names = new ArrayList<>();
            String query = "SELECT username FROM stuff_players";
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    names.add(rs.getString("username"));
                }
                future.complete(names);
            } catch (SQLException e) {
                plugin.getLogger().severe("Error fetching all registered player names: " + e.getMessage());
                future.complete(names);
            }
        });
        return future;
    }

    /**
     * Inserts a new punishment.
     */
    public CompletableFuture<Void> addPunishment(Punishment p) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "INSERT INTO stuff_punishments (uuid, ip_address, punisher_uuid, type, reason, start_time, end_time, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, p.getUuid() != null ? p.getUuid().toString() : null);
                ps.setString(2, p.getIpAddress());
                ps.setString(3, p.getPunisherUuid() != null ? p.getPunisherUuid().toString() : null);
                ps.setString(4, p.getType().name());
                ps.setString(5, p.getReason());
                ps.setTimestamp(6, p.getStartTime());
                ps.setTimestamp(7, p.getEndTime());
                ps.setBoolean(8, p.isActive());
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not add punishment to database: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Deactivates a specific type of punishment for a player.
     */
    public CompletableFuture<Boolean> deactivatePunishment(UUID uuid, Punishment.Type type) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "UPDATE stuff_punishments SET active = 0 WHERE uuid = ? AND type = ? AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type.name());
                int rows = ps.executeUpdate();
                future.complete(rows > 0);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not deactivate punishment " + type + " for UUID " + uuid + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Deactivates an IP-ban.
     */
    public CompletableFuture<Boolean> deactivateIpBan(String target) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "UPDATE stuff_punishments SET active = 0 WHERE (ip_address = ? OR uuid = (SELECT uuid FROM stuff_players WHERE LOWER(username) = LOWER(?) LIMIT 1)) AND type = 'IP_BAN' AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, target);
                ps.setString(2, target);
                int rows = ps.executeUpdate();
                future.complete(rows > 0);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not deactivate IP ban for " + target + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Queries warnings for a player.
     */
    public CompletableFuture<List<Punishment>> getWarnings(UUID uuid) {
        CompletableFuture<List<Punishment>> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            List<Punishment> warns = new ArrayList<>();
            String query = "SELECT * FROM stuff_punishments WHERE uuid = ? AND type = 'WARN' AND active = 1 ORDER BY start_time DESC";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        warns.add(mapPunishment(rs));
                    }
                }
                future.complete(warns);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not retrieve warnings for " + uuid + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Clears warnings for a player.
     */
    public CompletableFuture<Boolean> clearWarnings(UUID uuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "UPDATE stuff_punishments SET active = 0 WHERE uuid = ? AND type = 'WARN' AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                int rows = ps.executeUpdate();
                future.complete(rows > 0);
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not clear warnings for " + uuid + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Helper to load and dynamically verify active punishments (muting / banning / IP banning).
     * Deactivates the punishment automatically if it is expired.
     */
    public CompletableFuture<Punishment> getActivePunishment(UUID uuid, String ip, Punishment.Type type) {
        CompletableFuture<Punishment> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query;
            if (type == Punishment.Type.IP_BAN) {
                query = "SELECT * FROM stuff_punishments WHERE (ip_address = ? OR uuid = ?) AND type = 'IP_BAN' AND active = 1 LIMIT 1";
            } else {
                query = "SELECT * FROM stuff_punishments WHERE uuid = ? AND type = ? AND active = 1 LIMIT 1";
            }

            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                if (type == Punishment.Type.IP_BAN) {
                    ps.setString(1, ip);
                    ps.setString(2, uuid != null ? uuid.toString() : "");
                } else {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, type.name());
                }

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Punishment p = mapPunishment(rs);
                        if (p.isExpired()) {
                            // Deactivate expired punishment lazily
                            deactivatePunishmentById(p.getId());
                            future.complete(null);
                        } else {
                            future.complete(p);
                        }
                    } else {
                        future.complete(null);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not check active punishment " + type + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private void deactivatePunishmentById(int id) {
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "UPDATE stuff_punishments SET active = 0 WHERE id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Could not deactivate expired punishment " + id + ": " + e.getMessage());
            }
        });
    }

    public static class PlayerRecord {
        public final UUID uuid;
        public final String name;
        public final String ip;

        public PlayerRecord(UUID uuid, String name, String ip) {
            this.uuid = uuid;
            this.name = name;
            this.ip = ip;
        }
    }

    public CompletableFuture<PlayerRecord> getPlayerRecord(UUID uuid) {
        CompletableFuture<PlayerRecord> future = new CompletableFuture<>();
        SchedulerUtils.runAsync(plugin, () -> {
            String query = "SELECT username, ip_address FROM stuff_players WHERE uuid = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(new PlayerRecord(uuid, rs.getString("username"), rs.getString("ip_address")));
                    } else {
                        future.complete(null);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error looking up player record for UUID " + uuid + ": " + e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private Punishment mapPunishment(ResultSet rs) throws SQLException {
        String uuidStr = rs.getString("uuid");
        UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
        String punisherStr = rs.getString("punisher_uuid");
        UUID punisherUuid = punisherStr != null ? UUID.fromString(punisherStr) : null;

        return new Punishment(
                rs.getInt("id"),
                uuid,
                rs.getString("ip_address"),
                punisherUuid,
                Punishment.Type.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getTimestamp("start_time"),
                rs.getTimestamp("end_time"),
                rs.getBoolean("active")
        );
    }
}
