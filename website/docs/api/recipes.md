---
id: recipes
title: Recipes
sidebar_position: 3
description: recipes.remove, shaped, shapeless and smelting with argument rules, errors and examples.
---

# Recipes

The `recipes` table adds and removes crafting recipes. Use it in
`lua_scripts/server/`; that stage runs inside `RegistryEvent.Register<IRecipe>`
while the registry is still open.

```lua
recipes.remove("minecraft:stick")
recipes.shaped("minecraft:diamond_block", { "###", "###", "###" }, {
  ["#"] = "minecraft:diamond",
})
```

## `recipes.remove(target)`

Removes crafting recipes from `ForgeRegistries.RECIPES`.

### Accepted forms

```lua
-- By output item (a string is treated as an output stack)
recipes.remove("minecraft:stick")

-- By exact recipe registry ID
recipes.remove({ id = "minecraft:stick" })

-- By output item in table form
recipes.remove({ output = "minecraft:stone" })

-- Union of both
recipes.remove({ id = "minecraft:torch", output = "minecraft:stone" })
```

| Parameter | Type | Description |
|---|---|---|
| `target` | string or table | Output stack, or a table with `id` and/or `output` |

Returns the number of removed recipes (`integer`).

```lua
local removed = recipes.remove({ output = "minecraft:stone" })
log.info("removed " .. removed .. " recipes")
```

### Matching rules

| Target | Matches |
|---|---|
| `"minecraft:stick"` | Recipes whose primary output is a stick |
| `{ id = "minecraft:stick" }` | The recipe whose registry name is exactly `minecraft:stick` |
| `{ output = "minecraft:stone" }` | Recipes whose primary output is stone |
| `{ output = "minecraft:wool:32767" }` | Recipes producing any wool metadata |
| `{ id = ..., output = ... }` | Union: either condition is enough |

- A string target is parsed as an output item stack, **not** a registry ID.
- `{ id = ... }` compares `recipe.registryName.toString()` exactly.
- `{ output = ... }` matches item identity and metadata.
- `32767` (`OreDictionary.WILDCARD_VALUE`) matches all metadata variants.
- Only the primary output is inspected.

### Behaviour

- Scans the registry, collects matching names, then removes them.
- Logs the total:

```text
MoonTweaker: removed 3 recipes
```

- A recipe that cannot be removed is logged as a warning and skipped.
- Returns 0 when nothing matches.

### Errors

| Condition | Error |
|---|---|
| Table with neither `id` nor `output` | `remove() needs 'id' or 'output'` |
| Neither string nor table | `remove() expects item id or table` |
| Bad output item | `bad item id` / `unknown item` |

### Limitations

- Only `ForgeRegistries.RECIPES` is affected. **Furnace recipes cannot be
  removed** with this function.
- Recipes without a registry name cannot be targeted by `{ id = ... }`.
- Nested or custom recipe classes are matched only by their primary output.
- Removal does not persist across a restart unless the script runs again.

## `recipes.shaped(output, pattern, keys)`

```lua
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)
```

| Argument | Type | Description |
|---|---|---|
| `output` | item string/table | Recipe result, including optional count/metadata |
| `pattern` | array of strings | Rows of the crafting grid |
| `keys` | table | Single-character key → ingredient |

Returns `true`.

### Behaviour

- Grid width is the length of the longest row.
- Rows may have different lengths; missing cells are empty.
- A space (`" "`) is an empty cell.
- Every non-space character must have a key.
- Key values may be item IDs, stack tables (only `id` is used) or `ore:`
  entries.
- The recipe is registered as `moontweaker:script_<n>`.
- The group string is empty.

### Examples

```lua
-- Pickaxe with a hole
recipes.shaped(
  "minecraft:iron_pickaxe",
  { "III", " S ", " S " },
  {
    ["I"] = "minecraft:iron_ingot",
    ["S"] = "minecraft:stick",
  }
)
```

```lua
-- Output count
recipes.shaped(
  { id = "minecraft:torch", count = 4 },
  { "C", "S" },
  { ["C"] = "minecraft:coal", ["S"] = "minecraft:stick" }
)
```

