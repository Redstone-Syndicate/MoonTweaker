---
id: registry
title: Registry
sidebar_position: 5
description: registry.namespace, item, material, block, tool, armor and fluid with every option and error.
---

# Registry

The `registry` table creates items, blocks, tools, armor and fluids. It is
available **only** in `lua_scripts/registry/`.

In `server/` and `startup/`, every function raises:

```text
registry API is only available in lua_scripts/registry/
```

Registry scripts run during `FMLPreInitializationEvent` and queue objects.
MoonTweaker registers them during the block and item registry events. A restart
is required to apply changes.

## `registry.namespace(name)`

Sets the namespace used by IDs created after the call.

```lua
registry.namespace("my_mod")
```

| Parameter | Type | Description |
|---|---|---|
| `name` | string | Namespace matching `^[a-z0-9_.-]+$` |

Default: `moontweaker`.

Rules:

- Uppercase letters are rejected.
- Applies only to subsequent calls.
- Fully qualified IDs bypass the prefix:

```lua
registry.item("other_mod:custom_item")
```

- Invalid namespace: `bad namespace '<value>'`.

## `registry.item(name, opts?)`

Registers a plain item in the Miscellaneous creative tab.

```lua
registry.item("ruby", { max_stack = 64 })
```

| Parameter | Type | Description |
|---|---|---|
| `name` | string | Item ID or path |
| `opts` | table, optional | Options |

| Option | Type | Default | Notes |
|---|---|---|---|
| `max_stack` | integer | `64` | Clamped to 1..64 |

- Translation key: `<path>`.
- Full ID: `<namespace>:<name>`.
- Returns `nil`.

## `registry.material(kind, name, opts?)`

Registers a material item with a conventional suffix.

```lua
registry.material("ingot", "copper")
registry.material("dust", "copper")
registry.material("gem", "ruby")
```

| `kind` | Suffix | Example |
|---|---|---|
| `ingot` | `_ingot` | `my_mod:copper_ingot` |
| `dust` | `_dust` | `my_mod:copper_dust` |
| `gem` | `_gem` | `my_mod:ruby_gem` |

`opts` accepts the same fields as `registry.item`.

Unknown kind:

```text
unknown material kind '<kind>' (ingot, dust, gem)
```

## `registry.block(name, opts?)`

Registers a block and its `ItemBlock`.

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

Supported materials:

| Value | Minecraft material |
|---|---|
| `rock` | `Material.ROCK` |
| `wood` | `Material.WOOD` |
| `iron` | `Material.IRON` |
| `glass` | `Material.GLASS` |
| `sand` | `Material.SAND` |
| `ground` | `Material.GROUND` |
| `cloth` | `Material.CLOTH` |

- Creative tab: Building Blocks.
- Translation key: `<path>`.
- The block and `ItemBlock` share the same registry ID.
- No blockstate/model generation, tile entity, harvest level or custom drops.

Unknown material: `unknown block material '<name>'`.

## `registry.tool(kind, material, opts?)`

Creates a tool material and one tool item.

```lua
registry.tool("pickaxe", "copper", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})
```

| `kind` | Item class | ID |
|---|---|---|
| `pickaxe` | `ItemPickaxe` | `<material>_pickaxe` |
| `axe` | `ItemAxe` | `<material>_axe` |
| `shovel` | `ItemSpade` | `<material>_shovel` |
| `sword` | `ItemSword` | `<material>_sword` |
| `hoe` | `ItemHoe` | `<material>_hoe` |

| Option | Type | Default | Meaning |
|---|---|---|---|
| `level` | integer | `1` | Harvest level |
| `durability` | integer | `180` | Maximum durability |
| `efficiency` | number | `4.0` | Mining speed |
| `damage` | number | `1.0` | Attack damage modifier |
| `enchantability` | integer | `10` | Enchantability |

Behaviour:

- Tool materials are keyed by `<namespace>:<material>`.
- The **first** call for a material defines its stats. Later tools with the same
  material reuse the material and ignore their options.
- The axe uses attack damage `6.0` and speed `-3.0`.
- Creative tab: Tools.
- Translation key: `<material>_<kind>`.

Unknown kind:

```text
unknown tool '<kind>' (pickaxe, axe, shovel, sword, hoe)
```

To get a full set, call every kind with the same material and options.

