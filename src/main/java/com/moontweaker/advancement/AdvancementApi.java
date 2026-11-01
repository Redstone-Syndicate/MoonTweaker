package com.moontweaker.advancement;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.moontweaker.api.MoonTweakerApi;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Triumph-style custom advancements, defined purely in Lua.
 *
 * Scripts call {@code advancement.add("modid:path", { ... })} with a table that mirrors the
 * vanilla advancement JSON (display, criteria, requirements, rewards). The table is converted
 * to JSON, validated through vanilla's own parser, then written to
 * {@code <world>/data/advancements/<modid>/<path>.json} on apply, after which the server
 * {@link AdvancementManager} is reloaded. Vanilla then syncs the advancements to clients.
 *
 * Vanilla and mod advancements can be hidden with same-id tombstone files:
 * {@code advancement.removeVanillaAdvancement(id)}, {@code advancement.removeVanillaTree(id)},
 * or {@code advancement.removeAllVanilla()}. Custom definitions always win over a tombstone,
 * so a pack can replace vanilla trees with its own Lua-defined trees.
 */
public final class AdvancementApi {

    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final String MANIFEST = ".moontweaker";
    private static final String VANILLA_MANIFEST = ".moontweaker-vanilla";

    private static final Map<String, JsonObject> pending = new LinkedHashMap<>();
    private static final Map<String, JsonObject> startupPending = new LinkedHashMap<>();
    private static final Set<String> removals = new LinkedHashSet<>();
    private static final Set<String> startupRemovals = new LinkedHashSet<>();
    private static final Set<String> vanillaRemovals = new LinkedHashSet<>();
    private static final Set<String> startupVanillaRemovals = new LinkedHashSet<>();
    private static final Set<String> vanillaTreeRemovals = new LinkedHashSet<>();
    private static final Set<String> startupVanillaTreeRemovals = new LinkedHashSet<>();
    private static boolean allVanillaRemoved;
    private static boolean startupAllVanillaRemoved;

    private AdvancementApi() {
    }

    /**
     * Clears only the reloadable {@code server/} stage. Definitions queued from {@code startup/}
     * stay alive, because {@code /moontweaker reload} re-runs only {@code server/}.
     */
    public static void reset() {
        pending.clear();
        removals.clear();
        vanillaRemovals.clear();
        vanillaTreeRemovals.clear();
        allVanillaRemoved = false;
    }

