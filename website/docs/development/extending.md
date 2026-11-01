---
id: extending
title: Extending MoonTweaker
sidebar_position: 2
description: Add a new Lua API table, wire it into the lifecycle, follow the reset and error conventions, and document it.
---

# Extending MoonTweaker

This page walks through adding a new Lua API to MoonTweaker. The example is
deliberately small, but it exercises every convention the existing APIs use:
stage gating, static state, deferred apply, per-operation error isolation and
documentation.

The mod is Java 8 code. Do not use `var`, records, switch expressions or other
post-8 language features.

## How the existing APIs fit together

```text
ScriptManager.createGlobals()
  ├── RecipeApi.install(g, logger, this)
  ├── EventApi.install(g, logger)
  ├── LootApi.install(g)
  ├── RegistryApi.install(g)
  ├── ExpansionApi.install(g)
  └── AdvancementApi.install(g)
```

Each API class:

1. Builds a `LuaValue` table.
2. Binds functions with LuaJ function classes.
3. Validates arguments immediately.
4. Either performs the action or queues it.
5. Exposes `reset()` and/or `apply()` when it owns state.

`ScriptManager.load(stage)` owns the stage resets. `MoonTweaker` owns the Forge
lifecycle handlers.

## Worked example: an `example` API

Create `src/main/java/com/moontweaker/example/ExampleApi.java`:

```java
package com.moontweaker.example;

import com.moontweaker.api.MoonTweakerApi;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;

import java.util.ArrayList;
import java.util.List;

public final class ExampleApi {

    private static final List<String> queue = new ArrayList<>();
    private static boolean applied = false;

    private ExampleApi() {
    }

    public static void reset() {
        queue.clear();
        applied = false;
    }

    public static void install(Globals g, Logger logger) {
        LuaValue example = LuaValue.tableOf();

        example.set("echo", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String value = arg.checkjstring();
                logger.info("MoonTweaker: example echo {}", value);
                return LuaValue.valueOf(value);
            }
        });

        example.set("queue", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if ("registry".equals(MoonTweakerApi.currentStage)) {
                    throw new LuaError(
                        "example.queue: use lua_scripts/server/ or lua_scripts/startup/"
                    );
                }
                if (applied) {
                    throw new LuaError(
                        "example.queue: already applied; restart the game"
                    );
                }
                queue.add(arg.checkjstring());
                return LuaValue.NIL;
            }
        });

        g.set("example", example);
    }

    public static void apply(Logger logger) {
        if (queue.isEmpty()) {
            applied = true;
            return;
        }
        int count = 0;
        for (String value : queue) {
            try {
                logger.info("MoonTweaker: example applied {}", value);
                count++;
            } catch (Throwable e) {
                logger.error(
                    "MoonTweaker: failed to apply example {}: {}",
                    value, e.toString()
                );
            }
        }
        queue.clear();
        applied = true;
        logger.info("MoonTweaker: applied {} example entries", count);
    }
}
```

Key points:

| Convention | Why |
|---|---|
| `final` class, private constructor | Matches the other API classes; all state is static |
| `install(Globals, Logger)` | Same signature family as the existing APIs |
| `checkjstring()` | Produces a LuaJ argument error with the Lua call site |
| `LuaError` for user mistakes | Recorded by `ScriptManager.runFile` and shown by `/moontweaker errors` |
| Stage check at call time | The table is installed in every stage, so the check cannot live only in `install` |
| `applied` flag | Prevents queuing work that can never run |
| Per-entry `try`/`catch` | One bad entry must not stop the rest |
| `MoonTweaker:` log prefix | Distinguishes mod logs from `[lua]` output |

## Wire it into `ScriptManager`

### Install the table

In `ScriptManager.createGlobals()`, add the call after the existing APIs:

```java
RecipeApi.install(g, logger, this);
EventApi.install(g, logger);
LootApi.install(g);
RegistryApi.install(g);
ExpansionApi.install(g);
AdvancementApi.install(g);
ExampleApi.install(g, logger);   // new
return g;
```

Order matters only if one API reads another's globals. None of the existing
APIs do, so appending is safe.

### Reset it at the right stage

In `ScriptManager.load(String stage)`, add the reset to the `server` case:

```java
case "server":
    EventApi.clear();
    LootApi.reset();
    ExpansionApi.reset();
    AdvancementApi.reset();
    ExampleApi.reset();   // new
    break;
```

Decide deliberately:

- Reset on `server` if `/moontweaker reload` should rebuild the queue.
- Reset on `registry` if the state belongs to content creation.
- Do **not** reset on `startup`; that stage is designed to append.
- If the API has startup-only state that must survive reload, use separate
  `startup*` fields, as `AdvancementApi` does.

