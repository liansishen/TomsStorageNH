# Storage container compatibility

The Inventory Connector and both terminals accept explicitly recognised item-storage containers. Processing machines, automation buffers, equipment slots, storage-cell slots and fluid containers do not join the storage network. Rejected machines also stop the local inventory scan from continuing through them.

The inventory hopper can still transfer items between the network and machines. Its external inventory keeps the machine's sided insertion and extraction rules.

## Inventory survey

The survey covered the 222 mod JARs in a local GTNH 2.9.0-beta-2 server installation, including the merged GregTech/GT++ contents. Relevant storage APIs were also checked against the GTNHLib 0.11.51 dependency and GT5-Unofficial 5.09.54.183 used by GTNH 2.9.0-RC-1. This is a source/bytecode compatibility survey; it does not constitute an in-game test of every container and upgrade combination.

Optional containers are recognised by their exact class names and superclass chains. Their mods remain optional; the compatibility code does not load missing mod classes.

## Supported storage families

| Mod | Containers | Storage handling |
| --- | --- | --- |
| Minecraft | Chests and trapped chests | Each chest contributes its own 27 slots, including both halves of a double chest. |
| IronChest | All chest materials derived from `TileEntityIronChest` | Uses the material's actual slot count. |
| Et Futurum | Barrels and shulker boxes, including metal variants | Uses the material's actual slot count. |
| JABBA | Item barrels | Native GTNHLib item capability supplies the full stored quantity, independently of `exposeFullStorageSize`. Linked ender barrels share one storage identity. Sided input/output permissions remain enforced. |
| Storage Drawers | Standard, multi-drawer and compacting drawers | Native item capability exposes the drawer storage, excluding the duplicate input/output views and upgrades. Sided input/output settings remain enforced. |
| GregTech | Super Chests and Quantum Chests | The digital-chest capability exposes virtual storage and the actual output buffer. The display copy is excluded; cover permissions and external-access restrictions remain enforced. |
| GT++ | Legacy tiered chests and creative item holders | Legacy chests expose their real stored count and output buffer; native capability is used when present. |
| GT++ | Decayables chest | Its 15 inventory slots remain storage; the chest retains its own item-decay behaviour. |
| ProjectRed | Exploration barrels | Virtual stored items and the output buffer are counted separately. The top is insertion-only and the bottom is extraction-only. |
| EnderStorage | Ender chests | Shared storage is counted once for the same frequency and owner. |
| Extra Utilities | Full chests, mini chests, basic and advanced filing cabinets | Filing cabinets expose stored entries, keep buffered input out of the list, and accept deposits through their input slot. |
| Avaritiaddons | Compressed and Infinity Chests | Actual storage slots and large per-slot quantities are supported. The Infinity Chest's extra input slot contributes no displayed items. |
| Applied Energistics 2 | Sky Stone Chest | Its 36 item slots are accepted. |
| Tinkers' Construct | TiC, pattern and part chests | Actual storage slots, preserving item restrictions. |
| Forestry | Naturalist, apiarist, arborist and lepidopterist chests | Species-filtered storage slots. |
| Binnie Core | Compartment chests, including copper, bronze, iron, gold and diamond variants | All tabs contribute their actual storage slots (100, 150, 200, 200, 300 and 400). The shared machine tile is accepted only when it contains a compartment inventory component. |
| BiblioCraft / BiblioWoods | Framed chests, bookcases, shelves, potion shelves, weapon racks, armour stands and sword pedestals | Locked containers are inaccessible to the storage network. Wood variants inherit the same rules. |
| BiblioCraft | Weapon cases | Only the weapon slot is exposed; the carpet lining is excluded. Locks remain enforced. |
| BiblioCraft | Cookie jars, disc racks and dinner plates | Actual storage slots, preserving item restrictions. |
| Cooking for Blockheads | Counters, cabinets, corner variants, fridges and tool racks | Double fridges contribute each internal 27-slot inventory once. The owning tile is marked dirty after changes. |
| Thaumcraft | Hungry Chests and Magic Boxes | Actual item inventory; boxes sharing a master are counted once. |
| Thaumic Exploration | Bound chests | Bound contents are counted once per world and positive chest ID. Unbound chests retain independent inventories. |
| Automagy | Eager, greedy and tenacious chests | Actual chest storage slots. |
| Witchery | Leech chests and poppet shelves | Actual item storage slots. |
| Galacticraft / GalaxySpace | Treasure chests and inherited tier variants | Locked chests expose no items and accept no transfers. |
| Amun-Ra | Small and large chests | Actual chest storage slots. |
| Railcraft | Railcraft, metal and void chests | Actual chest storage, retaining each chest's own behaviour. |
| Carpenter's Blocks | Carpenter's Safe, including its capacity upgrade | Only exposed slots are accessed, with the safe's automation permissions and facing restrictions. |
| Chisel | Present chests | Both halves use one parent inventory and are counted once. |
| GTNH Core | Baby chest | Its single item-storage slot. |
| Ender IO | Vacuum Chest | Actual chest storage slots. |
| OpenPrinter | File cabinet | File-storage slots; the shredder is excluded. |
| Draconic Evolution | Draconium Chest | The 234 storage slots; smelting inputs, energy and crafting inventory remain separate. |
| Adventure Backpack | Placed backpacks | Only the main inventory exposed by the backpack's sided API; tanks and auxiliary slots are excluded. Held backpacks are outside the block scan. |

