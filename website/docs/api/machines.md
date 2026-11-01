---
id: machines
title: Machines
sidebar_position: 6
description: moonexpansion.thermal, enderio and ie recipe functions with parameters, defaults and examples.
---

# Machines

`moonexpansion` adds recipes to supported tech mods:

| Mod | Machine | Lua path |
|---|---|---|
| Thermal Expansion | Pulverizer | `moonexpansion.thermal.pulverizer` |
| Thermal Expansion | Induction Smelter | `moonexpansion.thermal.smelter` |
| Thermal Expansion | Magma Crucible | `moonexpansion.thermal.crucible` |
| Ender IO | SAG Mill | `moonexpansion.enderio.sag_mill` |
| Ender IO | Alloy Smelter | `moonexpansion.enderio.alloy_smelter` |
| Immersive Engineering | Crusher | `moonexpansion.ie.crusher` |
| Immersive Engineering | Blast Furnace | `moonexpansion.ie.blast_furnace` |
| Immersive Engineering | Arc Furnace | `moonexpansion.ie.arc_furnace` |

Use in `server/` or `startup/`. Calls queue work that is applied once at
`FMLLoadCompleteEvent`. Machine recipes require a restart to change.

## Availability

```lua
log.info("thermal=" .. tostring(moonexpansion.mods.thermal))
log.info("enderio=" .. tostring(moonexpansion.mods.enderio))
log.info("ie=" .. tostring(moonexpansion.mods.ie))
```

| Field | Meaning |
|---|---|
| `moonexpansion.mods.thermal` | Thermal Expansion loaded |
| `moonexpansion.mods.enderio` | Ender IO loaded |
| `moonexpansion.mods.ie` | Immersive Engineering loaded |

Calling a machine API for an uninstalled mod raises:

```text
MoonTweaker: <mod name> is not installed
```

## Stage and timing

- In `registry/`:

```text
moonexpansion: use lua_scripts/server/ or lua_scripts/startup/
```

- After apply:

```text
moonexpansion: machine recipes are applied at startup; restart the game
```

- Argument validation happens immediately.
- Semantic failures happen at apply time and are logged:

```text
MoonTweaker: failed to add <description>: <message>
```

- Successful apply logs:

```text
MoonTweaker: applied 9 machine recipes
```

- `/moontweaker reload` clears the queue but does not apply it again. Restart
  to change machine recipes.

## Input syntax

Machine arguments use compact strings:

| Form | Meaning |
|---|---|
| `"minecraft:iron_ingot"` | Item, count 1 |
| `"minecraft:wool:14"` | Item with metadata |
| `"minecraft:iron_dust * 3"` | Item, count 3 |
| `"ore:oreIron"` | Ore Dictionary input |
| `"fluid:water * 1000"` | Fluid output (crucible only) |

Stack tables are **not** accepted by machine APIs.

## Input expansion

| Machine type | Expansion |
|---|---|
| Single-input (Pulverizer, Crucible, SAG Mill, IE Crusher) | One recipe per matching ore item |
| Two-input (Induction Smelter, Alloy Smelter) | One recipe per combination |
| IE Blast/Arc Furnace | Ore name passed through as a dictionary reference |

For expanded ore inputs, each generated stack uses the ore entry's metadata and
count 1. A count cannot be attached to an `ore:` input.

## Thermal Expansion

### `moonexpansion.thermal.pulverizer.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.thermal.pulverizer.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  2500,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `energy` | `3000` | RF |
| `secondary_output` | none | Optional |
| `chance` | `100` | 0..100 |

If a secondary output is supplied, the five-argument Thermal Expansion overload
is used; otherwise the three-argument overload is used.

### `moonexpansion.thermal.smelter.add_recipe(output, primary_input, secondary_input, energy?, secondary_output?, chance?)`

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

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `primary_input` | required | Item or ore entry |
| `secondary_input` | required | Must be a string |
| `energy` | `4000` | RF |
| `secondary_output` | none | Optional |
| `chance` | `100` | 0..100 |

Missing second input:

```text
thermal.smelter needs a secondary input
```

Thermal Expansion rejects duplicate inputs. A duplicate is detected at apply
time and logged as a failure.

### `moonexpansion.thermal.crucible.add_recipe(fluid_output, input, energy?)`

```lua
moonexpansion.thermal.crucible.add_recipe(
  "fluid:water * 250",
  "minecraft:apple",
  1500
)
```

| Parameter | Default | Notes |
|---|---|---|
| `fluid_output` | required | `"fluid:<name> * <amount>"`, amount defaults to 1000 mB |
| `input` | required | Item or ore entry |
| `energy` | `2000` | RF |

