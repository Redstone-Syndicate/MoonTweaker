package com.moontweaker.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.ThreeArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class JsonApi {

    private static Logger logger;

    private JsonApi() {}

    public static void install(Globals g, Logger log) {
        logger = log;

        LuaValue json = LuaValue.tableOf();

        // json.parse(string) -> table
        json.set("parse", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue str) {
                String s = str.checkjstring();
                try {
                    JsonElement elem = new JsonParser().parse(s);
                    return jsonElementToLua(elem);
                } catch (Exception e) {
                    throw new LuaError("json.parse: " + e.getMessage());
                }
            }
        });

        // json.stringify(table) -> string
        json.set("stringify", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue val) {
                try {
                    JsonElement elem = luaToJsonElement(val);
                    return LuaValue.valueOf(new GsonBuilder().setPrettyPrinting().create().toJson(elem));
                } catch (Exception e) {
                    throw new LuaError("json.stringify: " + e.getMessage());
                }
            }
        });

        // json.readFile("config/data.json") -> table
        json.set("readFile", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue path) {
                String p = path.checkjstring();
                File file = new File(p);
                if (!file.isAbsolute()) {
                    File configDir = net.minecraftforge.fml.common.Loader.instance().getConfigDir();
                    File gameDir = configDir != null ? configDir.getParentFile() : new File(".");
                    file = new File(gameDir, p);
                }
                try {
                    String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                    JsonElement elem = new JsonParser().parse(content);
                    return jsonElementToLua(elem);
                } catch (Exception e) {
                    throw new LuaError("json.readFile: " + e.getMessage());
                }
            }
        });

        // json.writeFile("config/data.json", table) -> void
        json.set("writeFile", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue path, LuaValue val) {
                String p = path.checkjstring();
                File file = new File(p);
                if (!file.isAbsolute()) {
                    File configDir = net.minecraftforge.fml.common.Loader.instance().getConfigDir();
                    File gameDir = configDir != null ? configDir.getParentFile() : new File(".");
                    file = new File(gameDir, p);
                }
                try {
                    file.getParentFile().mkdirs();
                    JsonElement elem = luaToJsonElement(val);
                    String jsonStr = new GsonBuilder().setPrettyPrinting().create().toJson(elem);
                    Files.write(file.toPath(), jsonStr.getBytes(StandardCharsets.UTF_8));
                    return LuaValue.NIL;
                } catch (Exception e) {
                    throw new LuaError("json.writeFile: " + e.getMessage());
                }
            }
        });

        // NBT helpers
        LuaValue nbt = LuaValue.tableOf();

        // nbt.parse(string) -> nbt compound (as string for now, or table)
        nbt.set("parse", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue str) {
                String s = str.checkjstring();
                try {
                    NBTTagCompound tag = JsonToNBT.getTagFromJson(s);
                    return LuaValue.valueOf(tag.toString());
                } catch (Exception e) {
                    throw new LuaError("nbt.parse: " + e.getMessage());
                }
            }
        });

        // nbt.set(itemStack, "path", value) -> itemStack (with modified NBT)
        // path like "display.Name" or "tag.CustomData"
        nbt.set("set", new ThreeArgFunction() {
            @Override
            public LuaValue call(LuaValue stackTable, LuaValue path, LuaValue value) {
                // This is a helper - actual item NBT modification happens via player.giveItemStack
                // Just return the path=value for user to include in item stack table
                LuaValue result = LuaValue.tableOf();
                result.set("nbt_path", path);
                result.set("nbt_value", value);
                return result;
            }
        });

        // nbt.get(itemStack, "path") -> value (not implemented server-side easily)
        nbt.set("get", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue stackTable, LuaValue path) {
                return LuaValue.valueOf("<nbt get not implemented server-side>");
            }
        });

        g.set("json", json);
        g.set("nbt", nbt);
    }

    private static LuaValue jsonElementToLua(JsonElement elem) {
        if (elem.isJsonNull()) return LuaValue.NIL;
        if (elem.isJsonPrimitive()) {
            com.google.gson.JsonPrimitive prim = elem.getAsJsonPrimitive();
            if (prim.isBoolean()) return LuaValue.valueOf(prim.getAsBoolean());
            if (prim.isNumber()) return LuaValue.valueOf(prim.getAsDouble());
            return LuaValue.valueOf(prim.getAsString());
        }
        if (elem.isJsonArray()) {
            LuaValue arr = LuaValue.tableOf();
            int i = 1;
            for (JsonElement e : elem.getAsJsonArray()) {
                arr.set(i++, jsonElementToLua(e));
            }
            return arr;
        }
        if (elem.isJsonObject()) {
            LuaValue obj = LuaValue.tableOf();
            for (java.util.Map.Entry<String, JsonElement> entry : elem.getAsJsonObject().entrySet()) {
                obj.set(entry.getKey(), jsonElementToLua(entry.getValue()));
            }
            return obj;
        }
        return LuaValue.NIL;
    }

    private static JsonElement luaToJsonElement(LuaValue val) {
        if (val.isnil()) return com.google.gson.JsonNull.INSTANCE;
        if (val.isboolean()) return new com.google.gson.JsonPrimitive(val.checkboolean());
        if (val.isnumber()) return new com.google.gson.JsonPrimitive(val.checkdouble());
        if (val.isstring()) return new com.google.gson.JsonPrimitive(val.checkjstring());
        if (val.istable()) {
            LuaValue[] keys = val.checktable().keys();
            boolean array = keys.length > 0;
            int max = 0;
            for (LuaValue key : keys) {
                if (!key.isint()) {
                    array = false;
                    break;
                }
                max = Math.max(max, key.checkint());
            }
            if (array && max == keys.length) {
                com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
                for (int i = 1; i <= max; i++) {
                    arr.add(luaToJsonElement(val.get(i)));
                }
                return arr;
            }
            com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
            for (LuaValue key : keys) {
                obj.add(key.tojstring(), luaToJsonElement(val.get(key)));
            }
            return obj;
        }
        throw new LuaError("cannot convert " + val.typename() + " to JSON");
    }
}