## Access and shared inventories

The terminal list contains items extractable from the connected side. The same side's insertion rules still determine where deposits can go. This keeps a locked or inaccessible slot out of recipe availability and automatic ingredient selection.

Shared inventories are deduplicated inside a connector. Both halves of a double fridge are split into their internal inventories; present chests use their parent; EnderStorage chests and linked JABBA barrels use their backing storage; bound chests use their world and positive ID. Unbound chests keep their own identities. The hopper's loop check also recognises these storage identities.

Compacting drawers retain their native item-capability representation. Other mods' changes to a linked inventory or container capacity become visible on the next normal network scan.

## Surveyed exclusions

- Processing machines, furnaces, brewing stands, crafting crates, worktables, ovens, toasters, printers, shredders and other processing inventories.
- GT item buffers, filters, distributors, input/output buses and hatches, including compressed buses; these are automation endpoints.
- AE2 ME Chests, drives and interfaces: their exposed inventories contain cells or interface buffers. The Sky Stone Chest is supported separately.
- Drawer controllers and slaves, RemoteIO, transvector interfaces, magical mirrors and other inventory proxies: these do not hold their own item storage.
- GT lockers and IC2 Personal Safes: they prohibit external item automation. Personal Safes also require player ownership checks that an automatic storage network cannot perform.
- Display/control components such as signs, labels, clipboards and painting frames, and mixed processing/utility slots in desks and presses.
- Fluid tanks, drums, liquid barrels, battery storage, chest covers, player inventories, held bags and minecart entities.

## Verification

`gradlew.bat test` runs inventory regression tests covering admission rules, machine scan boundaries, direct terminals, sided permissions, locks, real bulk counts, simulation, large stacks, shared-inventory deduplication and hopper-to-machine transfer.

The survey's primary references are the corresponding GTNH fork sources and installed class definitions, notably:

- [GTNHLib item API, 0.11.51](https://github.com/GTNewHorizons/GTNHLib/tree/0.11.51/src/main/java/com/gtnewhorizon/gtnhlib/item)
- [GT digital chest, 5.09.54.183](https://github.com/GTNewHorizons/GT5-Unofficial/blob/5.09.54.183/src/main/java/gregtech/common/tileentities/storage/MTEDigitalChestBase.java)
- [GTNH JABBA](https://github.com/GTNewHorizons/Jabba)
- [GTNH Storage Drawers](https://github.com/GTNewHorizons/StorageDrawers)
- [GTNH Cooking for Blockheads](https://github.com/GTNewHorizons/CookingForBlockheads)
