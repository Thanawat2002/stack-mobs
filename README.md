# Stack Mobs

A production-ready Fabric mod that merges nearby identical mobs into stacked
entities to improve server performance and enhance gameplay.

**Requires Minecraft 26.3.x · Fabric Loader 0.20.1+ · Java 25**

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
| Player-mob protection | Named, leashed, tamed, ridden and trading/inventory mobs (e.g. villagers) never stack |
| Variant-aware | Only identical variants merge (sheep colour, cow/pig/chicken variant); split-off mobs keep them |
| Developer API | `StackMobAPI.INSTANCE` for third-party mod integration |

---

## Requirements

| Dependency | Version |
|---|---|
| Minecraft | **26.3.x** (enforced by `fabric.mod.json`) |
| Fabric Loader | ≥ 0.20.1 |
| Fabric API | Compatible with your Minecraft build |
| Java | 25 |

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

> **Note** – update the version properties in `gradle.properties` to
> match the exact Fabric build numbers for your target Minecraft release:
>
> ```properties
> minecraft_version=26.3
> loader_version=0.20.1
> loom_version=1.19-SNAPSHOT
> fabric_api_version=0.166.0+26.3
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
  "maxStackSize": 1000,
  "minStackSize": 10,
  "mergeDelay": 100,
  "showNameTag": true,
  "showHealthBar": false,
  "nameTagViewDistance": 8,
  "useBlacklist": true,
  "blacklist": [
    "minecraft:wither",
    "minecraft:ender_dragon"
  ],
  "useWhitelist": true,
  "whitelist": [
    "minecraft:chicken",
    "minecraft:cow",
    "minecraft:pig",
    "minecraft:sheep"
  ],
  "enabledWorlds": [],
  "disabledBiomes": []
}
```

| Key | Type | Default | Description |
|---|---|---|---|
| `enabled` | bool | `true` | Merge new stacks. When `false`, existing stacks keep working but nothing new merges |
| `stackRadius` | int | `8` | Merge radius in blocks |
| `maxStackSize` | int | `1000` | Max entities per stack |
| `minStackSize` | int | `10` | Identical mobs needed within `stackRadius` before a new stack forms (min 2) |
| `mergeDelay` | int | `100` | Ticks between merge scans (20 ticks = 1 s) |
| `showNameTag` | bool | `true` | Show *Mob xN* label |
| `showHealthBar` | bool | `false` | Append health % to label |
| `nameTagViewDistance` | int | `8` | Show the label while any player is within this many blocks (`0` = always). Visibility is per mob, so everyone sees it once one player is close |
| `useBlacklist` | bool | `true` | Apply `blacklist` (bosses are always excluded) |
| `blacklist` | list | *(wither, ender dragon)* | Entity IDs never to stack |
| `useWhitelist` | bool | `true` | When `true`, only `whitelist` IDs stack |
| `whitelist` | list | *(chicken, cow, pig, sheep)* | Entity IDs allowed to stack while `useWhitelist` is on |
| `enabledWorlds` | list | *(empty = all)* | Dimensions where new stacks may form, e.g. `minecraft:overworld` |
| `disabledBiomes` | list | *(empty)* | Biomes where new stacks don't form, e.g. `minecraft:plains` |

> **Default is farm animals only.** To stack hostile mobs too, set
> `"useWhitelist": false` (or add their IDs to `whitelist`).

Out-of-range values are corrected on load with a warning in the log. If the
file has invalid JSON, the mod logs the error, keeps its current settings and
leaves the file untouched — fix it and run `/stackmob reload`.

---

## Commands

All commands require operator level 2.

| Command | Description |
|---|---|
| `/stackmob reload` | Reload config from disk |
| `/stackmob toggle` | Enable / disable merging of new stacks (existing stacks keep working) |
| `/stackmob info` | Show current config and stats |
| `/stackmob unstack [radius]` | Split stacked mobs near you (default 32 blocks) |
| `/stackmob killall` | Kill every stacked mob across all dimensions (one mob's drops per stack) |
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

// Merge source into target. No-op unless they're the same type and variant
// and the combined size fits within maxStackSize
StackMobAPI.INSTANCE.merge(source, target);

// Split 5 members off as individual mobs; the rest stays stacked
StackMobAPI.INSTANCE.split(entity, 5);
```

Mutating calls only run on the server side.

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
│   ├── LivingEntityMixin       Spreads fatal damage across stack members.
│   ├── LivingEntityDropAccessor Makes protected drop() callable externally.
│   ├── AgeableMobMixin         Grows babies in a stack up one at a time.
│   ├── AnimalFeedingMixin      Stack-aware breeding and baby feeding.
│   ├── SheepShearingMixin      Batch shearing; sheared sheep split off.
│   └── ChickenEggMixin         Lays eggs for every chicken in a stack.
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
Damage is counted after armour, enchantments, Resistance and absorption,
exactly as vanilla applies it. When that damage is fatal to a stacked entity:
1. One member dies, plus one more for every full max-health of overflow damage
   (a big hit can kill several members at once).
2. Each dead member drops loot and XP as if one entity died (vanilla loot
   table, full XP).
3. The next member takes over with the leftover damage applied to its health.
4. The name tag updates to the new count.

When the last stack member dies (stack size = 1), vanilla death occurs
normally.

**Total loot = stackSize × single-entity drops** across all deaths.

Removal damage — `/kill`, `/stackmob killall` and falling into the void —
removes the whole stack at once with a single mob's drops, so clearing a large
stack doesn't flood the world with items.

### Shearing
Shearing a sheep stack clips as many sheep as the shears have durability left.
Each sheared sheep becomes its own entity and never stacks, so it regrows its
wool by eating grass just like in vanilla. Once its wool is back, it rejoins a
nearby stack on the next merge scan. The un-sheared remainder stays a stack.

### Feeding
- **Adults:** one stack click breeds as many pairs as the held food allows
  (2 food per baby). The fed group keeps a 5-minute breeding cooldown; the
  unfed rest splits off as its own stack.
- **Babies:** each food item feeds one baby in the stack, speeding up that
  baby's growth by vanilla's amount (10% of its remaining time).

### Chicken eggs
A chicken stack lays eggs for every chicken in it: each time its egg timer
fires, the egg loot table is rolled once per stacked chicken, so egg output
matches the same number of vanilla chickens.

### Unstacking
Use `/stackmob unstack` to split every stack near you into individual mobs,
or `StackMobAPI.INSTANCE.split(entity, n)` to split off `n` members. Split-off
mobs keep their colour/variant, sheared state, breeding cooldown and, for
babies, their own remaining growth time.

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