See [State, resets and reload](/docs/internals/mod-init/state-and-reload) for the
full matrix.

### Apply it at a lifecycle event

If the API defers work, call `apply` from the matching Forge handler. For
`FMLLoadCompleteEvent`, extend the existing method:

```java
@Mod.EventHandler
public void loadComplete(FMLLoadCompleteEvent event) {
    ExpansionApi.apply(LOGGER);
    ExampleApi.apply(LOGGER);   // new
}
```

If the API needs the server, use `FMLServerStartedEvent`:

```java
@Mod.EventHandler
public void serverStarted(FMLServerStartedEvent event) {
    AdvancementApi.apply(
        FMLCommonHandler.instance().getMinecraftServerInstance(),
        LOGGER
    );
    ExampleApi.apply(LOGGER);   // only if it needs a live server
}
```

If it needs a world save, follow the advancement pattern: take a
`MinecraftServer`, check `isCallingFromMinecraftThread()`, and use
`addScheduledTask` when off-thread.

If it needs a Forge event, add a subscriber instead. See below.

## Adding a Forge event subscriber

There are two options.

### Option A: extend `GameplayEvents`

This is the simplest path because the context helpers (`playerTable`,
`posTable`, `itemTable`, `blockId`, `entityId`) are already there:

```java
@SubscribeEvent
public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
    if (!EventApi.has("player.logged_out")) return;
    LuaValue ctx = EventApi.context(() -> {});
    ctx.set("player", playerTable(event.player));
    EventApi.emit("player.logged_out", ctx);
}
```

Then add `player.logged_out` to the event tables in
[Events](/docs/api/events), [Runtime](/docs/internals/mod-init/runtime) and the
limitations page.

### Option B: create a new subscriber class

```java
package com.moontweaker.example;

import com.moontweaker.event.EventApi;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.luaj.vm2.LuaValue;

public class ExampleEvents {

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!EventApi.has("player.logged_out")) return;

        LuaValue ctx = EventApi.context(() -> {});
        LuaValue player = LuaValue.tableOf();
        player.set("name", LuaValue.valueOf(event.player.getName()));
        player.set("uuid", LuaValue.valueOf(event.player.getUniqueID().toString()));
        ctx.set("player", player);

        EventApi.emit("player.logged_out", ctx);
    }
}
```

Register it in `MoonTweaker.preInit`:

```java
MinecraftForge.EVENT_BUS.register(new ExampleEvents());
```

Rules for subscribers:

1. Register in `preInit`, before registry events fire.
2. Check `EventApi.has(name)` before building any context.
3. Use `EventApi.context(Runnable)` so Lua gets `ctx:cancel()`.
4. Pass the real cancellation runnable only for cancelable events; use `() -> {}`
   otherwise.
5. Do not block the server thread.
6. Log callback failures through `EventApi.emit`; do not catch them yourself.

## Stage gating

There are two patterns in the codebase:

### Install-time gating

`RegistryApi` decides once, when globals are created:

```java
if (!"registry".equals(MoonTweakerApi.currentStage)) {
    g.set("registry", restricted());
    return;
}
```

Use this when the entire API is meaningless outside one stage.

### Call-time gating

`ExpansionApi` checks on every call:

```java
if ("registry".equals(MoonTweakerApi.currentStage)) {
    throw new LuaError("moonexpansion: use lua_scripts/server/ or lua_scripts/startup/");
}
```

Use this when the API is useful in more than one stage, or when the legal stage
depends on runtime state such as `applied`.

Do not rely on timing alone for a destructive mistake. Recipes fail naturally
because the registry is frozen; a new API may not have that protection.

## Error conventions

| Situation | Mechanism |
|---|---|
| Bad argument type | `checkjstring()`, `checktable()`, `checkint()` |
| Invalid value | `throw new LuaError("fragment: message")` |
| Missing registry entry | `throw new LuaError("unknown item '" + id + "'")` |
| Failure while applying | `logger.error("MoonTweaker: failed to ...: {}", ...)` |
| Failure in a Lua callback | Let `EventApi.emit` catch it |
| Failure in one queued operation | Catch inside the loop, continue |

Guidelines:

- Error messages are lowercase fragments, often with the function name:
  `loot.add(): 'min' must be at least 1`.
- Include the offending value in quotes: `unknown item 'minecraft:nope'`.
- Do not include a stack trace in the Lua error; the log already has one when
  needed.
