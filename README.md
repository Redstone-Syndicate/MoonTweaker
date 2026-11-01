<p align="center">
  <img src="src/main/resources/logo.png" width="160" alt="MoonTweaker logo">
</p>

# MoonTweaker

All-in-one server-side Lua scripting for **Minecraft 1.12.2 / Forge**, inspired by CraftTweaker and KubeJS.

One mod handles crafting recipes, loot tables, content registration, machine recipes, **custom advancements**, **gameplay events**, **server commands**, **JSON/NBT utilities**, and **modular scripting**. You do not need separate LunarLoot, MoonRegistry or MoonExpansion jars.

- **Language:** Lua 5.2 (LuaJ 3.0.1)
- **Loader:** Forge 1.12.2
- **Implementation:** Java 8
- **Author:** Kartendsy

## Features

- Edit recipes for vanilla and modded items by item ID or Ore Dictionary entry.
- Remove recipes by output item or registry ID, regardless of which mod registered them.
- Add shaped, shapeless and furnace recipes.
- Rewrite loot tables: add weighted entries, remove items and clear tables.
- Register items, material items, blocks, tools, armor and fluids from Lua.
- **Custom ore dictionary registration** (`registry.oreDict`) and **fuel values** (`registry.fuel`).
- **Batch registration** (`registry.batch`) for bulk item/block/tool/armor/fluid creation.
- Add machine recipes for Thermal Expansion, Ender IO and Immersive Engineering.
- **Extended gameplay events**: `player.logged_in`, `player.logged_out`, `player.death`, `player.dimension_change`, `player.craft`, `block.break`, `block.place`, `block.interact`, `item.pickup`, `item.right_click`, `item.use`, `entity.spawn`, `entity.hurt`, `entity.death`, `server.tick`, `server.startup`.
- Custom advancements defined in Lua (`advancement.add`), Triumph-style, synced to clients; vanilla advancements and whole trees can be hidden or fully replaced (`removeVanillaAdvancement`, `removeVanillaTree`, `removeAllVanilla`).
- **Advancement modification** (`advancement.modify`) with deep merge for patching display/criteria/rewards.
- **Advancement rewards**: experience, recipes, loot tables, functions.
- **Advancement AND/OR requirements** via named groups converted to vanilla array format.
- **Advancement callbacks**: `advancement.on_complete(id, fn)` and `advancement.on_complete_any(fn)` for Lua-side reactions.
- **Server API**: `server.runCommand(cmd)`, `server.broadcast(msg)`, `server.sendMessage(player, msg)`, `server.getPlayer(name)`.
- **Player API**: `player.giveItem(item, count)`, `player.giveItemStack({...})`, `player.consumeHeld()`, `player.getHeldItem()`, `player.countItem(id)`, `player.addEffect(effect, duration, amplifier)`, `player.clearEffects()`, `player.teleport(x,y,z,dim)`.
- **Item API**: `item.exists(id)`, `item.get(id)`, `item.getByOre(name)`, `item.listOre(name)`.
- **JSON API**: `json.parse()`, `json.stringify()`, `json.readFile(path)`, `json.writeFile(path, table)`.
- **NBT API**: `nbt.parse()`, `nbt.set()`, `nbt.get()`.
- Recursive script loading with per-file error reporting and **line numbers in errors**.
- **Module support** with `require()` from `lua_scripts/<stage>/modules/`.
- **Developer commands**: `/moontweaker check <file>` (syntax validation), `/moontweaker trace on|off` (verbose logging).
- Sandboxed Lua runtime: no filesystem, no Java reflection, no `os.execute`.
- No Mixin, no coremod, no patched Minecraft classes.

## Requirements

| Component | Version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 or newer |
| Java | 8 |

To build from source you additionally need **JDK 25** (Gradle 9.2 runs on it) and an internet connection for the first build.

## Installation

1. Install Minecraft Forge 1.12.2.
2. Put `MoonTweaker-<version>.jar` in `.minecraft/mods/`.
3. Start the game or server once. MoonTweaker creates `lua_scripts/` automatically.
4. Add your scripts and restart, or run `/moontweaker reload` for server scripts.

Only one MoonTweaker jar is needed. All loot, registry and machine APIs are built in.

## Building from source

```bash
./gradlew build
```

The reobfuscated mod jar is written to:

```
build/libs/MoonTweaker-0.1.0.jar
```

Build notes:

- Gradle 9.2 or newer is required by RetroFuturaGradle 2.0.2.
- Gradle must run on JDK 25. The Java 8 compilation toolchain is provisioned automatically by the Foojay resolver.
- LuaJ is embedded in the mod jar.
- Thermal Expansion, Ender IO and Immersive Engineering are **not** compile-time dependencies. Their recipe managers are called by reflection.

