---
id: registry
title: Registry
sidebar_position: 4
description: How registry scripts queue items, blocks, tools, armor and fluids, and how EnumHelper and FluidRegistry are used.
---

# Registry

`registry/` runs during `FMLPreInitializationEvent` and **queues** content.
`RegistryHandler` registers it during the block and item registry events.
Fluids are the exception: they enter `FluidRegistry` immediately.

## State

```java
private static final String DEFAULT_NAMESPACE = "moontweaker";
private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");

private static String namespace = DEFAULT_NAMESPACE;
private static final Set<ResourceLocation> usedIds = new HashSet<>();
private static final List<Item> items = new ArrayList<>();
private static final List<Block> blocks = new ArrayList<>();
private static final Map<String, Item.ToolMaterial> toolMaterials = new HashMap<>();
private static final Map<String, ItemArmor.ArmorMaterial> armorMaterials = new HashMap<>();
```

`reset()` clears all of it and restores the namespace. It runs only when the
`registry` stage loads, never on `/moontweaker reload`.

## ID handling

```java
private static ResourceLocation id(String name) {
    ResourceLocation location;
    try {
        if (name.contains(":")) {
            location = new ResourceLocation(name);
        } else {
            location = new ResourceLocation(namespace, name);
        }
    } catch (Exception e) {
        throw new LuaError("bad id '" + name + "'");
    }
    if (!usedIds.add(location)) throw new LuaError("duplicate id '" + location + "'");
    return location;
}
```

- A name containing `:` is treated as fully qualified and bypasses the current
  namespace.
- Otherwise the current namespace is prepended.
- Every ID is added to `usedIds`; a duplicate aborts the current file.
- The same `ResourceLocation` is used for a block and its `ItemBlock`, but only
  added once. The duplicate check is across **all** registry objects, not per
  registry.

`namespace()` validates `^[a-z0-9_.-]+$`. Uppercase letters are rejected.

## Items

```lua
registry.item("ruby", { max_stack = 64 })
```

```java
Item item = new Item();
item.setRegistryName(id);
item.setTranslationKey(id.getPath());
item.setCreativeTab(CreativeTabs.MISC);
item.setMaxStackSize(clamp(opts.get("max_stack").optint(64), 1, 64));
items.add(item);
```

- Creative tab: Miscellaneous.
- Translation key: the path, e.g. `ruby`.
- Stack size clamped to 1..64.

### Materials

```lua
registry.material("ingot", "copper")   -- my_mod:copper_ingot
registry.material("dust", "copper")    -- my_mod:copper_dust
registry.material("gem", "ruby")       -- my_mod:ruby_gem
```

`addMaterial` appends `_<kind>` to the name and delegates to `addItem`. Unknown
kinds throw:

```text
unknown material kind '<kind>' (ingot, dust, gem)
```

## Blocks

```lua
registry.block("copper_block", {
  hardness = 5.0,
  resistance = 10.0,
  material = "rock",
})
```

```java
ResourceLocation id = id(name);
Block block = new Block(blockMaterial(...));
block.setRegistryName(id);
block.setTranslationKey(id.getPath());
block.setHardness(...);
block.setResistance(...);
blocks.add(block);

ItemBlock itemBlock = new ItemBlock(block);
itemBlock.setRegistryName(id);
itemBlock.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
items.add(itemBlock);
```

- Material mapping: `rock`, `wood`, `iron`, `glass`, `sand`, `ground`, `cloth`.
- Creative tab: Building Blocks.
- No tile entity, no custom drops, no harvest level, no blockstate/model.
- The block and its item share the registry ID because they are in different
  registries.

## Tools

```lua
registry.tool("pickaxe", "copper", {
  level = 2,
  durability = 250,
  efficiency = 6.0,
  damage = 2.0,
  enchantability = 14,
})
```

Tool kinds and classes:

| Kind | Class | ID |
|---|---|---|
| `pickaxe` | private `Pickaxe extends ItemPickaxe` | `<material>_pickaxe` |
| `axe` | private `Axe extends ItemAxe` | `<material>_axe` |
| `shovel` | private `Shovel extends ItemSpade` | `<material>_shovel` |
| `sword` | `ItemSword` | `<material>_sword` |
| `hoe` | `ItemHoe` | `<material>_hoe` |

The private subclasses exist only to call the right superclass constructor:

```java
private static class Axe extends ItemAxe {
    Axe(Item.ToolMaterial material) { super(material, 6.0F, -3.0F); }
}
```

### Tool material creation

```java
String key = namespace + ":" + name;
return toolMaterials.computeIfAbsent(key, k -> {
    return EnumHelper.addToolMaterial(
        enumName("tool", name),
        opts.get("level").optint(1),
        opts.get("durability").optint(180),
        (float) opts.get("efficiency").optdouble(4.0),
        (float) opts.get("damage").optdouble(1.0),
        opts.get("enchantability").optint(10)
    );
});
```

- Keyed by `<namespace>:<material>`, so `copper` in two namespaces creates two
  materials.
- **First call wins.** Later tools with the same material reuse the material
  and ignore their options.
- Enum name: `<namespace>_tool_<name>` uppercased, non `[A-Z0-9_]` replaced by
  `_`.
