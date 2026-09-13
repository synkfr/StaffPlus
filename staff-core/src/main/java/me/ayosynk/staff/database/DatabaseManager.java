package me.ayosynk.staff.database;

import me.ayosynk.staff.StaffPlatform;
import me.ayosynk.staff.config.PluginConfig;
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
import java.util.concurrent.ForkJoinPool;
import me.ayosynk.staff.migration.ImportedPunishment;

/**
 * Platform-agnostic database manager.
 * Uses ForkJoinPool.commonPool() for async operations instead of Bukkit schedulers.
 */
public class DatabaseManager {

    private final StaffPlatform platform;
    private HikariDataSource dataSource;

    public DatabaseManager(StaffPlatform platform) {
        this.platform = platform;
    }

    public void init() {
        PluginConfig config = platform.getPluginConfig();
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
            File dbFile = new File(platform.getDataFolder(), "database.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1);
        }

        hikariConfig.setPoolName("StaffPool");
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
            stmt.execute("CREATE TABLE IF NOT EXISTS staff_players (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(16) NOT NULL, " +
                    "ip_address VARCHAR(45) NOT NULL, " +
                    "last_seen TIMESTAMP NOT NULL" +
                    ")");

            // Allows Table
            stmt.execute("CREATE TABLE IF NOT EXISTS staff_allows (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "active BOOLEAN NOT NULL" +
                    ")");

            // Migration: Add weight column if not exists
            try {
                stmt.execute("ALTER TABLE staff_players ADD COLUMN weight INT NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {}

            // Punishments Table
            String storage = platform.getPluginConfig().getStorageType();
            String query;
            if (storage.equalsIgnoreCase("mysql")) {
                query = "CREATE TABLE IF NOT EXISTS staff_punishments (" +
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
                query = "CREATE TABLE IF NOT EXISTS staff_punishments (" +
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
            platform.getLogger().severe("Could not setup database tables: " + e.getMessage());
        }
    }

    /**
     * Saves or updates a player's registry record in the database.
     */
    public CompletableFuture<Void> savePlayer(UUID uuid, String username, String ip, int weight) {
        return CompletableFuture.runAsync(() -> {
            boolean isMysql = platform.getPluginConfig().getStorageType().equalsIgnoreCase("mysql");
            String query;
            if (isMysql) {
                query = "INSERT INTO staff_players (uuid, username, ip_address, last_seen, weight) VALUES (?, ?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE username = VALUES(username), ip_address = VALUES(ip_address), last_seen = VALUES(last_seen), weight = VALUES(weight)";
            } else {
                query = "INSERT INTO staff_players (uuid, username, ip_address, last_seen, weight) VALUES (?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET username = excluded.username, ip_address = excluded.ip_address, last_seen = excluded.last_seen, weight = excluded.weight";
            }

            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, ip);
                ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                ps.setInt(5, weight);
                ps.executeUpdate();
            } catch (SQLException e) {
                platform.getLogger().severe("Could not save player record for " + username + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Asynchronously queries the cached hierarchy weight of a player.
     * Returns Integer.MAX_VALUE if uuid is null (representing Console).
     */
    public CompletableFuture<Integer> getPlayerWeight(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(Integer.MAX_VALUE);
        }
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT weight FROM staff_players WHERE uuid = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("weight");
                    } else {
                        return 0;
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error looking up weight for UUID " + uuid + ": " + e.getMessage());
                return 0;
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Records or updates a player's IP ban exemption bypass state.
     */
    public CompletableFuture<Void> addAllow(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            boolean isMysql = platform.getPluginConfig().getStorageType().equalsIgnoreCase("mysql");
            String query;
            if (isMysql) {
                query = "INSERT INTO staff_allows (uuid, active) VALUES (?, 1) " +
                        "ON DUPLICATE KEY UPDATE active = 1";
            } else {
                query = "INSERT INTO staff_allows (uuid, active) VALUES (?, 1) " +
                        "ON CONFLICT(uuid) DO UPDATE SET active = 1";
            }
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                platform.getLogger().severe("Could not add allow bypass for UUID " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Asynchronously revokes an active IP ban exemption bypass.
     */
    public CompletableFuture<Boolean> removeAllow(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "UPDATE staff_allows SET active = 0 WHERE uuid = ? AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                int rows = ps.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                platform.getLogger().severe("Could not remove allow bypass for UUID " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Queries whether a player is active in the IP ban allow bypass list.
     */
    public CompletableFuture<Boolean> isAllowed(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT active FROM staff_allows WHERE uuid = ? AND active = 1 LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error checking allow state for UUID " + uuid + ": " + e.getMessage());
                return false;
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Queries a player's UUID by their username (case insensitive).
     */
    public CompletableFuture<UUID> getPlayerUuidByName(String username) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT uuid FROM staff_players WHERE LOWER(username) = LOWER(?) LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("uuid"));
                    } else {
                        return null;
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error looking up UUID for username " + username + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Queries a player's last recorded username by their UUID.
     */
    public CompletableFuture<String> getPlayerNameByUuid(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT username FROM staff_players WHERE uuid = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("username");
                    } else {
                        return null;
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error looking up username for UUID " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Returns a list of all player names ever registered.
     */
    public CompletableFuture<List<String>> getAllRegisteredNames() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> names = new ArrayList<>();
            String query = "SELECT username FROM staff_players";
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    names.add(rs.getString("username"));
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error fetching all registered player names: " + e.getMessage());
            }
            return names;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Inserts a new punishment.
     */
    public CompletableFuture<Void> addPunishment(Punishment p) {
        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO staff_punishments (uuid, ip_address, punisher_uuid, type, reason, start_time, end_time, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
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
            } catch (SQLException e) {
                platform.getLogger().severe("Could not add punishment to database: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Deactivates a specific type of punishment for a player.
     */
    public CompletableFuture<Boolean> deactivatePunishment(UUID uuid, Punishment.Type type) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "UPDATE staff_punishments SET active = 0 WHERE uuid = ? AND type = ? AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, type.name());
                int rows = ps.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                platform.getLogger().severe("Could not deactivate punishment " + type + " for UUID " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Deactivates an IP-ban.
     */
    public CompletableFuture<Boolean> deactivateIpBan(String target) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "UPDATE staff_punishments SET active = 0 WHERE (ip_address = ? OR uuid = (SELECT uuid FROM staff_players WHERE LOWER(username) = LOWER(?) LIMIT 1)) AND type = 'IP_BAN' AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, target);
                ps.setString(2, target);
                int rows = ps.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                platform.getLogger().severe("Could not deactivate IP ban for " + target + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Queries warnings for a player.
     */
    public CompletableFuture<List<Punishment>> getWarnings(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> warns = new ArrayList<>();
            String query = "SELECT * FROM staff_punishments WHERE uuid = ? AND type = 'WARN' AND active = 1 ORDER BY start_time DESC";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        warns.add(mapPunishment(rs));
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Could not retrieve warnings for " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            return warns;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Clears warnings for a player.
     */
    public CompletableFuture<Boolean> clearWarnings(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "UPDATE staff_punishments SET active = 0 WHERE uuid = ? AND type = 'WARN' AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                int rows = ps.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                platform.getLogger().severe("Could not clear warnings for " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    public CompletableFuture<Punishment> getLatestActiveWarning(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM staff_punishments WHERE uuid = ? AND type = 'WARN' AND active = 1 ORDER BY start_time DESC LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapPunishment(rs);
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Could not retrieve latest warning for " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            return null;
        }, ForkJoinPool.commonPool());
    }

    public CompletableFuture<Boolean> removeWarningById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "UPDATE staff_punishments SET active = 0 WHERE id = ? AND type = 'WARN' AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, id);
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                platform.getLogger().severe("Could not remove warning " + id + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    public CompletableFuture<Boolean> removeLatestWarning(UUID uuid) {
        return getLatestActiveWarning(uuid).thenCompose(latest -> {
            if (latest == null) {
                return CompletableFuture.completedFuture(false);
            }
            return removeWarningById(latest.getId());
        });
    }

    public CompletableFuture<Punishment> getPunishmentById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT * FROM staff_punishments WHERE id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapPunishment(rs);
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Could not retrieve punishment by ID " + id + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            return null;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Helper to load and dynamically verify active punishments (muting / banning / IP banning).
     * Deactivates the punishment automatically if it is expired.
     */
    public CompletableFuture<Punishment> getActivePunishment(UUID uuid, String ip, Punishment.Type type) {
        return CompletableFuture.supplyAsync(() -> {
            String query;
            if (type == Punishment.Type.IP_BAN) {
                query = "SELECT * FROM staff_punishments WHERE (ip_address = ? OR uuid = ?) AND type = 'IP_BAN' AND active = 1 LIMIT 1";
            } else {
                query = "SELECT * FROM staff_punishments WHERE uuid = ? AND type = ? AND active = 1 LIMIT 1";
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
                            return null;
                        } else {
                            return p;
                        }
                    } else {
                        return null;
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Could not check active punishment " + type + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    private void deactivatePunishmentById(int id) {
        CompletableFuture.runAsync(() -> {
            String query = "UPDATE staff_punishments SET active = 0 WHERE id = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, id);
                ps.executeUpdate();
            } catch (SQLException e) {
                platform.getLogger().severe("Could not deactivate expired punishment " + id + ": " + e.getMessage());
            }
        }, ForkJoinPool.commonPool());
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

    /**
     * Retrieves all punishments (active and inactive) associated with a target player UUID.
     */
    public CompletableFuture<List<Punishment>> getHistory(UUID target) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> history = new ArrayList<>();
            String query = "SELECT * FROM staff_punishments WHERE uuid = ? ORDER BY start_time DESC";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        history.add(mapPunishment(rs));
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error fetching history for UUID " + target + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            return history;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Retrieves all punishments issued by a staff player UUID.
     */
    public CompletableFuture<List<Punishment>> getStaffHistory(UUID staff) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> history = new ArrayList<>();
            String query = "SELECT * FROM staff_punishments WHERE punisher_uuid = ? ORDER BY start_time DESC";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, staff.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        history.add(mapPunishment(rs));
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error fetching staff history for UUID " + staff + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
            return history;
        }, ForkJoinPool.commonPool());
    }

    /**
     * Deactivates all active punishments issued by a specific staff member.
     * Returns the count of rolled-back punishments.
     */
    public CompletableFuture<Integer> rollbackStaff(UUID staff) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "UPDATE staff_punishments SET active = 0 WHERE punisher_uuid = ? AND active = 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, staff.toString());
                return ps.executeUpdate();
            } catch (SQLException e) {
                platform.getLogger().severe("Error rolling back punishments for staff UUID " + staff + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    /**
     * Finds other registered player records that have logged in with the same IP address.
     */
    public CompletableFuture<List<PlayerRecord>> getAltsByIp(String ip) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerRecord> alts = new ArrayList<>();
            String query = "SELECT uuid, username, ip_address FROM staff_players WHERE ip_address = ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, ip);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        alts.add(new PlayerRecord(
                                UUID.fromString(rs.getString("uuid")),
                                rs.getString("username"),
                                rs.getString("ip_address")
                        ));
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error searching alts for IP " + ip + ": " + e.getMessage());
            }
            return alts;
        }, ForkJoinPool.commonPool());
    }

    public CompletableFuture<PlayerRecord> getPlayerRecord(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT username, ip_address FROM staff_players WHERE uuid = ? LIMIT 1";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerRecord(uuid, rs.getString("username"), rs.getString("ip_address"));
                    } else {
                        return null;
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error looking up player record for UUID " + uuid + ": " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
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

    /**
     * Executes a high-performance batch insert of imported punishments.
     * Inserts are executed inside a single transaction.
     */
    public CompletableFuture<Integer> importBatch(List<ImportedPunishment> punishments) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "INSERT INTO staff_punishments (uuid, ip_address, punisher_uuid, type, reason, start_time, end_time, active) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    for (ImportedPunishment p : punishments) {
                        ps.setString(1, p.uuid());
                        ps.setString(2, p.ipAddress());
                        ps.setString(3, p.punisherUuid());
                        ps.setString(4, p.type());
                        ps.setString(5, p.reason());
                        ps.setTimestamp(6, p.startTime());
                        ps.setTimestamp(7, p.endTime());
                        ps.setBoolean(8, p.active());
                        ps.addBatch();
                    }
                    int[] results = ps.executeBatch();
                    conn.commit();
                    int total = 0;
                    for (int r : results) {
                        if (r >= 0 || r == Statement.SUCCESS_NO_INFO) {
                            total++;
                        }
                    }
                    return total;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Failed to execute batch import: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    public static class StaffLeaderboardEntry {
        private final UUID staffUuid;
        private final String staffName;
        private final int count;

        public StaffLeaderboardEntry(UUID staffUuid, String staffName, int count) {
            this.staffUuid = staffUuid;
            this.staffName = staffName;
            this.count = count;
        }

        public UUID getStaffUuid() { return staffUuid; }
        public String getStaffName() { return staffName; }
        public int getCount() { return count; }
    }

    public CompletableFuture<List<Punishment>> getRecentBans(int offset, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Punishment> bans = new ArrayList<>();
            String query = "SELECT * FROM staff_punishments WHERE type IN ('BAN', 'IP_BAN') ORDER BY start_time DESC LIMIT ? OFFSET ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, limit);
                ps.setInt(2, Math.max(0, offset));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        bans.add(mapPunishment(rs));
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error fetching recent bans: " + e.getMessage());
                throw new RuntimeException(e);
            }
            return bans;
        }, ForkJoinPool.commonPool());
    }

    public CompletableFuture<Integer> getTotalBanCount() {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT COUNT(*) FROM staff_punishments WHERE type IN ('BAN', 'IP_BAN')";
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            } catch (SQLException e) {
                platform.getLogger().severe("Error fetching total ban count: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }, ForkJoinPool.commonPool());
    }

    public CompletableFuture<List<StaffLeaderboardEntry>> getStaffBanLeaderboard(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<StaffLeaderboardEntry> list = new ArrayList<>();
            String query = "SELECT p.punisher_uuid, COUNT(*) as cnt, pl.username " +
                    "FROM staff_punishments p " +
                    "LEFT JOIN staff_players pl ON p.punisher_uuid = pl.uuid " +
                    "WHERE p.type IN ('BAN', 'IP_BAN') AND p.punisher_uuid IS NOT NULL " +
                    "GROUP BY p.punisher_uuid, pl.username " +
                    "ORDER BY cnt DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection(); PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uuidStr = rs.getString("punisher_uuid");
                        UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
                        String name = rs.getString("username");
                        if (name == null) {
                            name = "Console";
                        }
                        int count = rs.getInt("cnt");
                        list.add(new StaffLeaderboardEntry(uuid, name, count));
                    }
                }
            } catch (SQLException e) {
                platform.getLogger().severe("Error fetching staff ban leaderboard: " + e.getMessage());
                throw new RuntimeException(e);
            }
            return list;
        }, ForkJoinPool.commonPool());
    }
}