## Ender IO

### `moonexpansion.enderio.sag_mill.add_recipe(output, input, energy?, secondary_output?, chance?)`

```lua
moonexpansion.enderio.sag_mill.add_recipe(
  "minecraft:sugar * 2",
  "minecraft:apple",
  240,
  "minecraft:gunpowder",
  25
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `energy` | `240` | RF |
| `secondary_output` | none | Optional |
| `chance` | `100` | 0..100 |

Uses `RecipeBonusType.CHANCE_ONLY` and `RecipeLevel.IGNORE`. The secondary
output chance is converted to a fraction (`chance / 100.0`).

### `moonexpansion.enderio.alloy_smelter.add_recipe(output, input, second_input?, energy?)`

```lua
moonexpansion.enderio.alloy_smelter.add_recipe(
  "minecraft:gold_ingot",
  "minecraft:apple",
  "minecraft:sand",
  2400
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `second_input` | none | Optional item or ore entry |
| `energy` | `2400` | RF |

At most two inputs are supported. Each `ore:` input expands to all matching
items, producing one recipe per combination. Recipe level is
`RecipeLevel.IGNORE`.

## Immersive Engineering

### `moonexpansion.ie.crusher.add_recipe(output, input, energy?)`

```lua
moonexpansion.ie.crusher.add_recipe(
  "minecraft:iron_dust * 3",
  "ore:oreIron"
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry (ore stays a dictionary reference) |
| `energy` | `3000` | Total RF cost |

Process time stays at the Immersive Engineering default; only the RF cost is
settable.

### `moonexpansion.ie.blast_furnace.add_recipe(output, input, time?, slag?)`

```lua
moonexpansion.ie.blast_furnace.add_recipe(
  "minecraft:glass",
  "minecraft:sand",
  800
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `time` | `1200` | Ticks |
| `slag` | none | Optional item stack |

### `moonexpansion.ie.arc_furnace.add_recipe(output, input, time?, energy_per_tick?, slag?)`

```lua
moonexpansion.ie.arc_furnace.add_recipe(
  "minecraft:flint * 2",
  "minecraft:gravel",
  100,
  80
)
```

| Parameter | Default | Notes |
|---|---|---|
| `output` | required | Item stack |
| `input` | required | Item or ore entry |
| `time` | `200` | Ticks |
| `energy_per_tick` | `100` | RF/t |
| `slag` | none | Optional item stack |

## Errors

| Condition | Error |
|---|---|
| Unknown item | `unknown item '<id>'` |
| Unknown ore | `<label>: unknown ore '<name>'` |
| Unknown fluid | `unknown fluid '<name>'` |
| Bad count | `bad count <n> in '<text>'` |
| Bad metadata | `bad metadata in '<id>'` |
| Bad fluid amount | `bad fluid amount <n>` |
| Energy < 1 | `value must be at least 1` |
| Chance out of range | `chance must be between 0 and 100` |
| Mod not installed | `MoonTweaker: <mod> is not installed` |
| Wrong stage | `moonexpansion: use lua_scripts/server/ or lua_scripts/startup/` |
| After apply | `moonexpansion: machine recipes are applied at startup; restart the game` |

Apply-time failures are logged, not thrown into Lua:

```text
MoonTweaker: failed to add thermal pulverizer minecraft:apple: <message>
```

## Limitations

- Add-only: existing machine recipes cannot be removed or edited.
- Thermal Expansion duplicate inputs are rejected.
- Ender IO and Immersive Engineering may accept duplicates.
- Machine recipes cannot be applied by `/moontweaker reload`; restart required.
- IE crusher process time is fixed.
- Reflection is version-tolerant but not version-proof.

## Complete example

```lua
-- lua_scripts/startup/machines.lua

if moonexpansion.mods.thermal then
  moonexpansion.thermal.pulverizer.add_recipe(
    "minecraft:sugar * 2", "minecraft:apple", 2500
  )
  moonexpansion.thermal.smelter.add_recipe(
    "minecraft:gold_ingot", "minecraft:apple", "minecraft:sand", 3500
  )
end

if moonexpansion.mods.enderio then
  moonexpansion.enderio.sag_mill.add_recipe(
    "minecraft:sugar * 2", "minecraft:apple", 240
  )
end

if moonexpansion.mods.ie then
  moonexpansion.ie.crusher.add_recipe(
    "minecraft:iron_dust * 3", "ore:oreIron"
  )
end
```

## What comes next

[Events](/docs/api/events) documents the gameplay event bridge.
