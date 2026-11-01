package com.moontweaker.lua;

import com.moontweaker.script.ScriptManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.ForgeRegistry;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;

import java.util.List;

public final class RecipeApi {

    private RecipeApi() {}

    public static void install(Globals g, Logger logger, ScriptManager manager) {
        g.set("item", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return stackTable(arg.checkjstring());
            }
        });

        LuaValue recipes = LuaValue.tableOf();
        recipes.set("remove", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                int removed = remove(arg, logger);
                logger.info("MoonTweaker: removed {} recipes", removed);
                return LuaValue.valueOf(removed);
            }
        });
        recipes.set("shaped", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addShaped(args, manager);
                return LuaValue.TRUE;
            }
        });
        recipes.set("shapeless", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addShapeless(args, manager);
                return LuaValue.TRUE;
            }
        });
        recipes.set("smelting", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                ItemStack input = parseStack(args.arg(1));
                ItemStack output = parseStack(args.arg(2));
                FurnaceRecipes.instance().addSmeltingRecipe(input, output, (float) args.arg(3).optdouble(0.0));
                return LuaValue.TRUE;
            }
        });
        g.set("recipes", recipes);
    }

    private static int remove(LuaValue arg, Logger logger) {
        String id = null;
        ItemStack output = null;
        if (arg.isstring()) {
            id = null;
            output = parseStack(arg.tojstring());
        } else if (arg.istable()) {
            LuaValue idVal = arg.get("id");
            if (!idVal.isnil()) id = idVal.tojstring();
            LuaValue outputVal = arg.get("output");
            if (!outputVal.isnil()) output = parseStack(outputVal);
            if (id == null && output == null) throw new LuaError("remove() needs 'id' or 'output'");
        } else {
            throw new LuaError("remove() expects item id or table");
        }

        List<ResourceLocation> targets = new java.util.ArrayList<>();
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValues()) {
            boolean match = false;
            if (id != null && recipe.getRegistryName().toString().equals(id)) {
                match = true;
            } else if (output != null && sameItem(recipe.getRecipeOutput(), output)) {
                match = true;
            }
            if (match) {
                targets.add(recipe.getRegistryName());
            }
        }

        ForgeRegistry<IRecipe> registry = (ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES;
        int removed = 0;
        for (ResourceLocation loc : targets) {
            try {
                if (registry.remove(loc) != null) removed++;
            } catch (Exception e) {
                logger.warn("MoonTweaker: cannot remove recipe {}: {}", loc, e.getMessage());
            }
        }
        return removed;
    }

    private static void addShaped(Varargs args, ScriptManager manager) {
        ItemStack output = parseStack(args.arg(1));
        LuaValue pattern = args.arg(2).checktable();
        LuaValue keys = args.arg(3).checktable();
        List<String> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= pattern.length(); i++) {
            rows.add(pattern.get(i).checkjstring());
        }
        if (rows.isEmpty()) throw new LuaError("shaped(): empty pattern");
        int width = 0;
        for (String row : rows) {
            if (row.length() > width) width = row.length();
        }
        int height = rows.size();
        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int row = 0; row < height; row++) {
            String rowStr = rows.get(row);
            for (int col = 0; col < width; col++) {
                char ch = col < rowStr.length() ? rowStr.charAt(col) : ' ';
                if (ch == ' ') continue;
                LuaValue key = keys.get(String.valueOf(ch));
                if (key.isnil()) throw new LuaError("shaped(): missing key '" + ch + "'");
                ingredients.set(row * width + col, parseIngredient(key));
            }
        }
        ShapedRecipes recipe = new ShapedRecipes("", width, height, ingredients, output);
        recipe.setRegistryName(new ResourceLocation("moontweaker", manager.nextRecipeId()));
        ForgeRegistries.RECIPES.register(recipe);
    }

    private static void addShapeless(Varargs args, ScriptManager manager) {
        ItemStack output = parseStack(args.arg(1));
        LuaValue list = args.arg(2).checktable();
        if (list.length() == 0) throw new LuaError("shapeless(): empty ingredient list");
        NonNullList<Ingredient> ingredients = NonNullList.withSize(list.length(), Ingredient.EMPTY);
        for (int i = 1; i <= list.length(); i++) {
            ingredients.set(i - 1, parseIngredient(list.get(i)));
        }
        ShapelessRecipes recipe = new ShapelessRecipes("", output, ingredients);
        recipe.setRegistryName(new ResourceLocation("moontweaker", manager.nextRecipeId()));
        ForgeRegistries.RECIPES.register(recipe);
    }

    private static LuaValue stackTable(String id) {
        String[] parts = id.split(":");
        if (parts.length < 2) throw new LuaError("bad item id '" + id + "'");
        if (ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[0], parts[1])) == null) {
            throw new LuaError("unknown item '" + id + "'");
        }
        LuaValue t = LuaValue.tableOf();
        t.set("id", LuaValue.valueOf(id));
        if (parts.length >= 3) {
            t.set("meta", LuaValue.valueOf(parseInt(parts[2], 0)));
        }
        t.set("count", LuaValue.valueOf(1));
        return t;
    }

    private static ItemStack parseStack(String id) {
        return parseStack(LuaValue.valueOf(id));
    }

    private static ItemStack parseStack(LuaValue v) {
        String id;
        int count = 1;
        int meta = 0;
        if (v.isstring()) {
            id = v.tojstring();
        } else if (v.istable()) {
            id = v.get("id").checkjstring();
            count = v.get("count").optint(1);
            meta = v.get("meta").optint(0);
        } else {
            throw new LuaError("expected item id or table");
        }

        String[] parts = id.split(":");
        if (parts.length < 2) throw new LuaError("bad item id '" + id + "'");
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(parts[0], parts[1]));
        if (item == null) throw new LuaError("unknown item '" + id + "'");
        if (parts.length >= 3) meta = parseInt(parts[2], meta);
        return new ItemStack(item, count, meta);
    }

    private static Ingredient parseIngredient(LuaValue v) {
        String id;
        if (v.isstring()) {
            id = v.tojstring();
        } else if (v.istable()) {
            id = v.get("id").checkjstring();
        } else {
            throw new LuaError("expected ingredient string");
        }
        if (id.startsWith("ore:")) return new OreIngredient(id.substring(4));
        return Ingredient.fromStacks(parseStack(id));
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        return b.getMetadata() == OreDictionary.WILDCARD_VALUE || a.getMetadata() == b.getMetadata();
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}