- Do not add runtime/apply errors to `ScriptManager.errors`. That list is for
  load-time script failures only.
- Use a `rootMessage(Throwable)` helper when logging reflection failures, so the
  target mod's message is visible instead of `InvocationTargetException`.

## Reflection guidelines

Reflection is acceptable only where Forge or the target mod exposes no public
API. Follow the existing pattern:

1. Resolve classes lazily, at apply time, not in a static initializer, unless
   the class is guaranteed to be present.
2. Try the deobfuscated field name first, then the SRG name.
3. Match methods by name and parameter count, then validate the result.
4. Unwrap `InvocationTargetException` and rethrow the target exception.
5. Isolate each operation in its own `try`/`catch`.
6. Log the operation description, not just the exception.

```java
private static Object invokeStatic(String className, String methodName, Object... args) {
    try {
        Class<?> type = Class.forName(className);
        for (java.lang.reflect.Method method : type.getMethods()) {
            if (!method.getName().equals(methodName)
                || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                return method.invoke(null, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                Throwable target = e.getTargetException();
                if (target instanceof RuntimeException) throw (RuntimeException) target;
                if (target instanceof Error) throw (Error) target;
                throw new RuntimeException(target);
            }
        }
        throw new IllegalStateException(
            className + "." + methodName + "/" + args.length + " not found"
        );
    } catch (ReflectiveOperationException e) {
        throw new RuntimeException(e);
    }
}
```

A missing optional mod must never break startup. Do not reference its classes
from a field declaration or static initializer that runs unconditionally.

## Threading

| Work | Thread |
|---|---|
| Stage loading | Mod loading thread |
| Registry events | Mod loading thread |
| Commands | Server thread |
| Gameplay events, ticks, loot | Server thread |
| Advancement apply | Server thread or scheduled onto it |

If an API touches world or player state, it must run on the server thread. If
the caller may be off-thread, schedule with `server.addScheduledTask(task)`.
Lua itself is always executed synchronously; MoonTweaker has no scheduler.

## Documentation checklist

Every new public API needs more than one page:

- [ ] An API reference page under `website/docs/api/`.
- [ ] An entry in `website/sidebars.js`.
- [ ] A row in the globals table in [Globals](/docs/api/globals).
- [ ] Argument and error tables, with exact messages.
- [ ] A stage/timing table.
- [ ] At least one complete Lua example.
- [ ] A row in [Error reference](/docs/reference/errors).
- [ ] A row in [Limitations](/docs/reference/limitations) if it cannot do
      something users will expect.
- [ ] A row in [Logging](/docs/reference/logging) for each new log line.
- [ ] A row in the reset matrix in
      [State, resets and reload](/docs/internals/mod-init/state-and-reload).
- [ ] An internals page if the implementation is non-trivial.
- [ ] A cross-link from [Architecture](/docs/internals/architecture).

The documentation site is built with `onBrokenLinks: 'throw'`, so a link to a
page that does not exist fails the build.

## Testing checklist

1. `./gradlew build` succeeds.
2. `./gradlew runClient` starts with the new API installed.
3. The API works in its intended stage.
4. The API fails with a clear `LuaError` in the wrong stage.
5. `/moontweaker errors` shows the failure.
6. `/moontweaker reload` resets or preserves state as designed.
7. A full restart produces the same behavior.
8. Apply-time failures are logged per operation and do not stop the rest.
9. `./gradlew reobfJar` succeeds and the reobfuscated jar works in a normal
   instance.
10. The documentation builds with `npm run build` in `website/`.

## Common pitfalls

| Pitfall | Consequence |
|---|---|
| Static state without a reset | Old entries survive a reload |
| Reset on `startup` | Breaks the append-only design |
| Assuming `currentStage` is constant | The same class serves `registry`, `server` and `startup`; read it at call time |
| Queuing after `apply` without a guard | Work is silently never applied |
| Applying only at load complete and resetting on reload | Reloaded operations never run |
| Catching `Throwable` and swallowing it | No log, no error, no clue |
| Adding apply errors to `ScriptManager.errors` | `/moontweaker errors` shows stale failures |
| Holding Lua closures in Java state | Old `Globals` cannot be collected; reload semantics break |
| Registering a subscriber after registry events | Misses the event entirely |
| Reflection in a static initializer | Missing optional mod breaks the whole class |
| Touching the world off-thread | Race conditions and crashes |

## What comes next

- [Building](/docs/development/building) for the toolchain and output jars.
- [Architecture](/docs/internals/architecture) for the package map.
- [Mod initialization](/docs/internals/mod-init/) for the lifecycle each API
  must fit into.
