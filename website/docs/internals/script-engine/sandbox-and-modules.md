---
id: sandbox-and-modules
title: Sandbox and Modules
sidebar_position: 2
description: Exactly which Lua libraries are available, what was removed, how require resolves modules and what the sandbox does not protect against.
---

# Sandbox and modules

MoonTweaker runs Lua with a reduced standard library. The goal is to prevent
accidental filesystem, process and reflection access, not to run untrusted
code safely.

## Library setup

```java
Globals g = new Globals();
g.load(new BaseLib());
g.load(new PackageLib());
g.load(new Bit32Lib());
g.load(new TableLib());
g.load(new StringLib());
g.load(new CoroutineLib());
g.load(new JseMathLib());
LoadState.install(g);
LuaC.install(g);
for (String name : Arrays.asList(
        "luajava", "io", "package", "debug", "dofile", "loadfile", "load")) {
    g.set(name, LuaValue.NIL);
}
g.set("os", safeOs());
g.set("print", ...);
g.set("require", ...);
g.set("log", logTable());
g.set("ore", ...);
RecipeApi.install(g, logger, this);
EventApi.install(g, logger);
LootApi.install(g);
RegistryApi.install(g);
ExpansionApi.install(g);
AdvancementApi.install(g);
```

The order matters:

1. Libraries are loaded.
2. Dangerous or unnecessary globals are replaced with `nil`.
3. MoonTweaker installs its own `os`, `print`, `require`, `log` and `ore`.
4. API tables are installed last.

## Available

### Base library

The normal Lua base functions are present:

```lua
assert, collectgarbage, error, getmetatable, ipairs, next, pairs,
pcall, rawequal, rawget, rawlen, rawset, select, setmetatable,
tonumber, tostring, type, xpcall, _G, _VERSION
```

`print` is replaced. `require`, `load`, `loadfile` and `dofile` are handled
separately below.

### `string`, `table`, `math`, `bit32`, `coroutine`

Full LuaJ implementations:

```lua
string.format("%s = %d", "x", 42)
table.sort(list, function(a, b) return a.weight < b.weight end)
math.floor(3.7)
bit32.band(0xFF, 0x0F)
coroutine.create(function() coroutine.yield(1) end)
```

`math` is `JseMathLib`, so it is backed by `java.lang.Math` and includes the
usual trigonometric, logarithmic and random functions.

### `os` — restricted

```java
private LuaValue safeOs() {
    LuaValue safe = LuaValue.tableOf();
    safe.set("time", ...);      // System.currentTimeMillis() / 1000.0
    safe.set("clock", ...);     // System.nanoTime() / 1e9
    safe.set("difftime", ...);  // a - b
    return safe;
}
```

| Function | Behaviour |
|---|---|
| `os.time()` | Unix time in seconds as a floating-point number. Arguments are ignored. |
| `os.clock()` | Monotonic process clock in seconds. |
| `os.difftime(a, b)` | Returns `a - b`. |

Everything else is absent: `os.date`, `os.getenv`, `os.execute`, `os.exit`,
`os.remove`, `os.rename`, `os.tmpname`.

`os.time()` returns a double, not an integer. Lua 5.2 normally returns an
integer; do not rely on integer division without `math.floor`.

### `print` and `log`

```lua
print("message")
log.info("message")
log.warn("message")
log.error("message")
```

All four take exactly **one** argument and write through the `moontweaker`
logger:

```text
[Server thread/INFO] [moontweaker]: [lua] message
```

Extra arguments are ignored. Concatenate explicitly:

```lua
log.info("x=" .. x .. " y=" .. y)
```

`print` and `log.info` are equivalent. `log.warn` and `log.error` use the
corresponding log levels. None of these are recorded in
`/moontweaker errors`.

## Removed

| Global | Why |
|---|---|
| `io` | No filesystem or console access |
| `luajava` | No direct JVM class access or reflection |
| `package` | No module path manipulation or native library loading |
| `debug` | No stack introspection or upvalue mutation |
| `dofile` | No arbitrary file execution |
| `loadfile` | No arbitrary file compilation |
| `load` | No runtime code generation from strings |

`_G` still exists and points at the globals table. The removed names are simply
`nil`:

```lua
print(tostring(io))        -- nil
print(tostring(luajava))   -- nil
print(tostring(debug))     -- nil
```

`LoadState` and `LuaC` remain installed internally so that
`globals.load(source, chunkName)` works. That does not give Lua a `load`
function.

### What this blocks

