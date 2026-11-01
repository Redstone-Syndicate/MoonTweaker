---
id: building
title: Building from Source
sidebar_position: 1
description: Toolchain requirements, Gradle tasks, output jars and development runs for MoonTweaker.
---

# Building from source

MoonTweaker builds with **RetroFuturaGradle 2.0.2** on **Gradle 9.2**. Gradle
must run on **JDK 25**; the Java 8 compilation toolchain is provisioned
automatically by the Foojay resolver.

## Quick path

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
./gradlew build
```

The installable jar is:

```text
build/libs/MoonTweaker-0.1.0.jar
```

Do **not** install `MoonTweaker-0.1.0-dev.jar`. It contains MCP names and is only
for development runs.

## Requirements

| Requirement | Version | Notes |
|---|---|---|
| JDK for Gradle | 25 | Gradle 9.2 requires it |
| Java toolchain | 8 | Downloaded automatically by Foojay |
| Gradle | 9.2 | Provided by the wrapper |
| Internet | First build only | Minecraft, Forge, mappings and LuaJ |
| Disk space | Several GB | Decompiled sources and caches |

The mod itself targets Java 8 at runtime. Players do not need JDK 25; that is a
build-time requirement only.

### Setting `JAVA_HOME`

Linux:

```bash
export JAVA_HOME=/usr/lib/jvm/java-25-openjdk
```

macOS:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 25)
```

Windows PowerShell:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
```

Verify:

```bash
java -version
./gradlew --version
```

`./gradlew --version` must report a JVM 25 runtime. If it reports 8, 17 or 21,
Gradle is using the wrong JDK even if `java -version` looks correct.

## Build tasks

| Command | Result |
|---|---|
| `./gradlew build` | Full build; produces both jars |
| `./gradlew jar` | Development jar only |
| `./gradlew reobfJar` | Reobfuscated, installable jar |
| `./gradlew clean` | Deletes `build/` |
| `./gradlew runClient` | Launches a development client |
| `./gradlew runServer --args nogui` | Launches a development server |
| `./gradlew --refresh-dependencies build` | Re-resolves dependencies |

`build` also runs `check`. There are no unit tests in the project, so the check
phase is quick; most of the time is spent on decompilation and reobfuscation.

### Output jars

```text
build/libs/
  MoonTweaker-0.1.0-dev.jar   development, MCP names
  MoonTweaker-0.1.0.jar       reobfuscated, installable
```

| Jar | Mappings | Use |
|---|---|---|
| `-dev.jar` | MCP | `runClient` / `runServer` only |
| `MoonTweaker-0.1.0.jar` | SRG | Normal Minecraft/Forge installation |

Installing the dev jar in a production instance produces missing-method or
missing-field errors, because Minecraft classes are obfuscated at runtime.

The file name follows the `version` in `build.gradle`:

```groovy
group = 'com.moontweaker'
version = '0.1.0'
```

## What the build does

1. Downloads the Gradle 9.2 distribution through the wrapper.
2. Resolves RetroFuturaGradle 2.0.2 from the GTNH Maven repository.
3. Downloads Minecraft 1.12.2 and Forge, decompiles and patches them.
4. Applies MCP mappings for the development jar.
5. Compiles the mod with the Java 8 toolchain.
6. Embeds LuaJ 3.0.1:

```groovy
configurations {
    embed
}

dependencies {
    implementation 'org.luaj:luaj-jse:3.0.1'
    embed 'org.luaj:luaj-jse:3.0.1'
}

jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from {
        configurations.embed.collect { it.isDirectory() ? it : zipTree(it) }
    }
    exclude 'META-INF/*.SF', 'META-INF/*.DSA', 'META-INF/*.RSA', 'META-INF/MANIFEST.MF'
    from('LICENSE')
    from('NOTICE')
}
```

7. Reobfuscates the compiled classes to SRG names with `reobfJar`.

LuaJ is bundled, so players do not need a separate LuaJ mod. `LICENSE` and
`NOTICE` are embedded in the jar to satisfy the GPL distribution terms.

The first build can take several minutes and downloads hundreds of megabytes.
Later builds reuse the Gradle and RFG caches.

## Development runs

```bash
./gradlew runClient
./gradlew runServer --args nogui
```

The run directory is `run/`. MoonTweaker creates:

```text
run/
  config/
  lua_scripts/
    registry/
    server/
    startup/
  logs/
  mods/
