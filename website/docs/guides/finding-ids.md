---
id: finding-ids
title: Finding IDs
sidebar_position: 2
description: How to find item, block, fluid, recipe, ore dictionary and advancement IDs.
---

# Finding IDs

Most script errors come from a wrong ID. This guide shows every lookup method
MoonTweaker and Minecraft provide.

## Item and block IDs

### In-game command

```text
/moontweaker dump items 200
/moontweaker dump blocks 200
```

Output:

```text
minecraft:stone (maxStack=64)
my_mod:ruby (maxStack=64)
```

The item dump prints roughly half the requested limit because of a known
double-increment quirk. Use a larger limit than you need, or page through
multiple dumps.

### JEI

With Just Enough Items installed:

1. Open the item list.
2. Hover an item.
3. Press `F3 + H` to enable advanced tooltips if IDs are not shown.
4. The tooltip shows the registry name, for example `minecraft:wool`.

For metadata, JEI shows the item and metadata separately in some versions.

### F3 + H

`F3 + H` toggles advanced tooltips, which show:

- registry names;
- metadata values;
- durability;
- other debug data.

### Holding an item

The `/moontweaker dump` command does not read your hand. Use JEI or advanced
tooltips.

## Metadata

IDs can have a third part:

```text
minecraft:wool:14
minecraft:log:2
```

Metadata meanings are per item. Common examples:

| Item | Metadata |
|---|---|
| `minecraft:wool` | 0–15 colors |
| `minecraft:log` | 0 oak, 1 spruce, 2 birch, 3 jungle |
| `minecraft:log2` | 0 acacia, 1 dark oak |
| `minecraft:stone` | 0 stone, 1 granite, 2 polished granite, ... |

Use `32767` to match any metadata in `recipes.remove`:

```lua
recipes.remove({ output = "minecraft:wool:32767" })
```

## Ore Dictionary names

```text
/moontweaker dump ores
```

Output:

```text
ingotIron [minecraft:iron_ingot]
ingotGold [minecraft:gold_ingot]
logWood [minecraft:log, minecraft:log2, ...]
```

Use them as:

```lua
"ore:ingotIron"
ore("ingotIron")
```

Ore names are **not** item IDs. `ingotIron` is not `minecraft:iron_ingot`.

Common ore names:

```text
ingotIron
ingotGold
ingotCopper
dustIron
dustGold
gemDiamond
logWood
plankWood
blockGlass
blockStone
```

The exact set depends on installed mods.

## Fluid names

```text
/moontweaker dump fluids
```

Output:

```text
water (density=1000)
lava (density=3000)
my_mod_oil (density=900)
```

In machine recipes:

```lua
"fluid:water * 1000"
"fluid:my_mod_oil * 250"
```

`registry.fluid` uses the path, not the internal name:

```lua
registry.fluid("oil", { ... })
-- machine reference: fluid:my_mod_oil
```

The internal name is `<namespace>_<path>`.

## Recipe IDs

```text
/moontweaker dump recipes 100
```

Output:

```text
minecraft:stick -> minecraft:stick x4
my_mod:ruby_block -> my_mod:ruby_block x1
moontweaker:script_0 -> minecraft:diamond_block x1
```

Recipe IDs are used only by:

```lua
recipes.remove({ id = "minecraft:stick" })
```

A bare string is an **output** match:

```lua
recipes.remove("minecraft:stick")
```

## Advancement IDs

Advancement IDs are not listed by `/moontweaker dump`. Use:

- the vanilla advancement screen;
- the `parent` fields in your own definitions;
- the log warnings:

```text
MoonTweaker: cannot remove advancement 'minecraft:story/nope': it does not exist
```

Vanilla roots:

```text
minecraft:story/root
minecraft:nether/root
minecraft:end/root
```

## Mod IDs

For `moonexpansion.mods.*` and dependency checks:

```text
/moontweaker dump items
```

The namespace of an item is usually the mod ID, but not always. Forge's mod
list (in the pause menu or `logs/latest.log`) shows exact mod IDs:

```text
thermalexpansion
enderio
immersiveengineering
```

## Blocks versus items

A block and its `ItemBlock` usually share an ID, but they are different
registries:

```text
minecraft:stone        -- block and item
minecraft:air          -- block; no item
minecraft:water        -- fluid block; no item
```

In scripts:

- `registry.block` creates both.
- `loot.add` needs an **item** ID, not a block-only ID.
- `e.block` in events is a **block** ID.

## Namespaces

An ID without a namespace defaults differently per API:

| API | Default |
|---|---|
| `item("stone")` | Error: fewer than two parts |
| `registry.item("ruby")` | Current registry namespace |
| `advancement.add("root", ...)` | `minecraft` (avoid; write `mypack:root`) |
| Loot table names | Must contain `:` |

Always write the full `namespace:path` outside `registry/`.

## Practical lookup workflow

1. Find the item in JEI.
2. Enable `F3 + H` if needed.
3. Copy the registry name.
4. Add metadata if the variant matters.
5. For ingredients, check `/moontweaker dump ores` for a suitable ore name.
6. Test with `/moontweaker errors` after a restart/reload.

## Common mistakes

| Mistake | Example | Fix |
|---|---|---|
| Using a display name | `"Iron Ingot"` | `"minecraft:iron_ingot"` |
| Missing namespace | `"iron_ingot"` | `"minecraft:iron_ingot"` |
| Ore name as item | `"ingotIron"` | `"ore:ingotIron"` |
| Item ID as recipe ID | `recipes.remove({ id = "minecraft:stick" })` | That is valid, but means the recipe named `minecraft:stick`, not every stick output |
| Block-only ID in loot | `"minecraft:water"` | Use an item ID |
| Wrong metadata | `"minecraft:wool:15"` for black | Check the metadata table |
| Uppercase namespace | `"My_Mod:ruby"` | Namespaces are lowercase |

## What comes next

[Assets](/docs/guides/assets) shows how to make registered content visible.