## `registry.armor(material, opts?)`

Creates an armor material and four pieces.

```lua
registry.armor("copper", {
  durability = 15,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
  toughness = 0.0,
})
```

| Piece | ID | Slot |
|---|---|---|
| Helmet | `<material>_helmet` | Head |
| Chestplate | `<material>_chestplate` | Chest |
| Leggings | `<material>_leggings` | Legs |
| Boots | `<material>_boots` | Feet |

| Option | Type | Default | Notes |
|---|---|---|---|
| `durability` | integer | `15` | Durability multiplier |
| `reduction` | array of 4 integers | `{1, 3, 2, 1}` | Helmet, chest, legs, boots |
| `enchantability` | integer | `10` | Enchantability |
| `toughness` | number | `0.0` | Armor toughness |

Behaviour:

- Armor materials are keyed by `<namespace>:<material>`; first call wins.
- Texture base: `<namespace>_<material>`.
- Render index `0`.
- Creative tab: Combat.
- If `reduction` is not a table, the default is used. If it is a table, its
  four entries are read with `0` fallback.

Textures:

```text
assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_1.png
assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_2.png
```

## `registry.fluid(name, opts?)`

Registers a fluid, its world block and a bucket.

```lua
registry.fluid("oil", {
  density = 900,
  viscosity = 1500,
  temperature = 300,
  luminosity = 0,
  color = 0x1A1A1A,
  gaseous = false,
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

Behaviour:

- Fluid registers **immediately** through `FluidRegistry.registerFluid`.
- Internal fluid name: `<namespace>_<path>`.
- Still texture: `<namespace>:blocks/<path>_still`.
- Flowing texture: `<namespace>:blocks/<path>_flowing`.
- World block: `BlockFluidClassic` with `Material.WATER`, same ID as the fluid.
- Bucket: `<path>_bucket`, Miscellaneous tab.
- `FluidRegistry.addBucketForFluid(fluid)` is called.

Errors:

```text
fluid '<id>' is already registered
```

Textures:

```text
assets/<namespace>/textures/blocks/<path>_still.png
assets/<namespace>/textures/blocks/<path>_flowing.png
```

## Complete example

```lua
-- lua_scripts/registry/mekalite.lua
registry.namespace("mekalite")

registry.item("mekalite_core", { max_stack = 16 })

registry.material("ingot", "mekalite")
registry.material("dust", "mekalite")
registry.material("gem", "mekalite")

registry.block("mekalite_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})

registry.tool("pickaxe", "mekalite", {
  level = 3,
  durability = 1200,
  efficiency = 8.0,
  damage = 2.0,
  enchantability = 14,
})

registry.armor("mekalite", {
  durability = 25,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
})

registry.fluid("mekalite_slurry", {
  density = 1200,
  viscosity = 1500,
  color = 0x66CCFF,
})
```

## Assets

The registry API creates objects only. A resource pack must provide models,
blockstates, textures and language entries:

```text
assets/<namespace>/models/item/<id>.json
assets/<namespace>/textures/items/<id>.png
assets/<namespace>/blockstates/<id>.json
assets/<namespace>/models/block/<id>.json
assets/<namespace>/textures/blocks/<id>.png
assets/<namespace>/lang/en_us.lang
```

Language keys:

| Type | Key |
|---|---|
| Item | `item.<id>.name` |
| Block | `tile.<id>.name` |
| Fluid | `fluid.<id>` |

See [Assets](/docs/guides/assets).

## Errors

| Condition | Error |
|---|---|
| Bad namespace | `bad namespace '<value>'` |
| Duplicate ID | `duplicate id '<id>'` |
| Unknown material kind | `unknown material kind '<kind>' (...)` |
| Unknown block material | `unknown block material '<name>'` |
| Unknown tool kind | `unknown tool '<kind>' (...)` |
| Duplicate fluid | `fluid '<id>' is already registered` |
| EnumHelper failure | `cannot create tool/armor material '<name>'` |

## Stage rules

| Stage | `registry.*` |
|---|---|
| `registry/` | Works |
| `server/` | Restricted table, every call throws |
| `startup/` | Restricted table, every call throws |
| Reload | Registry stage does not re-run |

## What comes next

[Machines](/docs/api/machines) documents `moonexpansion.*`.
