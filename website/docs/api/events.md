---
id: events
title: Events
sidebar_position: 7
description: events.on, the six gameplay events, context fields and cancellation rules.
---

# Events

```lua
events.on(name, callback)
```

Registers a listener. Callbacks receive one table argument. Use `server/` for
listeners that should survive `/moontweaker reload`; listeners from `startup/`
are cleared by a reload and not re-created until restart.

| Parameter | Type | Description |
|---|---|---|
| `name` | string | Event name |
| `callback` | function | Called with one context table |

Returns `nil`.

```lua
events.on("block.break", function(e)
  log.info(e.player.name .. " broke " .. e.block)
end)
```

- Listeners run in registration order.
- All listeners run even if one throws.
- Errors are logged, not added to `/moontweaker errors`.
- Unknown event names are accepted silently. A typo produces a listener that is
  never called.

## Event list

| Event | Fields | Cancelable |
|---|---|---|
| `player.logged_in` | `player` | No |
| `block.break` | `player`, `block`, `pos` | Yes |
| `block.place` | `player`, `block`, `pos` | Yes |
| `entity.death` | `entity`, `player` (may be `nil`) | Yes |
| `item.use` | `player`, `item` | Yes |
| `server.tick` | none | No |

## Event context

### `player.logged_in`

```lua
events.on("player.logged_in", function(e)
  log.info(e.player.name .. " joined")
end)
```

| Field | Type |
|---|---|
| `player` | player table |

Fires after the player joins; the player is already in the world. Not
cancelable.

### `block.break`

```lua
events.on("block.break", function(e)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)
```

| Field | Type |
|---|---|
| `player` | player table |
| `block` | block registry ID string |
| `pos` | position table |

Fires before the block is removed. `e:cancel()` prevents the break.

### `block.place`

```lua
events.on("block.place", function(e)
  if e.block == "minecraft:tnt" then
    e:cancel()
  end
end)
```

| Field | Type |
|---|---|
| `player` | player table |
| `block` | block registry ID string |
| `pos` | position table |

Fires before placement. `e:cancel()` prevents it.

### `entity.death`

```lua
events.on("entity.death", function(e)
  if e.entity == "minecraft:zombie" and e.player then
    log.info(e.player.name .. " killed a zombie")
  end
end)
```

| Field | Type |
|---|---|
| `entity` | entity registry ID string, or `"unknown"` |
| `player` | player table, or `nil` |

`player` is set only when the true damage source is a player. `e:cancel()`
cancels the death event.

### `item.use`

```lua
events.on("item.use", function(e)
  if e.item.id == "minecraft:ender_pearl" then
    e:cancel()
  end
end)
```

| Field | Type |
|---|---|
| `player` | player table |
| `item` | item table |

Fires on `PlayerInteractEvent.RightClickItem` only. It does not cover
`RightClickBlock` or `RightClickEmpty`, and cancelling it does not necessarily
stop block interaction.

### `server.tick`

```lua
local ticks = 0
events.on("server.tick", function()
  ticks = ticks + 1
  if ticks % 200 == 0 then
    log.info("10 seconds")
  end
end)
```

No fields. Fires at `TickEvent.Phase.END`, 20 times per second on a healthy
server. Not cancelable.

## Context tables

### Player

```lua
{
  name = "Player",
  uuid = "00000000-0000-0000-0000-000000000000",
  x = 0.0,
  y = 64.0,
  z = 0.0,
  dimension = 0,
}
```

### Position

```lua
{ x = 0, y = 64, z = 0 }
```

### Item

```lua
{ id = "minecraft:diamond_pickaxe", count = 1, meta = 0 }
```

### Blocks and entities

Registry ID strings:

```text
"minecraft:diamond_ore"
"minecraft:zombie"
```

Fallbacks:

| Field | Fallback |
|---|---|
| `block` | `"minecraft:air"` |
| `item.id` | `"minecraft:air"` |
| `entity` | `"unknown"` |

## Cancelling

Every context exposes `e:cancel()`:

```lua
events.on("block.place", function(e)
  if e.block == "minecraft:bedrock" then
    e:cancel()
  end
end)
```

| Event | Cancel effect |
|---|---|
| `block.break` | Cancels the break |
| `block.place` | Cancels the placement |
| `entity.death` | Cancels the death event |
| `item.use` | Cancels the interaction |
| `player.logged_in` | No effect |
| `server.tick` | No effect |

`cancel()` does not stop dispatch; later listeners still run.

## Examples

### Welcome message with coordinates

```lua
events.on("player.logged_in", function(e)
  log.info(string.format(
    "%s joined at %.1f %.1f %.1f in dimension %d",
    e.player.name, e.player.x, e.player.y, e.player.z, e.player.dimension
  ))
end)
```

### Protect a region

```lua
local protected = {
  ["100,64,100"] = true,
}

local function key(pos)
  return pos.x .. "," .. pos.y .. "," .. pos.z
end

events.on("block.break", function(e)
  if protected[key(e.pos)] then
    e:cancel()
  end
end)

events.on("block.place", function(e)
  if protected[key(e.pos)] then
    e:cancel()
  end
end)
```

### Reward kills

```lua
events.on("entity.death", function(e)
  if e.entity == "minecraft:zombie" and e.player then
    log.info(e.player.name .. " gets a reward")
  end
end)
```

### Periodic task

```lua
local ticks = 0
events.on("server.tick", function()
  ticks = ticks + 1
  if ticks % 1200 == 0 then
    log.info("one minute passed")
  end
end)
```

## Performance

- `server.tick` is the most expensive event: it fires 20 times per second.
- Keep callbacks short; avoid large loops, string concatenation and logging per
  tick.
- `EventApi.has(name)` short-circuits handlers when no listener exists, but once
  you register a `server.tick` listener it runs every tick.
- Event callbacks run on the server thread. Blocking freezes the server.

## Errors

| Condition | Error |
|---|---|
| `callback` not a function | `events.on expects a function` |
| Runtime error in a callback | Logged as `MoonTweaker: error in event '<name>': ...` |
| Unknown event name | No error; listener never fires |

Runtime errors are not recorded in `/moontweaker errors`.

## What comes next

[Advancements](/docs/api/advancements) documents `advancement.*`.
