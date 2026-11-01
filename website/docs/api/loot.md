---
id: loot
title: Loot
sidebar_position: 4
description: loot.add, loot.remove and loot.clear with validation, timing and examples.
---

# Loot

The `loot` table edits loot tables. Operations are queued and applied when Forge
fires `LootTableLoadEvent` for the matching table.

Use `server/` for reloadable loot. `startup/` also works, but its operations are
discarded by `/moontweaker reload` and not re-queued until restart.

## Table names

Loot table names are full resource locations:

```lua
"minecraft:chests/simple_dungeon"
"minecraft:chests/nether_bridge"
"minecraft:chests/village_blacksmith"
"minecraft:chests/abandoned_mineshaft"
"minecraft:chests/stronghold_library"
"minecraft:entities/zombie"
```

A name must contain `:`, otherwise:

```text
bad loot table name '<name>'
```

Operations for a table that never loads are never applied. No error is raised.

## `loot.add(table, spec)`

Adds a new pool containing one weighted item entry.

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

| Parameter | Type | Description |
|---|---|---|
| `table` | string | Loot table ID |
| `spec` | string or table | Item ID shorthand or spec table |

Returns `nil`.

### Spec fields

| Field | Type | Default | Meaning |
|---|---|---|---|
| `item` | string | required | Item ID, optionally with metadata suffix |
| `min` | integer | `1` | Minimum stack size |
| `max` | integer | `min` | Maximum stack size |
| `weight` | integer | `1` | Selection weight inside the pool |
| `quality` | integer | `0` | Quality bonus scaled by luck |
| `meta` | integer | ID suffix, else `0` | Item metadata |

### Validation

| Condition | Error |
|---|---|
| `min < 1` | `loot.add(): 'min' must be at least 1` |
| `max < min` | `loot.add(): 'max' must be >= 'min'` |
| `weight < 0` | `loot.add(): 'weight' must be >= 0` |
| Missing `item` | `loot.add(): 'item' is required` |
| Wrong spec type | `loot.add(): expected item id or table` |
| Unknown item | `unknown item '<id>'` |
| Bad metadata suffix | `bad metadata in '<id>'` |
| Bad table name | `bad loot table name '<name>'` |

`quality` is not validated.

### Metadata precedence

1. `spec.meta`, if the key is present (even `0`);
2. metadata suffix in `spec.item`;
3. `0`.

```lua
loot.add("minecraft:chests/desert_pyramid", {
  item = "minecraft:wool:14",
  min = 1,
  max = 2,
  weight = 5,
})
```

### String shorthand

```lua
loot.add("minecraft:chests/stronghold_library", "minecraft:book")
```

Equivalent to `{ item = "minecraft:book", min = 1, max = 1, weight = 1 }`.

### Pool behaviour

Each call creates its own `LootPool` with one `LootEntryItem`. Two calls are
rolled independently, even for the same table. To replace a table's contents,
use `loot.clear` first.

## `loot.remove(table, item)`

Removes direct item entries from a table.

```lua
loot.remove("minecraft:chests/abandoned_mineshaft", "minecraft:rail")
loot.remove("minecraft:chests/abandoned_mineshaft", { item = "minecraft:rail" })
```

| Parameter | Type | Description |
|---|---|---|
| `table` | string | Loot table ID |
| `item` | string or table | Item ID, or `{ item = <id> }` |

Returns `nil`.

### Behaviour

- Only `LootEntryItem` entries are inspected.
- Matching is by **item identity**, so metadata is ignored:
  `minecraft:wool:14` removes entries for all wool metadata values.
- Pools that become empty are removed.
- Nested table entries, empty entries and custom entry classes are left alone.
- The item ID must resolve to a registered item.

### Errors

| Condition | Error |
|---|---|
| Wrong argument type | `loot.remove(): expected item id` |
| Unknown item | `unknown item '<id>'` |
| Bad metadata suffix | `bad metadata in '<id>'` |
| Bad table name | `bad loot table name '<name>'` |

## `loot.clear(table)`

Removes every pool from the table.

```lua
loot.clear("minecraft:chests/simple_dungeon")
```

| Parameter | Type | Description |
|---|---|---|
| `table` | string | Loot table ID |

Returns `nil`.

Usually combined with `loot.add`:

```lua
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "my_mod:mystery_token",
  min = 1,
  max = 3,
  weight = 1,
})
```

Operations apply in queue order, so `clear` then `add` leaves only the added
entries.

## When changes apply

`LootTableLoadEvent` fires when the server loads a table. MoonTweaker applies all
queued operations whose table name matches, then logs:

```text
MoonTweaker: applied 2 loot changes to minecraft:chests/simple_dungeon
```

A failure is logged and does not stop other operations:

```text
MoonTweaker: failed to change loot table <table>: <error>
```

### Applying without a restart

```text
/moontweaker reload
/reload
```

1. `/moontweaker reload` rebuilds the queue from `server/`.
2. `/reload` makes the server reload resources, firing `LootTableLoadEvent`
   again.

Already-loaded tables are not mutated by `/moontweaker reload` alone.

## Repeated loads

Operations are not consumed after applying. If the same table loads twice, the
additions are applied twice. This is usually harmless for `clear` + `add`, but
plain `add` can duplicate entries after a resource reload.

## Examples

### Add a rare drop to a dungeon chest

```lua
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 1,
  max = 1,
  weight = 1,
  quality = 2,
})
```

### Replace a table entirely

```lua
loot.clear("minecraft:chests/village_blacksmith")
loot.add("minecraft:chests/village_blacksmith", {
  item = "minecraft:iron_ingot",
  min = 2,
  max = 5,
  weight = 10,
})
loot.add("minecraft:chests/village_blacksmith", {
  item = "minecraft:gold_ingot",
  min = 1,
  max = 3,
  weight = 4,
})
```

### Remove rails from mineshaft chests

```lua
loot.remove("minecraft:chests/abandoned_mineshaft", "minecraft:rail")
```

### Add a zombie drop

```lua
loot.add("minecraft:entities/zombie", {
  item = "minecraft:emerald",
  min = 1,
  max = 1,
  weight = 1,
})
```

## Limitations

- Add-only pools; no editing of existing pools.
- No loot conditions, functions or nested tables.
- No per-metadata removal.
- No per-entry weights inside an existing pool.
- Repeated loads duplicate additions.
- Reflection-based; a future mapping change can break it.

## What comes next

[Registry](/docs/api/registry) documents content registration.
