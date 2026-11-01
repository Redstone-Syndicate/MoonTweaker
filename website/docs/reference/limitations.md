---
id: limitations
title: Limitations
sidebar_position: 2
description: What MoonTweaker intentionally does not do, and the constraints behind each limitation.
---

# Limitations

MoonTweaker is powerful but deliberately narrow. Knowing the limits saves time.

## Recipes

- Crafting recipe changes cannot be applied after the registry freezes. A
  restart is required.
- `recipes.remove` does not remove furnace recipes. `FurnaceRecipes` is a
  separate map with no removal API in MoonTweaker.
- Removal matches the primary output or the exact registry ID only.
- Recipes without a registry name cannot be targeted by `{ id = ... }`.
- No recipe conditions, tags, custom recipe classes, or recipe book integration.
- No client-side recipe sync beyond vanilla's normal registry sync.
- Shaped/shapeless recipes always use the `moontweaker` namespace and an
  auto-incrementing ID.
- Adding a recipe after a reload fails because the registry is frozen; the
  counter reset is not a replacement mechanism.

## Loot

- Loot changes are server-side only; clients do not need the mod.
- `loot.remove` matches direct `LootEntryItem` entries by item identity and
  ignores metadata.
- Nested tables, custom entries, conditions and functions are not exposed.
- `loot.add` creates a new pool per call; it does not merge into an existing
  pool.
- Operations for tables that never load are silently unused.
- Repeated loads of the same table re-apply operations, which can duplicate
  additions.
- Reflection on `LootTable.pools`, `LootPool.lootEntries` and
  `LootEntryItem.item` is mapping-sensitive.

## Registry

- Content can only be created once per game start. Restart after editing
  `registry/`.
- No model, blockstate, texture, language or Ore Dictionary generation.
- Registered blocks have no tile entities, harvest levels, custom drops or
  blockstate variants.
- Creative tabs are fixed:
  - items and buckets → Miscellaneous;
  - blocks → Building Blocks;
  - tools → Tools;
  - armor → Combat.
- Clients need the mod and a resource pack for registered content.
- Tool and armor materials are first-call-wins per material name.
- No fluid interaction beyond the world block and bucket.
- No custom food, potions, enchantments, entities, biomes or dimensions.
- No Ore Dictionary registration for new items.

## Machines

- Add-only; existing machine recipes cannot be removed or edited.
- Applied only at `FMLLoadCompleteEvent`; reload cannot apply them.
- Reflection-based, so mod version changes can break individual recipes.
- Thermal Expansion rejects duplicate inputs.
- Ender IO and Immersive Engineering may accept duplicate recipes.
- Immersive Engineering crusher process time is not configurable.
- Ore input expansion happens at queue time; later ore dictionary changes are
  not reflected.

## Events

- Only six events are exposed:
  `player.logged_in`, `block.break`, `block.place`, `entity.death`,
  `item.use`, `server.tick`.
- No event priority control from Lua.
- No unsubscribe or one-shot listeners.
- `item.use` does not cover right-clicking blocks (`RightClickBlock`) or empty
  air (`RightClickEmpty`).
- Listeners registered by `startup/` are lost on `/moontweaker reload`.
- Callback errors are logged but not shown by `/moontweaker errors`.
- No custom events from Lua.

## Advancements

- Applied only at server start and `/moontweaker reload`; no mid-session
  hot-apply without a reload.
- Definitions must match vanilla 1.12.2 advancement JSON.
- Only triggers registered by vanilla or other mods are available.
- Generated files live in `<world>/data/advancements/`; deleting them manually
  without updating the manifests means they are re-created on the next apply.
- Removing a definition that exists only in `startup/` requires a restart,
  because startup maps are not rebuilt by reload.
- Tombstones require the target advancement to be loaded at apply time.
- Parent resolution depends on `AdvancementManager` load order.

## Script engine

- No instruction budget, timeout or watchdog. An infinite loop hangs the server.
- Scripts are trusted code; do not load untrusted scripts.
- No filesystem, network, process or Java reflection access.
- `os` is limited to `time`, `clock` and `difftime`.
- `require` is stage-relative and cache-cleared per load.
- No module sharing between stages.
- No persistent Lua state across restarts.
- Coroutines are available but never scheduled by MoonTweaker.
- A file error aborts that file; earlier side effects remain.

## Commands

- `/moontweaker` has no permission node; every player can run it.
- `dump items` prints roughly half the requested limit due to a double
  increment.
- `dump` output goes to chat and can be large.
- No command to list advancements.
- No command to validate a script without running it.

## Configuration

- There is no mod config file. Behaviour is controlled entirely by scripts.
- No per-world script folders; all worlds share `lua_scripts/`.
- No pack metadata, load manifest or dependency resolution between scripts.
- No built-in pack enable/disable switch.

## Compatibility

- Minecraft 1.12.2 and Forge 14.23.5.2847+ only.
- Java 8 runtime.
- The `@Mod` annotation still requires `forgelin`; install Forgelin-Continuous.
- Thermal Expansion, Ender IO and Immersive Engineering integrations are
  optional and version-sensitive.
- Other mods may register recipes or loot after MoonTweaker's hooks run; those
  late entries are not seen by `recipes.remove` or startup loot queues.

## Security

- No sandbox against malicious code beyond removing dangerous globals.
- No rate limiting or resource quotas.
- A script can exhaust memory or stack.
- `/moontweaker reload` can be triggered by any player unless restricted
  externally.

## What comes next

[FAQ](/docs/reference/faq) answers common questions about these limits.
