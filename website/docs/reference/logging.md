---
id: logging
title: Logging
sidebar_position: 4
description: Every MoonTweaker log line, its level, when it appears, and where to find it.
---

# Logging

MoonTweaker writes to the standard Forge logger named **`moontweaker`**. It does
not create its own log file, and it has no logging configuration of its own.

```java
public static final Logger LOGGER = LogManager.getLogger(MODID);
```

`MODID` is `moontweaker`, so every line is tagged:

```text
[Server thread/INFO] [moontweaker]: MoonTweaker: stage 'server' loaded, 3 scripts, 0 errors
```

The exact prefix depends on your Forge/Log4j2 configuration. The logger name
`[moontweaker]` is the stable part.

## Where logs go

| Environment | Location |
|---|---|
| Client | `.minecraft/logs/latest.log` |
| Client, previous sessions | `.minecraft/logs/<date>-<n>.log.gz` |
| Dedicated server | `<server>/logs/latest.log` |
| Development run | `run/logs/latest.log` |
| Launcher | Launcher console and crash reports |

`latest.log` is rewritten on each start; older sessions are rotated into
`.log.gz` archives. MoonTweaker does not write to `config/`, the world save or
`lua_scripts/` for logging purposes.

## Log levels

MoonTweaker only uses three levels:

| Level | Used for |
|---|---|
| `INFO` | Stage summaries, successful applies, Lua `print` / `log.info` |
| `WARN` | Recoverable problems: missing parents, missing tombstones, skipped recipe removals, Lua `log.warn` |
| `ERROR` | Failed loot/machine/advancement application, event callback exceptions, Lua `log.error` |

There are no `DEBUG` or `TRACE` calls. Changing the Log4j2 level for
`moontweaker` therefore cannot reveal more detail; the mod simply does not emit
it.

## Lua logging

```lua
print("hello")
log.info("hello")
log.warn("careful")
log.error("broken")
```

All four produce a single line with the `[lua]` prefix:

```text
[Server thread/INFO] [moontweaker]: [lua] hello
[Server thread/WARN] [moontweaker]: [lua] careful
[Server thread/ERROR] [moontweaker]: [lua] broken
```

Rules:

- Exactly one argument is used. Extra arguments are ignored.
- Values are converted with `tojstring()`, so tables print as `table: 0x...`.
- Concatenate explicitly: `log.info("x=" .. x)`.
- These lines are **not** recorded in `/moontweaker errors`.

The thread name reflects where the script ran. Stage loading happens on the mod
loading thread; event callbacks and commands run on the server thread.

## Stage summaries

Every stage that has a folder logs one line after running its files:

```text
MoonTweaker: stage 'registry' loaded, 2 scripts, 0 errors
MoonTweaker: stage 'server' loaded, 4 scripts, 1 errors
MoonTweaker: stage 'startup' loaded, 2 scripts, 1 errors
```

| Field | Meaning |
|---|---|
| `stage` | `registry`, `server` or `startup` |
| `scripts` | Number of files that completed without throwing |
| `errors` | **Total accumulated errors**, not just this stage |

Two details are easy to misread:

1. **`scripts` counts successes, not files.** A file that queues half its
   content and then throws is not counted, even though its earlier side effects
   remain.
2. **`errors` is cumulative.** `ScriptManager.errors` is not cleared between
   stages. The `startup` summary includes errors from `registry` and `server`.
   The per-file messages are **not** written to the server log; only the count
   is. Run `/moontweaker errors` to see the `file: message` list. After a
   reload clears the list, the messages are gone unless you captured the
   command output.

If a stage folder does not exist, MoonTweaker creates it and returns **without
logging a summary line**. A missing `stage 'startup' loaded` line usually means
the folder was empty or absent on that run.

## Startup sequence

A typical successful startup with content in every stage:

```text
[Server thread/INFO] [moontweaker]: MoonTweaker: stage 'registry' loaded, 2 scripts, 0 errors
[Server thread/INFO] [moontweaker]: MoonTweaker: registered 6 blocks
[Server thread/INFO] [moontweaker]: MoonTweaker: registered 11 items
[Server thread/INFO] [moontweaker]: MoonTweaker: removed 4 recipes
[Server thread/INFO] [moontweaker]: MoonTweaker: stage 'server' loaded, 4 scripts, 0 errors
[Server thread/INFO] [moontweaker]: MoonTweaker: stage 'startup' loaded, 2 scripts, 0 errors
[Server thread/INFO] [moontweaker]: MoonTweaker: applied 9 machine recipes
[Server thread/INFO] [moontweaker]: MoonTweaker: applied 12 custom advancements and hid 38 existing advancements
[Server thread/INFO] [moontweaker]: MoonTweaker: applied 2 loot changes to minecraft:chests/simple_dungeon
```

The order is fixed by Forge, not by MoonTweaker:

1. `registry` at pre-initialization.
2. Block/item registration events.
3. `server` inside the recipe registry event.
4. `startup` at post-initialization.
5. Machine apply at load complete.
6. Advancement apply at server started.
7. Loot apply whenever a table loads.

See [Mod initialization](/docs/internals/mod-init/) for the full lifecycle.

## Complete message catalogue

### Script engine

| Message | Level | When |
|---|---|---|
| `MoonTweaker: stage '<stage>' loaded, <n> scripts, <m> errors` | INFO | After a stage folder runs |
| `[lua] <message>` | INFO/WARN/ERROR | `print`, `log.info`, `log.warn`, `log.error` |

