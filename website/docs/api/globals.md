---
id: globals
title: Globals
sidebar_position: 2
description: item, ore, log, print, require, os and the global API tables.
---

# Globals

These names are available in every stage's Lua environment.

## Global API tables

| Name | Type | Available in |
|---|---|---|
| `recipes` | table | All stages; only effective in `server/` |
| `loot` | table | All stages; use `server/` or `startup/` |
| `registry` | table | Real only in `registry/`; restricted elsewhere |
| `moonexpansion` | table | `server/` and `startup/` |
| `events` | table | `server/` and `startup/` |
| `advancement` | table | `server/` and `startup/` |
| `log` | table | All stages |
| `os` | table | All stages (restricted) |
| `item` | function | All stages |
| `ore` | function | All stages |
| `require` | function | All stages |
| `print` | function | All stages |

## `item(id)`

```lua
item("minecraft:diamond")
item("minecraft:wool:14")
```

| Parameter | Type | Description |
|---|---|---|
| `id` | string | Item ID, optionally with metadata |

Returns a stack table:

```lua
{ id = "minecraft:diamond", count = 1 }
{ id = "minecraft:wool:14", count = 1, meta = 14 }
```

Behaviour:

- Validates the item against `ForgeRegistries.ITEMS`.
- Always sets `count = 1`.
- Adds `meta` only when the ID has a metadata suffix.
- Does not validate metadata values.
- A non-numeric metadata suffix is silently treated as `0`.

Errors:

| Condition | Error |
|---|---|
| Fewer than two colon parts | `bad item id '<id>'` |
| Unknown item | `unknown item '<id>'` |
| Non-string argument | LuaJ type error from `checkjstring` |

```lua
local stack = item("minecraft:diamond")
log.info(stack.id .. " x" .. stack.count)
```

## `ore(name)`

```lua
ore("ingotIron")   -- "ore:ingotIron"
```

| Parameter | Type | Description |
|---|---|---|
| `name` | string | Ore Dictionary name |

Returns the string `"ore:" .. name`. It does not validate that the ore exists.

```lua
recipes.shapeless("minecraft:torch", { ore("coal"), "minecraft:stick" })
```

## `log`

```lua
log.info("message")
log.warn("message")
log.error("message")
```

| Function | Log level |
|---|---|
| `log.info` | INFO |
| `log.warn` | WARN |
| `log.error` | ERROR |

Each takes exactly one argument. Extra arguments are ignored. Output is
prefixed with `[lua]`:

```text
[Server thread/INFO] [moontweaker]: [lua] message
```

`log.*` is not recorded in `/moontweaker errors`.

## `print(message)`

Equivalent to `log.info`:

```lua
print("hello")
```

Takes one argument. Extra arguments are ignored. Concatenate explicitly:

```lua
print("x=" .. x .. " y=" .. y)
```

## `require(name)`

Loads a Lua module from the current stage folder.

```lua
local balance = require("config.balance")
```

| Parameter | Type | Description |
|---|---|---|
| `name` | string | Dot-separated module path without `.lua` |

Resolution:

- Dots become `/`.
- `.lua` is appended.
- The file is resolved relative to the **stage root**.
- Path traversal outside the stage root is rejected.
- The module is cached per stage load.
- `return nil` caches `true`.
- Errors inside the module propagate to the caller.

See [Sandbox and modules](/docs/internals/script-engine/sandbox-and-modules#require)
for the full algorithm.

## `os`

Restricted clock functions:

| Function | Returns |
|---|---|
| `os.time()` | Unix time in seconds (double) |
| `os.clock()` | Monotonic process clock in seconds |
| `os.difftime(a, b)` | `a - b` |

Everything else is absent. See
[Sandbox](/docs/internals/script-engine/sandbox-and-modules#os--restricted).

```lua
local start = os.clock()
-- ...
log.info("elapsed " .. (os.clock() - start))
```

## Base library

The standard Lua base functions are available:

```lua
assert, collectgarbage, error, getmetatable, ipairs, next, pairs,
pcall, rawequal, rawget, rawlen, rawset, select, setmetatable,
tonumber, tostring, type, xpcall, _G, _VERSION
```

Removed:

```lua
io, luajava, package, debug, dofile, loadfile, load
```

## Examples

### Validate an item before using it

```lua
local ok, stack = pcall(item, "minecraft:not_a_real_item")
if not ok then
  log.warn("missing item: " .. tostring(stack))
end
```

`pcall` catches the Lua error raised by `item`.

### Log a table

```lua
local function dump(t)
  for k, v in pairs(t) do
    log.info(tostring(k) .. " = " .. tostring(v))
  end
end

dump({ a = 1, b = "two" })
```

### Time a section

```lua
local start = os.clock()
for i = 1, 10000 do
  local _ = i * i
end
log.info("loop took " .. string.format("%.4f", os.clock() - start) .. "s")
```

### Require a config module

```lua
-- server/config/balance.lua
return {
  diamondWeight = 10,
  enableBonusLoot = true,
}
```

```lua
-- server/main.lua
local balance = require("config.balance")
log.info("weight=" .. balance.diamondWeight)
```

## What comes next

[Recipes](/docs/api/recipes) is the largest API reference page.
