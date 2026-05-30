package me.ayosynk.stuff.migration;

import me.ayosynk.stuff.StuffPlatform;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * Platform-agnostic migration manager.
 * Uses ForkJoinPool instead of Bukkit schedulers, and snakeyaml instead of Bukkit YamlConfiguration.
 */
public class MigrationManager {

    private final StuffPlatform platform;
    private final Map<String, MigrationSource> sources = new HashMap<>();

    public MigrationManager(StuffPlatform platform) {
        this.platform = platform;
    }

    public void init() {
        registerSource(new VanillaSource());
        registerSource(new EssentialsSource());
        registerSource(new LiteBansSource());
        registerSource(new AdvancedBanSource());
        registerSource(new MaxBansSource());
        registerSource(new BanManagerSource());
        registerSource(new BungeeAdminToolsSource());
    }

    private void registerSource(MigrationSource source) {
        sources.put(source.getName().toLowerCase(), source);
    }

    public List<MigrationSource> getSources() {
        return new ArrayList<>(sources.values());
    }

    public MigrationSource getSource(String name) {
        return sources.get(name.toLowerCase());
    }

    /**
     * Scans for local plugin configurations and database files to report auto-detection status.
     * Returns a map of source name -> localized detection summary status message.
     */
    public Map<String, String> scanDetectedSources() {
        Map<String, String> scanResults = new HashMap<>();

        // 1. Vanilla
        File bannedPlayers = new File("banned-players.json");
        File bannedIps = new File("banned-ips.json");
        if (bannedPlayers.exists() || bannedIps.exists()) {
            scanResults.put("vanilla", "✔ Vanilla: Found local banned-players.json/banned-ips.json!");
        } else {
            scanResults.put("vanilla", "✘ Vanilla: No local ban lists found in server root.");
        }

        // 2. Essentials
        File essFolder = new File(platform.getDataFolder().getParentFile(), "Essentials/userdata");
        if (essFolder.exists() && essFolder.isDirectory()) {
            File[] files = essFolder.listFiles();
            int count = files != null ? files.length : 0;
            scanResults.put("essentials", "✔ Essentials: Found userdata folder with " + count + " player files.");
        } else {
            scanResults.put("essentials", "✘ Essentials: userdata folder not found.");
        }

        // 3. LiteBans
        File localH2 = new File("litebans.mv.db");
        if (localH2.exists()) {
            scanResults.put("litebans", "✔ LiteBans: Found local H2 Database file in server root (litebans.mv.db)!");
        } else {
            scanResults.put("litebans", checkConfig("LiteBans", "litebans.db"));
        }

        // 4. AdvancedBan
        scanResults.put("advancedban", checkConfig("AdvancedBan", "AdvancedBan.db"));

        // 5. MaxBans
        scanResults.put("maxbans", checkConfig("MaxBans", "maxbans.db"));

        // 6. BanManager
        scanResults.put("banmanager", checkConfig("BanManager", "banmanager.db"));

        // 7. BungeeAdminTools
        scanResults.put("bat", checkConfig("BungeeAdminTools", "bungeeadmintools.db"));

        return scanResults;
    }

    private String checkConfig(String pluginName, String dbName) {
        File folder = new File(platform.getDataFolder().getParentFile(), pluginName);
        File configFile = new File(folder, "config.yml");
        if (configFile.exists()) {
            try {
                Map<String, Object> config = loadYaml(configFile);
                String storage = getYamlString(config, "driver");
                if (storage == null) storage = getYamlString(config, "database.driver");
                if (storage == null) storage = getYamlString(config, "Data.Type");
                if (storage == null) storage = getYamlString(config, "database");

                if (storage != null && (storage.equalsIgnoreCase("sqlite") || storage.equalsIgnoreCase("file"))) {
                    File dbFile = new File(folder, dbName);
                    if (dbFile.exists()) {
                        return "✔ " + pluginName + ": Local SQLite database config & file found (" + dbName + ").";
                    }
                    return "✔ " + pluginName + ": Local SQLite config found (DB file not created yet).";
                }
                return "✔ " + pluginName + ": Local configuration found (configured for SQL backend).";
            } catch (Exception e) {
                return "✔ " + pluginName + ": Configuration found but could not be parsed.";
            }
        }
        return "✘ " + pluginName + ": Configuration directory not found.";
    }