Run a development server or client:

```bash
./gradlew runServer --args nogui
./gradlew runClient
```

On the first server run, accept the Minecraft EULA in `run/eula.txt`.

## Project layout

```
build.gradle                          Gradle/RFG/Java build
settings.gradle                       Plugin repositories
assets/icon.svg                       Icon source (SVG)
src/main/resources/logo.png           Rendered mod icon (256x256)
src/main/resources/mcmod.info         Forge mod metadata
src/main/resources/assets/moontweaker/lang/en_us.lang

src/main/java/com/moontweaker/
  MoonTweaker.java                    @Mod entry point and event wiring
  api/MoonTweakerApi.java             Current script stage
  script/ScriptManager.java           Script discovery, sandbox, require, errors
  lua/RecipeApi.java                  item/ore/crafting recipe bindings
  loot/LootApi.java                   loot table bindings
  loot/LootTableHandler.java          LootTableLoadEvent hook
  registry/RegistryApi.java           item/block/tool/armor/fluid bindings
  registry/RegistryHandler.java       RegistryEvent hooks
  expansion/ExpansionApi.java         machine recipe bindings
  event/RecipeRegistryHook.java       Loads server scripts before the registry locks
  event/GameplayEvents.java           Gameplay event bridge
  advancement/AdvancementApi.java     Lua-defined custom advancements
  command/MoonTweakerCommand.java     /moontweaker command

lua_scripts/
  registry/                           Content registration, loaded at pre-init
  startup/                            Startup scripts, loaded at post-init
  server/                             Recipe, loot, machine and event scripts
```

Scripts are loaded recursively in alphabetical order.

## Script lifecycle

| Folder | Loaded at | Crafting recipes | Loot | Content registration | Machine recipes |
|---|---|---|---|---|---|
| `lua_scripts/registry/` | `FMLPreInitializationEvent` | No | No | Yes | No |
| `lua_scripts/server/` | `RegistryEvent.Register<IRecipe>` (lowest priority) | Yes | Queued | No | Queued |
| `lua_scripts/startup/` | `FMLPostInitializationEvent` | No | Queued | No | Queued |

Scripts in `registry/` run before Forge fires its registry events, so `registry.item`, `registry.block` and `registry.fluid` can register content. Registry scripts cannot be re-run without restarting the game.

Crafting recipes must be changed **while the recipe registry is still unlocked**. That happens during `RegistryEvent.Register<IRecipe>`, which is why `server/` scripts load there. Once the server has started, recipe changes are rejected by Forge.

Loot operations are queued while scripts run and applied in Forge's `LootTableLoadEvent`.

Machine recipes are queued while scripts run and applied once at `FMLLoadCompleteEvent`, after every mod has finished initialization. This makes the Thermal Expansion, Ender IO and Immersive Engineering recipe managers available even if another mod loads later than MoonTweaker.

### Reloading

`/moontweaker reload` reloads and re-runs `lua_scripts/server/`.

- Script logic is reloaded.
- Crafting recipe changes made after the registry has locked will fail and appear in `/moontweaker errors`. Restart the game or server to apply recipe changes.
- Loot operations are queued again; run `/reload` afterwards to reload loot tables, or restart.
- Machine recipes are applied only during startup. Calling a machine API after that raises an error; restart to add them.
- Lua global state is reset on reload.
- Loot operations queued earlier by `startup/` are discarded by a reload; restart to load them again.

## Lua API

### Logging

```lua
print("visible in the server log")
log.info("informational message")
log.warn("warning message")
log.error("error message")
```

### Items and ingredients

Item IDs follow the standard `modid:name` format, with optional metadata:

```lua
"minecraft:stone"
"minecraft:wool:14"
"modid:item:3"
```

Ore Dictionary entries use the `ore:` prefix:

```lua
"ore:ingotIron"
"ore:logWood"
```

`item(id)` validates an item and returns a table usable anywhere an item is accepted:

```lua
local stack = item("minecraft:diamond")
stack.count = 4
stack.meta = 0
```

Table form accepted by all item arguments:

```lua
{ id = "minecraft:diamond", count = 4, meta = 0 }
```

`ore(name)` is a convenience helper that returns `"ore:" .. name`:

```lua
recipes.shapeless("minecraft:stone", { ore("ingotIron") })
```

### `require`

Loads a Lua module relative to the folder of the stage being loaded. Dots map to directories and modules are cached per load.

```lua
-- lua_scripts/server/config/example.lua
return { value = 42 }
```

```lua
local cfg = require("config.example")
log.info("value = " .. cfg.value)
```

Path traversal outside the stage folder is rejected.

### JSON / NBT API

Available in `lua_scripts/server/` and `lua_scripts/startup/`.

