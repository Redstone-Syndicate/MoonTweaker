---
id: assets
title: Assets and Resource Packs
sidebar_position: 3
description: Models, blockstates, textures, language keys and armor layers for content registered from Lua.
---

# Assets and resource packs

`registry.*` creates game objects, not visuals. Without a resource pack, items
and blocks exist on the server but render as missing textures on the client.

## Resource pack layout

```text
mypack/
  pack.mcmeta
  assets/
    my_mod/
      lang/
        en_us.lang
      models/
        item/
          ruby.json
          copper_pickaxe.json
        block/
          copper_block.json
      blockstates/
        copper_block.json
      textures/
        items/
          ruby.png
          copper_pickaxe.png
        blocks/
          copper_block.png
          oil_still.png
          oil_flowing.png
        models/
          armor/
            my_mod_copper_layer_1.png
            my_mod_copper_layer_2.png
```

The namespace under `assets/` must match `registry.namespace(...)`.

## `pack.mcmeta`

```json
{
  "pack": {
    "pack_format": 3,
    "description": "My Pack assets"
  }
}
```

`pack_format` is `3` for Minecraft 1.12.2.

Place the folder in:

```text
.minecraft/resourcepacks/mypack/
```

and enable it in **Options → Resource Packs**.

## Items

### Model

`assets/my_mod/models/item/ruby.json`:

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "my_mod:items/ruby"
  }
}
```

- `item/generated` is the standard flat item.
- Tools look better with `item/handheld`:

```json
{
  "parent": "item/handheld",
  "textures": {
    "layer0": "my_mod:items/copper_pickaxe"
  }
}
```

- Armor items also use `item/generated`.
- The texture path is `my_mod:items/ruby`, which resolves to
  `assets/my_mod/textures/items/ruby.png`.

### Texture

```text
assets/my_mod/textures/items/ruby.png
```

16×16 PNG with transparency.

## Blocks

A block needs three files: a blockstate, a block model and an item model.

### Blockstate

`assets/my_mod/blockstates/copper_block.json`:

```json
{
  "variants": {
    "normal": {
      "model": "my_mod:copper_block"
    }
  }
}
```

### Block model

`assets/my_mod/models/block/copper_block.json`:

```json
{
  "parent": "block/cube_all",
  "textures": {
    "all": "my_mod:blocks/copper_block"
  }
}
```

### Item model for the block

`assets/my_mod/models/item/copper_block.json`:

```json
{
  "parent": "my_mod:block/copper_block"
}
```

### Texture

```text
assets/my_mod/textures/blocks/copper_block.png
```

16×16 PNG.

### Other block shapes

| Parent | Shape |
|---|---|
| `block/cube_all` | All faces the same |
| `block/cube_bottom_top` | Different top/bottom/side |
| `block/cube_column` | Log-like |
| `block/orientable` | Furnace-like |
| `block/stairs` | Stairs (needs variants) |
| `block/slab` | Slab (needs variants) |

MoonTweaker creates a plain `Block`, so the model is purely visual. It does not
create stairs, slabs, orientable blocks or tile entities.

## Language keys

`assets/my_mod/lang/en_us.lang`:

```properties
item.ruby.name=Ruby
item.copper_ingot.name=Copper Ingot
item.copper_dust.name=Copper Dust
item.ruby_gem.name=Ruby Gem
item.copper_pickaxe.name=Copper Pickaxe
item.copper_axe.name=Copper Axe
item.copper_shovel.name=Copper Shovel
item.copper_sword.name=Copper Sword
item.copper_hoe.name=Copper Hoe
item.copper_helmet.name=Copper Helmet
item.copper_chestplate.name=Copper Chestplate
item.copper_leggings.name=Copper Leggings
item.copper_boots.name=Copper Boots
item.oil_bucket.name=Oil Bucket

tile.copper_block.name=Copper Block
tile.oil.name=Oil

fluid.oil=Oil
```

Rules:

- Item keys: `item.<path>.name`.
- Block keys: `tile.<path>.name`.
- Fluid key: `fluid.<path>`.
- No namespace in the key, because MoonTweaker sets the translation key to the
  path.
- `en_us.lang` is required; add other languages as needed.

## Tools

Model:

```json
{
  "parent": "item/handheld",
  "textures": {
    "layer0": "my_mod:items/copper_pickaxe"
  }
}
```

Repeat for `_axe`, `_shovel`, `_sword`, `_hoe` with their own textures.

## Armor

Item models use `item/generated`:

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "my_mod:items/copper_helmet"
  }
}
```

