package com.moontweaker.event;

import com.moontweaker.api.PlayerApi;
import com.moontweaker.api.ServerApi;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.luaj.vm2.LuaValue;

public class GameplayEvents {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!EventApi.has("player.logged_in")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> {});
            ctx.set("player", ServerApi.playerTable(player));
            EventApi.emit("player.logged_in", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!EventApi.has("block.break")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("block", LuaValue.valueOf(blockId(event.getState())));
            ctx.set("pos", posTable(event.getPos()));
            EventApi.emit("block.break", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!EventApi.has("block.place")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getPlayer();
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("block", LuaValue.valueOf(blockId(event.getPlacedBlock())));
            ctx.set("pos", posTable(event.getPos()));
            EventApi.emit("block.place", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!EventApi.has("entity.death")) return;
        Entity source = event.getSource().getTrueSource();
        if (source instanceof EntityPlayerMP) {
            PlayerApi.setCurrentPlayer((EntityPlayerMP) source);
        }
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("entity", LuaValue.valueOf(entityId(event.getEntityLiving())));
            ctx.set("player", source instanceof EntityPlayerMP ? ServerApi.playerTable((EntityPlayerMP) source) : LuaValue.NIL);
            EventApi.emit("entity.death", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!EventApi.has("item.use")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("item", itemTable(event.getItemStack()));
            EventApi.emit("item.use", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!EventApi.has("server.tick")) return;
        EventApi.emit("server.tick", EventApi.context(() -> {}));
    }

    // === Priority 3 Events ===

    // player.logged_out
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!EventApi.has("player.logged_out")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> {});
            ctx.set("player", ServerApi.playerTable(player));
            EventApi.emit("player.logged_out", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    // player.death with cause
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!EventApi.has("player.death")) return;
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("cause", LuaValue.valueOf(event.getSource().getDamageType()));
            Entity source = event.getSource().getTrueSource();
            ctx.set("killer", source instanceof EntityPlayerMP ? ServerApi.playerTable((EntityPlayerMP) source) : LuaValue.NIL);
            ctx.set("source_id", LuaValue.valueOf(entityId(source)));
            EventApi.emit("player.death", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    // player.dimension_change
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!EventApi.has("player.dimension_change")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> {});
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("from_dim", LuaValue.valueOf(event.fromDim));
            ctx.set("to_dim", LuaValue.valueOf(event.toDim));
            EventApi.emit("player.dimension_change", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    // player.craft - item crafted
    @SubscribeEvent
    public void onPlayerCraft(net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemCraftedEvent event) {
        if (!EventApi.has("player.craft")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("output", itemTable(event.crafting));
            // Recipe info not directly available in this event
            ctx.set("recipe_id", LuaValue.NIL);
            EventApi.emit("player.craft", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    // block.interact (right click block)
    @SubscribeEvent
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (!EventApi.has("block.interact")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("block", LuaValue.valueOf(blockId(event.getWorld().getBlockState(event.getPos()))));
            ctx.set("pos", posTable(event.getPos()));
            ctx.set("face", LuaValue.valueOf(event.getFace().getName()));
            ctx.set("hand", LuaValue.valueOf(event.getHand().toString()));
            ctx.set("item", itemTable(event.getItemStack()));
            EventApi.emit("block.interact", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    // item.pickup
    @SubscribeEvent
    public void onItemPickup(net.minecraftforge.fml.common.gameevent.PlayerEvent.ItemPickupEvent event) {
        if (!EventApi.has("item.pickup")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> {});
            ctx.set("player", ServerApi.playerTable(player));
            // ItemPickupEvent has 'pickedUp' field (EntityItem)
            net.minecraft.entity.item.EntityItem entityItem = event.pickedUp;
            if (entityItem != null) {
                ItemStack pickedStack = entityItem.getItem();
                ctx.set("item", itemTable(pickedStack));
                ctx.set("count", LuaValue.valueOf(pickedStack.getCount()));
            } else {
                ctx.set("item", LuaValue.NIL);
                ctx.set("count", LuaValue.valueOf(0));
            }
            EventApi.emit("item.pickup", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    // item.right_click (same as item.use but separate for clarity)
    @SubscribeEvent
    public void onItemRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!EventApi.has("item.right_click")) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        PlayerApi.setCurrentPlayer(player);
        try {
            LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
            ctx.set("player", ServerApi.playerTable(player));
            ctx.set("item", itemTable(event.getItemStack()));
            EventApi.emit("item.right_click", ctx);
        } finally {
            PlayerApi.clearCurrentPlayer();
        }
    }

    // entity.spawn
    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!EventApi.has("entity.spawn")) return;
        if (!(event.getEntity() instanceof net.minecraft.entity.EntityLivingBase)) return;
        LuaValue ctx = EventApi.context(() -> {});
        ctx.set("entity", LuaValue.valueOf(entityId(event.getEntity())));
        ctx.set("x", LuaValue.valueOf(event.getEntity().posX));
        ctx.set("y", LuaValue.valueOf(event.getEntity().posY));
        ctx.set("z", LuaValue.valueOf(event.getEntity().posZ));
        ctx.set("dimension", LuaValue.valueOf(event.getEntity().dimension));
        EventApi.emit("entity.spawn", ctx);
    }

    // entity.hurt
    @SubscribeEvent
    public void onEntityHurt(LivingHurtEvent event) {
        if (!EventApi.has("entity.hurt")) return;
        LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
        ctx.set("entity", LuaValue.valueOf(entityId(event.getEntityLiving())));
        ctx.set("amount", LuaValue.valueOf(event.getAmount()));
        ctx.set("source", LuaValue.valueOf(event.getSource().getDamageType()));
        Entity source = event.getSource().getTrueSource();
        ctx.set("attacker", source instanceof EntityPlayerMP ? ServerApi.playerTable((EntityPlayerMP) source) : LuaValue.NIL);
        ctx.set("source_id", LuaValue.valueOf(entityId(source)));
        EventApi.emit("entity.hurt", ctx);
    }

    // server.startup (already have server.tick, but add explicit startup)
    private static boolean serverStartupFired = false;
    @SubscribeEvent
    public void onServerTickStartup(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!serverStartupFired) {
            serverStartupFired = true;
            if (!EventApi.has("server.startup")) return;
            EventApi.emit("server.startup", EventApi.context(() -> {}));
        }
    }

    private static LuaValue posTable(BlockPos pos) {
        LuaValue t = LuaValue.tableOf();
        t.set("x", LuaValue.valueOf(pos.getX()));
        t.set("y", LuaValue.valueOf(pos.getY()));
        t.set("z", LuaValue.valueOf(pos.getZ()));
        return t;
    }

    private static LuaValue itemTable(ItemStack stack) {
        LuaValue t = LuaValue.tableOf();
        t.set("id", LuaValue.valueOf(stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "minecraft:air"));
        t.set("count", LuaValue.valueOf(stack.getCount()));
        t.set("meta", LuaValue.valueOf(stack.getMetadata()));
        return t;
    }

    private static String blockId(net.minecraft.block.state.IBlockState state) {
        return ForgeRegistries.BLOCKS.getKey(state.getBlock()) != null
            ? ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString()
            : "minecraft:air";
    }

    private static String entityId(Entity entity) {
        return EntityList.getKey(entity) != null
            ? EntityList.getKey(entity).toString()
            : "unknown";
    }
}