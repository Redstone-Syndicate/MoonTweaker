---
id: faq
title: FAQ
sidebar_position: 3
description: Short answers to common MoonTweaker questions.
---

# FAQ

## General

### What is MoonTweaker?

A server-side Lua scripting mod for Minecraft 1.12.2/Forge. It edits recipes,
loot, content, machine recipes, events and advancements from `.lua` files.

### Does it need to be on the client?

| Feature | Client mod needed? |
|---|---|
| Recipes | No |
| Loot | No |
| Events | No |
| Advancements | No |
| Registered items/blocks/fluids | **Yes** |

Registered content is part of the Forge registry; clients must have the mod and
a resource pack for assets.

### Why does it require Forgelin?

The `@Mod` annotation still declares `required-after:forgelin`. Install
Forgelin-Continuous even though MoonTweaker is implemented in Java.

### Which Java version?

Java 8. The build itself can run on a newer JDK, but the mod targets Java 8.

### Which Forge version?

Minecraft 1.12.2, Forge 14.23.5.2847 or newer.

## Installation

### Where is `lua_scripts/`?

Next to `config/`, not inside it:

```text
.minecraft/lua_scripts/
<server>/lua_scripts/
run/lua_scripts/
<instance>/minecraft/lua_scripts/
```

### Why is my dev jar not working?

`./gradlew jar` produces `MoonTweaker-0.1.0-dev.jar` with MCP names. Use
`./gradlew reobfJar` and install `build/libs/MoonTweaker-0.1.0.jar`.

### The command does not exist

- The jar is not in `mods/`.
- Forgelin is missing and the mod refused to load.
- You are connected to a server that does not have MoonTweaker.
- You are looking at the wrong game directory.

## Scripts

### Which stage should I use?

| Task | Stage |
|---|---|
| Items, blocks, tools, armor, fluids | `registry/` |
| Recipes, loot, events, advancements | `server/` |
| Definitions that must survive reload | `startup/` |
| Machine recipes | `server/` or `startup/` |

Use `server/` by default.

### Why do scripts run in a strange order?

Files are sorted by their full path string. Nested folders are not guaranteed to
run after top-level files. Use numeric prefixes if order matters:

```text
server/00_base.lua
server/10_recipes.lua
server/20_loot.lua
```

### Can scripts share variables between stages?

No. Each stage gets a fresh `Globals`. Use `require` within a stage or Java-side
queues between stages.

### Can I read a file from Lua?

No. `io`, `loadfile`, `dofile` and `load` are removed. Put data in Lua source or
a module.

### Can I use Java classes from Lua?

No. `luajava` is removed.

### Why is `require` not finding my module?

- The name uses dots, not slashes: `config.balance`.
- Do not include `.lua`.
- The file must be under the **current stage** folder.
- `require("../registry/x")` is rejected.

### Why did my module not reload?

It should, because the cache is cleared on every stage load. If it did not,
check that the file is under `server/` and that `/moontweaker reload` actually
ran.

### Is there a timeout for infinite loops?

No. An infinite loop hangs the server thread. Scripts are trusted.

## Reload and restart

### What does `/moontweaker reload` do?

Re-runs only `lua_scripts/server/`, clears errors and counters, resets event,
loot, machine and server-advancement state, then applies advancements.

### What does it not do?

- Re-run `registry/` or `startup/`.
- Unfreeze recipes.
- Re-apply machine recipes.
- Reload loot tables (use `/reload`).

### Why do my recipe changes not apply after reload?

The recipe registry is frozen after startup. Restart.

### Why do my loot changes not apply after reload?

Run `/reload` as well:

```text
/moontweaker reload
/reload
```

### Why do my machine recipes not apply after reload?

Machine recipes are applied only at `FMLLoadCompleteEvent`. Restart.

### Why did my `startup/` event listener stop working?

`/moontweaker reload` clears all listeners. `startup/` does not re-run. Move the
listener to `server/` or restart.

### Why do my `startup/` advancements survive reload?

They are stored in separate `startupPending` maps. This is intentional.

## Recipes

### How do I remove a recipe by output?

```lua
recipes.remove("minecraft:stick")
```

A bare string is an output match.

### How do I remove a recipe by registry ID?

```lua
recipes.remove({ id = "minecraft:stick" })
```

### Can I remove furnace recipes?

No. `FurnaceRecipes` has no removal API in MoonTweaker.

### Why does `recipes.remove` not remove a modded recipe?

