package me.ayosynk.staff.bukkit.config;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Names;
import eu.okaeri.configs.annotation.NameStrategy;

import java.util.*;

@Names(strategy = NameStrategy.HYPHEN_CASE)
public class StaffInfoMenuConfig extends OkaeriConfig {

    public String name = "<color:#A0A0A0>Staff Info: <color:#00E262>{player}";
    public int size = 54;

    public static class MenuItem extends OkaeriConfig {
        public int slot = -1;
        public List<Integer> slots = new ArrayList<>();

        public static class ItemProperties extends OkaeriConfig {
            public String material;
            public String name;
            public List<String> lore = new ArrayList<>();

            public ItemProperties(String material, String name, String... lore) {
                this.material = material;
                this.name = name;
                this.lore = Arrays.asList(lore);
            }

            public ItemProperties() {}
        }

        public ItemProperties item = new ItemProperties();

        public MenuItem(String material, String name, int slot, String... lore) {
            this.slot = slot;
            this.item = new ItemProperties(material, name, lore);
        }

        public MenuItem(String material, String name, List<Integer> slots, String... lore) {
            this.slots = slots;
            this.item = new ItemProperties(material, name, lore);
        }

        public MenuItem() {}
    }

    public static class FillItem extends OkaeriConfig {
        public String material;
        public String name = " ";
        public List<String> lore = new ArrayList<>();

        public FillItem(String material, String name, String... lore) {
            this.material = material;
            this.name = name;
            this.lore = Arrays.asList(lore);
        }

        public FillItem() {}
    }

    public Map<String, MenuItem> items = new LinkedHashMap<>();
    public FillItem fillItem = null;

    public StaffInfoMenuConfig() {
        // Default Configuration
        items.put("border_item", new MenuItem(
            "GRAY_STAINED_GLASS_PANE",
            " ",
            Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 50, 51, 52, 53)
        ));
        items.put("profile_head", new MenuItem(
            "PLAYER_HEAD",
            "<color:#00E262>{player}",
            13,
            "<color:#A0A0A0>UUID: <color:#FFFFFF>{uuid}",
            "<color:#A0A0A0>IP Address: <color:#FFFFFF>{ip}",
            "<color:#A0A0A0>Hierarchy Weight: <color:#00E262>{weight}"
        ));
        items.put("session_status", new MenuItem(
            "COMPASS",
            "<color:#E2B700>Session Status",
            11,
            "<color:#A0A0A0>Status: {status}",
            "<color:#A0A0A0>GameMode: <color:#FFFFFF>{gamemode}",
            "<color:#A0A0A0>Flying: <color:#FFFFFF>{flying}",
            "<color:#A0A0A0>Vanished: <color:#FFFFFF>{vanished}",
            "<color:#A0A0A0>Ping: <color:#FFFFFF>{ping}ms"
        ));
        items.put("location_info", new MenuItem(
            "GRASS_BLOCK",
            "<color:#00C2E2>Location Info",
            15,
            "<color:#A0A0A0>World: <color:#FFFFFF>{world}",
            "<color:#A0A0A0>X: <color:#FFFFFF>{x}",
            "<color:#A0A0A0>Y: <color:#FFFFFF>{y}",
            "<color:#A0A0A0>Z: <color:#FFFFFF>{z}"
        ));
        items.put("database_statistics", new MenuItem(
            "BOOK",
            "<color:#FFAA00>Database Statistics",
            22,
            "<color:#A0A0A0>Active Warnings: <color:#FFAA00>{warnings}",
            "<color:#A0A0A0>Alt Accounts on IP: <color:#FFAA00>{alts}",
            "<color:#A0A0A0>IP Ban Exempted: <color:#FFFFFF>{allowed}"
        ));
        items.put("teleport_target", new MenuItem(
            "ENDER_PEARL",
            "<color:#00E262>Teleport to Target",
            29,
            "<color:#A0A0A0>Click to teleport to player's current location."
        ));
        items.put("inspect_inventory", new MenuItem(
            "CHEST",
            "<color:#00E262>Inspect Inventory",
            30,
            "<color:#A0A0A0>Click to open live inventory viewer (/invsee)."
        ));
        items.put("warnings_log", new MenuItem(
            "BOOKSHELF",
            "<color:#FFAA00>Active Warnings Profile",
            31,
            "{warnings_list}",
            " ",
            "<color:#FFAA00>Click to clear all warnings."
        ));
        items.put("exemption_status", new MenuItem(
            "BEACON",
            "<color:#00E262>Exemption Status",
            32,
            "<color:#A0A0A0>Allows player to bypass active IP-bans.",
            " ",
            "{exemption_action}"
        ));
        items.put("view_history", new MenuItem(
            "PAPER",
            "<color:#00E262>View Logs History",
            33,
            "<color:#A0A0A0>Click to print full punishment history to chat."
        ));
        items.put("kick_player", new MenuItem(
            "FEATHER",
            "<color:#E2B700>Kick Player",
            38,
            "<color:#A0A0A0>Click to kick player from the server."
        ));
        items.put("warn_player", new MenuItem(
            "GOLDEN_AXE",
            "<color:#FFAA00>Warn Player",
            39,
            "<color:#A0A0A0>Click to issue warning (Reason: GUI Warning)."
        ));
        items.put("mute_player", new MenuItem(
            "BELL",
            "<color:#E2B700>Mute Player",
            40,
            "<color:#A0A0A0>Click to mute player for 1 hour (Reason: GUI Mute)."
        ));
        items.put("ban_player", new MenuItem(
            "NETHERITE_SWORD",
            "<color:#E20000>Ban Player",
            41,
            "<color:#A0A0A0>Click to ban player permanently (Reason: GUI Ban)."
        ));
        items.put("close_gui", new MenuItem(
            "BARRIER",
            "<color:#E20000>Close GUI",
            49
        ));
    }
}
