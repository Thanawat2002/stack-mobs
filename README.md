# Stack Mobs

A production-ready Fabric mod that merges nearby identical mobs into stacked
entities to improve server performance and enhance gameplay.

**Requires Minecraft 26.1.x or higher · Fabric Loader · Java 21**

---

## Features

| Feature | Description |
|---|---|
| Auto-stacking | Nearby identical mobs merge into one entity |
| Proportional drops | Each "kill" drops loot and XP for one entity |
| Dynamic name tags | Colour-coded floating labels (e.g. *Zombie x42*) |
| Health bar | Optional percentage display in name tag |
| Particle effects | Visual feedback on merge and split |
| Admin commands | `/stackmob reload/toggle/info/unstack/killall/debug` |
| Config hot-reload | Change settings without restarting the server |
| Boss protection | Wither, Ender Dragon, Elder Guardian, Warden never stack |
| Developer API | `StackMobAPI.INSTANCE` for third-party mod integration |

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | **≥ 26.1.x** (enforced by Gradle) |
| Fabric Loader | ≥ 0.16.9 |
| Fabric API | Compatible with your Minecraft build |
| Java | 21 |

---

## Installation

1. Download the latest `stack-mobs-x.y.z.jar` from the releases page.
2. Place it in your `mods/` folder alongside **Fabric API**.
3. Start the server. A default config is generated at
   `config/stackmobs.json` on first run.

---

## Building from source

```bash
# Unix / macOS
chmod +x gradlew
./gradlew build

# Windows
gradlew.bat build
```

The compiled jar will be at `build/libs/stack-mobs-<version>.jar`.

### Run in development

```bash
./gradlew runClient   # integrated client + server
./gradlew runServer   # dedicated server
```

> **Note** – update the three version properties in `gradle.properties` to
> match the exact Fabric/Yarn build numbers for your target Minecraft release:
>
> ```properties
> minecraft_version=26.1.1
> yarn_mappings=26.1.1+build.1
> loader_version=0.16.9
> fabric_version=0.100.0+26.1.1
> ```
>
> Check current values at <https://fabricmc.net/develop/>.

---

## Configuration

File: `config/stackmobs.json`

```json
{
  "enabled": true,
  "stackRadius": 8,
  "maxStackSize": 100,
  "mergeDelay": 40,
  "showNameTag": true,
  "showHealthBar": false,
  "blacklist": [
    "minecraft:wither",
    "minecraft:ender_dragon"
  ],
  "whitelist": [],
  "enabledWorlds": [],
  "disabledBiomes": []
}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `true` | Master on/off switch |
| `stackRadius` | int | `8` | Merge radius in blocks |
| `maxStackSize` | int | `100` | Max entities per stack |
| `mergeDelay` | int | `40` | Ticks between scans (20 ticks = 1 s) |
| `showNameTag` | bool | `true` | Show *Mob xN* label |
| `showHealthBar` | bool | `false` | Append health % to label |
| `blacklist` | list | *(bosses)* | Entity IDs never to stack |
| `whitelist` | list | *(empty)* | When non-empty, only these IDs stack |
| `enabledWorlds` | list | *(empty = all)* | Dimensions where stacking is active |
| `disabledBiomes` | list | *(empty)* | Biomes where stacking is suppressed |

---

## Commands

All commands require operator level 2.

| Command | Description |
|---|---|
| `/stackmob reload` | Reload config from disk |
| `/stackmob toggle` | Enable / disable stacking |
| `/stackmob info` | Show current config and stats |
| `/stackmob unstack [radius]` | Split stacked mobs near you (default 32 blocks) |
| `/stackmob killall` | Kill every stacked mob across all dimensions |
| `/stackmob debug` | Dump the active stack registry to chat |

---

## Developer API

Add Stack Mobs as an **optional** dependency in your mod and use the public
interface:

```java
import com.stackmobs.api.StackMobAPI;

// Read stack size
int size = StackMobAPI.INSTANCE.getStackSize(entity);

// Force stack size (updates name tag automatically)
StackMobAPI.INSTANCE.setStackSize(entity, 25);

// Merge two compatible mobs
StackMobAPI.INSTANCE.merge(source, target);

// Split a stacked mob
StackMobAPI.INSTANCE.split(entity, 5);
```

Declare the dependency in `fabric.mod.json`:

```json
"suggests": {
  "stackmobs": "*"
}
```

---

## Architecture

```
StackMobsMod          ModInitializer entry-point; wires all components.
├── ConfigManager     Reads/writes config/stackmobs.json via Gson.
├── StackManager      Core engine: merge scans, unstack, drop dispatch.
│   └── MobStack      Immutable snapshot of one active stack (debug only).
├── Events
│   ├── EntitySpawnHandler   ENTITY_LOAD → restores name tags on world load.
│   ├── EntityDeathHandler   AFTER_DEATH → cleans up registry entries.
│   └── ServerTickHandler    END_SERVER_TICK → drives periodic merge scans.
├── Commands
│   └── StackCommand  All /stackmob sub-commands via Brigadier.
├── Mixins
│   ├── MobEntityMixin          Injects stackSize field + NBT persistence.
│   ├── LivingEntityMixin       Intercepts fatal damage for stacked mobs.
│   └── LivingEntityDropAccessor Makes protected drop() callable externally.
├── Rendering
│   └── NameTagRenderer  Builds formatted Text for custom name tags.
└── API
    └── StackMobAPI      Public interface for third-party mod integration.
```

---

## Gameplay mechanics

### Stacking
Every `mergeDelay` ticks the server scans loaded mobs. Identical mob types
within `stackRadius` blocks are merged: the secondary entity is removed, and
the primary entity's stack counter increases. Name tag and particles are
updated immediately.

### Death
When a stacked entity takes fatal damage:
1. Stack size decreases by 1.
2. Loot and XP drop as if one entity died (vanilla loot table, full XP).
3. The entity restores to full health (representing the "next" mob).
4. The name tag updates to the new count.

When the last stack member dies (stack size = 1), vanilla death occurs
normally.

**Total loot = stackSize × single-entity drops** across all deaths.

### Unstacking
Use `/stackmob unstack` or `StackMobAPI.INSTANCE.split()` to spawn all
members as individual entities around the primary's position.

---

## Performance

- Merge scans run **once every `mergeDelay` ticks** (default 2 s), not
  every tick.
- Entity lookup uses Minecraft's chunk-spatial index (`Box`-based query),
  not a brute-force global scan.
- Stack state is stored in NBT on the entity itself; no external database or
  persistent maps are required.
- All logic is server-side; clients receive only normal entity-update packets.

---

## License

MIT – see `LICENSE` for details.
