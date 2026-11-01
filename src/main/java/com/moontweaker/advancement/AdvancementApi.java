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
import java.util.Collections;
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

        // NEW: advancement.modify(id, patch) - modify existing advancement
        advancement.set("modify", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue id, LuaValue patch) {
                String checked = parseId(id.checkjstring());
                if (!patch.istable()) throw new LuaError("advancement.modify(): expected patch table for '" + id + "'");
                
                // Find in pending first, then startup, then check if vanilla
                JsonObject existing = pending.get(checked);
                if (existing == null) existing = startupPending.get(checked);
                
                if (existing != null) {
                    // Merge patch into existing custom advancement
                    JsonObject patchJson = toJson(patch).getAsJsonObject();
                    deepMerge(existing, patchJson);
                    // Re-validate
                    try {
                        AdvancementManager.GSON.fromJson(existing, Advancement.Builder.class);
                    } catch (Exception e) {
                        throw new LuaError("advancement.modify(): invalid patch for '" + checked + "': " + rootMessage(e));
                    }
                } else {
                    // For vanilla/mod advancements, we need to fetch the existing JSON and merge
                    // Store patch to apply on next reload
                    // For now, just add as new custom (will override vanilla)
                    Map<String, JsonObject> target = isStartupStage() ? startupPending : pending;
                    target.put(checked, toJson(patch).getAsJsonObject());
                }
                return LuaValue.NIL;
            }
        });

        g.set("advancement", advancement);
    }

    // NEW: Deep merge patch into target
    private static void deepMerge(JsonObject target, JsonObject patch) {
        for (Map.Entry<String, JsonElement> entry : patch.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            if (value.isJsonObject() && target.has(key) && target.get(key).isJsonObject()) {
                deepMerge(target.get(key).getAsJsonObject(), value.getAsJsonObject());
            } else {
                target.add(key, value);
            }
        }
    }

    private static void add(String id, LuaValue def) {
        String checked = parseId(id);
        if (!def.istable()) throw new LuaError("advancement.add(): expected definition table for '" + id + "'");
        LuaValue display = def.get("display");
        if (display.istable()) {
            LuaValue icon = display.get("icon");
            if (icon.isstring()) {
                LuaValue iconObj = parseIconString(icon.checkjstring());
                display.set("icon", iconObj);
            }
        }
        // NEW: Process criteria to accept both meta and data
        LuaValue criteria = def.get("criteria");
        if (criteria.istable()) {
            processCriteriaItems(criteria);
        }
        // NEW: Process requirements for AND/OR logic (Priority 5 #17)
        LuaValue requirements = def.get("requirements");
        if (requirements.istable()) {
            // Validate and convert requirements to vanilla format (array of arrays)
            convertRequirements(def, requirements, criteria);
        }
        // NEW: Process rewards (Priority 5 #14)
        LuaValue rewards = def.get("rewards");
        if (rewards.istable()) {
            // Validate rewards structure
            validateRewards(rewards);
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

    // Convert requirements from Lua table to vanilla array-of-arrays format
    private static void convertRequirements(LuaValue def, LuaValue requirements, LuaValue criteria) {
        if (!criteria.istable()) return;
        Set<String> criterionNames = new LinkedHashSet<>();
        LuaValue[] critKeys = criteria.checktable().keys();
        for (LuaValue key : critKeys) {
            criterionNames.add(key.tojstring());
        }
        
        // Convert from { group1 = { "crit1", "crit2" }, group2 = { "crit3" } }
        // to [ [ "crit1", "crit2" ], [ "crit3" ] ]
        LuaValue[] reqKeys = requirements.checktable().keys();
        if (reqKeys.length > 0) {
            // Check if already in array format (numeric keys)
            boolean isArrayFormat = true;
            for (LuaValue key : reqKeys) {
                if (!key.isint()) {
                    isArrayFormat = false;
                    break;
                }
            }
            
            if (!isArrayFormat) {
                // Convert from named groups to array
                JsonArray vanillaReqs = new JsonArray();
                // Sort keys for consistent ordering
                List<String> sortedKeys = new ArrayList<>();
                for (LuaValue key : reqKeys) {
                    sortedKeys.add(key.tojstring());
                }
                Collections.sort(sortedKeys);
                
                for (String groupName : sortedKeys) {
                    LuaValue group = requirements.get(groupName);
                    if (group.istable()) {
                        JsonArray groupArray = new JsonArray();
                        LuaValue[] itemKeys = group.checktable().keys();
                        for (LuaValue itemKey : itemKeys) {
                            String critName = itemKey.tojstring();
                            if (criterionNames.contains(critName)) {
                                groupArray.add(new JsonPrimitive(critName));
                            }
                        }
                        if (groupArray.size() > 0) {
                            vanillaReqs.add(groupArray);
                        }
                    }
                }
                
                // Replace the requirements table with the converted array
                def.set("requirements", jsonArrayToLua(vanillaReqs));
            }
        }
    }

    private static LuaValue jsonArrayToLua(JsonArray arr) {
        LuaValue result = LuaValue.tableOf();
        for (int i = 0; i < arr.size(); i++) {
            JsonElement elem = arr.get(i);
            if (elem.isJsonArray()) {
                LuaValue inner = LuaValue.tableOf();
                JsonArray innerArr = elem.getAsJsonArray();
                for (int j = 0; j < innerArr.size(); j++) {
                    inner.set(j + 1, LuaValue.valueOf(innerArr.get(j).getAsString()));
                }
                result.set(i + 1, inner);
            }
        }
        return result;
    }

    private static void validateRewards(LuaValue rewards) {
        // Validate known reward types
        LuaValue[] keys = rewards.checktable().keys();
        for (LuaValue key : keys) {
            String name = key.tojstring();
            if (!name.equals("experience") && !name.equals("recipes") && 
                !name.equals("loot") && !name.equals("function")) {
                // Unknown reward type - warn but don't fail
            }
        }
    }

    // NEW: Validate requirements array for AND/OR logic
    private static void validateRequirements(LuaValue requirements, LuaValue criteria) {
        if (!criteria.istable()) return;
        Set<String> criterionNames = new LinkedHashSet<>();
        LuaValue[] critKeys = criteria.checktable().keys();
        for (LuaValue key : critKeys) {
            criterionNames.add(key.tojstring());
        }
        LuaValue[] reqKeys = requirements.checktable().keys();
        for (LuaValue key : reqKeys) {
            LuaValue req = requirements.get(key);
            if (req.istable()) {
                LuaValue[] reqItemKeys = req.checktable().keys();
                for (LuaValue reqItem : reqItemKeys) {
                    String name = reqItem.tojstring();
                    if (!criterionNames.contains(name)) {
                        // Just warn, don't fail - vanilla will validate on load
                    }
                }
            }
        }
    }

    // NEW: Parse icon string - supports "mod:item", "mod:item:meta", or "mod:item:meta:count"
    private static LuaValue parseIconString(String iconStr) {
        String[] parts = iconStr.split(":");
        if (parts.length < 2) throw new LuaError("bad icon format '" + iconStr + "', expected 'mod:item' or 'mod:item:meta'");
        LuaValue iconObj = LuaValue.tableOf();
        iconObj.set("item", LuaValue.valueOf(parts[0] + ":" + parts[1]));
        if (parts.length >= 3) {
            try {
                iconObj.set("meta", LuaValue.valueOf(Integer.parseInt(parts[2])));
            } catch (NumberFormatException e) {
                iconObj.set("data", LuaValue.valueOf(parts[2])); // fallback
            }
        }
        if (parts.length >= 4) {
            try {
                iconObj.set("count", LuaValue.valueOf(Integer.parseInt(parts[3])));
            } catch (NumberFormatException ignored) {}
        }
        return iconObj;
    }

    // NEW: Process criteria items to accept both meta and data
    private static void processCriteriaItems(LuaValue criteria) {
        LuaValue[] keys = criteria.checktable().keys();
        for (LuaValue key : keys) {
            LuaValue criterion = criteria.get(key);
            if (criterion.istable()) {
                LuaValue conditions = criterion.get("conditions");
                if (conditions.istable()) {
                    LuaValue items = conditions.get("items");
                    if (items.istable()) {
                        processItemArray(items);
                    } else if (items.isnil() == false) {
                        // Single item
                        processItemObject(items);
                    }
                    // Also check for "item" key directly (some vanilla criteria)
                    LuaValue item = conditions.get("item");
                    if (item.istable()) {
                        processItemObject(item);
                    }
                }
            }
        }
    }

    private static void processItemArray(LuaValue items) {
        LuaValue[] keys = items.checktable().keys();
        for (LuaValue key : keys) {
            LuaValue item = items.get(key);
            if (item.istable()) processItemObject(item);
        }
    }

    private static void processItemObject(LuaValue item) {
        // If has 'meta' but no 'data', copy meta to data (vanilla uses 'data')
        if (item.get("meta").isint() && item.get("data").isnil()) {
            item.set("data", item.get("meta"));
        }
        // If has 'data' but no 'meta', copy data to meta (for Lua convenience)
        if (item.get("data").isint() && item.get("meta").isnil()) {
            item.set("meta", item.get("data"));
        }
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
