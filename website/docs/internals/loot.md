---
id: loot
title: Loot
sidebar_position: 3
description: The loot operation queue, pool construction and the reflective fields MoonTweaker uses to mutate tables.
---

# Loot

Forge 1.12.2 fires `LootTableLoadEvent` for every table but exposes no public
API to add, remove or clear pools. MoonTweaker queues operations in Lua and
applies them reflectively when the table loads.

## Operation model

```java
private static abstract class Op {
    final String table;
    static class Add extends Op { Item item; int min, max, weight, quality, meta; }
    static class Remove extends Op { Item item; }
    static class Clear extends Op { }
}
private static final List<Op> ops = new ArrayList<>();
private static int poolCounter = 0;
```

| Lua call | Operation |
|---|---|
| `loot.add(table, spec)` | `Op.Add` |
| `loot.remove(table, item)` | `Op.Remove` |
| `loot.clear(table)` | `Op.Clear` |

Operations store resolved Java values, not Lua tables. A Lua table passed to
`loot.add` is parsed immediately, so later mutation of that table has no effect.

## Parsing `loot.add`

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

The string shorthand is equivalent to `{ item = <string> }`:

```lua
loot.add("minecraft:chests/simple_dungeon", "minecraft:diamond")
```

Validation:

| Condition | Error |
|---|---|
| `spec` is neither string nor table | `loot.add(): expected item id or table` |
| Table has no `item` | `loot.add(): 'item' is required` |
| `min < 1` | `loot.add(): 'min' must be at least 1` |
| `max < min` | `loot.add(): 'max' must be >= 'min'` |
| `weight < 0` | `loot.add(): 'weight' must be >= 0` |
| Unknown item | `unknown item '<id>'` |
| Non-numeric metadata suffix | `bad metadata in '<id>'` |
| Table name without `:` | `bad loot table name '<name>'` |

Defaults:

| Field | Default |
|---|---|
| `min` | `1` |
| `max` | `min` |
| `weight` | `1` |
| `quality` | `0` |
| `meta` | ID suffix, else `0` |

Metadata precedence:

1. `spec.meta`, if the key is present;
2. metadata suffix in `spec.item`;
3. `0`.

Note that `spec.meta = 0` **overrides** a suffix, because the presence check is
`!metaValue.isnil()`, not `!= 0`.

`quality` is not validated. Negative values are passed through.

## Pool construction

```java
private static void add(LootTable table, Op.Add op) {
    List<LootFunction> functions = new ArrayList<>();
    functions.add(new SetCount(new LootCondition[0],
        new RandomValueRange(op.min, op.max)));
    if (op.meta != 0) {
        functions.add(new SetMetadata(new LootCondition[0],
            new RandomValueRange(op.meta)));
    }

    String id = "moontweaker_" + poolCounter++;
    LootEntryItem entry = new LootEntryItem(
        op.item, op.weight, op.quality,
        functions.toArray(new LootFunction[0]),
        new LootCondition[0], id
    );
    table.addPool(new LootPool(
        new LootEntry[]{entry}, new LootCondition[0],
        new RandomValueRange(1f), new RandomValueRange(0f), id
    ));
}
```

Every `loot.add` creates:

- one new `LootPool`;
- one `LootEntryItem` inside it;
- `SetCount` with the `min..max` range, always;
- `SetMetadata` only when the effective metadata is non-zero;
- no conditions;
- pool rolls exactly 1, bonus rolls 0.

Because each call gets its own pool, two `loot.add` calls are rolled
independently. If you want competing weights in one roll, the current API cannot
express it.

Names share one counter:

```text
moontweaker_0
moontweaker_1
moontweaker_2
```

The counter is reset by `LootApi.reset()` when `server/` loads or reloads.

## Applying operations

```java
public static int apply(String tableName, LootTable table, Logger logger) {
    int applied = 0;
    for (Op op : ops) {
        if (!op.table.equals(tableName)) continue;
        try {
            if (op instanceof Op.Add) add(table, (Op.Add) op);
            else if (op instanceof Op.Remove) remove(table, ((Op.Remove) op).item);
            else if (op instanceof Op.Clear) pools(table).clear();
            applied++;
        } catch (Throwable e) {
            logger.error("MoonTweaker: failed to change loot table {}: {}", tableName, e.toString());
        }
    }
    return applied;
}
```

- The queue is scanned linearly for every table load.
- Operations are **not removed** after applying. Loading the same table twice
  applies them twice.
- A failing operation does not stop the rest.
- `applied` counts successful operations, not affected entries.

### Ordering

Queue order is preserved. `clear` followed by `add` produces only the new pool:

```lua
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", { item = "minecraft:diamond" })
```

`add` followed by `clear` produces an empty table.

## Removing entries

```java
private static void remove(LootTable table, Item item) {
    List<LootPool> pools = pools(table);
    for (LootPool pool : pools) {
        List<LootEntry> entries = entries(pool);
        entries.removeIf(e -> {
            if (!(e instanceof LootEntryItem)) return false;
            return entryItemField.get(e) == item;
        });
    }
    pools.removeIf(p -> entries(p).isEmpty());
}
```

Properties:

- Only `LootEntryItem` entries are inspected.
- Matching uses **object identity** (`==`), not ID or metadata.
- Metadata is ignored: removing `minecraft:wool:14` removes entries for every
  wool metadata value.
- Nested table entries, empty entries and custom entry classes are left alone.
- Pools that become empty are removed from the table.

## Reflective fields

```java
private static Field poolsField;        // LootTable.pools / field_186466_c
private static Field poolEntriesField;  // LootPool.lootEntries / field_186453_a
private static Field entryItemField;    // LootEntryItem.item / field_186368_a
```

Resolution order for each:

1. Deobfuscated field name.
2. SRG field name.
3. `RuntimeException("Field not found: <deobf> / <srg>")`, wrapped in
   `ExceptionInInitializerError`.

The fields are resolved once, in a static initializer. If they fail, the
`LootApi` class cannot initialize and the loot API is unusable for the whole
process. This is the most mapping-sensitive part of the mod.

## Reset semantics

```java
public static void reset() {
    ops.clear();
    poolCounter = 0;
}
```

Called only when the `server` stage loads. It clears operations queued by
`startup/` as well, because both stages share one list. See
[State, resets and reload](/docs/internals/mod-init/state-and-reload).

## Interaction with `/reload`

Loot tables are loaded by the server resource reload. Sequence:

```text
/moontweaker reload   -> reset queue, run server/, queue operations
/reload               -> server reloads resources, tables fire LootTableLoadEvent
```

Without `/reload`, already-loaded tables keep their old pools. Tables that load
later (for example a mod loading a table on demand) still get the queued
operations.

## Limitations

- Add-only pools: no way to add entries to an existing pool.
- No conditions, functions (other than count/metadata), loot tables or
  explosion decay.
- No quality validation.
- No nested table entry editing.
- No per-entry removal by metadata.
- Repeated table loads duplicate additions.
- Reflection breaks if field names change in a future Forge version.

## What comes next

[Registry](/docs/internals/registry) covers content creation and the material
maps.