### Registry

| Message | Level | When |
|---|---|---|
| `MoonTweaker: registered <n> blocks` | INFO | Block registry event, only if the list is non-empty |
| `MoonTweaker: registered <n> items` | INFO | Item registry event, only if the list is non-empty |

### Recipes

| Message | Level | When |
|---|---|---|
| `MoonTweaker: removed <n> recipes` | INFO | Every `recipes.remove` call, including `0` |
| `MoonTweaker: cannot remove recipe <id>: <message>` | WARN | A single removal threw |

### Loot

| Message | Level | When |
|---|---|---|
| `MoonTweaker: applied <n> loot changes to <table>` | INFO | A table loaded and at least one operation matched |
| `MoonTweaker: failed to change loot table <table>: <error>` | ERROR | One operation threw |

### Machines

| Message | Level | When |
|---|---|---|
| `MoonTweaker: applied <n> machine recipes` | INFO | Load complete, only if the queue was non-empty |
| `MoonTweaker: failed to add <description>: <message>` | ERROR | One queued machine recipe threw |

### Advancements

| Message | Level | When |
|---|---|---|
| `MoonTweaker: applied <n> custom advancements and hid <m> existing advancements` | INFO | Files changed or a manifest was non-empty |
| `MoonTweaker: advancement '<id>' references missing parent '<parent>'` | WARN | Parent is neither pending nor loaded |
| `MoonTweaker: cannot remove advancement '<id>': it does not exist` | WARN | Tombstone target not loaded |
| `MoonTweaker: cannot remove advancement tree '<id>': it does not exist` | WARN | Tree root not loaded |
| `MoonTweaker: advancement reload reported errors; check the log for missing parents or invalid definitions` | WARN | `AdvancementManager.hasErrored()` after reload |
| `MoonTweaker: failed to apply advancements: <exception>` | ERROR | The whole apply threw |

The counts in the applied line are the size of the merged pending map and the
size of the tombstone target set, not the number of files actually written.

### Events

| Message | Level | When |
|---|---|---|
| `MoonTweaker: error in event '<name>': <message>` | ERROR | A Lua callback threw |

## What is not logged

Silence is meaningful in several places:

| Situation | Log output |
|---|---|
| No machine recipes queued | No `applied ... machine recipes` line |
| No loot operations match a table | No loot line for that table |
| No advancement changes and empty manifests | No advancement line |
| A stage folder is missing | No stage summary line |
| An unchanged advancement file | Nothing; the file is not rewritten |
| A loot table never loads | Nothing; queued operations stay unused |
| `dump` command output | Chat only, not the log |

## Reload logs

`/moontweaker reload` produces a fresh stage summary and then an advancement
line if anything changed:

```text
[Server thread/INFO] [moontweaker]: [lua] reloading example
[Server thread/INFO] [moontweaker]: MoonTweaker: stage 'server' loaded, 1 scripts, 0 errors
[Server thread/INFO] [moontweaker]: MoonTweaker: applied 1 custom advancements and hid 0 existing advancements
```

Loot is not re-applied by this command. After `/reload`, each table that loads
logs its own line:

```text
[Server thread/INFO] [moontweaker]: MoonTweaker: applied 2 loot changes to minecraft:chests/simple_dungeon
```

Machine recipes are not applied again, so no new
`applied ... machine recipes` line appears after a reload.

## Log versus `/moontweaker errors`

| Information | Server log | `/moontweaker errors` |
|---|---|---|
| Load-time script errors | Stage summary count only | Full `file: message` list |
| Registry/startup errors after a reload | Stage count remains; the message was never logged | **Cleared** |
| Event callback errors | Yes | No |
| Loot apply errors | Yes | No |
| Machine apply errors | Yes | No |
| Advancement apply errors | Yes | No |
| Lua `log.*` output | Yes | No |

The command clears `ScriptManager.errors` before re-running `server/`. Errors
from `registry/` and `startup/` therefore disappear from the command output even
though those stages did not re-run. The original lines remain in the log.

## Reading logs efficiently

```bash
# Everything from the mod
grep "moontweaker" logs/latest.log

# Lua output only
grep "\[lua\]" logs/latest.log

# Apply-time failures
grep -E "failed to (add|change|apply)" logs/latest.log

# Stage summaries
grep "stage '" logs/latest.log

# Advancement warnings
grep "advancement" logs/latest.log
```

On a dedicated server, `latest.log` is usually the fastest source. Crash reports
include the full log tail and the Forge mod list.

## Thread names

| Work | Typical thread |
|---|---|
| `registry`, `server`, `startup` loading | `mod loading worker` / main |
| Commands, loot, events, ticks | `Server thread` |
| Advancement apply | `Server thread`, or scheduled onto it |

A `[lua]` line's thread tells you where the script ran. A `server.tick` log line
always shows `Server thread`.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| No `[moontweaker]` lines at all | Mod not loaded, wrong `mods/` folder, or Forgelin missing |
| Stage line missing for one stage | Folder absent or empty at that run |
| `errors` count grows between stages | Earlier stage errors are still in the list; this is expected |
| `/moontweaker errors` is empty but behavior is wrong | Runtime/apply error, or a reload cleared the list |
| No machine apply line | No machine recipes queued, or the stage never ran |
| No loot line | Table name mismatch or table not loaded |
| No advancement line | No definitions, no tombstones, and no previous manifests |

## What comes next

[License](/docs/reference/license) explains the project's licensing and
attribution requirements.