- `EnumHelper.addToolMaterial` mutates Forge's material list during pre-init.

To get a consistent full tool set, call every kind with the same options.

## Armor

```lua
registry.armor("copper", {
  durability = 15,
  reduction = { 2, 5, 4, 1 },
  enchantability = 12,
  toughness = 0.0,
})
```

Creates four items:

| Piece | Slot |
|---|---|
| `<material>_helmet` | Head |
| `<material>_chestplate` | Chest |
| `<material>_leggings` | Legs |
| `<material>_boots` | Feet |

```java
ItemArmor armor = new ItemArmor(material, 0, slot);
```

- Render index `0`.
- Creative tab: Combat.
- Texture base: `<namespace>_<material>`.
- Sound: `SoundEvents.ITEM_ARMOR_EQUIP_GENERIC`.
- Material keyed by `<namespace>:<material>`, first call wins.
- If `reduction` is not a table, `{1, 3, 2, 1}` is used. If it is a table, the
  four entries are read with `0` as fallback.

Armor textures are looked up at:

```text
assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_1.png
assets/<namespace>/textures/models/armor/<namespace>_<material>_layer_2.png
```

## Fluids

```lua
registry.fluid("oil", {
  density = 900,
  viscosity = 1500,
  temperature = 300,
  luminosity = 0,
  color = 0x1A1A1A,
  gaseous = false,
})
```

```java
ResourceLocation id = id(name);
Fluid fluid = new Fluid(
    id.getNamespace() + "_" + id.getPath(),
    new ResourceLocation(id.getNamespace(), "blocks/" + id.getPath() + "_still"),
    new ResourceLocation(id.getNamespace(), "blocks/" + id.getPath() + "_flowing")
);
fluid.setUnlocalizedName(id.getPath());
...
if (!FluidRegistry.registerFluid(fluid)) {
    throw new LuaError("fluid '" + id + "' is already registered");
}

Block block = new BlockFluidClassic(fluid, Material.WATER);
block.setRegistryName(id);
blocks.add(block);

ResourceLocation bucketId = fresh(id.getNamespace(), id.getPath() + "_bucket");
ItemBucket bucket = new ItemBucket(block);
bucket.setRegistryName(bucketId);
items.add(bucket);

FluidRegistry.addBucketForFluid(fluid);
```

Key points:

- The fluid registers **immediately**, during pre-init. This is the one
  registry operation that does not wait for a registry event.
- Internal fluid name: `<namespace>_<path>`.
- Still texture: `<namespace>:blocks/<path>_still`.
- Flowing texture: `<namespace>:blocks/<path>_flowing`.
- World block: `BlockFluidClassic` with `Material.WATER`, same ID as the fluid.
- Bucket ID: `<path>_bucket`, added through `fresh()` so it participates in the
  duplicate check.
- `FluidRegistry.addBucketForFluid` links the bucket for bucket-filling logic.
- Registering the same fluid twice raises an error. The `usedIds` check may
  catch it first as `duplicate id`.

`Fluid` constructor arguments are `(name, still, flowing)`; the namespace is
prepended to the internal name, which is why it looks doubled in some logs.

## Registration events

```java
public class RegistryHandler {
    @SubscribeEvent
    public void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        List<Block> blocks = RegistryApi.pendingBlocks();
        for (Block block : blocks) event.getRegistry().register(block);
        if (!blocks.isEmpty()) logger.info("MoonTweaker: registered {} blocks", blocks.size());
    }

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> event) {
        List<Item> items = RegistryApi.pendingItems();
        for (Item item : items) event.getRegistry().register(item);
        if (!items.isEmpty()) logger.info("MoonTweaker: registered {} items", items.size());
    }
}
```

- Default priority (`NORMAL`).
- Block event fires before item event.
- Both lists are `Collections.unmodifiableList` views; the handler does not
  mutate them.
- The lists are never cleared after registration. `/moontweaker dump registry`
  still shows them.

## Assets

The registry API creates game objects only. A resource pack must provide:

```text
assets/<namespace>/models/item/<id>.json
assets/<namespace>/textures/items/<id>.png
assets/<namespace>/blockstates/<id>.json
assets/<namespace>/models/block/<id>.json
assets/<namespace>/textures/blocks/<id>.png
assets/<namespace>/lang/en_us.lang
```

Language keys:

| Type | Key |
|---|---|
| Item | `item.<id>.name` |
| Block | `tile.<id>.name` |
| Fluid | `fluid.<id>` |

See [Assets](/docs/guides/assets) for templates.

## Failure modes

| Situation | Result |
|---|---|
| Duplicate ID | `duplicate id '<id>'`, file aborts |
| Bad namespace | `bad namespace '<value>'` |
| Unknown block material | `unknown block material '<name>'` |
| Unknown tool kind | `unknown tool '<kind>' (...)` |
| Unknown material kind | `unknown material kind '<kind>' (...)` |
| Duplicate fluid | `fluid '<id>' is already registered` |
| EnumHelper failure | `cannot create tool/armor material '<name>'` |
| Missing assets | Content exists server-side; missing texture client-side |

Objects queued before the failing line remain queued and are registered.

## What comes next

[Machines](/docs/internals/machines) covers the reflective integration with
Thermal Expansion, Ender IO and Immersive Engineering.
