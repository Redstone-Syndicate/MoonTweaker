---
id: data-types
title: Data Types
sidebar_position: 1
description: Item IDs, metadata, ore dictionary entries, stack tables, machine shorthand and argument coercion rules.
---

# Data types

MoonTweaker APIs accept a small set of Lua shapes. The exact rules differ
slightly between APIs, so this page is the canonical reference.

## Item IDs

```text
namespace:path
namespace:path:metadata
```

Examples:

```lua
"minecraft:stone"
"minecraft:wool:14"
"thermalfoundation:material:128"
"my_mod:ruby"
```

Rules:

- At least two colon-separated parts are required.
- The namespace and path are resolved through `ForgeRegistries.ITEMS`.
- An unknown item raises `unknown item '<id>'`.
- The optional third part is parsed as an integer metadata value.
- A non-numeric metadata suffix behaves differently per API:

| API | Non-numeric metadata |
|---|---|
| `item(id)` | Silently treated as `0` |
| `recipes.*` | Silently treated as the fallback (`0` or table `meta`) |
| `loot.add` / `loot.remove` | `bad metadata in '<id>'` |
| `moonexpansion.*` | `bad metadata in '<id>'` |

The difference comes from `Integer.parseInt` fallbacks in `RecipeApi` versus
explicit errors in `LootApi` and `ExpansionApi`.

### Metadata wildcard

`32767` is `OreDictionary.WILDCARD_VALUE`:

```lua
recipes.remove({ output = "minecraft:wool:32767" })
```

This matches every metadata variant. The wildcard is only meaningful for
matching; creating an `ItemStack` with metadata 32767 is allowed but unusual.

## Ore Dictionary entries

Prefix an entry with `ore:`:

```lua
"ore:ingotIron"
"ore:logWood"
"ore:blockGlass"
```

`ore(name)` is a convenience function:

```lua
ore("ingotIron")   -- returns "ore:ingotIron"
```

Accepted:

- In recipe ingredients (`recipes.shaped` keys, `recipes.shapeless` list).
- As machine recipe inputs.

Not accepted:

- As a recipe output.
- As a loot item.
- As a registry ID.
- As an advancement icon.

Machine APIs resolve ore entries at queue time and error on an unknown or empty
ore name. Recipe APIs create an `OreIngredient` without validating that the ore
name has entries; an empty ore name produces a recipe that can never match.

## Stack tables

Anywhere an item is accepted, a table may be used instead of a string:

```lua
{ id = "minecraft:diamond", count = 4, meta = 0 }
```

| Field | Type | Default | Notes |
|---|---|---|---|
| `id` | string | required | Item ID, optionally with metadata suffix |
| `count` | integer | `1` | Stack size |
| `meta` | integer | `0` | Metadata; an ID suffix overrides it |

If `id` contains metadata, the suffix wins:

```lua
{ id = "minecraft:wool:14", meta = 0 }   -- metadata 14
```

`item(id)` validates the item and returns a stack table:

```lua
local stack = item("minecraft:diamond")
-- { id = "minecraft:diamond", count = 1 }

local wool = item("minecraft:wool:14")
-- { id = "minecraft:wool:14", count = 1, meta = 14 }
```

`item()` only checks that the item registry entry exists, not that the metadata
is valid. It always sets `count = 1`.

### Ingredient tables

In `recipes.shaped` keys and `recipes.shapeless` lists, a table contributes only
its `id`:

```lua
{ id = "minecraft:wool:14", count = 64 }   -- count ignored
```

Use a string with a metadata suffix when the ingredient needs metadata. The
`count` field is ignored because vanilla ingredients match a single item.

### Stack tables are not universal

| API | Accepts stack table? |
|---|---|
| `item()` | Takes a string only |
| `recipes.shaped` output | Yes |
| `recipes.shaped` keys | Yes (only `id`) |
| `recipes.shapeless` output | Yes |
| `recipes.shapeless` list | Yes (only `id`) |
| `recipes.smelting` | Yes |
| `recipes.remove` output | Yes |
| `loot.add` spec | No; use `item`, `min`, `max`, `meta` fields |
| `loot.remove` | Yes, table with `item` |
| `moonexpansion.*` | No; use the `"id * count"` string form |
| `advancement` icon | String shorthand or `{ item, data }` |

## Machine shorthand

Machine recipe arguments accept a compact string syntax:

