---
id: advancements
title: Advancements
sidebar_position: 8
description: advancement.add and the remove functions, with the full definition schema and tombstone semantics.
---

# Advancements

The `advancement` table defines custom advancements and hides vanilla or modded
ones. Definitions are queued in Lua, written as JSON at server start (or on
`/moontweaker reload`), then loaded by vanilla's `AdvancementManager`.

## `advancement.add(id, definition)`

```lua
advancement.add("mypack:root", {
  display = {
    icon = "minecraft:crafting_table",
    title = { text = "My Pack" },
    description = { text = "A new advancement tree" },
    background = "minecraft:textures/gui/advancements/backgrounds/stone.png",
  },
  criteria = {
    tick = { trigger = "minecraft:tick" },
  },
})
```

| Parameter | Type | Description |
|---|---|---|
| `id` | string | `namespace:path` |
| `definition` | table | Vanilla advancement JSON shape |

Returns `nil`.

### Definition shape

| Field | Required | Notes |
|---|---|---|
| `display` | For visible advancements | Omit for invisible technical advancements |
| `display.icon` | Yes for display | String shorthand or `{ item, data }` |
| `display.title` | Yes for display | Text component |
| `display.description` | Yes for display | Text component |
| `display.background` | Root only | Texture path |
| `display.frame` | No | `task`, `goal`, `challenge` |
| `display.show_toast` | No | Default true |
| `display.announce_to_chat` | No | Default true |
| `display.hidden` | No | Default false |
| `parent` | No | Full advancement ID; omit for a root |
| `criteria` | Yes | Map of criterion name to trigger |
| `requirements` | No | Array of arrays of criterion names |
| `rewards` | No | `experience`, `recipes`, `loot`, `function` |

The definition is validated through `AdvancementManager.GSON` before it is
queued. Invalid definitions raise a Lua error immediately.

### Icon shorthand

```lua
display = { icon = "minecraft:diamond" }
```

is rewritten to:

```json
"display": { "icon": { "item": "minecraft:diamond" } }
```

For metadata:

```lua
display = { icon = { item = "minecraft:wool", data = 14 } }
```

### Criteria

```lua
criteria = {
  got_diamond = {
    trigger = "minecraft:inventory_changed",
    conditions = {
      items = {
        { item = "minecraft:diamond", count = 1 },
      },
    },
  },
}
```

Any trigger registered by vanilla or another mod works.

### Requirements

```lua
criteria = {
  a = { trigger = "minecraft:tick" },
  b = { trigger = "minecraft:tick" },
},
requirements = {
  { "a", "b" },   -- both required
}
```

Omit `requirements` to use vanilla's default.

### Parent

```lua
advancement.add("mypack:quest1", {
  parent = "mypack:root",
  display = { ... },
  criteria = { ... },
})
```

The parent may be:
- another definition in the same apply;
- an advancement already loaded by vanilla or another mod;
- a tombstoned advancement (it still exists, invisible).

A missing parent is logged as a warning. The file is still written.

### Rewards

```lua
rewards = {
  experience = 100,
  recipes = { "minecraft:diamond_block" },
  loot = { "minecraft:chests/simple_dungeon" },
}
```

Passed through to vanilla unchanged.

## `advancement.remove(id)`

Removes a **custom** definition queued by MoonTweaker.

```lua
advancement.remove("mypack:quest1")
```

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Custom advancement ID |

Returns `nil`.

- Removes the ID from the merged pending map at apply time.
- Deletes the generated file on the next apply.
- Does **not** tombstone a vanilla advancement with the same ID. Use
  `removeVanillaAdvancement` for that.

## `advancement.removeVanillaAdvancement(id)`

Hides a single advancement from vanilla or another mod.

```lua
advancement.removeVanillaAdvancement("minecraft:story/mine_stone")
```

Alias: `advancement.remove_vanilla`.

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Advancement ID to hide |

Returns `nil`.

A tombstone file is generated with the same ID, no display and an impossible
criterion. Removing the Lua call restores the original on the next apply.

If the advancement does not exist at apply time:

```text
MoonTweaker: cannot remove advancement '<id>': it does not exist
```

## `advancement.removeVanillaTree(id)`

Hides a whole tree: the given root and every descendant.

```lua
advancement.removeVanillaTree("minecraft:story/root")
```

Alias: `advancement.remove_vanilla_tree`.

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Root advancement ID |

