package com.moontweaker.lua;

import com.moontweaker.script.ScriptManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
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

    private static Logger logger;

    private RecipeApi() {}

    public static void install(Globals g, Logger log, ScriptManager manager) {
        logger = log;
        g.set("item", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                return stackTable(arg.checkjstring());
            }
        });

        LuaValue recipes = LuaValue.tableOf();
        
        // recipes.remove(id_or_output_or_table) - existing
        recipes.set("remove", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                int removed = remove(arg, logger);
                logger.info("MoonTweaker: removed {} recipes", removed);
                return LuaValue.valueOf(removed);
            }
        });

        // recipes.removeByType("minecraft:crafting_shaped") - NEW
        recipes.set("removeByType", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String typeId = arg.checkjstring();
                int removed = removeByType(typeId, logger);
                logger.info("MoonTweaker: removed {} recipes of type {}", removed, typeId);
                return LuaValue.valueOf(removed);
            }
        });

        // recipes.removeAll() - NEW
        recipes.set("removeAll", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                int removed = removeAll(logger);
                logger.info("MoonTweaker: removed {} ALL recipes", removed);
                return LuaValue.valueOf(removed);
            }
        });

        // recipes.removeSmelting(input) - NEW
        recipes.set("removeSmelting", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                int removed = removeSmelting(arg, logger);
                logger.info("MoonTweaker: removed {} smelting recipes", removed);
                return LuaValue.valueOf(removed);
            }
        });

        // recipes.shaped(output, pattern, keys) - supports ore: in keys
        recipes.set("shaped", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addShaped(args, manager);
                return LuaValue.TRUE;
            }
        });

        // recipes.shapeless(output, ingredients) - supports ore:
        recipes.set("shapeless", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                addShapeless(args, manager);
                return LuaValue.TRUE;
            }
        });

        // recipes.smelting(input, output, xp)
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

    // NEW: removeByType - remove recipes by type (e.g., "minecraft:crafting_shaped")
    private static int removeByType(String typeId, Logger logger) {
        List<ResourceLocation> targets = new java.util.ArrayList<>();
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValues()) {
            if (recipe.getRegistryName().toString().startsWith(typeId + ":") || 
                recipe.getRegistryName().getNamespace().equals(typeId.split(":")[0])) {
                // Check recipe type
                String recipeType = recipe.getRegistryName().toString();
                if (recipeType.contains(typeId) || typeId.equals("*")) {
                    targets.add(recipe.getRegistryName());
                }
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

    // NEW: removeAll - remove ALL custom recipes (moontweaker namespace)
    private static int removeAll(Logger logger) {
        List<ResourceLocation> targets = new java.util.ArrayList<>();
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValues()) {
            if ("moontweaker".equals(recipe.getRegistryName().getNamespace())) {
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

    // NEW: removeSmelting - remove furnace recipes by input
    private static int removeSmelting(LuaValue arg, Logger logger) {
        ItemStack input = parseStack(arg);
        FurnaceRecipes furnace = FurnaceRecipes.instance();
        int removed = 0;
        
        // Use reflection to access private smeltingList
        try {
            java.lang.reflect.Field field = FurnaceRecipes.class.getDeclaredField("smeltingList");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<ItemStack, ItemStack> smeltingList = (java.util.Map<ItemStack, ItemStack>) field.get(furnace);
            
            java.util.Iterator<java.util.Map.Entry<ItemStack, ItemStack>> it = smeltingList.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<ItemStack, ItemStack> entry = it.next();
                if (sameItem(entry.getKey(), input)) {
                    it.remove();
                    removed++;
                }
            }
        } catch (Exception e) {
            logger.warn("MoonTweaker: cannot remove smelting recipe: {}", e.getMessage());
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
        // Check if any ingredient is ore dict - use ShapedOreRecipe
        boolean hasOreDict = false;
        for (Ingredient ing : ingredients) {
            if (ing instanceof OreIngredient) {
                hasOreDict = true;
                break;
            }
        }
        
        IRecipe recipe;
        // ShapedRecipes supports OreIngredient in ingredients list since Ingredient is the base type
        recipe = new ShapedRecipes("", width, height, ingredients, output);
        recipe.setRegistryName(new ResourceLocation("moontweaker", manager.nextRecipeId()));
        
        registerRecipe(recipe);
    }

    private static void addShapeless(Varargs args, ScriptManager manager) {
        ItemStack output = parseStack(args.arg(1));
        LuaValue list = args.arg(2).checktable();
        if (list.length() == 0) throw new LuaError("shapeless(): empty ingredient list");
        NonNullList<Ingredient> ingredients = NonNullList.withSize(list.length(), Ingredient.EMPTY);
        for (int i = 1; i <= list.length(); i++) {
            ingredients.set(i - 1, parseIngredient(list.get(i)));
        }
        
        // ShapelessRecipes supports OreIngredient in ingredients list
        IRecipe recipe = new ShapelessRecipes("", output, ingredients);
        recipe.setRegistryName(new ResourceLocation("moontweaker", manager.nextRecipeId()));
        
        registerRecipe(recipe);
    }

    // NEW: Register with lock check
    private static void registerRecipe(IRecipe recipe) {
        ForgeRegistry<IRecipe> registry = (ForgeRegistry<IRecipe>) ForgeRegistries.RECIPES;
        if (registry.isLocked()) {
            throw new LuaError("Recipe registry is locked! Cannot add recipe '" + recipe.getRegistryName() + "' after registry freeze. Add recipes in 'registry' stage or use /moontweaker reload before registry locks.");
        }
        registry.register(recipe);
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
            // Also accept 'data' as alias for 'meta'
            if (v.get("data").isint()) meta = v.get("data").checkint();
        } else {
            throw new LuaError("expected item id or table");
        }

        // NEW: Validate count > 0
        if (count <= 0) throw new LuaError("item count must be positive, got " + count);

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

    // FIXED: sameItem - removed dead code (b.getMetadata() == WILDCARD_VALUE never true)
    private static boolean sameItem(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.getItem() != b.getItem()) return false;
        // Only compare metadata if not wildcard (-1)
        int metaB = b.getMetadata();
        return metaB == OreDictionary.WILDCARD_VALUE || a.getMetadata() == metaB;
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}