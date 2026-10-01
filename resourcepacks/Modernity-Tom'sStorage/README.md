# Modernity adapter for Tom's Simple Storage

This resource pack provides Modernity-style storage-terminal and crafting-terminal GUI textures for the Minecraft 1.7.10 port of Tom's Simple Storage. The cover uses the Modernity frame with a central T button.

## Installation

1. Download `Modernity-TomsStorage-<version>.zip` from the matching mod release.
2. Place the ZIP in Minecraft's `resourcepacks` directory.
3. Enable Modernity and this adapter in the Resource Packs menu, with this adapter above Modernity so its GUI textures take priority.

The adapter requires the Tom's Simple Storage mod. Keep the downloaded ZIP intact; its `pack.mcmeta`, `pack.png` and `assets/` entries are already at the archive root.

## Local packaging

Run `./gradlew packageResourcePacks` from the repository root. The archive is generated under `build/resourcepacks/`; use `-PresourcePackVersion=<version>` to set its version explicitly. See [the release workflow](../../docs/releasing.md) for publishing steps.