    // ==========================================
    // YAML HELPERS (platform-agnostic, using snakeyaml)
    // ==========================================

    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadYaml(File file) throws Exception {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
        try (FileInputStream fis = new FileInputStream(file)) {
            Object data = yaml.load(fis);
            if (data instanceof Map) {
                return (Map<String, Object>) data;
            }
            return new HashMap<>();
        }
    }

    @SuppressWarnings("unchecked")
    private static String getYamlString(Map<String, Object> config, String dotPath) {
        String[] parts = dotPath.split("\\.");
        Object current = config;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current != null ? current.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static int getYamlInt(Map<String, Object> config, String dotPath, int def) {
        String val = getYamlString(config, dotPath);
        if (val == null) return def;
        try { return Integer.parseInt(val); } catch (NumberFormatException e) { return def; }
    }

    @SuppressWarnings("unchecked")
    private static boolean getYamlBool(Map<String, Object> config, String dotPath, boolean def) {
        String val = getYamlString(config, dotPath);
        if (val == null) return def;
        return Boolean.parseBoolean(val);
    }

    @SuppressWarnings("unchecked")
    private static long getYamlLong(Map<String, Object> config, String dotPath, long def) {
        String val = getYamlString(config, dotPath);
        if (val == null) return def;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return def; }
    }

