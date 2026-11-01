package com.moontweaker.event;

import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EventApi {

    private static final Map<String, List<LuaValue>> listeners = new HashMap<>();
    private static Logger logger;

    public static void install(Globals g, Logger log) {
        logger = log;
        LuaValue events = LuaValue.tableOf();
        events.set("on", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue name, LuaValue fn) {
                if (!fn.isfunction()) throw new LuaError("events.on expects a function");
                listeners.computeIfAbsent(name.checkjstring(), k -> new ArrayList<>()).add(fn);
                return LuaValue.NIL;
            }
        });
        g.set("events", events);
    }

    public static void clear() {
        listeners.clear();
    }

    public static boolean has(String name) {
        List<LuaValue> list = listeners.get(name);
        return list != null && !list.isEmpty();
    }

    public static LuaValue context(Runnable onCancel) {
        LuaValue ctx = LuaValue.tableOf();
        ctx.set("cancel", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                onCancel.run();
                return LuaValue.NIL;
            }
        });
        return ctx;
    }

    public static void emit(String name, LuaValue ctx) {
        List<LuaValue> list = listeners.get(name);
        if (list == null) return;
        for (LuaValue fn : list) {
            try {
                fn.call(ctx);
            } catch (LuaError e) {
                logger.error("MoonTweaker: error in event '{}': {}", name, e.getMessage());
            } catch (Throwable e) {
                logger.error("MoonTweaker: error in event '{}': {}", name, e.toString());
            }
        }
    }
}