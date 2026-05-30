package me.ayosynk.stuff.utils;

import me.ayosynk.stuff.StuffPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class DiscordWebhookUtils {

    private static final HttpClient client = HttpClient.newBuilder().build();

    public static CompletableFuture<Void> sendEmbed(StuffPlugin plugin, String title, String colorHex, String player, String staff, String duration, String reason) {
        if (!plugin.getPluginConfig().isDiscordWebhookEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        String webhookUrl = plugin.getPluginConfig().getDiscordWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                int color = Integer.parseInt(colorHex, 16);
                String timestamp = Instant.now().toString();

                String json = "{\n" +
                        "  \"username\": \"" + escapeJson(plugin.getPluginConfig().getDiscordWebhookUsername()) + "\",\n" +
                        "  \"avatar_url\": \"" + escapeJson(plugin.getPluginConfig().getDiscordWebhookAvatarUrl()) + "\",\n" +
                        "  \"embeds\": [{\n" +
                        "    \"title\": \"" + escapeJson(title) + "\",\n" +
                        "    \"color\": " + color + ",\n" +
                        "    \"timestamp\": \"" + timestamp + "\",\n" +
                        "    \"fields\": [\n" +
                        "      {\"name\": \"Player\", \"value\": \"" + escapeJson(player) + "\", \"inline\": true},\n" +
                        "      {\"name\": \"Staff Member\", \"value\": \"" + escapeJson(staff) + "\", \"inline\": true},\n" +
                        "      {\"name\": \"Duration\", \"value\": \"" + escapeJson(duration) + "\", \"inline\": true},\n" +
                        "      {\"name\": \"Reason\", \"value\": \"" + escapeJson(reason) + "\", \"inline\": false}\n" +
                        "    ]\n" +
                        "  }]\n" +
                        "}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(response -> {
                            if (response.statusCode() >= 300) {
                                plugin.getLogger().warning("Discord webhook returned status code: " + response.statusCode() + " Response: " + response.body());
                            }
                        });
            } catch (Exception e) {
                plugin.getLogger().warning("Error sending Discord webhook: " + e.getMessage());
            }
        });
    }

    private static String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