    // Utility helper to safely get timestamp
    private static Timestamp parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equalsIgnoreCase("forever")) {
            return null;
        }
        try {
            return new Timestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").parse(dateStr).getTime());
        } catch (Exception e) {
            try {
                return new Timestamp(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").parse(dateStr).getTime());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    // ==========================================
    // MIGRATION SOURCE IMPLEMENTATIONS
    // ==========================================

    private class VanillaSource implements MigrationSource {
        @Override public String getName() { return "vanilla"; }
        @Override public String getDescription() { return "Vanilla banned-players.json and banned-ips.json files"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlatform platform, Consumer<String> sendMessage, String[] args) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    List<ImportedPunishment> list = new ArrayList<>();
                    File playersFile = new File("banned-players.json");
                    File ipsFile = new File("banned-ips.json");

                    if (!playersFile.exists() && !ipsFile.exists()) {
                        throw new RuntimeException("No Vanilla ban files found in the server root folder.");
                    }

                    Gson gson = new Gson();
                    if (playersFile.exists()) {
                        try (FileReader fr = new FileReader(playersFile)) {
                            JsonArray array = gson.fromJson(fr, JsonArray.class);
                            if (array != null) {
                                for (JsonElement el : array) {
                                    JsonObject obj = el.getAsJsonObject();
                                    String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
                                    String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Vanilla Migrated Ban";
                                    String created = obj.has("created") ? obj.get("created").getAsString() : null;
                                    String expires = obj.has("expires") ? obj.get("expires").getAsString() : null;

                                    Timestamp start = parseDate(created);
                                    if (start == null) start = new Timestamp(System.currentTimeMillis());
                                    Timestamp end = parseDate(expires);

                                    list.add(new ImportedPunishment(uuid, null, null, "BAN", reason, start, end, true));
                                }
                            }
                        }
                    }

                    if (ipsFile.exists()) {
                        try (FileReader fr = new FileReader(ipsFile)) {
                            JsonArray array = gson.fromJson(fr, JsonArray.class);
                            if (array != null) {
                                for (JsonElement el : array) {
                                    JsonObject obj = el.getAsJsonObject();
                                    String ip = obj.has("ip") ? obj.get("ip").getAsString() : null;
                                    String reason = obj.has("reason") ? obj.get("reason").getAsString() : "Vanilla Migrated IP Ban";
                                    String created = obj.has("created") ? obj.get("created").getAsString() : null;
                                    String expires = obj.has("expires") ? obj.get("expires").getAsString() : null;

                                    Timestamp start = parseDate(created);
                                    if (start == null) start = new Timestamp(System.currentTimeMillis());
                                    Timestamp end = parseDate(expires);

                                    list.add(new ImportedPunishment(null, ip, null, "IP_BAN", reason, start, end, true));
                                }
                            }
                        }
                    }

                    if (list.isEmpty()) return 0;
                    return platform.getDatabaseManager().importBatch(list).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ForkJoinPool.commonPool());
        }
    }

    private class EssentialsSource implements MigrationSource {
        @Override public String getName() { return "essentials"; }
        @Override public String getDescription() { return "Essentials userdata YAML profiles"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlatform platform, Consumer<String> sendMessage, String[] args) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File userdata = new File(platform.getDataFolder().getParentFile(), "Essentials/userdata");
                    if (!userdata.exists() || !userdata.isDirectory()) {
                        throw new RuntimeException("Essentials userdata folder not found.");
                    }

                    File[] files = userdata.listFiles();
                    if (files == null || files.length == 0) return 0;

                    List<ImportedPunishment> list = new ArrayList<>();
                    for (File file : files) {
                        if (!file.getName().endsWith(".yml")) continue;

                        String filename = file.getName();
                        String uuidStr = filename.substring(0, filename.length() - 4); // strip .yml

                        try {
                            Map<String, Object> config = loadYaml(file);

                            // Check Ban
                            if (config.containsKey("ban")) {
                                Object banObj = config.get("ban");
                                if (banObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> ban = (Map<String, Object>) banObj;
                                    String reason = ban.getOrDefault("reason", "Essentials Migrated Ban").toString();
                                    long expiration = 0;
                                    try { expiration = Long.parseLong(ban.getOrDefault("expiration", "0").toString()); } catch (Exception ignored) {}
                                    boolean active = Boolean.parseBoolean(ban.getOrDefault("active", "true").toString());

                                    if (active) {
                                        Timestamp start = new Timestamp(System.currentTimeMillis());
                                        Timestamp end = expiration > 0 ? new Timestamp(expiration) : null;
                                        list.add(new ImportedPunishment(uuidStr, null, null, "BAN", reason, start, end, true));
                                    }
                                }
                            }

                            // Check Mute
                            if (config.containsKey("mute")) {
                                Object muteObj = config.get("mute");
                                if (muteObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> mute = (Map<String, Object>) muteObj;
                                    String reason = mute.getOrDefault("reason", "Essentials Migrated Mute").toString();
                                    long expiration = 0;
                                    try { expiration = Long.parseLong(mute.getOrDefault("expiration", "0").toString()); } catch (Exception ignored) {}
                                    boolean active = Boolean.parseBoolean(mute.getOrDefault("active", "true").toString());

                                    if (active) {
                                        Timestamp start = new Timestamp(System.currentTimeMillis());
                                        Timestamp end = expiration > 0 ? new Timestamp(expiration) : null;
                                        list.add(new ImportedPunishment(uuidStr, null, null, "MUTE", reason, start, end, true));
                                    }
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    if (list.isEmpty()) return 0;
                    return platform.getDatabaseManager().importBatch(list).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ForkJoinPool.commonPool());
        }
    }

    private class LiteBansSource implements MigrationSource {
        @Override public String getName() { return "litebans"; }
        @Override public String getDescription() { return "LiteBans database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlatform platform, Consumer<String> sendMessage, String[] args) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File folder = new File(platform.getDataFolder().getParentFile(), "LiteBans");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";
                    String tablePrefix = "litebans_";

                    if (configFile.exists()) {
                        Map<String, Object> config = loadYaml(configFile);
                        String driver = getYamlString(config, "driver");
                        if (driver == null) driver = "sqlite";
                        String prefix = getYamlString(config, "table_prefix");
                        if (prefix != null) tablePrefix = prefix;

                        if (driver.equalsIgnoreCase("sqlite")) {
                            File dbFile = new File(folder, "litebans.db");
                            if (dbFile.exists()) {
                                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                            }
                        } else {
                            String address = getYamlString(config, "address");
                            if (address == null) address = "localhost:3306";
                            String dbName = getYamlString(config, "database");
                            if (dbName == null) dbName = "litebans";
                            username = getYamlString(config, "username");
                            if (username == null) username = "root";
                            password = getYamlString(config, "password");
                            if (password == null) password = "";
                            jdbcUrl = "jdbc:mysql://" + address + "/" + dbName;
                        }
                    }

                    // CLI overrides
                    if (args.length >= 4) {
                        jdbcUrl = args[1];
                        username = args[2];
                        password = args[3];
                        if (args.length >= 5) tablePrefix = args[4];
                    }

                    if (jdbcUrl == null) {
                        File h2File = new File("litebans.mv.db");
                        if (h2File.exists()) {
                            try { Class.forName("me.ayosynk.stuff.libs.h2.Driver"); }
                            catch (ClassNotFoundException e) { Class.forName("org.h2.Driver"); }
                            jdbcUrl = "jdbc:h2:./litebans;mode=MySQL";
                        }
                    }

                    if (jdbcUrl == null) {
                        throw new RuntimeException("Could not autodetect LiteBans connection. Use: /stuffimport litebans <jdbcUrl> <user> <pass> [prefix]");
                    }

                    List<ImportedPunishment> list = new ArrayList<>();
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                        // 1. Migrate bans
                        String query = "SELECT uuid, ip, reason, banned_by_uuid, time, until, active FROM " + tablePrefix + "bans";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("uuid");
                                String ip = rs.getString("ip");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("banned_by_uuid");
                                long startMs = rs.getLong("time");
                                long endMs = rs.getLong("until");
                                boolean active = rs.getBoolean("active");
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;
                                String type = (ip != null && uuid == null) ? "IP_BAN" : "BAN";
                                list.add(new ImportedPunishment(uuid, ip, staff, type, reason, start, end, active));
                            }
                        }

                        // 2. Migrate mutes
                        query = "SELECT uuid, ip, reason, banned_by_uuid, time, until, active FROM " + tablePrefix + "mutes";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("uuid");
                                String ip = rs.getString("ip");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("banned_by_uuid");
                                long startMs = rs.getLong("time");
                                long endMs = rs.getLong("until");
                                boolean active = rs.getBoolean("active");
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;
                                list.add(new ImportedPunishment(uuid, ip, staff, "MUTE", reason, start, end, active));
                            }
                        }

                        // 3. Migrate warnings
                        query = "SELECT uuid, ip, reason, banned_by_uuid, time, active FROM " + tablePrefix + "warnings";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("uuid");
                                String ip = rs.getString("ip");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("banned_by_uuid");
                                long startMs = rs.getLong("time");
                                boolean active = rs.getBoolean("active");
                                Timestamp start = new Timestamp(startMs);
                                list.add(new ImportedPunishment(uuid, ip, staff, "WARN", reason, start, null, active));
                            }
                        }
                    }

                    if (list.isEmpty()) return 0;
                    return platform.getDatabaseManager().importBatch(list).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ForkJoinPool.commonPool());
        }
    }

    private class AdvancedBanSource implements MigrationSource {
        @Override public String getName() { return "advancedban"; }
        @Override public String getDescription() { return "AdvancedBan database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlatform platform, Consumer<String> sendMessage, String[] args) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File folder = new File(platform.getDataFolder().getParentFile(), "AdvancedBan");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";

                    if (configFile.exists()) {
                        Map<String, Object> config = loadYaml(configFile);
                        boolean isSqlite = !getYamlBool(config, "MySQL.use", false);

                        if (isSqlite) {
                            File dbFile = new File(folder, "AdvancedBan.db");
                            if (dbFile.exists()) jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                        } else {
                            String address = getYamlString(config, "MySQL.IP");
                            if (address == null) address = "localhost";
                            int port = getYamlInt(config, "MySQL.Port", 3306);
                            String dbName = getYamlString(config, "MySQL.DB");
                            if (dbName == null) dbName = "advancedban";
                            username = getYamlString(config, "MySQL.User");
                            if (username == null) username = "root";
                            password = getYamlString(config, "MySQL.Password");
                            if (password == null) password = "";
                            jdbcUrl = "jdbc:mysql://" + address + ":" + port + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1]; username = args[2]; password = args[3];
                    }

                    if (jdbcUrl == null) {
                        throw new RuntimeException("Could not autodetect AdvancedBan connection. Use: /stuffimport advancedban <jdbcUrl> <user> <pass>");
                    }

                    List<ImportedPunishment> list = new ArrayList<>();
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                        String query = "SELECT uuid, reason, operator, punishmentType, start, end FROM Punishments";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("uuid");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("operator");
                                String pType = rs.getString("punishmentType");
                                long startMs = rs.getLong("start");
                                long endMs = rs.getLong("end");

                                String type;
                                if (pType.contains("BAN")) { type = pType.contains("IP") ? "IP_BAN" : "BAN"; }
                                else if (pType.contains("MUTE")) { type = "MUTE"; }
                                else if (pType.contains("WARN")) { type = "WARN"; }
                                else { continue; }

                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = (endMs > 0 && endMs != -1) ? new Timestamp(endMs) : null;
                                String punisher = null;
                                if (staff != null && staff.length() == 36) punisher = staff;
                                list.add(new ImportedPunishment(uuid, null, punisher, type, reason, start, end, true));
                            }
                        }
                    }

                    if (list.isEmpty()) return 0;
                    return platform.getDatabaseManager().importBatch(list).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ForkJoinPool.commonPool());
        }
    }

    private class MaxBansSource implements MigrationSource {
        @Override public String getName() { return "maxbans"; }
        @Override public String getDescription() { return "MaxBans database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlatform platform, Consumer<String> sendMessage, String[] args) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File folder = new File(platform.getDataFolder().getParentFile(), "MaxBans");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";

                    if (configFile.exists()) {
                        Map<String, Object> config = loadYaml(configFile);
                        boolean isSqlite = !getYamlBool(config, "database.mysql", false);

                        if (isSqlite) {
                            File dbFile = new File(folder, "maxbans.db");
                            if (dbFile.exists()) jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                        } else {
                            String host = getYamlString(config, "database.host");
                            if (host == null) host = "localhost";
                            int port = getYamlInt(config, "database.port", 3306);
                            String dbName = getYamlString(config, "database.name");
                            if (dbName == null) dbName = "maxbans";
                            username = getYamlString(config, "database.user");
                            if (username == null) username = "root";
                            password = getYamlString(config, "database.password");
                            if (password == null) password = "";
                            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1]; username = args[2]; password = args[3];
                    }

                    if (jdbcUrl == null) {
                        throw new RuntimeException("Could not autodetect MaxBans connection. Use: /stuffimport maxbans <jdbcUrl> <user> <pass>");
                    }

                    List<ImportedPunishment> list = new ArrayList<>();
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                        // 1. Migrate standard bans (by name — UUID resolution not available cross-platform)
                        String query = "SELECT name, reason, banner, time, expires FROM bans";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("name");
                                String reason = rs.getString("reason");
                                long startMs = rs.getLong("time");
                                long endMs = rs.getLong("expires");
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;
                                // MaxBans stores by name, not UUID — import with null UUID
                                list.add(new ImportedPunishment(null, null, null, "BAN", reason + " [MaxBans: " + name + "]", start, end, true));
                            }
                        }

                        // 2. Migrate IP bans
                        query = "SELECT ip, reason, banner, time, expires FROM ipbans";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String ip = rs.getString("ip");
                                String reason = rs.getString("reason");
                                long startMs = rs.getLong("time");
                                long endMs = rs.getLong("expires");
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;
                                list.add(new ImportedPunishment(null, ip, null, "IP_BAN", reason, start, end, true));
                            }
                        }

                        // 3. Migrate mutes
                        query = "SELECT name, reason, banner, time, expires FROM mutes";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("name");
                                String reason = rs.getString("reason");
                                long startMs = rs.getLong("time");
                                long endMs = rs.getLong("expires");
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;
                                list.add(new ImportedPunishment(null, null, null, "MUTE", reason + " [MaxBans: " + name + "]", start, end, true));
                            }
                        }

                        // 4. Migrate warnings
                        query = "SELECT name, reason, banner, time FROM warnings";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("name");
                                String reason = rs.getString("reason");
                                long startMs = rs.getLong("time");
                                Timestamp start = new Timestamp(startMs);
                                list.add(new ImportedPunishment(null, null, null, "WARN", reason + " [MaxBans: " + name + "]", start, null, true));
                            }
                        }
                    }

                    if (list.isEmpty()) return 0;
                    return platform.getDatabaseManager().importBatch(list).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ForkJoinPool.commonPool());
        }
    }

    private class BanManagerSource implements MigrationSource {
        @Override public String getName() { return "banmanager"; }
        @Override public String getDescription() { return "BanManager database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlatform platform, Consumer<String> sendMessage, String[] args) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File folder = new File(platform.getDataFolder().getParentFile(), "BanManager");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";
                    String tablePrefix = "bm_";

                    if (configFile.exists()) {
                        Map<String, Object> config = loadYaml(configFile);
                        String dbType = getYamlString(config, "database.driver");
                        if (dbType == null) dbType = "sqlite";

                        if (dbType.equalsIgnoreCase("sqlite")) {
                            File dbFile = new File(folder, "banmanager.db");
                            if (dbFile.exists()) jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                        } else {
                            String host = getYamlString(config, "database.host");
                            if (host == null) host = "localhost";
                            int port = getYamlInt(config, "database.port", 3306);
                            String dbName = getYamlString(config, "database.name");
                            if (dbName == null) dbName = "banmanager";
                            username = getYamlString(config, "database.user");
                            if (username == null) username = "root";
                            password = getYamlString(config, "database.password");
                            if (password == null) password = "";
                            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1]; username = args[2]; password = args[3];
                        if (args.length >= 5) tablePrefix = args[4];
                    }

                    if (jdbcUrl == null) {
                        throw new RuntimeException("Could not autodetect BanManager connection. Use: /stuffimport banmanager <jdbcUrl> <user> <pass> [prefix]");
                    }

                    List<ImportedPunishment> list = new ArrayList<>();
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                        // 1. Migrate player bans
                        String query = "SELECT player_uuid, reason, actor_uuid, created, expires FROM " + tablePrefix + "player_bans";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("player_uuid");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("actor_uuid");
                                long startMs = rs.getLong("created");
                                long endMs = rs.getLong("expires");
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;
                                list.add(new ImportedPunishment(uuid, null, staff, "BAN", reason, start, end, true));
                            }
                        }

                        // 2. Migrate player mutes
                        query = "SELECT player_uuid, reason, actor_uuid, created, expires FROM " + tablePrefix + "player_mutes";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("player_uuid");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("actor_uuid");
                                long startMs = rs.getLong("created");
                                long endMs = rs.getLong("expires");
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;
                                list.add(new ImportedPunishment(uuid, null, staff, "MUTE", reason, start, end, true));
                            }
                        }

                        // 3. Migrate warnings
                        query = "SELECT player_uuid, reason, actor_uuid, created FROM " + tablePrefix + "warnings";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("player_uuid");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("actor_uuid");
                                long startMs = rs.getLong("created");
                                Timestamp start = new Timestamp(startMs);
                                list.add(new ImportedPunishment(uuid, null, staff, "WARN", reason, start, null, true));
                            }
                        }
                    }

                    if (list.isEmpty()) return 0;
                    return platform.getDatabaseManager().importBatch(list).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ForkJoinPool.commonPool());
        }
    }

    private class BungeeAdminToolsSource implements MigrationSource {
        @Override public String getName() { return "bat"; }
        @Override public String getDescription() { return "BungeeAdminTools database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlatform platform, Consumer<String> sendMessage, String[] args) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    File folder = new File(platform.getDataFolder().getParentFile(), "BungeeAdminTools");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";
                    String tablePrefix = "bat_";

                    if (configFile.exists()) {
                        Map<String, Object> config = loadYaml(configFile);
                        String dbType = getYamlString(config, "database.driver");
                        if (dbType == null) dbType = "sqlite";

                        if (dbType.equalsIgnoreCase("sqlite")) {
                            File dbFile = new File(folder, "bungeeadmintools.db");
                            if (dbFile.exists()) jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                        } else {
                            String host = getYamlString(config, "database.host");
                            if (host == null) host = "localhost";
                            int port = getYamlInt(config, "database.port", 3306);
                            String dbName = getYamlString(config, "database.name");
                            if (dbName == null) dbName = "bungeeadmintools";
                            username = getYamlString(config, "database.user");
                            if (username == null) username = "root";
                            password = getYamlString(config, "database.password");
                            if (password == null) password = "";
                            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1]; username = args[2]; password = args[3];
                        if (args.length >= 5) tablePrefix = args[4];
                    }

                    if (jdbcUrl == null) {
                        throw new RuntimeException("Could not autodetect BungeeAdminTools connection. Use: /stuffimport bat <jdbcUrl> <user> <pass> [prefix]");
                    }

                    List<ImportedPunishment> list = new ArrayList<>();
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                        // 1. Migrate bans
                        String query = "SELECT player_uuid, ban_reason, ban_staff, ban_date, ban_end, ban_state FROM " + tablePrefix + "ban";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("player_uuid");
                                String reason = rs.getString("ban_reason");
                                String staff = rs.getString("ban_staff");
                                Timestamp start = rs.getTimestamp("ban_date");
                                Timestamp end = rs.getTimestamp("ban_end");
                                int state = rs.getInt("ban_state");
                                boolean active = state == 1;
                                list.add(new ImportedPunishment(uuid, null, staff, "BAN", reason, start, end, active));
                            }
                        }

                        // 2. Migrate mutes
                        query = "SELECT player_uuid, mute_reason, mute_staff, mute_date, mute_end, mute_state FROM " + tablePrefix + "mute";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String uuid = rs.getString("player_uuid");
                                String reason = rs.getString("mute_reason");
                                String staff = rs.getString("mute_staff");
                                Timestamp start = rs.getTimestamp("mute_date");
                                Timestamp end = rs.getTimestamp("mute_end");
                                int state = rs.getInt("mute_state");
                                boolean active = state == 1;
                                list.add(new ImportedPunishment(uuid, null, staff, "MUTE", reason, start, end, active));
                            }
                        }
                    }

                    if (list.isEmpty()) return 0;
                    return platform.getDatabaseManager().importBatch(list).join();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ForkJoinPool.commonPool());
        }
    }
}
