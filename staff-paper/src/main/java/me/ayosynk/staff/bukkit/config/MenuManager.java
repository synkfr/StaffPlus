package me.ayosynk.staff.bukkit.config;

import me.ayosynk.staff.bukkit.StaffBukkitPlugin;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.bukkit.YamlBukkitConfigurer;
import org.bukkit.Material;

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

        StaffInfoMenuConfig defaults = new StaffInfoMenuConfig();

        try {
            this.staffInfoMenuConfig = ConfigManager.create(StaffInfoMenuConfig.class, (it) -> {
                it.withConfigurer(new YamlBukkitConfigurer());
                it.withBindFile(file);
                it.saveDefaults();
                it.load(true);
            });
        } catch (Exception e) {
            plugin.getLogger().warning("==================================================");
            plugin.getLogger().warning("YAML SYNTAX ERROR or validation failure in menus/staff_info.yml!");
            plugin.getLogger().warning("Error details: " + e.getMessage());
            plugin.getLogger().warning("Using default Staff Info menu configuration.");
            plugin.getLogger().warning("==================================================");
            this.staffInfoMenuConfig = defaults;
            return;
        }

        // Logical Validation and Sanitization
        validateAndSanitize(defaults);
    }

    private void validateAndSanitize(StaffInfoMenuConfig defaults) {
        // Size validation
        if (staffInfoMenuConfig.size <= 0 || staffInfoMenuConfig.size % 9 != 0 || staffInfoMenuConfig.size > 54) {
            plugin.getLogger().warning("[menus/staff_info.yml] Invalid 'size' (" + staffInfoMenuConfig.size + "). Must be a multiple of 9 between 9 and 54. Using default (54).");
            staffInfoMenuConfig.size = defaults.size;
        }

        // Fill Item validation
        if (staffInfoMenuConfig.fillItem != null) {
            String matStr = staffInfoMenuConfig.fillItem.material;
            if (matStr == null || Material.matchMaterial(matStr) == null) {
                plugin.getLogger().warning("[menus/staff_info.yml] Invalid 'fill-item' material '" + matStr + "'. Disabling fill item.");
                staffInfoMenuConfig.fillItem = null;
            }
        }

        // Items validation
        for (Map.Entry<String, StaffInfoMenuConfig.MenuItem> entry : defaults.items.entrySet()) {
            String key = entry.getKey();
            StaffInfoMenuConfig.MenuItem defaultItem = entry.getValue();
            StaffInfoMenuConfig.MenuItem loadedItem = staffInfoMenuConfig.items.get(key);

            if (loadedItem == null) {
                plugin.getLogger().warning("[menus/staff_info.yml] Missing item key 'items." + key + "'. Using default layout for this item.");
                staffInfoMenuConfig.items.put(key, defaultItem);
                continue;
            }

            // Ensure item properties exist
            if (loadedItem.item == null) {
                loadedItem.item = new StaffInfoMenuConfig.MenuItem.ItemProperties(
                    defaultItem.item.material,
                    defaultItem.item.name,
                    defaultItem.item.lore.toArray(new String[0])
                );
            }

            // Material validation
            if (loadedItem.item.material == null) {
                plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + ".item' is missing 'material'. Using default (" + defaultItem.item.material + ").");
                loadedItem.item.material = defaultItem.item.material;
            } else {
                Material mat = Material.matchMaterial(loadedItem.item.material);
                if (mat == null) {
                    plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + ".item' has invalid material '" + loadedItem.item.material + "'. Using default (" + defaultItem.item.material + ").");
                    loadedItem.item.material = defaultItem.item.material;
                } else {
                    loadedItem.item.material = mat.name();
                }
            }

            // Slots/Slot validation
            if (loadedItem.slots != null && !loadedItem.slots.isEmpty()) {
                List<Integer> validSlots = new ArrayList<>();
                for (int slot : loadedItem.slots) {
                    if (slot < 0 || slot >= staffInfoMenuConfig.size) {
                        plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + "' has out of bounds slot '" + slot + "'. Slot must be between 0 and " + (staffInfoMenuConfig.size - 1) + ".");
                    } else {
                        validSlots.add(slot);
                    }
                }
                loadedItem.slots = validSlots;
                if (loadedItem.slots.isEmpty()) {
                    loadedItem.slots = new ArrayList<>(defaultItem.slots);
                }
            } else {
                if (loadedItem.slot < -1 || loadedItem.slot >= staffInfoMenuConfig.size) {
                    plugin.getLogger().warning("[menus/staff_info.yml] Item 'items." + key + "' has out of bounds slot '" + loadedItem.slot + "'. Slot must be between 0 and " + (staffInfoMenuConfig.size - 1) + ". Using default slot (" + defaultItem.slot + ").");
                    loadedItem.slot = defaultItem.slot;
                }
            }

            // Name/Lore defaults fallback
            if (loadedItem.item.name == null) {
                loadedItem.item.name = defaultItem.item.name;
            }
            if (loadedItem.item.lore == null || loadedItem.item.lore.isEmpty()) {
                loadedItem.item.lore = defaultItem.item.lore;
            }
        }
    }
}