| Form | Meaning |
|---|---|
| `"minecraft:iron_ingot"` | Item, count 1, metadata 0 |
| `"minecraft:wool:14"` | Item, count 1, metadata 14 |
| `"minecraft:iron_dust * 3"` | Item, count 3 (1..64) |
| `"ore:oreIron"` | Ore Dictionary entry (inputs only) |
| `"fluid:water * 1000"` | Fluid with amount in mB (crucible output only) |

Rules:

- The count separator is `*`, split at the first occurrence.
- Count must be 1..64 inclusive.
- `ore:` inputs expand per machine; see
  [Machines](/docs/internals/machines#input-expansion).
- `fluid:` names resolve through `FluidRegistry`; amount defaults to 1000 and
  must be at least 1.
- Machine APIs do **not** accept stack tables.

## Numeric arguments

- Energy, time and RF-per-tick values must be Lua numbers.
- They are converted with `toint()`, so fractional values are truncated.
- Values below 1 raise `value must be at least 1`.
- `chance` accepts 0..100 and defaults to 100.
- Optional arguments can be omitted; missing values use the documented default.
- A non-number where a number is required raises
  `expected a number, got '<value>'`.

## IDs versus outputs

This is the most common source of confusion:

```lua
recipes.remove("minecraft:stick")            -- OUTPUT match
recipes.remove({ id = "minecraft:stick" })   -- REGISTRY NAME match
```

A bare string is parsed as an output stack. A table's `id` field is a recipe
registry name, not an item ID. They can look identical but mean different
things.

| Concept | Example | Used by |
|---|---|---|
| Item ID | `minecraft:stick` | `item`, loot, registry, machines |
| Recipe registry ID | `minecraft:stick` (the vanilla recipe) | `recipes.remove({ id = ... })` |
| Advancement ID | `mypack:root` | `advancement.*`, `parent` |
| Loot table ID | `minecraft:chests/simple_dungeon` | `loot.*` |
| Fluid ID | `water`, `my_mod:oil` | `fluid:` shorthand, `registry.fluid` |
| Ore name | `ingotIron` | `ore:ingotIron`, `ore()` |

## Loot spec table

```lua
{
  item = "minecraft:diamond",
  min = 2,
  max = 4,
  weight = 10,
  quality = 0,
  meta = 0,
}
```

| Field | Type | Default |
|---|---|---|
| `item` | string | required |
| `min` | integer | `1` |
| `max` | integer | `min` |
| `weight` | integer | `1` |
| `quality` | integer | `0` |
| `meta` | integer | ID suffix, else `0` |

A bare string is shorthand for `{ item = <string> }`.

## Event context tables

Player:

```lua
{ name, uuid, x, y, z, dimension }
```

Position:

```lua
{ x, y, z }
```

Item:

```lua
{ id, count, meta }
```

Blocks and entities are registry ID strings. See
[Events](/docs/api/events#event-context).

## Advancement tables

Advancement definitions are converted to JSON. The conversion rules matter:

- A table with only integer keys `1..n` becomes a JSON array.
- Any other table becomes a JSON object.
- An empty table becomes `{}`, not `[]`.
- Integral numbers become JSON integers; fractional numbers become doubles.
- `nil` becomes `null` when it appears as a table value; assigning `nil` to a
  key removes it from the table in Lua, so it will not be serialized.

See [Advancements](/docs/api/advancements#definition-shape) for the schema.

## Namespaces

| API | Default namespace | Changeable? |
|---|---|---|
| `registry.*` | `moontweaker` | Yes, `registry.namespace(name)` |
| `recipes.shaped/shapeless` | `moontweaker` | No, always `moontweaker:script_n` |
| `advancement.add` | `minecraft` if omitted by `ResourceLocation` | Always write `namespace:path` |
| `loot.*` | none; table names are full resource locations | — |
| `moonexpansion.*` | none; item IDs are full resource locations | — |

## Validation summary

| Error text | Raised by |
|---|---|
| `bad item id '<id>'` | Item parsing when fewer than two parts |
| `unknown item '<id>'` | Item not in `ForgeRegistries.ITEMS` |
| `bad metadata in '<id>'` | Loot and machine metadata suffix |
| `bad loot table name '<name>'` | Loot table without `:` |
| `bad namespace '<value>'` | `registry.namespace` |
| `duplicate id '<id>'` | Registry duplicate |
| `bad advancement id '<id>'` | Advancement ID parse |
| `expected item id or table` | Recipe/loot argument type |
| `expected a number, got '<value>'` | Machine numeric argument |
| `value must be at least 1` | Machine energy/time |
| `chance must be between 0 and 100` | Machine chance |

## What comes next

[Globals](/docs/api/globals) documents `item`, `ore`, `log`, `print`, `require`
and `os`.
