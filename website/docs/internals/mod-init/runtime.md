---
id: runtime
title: Runtime
sidebar_position: 7
description: Loot table loads, gameplay event dispatch, server ticks and threading after the world is running.
---

# Runtime

Once the server has started, MoonTweaker does three things:

1. Applies queued loot operations when a loot table loads.
2. Bridges selected Forge gameplay events into Lua callbacks.
3. Emits `server.tick` at the end of every server tick.

Everything here runs on the server thread. There is no background scheduler.

## Loot at runtime

### The hook

```java
public class LootTableHandler {
    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        int applied = LootApi.apply(event.getName().toString(), event.getTable(), logger);
        if (applied > 0) {
            logger.info("MoonTweaker: applied {} loot changes to {}", applied, event.getName());
        }
    }
}
```

`LootTableLoadEvent` fires whenever the server loads a table: at world load,
during `/reload`, and when a mod requests a table later. The event name is the
table's `ResourceLocation`, for example
`minecraft:chests/simple_dungeon`.

### How `apply` works

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

Properties:

- Operations are **not consumed**. Every load of the same table re-applies the
  full matching list. A table loaded twice gets the additions twice.
- Matching is exact string equality on the table name.
- Operations for other tables are skipped cheaply.
- A failing operation is logged and does not stop later operations.
- The returned count is the number of operations attempted successfully, not
  the number of entries added.

### Ordering

Operations run in queue order. This makes clear-then-add deterministic:

```lua
loot.clear("minecraft:chests/simple_dungeon")
loot.add("minecraft:chests/simple_dungeon", {
  item = "my_mod:token", min = 1, max = 1, weight = 1,
})
```

The `clear` empties the pool list, then `add` appends one new pool.

### Reflective mutation

Vanilla exposes no public API for removing pools or entries, so `LootApi`
resolves three fields once in a static initializer:

| Class | Deobfuscated field | SRG fallback |
|---|---|---|
| `LootTable` | `pools` | `field_186466_c` |
| `LootPool` | `lootEntries` | `field_186453_a` |
| `LootEntryItem` | `item` | `field_186368_a` |

If none of the names resolve, the static initializer throws
`ExceptionInInitializerError`, and the mod cannot use the loot API at all.
This is the most mapping-sensitive part of MoonTweaker.

### Applying changes without a restart

```text
/moontweaker reload
/reload
```

1. `reload` re-runs `server/`, which resets and rebuilds the loot queue.
2. `/reload` makes the server reload resources, including loot tables, which
   fires `LootTableLoadEvent` again.

If you only run `/moontweaker reload`, the queue changes but existing tables are
not mutated until they load again. Already-loaded tables keep their old pools
until `/reload` or a restart.

## Gameplay events

`GameplayEvents` is one Forge subscriber with six handlers. Each handler checks
`EventApi.has(name)` first, so packs that do not use an event pay only a map
lookup.

| Forge event | Lua event | Context fields | Cancelable |
|---|---|---|---|
| `PlayerEvent.PlayerLoggedInEvent` | `player.logged_in` | `player` | No |
| `BlockEvent.BreakEvent` | `block.break` | `player`, `block`, `pos` | Yes |
| `BlockEvent.PlaceEvent` | `block.place` | `player`, `block`, `pos` | Yes |
| `LivingDeathEvent` | `entity.death` | `entity`, `player` (may be nil) | Yes |
| `PlayerInteractEvent.RightClickItem` | `item.use` | `player`, `item` | Yes |
| `TickEvent.ServerTickEvent` (END) | `server.tick` | none | No |

### Dispatch

```java
public static void emit(String name, LuaValue ctx) {
    List<LuaValue> list = listeners.get(name);
    if (list == null) return;
    for (LuaValue fn : list) {
        try {
            fn.call(ctx);
        } catch (LuaError e) {
            logger.error("MoonTweaker: error in event '{}': {}", name, e.getMessage());
        } catch (Throwable e) {
            logger.error("MoonTweaker: error in event '{}': {}", name, e.toString());
        }
    }
}
```