- Reading or writing files.
- Executing shell commands.
- Reflecting into Java classes.
- Loading native libraries.
- Inspecting or mutating function upvalues through `debug`.
- Compiling code from strings at runtime.

### What this does not block

- Infinite loops, huge allocations, deep recursion and stack overflows.
- Expensive string concatenation in `server.tick`.
- Throwing Java exceptions through the API.
- Reading game state through the exposed APIs.
- Coroutines that are never resumed.

There is **no instruction budget, timeout or watchdog**. A script is trusted
code. `while true do end` freezes the server thread.

## `require`

`require` is MoonTweaker's own loader, not Lua's `package.searchers`:

```java
private LuaValue requireModule(Globals g, String name) {
    LuaValue cached = modules.get(name);
    if (cached != null) return cached;

    String rel = name.replace('.', '/') + ".lua";
    File root = new File(scriptDir(), MoonTweakerApi.currentStage);
    root = root.getCanonicalFile();

    File file = new File(root, rel).getCanonicalFile();
    if (!file.toPath().startsWith(root.toPath())) {
        throw new LuaError("invalid module: " + name);
    }
    if (!file.isFile()) throw new LuaError("module not found: " + name);

    LuaValue result = g.load(readFile(file), "@" + rel).call();
    LuaValue module = result.isnil() ? LuaValue.TRUE : result;
    modules.put(name, module);
    return module;
}
```

### Resolution rules

- Dots become slashes: `config.balance` → `config/balance.lua`.
- `.lua` is always appended; do not include it in the name.
- Resolution is relative to the **current stage folder**, not the requiring
  file's folder.
- Canonicalization resolves `..`, `.` and symlinks.
- The resolved path must start with the canonical stage root.

```lua
-- server/main.lua
local balance = require("config.balance")   -- server/config/balance.lua
local tools   = require("recipes.tools")    -- server/recipes/tools.lua
```

### Caching

- Cache key is the **raw name string**.
- Cache is cleared at the start of every stage load.
- Cache is per stage; modules are not shared between `registry`, `server` and
  `startup`.
- `require("a.b")` and `require("a/b")` resolve to the same file but use
  different cache keys, so the file runs twice.
- The cache is populated **after** the module returns. A circular require
  recurses until the stack overflows.
- A module that throws is not cached. A later `require` retries it.

### Return values

```lua
-- server/config/balance.lua
return { diamondWeight = 10 }
```

- A returned value is cached and returned.
- `return nil` or no `return` caches `true`.
- `return false` caches and returns `false`.

### Errors

| Condition | Error |
|---|---|
| Path escapes the stage root | `invalid module: <name>` |
| Canonicalization fails | `invalid module: <name>` |
| File does not exist | `module not found: <name>` |
| Stage root cannot be canonicalized | `invalid module root` |
| Module throws | The module's own error, propagated |

```lua
require("../registry/content")   -- invalid module: ../registry/content
require("missing.module")        -- module not found: missing.module
```

## Script root

```java
public File scriptDir() {
    File configDir = Loader.instance().getConfigDir();
    File gameDir = configDir != null ? configDir.getParentFile() : new File(".");
    return new File(gameDir, "lua_scripts");
}
```

The root is resolved lazily, on the first stage load. It is the parent of the
Forge config directory. See
[Installation](/docs/getting-started/installation#where-the-script-folder-lives).

## Coroutines

`coroutine` is fully available, but MoonTweaker does not schedule or resume
coroutines. A coroutine runs only while Lua code is already executing:

```lua
local co = coroutine.create(function()
  coroutine.yield(1)
  return 2
end)

local ok, value = coroutine.resume(co)   -- true, 1
local ok2, value2 = coroutine.resume(co) -- true, 2
```

You can store a coroutine in a Lua variable or table and resume it from later
event callbacks, but:
- there is no timer to resume it automatically;
- the coroutine belongs to one stage's `Globals`;
- after `/moontweaker reload`, listeners are cleared and the old globals may be
  collected, so stored coroutines are effectively lost.

## Practical sandbox rules

1. Do not use Lua to read files. Put data in Lua source or in a module.
2. Do not attempt Java reflection. Use the provided API tables.
3. Do not run unbounded loops or recursion.
4. Keep `server.tick` callbacks tiny.
5. Treat every script as code with full server-thread authority.

## What comes next

[Reload](/docs/internals/script-engine/reload) explains what happens to the
sandbox, module cache and closures when `/moontweaker reload` runs.