    public static void install(Globals g) {
        LuaValue advancement = LuaValue.tableOf();
        advancement.set("add", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue id, LuaValue def) {
                AdvancementApi.add(id.checkjstring(), def);
                return LuaValue.NIL;
            }
        });
        advancement.set("remove", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue id) {
                remove(parseId(id.checkjstring()));
                return LuaValue.NIL;
            }
        });
        OneArgFunction removeVanilla = new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue id) {
                removeVanillaAdvancement(id.checkjstring());
                return LuaValue.NIL;
            }
        };
        advancement.set("removeVanillaAdvancement", removeVanilla);
        advancement.set("remove_vanilla", removeVanilla);

        OneArgFunction removeTree = new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue id) {
                removeVanillaTree(id.checkjstring());
                return LuaValue.NIL;
            }
        };
        advancement.set("removeVanillaTree", removeTree);
        advancement.set("remove_vanilla_tree", removeTree);

        ZeroArgFunction removeAll = new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                removeAllVanilla();
                return LuaValue.NIL;
            }
        };
        advancement.set("removeAllVanilla", removeAll);
        advancement.set("remove_all_vanilla", removeAll);
        g.set("advancement", advancement);
    }

    private static void add(String id, LuaValue def) {
        String checked = parseId(id);
        if (!def.istable()) throw new LuaError("advancement.add(): expected definition table for '" + id + "'");
        LuaValue display = def.get("display");
        if (display.istable()) {
            LuaValue icon = display.get("icon");
            if (icon.isstring()) {
                LuaValue iconObj = LuaValue.tableOf();
                iconObj.set("item", icon);
                display.set("icon", iconObj);
            }
        }
        JsonElement json = toJson(def);
        if (!json.isJsonObject()) throw new LuaError("advancement.add(): definition must be a table");
        try {
            AdvancementManager.GSON.fromJson(json, Advancement.Builder.class);
        } catch (Exception e) {
            throw new LuaError("advancement.add(): invalid definition for '" + checked + "': " + rootMessage(e));
        }
        Map<String, JsonObject> target = isStartupStage() ? startupPending : pending;
        target.put(checked, json.getAsJsonObject());
        removals.remove(checked);
        startupRemovals.remove(checked);
        vanillaRemovals.remove(checked);
        startupVanillaRemovals.remove(checked);
    }

    private static void remove(String id) {
        String checked = parseId(id);
        if (isStartupStage()) {
            startupRemovals.add(checked);
        } else {
            removals.add(checked);
        }
    }

    private static boolean isStartupStage() {
        return "startup".equals(MoonTweakerApi.currentStage);
    }

    /**
     * Hides an advancement that comes from vanilla or another mod. The generated file has the same id,
     * so {@code AdvancementManager} loads it instead of the original; it has no display and an
     * {@code impossible} criterion, which keeps it out of the GUI and unable to complete. Removing the
     * call restores the original advancement on the next apply.
     */
    public static void removeVanillaAdvancement(String id) {
        String checked = parseId(id);
        removals.remove(checked);
        startupRemovals.remove(checked);
        if (isStartupStage()) {
            startupVanillaRemovals.add(checked);
        } else {
            vanillaRemovals.add(checked);
        }
        vanillaTreeRemovals.remove(checked);
        startupVanillaTreeRemovals.remove(checked);
    }

    /**
     * Hides an entire advancement tree: the given root plus every descendant. Use this to replace a
     * vanilla tab, e.g. {@code advancement.removeVanillaTree("minecraft:story/root")}. Any custom
     * advancement with the same id as a removed node is kept and wins over the tombstone.
     */
    public static void removeVanillaTree(String id) {
        String checked = parseId(id);
        removals.remove(checked);
        startupRemovals.remove(checked);
        vanillaRemovals.remove(checked);
        startupVanillaRemovals.remove(checked);
        if (isStartupStage()) {
            startupVanillaTreeRemovals.add(checked);
        } else {
            vanillaTreeRemovals.add(checked);
        }
    }

    /**
     * Hides every advancement in the {@code minecraft} namespace. Custom advancements are kept, so a
     * pack can replace all vanilla tabs with its own Lua-defined trees.
     */
    public static void removeAllVanilla() {
        if (isStartupStage()) {
            startupAllVanillaRemoved = true;
        } else {
            allVanillaRemoved = true;
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

    public static void apply(MinecraftServer server, Logger logger) {
        Runnable task = () -> applyNow(server, logger);
        if (server.isCallingFromMinecraftThread()) {
            task.run();
        } else {
            server.addScheduledTask(task);
        }
    }

    private static void applyNow(MinecraftServer server, Logger logger) {
        try {
            File advDir = advancementsDir(server);
            Set<String> previous = readManifest(advDir, MANIFEST);
            Set<String> previousVanilla = readManifest(advDir, VANILLA_MANIFEST);

            Map<String, JsonObject> mergedPending = new LinkedHashMap<>(startupPending);
            mergedPending.putAll(pending);
            Set<String> mergedRemovals = new LinkedHashSet<>(startupRemovals);
            mergedRemovals.addAll(removals);
            for (String id : mergedRemovals) {
                mergedPending.remove(id);
            }

            warnMissingParents(server, mergedPending, logger);

            Set<String> mergedVanillaRemovals = new LinkedHashSet<>(startupVanillaRemovals);
            mergedVanillaRemovals.addAll(vanillaRemovals);
            Set<String> mergedVanillaTreeRemovals = new LinkedHashSet<>(startupVanillaTreeRemovals);
            mergedVanillaTreeRemovals.addAll(vanillaTreeRemovals);

            Set<String> vanillaTargets = collectVanillaTargets(server, mergedPending,
                mergedVanillaRemovals, mergedVanillaTreeRemovals,
                startupAllVanillaRemoved || allVanillaRemoved, logger);

            int changed = 0;
            for (String id : mergedRemovals) {
                if (deleteFile(advDir, id)) changed++;
                previous.remove(id);
            }
            for (String id : new ArrayList<>(previous)) {
                if (!mergedPending.containsKey(id) && deleteFile(advDir, id)) {
                    changed++;
                }
            }
            for (String id : new ArrayList<>(previousVanilla)) {
                if (!vanillaTargets.contains(id) && !mergedPending.containsKey(id) && deleteFile(advDir, id)) {
                    changed++;
                }
            }

            for (Map.Entry<String, JsonObject> entry : mergedPending.entrySet()) {
                if (writeJson(advDir, entry.getKey(), entry.getValue())) changed++;
            }
            AdvancementManager manager = server.getAdvancementManager();
            for (String id : vanillaTargets) {
                if (!mergedPending.containsKey(id)) {
                    Advancement existing = manager.getAdvancement(new ResourceLocation(id));
                    if (writeJson(advDir, id, tombstone(existing))) changed++;
                }
            }
            writeManifest(advDir, MANIFEST, mergedPending.keySet());
            writeManifest(advDir, VANILLA_MANIFEST, vanillaTargets);
            if (changed > 0 || !previous.isEmpty() || !previousVanilla.isEmpty()) {
                server.getAdvancementManager().reload();
                if (server.getAdvancementManager().hasErrored()) {
                    logger.warn("MoonTweaker: advancement reload reported errors; check the log for missing parents or invalid definitions");
                }
                // Vanilla only re-syncs online players from MinecraftServer.reload(); without this,
                // /moontweaker reload leaves clients (and their criterion listeners) on the old tree.
                if (server.getPlayerList() != null) {
                    server.getPlayerList().reloadResources();
                }
                logger.info("MoonTweaker: applied {} custom advancements and hid {} existing advancements",
                    mergedPending.size(), vanillaTargets.size());
            }
        } catch (Throwable e) {
            logger.error("MoonTweaker: failed to apply advancements: {}", e.toString());
        }
    }

    private static void warnMissingParents(MinecraftServer server, Map<String, JsonObject> pending, Logger logger) {
        for (Map.Entry<String, JsonObject> entry : pending.entrySet()) {
            JsonObject json = entry.getValue();
            if (!json.has("parent")) continue;
            String parent = json.get("parent").getAsString();
            if (pending.containsKey(parent)) continue;
            if (server.getAdvancementManager().getAdvancement(new ResourceLocation(parent)) != null) continue;
            logger.warn("MoonTweaker: advancement '{}' references missing parent '{}'", entry.getKey(), parent);
        }
    }

    /**
     * Resolves the ids that must be tombstoned. A tombstone is a same-id JSON file without a display
     * and with an {@code impossible} criterion; vanilla loads it instead of the original file, so the
     * original advancement and its tab disappear. Custom definitions always win over a tombstone.
     */
    private static Set<String> collectVanillaTargets(MinecraftServer server,
                                                     Map<String, JsonObject> pending,
                                                     Set<String> vanillaRemovals,
                                                     Set<String> vanillaTreeRemovals,
                                                     boolean allVanilla,
                                                     Logger logger) {
        Set<String> targets = new LinkedHashSet<>();
        AdvancementManager manager = server.getAdvancementManager();

        if (allVanilla) {
            for (Advancement advancement : manager.getAdvancements()) {
                ResourceLocation id = advancement.getId();
                if (!"minecraft".equals(id.getNamespace())) continue;
                String key = id.toString();
                // Current custom definitions win; they are written after the tombstones.
                if (pending.containsKey(key)) continue;
                targets.add(key);
            }
        }

        for (String id : vanillaRemovals) {
            Advancement advancement = manager.getAdvancement(new ResourceLocation(id));
            if (advancement == null) {
                logger.warn("MoonTweaker: cannot remove advancement '{}': it does not exist", id);
                continue;
            }
            targets.add(id);
        }

        for (String id : vanillaTreeRemovals) {
            Advancement root = manager.getAdvancement(new ResourceLocation(id));
            if (root == null) {
                logger.warn("MoonTweaker: cannot remove advancement tree '{}': it does not exist", id);
                continue;
            }
            collectTree(root, targets, pending, new LinkedHashSet<>());
        }

        targets.removeAll(pending.keySet());
        return targets;
    }

    private static void collectTree(Advancement advancement, Set<String> targets,
                                    Map<String, JsonObject> pending, Set<String> visited) {
        String id = advancement.getId().toString();
        if (!visited.add(id)) return;
        if (!pending.containsKey(id)) {
            targets.add(id);
        }
        for (Advancement child : advancement.getChildren()) {
            collectTree(child, targets, pending, visited);
        }
    }

    private static boolean writeJson(File advDir, String id, JsonObject json) throws java.io.IOException {
        File file = fileFor(advDir, id);
        byte[] bytes = PRETTY.toJson(json).getBytes(StandardCharsets.UTF_8);
        if (file.isFile() && Arrays.equals(Files.readAllBytes(file.toPath()), bytes)) {
            return false;
        }
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), bytes);
        return true;
    }

    private static JsonObject tombstone(Advancement existing) {
        JsonObject root = new JsonObject();
        // Keep the original parent so removing one node does not turn it into a new invisible
        // root; its visible descendants then attach to the nearest visible ancestor.
        if (existing != null && existing.getParent() != null) {
            root.addProperty("parent", existing.getParent().getId().toString());
        }
        JsonObject criterion = new JsonObject();
        criterion.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add("moontweaker_removed", criterion);
        root.add("criteria", criteria);
        return root;
    }

    private static File advancementsDir(MinecraftServer server) {
        File worldDir = server.worlds[0].getSaveHandler().getWorldDirectory();
        return new File(new File(worldDir, "data"), "advancements");
    }

    private static File fileFor(File advDir, String id) {
        ResourceLocation rl = new ResourceLocation(id);
        return new File(advDir, rl.getNamespace() + "/" + rl.getPath() + ".json");
    }

    private static boolean deleteFile(File advDir, String id) throws java.io.IOException {
        File file = fileFor(advDir, id);
        return Files.deleteIfExists(file.toPath());
    }

    private static Set<String> readManifest(File advDir, String name) {
        Set<String> ids = new LinkedHashSet<>();
        File manifest = new File(advDir, name);
        try {
            if (manifest.isFile()) {
                for (String line : Files.readAllLines(manifest.toPath(), StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (!line.isEmpty()) ids.add(line);
                }
            }
        } catch (Exception ignored) {
        }
        return ids;
    }

    private static void writeManifest(File advDir, String name, Set<String> ids) throws java.io.IOException {
        advDir.mkdirs();
        List<String> lines = new ArrayList<>(ids);
        Files.write(new File(advDir, name).toPath(), lines, StandardCharsets.UTF_8);
    }

    private static JsonElement toJson(LuaValue value) {
        if (value.isnil()) return JsonNull.INSTANCE;
        if (value.isboolean()) return new JsonPrimitive(value.checkboolean());
        if (value.isnumber()) {
            double d = value.checkdouble();
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) <= Long.MAX_VALUE) {
                return new JsonPrimitive((long) d);
            }
            return new JsonPrimitive(d);
        }
        if (value.isstring()) return new JsonPrimitive(value.checkjstring());
        if (value.istable()) {
            LuaValue[] keys = value.checktable().keys();
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
                JsonArray arr = new JsonArray();
                for (int i = 1; i <= max; i++) {
                    arr.add(toJson(value.get(i)));
                }
                return arr;
            }
            JsonObject obj = new JsonObject();
            for (LuaValue key : keys) {
                obj.add(key.tojstring(), toJson(value.get(key)));
            }
            return obj;
        }
        throw new LuaError("cannot convert " + value.typename() + " to JSON");
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }
}
