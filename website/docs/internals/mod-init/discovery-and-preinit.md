---
id: discovery-and-preinit
title: Discovery and Pre-initialization
sidebar_position: 2
description: How Forge discovers MoonTweaker, constructs the @Mod class, runs FMLPreInitializationEvent and loads the registry stage.
---

# Discovery and pre-initialization

This page covers everything from the jar sitting in `mods/` to the end of
`FMLPreInitializationEvent`.

## 1. Forge discovers the jar

Forge scans `mods/` during early loading and reads mod metadata. MoonTweaker
provides two things:

- `src/main/resources/mcmod.info` — human-readable metadata for the mod list.
- `@Mod` on `com.moontweaker.MoonTweaker` — the machine-readable declaration
  used by FML.

```java
@Mod(
    modid = MoonTweaker.MODID,
    name = MoonTweaker.NAME,
    version = MoonTweaker.VERSION,
    dependencies = "required-after:forgelin"
)
public class MoonTweaker {
    public static final String MODID = "moontweaker";
    public static final String NAME = "MoonTweaker";
    public static final String VERSION = "0.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final ScriptManager SCRIPTS = new ScriptManager(LOGGER);
    ...
}
```

### The `forgelin` dependency

The `dependencies` string is a hard FML dependency:

```text
required-after:forgelin
```

Even though MoonTweaker is now implemented in Java, the annotation still
declares this dependency. FML will refuse to load MoonTweaker if Forgelin is
absent. Install **Forgelin-Continuous** with the mod unless you are building a
fork that removes the dependency.

### Class loading and static initialization

When FML constructs the mod object, the JVM initializes the class and runs the
static field initializers **once**:

1. `MODID`, `NAME`, `VERSION` string constants.
2. `LOGGER = LogManager.getLogger("moontweaker")`.
3. `SCRIPTS = new ScriptManager(LOGGER)`.

`ScriptManager`'s constructor only stores the logger. No files are read and no
Lua environment exists yet. The script directory is resolved lazily by
`scriptDir()`:

```java
File configDir = Loader.instance().getConfigDir();
File gameDir = configDir != null ? configDir.getParentFile() : new File(".");
return new File(gameDir, "lua_scripts");
```

This is why the script root is the **parent** of `config/`, not `config/`
itself. If `Loader.getConfigDir()` is unavailable, the mod falls back to the
current working directory.

## 2. `FMLPreInitializationEvent`

FML dispatches pre-initialization after all mods have been constructed but
before the block/item registry events. MoonTweaker's handler does two things:

```java
@Mod.EventHandler
public void preInit(FMLPreInitializationEvent event) {
    SCRIPTS.loadRegistry();
    MinecraftForge.EVENT_BUS.register(new RegistryHandler(LOGGER));
    MinecraftForge.EVENT_BUS.register(new LootTableHandler(LOGGER));
    MinecraftForge.EVENT_BUS.register(new RecipeRegistryHook(SCRIPTS));
    MinecraftForge.EVENT_BUS.register(new GameplayEvents());
}
```

Order matters conceptually:

1. **Load `registry/` first.** Scripts queue objects into static lists. They do
   not need the handlers yet.
2. **Register handlers afterwards.** The registry events have not fired yet;
   pre-init is still running. The handlers will be present when Forge fires
   them.

### What `loadRegistry()` does

`ScriptManager.load("registry")` performs the generic stage sequence:

1. Clear the `require` module cache.
2. Set `MoonTweakerApi.currentStage = "registry"`.
3. Call `RegistryApi.reset()` (namespace back to `moontweaker`, pending lists
   cleared).
4. Create the stage directory if it does not exist.
5. Build a fresh Lua `Globals`.
6. Recursively run every `.lua` file in sorted path order.
7. Log `stage 'registry' loaded, N scripts, M errors`.

Because the stage is `registry`, `RegistryApi.install(g)` installs the **real**
registry table. In every other stage it installs a stub whose functions all
throw:

```text
registry API is only available in lua_scripts/registry/
```

## 3. What registry scripts can do

```lua
-- lua_scripts/registry/mekalite_content.lua
registry.namespace("mekalite")

registry.item("mekalite_core", { max_stack = 64 })

registry.block("mekalite_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})

registry.tool("pickaxe", "mekalite", {
  level = 3,
  durability = 1200,
  efficiency = 8.0,
  damage = 2.0,
  enchantability = 14,
})

registry.armor("mekalite", {
  durability = 25,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
})

registry.fluid("mekalite_slurry", {
  density = 1200,
  viscosity = 1500,
  color = 0x66CCFF,
})
```

At this point **nothing has been registered with Forge yet**, with one
exception: fluids. `registry.fluid` calls `FluidRegistry.registerFluid(fluid)`
immediately, because the fluid registry is open during pre-init. The fluid's
world block and bucket are queued like other blocks and items.

### Objects queued by the registry stage

| Lua call | Queued objects |
|---|---|
| `registry.item` | One `Item` |
| `registry.material` | One `Item` with a suffix (`_ingot`, `_dust`, `_gem`) |
| `registry.block` | One `Block` + one `ItemBlock` |
| `registry.tool` | One tool `Item`; tool material created immediately with `EnumHelper` |
| `registry.armor` | Four `ItemArmor` pieces; armor material created immediately |
| `registry.fluid` | Fluid registered immediately; `BlockFluidClassic` + `ItemBucket` queued |

Tool and armor materials are created with `EnumHelper.addToolMaterial` /
`addArmorMaterial` while the script runs. This mutates Forge's enum-like
material lists during pre-init, which is the intended window.

## 4. Failure behaviour

- A Lua error in one registry file is recorded and the remaining files still
  run.
- Objects queued before the failing line remain queued. For example, an item
  registered on line 3 stays queued if line 5 throws.
- A duplicate ID inside one load raises `duplicate id '<namespace:path>'` and
  aborts that file. Earlier objects in the same file stay queued.
- `RegistryApi.reset()` runs only when the registry stage loads. There is no
  reload for this stage; a restart is required to change content.

## 5. Why this stage is not reloadable

By the time `/moontweaker reload` is available, Forge has frozen the block and
item registries. Registering a new block or item then throws
`Attempted to register to late` (Forge's message). MoonTweaker does not attempt
it. The `registry` stage is therefore loaded exactly once per game start, and
`/moontweaker reload` only re-runs `server/`.

## 6. What comes next

After every mod's `preInit` handler returns, FML fires the block and item
registry events. That is the subject of
[Block and item registry events](/docs/internals/mod-init/registry-events).