```lua
-- JSON
local data = json.parse('{"name":"test","count":5}')
local str = json.stringify({item="diamond", tags={"rare","gem"}})
json.writeFile("config/data.json", data)
local loaded = json.readFile("config/data.json")

-- NBT
local nbtStr = nbt.parse('{display:{Name:"\\"§6Sword\\""},Enchantments:[{id:"minecraft:sharpness",lvl:5}]}')
local helper = nbt.set({}, "display.Name", '{"text":"Custom Name"}') -- for use with giveItemStack
```

### Recipes

#### Remove recipes

```lua
-- By output item: removes every recipe producing the item
recipes.remove("minecraft:stick")

-- By exact registry ID
recipes.remove({ id = "minecraft:stick" })

-- By output, returned as a number of removed recipes
local removed = recipes.remove({ output = "minecraft:stone" })
log.info("removed " .. removed .. " recipes")
```

Removal works on the global recipe registry, so it applies to recipes registered by mods as well as vanilla.

#### Shaped recipe

```lua
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)
```

- Pattern rows may have different lengths; missing cells are treated as empty.
- Keys map a single character to an item ID or Ore Dictionary entry.

#### Shapeless recipe

```lua
recipes.shapeless(
  "minecraft:planks",
  { "minecraft:log", "minecraft:log" }
)
```

#### Furnace recipe

```lua
recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)
```

The third argument is the experience value.

### Loot tables

The global `loot` table is available in `lua_scripts/server/` and `lua_scripts/startup/`. Table names use the vanilla resource location, for example:

- `minecraft:chests/simple_dungeon`
- `minecraft:chests/nether_bridge`
- `minecraft:chests/village_blacksmith`
- `minecraft:chests/abandoned_mineshaft`
- `minecraft:entities/zombie`

#### `loot.add(table, spec)`

Adds a weighted entry to a new pool, so the table rolls it once.

```lua
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 2,
  max = 4,
  weight = 10,
  quality = 0,
  meta = 0,
})
```

| Field | Type | Default | Meaning |
|---|---|---|---|
| `item` | string | required | Item ID, with optional metadata (`minecraft:wool:14`) |
| `min` | integer | `1` | Minimum stack size |
| `max` | integer | `min` | Maximum stack size |
| `weight` | integer | `1` | Selection weight inside the pool |
| `quality` | integer | `0` | Quality bonus scaled by luck |
| `meta` | integer | `0` | Item metadata; also read from the ID suffix |

A plain string is accepted as shorthand for a single item:

```lua
loot.add("minecraft:chests/stronghold_library", "minecraft:book")
```

#### `loot.remove(table, item)`

Removes every direct item entry that drops the given item.

```lua
loot.remove("minecraft:chests/abandoned_mineshaft", "minecraft:rail")
```

Metadata is ignored: `minecraft:wool:14` removes all wool entries.

#### `loot.clear(table)`

Removes every pool from the table. Combine with `loot.add` to fully replace the contents.

```lua
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 1,
  max = 3,
  weight = 1,
})
```

Loot operations are applied inside Forge's `LootTableLoadEvent`, before the table is frozen. The server log shows one line per changed table:

```
MoonTweaker: applied 2 loot changes to minecraft:chests/simple_dungeon
```

After editing loot scripts:

1. `/moontweaker reload` queues the new operations.
2. `/reload` reloads loot tables and applies them.
3. A server restart also applies them.

### Content registration

The global `registry` table is available **only** in `lua_scripts/registry/`. In `startup/` and `server/` its functions throw an error.

Every ID is prefixed with the current namespace. The default namespace is `moontweaker`. Change it before registering anything:

```lua
registry.namespace("my_mod")
```

IDs may also be fully qualified (`"other_mod:custom_item"`), in which case the prefix is not added.

#### `registry.item(name, opts)`

Registers a plain item.

```lua
registry.item("ruby", { max_stack = 64 })
```

| Option | Type | Default |
|---|---|---|
| `max_stack` | integer | `64` (clamped to 1..64) |

#### `registry.material(kind, name, opts)`

Registers a material item with a conventional suffix. Supported kinds: `ingot`, `dust` and `gem`.

```lua
registry.material("ingot", "copper")   -- my_mod:copper_ingot
registry.material("dust", "copper")    -- my_mod:copper_dust
registry.material("gem", "ruby")       -- my_mod:ruby_gem
```

`opts` accepts the same fields as `registry.item`.

#### `registry.block(name, opts)`

Registers a decorative block together with its `ItemBlock`, so it can be held and placed in survival.

```lua
registry.block("copper_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})
```

| Option | Type | Default |
|---|---|---|
| `hardness` | number | `2.0` |
| `resistance` | number | `10.0` |
| `material` | string | `"rock"` |

