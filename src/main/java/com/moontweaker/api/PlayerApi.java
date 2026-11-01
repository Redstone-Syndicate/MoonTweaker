package com.moontweaker.api;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.List;

public final class PlayerApi {

    private static Logger logger;

    private PlayerApi() {}

    public static void install(Globals g, Logger log) {
        logger = log;

        LuaValue player = LuaValue.tableOf();

        // player.getHeldItem() -> { id, count, meta, display_name, nbt? } or nil
        player.set("getHeldItem", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.getHeldItem: no current player context");
                ItemStack stack = p.getHeldItemMainhand();
                return stack.isEmpty() ? LuaValue.NIL : itemStackToTable(stack);
            }
        });

        // player.getOffhandItem() -> item table or nil
        player.set("getOffhandItem", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.getOffhandItem: no current player context");
                ItemStack stack = p.getHeldItemOffhand();
                return stack.isEmpty() ? LuaValue.NIL : itemStackToTable(stack);
            }
        });

        // player.countItem("minecraft:diamond") -> integer
        player.set("countItem", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue itemId) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.countItem: no current player context");
                String id = itemId.checkjstring();
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                if (item == null) return LuaValue.valueOf(0);
                int count = 0;
                for (ItemStack stack : p.inventory.mainInventory) {
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        count += stack.getCount();
                    }
                }
                for (ItemStack stack : p.inventory.offHandInventory) {
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        count += stack.getCount();
                    }
                }
                return LuaValue.valueOf(count);
            }
        });

        // player.countItemOre("ingotIron") -> integer (counts all items in ore dict)
        player.set("countItemOre", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue oreName) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.countItemOre: no current player context");
                String name = oreName.checkjstring();
                java.util.List<ItemStack> oreStacks = OreDictionary.getOres(name);
                if (oreStacks.isEmpty()) return LuaValue.valueOf(0);
                int count = 0;
                for (ItemStack stack : p.inventory.mainInventory) {
                    if (!stack.isEmpty()) {
                        for (ItemStack oreStack : oreStacks) {
                            if (OreDictionary.itemMatches(oreStack, stack, true)) {
                                count += stack.getCount();
                                break;
                            }
                        }
                    }
                }
                for (ItemStack stack : p.inventory.offHandInventory) {
                    if (!stack.isEmpty()) {
                        for (ItemStack oreStack : oreStacks) {
                            if (OreDictionary.itemMatches(oreStack, stack, true)) {
                                count += stack.getCount();
                                break;
                            }
                        }
                    }
                }
                return LuaValue.valueOf(count);
            }
        });

        // player.giveItem("minecraft:diamond", 1, 0) -> boolean success
        player.set("giveItem", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue itemId, LuaValue count, LuaValue meta) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.giveItem: no current player context");
                String id = itemId.checkjstring();
                int cnt = count.checkint();
                int m = meta.checkint();
                if (cnt <= 0) throw new LuaError("player.giveItem: count must be positive");
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                if (item == null) throw new LuaError("player.giveItem: unknown item '" + id + "'");
                ItemStack stack = new ItemStack(item, cnt, m);
                boolean added = p.inventory.addItemStackToInventory(stack);
                if (!added && !stack.isEmpty()) {
                    p.dropItem(stack, false);
                }
                return LuaValue.valueOf(added);
            }
        });

        // player.giveItemStack({ item = "minecraft:diamond", count = 1, meta = 0, nbt = "{...}" }) -> boolean
        player.set("giveItemStack", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue table) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.giveItemStack: no current player context");
                if (!table.istable()) throw new LuaError("player.giveItemStack: expected table");
                ItemStack stack = tableToItemStack(table);
                if (stack == null) throw new LuaError("player.giveItemStack: invalid item definition");
                boolean added = p.inventory.addItemStackToInventory(stack);
                if (!added && !stack.isEmpty()) {
                    p.dropItem(stack, false);
                }
                return LuaValue.valueOf(added);
            }
        });

        // player.consumeHeld(1) -> boolean (consumes from main hand)
        player.set("consumeHeld", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue amount) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.consumeHeld: no current player context");
                int amt = amount.checkint();
                if (amt <= 0) throw new LuaError("player.consumeHeld: amount must be positive");
                ItemStack held = p.getHeldItemMainhand();
                if (held.isEmpty()) return LuaValue.valueOf(false);
                int toConsume = Math.min(amt, held.getCount());
                held.shrink(toConsume);
                return LuaValue.valueOf(true);
            }
        });

        // player.removeItem("minecraft:diamond", 5) -> integer (actually removed count)
        player.set("removeItem", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue itemId, LuaValue count) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.removeItem: no current player context");
                String id = itemId.checkjstring();
                int toRemove = count.checkint();
                if (toRemove <= 0) return LuaValue.valueOf(0);
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
                if (item == null) return LuaValue.valueOf(0);
                int removed = 0;
                for (int i = 0; i < p.inventory.mainInventory.size() && removed < toRemove; i++) {
                    ItemStack stack = p.inventory.mainInventory.get(i);
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        int take = Math.min(stack.getCount(), toRemove - removed);
                        stack.shrink(take);
                        removed += take;
                    }
                }
                for (int i = 0; i < p.inventory.offHandInventory.size() && removed < toRemove; i++) {
                    ItemStack stack = p.inventory.offHandInventory.get(i);
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        int take = Math.min(stack.getCount(), toRemove - removed);
                        stack.shrink(take);
                        removed += take;
                    }
                }
                return LuaValue.valueOf(removed);
            }
        });

        // player.addEffect("minecraft:speed", 600, 1, true) -> boolean
        // duration in ticks, amplifier 0-based, ambient particles optional (default false)
        player.set("addEffect", new VarArgFunction() {
            @Override
            public LuaValue invoke(org.luaj.vm2.Varargs args) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.addEffect: no current player context");
                if (args.narg() < 3) throw new LuaError("player.addEffect: expected 3-4 arguments");
                String id = args.checkjstring(1);
                ResourceLocation rl = new ResourceLocation(id);
                net.minecraft.potion.Potion potion = ForgeRegistries.POTIONS.getValue(rl);
                if (potion == null) throw new LuaError("player.addEffect: unknown effect '" + id + "'");
                int dur = args.checkint(2);
                int amp = args.checkint(3);
                boolean amb = args.narg() >= 4 ? args.toboolean(4) : false;
                if (dur <= 0) throw new LuaError("player.addEffect: duration must be positive");
                p.addPotionEffect(new PotionEffect(potion, dur, amp, amb, true));
                return LuaValue.valueOf(true);
            }
        });

        // player.removeEffect("minecraft:speed") -> boolean
        player.set("removeEffect", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue effectId) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.removeEffect: no current player context");
                String id = effectId.checkjstring();
                ResourceLocation rl = new ResourceLocation(id);
                net.minecraft.potion.Potion potion = ForgeRegistries.POTIONS.getValue(rl);
                if (potion == null) return LuaValue.valueOf(false);
                p.removePotionEffect(potion);
                return LuaValue.valueOf(true);
            }
        });

        // player.clearEffects() -> void
        player.set("clearEffects", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.clearEffects: no current player context");
                p.clearActivePotions();
                return LuaValue.NIL;
            }
        });

        // player.teleport(x, y, z, dimension) -> void
        player.set("teleport", new VarArgFunction() {
            @Override
            public LuaValue invoke(org.luaj.vm2.Varargs args) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.teleport: no current player context");
                if (args.narg() < 4) throw new LuaError("player.teleport: expected 4 arguments");
                double tx = args.checkdouble(1);
                double ty = args.checkdouble(2);
                double tz = args.checkdouble(3);
                int dim = args.checkint(4);
                MinecraftServer mcServer = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (mcServer == null) throw new LuaError("server not available");
                net.minecraft.world.WorldServer targetWorld = mcServer.getWorld(dim);
                if (targetWorld == null) throw new LuaError("player.teleport: invalid dimension " + dim);
                if (p.dimension != dim) {
                    // Use changeDimension for cross-dimension teleport
                    p.changeDimension(dim);
                    p.setPositionAndUpdate(tx, ty, tz);
                } else {
                    p.setPositionAndUpdate(tx, ty, tz);
                }
                return LuaValue.NIL;
            }
        });

        // player.setGamemode(0/1/2/3) -> void
        player.set("setGamemode", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue mode) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.setGamemode: no current player context");
                int m = mode.checkint();
                net.minecraft.world.GameType gt = net.minecraft.world.GameType.getByID(m);
                if (gt == null) throw new LuaError("player.setGamemode: invalid gamemode " + m);
                p.setGameType(gt);
                return LuaValue.NIL;
            }
        });

        // player.heal(amount) -> void
        player.set("heal", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue amount) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.heal: no current player context");
                float amt = (float) amount.checkdouble();
                p.heal(amt);
                return LuaValue.NIL;
            }
        });

        // player.damage(amount, source) -> void (source optional string)
        player.set("damage", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue amount, LuaValue source) {
                EntityPlayerMP p = getCurrentPlayer();
                if (p == null) throw new LuaError("player.damage: no current player context");
                float amt = (float) amount.checkdouble();
                if (source.isstring()) {
                    // Custom damage source would need more setup, use generic
                    p.attackEntityFrom(net.minecraft.util.DamageSource.GENERIC, amt);
                } else {
                    p.attackEntityFrom(net.minecraft.util.DamageSource.GENERIC, amt);
                }
                return LuaValue.NIL;
            }
        });

        g.set("player", player);
    }

    // Called from event context to set current player
    private static ThreadLocal<EntityPlayerMP> currentPlayer = new ThreadLocal<>();

    public static void setCurrentPlayer(EntityPlayerMP p) {
        currentPlayer.set(p);
    }

    public static void clearCurrentPlayer() {
        currentPlayer.remove();
    }

    private static EntityPlayerMP getCurrentPlayer() {
        return currentPlayer.get();
    }

    private static LuaValue itemStackToTable(ItemStack stack) {
        LuaValue t = LuaValue.tableOf();
        t.set("id", LuaValue.valueOf(stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "minecraft:air"));
        t.set("count", LuaValue.valueOf(stack.getCount()));
        t.set("meta", LuaValue.valueOf(stack.getMetadata()));
        t.set("maxStack", LuaValue.valueOf(stack.getMaxStackSize()));
        t.set("displayName", LuaValue.valueOf(stack.getDisplayName()));
        if (stack.hasTagCompound()) {
            t.set("nbt", LuaValue.valueOf(stack.getTagCompound().toString()));
        }
        // Enchantments
        if (stack.isItemEnchanted()) {
            t.set("enchanted", LuaValue.valueOf(true));
        }
        // Ore dict names
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

    private static ItemStack tableToItemStack(LuaValue table) {
        LuaValue itemVal = table.get("item");
        if (!itemVal.isstring()) return null;
        String id = itemVal.checkjstring();
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        if (item == null) return null;
        int count = table.get("count").isnumber() ? table.get("count").checkint() : 1;
        int meta = table.get("meta").isnumber() ? table.get("meta").checkint() : 0;
        ItemStack stack = new ItemStack(item, count, meta);
        LuaValue nbtVal = table.get("nbt");
        if (nbtVal.isstring()) {
            try {
                net.minecraft.nbt.NBTTagCompound nbt = net.minecraft.nbt.JsonToNBT.getTagFromJson(nbtVal.checkjstring());
                stack.setTagCompound(nbt);
            } catch (Exception e) {
                logger.warn("PlayerApi: failed to parse NBT: {}", e.getMessage());
            }
        }
        return stack;
    }
}