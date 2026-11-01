package com.moontweaker.api;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

public final class ItemApi {

    private static Logger logger;

    private ItemApi() {}

    public static void install(Globals g, Logger log) {
        logger = log;

        LuaValue item = LuaValue.tableOf();

        // item.exists("minecraft:diamond") -> boolean
        item.set("exists", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue id) {
                String itemId = id.checkjstring();
                Item itemObj = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                return LuaValue.valueOf(itemObj != null);
            }
        });

        // item.get("minecraft:diamond", 1) -> { id, meta, count, display_name, max_stack, mod, ore_dict }
        item.set("get", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue id, LuaValue count) {
                String itemId = id.checkjstring();
                Item itemObj = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                if (itemObj == null) return LuaValue.NIL;
                int cnt = count.isnumber() ? count.checkint() : 1;
                ItemStack stack = new ItemStack(itemObj, cnt, 0);
                return itemStackToTable(stack);
            }
        });

        // item.getMeta("minecraft:diamond", 1) -> { id, meta, count, ... } with meta
        item.set("getMeta", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue id, LuaValue meta) {
                String itemId = id.checkjstring();
                Item itemObj = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
                if (itemObj == null) return LuaValue.NIL;
                int m = meta.checkint();
                ItemStack stack = new ItemStack(itemObj, 1, m);
                return itemStackToTable(stack);
            }
        });

        // item.getByOre("ingotIron", 1) -> first matching item table or nil
        item.set("getByOre", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue oreName, LuaValue count) {
                String name = oreName.checkjstring();
                java.util.List<ItemStack> stacks = OreDictionary.getOres(name);
                if (stacks.isEmpty()) return LuaValue.NIL;
                int cnt = count.isnumber() ? count.checkint() : 1;
                ItemStack stack = stacks.get(0).copy();
                stack.setCount(cnt);
                return itemStackToTable(stack);
            }
        });

        // item.listOre("ingotIron") -> array of item tables
        item.set("listOre", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue oreName) {
                String name = oreName.checkjstring();
                java.util.List<ItemStack> stacks = OreDictionary.getOres(name);
                LuaValue arr = LuaValue.tableOf();
                for (int i = 0; i < stacks.size(); i++) {
                    arr.set(i + 1, itemStackToTable(stacks.get(i)));
                }
                return arr;
            }
        });

        // item.getMod("minecraft:diamond") -> "minecraft"
        item.set("getMod", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue id) {
                String itemId = id.checkjstring();
                ResourceLocation rl = new ResourceLocation(itemId);
                return LuaValue.valueOf(rl.getNamespace());
            }
        });

        g.set("item", item);
    }

    private static LuaValue itemStackToTable(ItemStack stack) {
        LuaValue t = LuaValue.tableOf();
        t.set("id", LuaValue.valueOf(stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "minecraft:air"));
        t.set("count", LuaValue.valueOf(stack.getCount()));
        t.set("meta", LuaValue.valueOf(stack.getMetadata()));
        t.set("maxStack", LuaValue.valueOf(stack.getMaxStackSize()));
        t.set("displayName", LuaValue.valueOf(stack.getDisplayName()));
        t.set("mod", LuaValue.valueOf(stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().getNamespace() : "unknown"));
        if (stack.hasTagCompound()) {
            t.set("nbt", LuaValue.valueOf(stack.getTagCompound().toString()));
        }
        int[] oreIds = OreDictionary.getOreIDs(stack);
        if (oreIds.length > 0) {
            LuaValue oreArr = LuaValue.tableOf();
            for (int i = 0; i < oreIds.length; i++) {
                oreArr.set(i + 1, LuaValue.valueOf(OreDictionary.getOreName(oreIds[i])));
            }
            t.set("oreDict", oreArr);
        }
        return t;
    }
}