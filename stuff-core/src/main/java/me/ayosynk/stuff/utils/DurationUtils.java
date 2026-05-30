package me.ayosynk.stuff.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtils {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)\\s*(y|mo|w|d|h|m|s)", Pattern.CASE_INSENSITIVE);

    private DurationUtils() {}

    /**
     * Parses a string representing a duration and returns the duration in milliseconds.
     * Returns -1 if the duration is permanent.
     * Returns -2 if parsing fails (wrong format).
     */
    public static long parseDuration(String input) {
        if (input == null || input.isEmpty()) {
            return -2;
        }

        if (input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("permanent") || input.equalsIgnoreCase("forever")) {
            return -1;
        }

        long totalMs = 0;
        Matcher matcher = PATTERN.matcher(input);
        boolean found = false;

        while (matcher.find()) {
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2).toLowerCase();

            switch (unit) {
                case "s":
                    totalMs += amount * 1000L;
                    break;
                case "m":
                    totalMs += amount * 60L * 1000L;
                    break;
                case "h":
                    totalMs += amount * 60L * 60L * 1000L;
                    break;
                case "d":
                    totalMs += amount * 24L * 60L * 60L * 1000L;
                    break;
                case "w":
                    totalMs += amount * 7L * 24L * 60L * 60L * 1000L;
                    break;
                case "mo":
                    totalMs += amount * 30L * 24L * 60L * 60L * 1000L;
                    break;
                case "y":
                    totalMs += amount * 365L * 24L * 60L * 60L * 1000L;
                    break;
            }
        }

        return found ? totalMs : -2;
    }

    /**
     * Formats a remaining time in milliseconds into a readable string.
     */
    public static String formatDuration(long ms) {
        if (ms <= 0) {
            return "Permanent";
        }

        long seconds = ms / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        long days = hours / 24L;

        seconds %= 60L;
        minutes %= 60L;
        hours %= 24L;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
