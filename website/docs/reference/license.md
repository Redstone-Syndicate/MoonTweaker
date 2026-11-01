---
id: license
title: License and Attribution
sidebar_position: 5
description: GPL-3.0-or-later, the section 7(b) attribution term, distribution rules and credits.
---

# License and attribution

MoonTweaker is free software under the **GNU General Public License, version 3
or later** (`GPL-3.0-or-later`).

| Item | Value |
|---|---|
| Copyright holder | Kartendsy |
| Copyright year | 2026 |
| License | GPL-3.0-or-later |
| Full text | [`LICENSE`](https://github.com/Kartendsy/MoonTweaker/blob/main/LICENSE) |
| Notice | [`NOTICE`](https://github.com/Kartendsy/MoonTweaker/blob/main/NOTICE) |

Both files are embedded in the built mod jar by `build.gradle`:

```groovy
from('LICENSE')
from('NOTICE')
```

## What the license allows

Under the GPL you may:

- use MoonTweaker for any purpose, including on a commercial server;
- read, modify and redistribute the source code;
- distribute modified versions;
- include the mod in a modpack;
- charge for distribution, as long as the license terms are met.

## What the license requires

If you distribute MoonTweaker or a modified version, you must:

1. **Keep the license.** Include the full GPL text and the `NOTICE` file.
2. **Provide the source.** Make the corresponding source available under the
   GPL, including your modifications.
3. **Keep the same license.** You cannot relicense derivative versions under
   more restrictive terms.
4. **Mark changes.** Modified versions must be marked as modified, as required
   by section 5(a).
5. **Preserve the attribution.** See the additional term below.
6. **Do not add restrictions.** You cannot impose terms that prevent others from
   exercising the GPL rights.

There is **no warranty**. The software is provided "as is", without any express
or implied warranty, including fitness for a particular purpose.

## The section 7(b) attribution term

The `NOTICE` file adds one term under GPL v3 section 7(b). Every copy and every
modified version must preserve this attribution:

```text
MoonTweaker by Kartendsy
```

It must appear:

- in the mod metadata (`mcmod.info`);
- in the README;
- in any user-visible credits.

You may not remove, hide or replace it. Modified versions must also be marked as
modified in a reasonable way, as required by section 5(a) of the GPL.

The full wording from `NOTICE`:

> You must preserve the following attribution in all copies and in all modified
> versions of MoonTweaker, including in the mod metadata, README and any
> user-visible credits:
>
>     MoonTweaker by Kartendsy
>
> You may not remove, hide or replace this attribution. Modified versions must
> be marked as modified in a reasonable way, as required by section 5(a) of the
> GNU GPL v3.

This is an additional term permitted by section 7(b) of the GPL v3, which allows
a licensor to require preservation of reasonable legal notices or author
attributions.

## Modpacks

Including an **unmodified** MoonTweaker jar in a modpack is allowed. The jar
already contains `LICENSE`, `NOTICE` and the `Kartendsy` metadata, so the
attribution travels with it.

If you modify the mod for a pack:

- mark the jar or build as modified;
- keep the attribution `MoonTweaker by Kartendsy`;
- keep the GPL license and notice;
- publish the modified source under the GPL.

If you only write Lua scripts for MoonTweaker, the mod itself is unchanged. The
GPL covers MoonTweaker; your scripts are separate files loaded at runtime. This
page is not legal advice; consult the license text or a lawyer for
derivative-work questions.

## Redistribution checklist

- [ ] The jar is unmodified, or the modification is clearly marked.
- [ ] `LICENSE` is present.
- [ ] `NOTICE` is present.
- [ ] `MoonTweaker by Kartendsy` appears in the metadata and credits.
- [ ] The source is available under GPL-3.0-or-later.
- [ ] No extra restrictions are added.
- [ ] The mod is not presented as the original work of someone else.

## What is not required

- You do **not** need to pay a fee.
- You do **not** need to publish your Lua scripts.
- You do **not** need to open-source your server configuration.
- You do **not** need to ask permission to include the unmodified jar in a
  modpack.
- You do **not** need to use the same license for unrelated software that merely
  interacts with MoonTweaker.

## Third-party components

| Component | Role | License |
|---|---|---|
| LuaJ 3.0.1 | Embedded Lua 5.2 runtime | See the [LuaJ project](https://github.com/luaj/luaj) |
| RetroFuturaGradle | Build toolchain | See the [RFG project](https://github.com/GTNewHorizons/RetroFuturaGradle) |
| Minecraft Forge | Runtime platform | Not bundled; see Forge's terms |

Only LuaJ is embedded in the mod jar. Minecraft, Forge and the optional machine
mods are not redistributed by MoonTweaker.

## Credits

- **Author and copyright holder:** Kartendsy
- **Lua runtime:** [LuaJ](https://github.com/luaj/luaj)
- **Build toolchain:**
  [RetroFuturaGradle](https://github.com/GTNewHorizons/RetroFuturaGradle)
- **Inspiration:** the scripting model of CraftTweaker and KubeJS

The documentation on this site describes the same project and is covered by the
same license unless a page states otherwise.

## Questions

If you are unsure whether a distribution complies:

1. Read the full [`LICENSE`](https://github.com/Kartendsy/MoonTweaker/blob/main/LICENSE).
2. Read the [`NOTICE`](https://github.com/Kartendsy/MoonTweaker/blob/main/NOTICE).
3. Ask in the project's GitHub issues before publishing.

## What comes next

[Building](/docs/development/building) explains how to produce your own jar from
source.
