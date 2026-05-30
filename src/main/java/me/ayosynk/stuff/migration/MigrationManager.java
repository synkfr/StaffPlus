package me.ayosynk.stuff.migration;

import me.ayosynk.stuff.StuffPlugin;
import me.ayosynk.stuff.database.DatabaseManager;
import me.ayosynk.stuff.utils.SchedulerUtils;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class MigrationManager {

    private final StuffPlugin plugin;
    private final Map<String, MigrationSource> sources = new HashMap<>();

    public MigrationManager(StuffPlugin plugin) {
        this.plugin = plugin;
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
        File essFolder = new File(plugin.getDataFolder().getParentFile(), "Essentials/userdata");
        if (essFolder.exists() && essFolder.isDirectory()) {
            File[] files = essFolder.listFiles();
            int count = files != null ? files.length : 0;
            scanResults.put("essentials", "✔ Essentials: Found userdata folder with " + count + " player files.");
        } else {
            scanResults.put("essentials", "✘ Essentials: userdata folder not found.");
        }

        // 3. LiteBans
        scanResults.put("litebans", checkConfig("LiteBans", "litebans.db"));

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
        File folder = new File(plugin.getDataFolder().getParentFile(), pluginName);
        File configFile = new File(folder, "config.yml");
        if (configFile.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            String storage = config.getString("driver");
            if (storage == null) {
                storage = config.getString("database.driver");
            }
            if (storage == null) {
                storage = config.getString("Data.Type");
            }
            if (storage == null) {
                storage = config.getString("database");
            }
            
            if (storage != null && (storage.equalsIgnoreCase("sqlite") || storage.equalsIgnoreCase("file"))) {
                File dbFile = new File(folder, dbName);
                if (dbFile.exists()) {
                    return "✔ " + pluginName + ": Local SQLite database config & file found (" + dbName + ").";
                }
                return "✔ " + pluginName + ": Local SQLite config found (DB file not created yet).";
            }
            return "✔ " + pluginName + ": Local configuration found (configured for SQL backend).";
        }
        return "✘ " + pluginName + ": Configuration directory not found.";
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
        public CompletableFuture<Integer> migrate(StuffPlugin plugin, CommandSender sender, String[] args) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    List<ImportedPunishment> list = new ArrayList<>();
                    File playersFile = new File("banned-players.json");
                    File ipsFile = new File("banned-ips.json");

                    if (!playersFile.exists() && !ipsFile.exists()) {
                        future.completeExceptionally(new Exception("No Vanilla ban files found in the server root folder."));
                        return;
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

                    if (list.isEmpty()) {
                        future.complete(0);
                    } else {
                        plugin.getDatabaseManager().importBatch(list).thenAccept(future::complete).exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }

    private class EssentialsSource implements MigrationSource {
        @Override public String getName() { return "essentials"; }
        @Override public String getDescription() { return "Essentials userdata YAML profiles"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlugin plugin, CommandSender sender, String[] args) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    File userdata = new File(plugin.getDataFolder().getParentFile(), "Essentials/userdata");
                    if (!userdata.exists() || !userdata.isDirectory()) {
                        future.completeExceptionally(new Exception("Essentials userdata folder not found."));
                        return;
                    }

                    File[] files = userdata.listFiles();
                    if (files == null || files.length == 0) {
                        future.complete(0);
                        return;
                    }

                    List<ImportedPunishment> list = new ArrayList<>();
                    for (File file : files) {
                        if (!file.getName().endsWith(".yml")) continue;
                        
                        String filename = file.getName();
                        String uuidStr = filename.substring(0, filename.length() - 4); // strip .yml
                        
                        try {
                            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                            
                            // Check Ban
                            if (config.contains("ban")) {
                                String reason = config.getString("ban.reason", "Essentials Migrated Ban");
                                long expiration = config.getLong("ban.expiration", 0);
                                boolean active = config.getBoolean("ban.active", true);
                                
                                if (active) {
                                    Timestamp start = new Timestamp(System.currentTimeMillis());
                                    Timestamp end = expiration > 0 ? new Timestamp(expiration) : null;
                                    list.add(new ImportedPunishment(uuidStr, null, null, "BAN", reason, start, end, true));
                                }
                            }

                            // Check Mute
                            if (config.contains("mute")) {
                                String reason = config.getString("mute.reason", "Essentials Migrated Mute");
                                long expiration = config.getLong("mute.expiration", 0);
                                boolean active = config.getBoolean("mute.active", true);

                                if (active) {
                                    Timestamp start = new Timestamp(System.currentTimeMillis());
                                    Timestamp end = expiration > 0 ? new Timestamp(expiration) : null;
                                    list.add(new ImportedPunishment(uuidStr, null, null, "MUTE", reason, start, end, true));
                                }
                            }
                        } catch (Exception ignored) {}
                    }

                    if (list.isEmpty()) {
                        future.complete(0);
                    } else {
                        plugin.getDatabaseManager().importBatch(list).thenAccept(future::complete).exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }

    private class LiteBansSource implements MigrationSource {
        @Override public String getName() { return "litebans"; }
        @Override public String getDescription() { return "LiteBans database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlugin plugin, CommandSender sender, String[] args) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    // Try to autodetect configuration
                    File folder = new File(plugin.getDataFolder().getParentFile(), "LiteBans");
                    File configFile = new File(folder, "config.yml");
                    
                    String jdbcUrl = null;
                    String username = "";
                    String password = "";
                    String tablePrefix = "litebans_";

                    if (configFile.exists()) {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                        String driver = config.getString("driver", "sqlite");
                        tablePrefix = config.getString("table_prefix", "litebans_");
                        
                        if (driver.equalsIgnoreCase("sqlite")) {
                            File dbFile = new File(folder, "litebans.db");
                            if (dbFile.exists()) {
                                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                            }
                        } else {
                            String address = config.getString("address", "localhost:3306");
                            String dbName = config.getString("database", "litebans");
                            username = config.getString("username", "root");
                            password = config.getString("password", "");
                            jdbcUrl = "jdbc:mysql://" + address + "/" + dbName;
                        }
                    }

                    // CLI overrides
                    if (args.length >= 4) {
                        jdbcUrl = args[1];
                        username = args[2];
                        password = args[3];
                        if (args.length >= 5) {
                            tablePrefix = args[4];
                        }
                    }

                    if (jdbcUrl == null) {
                        future.completeExceptionally(new Exception("Could not autodetect LiteBans connection. Use: /stuffimport litebans <jdbcUrl> <user> <pass> [prefix]"));
                        return;
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

                    if (list.isEmpty()) {
                        future.complete(0);
                    } else {
                        plugin.getDatabaseManager().importBatch(list).thenAccept(future::complete).exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }

    private class AdvancedBanSource implements MigrationSource {
        @Override public String getName() { return "advancedban"; }
        @Override public String getDescription() { return "AdvancedBan database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlugin plugin, CommandSender sender, String[] args) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    File folder = new File(plugin.getDataFolder().getParentFile(), "AdvancedBan");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";

                    if (configFile.exists()) {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                        boolean isSqlite = config.getBoolean("MySQL.use", false) == false;
                        
                        if (isSqlite) {
                            File dbFile = new File(folder, "AdvancedBan.db");
                            if (dbFile.exists()) {
                                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                            }
                        } else {
                            String address = config.getString("MySQL.IP", "localhost") + ":" + config.getInt("MySQL.Port", 3306);
                            String dbName = config.getString("MySQL.DB", "advancedban");
                            username = config.getString("MySQL.User", "root");
                            password = config.getString("MySQL.Password", "");
                            jdbcUrl = "jdbc:mysql://" + address + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1];
                        username = args[2];
                        password = args[3];
                    }

                    if (jdbcUrl == null) {
                        future.completeExceptionally(new Exception("Could not autodetect AdvancedBan connection. Use: /stuffimport advancedban <jdbcUrl> <user> <pass>"));
                        return;
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
                                if (pType.contains("BAN")) {
                                    type = pType.contains("IP") ? "IP_BAN" : "BAN";
                                } else if (pType.contains("MUTE")) {
                                    type = "MUTE";
                                } else if (pType.contains("WARN")) {
                                    type = "WARN";
                                } else {
                                    continue; // Skip kicks or other events
                                }

                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = (endMs > 0 && endMs != -1) ? new Timestamp(endMs) : null;
                                
                                // Clean staff UUID check
                                String punisher = null;
                                if (staff != null && staff.length() == 36) punisher = staff;

                                list.add(new ImportedPunishment(uuid, null, punisher, type, reason, start, end, true));
                            }
                        }
                    }

                    if (list.isEmpty()) {
                        future.complete(0);
                    } else {
                        plugin.getDatabaseManager().importBatch(list).thenAccept(future::complete).exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }

    private class MaxBansSource implements MigrationSource {
        @Override public String getName() { return "maxbans"; }
        @Override public String getDescription() { return "MaxBans database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlugin plugin, CommandSender sender, String[] args) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    File folder = new File(plugin.getDataFolder().getParentFile(), "MaxBans");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";

                    if (configFile.exists()) {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                        boolean isSqlite = config.getBoolean("database.mysql", false) == false;

                        if (isSqlite) {
                            File dbFile = new File(folder, "maxbans.db");
                            if (dbFile.exists()) {
                                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                            }
                        } else {
                            String address = config.getString("database.host", "localhost") + ":" + config.getInt("database.port", 3306);
                            String dbName = config.getString("database.name", "maxbans");
                            username = config.getString("database.user", "root");
                            password = config.getString("database.password", "");
                            jdbcUrl = "jdbc:mysql://" + address + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1];
                        username = args[2];
                        password = args[3];
                    }

                    if (jdbcUrl == null) {
                        future.completeExceptionally(new Exception("Could not autodetect MaxBans connection. Use: /stuffimport maxbans <jdbcUrl> <user> <pass>"));
                        return;
                    }

                    List<ImportedPunishment> list = new ArrayList<>();
                    try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
                        // 1. Migrate standard bans (by name)
                        String query = "SELECT name, reason, banner, time, expires FROM bans";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("name");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("banner");
                                long startMs = rs.getLong("time");
                                long endMs = rs.getLong("expires");

                                String uuid = resolveNameToUuidStr(name);
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;

                                list.add(new ImportedPunishment(uuid, null, null, "BAN", reason, start, end, true));
                            }
                        }

                        // 2. Migrate IP bans
                        query = "SELECT ip, reason, banner, time, expires FROM ipbans";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String ip = rs.getString("ip");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("banner");
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
                                String staff = rs.getString("banner");
                                long startMs = rs.getLong("time");
                                long endMs = rs.getLong("expires");

                                String uuid = resolveNameToUuidStr(name);
                                Timestamp start = new Timestamp(startMs);
                                Timestamp end = endMs > 0 ? new Timestamp(endMs) : null;

                                list.add(new ImportedPunishment(uuid, null, null, "MUTE", reason, start, end, true));
                            }
                        }

                        // 4. Migrate warnings
                        query = "SELECT name, reason, banner, time FROM warnings";
                        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                String name = rs.getString("name");
                                String reason = rs.getString("reason");
                                String staff = rs.getString("banner");
                                long startMs = rs.getLong("time");

                                String uuid = resolveNameToUuidStr(name);
                                Timestamp start = new Timestamp(startMs);

                                list.add(new ImportedPunishment(uuid, null, null, "WARN", reason, start, null, true));
                            }
                        }
                    }

                    if (list.isEmpty()) {
                        future.complete(0);
                    } else {
                        plugin.getDatabaseManager().importBatch(list).thenAccept(future::complete).exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }

        private String resolveNameToUuidStr(String name) {
            try {
                OfflinePlayer op = Bukkit.getOfflinePlayer(name);
                return op.getUniqueId().toString();
            } catch (Exception e) {
                return null;
            }
        }
    }

    private class BanManagerSource implements MigrationSource {
        @Override public String getName() { return "banmanager"; }
        @Override public String getDescription() { return "BanManager database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlugin plugin, CommandSender sender, String[] args) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    File folder = new File(plugin.getDataFolder().getParentFile(), "BanManager");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";
                    String tablePrefix = "bm_";

                    if (configFile.exists()) {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                        String dbType = config.getString("database.driver", "sqlite");
                        
                        if (dbType.equalsIgnoreCase("sqlite")) {
                            File dbFile = new File(folder, "banmanager.db");
                            if (dbFile.exists()) {
                                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                            }
                        } else {
                            String address = config.getString("database.host", "localhost") + ":" + config.getInt("database.port", 3306);
                            String dbName = config.getString("database.name", "banmanager");
                            username = config.getString("database.user", "root");
                            password = config.getString("database.password", "");
                            jdbcUrl = "jdbc:mysql://" + address + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1];
                        username = args[2];
                        password = args[3];
                        if (args.length >= 5) {
                            tablePrefix = args[4];
                        }
                    }

                    if (jdbcUrl == null) {
                        future.completeExceptionally(new Exception("Could not autodetect BanManager connection. Use: /stuffimport banmanager <jdbcUrl> <user> <pass> [prefix]"));
                        return;
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

                    if (list.isEmpty()) {
                        future.complete(0);
                    } else {
                        plugin.getDatabaseManager().importBatch(list).thenAccept(future::complete).exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }

    private class BungeeAdminToolsSource implements MigrationSource {
        @Override public String getName() { return "bat"; }
        @Override public String getDescription() { return "BungeeAdminTools database (auto-detected or parameters)"; }

        @Override
        public CompletableFuture<Integer> migrate(StuffPlugin plugin, CommandSender sender, String[] args) {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            SchedulerUtils.runAsync(plugin, () -> {
                try {
                    File folder = new File(plugin.getDataFolder().getParentFile(), "BungeeAdminTools");
                    File configFile = new File(folder, "config.yml");

                    String jdbcUrl = null;
                    String username = "";
                    String password = "";
                    String tablePrefix = "bat_";

                    if (configFile.exists()) {
                        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                        String dbType = config.getString("database.driver", "sqlite");

                        if (dbType.equalsIgnoreCase("sqlite")) {
                            File dbFile = new File(folder, "bungeeadmintools.db");
                            if (dbFile.exists()) {
                                jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                            }
                        } else {
                            String address = config.getString("database.host", "localhost") + ":" + config.getInt("database.port", 3306);
                            String dbName = config.getString("database.name", "bungeeadmintools");
                            username = config.getString("database.user", "root");
                            password = config.getString("database.password", "");
                            jdbcUrl = "jdbc:mysql://" + address + "/" + dbName;
                        }
                    }

                    if (args.length >= 4) {
                        jdbcUrl = args[1];
                        username = args[2];
                        password = args[3];
                        if (args.length >= 5) {
                            tablePrefix = args[4];
                        }
                    }

                    if (jdbcUrl == null) {
                        future.completeExceptionally(new Exception("Could not autodetect BungeeAdminTools connection. Use: /stuffimport bat <jdbcUrl> <user> <pass> [prefix]"));
                        return;
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

                    if (list.isEmpty()) {
                        future.complete(0);
                    } else {
                        plugin.getDatabaseManager().importBatch(list).thenAccept(future::complete).exceptionally(ex -> {
                            future.completeExceptionally(ex);
                            return null;
                        });
                    }
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        }
    }
}