```lua
-- Ore Dictionary ingredient
recipes.shaped(
  "minecraft:piston",
  { "PPP", "ISI", "ISI" },
  {
    ["P"] = "minecraft:planks",
    ["I"] = "ore:ingotIron",
    ["S"] = "minecraft:cobblestone",
  }
)
```

### Errors

| Condition | Error |
|---|---|
| Empty pattern | `shaped(): empty pattern` |
| Missing key | `shaped(): missing key 'X'` |
| Bad output/ingredient | `bad item id` / `unknown item` |
| `pattern` not a table | LuaJ `checktable` error |
| `keys` not a table | LuaJ `checktable` error |

## `recipes.shapeless(output, ingredients)`

```lua
recipes.shapeless(
  "minecraft:planks",
  { "minecraft:log", "minecraft:log" }
)
```

| Argument | Type | Description |
|---|---|---|
| `output` | item string/table | Recipe result |
| `ingredients` | array | Item IDs, stack tables or `ore:` entries |

Returns `true`.

### Behaviour

- The list must not be empty.
- Uses the array part only (`list.length()`); string keys are ignored.
- Ore entries expand to an ore ingredient.
- The recipe is registered as `moontweaker:script_<n>`.

### Examples

```lua
recipes.shapeless(
  "my_mod:mystery_token",
  { "ore:ingotCopper", "minecraft:redstone" }
)
```

```lua
recipes.shapeless(
  { id = "minecraft:planks", count = 4, meta = 2 },
  { "minecraft:log", "minecraft:log", "minecraft:log", "minecraft:log" }
)
```

### Errors

| Condition | Error |
|---|---|
| Empty list | `shapeless(): empty ingredient list` |
| Bad output/ingredient | `bad item id` / `unknown item` |
| `ingredients` not a table | LuaJ `checktable` error |

## `recipes.smelting(input, output, experience?)`

> **Argument order:** input first, output second. This differs from many recipe
> APIs.

```lua
recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)
```

| Argument | Type | Default | Description |
|---|---|---|---|
| `input` | item string/table | required | Furnace input |
| `output` | item string/table | required | Furnace result |
| `experience` | number | `0.0` | Experience awarded |

Returns `true`.

### Behaviour

- Calls `FurnaceRecipes.instance().addSmeltingRecipe(input, output, xp)`
  immediately.
- Not registered as an `IRecipe`.
- Cannot be removed with `recipes.remove`.
- Does not appear as a crafting-table recipe in JEI.
- Uses the first matching input stack.

### Examples

```lua
recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)
recipes.smelting("minecraft:stick", "minecraft:torch", 0.5)
```

## Recipe IDs

Shaped and shapeless recipes use:

```text
moontweaker:script_0
moontweaker:script_1
moontweaker:script_2
```

The counter is reset by `/moontweaker reload`, but the registry is frozen after
startup, so a reload cannot replace recipes.

## Stage and reload rules

| Situation | Result |
|---|---|
| `server/` at normal startup | Works |
| `server/` after `/moontweaker reload` | Add/remove fails (registry frozen) |
| `startup/` | Add/remove fails (registry frozen) |
| `registry/` | Installed but runs before the recipe event; not the intended stage |

`recipes` is installed in every stage, but only `server/` runs at the legal
moment.

## Complete example

```lua
-- lua_scripts/server/recipes.lua

-- Remove all stick recipes
recipes.remove("minecraft:stick")

-- Replace the diamond block recipe
recipes.remove({ output = "minecraft:diamond_block" })
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)

-- Cheap torches
recipes.shaped(
  { id = "minecraft:torch", count = 8 },
  { "C", "S" },
  { ["C"] = "ore:coal", ["S"] = "minecraft:stick" }
)

-- Smelt gravel into flint
recipes.smelting("minecraft:gravel", "minecraft:flint", 0.2)
```

## What comes next

[Loot](/docs/api/loot) documents `loot.add`, `loot.remove` and `loot.clear`.