- Listeners run in registration order.
- Every listener runs even if an earlier one throws.
- Errors are logged to the mod logger. They are **not** added to
  `/moontweaker errors`, which only records load-time script errors.
- The same context table is passed to every listener. One listener can mutate
  fields for the next listener.

### Cancelling

`ctx:cancel()` is a function stored in the context table:

```lua
events.on("block.break", function(e)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)
```

It calls the `Runnable` captured by the handler, which calls
`event.setCanceled(true)`. It does not stop dispatch; later listeners still run.
For non-cancelable events the runnable is empty and `cancel()` is a no-op.

### Context construction

Player:

```lua
{
  name = "Player",
  uuid = "00000000-0000-0000-0000-000000000000",
  x = 0.0, y = 64.0, z = 0.0,
  dimension = 0,
}
```

Position:

```lua
{ x = 0, y = 64, z = 0 }
```

Item:

```lua
{ id = "minecraft:diamond_pickaxe", count = 1, meta = 0 }
```

Blocks and entities are registry ID strings. A missing registry key falls back
to `minecraft:air` for blocks and items and `unknown` for entities.

### Event-specific behaviour

- `block.break` fires before the block is removed; cancelling prevents the
  break.
- `block.place` fires before placement; cancelling prevents it.
- `entity.death` cancels the death event. The `player` field is only set when
  the true damage source is a player.
- `item.use` only covers **right-clicking an item in air or on a block** via
  `RightClickItem`. It does not cover `RightClickBlock` or `RightClickEmpty`, and
  cancelling it does not necessarily stop block interaction.
- `player.logged_in` fires after the player joins; the player is already in the
  world.
- `server.tick` fires once per tick at `Phase.END`, i.e. 20 times per second on
  a healthy server.

## Server ticks

```java
@SubscribeEvent
public void onServerTick(TickEvent.ServerTickEvent event) {
    if (event.phase != TickEvent.Phase.END) return;
    if (!EventApi.has("server.tick")) return;
    EventApi.emit("server.tick", EventApi.context(() -> {}));
}
```

`server.tick` is the most expensive event because it fires constantly. A Lua
callback that iterates a large table or performs string formatting will show up
in the tick time. Prefer:
- a counter and modulo to run logic every N ticks;
- cached values instead of repeated lookups;
- no logging inside the callback.

```lua
local ticks = 0
events.on("server.tick", function()
  ticks = ticks + 1
  if ticks % 200 ~= 0 then return end
  log.info("10 seconds passed")
end)
```

## Threading model

| Work | Thread |
|---|---|
| `registry/`, `server/`, `startup/` loading | Mod loading thread |
| `/moontweaker reload` | Server thread (command execution) |
| Loot table load | Server thread |
| Gameplay events | Server thread |
| `server.tick` | Server thread |
| Advancement apply | Server thread, or scheduled onto it |

Lua code is always executed synchronously by the caller. There are no
coroutines scheduled by MoonTweaker, no timers and no async callbacks. A
`while true do end` in any callback freezes the server; there is no watchdog.

## Reload interaction

`/moontweaker reload` calls `EventApi.clear()` before re-running `server/`:

- Listeners registered by `server/` and by `startup/` are all removed.
- `server/` registers its listeners again.
- `startup/` does **not** re-run, so its listeners are gone until restart.

Loot behaves differently: `LootApi.reset()` clears the shared queue, then
`server/` re-queues its operations. Loot operations queued by `startup/` are
discarded and are not restored until restart.

Advancements are the exception: startup definitions are stored in separate
fields and survive the reset. See
[State, resets and reload](/docs/internals/mod-init/state-and-reload).

## What comes next

The last page of this section is the complete state machine: which fields each
stage resets, what reload preserves, and why some operations silently do
nothing after a reload. Continue with
[State, resets and reload](/docs/internals/mod-init/state-and-reload).
