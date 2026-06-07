package me.ayosynk.staff.bukkit.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class MiniMessageUtils {

    private MiniMessageUtils() {}

    /**
     * Parses a MiniMessage-formatted string (including hex color tags) into an Adventure Component.
     */
    public static Component parse(String input) {
        if (input == null) {
            return Component.empty();
        }
        return Component.text()
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                .append(MiniMessage.miniMessage().deserialize(input))
                .build();
    }
}
