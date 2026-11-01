package com.moontweaker.loot;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.RandomValueRange;
import net.minecraft.world.storage.loot.conditions.LootCondition;
import net.minecraft.world.storage.loot.functions.LootFunction;
import net.minecraft.world.storage.loot.functions.SetCount;
import net.minecraft.world.storage.loot.functions.SetMetadata;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class LootApi {

    private static abstract class Op {
        final String table;
        Op(String table) { this.table = table; }
        static class Add extends Op {
            final Item item;
            final int min, max, weight, quality, meta;
            Add(String table, Item item, int min, int max, int weight, int quality, int meta) {
                super(table);
                this.item = item; this.min = min; this.max = max; this.weight = weight; this.quality = quality; this.meta = meta;
            }
        }
        static class Remove extends Op {
            final Item item;
            Remove(String table, Item item) { super(table); this.item = item; }
        }
        static class Clear extends Op {
            Clear(String table) { super(table); }
        }
    }

    private static final List<Op> ops = new ArrayList<>();
    private static int poolCounter = 0;

    public static void reset() {
        ops.clear();
        poolCounter = 0;
    }

    public static void install(Globals g) {
        LuaValue loot = LuaValue.tableOf();
        loot.set("add", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue spec) {
                ops.add(parseAdd(tableName(table), spec));
                return LuaValue.NIL;
            }
        });
        loot.set("remove", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue table, LuaValue item) {
                ops.add(new Op.Remove(tableName(table), parseItem(item)));
                return LuaValue.NIL;
            }
        });
        loot.set("clear", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue table) {
                ops.add(new Op.Clear(tableName(table)));
                return LuaValue.NIL;
            }
        });
        g.set("loot", loot);
    }

    public static int apply(String tableName, LootTable table, Logger logger) {
        int applied = 0;
        for (Op op : ops) {
            if (!op.table.equals(tableName)) continue;
            try {
                if (op instanceof Op.Add) {
                    add(table, (Op.Add) op);
                } else if (op instanceof Op.Remove) {
                    remove(table, ((Op.Remove) op).item);
                } else if (op instanceof Op.Clear) {
                    pools(table).clear();
                }
                applied++;
            } catch (Throwable e) {
                logger.error("MoonTweaker: failed to change loot table {}: {}", tableName, e.toString());
            }
        }
        return applied;
    }

    private static Op.Add parseAdd(String table, LuaValue spec) {
        String id;
        int min = 1, max = 1, weight = 1, quality = 0, meta = 0;
        boolean hasMeta = false;

        if (spec.isstring()) {
            id = spec.tojstring();
        } else if (spec.istable()) {
            LuaValue item = spec.get("item");
            if (item.isnil()) throw new LuaError("loot.add(): 'item' is required");
            id = item.checkjstring();
            min = spec.get("min").optint(1);
            max = spec.get("max").optint(min);
            weight = spec.get("weight").optint(1);
            quality = spec.get("quality").optint(0);
            LuaValue metaValue = spec.get("meta");
            meta = metaValue.optint(0);
            hasMeta = !metaValue.isnil();
        } else {
            throw new LuaError("loot.add(): expected item id or table");
        }

        Pair<Item, Integer> parsed = parseId(id);
        Item item = parsed.first;
        Integer idMeta = parsed.second;
        if (!hasMeta && idMeta != null) {
            meta = idMeta;
            hasMeta = true;
        }
        if (min < 1) throw new LuaError("loot.add(): 'min' must be at least 1");
        if (max < min) throw new LuaError("loot.add(): 'max' must be >= 'min'");
        if (weight < 0) throw new LuaError("loot.add(): 'weight' must be >= 0");
        return new Op.Add(table, item, min, max, weight, quality, hasMeta ? meta : 0);
    }

    private static void add(LootTable table, Op.Add op) {
        List<LootFunction> functions = new ArrayList<>();
        functions.add(new SetCount(new LootCondition[0], new RandomValueRange(op.min, op.max)));
        if (op.meta != 0) {
            functions.add(new SetMetadata(new LootCondition[0], new RandomValueRange(op.meta)));
        }

        String id = "moontweaker_" + poolCounter++;
        LootEntryItem entry = new LootEntryItem(
            op.item, op.weight, op.quality,
            functions.toArray(new LootFunction[0]),
            new LootCondition[0], id
        );
        table.addPool(new LootPool(
            new LootEntry[]{entry}, new LootCondition[0],
            new RandomValueRange(1f), new RandomValueRange(0f), id
        ));
    }

    private static void remove(LootTable table, Item item) {
        List<LootPool> pools = pools(table);
        for (LootPool pool : pools) {
            List<LootEntry> entries = entries(pool);
            entries.removeIf(e -> {
                if (!(e instanceof LootEntryItem)) return false;
                try {
                    return entryItemField.get(e) == item;
                } catch (IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
        pools.removeIf(p -> entries(p).isEmpty());
    }

    private static LootCondition[] conditions() {
        return new LootCondition[0];
    }

    private static String tableName(LuaValue value) {
        String name = value.checkjstring();
        if (name.isEmpty() || !name.contains(":")) throw new LuaError("bad loot table name '" + name + "'");
        return name;
    }

    private static Item parseItem(LuaValue value) {
        String id;
        if (value.isstring()) {
            id = value.tojstring();
        } else if (value.istable()) {
            id = value.get("item").checkjstring();
        } else {
            throw new LuaError("loot.remove(): expected item id");
        }
        return parseId(id).first;
    }

    private static Pair<Item, Integer> parseId(String id) {
        String[] parts = id.split(":");
        if (parts.length < 2) throw new LuaError("bad item id '" + id + "'");
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[0], parts[1]));
        if (item == null) throw new LuaError("unknown item '" + id + "'");
        Integer meta = null;
        if (parts.length >= 3) {
            try {
                meta = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                throw new LuaError("bad metadata in '" + id + "'");
            }
        }
        return new Pair<>(item, meta);
    }

    private static Field poolsField;
    private static Field poolEntriesField;
    private static Field entryItemField;

    static {
        try {
            poolsField = findField(LootTable.class, "pools", "field_186466_c");
            poolEntriesField = findField(LootPool.class, "lootEntries", "field_186453_a");
            entryItemField = findField(LootEntryItem.class, "item", "field_186368_a");
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Field findField(Class<?> type, String deobf, String srg) {
        try {
            Field field = type.getDeclaredField(deobf);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            try {
                Field field = type.getDeclaredField(srg);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e2) {
                throw new RuntimeException("Field not found: " + deobf + " / " + srg, e2);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<LootPool> pools(LootTable table) {
        try {
            return (List<LootPool>) poolsField.get(table);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<LootEntry> entries(LootPool pool) {
        try {
            return (List<LootEntry>) poolEntriesField.get(pool);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Pair<F, S> {
        final F first;
        final S second;
        Pair(F first, S second) { this.first = first; this.second = second; }
    }
}