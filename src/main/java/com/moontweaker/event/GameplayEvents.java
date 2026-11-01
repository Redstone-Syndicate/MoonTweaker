package com.moontweaker.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
        LuaValue ctx = EventApi.context(() -> {});
        ctx.set("player", playerTable(event.player));
        EventApi.emit("player.logged_in", ctx);
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!EventApi.has("block.break")) return;
        LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
        ctx.set("player", playerTable(event.getPlayer()));
        ctx.set("block", LuaValue.valueOf(blockId(event.getState())));
        ctx.set("pos", posTable(event.getPos()));
        EventApi.emit("block.break", ctx);
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!EventApi.has("block.place")) return;
        LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
        ctx.set("player", playerTable(event.getPlayer()));
        ctx.set("block", LuaValue.valueOf(blockId(event.getPlacedBlock())));
        ctx.set("pos", posTable(event.getPos()));
        EventApi.emit("block.place", ctx);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!EventApi.has("entity.death")) return;
        LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
        ctx.set("entity", LuaValue.valueOf(entityId(event.getEntityLiving())));
        Entity source = event.getSource().getTrueSource();
        ctx.set("player", source instanceof EntityPlayer ? playerTable((EntityPlayer) source) : LuaValue.NIL);
        EventApi.emit("entity.death", ctx);
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!EventApi.has("item.use")) return;
        LuaValue ctx = EventApi.context(() -> event.setCanceled(true));
        ctx.set("player", playerTable(event.getEntityPlayer()));
        ctx.set("item", itemTable(event.getItemStack()));
        EventApi.emit("item.use", ctx);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!EventApi.has("server.tick")) return;
        EventApi.emit("server.tick", EventApi.context(() -> {}));
    }

    private static LuaValue playerTable(EntityPlayer player) {
        LuaValue t = LuaValue.tableOf();
        t.set("name", LuaValue.valueOf(player.getName()));
        t.set("uuid", LuaValue.valueOf(player.getUniqueID().toString()));
        t.set("x", LuaValue.valueOf(player.posX));
        t.set("y", LuaValue.valueOf(player.posY));
        t.set("z", LuaValue.valueOf(player.posZ));
        t.set("dimension", LuaValue.valueOf(player.dimension));
        return t;
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