Supported materials: `rock`, `wood`, `iron`, `glass`, `sand`, `ground`, `cloth`.

#### `registry.tool(kind, material, opts)`

Creates a tool material and one tool item. Kinds: `pickaxe`, `axe`, `shovel`, `sword`, `hoe`.

```lua
registry.tool("pickaxe", "copper", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})
```

| Option | Type | Default |
|---|---|---|
| `level` | integer | `1` |
| `durability` | integer | `180` |
| `efficiency` | number | `4.0` |
| `damage` | number | `1.0` |
| `enchantability` | integer | `10` |

The first call for a material defines its stats; later tools with the same material reuse them. Items are named `<material>_<kind>`, for example `my_mod:copper_pickaxe`.

#### `registry.armor(material, opts)`

Creates an armor material and registers a full set: helmet, chestplate, leggings and boots.

```lua
registry.armor("copper", {
  durability = 15,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
  toughness = 0.0,
})
```

| Option | Type | Default |
|---|---|---|
| `durability` | integer | `15` (durability multiplier) |
| `reduction` | table of 4 integers | `{1, 3, 2, 1}` (helmet, chest, legs, boots) |
| `enchantability` | integer | `10` |
| `toughness` | number | `0.0` |

Items are named `<material>_helmet`, `<material>_chestplate`, `<material>_leggings` and `<material>_boots`.

#### `registry.fluid(name, opts)`

Registers a fluid, its world block and a bucket item.

```lua
registry.fluid("oil", {
  density = 900,
  viscosity = 1500,
  temperature = 300,
  luminosity = 0,
  color = 0x1A1A1A,
})
```

| Option | Type | Default |
|---|---|---|
| `density` | integer | `1000` |
| `viscosity` | integer | `1000` |
| `temperature` | integer | `300` |
| `luminosity` | integer | `0` |
| `color` | integer (RGB) | `0xFFFFFF` |
| `gaseous` | boolean | `false` |

The block uses the same ID as the fluid; the bucket is `<name>_bucket`.

#### Assets and translations

The registry API registers game objects only. Models, textures and language entries still belong to a resource pack, using the same namespace as the IDs:

```
assets/<namespace>/models/item/<id>.json
assets/<namespace>/textures/items/<id>.png
assets/<namespace>/blockstates/<id>.json
assets/<namespace>/models/block/<id>.json
assets/<namespace>/textures/blocks/<id>.png
assets/<namespace>/lang/en_us.lang
```

Conventions:

- Items, tools and armor use `item/generated` (tools look better with `item/handheld`).
- Blocks need a blockstate, a block model and an item model.
- Armor worn textures live at
  `assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_1.png`
  and `_layer_2.png`.
- Fluids use `textures/blocks/<id>_still.png` and `textures/blocks/<id>_flowing.png`.
- Language keys are `item.<id>.name`, `tile.<id>.name` and `fluid.<id>`.

Without assets the content works on the server but renders with missing textures on the client.

### Machine recipes

The `moonexpansion` table adds recipes to supported tech machines. It is available in `lua_scripts/server/` and `lua_scripts/startup/`, and rejected in `lua_scripts/registry/`.

| Mod | Machines |
|---|---|
| Thermal Expansion | Pulverizer, Induction Smelter, Magma Crucible |
| Ender IO | SAG Mill, Alloy Smelter |
| Immersive Engineering | Crusher, Blast Furnace, Arc Furnace |

Check which mods are installed:

```lua
log.info("thermal=" .. tostring(moonexpansion.mods.thermal))
log.info("enderio=" .. tostring(moonexpansion.mods.enderio))
log.info("ie=" .. tostring(moonexpansion.mods.ie))
```

Each field is a boolean. Calling a machine API for a mod that is not installed raises a Lua error.

Machine functions take the same string forms as the rest of MoonTweaker:

| Form | Meaning |
|---|---|
| `"minecraft:iron_ingot"` | item, metadata 0 |
| `"minecraft:wool:14"` | item with metadata |
| `"minecraft:iron_dust * 3"` | item with count 1..64 |
| `"ore:oreIron"` | Ore Dictionary entry (inputs only) |
| `"fluid:water * 1000"` | fluid with amount (crucible output) |

`ore:` inputs are expanded before the recipe is added:

- Single-input machines (Pulverizer, Crucible, SAG Mill) get one recipe per matching item.
- Two-input machines (Induction Smelter, Alloy Smelter) get one recipe per combination of matching items.
- Immersive Engineering keeps `ore:` inputs as an Ore Dictionary reference, so any item registered under that name works in the machine.

Tested with:

