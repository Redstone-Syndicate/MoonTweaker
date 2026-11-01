---
id: errors
title: Error Reference
sidebar_position: 1
description: Every MoonTweaker error message, where it comes from and how to fix it.
---

# Error reference

MoonTweaker errors fall into three groups:

1. **Load-time script errors** — recorded and shown by `/moontweaker errors`.
2. **Apply-time errors** — logged only, never in the command output.
3. **Runtime event errors** — logged only.

The stage summary reports only a count:

```text
MoonTweaker: stage 'server' loaded, 3 scripts, 1 errors
```

## Script system

| Message | Cause | Fix |
|---|---|---|
| `invalid module root` | Stage root cannot be canonicalized | Check filesystem permissions |
| `invalid module: <name>` | `require` path escapes the stage or canonicalization fails | Keep modules inside the stage |
| `module not found: <name>` | Missing `.lua` file | Check dots→folders mapping |
| `registry API is only available in lua_scripts/registry/` | `registry.*` called outside `registry/` | Move code and restart |
| `moonexpansion: use lua_scripts/server/ or lua_scripts/startup/` | Machine call in `registry/` | Move code |
| `moonexpansion: machine recipes are applied at startup; restart the game` | Machine call after apply | Restart |
| `MoonTweaker: <mod> is not installed` | Machine API for an absent mod | Install it or guard with `moonexpansion.mods.*` |
| `events.on expects a function` | Second argument is not a function | Pass `function(e) ... end` |
| `cannot convert <type> to JSON` | Advancement definition contains a function/userdata | Use only JSON-compatible values |
| `expected item id or table` | Wrong Lua type for a stack | Pass a string or stack table |
| `expected ingredient string` | Recipe ingredient is not a string/table | Pass an item ID or `{ id = ... }` |

## Items and recipes

| Message | Cause |
|---|---|
| `bad item id '<id>'` | Missing namespace or malformed ID |
| `unknown item '<id>'` | Item not in `ForgeRegistries.ITEMS` |
| `remove() needs 'id' or 'output'` | Removal table has neither field |
| `remove() expects item id or table` | Wrong removal argument type |
| `shaped(): empty pattern` | Empty pattern table |
| `shaped(): missing key 'X'` | Pattern character has no key |
| `shapeless(): empty ingredient list` | Empty ingredient table |
| `Attempted to register to late` | Recipe registration after freeze |
| `Attempted to remove from the registry to late` | Removal after freeze |

Forge's "to late" wording is intentional in the source; it means "too late".

## Loot

| Message | Cause |
|---|---|
| `bad loot table name '<name>'` | Table name missing `:` |
| `loot.add(): 'item' is required` | Spec table has no `item` |
| `loot.add(): expected item id or table` | Spec is not a string/table |
| `loot.add(): 'min' must be at least 1` | Invalid range |
| `loot.add(): 'max' must be >= 'min'` | Invalid range |
| `loot.add(): 'weight' must be >= 0` | Negative weight |
| `bad metadata in '<id>'` | Non-numeric metadata suffix |
| `loot.remove(): expected item id` | Wrong removal argument |

## Registry

| Message | Cause |
|---|---|
| `bad namespace '<value>'` | Uppercase or unsupported characters |
| `bad id '<name>'` | Malformed resource location |
| `duplicate id '<namespace:path>'` | Same ID used twice in one load |
| `unknown material kind '<kind>'` | Not `ingot`, `dust` or `gem` |
| `unknown tool '<kind>'` | Not `pickaxe`, `axe`, `shovel`, `sword`, `hoe` |
| `unknown block material '<name>'` | Unsupported block material |
| `cannot create tool material '<name>'` | `EnumHelper` failed |
| `cannot create armor material '<name>'` | `EnumHelper` failed |
| `fluid '<id>' is already registered` | Fluid name already exists |

## Machines

| Message | Cause |
|---|---|
| `thermal.smelter needs a secondary input` | Third argument missing or not a string |
| `bad count in '<text>'` | Count after `*` is not an integer |
| `bad count N in '<text>'` | Count outside 1..64 |
| `bad metadata in '<id>'` | Metadata suffix is not an integer |
| `expected fluid:<name> [* amount]` | Crucible output is not a fluid string |
| `bad fluid amount in '<text>'` | Amount is not an integer |
| `bad fluid amount N` | Amount below 1 |
| `unknown fluid '<name>'` | Fluid not registered |
| `unknown ore '<name>'` | Ore Dictionary entry is empty |
| `expected a number, got '<value>'` | Energy/time argument is not numeric |
| `value must be at least 1` | Energy/time/RF/tick below 1 |
| `chance must be between 0 and 100` | Chance outside range |

## Advancements

| Message | Cause |
|---|---|
| `bad advancement id '<id>'` | Invalid resource location or empty path |
| `advancement.add(): expected definition table for '<id>'` | Second argument is not a table |
| `advancement.add(): definition must be a table` | Conversion produced a non-object |
| `advancement.add(): invalid definition for '<id>': <cause>` | Vanilla parser rejected it |
| `cannot convert <type> to JSON` | Definition contains a function/userdata |

## Apply-time and runtime (log only)

These never appear in `/moontweaker errors`.

```text
MoonTweaker: failed to add thermal pulverizer <item>: recipe already exists
MoonTweaker: failed to add <description>: <class or method not found>
MoonTweaker: failed to change loot table <table>: <error>
MoonTweaker: failed to apply advancements: <error>
MoonTweaker: error in event '<name>': <error>
MoonTweaker: advancement '<id>' references missing parent '<parent>'
MoonTweaker: cannot remove advancement '<id>': it does not exist
MoonTweaker: cannot remove advancement tree '<id>': it does not exist
MoonTweaker: advancement reload reported errors; check the log for missing parents or invalid definitions
```

| Message | Meaning |
|---|---|
| `failed to add <description>` | A machine recipe failed at apply time |
| `failed to change loot table <table>` | Reflection or entry mutation failed |
| `failed to apply advancements` | Disk, manager or JSON failure |
| `error in event '<name>'` | A Lua callback threw at runtime |
| `references missing parent` | Parent is not pending and not loaded |
| `cannot remove ... does not exist` | Tombstone target was not loaded |

## LuaJ errors

Generic Lua errors are not MoonTweaker messages. Common ones:

| Error | Cause |
|---|---|
| `attempt to index a nil value` | Missing table/field |
| `attempt to call a nil value` | Function name typo or API not installed |
| `attempt to compare number with nil` | Missing option |
| `bad argument #1 to '...' (string expected, got nil)` | Wrong argument type |
| `stack overflow` | Infinite recursion |
| `unexpected symbol near '...'` | Syntax error |

The chunk name is included:

```text
config/balance.lua:7: attempt to index a nil value
```

## Where to look

| Error type | Where |
|---|---|
| Load-time script errors | `/moontweaker errors` and the stage summary |
| Machine/loot/advancement apply errors | Server log |
| Event callback errors | Server log |
| Recipe freeze errors | `/moontweaker errors` |
| Missing assets | Client log and visual missing textures |

## Fixing errors in order

1. Fix compile/syntax errors first.
2. Fix `unknown item` and `bad id` errors.
3. Fix argument errors (`min`, `max`, `count`, `chance`).
4. Fix stage errors (registry/recipes/machines).
5. Re-test with a clean restart.
6. Check apply-time logs for reflection failures.

## What comes next

[Limitations](/docs/reference/limitations) lists what MoonTweaker intentionally
does not do.