```

Put test scripts in `run/lua_scripts/`. The script root is the parent of
`config/`, not `config/` itself.

### First server run

Accept the EULA:

```text
run/eula.txt
```

Set:

```properties
eula=true
```

Then start the server again.

### Forgelin

The `@Mod` annotation declares:

```java
dependencies = "required-after:forgelin"
```

Even though MoonTweaker is written in Java, Forge refuses to load it without
Forgelin. For development runs, put a Forgelin-Continuous jar in `run/mods/` or
add it as a runtime dependency.

### Reloading during development

```text
/moontweaker reload
/reload
```

Remember the limits:

- recipes and registry content need a full restart;
- machine recipes need a full restart;
- loot needs `/moontweaker reload` followed by `/reload`;
- advancements apply on `/moontweaker reload`.

See [Reload](/docs/internals/script-engine/reload).

## Project layout

```text
MoonTweaker/
  build.gradle                 Gradle, RFG and Java configuration
  settings.gradle              Plugin repositories and Foojay resolver
  gradle.properties            Gradle JVM arguments and caching
  gradle/wrapper/              Gradle 9.2 wrapper
  src/main/java/               Mod source
  src/main/resources/          mcmod.info, logo, lang
  lua_scripts/                 Example scripts (if present)
  website/                     This Docusaurus site
  LICENSE
  NOTICE
  README.md
  DOCS.md
```

## Version bumps

The version appears in three places and must be kept in sync:

| File | Field |
|---|---|
| `build.gradle` | `version = '0.1.0'` |
| `src/main/java/com/moontweaker/MoonTweaker.java` | `VERSION = "0.1.0"` |
| `src/main/resources/mcmod.info` | `"version": "0.1.0"` |

The jar name comes from `build.gradle`; the in-game mod list reads
`mcmod.info` and the `@Mod` annotation. A mismatch produces a jar whose file
name, mod list entry and reported version disagree.

Also update version references in `README.md`, `DOCS.md` and this site when
publishing a release.

## Verifying the jar

```bash
unzip -l build/libs/MoonTweaker-0.1.0.jar | grep -E \
  "mcmod.info|logo.png|LICENSE|NOTICE|org/luaj"
```

Expected entries include:

```text
mcmod.info
logo.png
LICENSE
NOTICE
org/luaj/vm2/...
com/moontweaker/...
```

Check the metadata:

```bash
unzip -p build/libs/MoonTweaker-0.1.0.jar mcmod.info
```

The `version` must match the jar name.

## Installing a build

1. Stop Minecraft or the server.
2. Remove any older MoonTweaker jar from `mods/`.
3. Copy `build/libs/MoonTweaker-0.1.0.jar` into `mods/`.
4. Make sure Forgelin-Continuous is installed.
5. Start the game/server.

Common locations:

```text
.minecraft/mods/
<server>/mods/
<instance>/minecraft/mods/
```

A stale jar is the most common reason a script change appears to do nothing.
Compare the file timestamp before debugging the script.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Unsupported class file major version` | Gradle is running on an old JDK | Set `JAVA_HOME` to JDK 25 |
| `No matching toolchains found ... languageVersion=8` | Foojay could not download JDK 8 | Check network/proxy, or install JDK 8 |
| Dependency resolution fails | GTNH/Cleanroom Maven unreachable | Check network and `settings.gradle` repositories |
| Build runs out of memory | Decompilation is memory-hungry | Raise `org.gradle.jvmargs` in `gradle.properties` |
| `Task 'reobfJar' not found` | Wrong project or broken plugin resolution | Use the wrapper from the project root |
| `runClient` crashes on missing `forgelin` | Forgelin not in `run/mods/` | Add Forgelin-Continuous |
| `runServer` exits immediately | EULA not accepted | Set `eula=true` in `run/eula.txt` |
| Game shows old behavior | Dev jar installed, or stale jar | Install the reobfuscated jar and restart |
| Jar name has no `-dev` suffix but still crashes | Built with a different mapping setup | Run `./gradlew clean reobfJar` |
| First build is very slow | Minecraft/Forge decompilation | Wait; later builds are cached |

### Clean rebuild

```bash
./gradlew clean
./gradlew build
```

Use this after changing mappings, Gradle plugins or the RFG configuration. It is
not needed for ordinary source edits.

### Gradle memory

`gradle.properties` currently sets:

```properties
org.gradle.jvmargs=-Xmx3G
org.gradle.caching=true
org.gradle.welcome=never
```

If the decompile step fails with `OutOfMemoryError`, raise `-Xmx3G` to `-Xmx4G`
or more, depending on available RAM.

## What comes next

[Extending](/docs/development/extending) walks through adding a new Lua API and
wiring it into the lifecycle.
