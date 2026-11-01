---
id: quick-start
title: Quick Start
sidebar_position: 2
description: Write your first MoonTweaker script and verify that it loaded.
---

# Quick start

This page takes you from a fresh install to a working script in a few minutes.

## 1. Create a server script

Create `lua_scripts/server/example.lua`:

```lua
log.info("hello from MoonTweaker")

-- Remove every recipe that produces a stick
recipes.remove("minecraft:stick")

-- Add a shaped recipe
recipes.shaped(
  "minecraft:diamond_block",
  { "###", "###", "###" },
  { ["#"] = "minecraft:diamond" }
)

-- Add a weighted loot entry
loot.add("minecraft:chests/simple_dungeon", {
  item = "minecraft:diamond",
  min = 1,
  max = 3,
  weight = 10,
})

-- React to a block break
events.on("block.break", function(e)
  if e.block == "minecraft:diamond_ore" then
    e:cancel()
  end
end)
```

## 2. Start the game or server

Recipe registration happens once, while Forge builds the recipe registry. The
first run must therefore be a full restart:

```text
stop the game/server
start it again
```

The log should contain:

```text
[lua] hello from MoonTweaker
MoonTweaker: stage 'server' loaded, 1 scripts, 0 errors
MoonTweaker: removed 1 recipes
```

## 3. Verify

Run:

```text
/moontweaker errors
```

Expected:

```text
No script errors
```

Check the recipe in JEI or the crafting table. The stick recipe should be gone
and the diamond block recipe should use nine diamonds.

Loot is applied when each table loads. If the world was already running, run:

```text
/moontweaker reload
/reload
```

## 4. Edit and reload

For `server/` loot, event and advancement scripts you do not need to restart:

```text
/moontweaker reload
```

This re-runs every file under `lua_scripts/server/`, clears event listeners and
re-queues loot and advancement operations. It does **not** re-run `registry/`
or `startup/`, and it cannot unlock the recipe registry.

| Change | Apply with |
|---|---|
| Server loot/event/advancement script | `/moontweaker reload` (loot also needs `/reload`) |
| Server recipe script | Restart |
| `startup/` script | Restart |
| `registry/` content | Restart |
| Machine recipes | Restart |
| Added/removed mods | Restart |

Recipes are the exception: the Forge recipe registry is frozen after startup, so
`recipes.shaped`, `recipes.shapeless` and `recipes.remove` cannot take effect on
a reload. See [Recipes](/docs/api/recipes#stage-and-reload-rules).

## 5. Pick the right stage

```text
lua_scripts/
  registry/   content registration, runs at pre-initialization
  startup/    late startup work, runs at post-initialization
  server/     recipes, loot, events, advancements
              (loot/events/advancements reloadable; recipes restart)
```

Use `server/` by default. Use `registry/` only for `registry.item`,
`registry.block`, `registry.tool`, `registry.armor` and `registry.fluid`. Use
`startup/` when you want a definition to survive `/moontweaker reload` (for
example, a base advancement tree) or when a script must run after all mods have
finished post-initialization.

## 6. Where to go next

- [Script system](/docs/internals/script-engine/) — stages, ordering, sandbox,
  `require`.
- [Data types](/docs/api/data-types) — item IDs, metadata, ore dictionary,
  stack tables.
- [Recipes](/docs/api/recipes) · [Loot](/docs/api/loot) ·
  [Registry](/docs/api/registry) · [Machines](/docs/api/machines) ·
  [Events](/docs/api/events) · [Advancements](/docs/api/advancements)
- [Mod initialization](/docs/internals/mod-init/) — what actually happens
  inside Forge and MoonTweaker from jar discovery to the first tick.
