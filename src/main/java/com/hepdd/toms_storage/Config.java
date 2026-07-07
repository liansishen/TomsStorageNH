package com.hepdd.toms_storage;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    public static boolean onlyTrimsConnect = false;
    public static boolean debugNeiAutocrafting = false;
    public static int inventoryConnectorRange = 16;
    public static int wirelessReach = 16;

    public static int getInventoryConnectorRangeSquared() {
        return inventoryConnectorRange * inventoryConnectorRange;
    }

    public static void synchronizeConfiguration(File configFile) {
        Configuration configuration = new Configuration(configFile);

        inventoryConnectorRange = configuration.getInt(
            "inventoryConnectorRange",
            Configuration.CATEGORY_GENERAL,
            inventoryConnectorRange,
            4,
            256,
            "Inventory Connector scan range.");
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

        if (configuration.hasChanged()) {
            configuration.save();
        }
    }
}
