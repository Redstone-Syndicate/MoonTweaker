package com.moontweaker.registry;

import com.moontweaker.api.MoonTweakerApi;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemSword;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.EnumHelper;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.*;
import java.util.regex.Pattern;

public final class RegistryApi {

    private static final String DEFAULT_NAMESPACE = "moontweaker";
    private static final Pattern NAMESPACE_PATTERN = Pattern.compile("^[a-z0-9_.-]+$");

    private static String namespace = DEFAULT_NAMESPACE;
    private static final Set<ResourceLocation> usedIds = new HashSet<>();
    private static final List<Item> items = new ArrayList<>();
    private static final List<Block> blocks = new ArrayList<>();
    private static final Map<String, Item.ToolMaterial> toolMaterials = new HashMap<>();
    private static final Map<String, ItemArmor.ArmorMaterial> armorMaterials = new HashMap<>();

    private static final List<Map.Entry<String, EntityEquipmentSlot>> ARMOR_PIECES = Arrays.asList(
        new AbstractMap.SimpleEntry<>("helmet", EntityEquipmentSlot.HEAD),
        new AbstractMap.SimpleEntry<>("chestplate", EntityEquipmentSlot.CHEST),
        new AbstractMap.SimpleEntry<>("leggings", EntityEquipmentSlot.LEGS),
        new AbstractMap.SimpleEntry<>("boots", EntityEquipmentSlot.FEET)
    );

    public static void reset() {
        namespace = DEFAULT_NAMESPACE;
        usedIds.clear();
        items.clear();
        blocks.clear();
        toolMaterials.clear();
        armorMaterials.clear();
    }

