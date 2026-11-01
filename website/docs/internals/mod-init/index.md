---
id: index
title: Mod Initialization Overview
slug: /internals/mod-init/
sidebar_position: 1
description: The complete Forge 1.12.2 lifecycle of MoonTweaker, from jar discovery to the first server tick.
---

# Mod initialization overview

This section follows MoonTweaker from the moment Forge finds the jar to the
first server tick. It explains **why** the script stages exist, which Forge
event each one runs on, and what the mod has already done by the time your Lua
code executes.

The short version:

```mermaid
sequenceDiagram
    autonumber
    participant FML as Forge/FML
    participant MT as MoonTweaker
    participant Lua as Lua scripts
    participant REG as Forge registries

    FML->>MT: construct @Mod instance
    FML->>MT: FMLPreInitializationEvent
    MT->>Lua: load registry/
    Lua-->>MT: queue items, blocks, tools, armor, fluids
    MT->>FML: register RegistryHandler, LootTableHandler, RecipeRegistryHook, GameplayEvents
    FML->>REG: RegistryEvent.Register<Block>
    MT->>REG: register pending blocks
    FML->>REG: RegistryEvent.Register<Item>
    MT->>REG: register pending items and ItemBlocks
    FML->>REG: RegistryEvent.Register<IRecipe> (LOWEST)
    MT->>Lua: load server/
    Lua-->>REG: add/remove recipes immediately; queue loot, events, machines, advancements
    FML->>MT: FMLPostInitializationEvent
    MT->>Lua: load startup/
    Lua-->>MT: queue loot, events, machines, advancements
    FML->>MT: FMLLoadCompleteEvent
    MT->>MT: apply queued machine recipes
    FML->>MT: FMLServerStartingEvent
    MT->>FML: register /moontweaker
    FML->>MT: FMLServerStartedEvent
    MT->>MT: write advancement JSON, reload AdvancementManager, resync players
    FML->>MT: LootTableLoadEvent (per table)
    MT->>MT: apply queued loot operations
    FML->>MT: gameplay events / server tick
    MT->>Lua: emit event callbacks
```

## The lifecycle table

| # | Forge moment | MoonTweaker action | Script stage | State that becomes legal |
|---|---|---|---|---|
| 1 | Mod construction | Static fields created: logger, `ScriptManager`, constants | — | — |
| 2 | `FMLPreInitializationEvent` | `loadRegistry()`; register four event handlers | `registry/` | `registry.*` queues content; `FluidRegistry` is open |
| 3 | `RegistryEvent.Register<Block>` | `RegistryHandler` registers pending blocks | — | Blocks enter the registry |
| 4 | `RegistryEvent.Register<Item>` | `RegistryHandler` registers pending items and `ItemBlock`s | — | Items enter the registry |
| 5 | `RegistryEvent.Register<IRecipe>` at `LOWEST` | `RecipeRegistryHook` calls `loadServer()` | `server/` | Crafting recipes can be added/removed |
| 6 | `FMLPostInitializationEvent` | `loadStartup()` | `startup/` | Late queueing; recipe registry is already frozen |
| 7 | `FMLLoadCompleteEvent` | `ExpansionApi.apply()` | — | Machine recipe queue is flushed |
| 8 | `FMLServerStartingEvent` | Register `/moontweaker` | — | Server command available |
| 9 | `FMLServerStartedEvent` | `AdvancementApi.apply(server)` | — | World exists; advancement files can be written |
| 10 | `LootTableLoadEvent` | `LootApi.apply(name, table)` | — | Loot tables can be mutated |
| 11 | Gameplay events | `GameplayEvents` → `EventApi.emit` | — | Player/block/entity callbacks |
| 12 | `TickEvent.ServerTickEvent` (END) | `EventApi.emit("server.tick")` | — | Per-tick callbacks |

Two details are easy to get wrong:

1. **`server/` runs before `startup/`.** Forge fires the recipe registry event
   inside `CraftingHelper.loadRecipes()` during `Loader.initializeMods()`,
   before `FMLPostInitializationEvent` is distributed. MoonTweaker registers
   the recipe hook at `EventPriority.LOWEST` so every other mod has already
   added its recipes when your server scripts run.
