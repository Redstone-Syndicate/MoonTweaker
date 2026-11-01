---
id: commands
title: Commands
sidebar_position: 9
description: /moontweaker reload, errors and dump, with output examples and permission notes.
---

# Commands

MoonTweaker registers one server command:

```text
/moontweaker <reload|errors|dump>
/mt <reload|errors|dump>
```

Alias: `mt`.

The command is registered at `FMLServerStartingEvent` and is available to every
player because `checkPermission` always returns `true`. There is no permission
node; use a permissions plugin on public servers.

## `/moontweaker reload`

Re-runs `lua_scripts/server/`, clears errors and counters, and applies
advancements.

```text
/moontweaker reload
```

Output:

```text
MoonTweaker reloaded: 3 scripts, 0 errors
```

With errors:

```text
MoonTweaker reloaded: 3 scripts, 2 errors
Run /moontweaker errors for details
```

What it does:

1. Clears the error list.
2. Resets the recipe counter.
3. Clears the module cache.
4. Clears event listeners, loot operations, machine operations and server
   advancement definitions.
5. Runs every `server/**.lua` file in sorted order.
6. Applies advancements.

What it does **not** do:

- Re-run `registry/` or `startup/`.
- Unfreeze the recipe registry.
- Re-apply machine recipes.
- Reload loot tables (use `/reload`).

See [Reload](/docs/internals/script-engine/reload).

## `/moontweaker errors`

Prints every recorded script error.

```text
/moontweaker errors
```

No errors:

```text
No script errors
```

With errors:

```text
config/balance.lua:7: unknown item 'minecraft:not_real'
recipes/tools.lua:12: shaped(): missing key 'X'
```

Format:

```text
<relative file>: <message>
```

The file path is relative to the stage root. The list contains only load-time
errors from `registry/`, `server/` and `startup/`; runtime event errors are
logged but not listed.

The error list is cleared by `/moontweaker reload`. Errors from `registry/` and
`startup/` disappear from this command after a reload even though those stages
did not re-run.

## `/moontweaker dump <type> [limit]`

Prints registry contents to chat.

```text
/moontweaker dump items
/moontweaker dump blocks 100
/moontweaker dump fluids
/moontweaker dump recipes 20
/moontweaker dump ores
/moontweaker dump registry
```

| Type | Contents |
|---|---|
| `items` | Item registry IDs and max stack sizes |
| `blocks` | Block registry IDs |
| `fluids` | Fluid names and densities |
| `recipes` | Recipe registry IDs and outputs |
| `ores` | Ore Dictionary names and sample items |
| `registry` | MoonTweaker's pending items and blocks |

Default type: `items`. Default limit: `50`. A non-numeric limit falls back to
`50`.

### Output examples

Items:

```text
=== Items (max 50) ===
minecraft:stone (maxStack=64)
minecraft:diamond (maxStack=64)
...
Total: 1234
```

Recipes:

```text
=== Recipes (max 20) ===
minecraft:stick -> minecraft:stick x4
...
Total: 800
```

Ores:

```text
=== Ore Dictionary (max 50) ===
ingotIron [minecraft:iron_ingot]
...
Total: 300
```

Registry:

```text
=== Pending Registry (from scripts) ===
Items: 8
  my_mod:ruby
  my_mod:copper_ingot
Blocks: 2
  my_mod:copper_block
```

`dump registry` shows the static pending lists. They are not cleared after
registration, so this works after startup.

### Known quirk

`dumpItems` contains the limit check twice:

```java
if (count++ >= limit) break;
if (count++ >= limit) break;
```

As a result, the items dump prints roughly half the requested limit even though
the header says `max <limit>`. The other dump types are not affected.

### Unknown type

```text
Unknown dump type. Use: items, blocks, fluids, recipes, ores, registry
```

### Tab completion

- First argument: `reload`, `errors`, `dump`.
- Second argument after `dump`: `items`, `blocks`, `fluids`, `recipes`,
  `ores`, `registry`.

## Permissions

```java
@Override
public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
    return true;
}
```

Every player can run the command. Restrict it externally if needed:

- Forge permissions plugins (LuckPerms, SpongeForge bridges).
- Command blocks and server console always work.
- There is no `moontweaker.command` node.

## Chat output and logging

- Command feedback is sent only to the command sender.
- Stage loading and application still log to the server log.
- `dump` output can be large; use the limit argument.

## Examples

### Reload after editing a loot script

```text
/moontweaker reload
/reload
```

### Diagnose a broken script

```text
/moontweaker errors
```

### Find an item ID

```text
/moontweaker dump items 200
```

### Check ore dictionary names

```text
/moontweaker dump ores
```

### Inspect registered content

```text
/moontweaker dump registry
```

## What comes next

[Guides](/docs/guides/advancement-trees) provides complete task-oriented
walkthroughs.
