---
id: testing
title: Testing a Script Pack
sidebar_position: 4
description: A complete checklist for verifying recipes, loot, registry content, events, machines and advancements.
---

# Testing a script pack

A script that works after `/moontweaker reload` may still fail after a clean
restart, because reload does not re-run `registry/` or `startup/` and cannot
unfreeze registries. Always test both paths.

## 1. Clean install test

1. Stop the game/server.
2. Replace the jar with the reobfuscated build:

```text
build/libs/MoonTweaker-0.1.0.jar
```

3. Delete the world's `data/advancements/` **only** if you want to test from a
   clean advancement state. Do not delete it on a production world.
4. Start the game/server.
5. Check the log for stage summaries:

```text
MoonTweaker: stage 'registry' loaded, N scripts, 0 errors
MoonTweaker: stage 'server' loaded, N scripts, 0 errors
MoonTweaker: stage 'startup' loaded, N scripts, 0 errors
```

If a stage line is missing, the stage folder was missing or the mod did not
load.

## 2. Error check

```text
/moontweaker errors
```

Expected:

```text
No script errors
```

If not, fix the first error first; later errors are often consequences.

## 3. Registry content

For each `registry.*` call:

- [ ] Item appears in the creative tab.
- [ ] Item has a model and texture (not purple/black).
- [ ] Block places and drops itself.
- [ ] Block has a blockstate and model.
- [ ] Tool mines at the intended level.
- [ ] Armor renders on the player.
- [ ] Fluid bucket exists and places fluid.
- [ ] Language key resolves (not `item.foo.name`).

Commands:

```text
/moontweaker dump registry
/moontweaker dump items
/moontweaker dump blocks
/moontweaker dump fluids
```

## 4. Recipes

- [ ] Removed recipes are gone in JEI and the crafting table.
- [ ] Shaped recipes match the intended pattern.
- [ ] Shapeless recipes accept the intended ingredients.
- [ ] Ore dictionary ingredients accept all variants.
- [ ] Smelting recipes work in a furnace.
- [ ] Recipe IDs do not collide.

Commands:

```text
/moontweaker dump recipes 100
```

Check the log for:

```text
MoonTweaker: removed N recipes
```

## 5. Loot

- [ ] `loot.add` entries appear in the target table.
- [ ] `loot.remove` removes the intended entries.
- [ ] `loot.clear` empties the table.
- [ ] Metadata variants behave as expected.
- [ ] `/moontweaker reload` followed by `/reload` applies changes.
- [ ] A clean restart produces the same result.

Check the log for:

```text
MoonTweaker: applied N loot changes to <table>
```

If the line is absent, the table did not load or the table name is wrong.

## 6. Events

- [ ] `player.logged_in` fires on join.
- [ ] `block.break` cancels when expected.
- [ ] `block.place` cancels when expected.
- [ ] `entity.death` sees the right entity and player.
- [ ] `item.use` fires on right-click.
- [ ] `server.tick` runs without lag.

Test cancellation by trying the action, not only by reading the log.

Check for:

```text
MoonTweaker: error in event '<name>': ...
```

These are not in `/moontweaker errors`.

## 7. Machines

- [ ] The mod is installed and `moonexpansion.mods.*` is true.
- [ ] Recipes appear in the machine's recipe viewer.
- [ ] Ore inputs expand as expected.
- [ ] Secondary outputs and chances work.
- [ ] A clean restart applies the recipes.

Check the log for:

```text
MoonTweaker: applied N machine recipes
MoonTweaker: failed to add <description>: <message>
```

## 8. Advancements

- [ ] The tab appears.
- [ ] Root has a background.
- [ ] Children attach to the right parent.
- [ ] Criteria complete.
- [ ] Rewards are granted.
- [ ] Removed vanilla tabs are gone.
- [ ] `/moontweaker reload` updates the tree.
- [ ] A clean restart produces the same tree.

Commands:

```text
/moontweaker reload
```

Check for:

```text
MoonTweaker: applied N custom advancements and hid M existing advancements
MoonTweaker: advancement '<id>' references missing parent '<parent>'
```

## 9. Reload test

After the clean test:

1. Edit a `server/` script.
2. `/moontweaker reload`.
3. `/reload` if loot changed.
4. Verify the change.

Then test the limits:

- [ ] Editing `startup/` and reloading does **not** apply (expected).
- [ ] Editing `registry/` and reloading does **not** apply (expected).
- [ ] Adding a recipe in `server/` and reloading fails with a frozen-registry
      error (expected).
- [ ] Machine recipes do not change after reload (expected).

If any of these unexpectedly works, the jar may be stale or a different
MoonTweaker build is installed.

## 10. Dedicated server test

- [ ] The jar and Forgelin are in `mods/`.
- [ ] The server starts without a missing-dependency crash.
- [ ] `lua_scripts/` is next to `config/`, not inside it.
- [ ] `/moontweaker errors` works from the console.
- [ ] Clients without the mod can join for recipe/loot/event/advancement packs.
- [ ] Clients have the resource pack for registered content.

## 11. Performance test

- [ ] No `server.tick` callback does heavy work every tick.
- [ ] No infinite loops or deep recursion.
- [ ] Event callbacks return quickly.
- [ ] Large loot/recipe lists do not cause noticeable startup delay.
- [ ] The server tick time is stable with the pack enabled.

## 12. Reproducible test pack

Keep a minimal pack that exercises each subsystem:

```text
lua_scripts/
  registry/
    test_content.lua
  server/
    test_recipes.lua
    test_loot.lua
    test_events.lua
  startup/
    test_advancements.lua
    test_machines.lua
```

Run it on a fresh world before shipping. If it passes, enable the real pack.

## Common failures

| Symptom | Likely cause |
|---|---|
| Stage line missing | Folder missing or mod not loaded |
| `No such command` | Jar not loaded or wrong game directory |
| Recipe changes ignored | Edited `startup/` or reloaded instead of restarting |
| Loot changes ignored | Forgot `/reload` |
| Registry content missing | Forgot restart |
| Machine recipes missing | Mod not installed or apply failed |
| Advancement missing | Invalid definition, missing parent or startup removal not restarted |
| Purple textures | Missing resource pack assets |
| Client crash on join | Client missing registered content mod |
| `/moontweaker errors` empty but behavior wrong | Runtime event error or wrong stage |

## What comes next

[Reference](/docs/reference/errors) contains the full error catalogue.
