---
id: installation
title: Installation
sidebar_position: 1
description: Install MoonTweaker on a Minecraft 1.12.2 Forge client or dedicated server.
---

# Installation

## Requirements

| Component | Required version |
|---|---|
| Minecraft | 1.12.2 |
| Forge | 14.23.5.2847 or newer |
| Java | 8 |
| MoonTweaker | `MoonTweaker-0.1.0.jar` (release build) |

MoonTweaker declares a Forge dependency on `forgelin`. Even though the mod is
implemented in Java, the `@Mod` annotation still carries
`required-after:forgelin`; install **Forgelin-Continuous** alongside it if your
instance does not already have it.

## Install

1. Install Minecraft Forge 1.12.2.
2. Put `MoonTweaker-0.1.0.jar` in the `mods/` folder.
3. Start the game or server once.
4. MoonTweaker creates the script folders next to `config/`:

```text
.minecraft/                  (or your server directory)
  mods/
    MoonTweaker-0.1.0.jar
    Forgelin-Continuous-<version>.jar
  config/
  lua_scripts/
    registry/
    server/
    startup/
```

5. Add `.lua` files to the appropriate stage folder.
6. Restart, or run `/moontweaker reload` for `server/` changes.

:::warning Use the reobfuscated jar
When building from source, `./gradlew jar` produces a **development** jar named
`MoonTweaker-0.1.0-dev.jar`. That jar uses MCP names and must not be installed
in a normal launcher. Run `./gradlew reobfJar` (or `./gradlew build`) and use
`build/libs/MoonTweaker-0.1.0.jar`, which is reobfuscated to SRG names.
:::

## Where the script folder lives

The script root is the **parent of the Forge config directory**, not the config
directory itself:

```java
File configDir = Loader.instance().getConfigDir();
File gameDir = configDir.getParentFile();
File scripts = new File(gameDir, "lua_scripts");
```

Consequences:

| Environment | Script root |
|---|---|
| Normal client | `.minecraft/lua_scripts/` |
| Dedicated server | `<server>/lua_scripts/` |
| `./gradlew runClient` / `runServer` | `run/lua_scripts/` |
| Launcher instance | `<instance>/minecraft/lua_scripts/` |

If the folder is missing, MoonTweaker creates it on the first stage load.

## Client or server?

| Feature | Needs the mod on the client? |
|---|---|
| Recipe changes | No |
| Loot changes | No |
| Gameplay events | No |
| Custom advancements | No; the server syncs them to vanilla clients |
| Registered items, blocks, tools, armor, fluids | **Yes**, plus a resource pack for models and textures |

Registered content becomes part of the Forge registry. A client without the mod
cannot resolve those IDs. Pure recipe, loot, event and advancement scripts work
on a vanilla client.

## Verify the install

After starting the game, search the log for:

```text
MoonTweaker: stage 'registry' loaded, N scripts, 0 errors
MoonTweaker: stage 'server' loaded, N scripts, 0 errors
MoonTweaker: stage 'startup' loaded, N scripts, 0 errors
```

Then run:

```text
/moontweaker errors
```

It should answer `No script errors`. If the command does not exist, the jar did
not load; check the Forge mod list and the log for a missing `forgelin`
dependency.

## Updating

1. Stop the game or server.
2. Replace the jar in `mods/`.
3. Start again.

Scripts and world data are not touched by an update. Advancement tombstones
live in `<world>/data/advancements/` and are managed by MoonTweaker; do not
delete them by hand while the pack still hides those advancements.

## Uninstalling

Remove the jar. Scripts and generated advancement files remain on disk. If you
want a clean removal:

1. Delete `lua_scripts/`.
2. Delete `data/advancements/` inside each world **only** if you no longer need
   the generated advancements; the folder may also contain files from other
   mods or data packs.
3. Remove `mods/MoonTweaker-*.jar`.

## Compatibility notes

- Thermal Expansion, Ender IO and Immersive Engineering are optional. Their
  recipe managers are resolved by reflection, so a different major version may
  work or may log a failure for individual recipes.
- MoonTweaker does not add a permission node. `/moontweaker` is available to
  every player; restrict it with a permissions plugin on public servers.
- Only one MoonTweaker jar is needed. Older LunarLoot, MoonRegistry and
  MoonExpansion jars are obsolete.