Returns `nil`.

- Walks `advancement.getChildren()` recursively.
- Custom definitions with the same IDs are kept and win over tombstones.
- A non-existent root logs a warning.

## `advancement.removeAllVanilla()`

Hides every loaded advancement in the `minecraft` namespace.

```lua
advancement.removeAllVanilla()
```

Alias: `advancement.remove_all_vanilla`.

Returns `nil`.

- Only `minecraft:*` IDs are targeted.
- Modded namespaces are untouched.
- Custom definitions are kept.
- The set is resolved at apply time from the currently loaded manager.

## Complete example

```lua
-- lua_scripts/server/advancements.lua

advancement.removeVanillaTree("minecraft:story/root")

advancement.add("mypack:root", {
  display = {
    icon = "minecraft:crafting_table",
    title = { text = "My Pack" },
    description = { text = "A new beginning" },
    background = "minecraft:textures/gui/advancements/backgrounds/stone.png",
  },
  criteria = {
    tick = { trigger = "minecraft:tick" },
  },
})

advancement.add("mypack:get_wood", {
  parent = "mypack:root",
  display = {
    icon = "minecraft:oak_log",
    title = { text = "Lumberjack" },
    description = { text = "Get a log" },
    frame = "task",
  },
  criteria = {
    log = {
      trigger = "minecraft:inventory_changed",
      conditions = {
        items = {
          { item = "minecraft:log" },
        },
      },
    },
  },
})

advancement.add("mypack:get_diamond", {
  parent = "mypack:get_wood",
  display = {
    icon = "minecraft:diamond",
    title = { text = "Shiny" },
    description = { text = "Get a diamond" },
    frame = "goal",
  },
  criteria = {
    diamond = {
      trigger = "minecraft:inventory_changed",
      conditions = {
        items = {
          { item = "minecraft:diamond" },
        },
      },
    },
  },
  rewards = {
    experience = 50,
  },
})
```

## Timing

| Stage | Behaviour |
|---|---|
| `server/` | Queued; applied at `FMLServerStartedEvent` and on `/moontweaker reload` |
| `startup/` | Queued in separate maps; applied at server start and preserved across reload |
| `registry/` | Queued, but discarded when `server/` loads |

Advancements cannot be applied during pre-init or post-init because there is no
world save yet.

## Startup versus server

- Definitions in `startup/` survive `/moontweaker reload`, because they are
  stored in `startupPending` and friends.
- Definitions in `server/` are cleared and re-queued on reload.
- A server definition with the same ID as a startup definition wins.
- Removing a startup definition requires a restart; the startup map is not
  rebuilt by reload.

## Custom wins over tombstones

At apply time, every custom pending ID is removed from the tombstone target set:

```java
targets.removeAll(pending.keySet());
```

This lets a pack replace a vanilla tree root with its own definition:

```lua
advancement.removeVanillaTree("minecraft:story/root")
advancement.add("minecraft:story/root", { ... })
```

## Files and manifests

MoonTweaker writes:

```text
<world>/data/advancements/<namespace>/<path>.json
<world>/data/advancements/.moontweaker
<world>/data/advancements/.moontweaker-vanilla
```

- Custom definitions are written as pretty JSON.
- Tombstones use the same path as the original ID.
- `.moontweaker` lists generated custom IDs.
- `.moontweaker-vanilla` lists generated tombstones.
- Removing a Lua definition deletes its file on the next apply.
- Unchanged files are not rewritten.

Do not edit these files by hand.

## Errors

| Condition | Error |
|---|---|
| Bad ID | `bad advancement id '<id>'` |
| Definition not a table | `advancement.add(): expected definition table for '<id>'` |
| Vanilla validation failure | `advancement.add(): invalid definition for '<id>': <cause>` |
| Missing parent | Warning, file still written |
| Remove non-existent advancement | Warning, skipped |
| Remove non-existent tree | Warning, skipped |
| Disk write failure | `failed to apply advancements`, logged |

## Limitations

- Definitions must be expressible as vanilla 1.12.2 advancement JSON.
- No dynamic criteria; use vanilla triggers.
- Root backgrounds must be valid texture paths.
- Parent resolution depends on load order in `AdvancementManager`.
- Tombstones require the target to be loaded at apply time.
- Removing a startup definition requires a restart.

## What comes next

[Commands](/docs/api/commands) documents `/moontweaker`.
