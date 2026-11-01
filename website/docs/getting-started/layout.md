---
id: layout
title: Files and Layout
sidebar_position: 3
description: Installed layout, repository layout, stage folders, load order and generated world data.
---

# Files and layout

## Installed layout

```text
<game directory>/
  mods/
    MoonTweaker-0.1.0.jar
  config/
  lua_scripts/
    registry/
      content.lua
    server/
      main.lua
      recipes/
        tools.lua
      quests/
        chapter1.lua
    startup/
      base.lua
  saves/
    <world>/
      data/
        advancements/
          .moontweaker
          .moontweaker-vanilla
          mypack/
            root.json
          minecraft/
            story/root.json
```

The script root is the parent of `config/`. See
[Installation](/docs/getting-started/installation#where-the-script-folder-lives).

## Repository layout

```text
build.gradle                          Gradle / RFG / Java build
settings.gradle                       Plugin repositories
assets/icon.svg                       Icon source
src/main/resources/logo.png           Mod icon
src/main/resources/mcmod.info         Forge metadata

src/main/java/com/moontweaker/
  MoonTweaker.java                    @Mod entry point and event wiring
  api/MoonTweakerApi.java             Current script stage
  script/ScriptManager.java           Discovery, Globals, require, errors, reload
  lua/RecipeApi.java                  item, ore, recipes.*
  loot/LootApi.java                   loot.* queue and table mutation
  loot/LootTableHandler.java          LootTableLoadEvent hook
  registry/RegistryApi.java           registry.* bindings and pending objects
  registry/RegistryHandler.java       Block/Item registry events
  expansion/ExpansionApi.java         moonexpansion.* and reflection
  event/EventApi.java                 Listener registry and dispatch
  event/GameplayEvents.java           Forge event subscribers
  event/RecipeRegistryHook.java       Loads server/ at LOWEST priority
  advancement/AdvancementApi.java     Lua advancement definitions and tombstones
  command/MoonTweakerCommand.java     /moontweaker command

lua_scripts/
  registry/                           Content registration
  startup/                            Late startup scripts
  server/                             Recipe, loot, event, advancement scripts

website/                              Docusaurus documentation site
  docs/                               Markdown sources
  sidebars.js                         Sidebar structure
  docusaurus.config.js                Site configuration
```

## Stage folders

| Folder | Forge moment | Reloadable? | Typical use |
|---|---|---|---|
| `registry/` | `FMLPreInitializationEvent` | No, restart | Items, blocks, tools, armor, fluids |
| `server/` | `RegistryEvent.Register<IRecipe>` at `LOWEST` | Loot/events/advancements; recipes need restart | Recipes, loot, events, advancements |
| `startup/` | `FMLPostInitializationEvent` | No, restart | Base definitions, machine recipes, loot, advancements |

The full ordering and the reason for each choice is in
[Mod initialization](/docs/internals/mod-init/).

## File discovery

- Only files ending in `.lua` are executed.
- Discovery is recursive; nested folders are allowed.
- Files are sorted lexicographically by their **full path**.
- Each stage gets a fresh Lua `Globals` environment.
- Modules loaded with `require` are cached per stage load.
- A file error does not stop the remaining files.

Example order:

```text
server/a.lua
server/config/b.lua
server/z.lua
```

`a.lua` runs first, then `config/b.lua`, then `z.lua`.

## Modules

`require("config.balance")` resolves to
`lua_scripts/<current-stage>/config/balance.lua`:

```lua
-- server/config/balance.lua
return { diamondWeight = 10 }

-- server/main.lua
local balance = require("config.balance")
```

The resolved path must stay inside the stage folder. `require("../registry/x")`
is rejected.

## Generated files

MoonTweaker writes only inside the world:

| Path | Purpose |
|---|---|
| `<world>/data/advancements/<namespace>/<path>.json` | Custom advancement definitions |
| `<world>/data/advancements/.moontweaker` | Manifest of generated custom advancement ids |
| `<world>/data/advancements/.moontweaker-vanilla` | Manifest of tombstoned advancement ids |

Files are written with pretty-printed JSON, validated through vanilla's
`AdvancementManager.GSON` before they are queued. A file whose content did not
change is not rewritten.

Do not edit generated files by hand. Change the Lua definition instead; the
next apply will overwrite or delete them according to the manifests.

## Logs

All mod output uses the `moontweaker` logger:

```text
[Server thread/INFO] [moontweaker]: MoonTweaker: stage 'server' loaded, 4 scripts, 0 errors
[Server thread/INFO] [moontweaker]: [lua] hello from MoonTweaker
```

See [Logging and diagnostics](/docs/reference/logging) for the message
catalogue.