2. **`registry/` does not register anything directly.** It queues Java objects.
   The actual `RegistryEvent.Register<Block/Item>` calls happen later, after
   every mod's pre-init handler has finished.

## Why three stages?

Forge 1.12.2 freezes different systems at different times:

| System | Legal window | Stage |
|---|---|---|
| Blocks and items | `RegistryEvent.Register<Block/Item>` during pre-init | `registry/` queues, handlers register |
| Crafting recipes (`IRecipe`) | `RegistryEvent.Register<IRecipe>` during `Loader.initializeMods()` | `server/` |
| Loot tables | `LootTableLoadEvent`, whenever a table loads | `server/` or `startup/` queue |
| Machine recipe managers | After all mods initialized (`FMLLoadCompleteEvent`) | `server/` or `startup/` queue |
| Advancements | Server started, because they live in the world save | `server/` or `startup/` queue |

A single script folder cannot satisfy all of these. Running everything at
pre-init would make recipes from other mods invisible; running everything at
post-init would be too late to register blocks and items. The three stages are
the minimum split that keeps each operation inside its legal window.

## Stage capabilities

| Operation | `registry/` | `server/` | `startup/` |
|---|---|---|---|
| `registry.*` | Yes | Error | Error |
| `recipes.*` | Not effective | Yes | Too late |
| `loot.*` | Queued, then discarded | Yes | Yes |
| `moonexpansion.*` | Error | Queued | Queued |
| `advancement.*` | Queued, then discarded | Yes | Queued |
| `events.on` | Cleared when `server/` loads | Yes | Yes |
| `require` | Relative to `registry/` | Relative to `server/` | Relative to `startup/` |

Only `registry` and `moonexpansion` are explicitly stage-gated in code. The
other rows describe when operations are effective and safe. See
[State, resets and reload](/docs/internals/mod-init/state-and-reload) for the
exact reset rules.

## Source map

| File | Role in initialization |
|---|---|
| `MoonTweaker.java` | `@Mod` entry point; five `@Mod.EventHandler` methods; static `SCRIPTS` and `LOGGER` |
| `api/MoonTweakerApi.java` | `currentStage` string read by stage-gated APIs |
| `script/ScriptManager.java` | `load(stage)`, `createGlobals()`, file discovery, error capture |
| `registry/RegistryHandler.java` | Subscribes to block and item registry events |
| `event/RecipeRegistryHook.java` | Subscribes to the recipe registry event at `LOWEST` |
| `loot/LootTableHandler.java` | Subscribes to `LootTableLoadEvent` |
| `event/GameplayEvents.java` | Subscribes to player, block, entity and tick events |
| `expansion/ExpansionApi.java` | Queues machine recipes; `apply()` at load complete |
| `advancement/AdvancementApi.java` | Queues definitions; `apply(server)` at server started and reload |
| `command/MoonTweakerCommand.java` | `/moontweaker reload`, `errors`, `dump` |

## Reading order

1. [Discovery and pre-initialization](/docs/internals/mod-init/discovery-and-preinit)
   — how Forge finds the jar, constructs the mod and runs `registry/`.
2. [Block and item registry events](/docs/internals/mod-init/registry-events)
   — how queued content becomes real registry entries.
3. [Recipe registration](/docs/internals/mod-init/recipe-registration) — why
   `server/` runs at `LOWEST` and what it can do.
4. [Post-initialization and load complete](/docs/internals/mod-init/postinit-and-load-complete)
   — `startup/` and machine recipe application.
5. [Server lifecycle](/docs/internals/mod-init/server-lifecycle) — commands,
   advancements and per-world state.
6. [Runtime](/docs/internals/mod-init/runtime) — loot, gameplay events, ticks
   and threading.
7. [State, resets and reload](/docs/internals/mod-init/state-and-reload) — the
   complete state machine and what `/moontweaker reload` actually does.

:::note Forge version
The sequence above matches Forge 14.23.5.2847 for Minecraft 1.12.2. The
relevant Forge internals are `Loader.preinitializeMods()`,
`Loader.initializeMods()`, `CraftingHelper.loadRecipes()` and
`GameData.fireRegistryEvents()`.
:::
