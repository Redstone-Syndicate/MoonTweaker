package com.moontweaker.advancement;

import com.moontweaker.api.ServerApi;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AdvancementCallbacks {

    private static final Map<String, LuaValue> callbacks = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> completedCache = new ConcurrentHashMap<>();
    private static MinecraftServer server;
    private static Logger logger;
    private static boolean registered = false;

    private AdvancementCallbacks() {}

    public static void install(Globals g, Logger log) {
        logger = log;

        LuaValue advancement = g.get("advancement");
        if (advancement.isnil()) {
            advancement = LuaValue.tableOf();
            g.set("advancement", advancement);
        }

        advancement.set("on_complete", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue id, LuaValue fn) {
                if (!fn.isfunction()) throw new LuaError("advancement.on_complete expects a function");
                String checked = parseId(id.checkjstring());
                callbacks.put(checked, fn);
                logger.info("MoonTweaker: registered on_complete callback for '{}'", checked);
                return LuaValue.NIL;
            }
        });

        advancement.set("on_complete_any", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue fn) {
                if (!fn.isfunction()) throw new LuaError("advancement.on_complete_any expects a function");
                callbacks.put("*", fn);
                logger.info("MoonTweaker: registered on_complete_any callback");
                return LuaValue.NIL;
            }
        });

        if (!registered) {
            FMLCommonHandler.instance().bus().register(new AdvancementCallbacks());
            registered = true;
        }
    }

    public static void clear() {
        callbacks.clear();
        completedCache.clear();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (server == null) {
            server = FMLCommonHandler.instance().getMinecraftServerInstance();
        }
        if (server == null || callbacks.isEmpty()) return;

        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            checkPlayerAdvancements(player);
        }
    }

    private void checkPlayerAdvancements(EntityPlayerMP player) {
        PlayerAdvancements advancements = player.getAdvancements();
        if (advancements == null) return;

        UUID uuid = player.getUniqueID();
        Set<String> previous = completedCache.get(uuid);
        if (previous == null) {
            previous = new HashSet<>();
            completedCache.put(uuid, previous);
        }

        Set<String> current = new HashSet<>();
        for (Advancement advancement : server.getAdvancementManager().getAdvancements()) {
            AdvancementProgress progress = advancements.getProgress(advancement);
            if (progress != null && progress.isDone()) {
                current.add(advancement.getId().toString());
            }
        }

        for (String id : current) {
            if (!previous.contains(id)) {
                // Newly completed!
                fireCallback(id, player);
            }
        }

        completedCache.put(uuid, current);
    }

    private void fireCallback(String advancementId, EntityPlayerMP player) {
        LuaValue specific = callbacks.get(advancementId);
        LuaValue wildcard = callbacks.get("*");

        if (specific != null) {
            try {
                specific.call(ServerApi.playerTable(player));
            } catch (LuaError e) {
                logger.error("MoonTweaker: error in advancement.on_complete('{}'): {}", advancementId, e.getMessage());
            } catch (Throwable e) {
                logger.error("MoonTweaker: error in advancement.on_complete('{}'): {}", advancementId, e.toString());
            }
        }
        if (wildcard != null) {
            try {
                wildcard.call(ServerApi.playerTable(player), LuaValue.valueOf(advancementId));
            } catch (LuaError e) {
                logger.error("MoonTweaker: error in advancement.on_complete_any('{}'): {}", advancementId, e.getMessage());
            } catch (Throwable e) {
                logger.error("MoonTweaker: error in advancement.on_complete_any('{}'): {}", advancementId, e.toString());
            }
        }
    }

    private static String parseId(String id) {
        try {
            ResourceLocation rl = new ResourceLocation(id);
            if (rl.getPath().isEmpty()) throw new LuaError("bad advancement id '" + id + "'");
            return rl.toString();
        } catch (Exception e) {
            throw new LuaError("bad advancement id '" + id + "'");
        }
    }
}