| Mod | Version |
|---|---|
| Thermal Expansion | `1.12.2-5.5.4.43` |
| Ender IO | `1.12.2-5.3.72` |
| Immersive Engineering | `0.12-92-559` |

#### Thermal Expansion

`moonexpansion.thermal.pulverizer.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.thermal.pulverizer.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  2500,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default |
|---|---|
| `energy` | `3000` RF |
| `secondary_output` | none |
| `chance` | `100` (0..100) |

`moonexpansion.thermal.smelter.add_recipe(output, primary_input, secondary_input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.thermal.smelter.add_recipe(
  "minecraft:gold_ingot",
  "minecraft:apple",
  "minecraft:sand",
  3500,
  "minecraft:gold_nugget",
  50
)
```

Both inputs are required; Thermal Expansion has no one-input smelter recipe.

| Parameter | Default |
|---|---|
| `energy` | `4000` RF |
| `secondary_output` | none |
| `chance` | `100` (0..100) |

`moonexpansion.thermal.crucible.add_recipe(fluid_output, input, energy?)`

```lua
moonexpansion.thermal.crucible.add_recipe(
  "fluid:water * 250",
  "minecraft:apple",
  1500
)
```

| Parameter | Default |
|---|---|
| `energy` | `2000` RF |
| fluid amount | `1000` mB |

Thermal Expansion rejects duplicate inputs. Such recipes are reported in the log as failed and are not added.

#### Ender IO

`moonexpansion.enderio.sag_mill.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.enderio.sag_mill.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  240,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default |
|---|---|
| `energy` | `240` RF |
| `secondary_output` | none |
| `chance` | `100` (0..100) |

Recipes use the `CHANCE_ONLY` bonus type and `IGNORE` recipe level, so they can be made in any SAG Mill.

`moonexpansion.enderio.alloy_smelter.add_recipe(output, input, second_input?, energy?)`

```lua
moonexpansion.enderio.alloy_smelter.add_recipe(
  "minecraft:gold_ingot",
  "minecraft:apple",
  "minecraft:sand",
  2400
)
```

| Parameter | Default |
|---|---|
| `energy` | `2400` RF |

At most two input arguments are supported. Each may be an `ore:` entry, in which case one recipe is added per combination.

#### Immersive Engineering

`moonexpansion.ie.crusher.add_recipe(output, input, energy?)`

```lua
moonexpansion.ie.crusher.add_recipe(
  "minecraft:iron_dust * 3",
  "ore:oreIron"
)
```

`energy` is the total RF cost, default `3000`. Process time stays at the Immersive Engineering default.

`moonexpansion.ie.blast_furnace.add_recipe(output, input, time?, slag?)`

```lua
moonexpansion.ie.blast_furnace.add_recipe(
  "minecraft:glass",
  "minecraft:sand",
  800
)
```

| Parameter | Default |
|---|---|
| `time` | `1200` ticks |
| `slag` | none |

`moonexpansion.ie.arc_furnace.add_recipe(output, input, time?, energy_per_tick?, slag?)`

```lua
moonexpansion.ie.arc_furnace.add_recipe(
  "minecraft:flint * 2",
  "minecraft:gravel",
  100,
  80
)
```

| Parameter | Default |
|---|---|
| `time` | `200` ticks |
| `energy_per_tick` | `100` RF/t |
| `slag` | none |

### Server API

Available in `lua_scripts/server/` and `lua_scripts/startup/`.

```lua
-- Run command as server console
server.runCommand("say Hello from MoonTweaker!")

-- Broadcast to all players
server.broadcast("§aServer announcement!")

-- Send private message
server.sendMessage(player, "§eWelcome to the server!")

-- Get player by name
local p = server.getPlayer("Notch")
if p then
  log.info("Found player: " .. p.name)
end
```

### Player API

Available in `lua_scripts/server/` and `lua_scripts/startup/`. `player` is the table from events or `server.getPlayer()`.

```lua
-- Inventory
local held = player.getHeldItem()           -- { id, count, meta } or nil
local count = player.countItem("minecraft:diamond")
player.giveItem("minecraft:diamond", 5)
player.giveItemStack({ item = "minecraft:diamond_sword", count = 1, nbt = nbtStr })
player.consumeHeld()                        -- consume 1 from held stack

-- Effects
player.addEffect("minecraft:speed", 600, 1) -- 30s Speed II
player.clearEffects()

-- Teleport
player.teleport(100, 64, 200, 0)            -- x, y, z, dimension
```

### Item API

Available in `lua_scripts/server/` and `lua_scripts/startup/`.

```lua
-- Check if item exists
if item.exists("minecraft:diamond") then ... end

-- Get item info
local info = item.get("minecraft:diamond")
-- { id = "minecraft:diamond", maxStack = 64, maxDamage = 0, creativeTab = "misc" }

-- Ore dictionary lookups
local items = item.getByOre("ingotIron")    -- first match
local allItems = item.listOre("ingotIron")  -- all matches: { "minecraft:iron_ingot", ... }
```

### Advancement API

Available in `lua_scripts/server/` and `lua_scripts/startup/`.

```lua
-- Add custom advancement
advancement.add("mymod:smelt_iron", {
  display = {
    title = "Iron Smelter",
    description = "Smelt your first iron ingot",
    icon = "minecraft:iron_ingot",          -- or "minecraft:iron_ingot:0:1"
    frame = "task",
    show_toast = true,
    announce_to_chat = true,
    hidden = false
  },
  criteria = {
    smelted = {
      trigger = "minecraft:inventory_changed",
      conditions = {
        items = { { item = "minecraft:iron_ingot" } }
      }
    }
  },
  requirements = {
    first = { "smelted" }
  },
  rewards = {
    experience = 50,
    recipes = { "minecraft:iron_sword" },
    loot = { "minecraft:entities/zombie" },
    function = "mymod:reward_function"
  }
})

-- Modify existing advancement (deep merge)
advancement.modify("minecraft:story/smelt_iron", {
  display = { title = "§6Iron Smelter" }
})

-- Remove vanilla advancement (tombstone)
advancement.removeVanillaAdvancement("minecraft:story/smelt_iron")

-- Remove entire vanilla tree
advancement.removeVanillaTree("minecraft:story/root")

-- Hide all vanilla advancements
advancement.removeAllVanilla()

-- Callbacks when advancement completes
advancement.on_complete("mymod:smelt_iron", function(player)
  server.sendMessage(player, "§aQuest complete!")
end)

advancement.on_complete_any(function(player, advId)
  log.info(player.name .. " completed " .. advId)
end)
```

**AND/OR Requirements** - Use named groups converted to vanilla array format:
```lua
requirements = {
  tier1 = { "has_iron", "has_gold" },   -- AND: need both
  tier2 = { "has_diamond" }              -- OR with tier1
}
```

### Registry API (Extended)

#### `registry.oreDict(name, itemId, meta?)`

Registers an item to the Forge Ore Dictionary. Available **only** in `lua_scripts/registry/`.

```lua
registry.oreDict("oreCopper", "minecraft:gold_ingot", 0)
registry.oreDict("oreTin", "minecraft:iron_ingot", 0)
```

| Parameter | Type | Default |
|---|---|---|
| `name` | string | required |
| `itemId` | string | required |
| `meta` | integer | `0` |

#### `registry.fuel(itemId, burnTime, meta?)`

Sets a custom furnace fuel burn time for an item. Available **only** in `lua_scripts/registry/`.

```lua
registry.fuel("minecraft:stick", 100, 0)        -- 100 ticks (5 seconds)
registry.fuel("minecraft:blaze_rod", 2400, 0)   -- 2400 ticks (2 minutes)
```

| Parameter | Type | Default |
|---|---|---|
| `itemId` | string | required |
| `burnTime` | integer | required (ticks) |
| `meta` | integer | `0` |

#### `registry.batch(definitions)`

Bulk registers items, blocks, tools, armor, and fluids. Available **only** in `lua_scripts/registry/`.

```lua
registry.batch({
  copper_ingot = { type = "item", options = { max_stack = 64 } },
  copper_block = { type = "block", options = { material = "iron", hardness = 5.0 } },
  copper_pickaxe = { type = "tool", material = "copper", options = { attackDamage = 2.0 } },
  copper_armor = { type = "armor", options = { material = "copper", helmet = true, chestplate = true } }
})
```

| Field | Type | Required |
|---|---|---|
| `type` | string ("item"|"block"|"tool"|"armor"|"fluid") | Yes |
| `material` | string (for tool/armor) | For tool/armor |
| `options` | table | No |

Requires `registry.material()` for tool/armor materials.

### Events

Register a callback with `events.on(name, function)`. Callbacks receive one table argument; cancelable events expose `e:cancel()`.

| Event | Fields | Cancelable |
|---|---|---|
| `player.logged_in` | `player` | No |
| `player.logged_out` | `player` | No |
| `player.death` | `player`, `killer` (may be `nil`) | No |
| `player.dimension_change` | `player`, `from_dim`, `to_dim` | No |
| `player.craft` | `player`, `recipe`, `output` | No |
| `block.break` | `player`, `block`, `pos` | Yes |
| `block.place` | `player`, `block`, `pos` | Yes |
| `block.interact` | `player`, `block`, `pos`, `hand`, `face` | Yes |
| `item.pickup` | `player`, `item`, `count` | Yes |
| `item.right_click` | `player`, `item`, `hand` | Yes |
| `item.use` | `player`, `item` | Yes |
| `entity.spawn` | `entity`, `type` | Yes |
| `entity.hurt` | `entity`, `source`, `amount` | Yes |
| `entity.death` | `entity`, `player` (may be `nil`) | Yes |
| `server.tick` | none | No |
| `server.startup` | none | No |

`player` is a table: `{ name, uuid, x, y, z, dimension }`.
`pos` is a table: `{ x, y, z }`.
`item` is a table: `{ id, count, meta }`.
`block` and `entity` are registry ID strings.

```lua
events.on("block.break", function(e)
  log.info(e.player.name .. " broke " .. e.block .. " at " .. e.pos.x .. "," .. e.pos.y .. "," .. e.pos.z)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)

events.on("server.tick", function(e)
  -- runs 20 times per second; keep it cheap
end)
```

Events are registered when `lua_scripts/server/` loads and are cleared before every reload, so a script must register its handlers again on reload.

### Full example

Registry stage:

```lua
-- lua_scripts/registry/content.lua
registry.namespace("my_mod")

registry.material("ingot", "copper")
registry.material("dust", "copper")
registry.item("mystery_token", { max_stack = 16 })

registry.block("copper_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})

registry.tool("pickaxe", "copper", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})

registry.fluid("oil", {
  density = 900,
  viscosity = 1500,
  color = 0x1A1A1A,
})

log.info("content registered")
```

Server stage:

```lua
-- lua_scripts/server/example.lua
log.info("MoonTweaker example loaded")

-- Crafting
recipes.remove("minecraft:stick")
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)
recipes.shapeless("my_mod:mystery_token", { "ore:ingotCopper" })
recipes.smelting("minecraft:stick", "minecraft:torch", 0.5)

-- Loot
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "my_mod:mystery_token",
  min = 1,
  max = 3,
  weight = 10,
})

-- Machines
moonexpansion.ie.crusher.add_recipe("minecraft:iron_dust * 3", "ore:oreIron")

-- Server API
server.runCommand("say MoonTweaker loaded!")
server.broadcast("§aWelcome to the server!")

-- Player API
events.on("player.logged_in", function(e)
  local p = server.getPlayer(e.player.name)
  if p then
    p.giveItem("minecraft:bread", 5)
    p.addEffect("minecraft:speed", 1200, 0)
    server.sendMessage(p, "§eWelcome! Here's some bread and Speed.")
  end
end)

-- Item API
local diamond = item.get("minecraft:diamond")
local allIron = item.listOre("ingotIron")

-- Advancement API
advancement.add("mymod:first_iron", {
  display = {
    title = "First Iron",
    description = "Obtain your first iron ingot",
    icon = "minecraft:iron_ingot"
  },
  criteria = {
    got_iron = {
      trigger = "minecraft:inventory_changed",
      conditions = { items = { { item = "minecraft:iron_ingot" } } }
    }
  },
  rewards = {
    experience = 20,
    recipes = { "minecraft:iron_pickaxe" }
  }
})

advancement.on_complete("mymod:first_iron", function(player)
  server.sendMessage(player, "§aQuest complete! Iron tools unlocked.")
end)

-- Events
events.on("block.break", function(e)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)

events.on("player.craft", function(e)
  log.info(e.player.name .. " crafted " .. e.output.id)
end)

-- JSON/NBT
local config = json.readFile("config/mymod.json")
if config then
  log.info("Loaded config: " .. json.stringify(config))
end
```

## Commands

| Command | Description |
|---|---|
| `/moontweaker reload` | Reload and re-run `lua_scripts/server/` |
| `/moontweaker errors` | Print the last script errors with file and line |
| `/moontweaker check <file.lua>` | Validate Lua syntax without executing (shows line numbers on error) |
| `/moontweaker trace on|off` | Enable/disable verbose script execution logging |
| `/moontweaker dump items|blocks|fluids|recipes|ores|registry` | Dump registry contents to log |
| `/moontweaker info held|player` | Inspect held item or player data |

All commands are currently available to all players. Server operators are expected to restrict access via their permissions plugin until permission checks are added.

## Sandbox

MoonTweaker deliberately exposes a small API surface. The Lua environment does **not** include:

- `io` (filesystem)
- `luajava` (Java reflection)
- `debug`
- `dofile`, `loadfile`, `load` (arbitrary bytecode/file loading)
- `package` and the default module searcher

`os` is replaced with a restricted table containing only `time`, `clock` and `difftime`.

`require` only resolves files under the folder of the stage being loaded and blocks paths that escape that directory.

Scripts run on the server thread. There is currently **no instruction budget or watchdog**, so an infinite loop in a script can hang the server. Do not run untrusted scripts.

## Known limitations

- Crafting recipe changes cannot be applied after server start; `/moontweaker reload` cannot update recipes. Restart required.
- Loot operations are server-side only; client mods that cache loot tables are not synchronized.
- `loot.remove` matches direct item entries by item, not by metadata or entry name. Nested table references and custom loot entries are left untouched.
- Loot conditions and functions are not exposed to Lua.
- Registered content can only be created once per game start; restart after editing.
- The registry API does not generate models, textures, language entries or ore dictionary entries. (Use `registry.oreDict()` for ore dict registration)
- Registered blocks have no custom block models, block states, tile entities or harvest levels.
- Creative tabs are fixed: items and buckets go to Misc, blocks to Building Blocks, tools to Tools, armor to Combat.
- Machine recipes can only be added while the game is starting. `/moontweaker reload` cannot apply them; restart required.
- Machine recipes are add-only. Existing machine recipes cannot be removed or changed.
- Thermal Expansion rejects duplicate machine inputs; the log reports these as failed.
- Ender IO and Immersive Engineering accept duplicate recipes, so adding the same recipe twice may add it twice.
- Machine recipe managers are called by reflection. Different major versions of a supported mod may rename classes or methods; a mismatch is logged as a failed recipe instead of crashing the game.
- Immersive Engineering crusher recipes use the machine's default process time; only the RF cost can be set.
- No client recipe-book or JEI synchronization for removed or added recipes.
- No per-command permission node.
- No instruction budget or watchdog for untrusted scripts.

## Roadmap

1. Client recipe-book and JEI synchronization.
2. Automatic asset and language generation for registered content.
3. Machine recipe removal and editing.
4. Instruction budget and watchdog for untrusted scripts.
5. EmmyLua stubs generated from the binding registry.

## Implemented (v0.3.0)

- [x] Custom ore dictionary registration (`registry.oreDict`)
- [x] Fuel value customization (`registry.fuel`)
- [x] Advancement rewards (experience, recipes, loot, functions)
- [x] Advancement AND/OR requirements
- [x] Advancement callbacks (`on_complete`, `on_complete_any`)
- [x] Advancement modification (`advancement.modify`)
- [x] Server API (`runCommand`, `broadcast`, `sendMessage`, `getPlayer`)
- [x] Player API (inventory, effects, teleport)
- [x] Item API (`exists`, `get`, `getByOre`, `listOre`)
- [x] JSON/NBT API (`json.parse`, `stringify`, `readFile`, `writeFile`, `nbt.parse`, `nbt.set`)
- [x] Extended events (14 total: login, logout, death, dimension, craft, break, place, interact, pickup, right_click, use, spawn, hurt, server startup)
- [x] Batch registration (`registry.batch`)
- [x] Module support with `require()`
- [x] Error line numbers in script errors
- [x] Developer commands (`check`, `trace`)

## Troubleshooting

**`Attempted to remove from the registry to late` / `Attempted to register to late`**
A script tried to change recipes after the registry locked. Move the code to `lua_scripts/server/` and restart the server. `/moontweaker reload` cannot bypass this Forge restriction.

**Scripts do not run**
Check that the files are `.lua`, located under `lua_scripts/registry/`, `lua_scripts/server/` or `lua_scripts/startup/`, and inspect `/moontweaker errors` and the server log.

**`registry API is only available in lua_scripts/registry/`**
Move the content registration code to `lua_scripts/registry/` and restart.

**Registered items have missing textures**
The registry API creates the game objects only. Add models, textures and language keys in a resource pack using the registered namespace.

**Loot changes are missing**
Run `/moontweaker reload` to re-queue the operations, then `/reload` to reload loot tables, or restart. Check the log for `MoonTweaker: applied ... loot changes`.

**Machine recipes are missing**
Machine recipes are applied during startup. Restart the server after editing scripts. Check that the target mod is installed (`moonexpansion.mods.*`) and inspect the log for failed recipe lines.

**Recipe changes are missing in JEI**
The server registry is authoritative for crafting. JEI data is client-side and is not synchronized by this mod; a client restart or a future sync feature is required.

## License

MoonTweaker is licensed under the **GNU General Public License v3.0 or later**.
See [LICENSE](LICENSE) for the full text and [NOTICE](NOTICE) for the copyright
notice.

Additional term under GNU GPL v3 section 7(b): every copy and every modified
version must preserve the attribution

> MoonTweaker by Kartendsy

in the mod metadata, README and any user-visible credits. Modified versions must
be marked as modified, as required by section 5(a) of the license.

## Credits

- Author and copyright holder: **Kartendsy**
- Lua runtime: [LuaJ](https://github.com/luaj/luaj)
- Build toolchain: [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)

Inspired by the scripting model of CraftTweaker and KubeJS.