- The mod registered it after MoonTweaker's `LOWEST` hook.
- The recipe has no primary output matching your target.
- The recipe is not in `ForgeRegistries.RECIPES`.

### Can I add a recipe with a custom class?

No. Only `ShapedRecipes` and `ShapelessRecipes` are created.

### Why does my shaped recipe fail?

- A pattern character has no key.
- The pattern is empty.
- An ingredient ID is unknown.

### Why is my smelting recipe not in JEI?

`recipes.smelting` mutates `FurnaceRecipes`, which JEI may not show as a
crafting recipe. Check a furnace.

## Loot

### Why is my loot not in the chest?

- The table name is wrong.
- The table never loaded.
- You did not run `/reload` after `/moontweaker reload`.
- The operation failed; check the log.

### Can I remove a loot entry by metadata?

No. `loot.remove` matches item identity and ignores metadata.

### Can I add conditions to a loot entry?

No. Only count and metadata functions are created.

### Why did my loot duplicate?

Operations are re-applied every time the table loads. A resource reload can add
the same pool again.

## Registry

### Why is my item invisible?

Missing item model or texture in a resource pack.

### Why is my block purple/black?

Missing blockstate or block model.

### Why is my armor invisible?

Missing layer textures or wrong base name:
`<namespace>_<material>_layer_1.png`.

### Why is my fluid purple?

Missing still/flowing textures or blockstate.

### Can I register content in `server/`?

No. `registry` is restricted outside `registry/`, and the registry is frozen by
then.

### Can I add an Ore Dictionary entry for my item?

No.

### Can I change the creative tab?

No. Tabs are fixed per content type.

### Why did my second tool with the same material ignore its stats?

Tool materials are first-call-wins per `<namespace>:<material>`.

## Machines

### Why are my machine recipes missing?

- The mod is not installed.
- You did not restart.
- The recipe failed at apply time; search the log for `failed to add`.
- The mod version changed and reflection no longer matches.

### Can I remove a machine recipe?

No. The API is add-only.

### Why does Thermal Expansion reject my recipe?

Duplicate input. Thermal Expansion returns `null` for duplicates.

### Why is my IE crusher time ignored?

Only RF cost is configurable; process time is fixed.

## Events

### Why is my event not firing?

- Unknown event name (typos are silent).
- The listener was registered in `startup/` and a reload cleared it.
- The event did not occur.
- The callback threw; check the log.

### Why is my event error not in `/moontweaker errors`?

Only load-time script errors are recorded there. Runtime errors are logged.

### Can I cancel `player.logged_in`?

No.

### Does `item.use` fire when right-clicking a block?

No. It only covers `RightClickItem`.

### Can I set event priority?

No.

### Can I unsubscribe a listener?

No. Use a flag inside the callback or reload.

## Advancements

### Why is my advancement not visible?

- Missing `display`.
- Missing `display.background` on a root.
- Missing parent.
- It is a tombstone.
- It was removed by `removeAllVanilla` or a tree removal.

### Why does the client not update after a reload?

MoonTweaker calls `PlayerList.reloadResources()` when files change. If the
client still shows the old tree, reconnect and check the log for apply errors.

### Why does removing a startup advancement need a restart?

Startup maps are not rebuilt by `/moontweaker reload`. The definition remains in
`startupPending` until the next game start.

### Can I use modded triggers?

Yes, if the trigger is registered and referenced by ID in the JSON.

### Can I replace a vanilla tab?

Yes:

```lua
advancement.removeVanillaTree("minecraft:story/root")
advancement.add("minecraft:story/root", { ... })
```

## Commands and permissions

### Who can run `/moontweaker`?

Everyone. There is no permission node.

### How do I restrict it?

Use a permissions plugin or remove the command from players with a wrapper.

### Why does `dump items` show fewer than the limit?

A double-increment bug halves the output. Use a larger limit.

## Performance

### Why is my server lagging?

- A `server.tick` callback does heavy work every tick.
- An event callback loops over a large table.
- A script has an infinite loop or deep recursion.

### How often does `server.tick` fire?

20 times per second at `TickEvent.Phase.END`.

## Uninstall

### How do I remove MoonTweaker?

1. Stop the server.
2. Remove the jar.
3. Optionally delete `lua_scripts/`.
4. Optionally delete generated advancement files and manifests in each world.

Generated advancements remain active if their files remain.

## What comes next

[Logging](/docs/reference/logging) explains every log line.
