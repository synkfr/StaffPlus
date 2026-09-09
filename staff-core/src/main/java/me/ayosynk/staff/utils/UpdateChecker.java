package me.ayosynk.staff.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.ayosynk.staff.StaffPlatform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class UpdateChecker {

    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/staff+/version";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/staff%2B/versions";

    private final StaffPlatform platform;
    private final HttpClient httpClient;

    private volatile boolean updateAvailable = false;
    private volatile String latestVersion = "";

    public UpdateChecker(StaffPlatform platform) {
        this.platform = platform;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<Boolean> checkForUpdates() {
        if (!platform.getPluginConfig().isUpdateCheckerEnabled()) {
            return CompletableFuture.completedFuture(false);
        }

        String currentVersion = platform.getPluginVersion();
        String userAgent = "StaffPlus-UpdateChecker/" + currentVersion + " (https://modrinth.com/plugin/staff+)";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MODRINTH_API_URL))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return false;
                    }

                    try {
                        JsonElement element = JsonParser.parseString(response.body());
                        if (!element.isJsonArray()) {
                            return false;
                        }

                        JsonArray versions = element.getAsJsonArray();
                        if (versions.isEmpty()) {
                            return false;
                        }

                        JsonObject latest = versions.get(0).getAsJsonObject();
                        String remoteVersion = latest.get("version_number").getAsString();
                        this.latestVersion = remoteVersion;

                        if (isNewer(currentVersion, remoteVersion)) {
                            this.updateAvailable = true;
                            platform.getLogger().info("A new version of Staff+ is available: v" + remoteVersion + " (Current: v" + currentVersion + ")");
                            platform.getLogger().info("Download at: " + MODRINTH_PROJECT_URL);
                            return true;
                        }
                    } catch (Exception e) {
                        platform.getLogger().warning("Failed to parse Modrinth update response: " + e.getMessage());
                    }
                    return false;
                })
                .exceptionally(ex -> {
                    platform.getLogger().warning("Unable to check for updates: " + ex.getMessage());
                    return false;
                });
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getModrinthUrl() {
        return MODRINTH_PROJECT_URL;
    }

    public static boolean isNewer(String current, String remote) {
        if (remote == null || remote.isEmpty() || current == null || current.isEmpty()) {
            return false;
        }

        String c = current.trim();
        if (c.startsWith("v") || c.startsWith("V")) {
            c = c.substring(1);
        }

        String r = remote.trim();
        if (r.startsWith("v") || r.startsWith("V")) {
            r = r.substring(1);
        }

        String[] currentParts = c.split("[.-]");
        String[] remoteParts = r.split("[.-]");

        int length = Math.max(currentParts.length, remoteParts.length);
        for (int i = 0; i < length; i++) {
            int currentNum = 0;
            int remoteNum = 0;

            if (i < currentParts.length) {
                try {
                    currentNum = Integer.parseInt(currentParts[i]);
                } catch (NumberFormatException ignored) {}
            }

            if (i < remoteParts.length) {
                try {
                    remoteNum = Integer.parseInt(remoteParts[i]);
                } catch (NumberFormatException ignored) {}
            }

            if (remoteNum > currentNum) {
                return true;
            } else if (remoteNum < currentNum) {
                return false;
            }
        }

        return false;
    }
}
