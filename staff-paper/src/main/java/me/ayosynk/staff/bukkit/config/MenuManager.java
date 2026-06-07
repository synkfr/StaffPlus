package me.ayosynk.staff.bukkit.config;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class MenuManager {

    private final StaffBukkitPlugin plugin;
    private StaffInfoMenuConfig staffInfoMenuConfig;

    public MenuManager(StaffBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    public StaffInfoMenuConfig getStaffInfoMenuConfig() {
        return staffInfoMenuConfig;
    }

    public void load() {
        File menusDir = new File(plugin.getDataFolder(), "menus");
        if (!menusDir.exists()) {
            menusDir.mkdirs();
        }

        File file = new File(menusDir, "staff_info.yml");
        if (!file.exists()) {
            plugin.saveResource("menus/staff_info.yml", false);
        }

        StaffInfoMenuConfig defaults = new StaffInfoMenuConfig();
        StaffInfoMenuConfig loaded = new StaffInfoMenuConfig();
        
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception e) {
            plugin.getLogger().warning("==================================================");
            plugin.getLogger().warning("YAML SYNTAX ERROR in menus/staff_info.yml!");
            plugin.getLogger().warning("Error details: " + e.getMessage());
            plugin.getLogger().warning("Using default Staff Info menu configuration.");
            plugin.getLogger().warning("==================================================");
            this.staffInfoMenuConfig = defaults;
            return;
        }

        // Title
        String title = yaml.getString("name");
        if (title == null) {
            plugin.getLogger().warning("[menus/staff_info.yml] Missing 'name' (title) property. Using default.");
            loaded.title = defaults.title;
        } else {
            loaded.title = title;
        }

        // Size
        int size = yaml.getInt("size", 54);
        if (size <= 0 || size % 9 != 0 || size > 54) {
            plugin.getLogger().warning("[menus/staff_info.yml] Invalid 'size' (" + size + "). Must be a multiple of 9 between 9 and 54. Using default (54).");
            loaded.size = defaults.size;
        } else {
            loaded.size = size;
        }

        // Fill Item
        if (yaml.contains("fill-item")) {
            ConfigurationSection fillSec = yaml.getConfigurationSection("fill-item");
            if (fillSec != null) {
                String matStr = fillSec.getString("material");
                if (matStr != null) {
                    Material mat = Material.matchMaterial(matStr);
                    if (mat == null) {
                        plugin.getLogger().warning("[menus/staff_info.yml] Invalid 'fill-item' material '" + matStr + "'. Ignoring fill item.");
                    } else {
                        loaded.fillItem = new StaffInfoMenuConfig.FillItem(
                            mat.name(),
                            fillSec.getString("name", " "),
                            fillSec.getStringList("lore").toArray(new String[0])
                        );
                    }
                }
            }
        }

        ConfigurationSection itemsSection = yaml.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("[menus/staff_info.yml] Missing 'items' configuration section. Using defaults.");
            this.staffInfoMenuConfig = defaults;
            return;
        }

        // Load each of the expected item keys
        for (Map.Entry<String, StaffInfoMenuConfig.MenuItem> entry : defaults.items.entrySet()) {
            String key = entry.getKey();
            StaffInfoMenuConfig.MenuItem defaultItem = entry.getValue();
            ConfigurationSection itemSec = itemsSection.getConfigurationSection(key);

            if (itemSec == null) {
                plugin.getLogger().warning("[menus/staff_info.yml] Missing item key 'items." + key + "'. Falling back to default layout for this item.");
                loaded.items.put(key, defaultItem);
                continue;
            }

            StaffInfoMenuConfig.MenuItem loadedItem = new StaffInfoMenuConfig.MenuItem();

            // Slots/Slot parsing
            if (itemSec.contains("slots")) {
                List<Integer> slots = itemSec.getIntegerList("slots");
                for (int slot : slots) {
                    if (slot < 0 || slot >= loaded.size) {
                        plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + "' has out of bounds slot '" + slot + "'. Slot must be between 0 and " + (loaded.size - 1) + ".");
                    } else {
                        loadedItem.slots.add(slot);
                    }
                }
                if (loadedItem.slots.isEmpty()) {
                    loadedItem.slots = new ArrayList<>(defaultItem.slots);
                }
            } else {
                int slot = itemSec.getInt("slot", -1);
                if (slot < -1 || slot >= loaded.size) {
                    plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + "' has out of bounds slot '" + slot + "'. Slot must be between 0 and " + (loaded.size - 1) + ". Using default slot (" + defaultItem.slot + ").");
                    loadedItem.slot = defaultItem.slot;
                } else {
                    loadedItem.slot = slot;
                }
            }

            // Material parsing under 'item.material'
            ConfigurationSection itemDataSec = itemSec.getConfigurationSection("item");
            if (itemDataSec == null) {
                plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + "' is missing the 'item' sub-section. Using defaults for item properties.");
                loadedItem.material = defaultItem.material;
                loadedItem.name = defaultItem.name;
                loadedItem.lore = defaultItem.lore;
            } else {
                String matStr = itemDataSec.getString("material");
                if (matStr == null) {
                    plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + ".item' is missing 'material'. Using default (" + defaultItem.material + ").");
                    loadedItem.material = defaultItem.material;
                } else {
                    Material mat = Material.matchMaterial(matStr);
                    if (mat == null) {
                        plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + ".item' has invalid material '" + matStr + "'. Using default (" + defaultItem.material + ").");
                        loadedItem.material = defaultItem.material;
                    } else {
                        loadedItem.material = mat.name();
                    }
                }

                // Name parsing
                String name = itemDataSec.getString("name");
                if (name == null) {
                    loadedItem.name = defaultItem.name;
                } else {
                    loadedItem.name = name;
                }

                // Lore parsing
                if (itemDataSec.contains("lore")) {
                    loadedItem.lore = itemDataSec.getStringList("lore");
                } else {
                    loadedItem.lore = defaultItem.lore;
                }
            }

            loaded.items.put(key, loadedItem);
        }

        this.staffInfoMenuConfig = loaded;
    }
}
