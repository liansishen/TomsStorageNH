package com.hepdd.toms_storage;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static final int SEARCH_AUTO_FOCUS = 1;
    public static final int SEARCH_KEEP_TEXT = 2;
    public static final int SEARCH_SYNC_NEI = 4;
    private static final int SEARCH_PRESET_STANDARD = 0;
    private static final int SEARCH_PRESET_AUTO = SEARCH_AUTO_FOCUS;
    private static final int SEARCH_PRESET_AUTO_KEEP = SEARCH_AUTO_FOCUS | SEARCH_KEEP_TEXT;
    private static final int SEARCH_PRESET_NEI = SEARCH_AUTO_FOCUS | SEARCH_KEEP_TEXT | SEARCH_SYNC_NEI;

    public static boolean onlyTrimsConnect = false;
    public static boolean debugNeiAutocrafting = false;
    public static int inventoryConnectorRange = 16;
    public static int maxInventoryCableNodes = 1024;
    public static int wirelessReach = 16;
    public static int terminalSearchMode = SEARCH_PRESET_AUTO_KEEP;
    public static String terminalLastSearch = "";
    public static boolean terminalSearchMigrated = false;

    private static Configuration configuration;

    public static int getInventoryConnectorRangeSquared() {
        return inventoryConnectorRange * inventoryConnectorRange;
    }

    public static void synchronizeConfiguration(File configFile) {
        configuration = new Configuration(configFile);

        inventoryConnectorRange = configuration.getInt(
            "inventoryConnectorRange",
            Configuration.CATEGORY_GENERAL,
            inventoryConnectorRange,
            4,
            256,
            "Inventory Connector scan range.");
        maxInventoryCableNodes = configuration.getInt(
            "maxInventoryCableNodes",
            Configuration.CATEGORY_GENERAL,
            maxInventoryCableNodes,
            16,
            65536,
            "Maximum cable blocks scanned by one Inventory Connector.");
        onlyTrimsConnect = configuration.getBoolean(
            "onlyTrimsConnect",
            Configuration.CATEGORY_GENERAL,
            onlyTrimsConnect,
            "Only allow trims to connect inventories.");
        debugNeiAutocrafting = configuration.getBoolean(
            "debugNeiAutocrafting",
            Configuration.CATEGORY_GENERAL,
            debugNeiAutocrafting,
            "Log NEI autocrafting integration debug output.");
        wirelessReach = configuration
            .getInt("wirelessReach", Configuration.CATEGORY_GENERAL, wirelessReach, 4, 64, "Wireless terminal reach.");
        terminalSearchMode = configuration.getInt(
            "terminalSearchMode",
            "client",
            terminalSearchMode,
            0,
            SEARCH_AUTO_FOCUS | SEARCH_KEEP_TEXT | SEARCH_SYNC_NEI,
            "Terminal search options bit mask: 1 auto focus, 2 keep text, 4 synchronize with NEI.");
        terminalSearchMode = normalizeSearchMode(terminalSearchMode);
        configuration.get("client", "terminalSearchMode", terminalSearchMode)
            .set(terminalSearchMode);
        terminalLastSearch = configuration
            .getString("terminalLastSearch", "client", terminalLastSearch, "Last locally retained terminal search.");
        terminalSearchMigrated = configuration.getBoolean(
            "terminalSearchMigrated",
            "client",
            terminalSearchMigrated,
            "Whether the old per-terminal search value has been migrated to the local setting.");

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static boolean hasSearchOption(int option) {
        return (terminalSearchMode & option) != 0;
    }

    public static void saveTerminalSearchSettings(int mode, String search, boolean migrated) {
        terminalSearchMode = normalizeSearchMode(mode);
        terminalLastSearch = search == null ? "" : search;
        terminalSearchMigrated = migrated;
        if (configuration == null) return;
        configuration.get("client", "terminalSearchMode", terminalSearchMode)
            .set(terminalSearchMode);
        configuration.get("client", "terminalLastSearch", terminalLastSearch)
            .set(terminalLastSearch);
        configuration.get("client", "terminalSearchMigrated", terminalSearchMigrated)
            .set(terminalSearchMigrated);
        if (configuration.hasChanged()) configuration.save();
    }

    public static int normalizeSearchMode(int mode) {
        int masked = mode & SEARCH_PRESET_NEI;
        if ((masked & SEARCH_SYNC_NEI) != 0) return SEARCH_PRESET_NEI;
        if ((masked & SEARCH_KEEP_TEXT) != 0) return SEARCH_PRESET_AUTO_KEEP;
        if ((masked & SEARCH_AUTO_FOCUS) != 0) return SEARCH_PRESET_AUTO;
        return SEARCH_PRESET_STANDARD;
    }
}