    public static void install(Globals g) {
        if (!"registry".equals(MoonTweakerApi.currentStage)) {
            g.set("registry", restricted());
            return;
        }

        LuaValue registry = LuaValue.tableOf();
        registry.set("namespace", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String value = arg.checkjstring();
                if (!NAMESPACE_PATTERN.matcher(value).matches()) throw new LuaError("bad namespace '" + value + "'");
                namespace = value;
                return LuaValue.NIL;
            }
        });
        registry.set("item", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addItem(args.arg(1).checkjstring(), options(args.arg(2)));
                return LuaValue.NIL;
            }
        });
        registry.set("material", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addMaterial(args.arg(1).checkjstring(), args.arg(2).checkjstring(), options(args.arg(3)));
                return LuaValue.NIL;
            }
        });
        registry.set("block", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addBlock(args.arg(1).checkjstring(), options(args.arg(2)));
                return LuaValue.NIL;
            }
        });
        registry.set("tool", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addTool(args.arg(1).checkjstring(), args.arg(2).checkjstring(), options(args.arg(3)));
                return LuaValue.NIL;
            }
        });
        registry.set("armor", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addArmor(args.arg(1).checkjstring(), options(args.arg(2)));
                return LuaValue.NIL;
            }
        });
        registry.set("fluid", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addFluid(args.arg(1).checkjstring(), options(args.arg(2)));
                return LuaValue.NIL;
            }
        });
        g.set("registry", registry);
    }

    public static List<Item> pendingItems() {
        return Collections.unmodifiableList(items);
    }

    public static List<Block> pendingBlocks() {
        return Collections.unmodifiableList(blocks);
    }

    private static LuaValue restricted() {
        LuaValue table = LuaValue.tableOf();
        VarArgFunction fail = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                throw new LuaError("registry API is only available in lua_scripts/registry/");
            }
        };
        for (String name : Arrays.asList("namespace", "item", "material", "block", "tool", "armor", "fluid")) {
            table.set(name, fail);
        }
        return table;
    }

    private static void addItem(String name, LuaValue opts) {
        ResourceLocation id = id(name);
        Item item = new Item();
        item.setRegistryName(id);
        item.setTranslationKey(id.getPath());
        item.setCreativeTab(CreativeTabs.MISC);
        item.setMaxStackSize(clamp(opts.get("max_stack").optint(64), 1, 64));
        items.add(item);
    }

    private static void addMaterial(String kind, String name, LuaValue opts) {
        String suffix;
        switch (kind) {
            case "ingot": case "dust": case "gem":
                suffix = kind;
                break;
            default:
                throw new LuaError("unknown material kind '" + kind + "' (ingot, dust, gem)");
        }
        addItem(name + "_" + suffix, opts);
    }

    private static void addBlock(String name, LuaValue opts) {
        ResourceLocation id = id(name);
        Block block = new Block(blockMaterial(opts.get("material").optjstring("rock")));
        block.setRegistryName(id);
        block.setTranslationKey(id.getPath());
        block.setHardness((float) opts.get("hardness").optdouble(2.0));
        block.setResistance((float) opts.get("resistance").optdouble(10.0));
        blocks.add(block);

        ItemBlock itemBlock = new ItemBlock(block);
        itemBlock.setRegistryName(id);
        itemBlock.setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
        items.add(itemBlock);
    }

    private static void addTool(String kind, String materialName, LuaValue opts) {
        Item.ToolMaterial material = toolMaterial(materialName, opts);
        Item tool;
        switch (kind) {
            case "pickaxe": tool = new Pickaxe(material); break;
            case "axe": tool = new Axe(material); break;
            case "shovel": tool = new Shovel(material); break;
            case "sword": tool = new ItemSword(material); break;
            case "hoe": tool = new ItemHoe(material); break;
            default: throw new LuaError("unknown tool '" + kind + "' (pickaxe, axe, shovel, sword, hoe)");
        }
        ResourceLocation id = id(materialName + "_" + kind);
        tool.setRegistryName(id);
        tool.setTranslationKey(id.getPath());
        tool.setCreativeTab(CreativeTabs.TOOLS);
        items.add(tool);
    }

    private static void addArmor(String materialName, LuaValue opts) {
        ItemArmor.ArmorMaterial material = armorMaterial(materialName, opts);
        for (Map.Entry<String, EntityEquipmentSlot> entry : ARMOR_PIECES) {
            String piece = entry.getKey();
            EntityEquipmentSlot slot = entry.getValue();
            ItemArmor armor = new ItemArmor(material, 0, slot);
            ResourceLocation id = id(materialName + "_" + piece);
            armor.setRegistryName(id);
            armor.setTranslationKey(id.getPath());
            armor.setCreativeTab(CreativeTabs.COMBAT);
            items.add(armor);
        }
    }

    private static void addFluid(String name, LuaValue opts) {
        ResourceLocation id = id(name);
        Fluid fluid = new Fluid(
            id.getNamespace() + "_" + id.getPath(),
            new ResourceLocation(id.getNamespace(), "blocks/" + id.getPath() + "_still"),
            new ResourceLocation(id.getNamespace(), "blocks/" + id.getPath() + "_flowing")
        );
        fluid.setUnlocalizedName(id.getPath());
        fluid.setDensity(opts.get("density").optint(1000));
        fluid.setViscosity(opts.get("viscosity").optint(1000));
        fluid.setTemperature(opts.get("temperature").optint(300));
        fluid.setLuminosity(opts.get("luminosity").optint(0));
        fluid.setColor(opts.get("color").optint(0xFFFFFF));
        fluid.setGaseous(opts.get("gaseous").optboolean(false));
        if (!FluidRegistry.registerFluid(fluid)) {
            throw new LuaError("fluid '" + id + "' is already registered");
        }

        Block block = new BlockFluidClassic(fluid, Material.WATER);
        block.setRegistryName(id);
        block.setTranslationKey(id.getPath());
        blocks.add(block);

        ResourceLocation bucketId = fresh(id.getNamespace(), id.getPath() + "_bucket");
        ItemBucket bucket = new ItemBucket(block);
        bucket.setRegistryName(bucketId);
        bucket.setTranslationKey(bucketId.getPath());
        bucket.setCreativeTab(CreativeTabs.MISC);
        items.add(bucket);

        FluidRegistry.addBucketForFluid(fluid);
    }

    private static Item.ToolMaterial toolMaterial(String name, LuaValue opts) {
        String key = namespace + ":" + name;
        return toolMaterials.computeIfAbsent(key, k -> {
            try {
                return EnumHelper.addToolMaterial(
                    enumName("tool", name),
                    opts.get("level").optint(1),
                    opts.get("durability").optint(180),
                    (float) opts.get("efficiency").optdouble(4.0),
                    (float) opts.get("damage").optdouble(1.0),
                    opts.get("enchantability").optint(10)
                );
            } catch (Exception e) {
                throw new LuaError("cannot create tool material '" + name + "'");
            }
        });
    }

    private static ItemArmor.ArmorMaterial armorMaterial(String name, LuaValue opts) {
        String key = namespace + ":" + name;
        return armorMaterials.computeIfAbsent(key, k -> {
            LuaValue reduction = opts.get("reduction");
            int[] amounts;
            if (reduction.istable()) {
                amounts = new int[4];
                for (int i = 0; i < 4; i++) {
                    amounts[i] = reduction.get(i + 1).optint(0);
                }
            } else {
                amounts = new int[]{1, 3, 2, 1};
            }
            try {
                return EnumHelper.addArmorMaterial(
                    enumName("armor", name),
                    namespace + "_" + name,
                    opts.get("durability").optint(15),
                    amounts,
                    opts.get("enchantability").optint(10),
                    SoundEvents.ITEM_ARMOR_EQUIP_GENERIC,
                    (float) opts.get("toughness").optdouble(0.0)
                );
            } catch (Exception e) {
                throw new LuaError("cannot create armor material '" + name + "'");
            }
        });
    }

    private static String enumName(String kind, String name) {
        return (namespace + "_" + kind + "_" + name).toUpperCase().replaceAll("[^A-Z0-9_]", "_");
    }

    private static Material blockMaterial(String name) {
        switch (name) {
            case "rock": return Material.ROCK;
            case "wood": return Material.WOOD;
            case "iron": return Material.IRON;
            case "glass": return Material.GLASS;
            case "sand": return Material.SAND;
            case "ground": return Material.GROUND;
            case "cloth": return Material.CLOTH;
            default: throw new LuaError("unknown block material '" + name + "'");
        }
    }

    private static LuaValue options(LuaValue value) {
        return value.isnil() ? LuaValue.tableOf() : value.checktable();
    }

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

    private static ResourceLocation fresh(String ns, String path) {
        ResourceLocation location;
        try {
            location = new ResourceLocation(ns, path);
        } catch (Exception e) {
            throw new LuaError("bad id '" + ns + ":" + path + "'");
        }
        if (!usedIds.add(location)) throw new LuaError("duplicate id '" + location + "'");
        return location;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static class Pickaxe extends ItemPickaxe {
        Pickaxe(Item.ToolMaterial material) { super(material); }
    }
    private static class Axe extends ItemAxe {
        Axe(Item.ToolMaterial material) { super(material, 6.0F, -3.0F); }
    }
    private static class Shovel extends ItemSpade {
        Shovel(Item.ToolMaterial material) { super(material); }
    }
}