Armor layers:

```text
assets/my_mod/textures/models/armor/my_mod_copper_layer_1.png
assets/my_mod/textures/models/armor/my_mod_copper_layer_2.png
```

- Layer 1: helmet, chestplate, boots.
- Layer 2: leggings.
- 64×32 PNG.
- The base name is `<namespace>_<material>`, matching
  `registry.armor("copper", ...)` in namespace `my_mod`.

## Fluids

`registry.fluid("oil", ...)` in namespace `my_mod` expects:

```text
assets/my_mod/textures/blocks/oil_still.png
assets/my_mod/textures/blocks/oil_flowing.png
```

The fluid block also needs a blockstate and models if you want it to render
correctly in the world. Forge's `BlockFluidClassic` uses the fluid's still and
flowing textures, but a blockstate is still required:

`assets/my_mod/blockstates/oil.json`:

```json
{
  "variants": {
    "level=0": { "model": "my_mod:oil" },
    "level=1": { "model": "my_mod:oil" },
    "...": { "model": "my_mod:oil" }
  }
}
```

A minimal model:

`assets/my_mod/models/block/oil.json`:

```json
{
  "textures": {
    "particle": "my_mod:blocks/oil_still"
  }
}
```

Fluid rendering is the most version-sensitive asset area. Test in-world; a
missing blockstate shows the purple/black missing model.

## Buckets

`registry.fluid` creates `<path>_bucket`. Model:

`assets/my_mod/models/item/oil_bucket.json`:

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "my_mod:items/oil_bucket"
  }
}
```

Texture:

```text
assets/my_mod/textures/items/oil_bucket.png
```

Lang:

```properties
item.oil_bucket.name=Oil Bucket
```

## Complete example

For this Lua:

```lua
registry.namespace("my_mod")
registry.item("ruby")
registry.material("ingot", "copper")
registry.block("copper_block", { hardness = 5.0 })
registry.tool("pickaxe", "copper")
registry.armor("copper", { reduction = { 2, 5, 4, 1 } })
registry.fluid("oil", { density = 900 })
```

Provide at least:

```text
assets/my_mod/lang/en_us.lang
assets/my_mod/models/item/ruby.json
assets/my_mod/models/item/copper_ingot.json
assets/my_mod/models/item/copper_block.json
assets/my_mod/models/item/copper_pickaxe.json
assets/my_mod/models/item/copper_helmet.json
assets/my_mod/models/item/copper_chestplate.json
assets/my_mod/models/item/copper_leggings.json
assets/my_mod/models/item/copper_boots.json
assets/my_mod/models/item/oil_bucket.json
assets/my_mod/models/block/copper_block.json
assets/my_mod/blockstates/copper_block.json
assets/my_mod/blockstates/oil.json
assets/my_mod/models/block/oil.json
assets/my_mod/textures/items/ruby.png
assets/my_mod/textures/items/copper_ingot.png
assets/my_mod/textures/items/copper_pickaxe.png
assets/my_mod/textures/items/copper_helmet.png
assets/my_mod/textures/items/copper_chestplate.png
assets/my_mod/textures/items/copper_leggings.png
assets/my_mod/textures/items/copper_boots.png
assets/my_mod/textures/items/oil_bucket.png
assets/my_mod/textures/blocks/copper_block.png
assets/my_mod/textures/blocks/oil_still.png
assets/my_mod/textures/blocks/oil_flowing.png
assets/my_mod/textures/models/armor/my_mod_copper_layer_1.png
assets/my_mod/textures/models/armor/my_mod_copper_layer_2.png
```

## Troubleshooting

| Symptom | Cause |
|---|---|
| Purple/black cube | Missing blockstate or model |
| Invisible item | Missing item model |
| Missing texture | Wrong path or namespace |
| English name is `item.ruby.name` | Missing lang key |
| Armor is invisible | Missing layer textures or wrong base name |
| Fluid is purple | Missing still/flowing textures or blockstate |
| Model not found | Namespace/path mismatch |

## What comes next

[Testing](/docs/guides/testing) is the final checklist before shipping a pack.
