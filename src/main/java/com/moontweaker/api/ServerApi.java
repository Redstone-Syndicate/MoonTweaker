package com.moontweaker.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.util.List;

public final class ServerApi {

    private static MinecraftServer server;
    private static Logger logger;

    private ServerApi() {}

    public static void install(Globals g, Logger log) {
        logger = log;
        server = FMLCommonHandler.instance().getMinecraftServerInstance();

        LuaValue s = LuaValue.tableOf();

        // server.runCommand(cmd) -> executes command as server (console)
        s.set("runCommand", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue cmd) {
                String command = cmd.checkjstring();
                if (server == null) throw new LuaError("server not available");
                server.getCommandManager().executeCommand(server, command);
                return LuaValue.NIL;
            }
        });

        // server.broadcast(message) -> send to all players
        s.set("broadcast", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue msg) {
                String message = msg.checkjstring();
                if (server == null) throw new LuaError("server not available");
                TextComponentString component = new TextComponentString(message);
                List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
                for (EntityPlayerMP player : players) {
                    player.sendMessage(component);
                }
                return LuaValue.NIL;
            }
        });

        // server.sendMessage(player, message) -> send to specific player (name or player table)
        s.set("sendMessage", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue playerArg, LuaValue msg) {
                String message = msg.checkjstring();
                if (server == null) throw new LuaError("server not available");
                EntityPlayerMP target = resolvePlayer(playerArg);
                if (target == null) throw new LuaError("player not found");
                target.sendMessage(new TextComponentString(message));
                return LuaValue.NIL;
            }
        });

        // server.getPlayer(name) -> returns player table or nil
        s.set("getPlayer", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue name) {
                if (server == null) throw new LuaError("server not available");
                String playerName = name.checkjstring();
                EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(playerName);
                return player != null ? playerTable(player) : LuaValue.NIL;
            }
        });

        // server.getPlayers() -> array of player tables
        s.set("getPlayers", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                if (server == null) throw new LuaError("server not available");
                List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
                LuaValue arr = LuaValue.tableOf();
                int i = 1;
                for (EntityPlayerMP p : players) {
                    arr.set(i++, playerTable(p));
                }
                return arr;
            }
        });

        g.set("server", s);
    }

    private static EntityPlayerMP resolvePlayer(LuaValue arg) {
        if (arg.isstring()) {
            return server.getPlayerList().getPlayerByUsername(arg.checkjstring());
        }
        if (arg.istable()) {
            LuaValue nameVal = arg.get("name");
            if (nameVal.isstring()) {
                return server.getPlayerList().getPlayerByUsername(nameVal.checkjstring());
            }
            LuaValue uuidVal = arg.get("uuid");
            if (uuidVal.isstring()) {
                return server.getPlayerList().getPlayerByUUID(java.util.UUID.fromString(uuidVal.checkjstring()));
            }
        }
        return null;
    }

    public static LuaValue playerTable(EntityPlayerMP player) {
        LuaValue t = LuaValue.tableOf();
        t.set("name", LuaValue.valueOf(player.getName()));
        t.set("uuid", LuaValue.valueOf(player.getUniqueID().toString()));
        t.set("x", LuaValue.valueOf(player.posX));
        t.set("y", LuaValue.valueOf(player.posY));
        t.set("z", LuaValue.valueOf(player.posZ));
        t.set("dimension", LuaValue.valueOf(player.dimension));
        t.set("health", LuaValue.valueOf(player.getHealth()));
        t.set("maxHealth", LuaValue.valueOf(player.getMaxHealth()));
        t.set("gamemode", LuaValue.valueOf(player.interactionManager.getGameType().getID()));
        return t;
    }
}