# Tom's Simple Storage 1.7.10 Port

[简体中文](README_zh.md)

Tom's Simple Storage 1.7.10 Port is a lightweight backport of [Tom's Simple Storage](https://github.com/tom5454/Toms-Storage) for Minecraft 1.7.10 and the GT New Horizons ecosystem.

The mod is intended for early-game inventory management and simple automation. It provides convenient access to ordinary containers without replacing the larger storage and logistics systems available later in a GTNH playthrough. This is a focused port rather than a complete backport of every upstream feature.

## Download

- [Latest GitHub release](https://github.com/liansishen/TomsStorageNH/releases/latest)
- [All releases](https://github.com/liansishen/TomsStorageNH/releases)

## Required Dependencies

- NotEnoughItems, using the GTNH line or a compatible version
- UniMixins

The NEI integration uses GTNH NEI APIs and client-side mixins. A compatible NEI build must provide `AutoCraftingManager` and `DefaultOverlayHandler`.

NotEnoughCharacters is optional and improves Chinese and pinyin searching.

## Features

### Storage Network

- Inventory Connectors combine touching inventories into one storage network.
- Inventory Trim extends the connected path between inventories.
- Storage Terminals provide centralized insertion, extraction, sorting and searching.
- Crafting Terminals add a crafting grid with NEI recipe transfer and auto-crafting integration.
- Wireless Terminals can be bound to a terminal and used within the configured range and dimension.

### Search

- Searches item names and tooltips; prefix a query with `@` to search by mod.
- Optional NotEnoughCharacters support for Chinese and pinyin matching.
- One search button cycles through four presets:
  1. Standard search
  2. Auto search
  3. Auto search with retained text
  4. Auto search with retained text and NEI synchronization
- Search state is stored locally, so multiple players using the same terminal do not overwrite each other.

### Basic Automation

- Inventory Cables connect only to other cables, an Inventory Connector, or the network side of a Basic Inventory Hopper.
- Cables do not directly connect ordinary inventories, machines, terminals or Inventory Trim.
- Multiple hoppers may share one cable network. A cable network containing multiple Inventory Connectors is disabled as a conflict.
- The Basic Inventory Hopper transfers one item every 10 ticks and respects sided inventories and redstone disabling.
- Its wide end is the input and its narrow nozzle is the output:
  - Connect the cable to the wide end to export from the storage network into the nozzle-side inventory.
  - Connect the cable to the narrow end to import from the wide-side inventory into the storage network.
- Exporting from the storage network requires an item filter. Import filters are optional.
- Right-click the hopper with an item to set its filter, or sneak-right-click it with an empty hand to clear the filter.

### Inventory Connector Range Preview

- Sneak-right-click an Inventory Connector with an empty hand to toggle a colored spherical range preview.
- The sphere represents the local inventory/Trim scan radius. Inventories still require a continuous connected path.
- Cable networks use their own node limit and are not restricted by this sphere.

## Quick Start

1. Place an Inventory Connector against the inventories you want to combine. Use Inventory Trim to extend the local connected path.
2. Place a Storage Terminal or Crafting Terminal against the Inventory Connector to access the complete network.
3. For automation, run Inventory Cable from the connector to one or more Basic Inventory Hoppers.
4. Orient each hopper so its narrow nozzle points toward the intended output. Attach the cable to the opposite end for network export, or to the nozzle for network import.
5. Set an item filter before exporting from the storage network.

## Building

Run the repository Gradle wrapper:

```shell
./gradlew build
```

The reobfuscated release jar is generated under `build/libs`.

## Credits and License

This project is based on the concepts and assets of [Tom's Simple Storage](https://github.com/tom5454/Toms-Storage).

This project is licensed under the [GPL-3.0 License](LICENSE).
