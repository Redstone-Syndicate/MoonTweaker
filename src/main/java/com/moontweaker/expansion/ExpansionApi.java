package com.moontweaker.expansion;

import com.moontweaker.api.MoonTweakerApi;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.oredict.OreDictionary;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ExpansionApi {

    private static final String THERMAL_MODID = "thermalexpansion";
    private static final String ENDERIO_MODID = "enderio";
    private static final String IE_MODID = "immersiveengineering";

    private static final String MEKANISM_MODID = "mekanism";
    private static final String MEKANISM_RECIPE_HANDLER = "mekanism.common.recipe.RecipeHandler";

    private static final String PULVERIZER = "cofh.thermalexpansion.util.managers.machine.PulverizerManager";
    private static final String SMELTER = "cofh.thermalexpansion.util.managers.machine.SmelterManager";
    private static final String CRUCIBLE = "cofh.thermalexpansion.util.managers.machine.CrucibleManager";

    private static final String SAG_MILL = "crazypants.enderio.base.recipe.sagmill.SagMillRecipeManager";
    private static final String ALLOY_SMELTER = "crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager";
    private static final String EIO_RECIPE = "crazypants.enderio.base.recipe.Recipe";
    private static final String EIO_RECIPE_INPUT = "crazypants.enderio.base.recipe.RecipeInput";
    private static final String EIO_RECIPE_INPUT_INTERFACE = "crazypants.enderio.base.recipe.IRecipeInput";
    private static final String EIO_RECIPE_OUTPUT = "crazypants.enderio.base.recipe.RecipeOutput";
    private static final String EIO_BONUS = "crazypants.enderio.base.recipe.RecipeBonusType";
    private static final String EIO_LEVEL = "crazypants.enderio.base.recipe.RecipeLevel";
    private static final String EIO_LIST = "com.enderio.core.common.util.NNList";

    private static final String IE_CRUSHER = "blusunrize.immersiveengineering.api.crafting.CrusherRecipe";
    private static final String IE_BLAST = "blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe";
    private static final String IE_ARC = "blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe";

    private static class Op {
        final String description;
        final Runnable action;
        Op(String description, Runnable action) {
            this.description = description;
            this.action = action;
        }
    }

    private static final List<Op> ops = new ArrayList<>();
    private static boolean applied = false;

    public static void reset() {
        ops.clear();
        applied = false;
    }

    public static void apply(Logger logger) {
        if (ops.isEmpty()) {
            applied = true;
            return;
        }
        int count = 0;
        for (Op op : ops) {
            try {
                op.action.run();
                count++;
            } catch (Throwable e) {
                logger.error("MoonTweaker: failed to add {}: {}", op.description, rootMessage(e));
            }
        }
        ops.clear();
        applied = true;
        logger.info("MoonTweaker: applied {} machine recipes", count);
    }

    public static void install(Globals g) {
        boolean thermalLoaded = Loader.isModLoaded(THERMAL_MODID);
        boolean enderioLoaded = Loader.isModLoaded(ENDERIO_MODID);
        boolean ieLoaded = Loader.isModLoaded(IE_MODID);

        LuaValue root = LuaValue.tableOf();

        LuaValue mods = LuaValue.tableOf();
        mods.set("thermal", LuaValue.valueOf(thermalLoaded));
        mods.set("enderio", LuaValue.valueOf(enderioLoaded));
        mods.set("ie", LuaValue.valueOf(ieLoaded));
        root.set("mods", mods);

        LuaValue thermal = LuaValue.tableOf();
        thermal.set("pulverizer", machine(thermalLoaded, "Thermal Expansion", args -> {
            ItemStack output = stack(args.arg(1));
            List<ItemStack> inputs = inputStacks(args.arg(2), "thermal.pulverizer");
            int energy = energy(args.arg(3), 3000);
            ItemStack secondary = args.arg(4).isnil() ? null : stack(args.arg(4));
            int chance = chance(args.arg(5));
            queue("thermal pulverizer " + name(output), () -> thermalPulverizer(output, inputs, energy, secondary, chance));
        }));
        thermal.set("smelter", machine(thermalLoaded, "Thermal Expansion", args -> {
            ItemStack output = stack(args.arg(1));
            List<ItemStack> primary = inputStacks(args.arg(2), "thermal.smelter");
            if (!args.arg(3).isstring()) throw new LuaError("thermal.smelter needs a secondary input");
            List<ItemStack> secondary = inputStacks(args.arg(3), "thermal.smelter");
            int index = 4;
            int energy = energy(args.arg(index++), 4000);
            ItemStack secondaryOutput = args.arg(index).isnil() ? null : stack(args.arg(index));
            int chance = chance(args.arg(index + 1));
            queue("thermal smelter " + name(output), () -> thermalSmelter(output, primary, secondary, energy, secondaryOutput, chance));
        }));
        thermal.set("crucible", machine(thermalLoaded, "Thermal Expansion", args -> {
            FluidStack output = fluid(args.arg(1));
            List<ItemStack> inputs = inputStacks(args.arg(2), "thermal.crucible");
            int energy = energy(args.arg(3), 2000);
            queue("thermal crucible " + name(output), () -> thermalCrucible(output, inputs, energy));
        }));
        root.set("thermal", thermal);

        LuaValue enderio = LuaValue.tableOf();
        enderio.set("sag_mill", machine(enderioLoaded, "Ender IO", args -> {
            ItemStack output = stack(args.arg(1));
            List<ItemStack> inputs = inputStacks(args.arg(2), "enderio.sag_mill");
            int energy = energy(args.arg(3), 240);
            ItemStack secondary = args.arg(4).isnil() ? null : stack(args.arg(4));
            int chance = chance(args.arg(5));
            queue("enderio sag mill " + name(output), () -> eioSagMill(output, inputs, energy, secondary, chance));
        }));
        enderio.set("alloy_smelter", machine(enderioLoaded, "Ender IO", args -> {
            ItemStack output = stack(args.arg(1));
            List<ItemStack> first = inputStacks(args.arg(2), "enderio.alloy_smelter");
            int index = 3;
            List<ItemStack> second = args.arg(index).isstring() ? inputStacks(args.arg(index++), "enderio.alloy_smelter") : Collections.emptyList();
            int energy = energy(args.arg(index), 2400);
            queue("enderio alloy smelter " + name(output), () -> eioAlloySmelter(output, Arrays.asList(first, second), energy));
        }));
        root.set("enderio", enderio);

        LuaValue ie = LuaValue.tableOf();
        ie.set("crusher", machine(ieLoaded, "Immersive Engineering", args -> {
            ItemStack output = stack(args.arg(1));
            Object input = inputObject(args.arg(2), "ie.crusher");
            int energy = energy(args.arg(3), 3000);
            queue("ie crusher " + name(output), () -> ieCrusher(output, input, energy));
        }));
        ie.set("blast_furnace", machine(ieLoaded, "Immersive Engineering", args -> {
            ItemStack output = stack(args.arg(1));
            Object input = inputObject(args.arg(2), "ie.blast_furnace");
            int time = energy(args.arg(3), 1200);
            ItemStack slag = args.arg(4).isnil() ? ItemStack.EMPTY : stack(args.arg(4));
            queue("ie blast furnace " + name(output), () -> ieBlastFurnace(output, input, time, slag));
        }));
        ie.set("arc_furnace", machine(ieLoaded, "Immersive Engineering", args -> {
            ItemStack output = stack(args.arg(1));
            Object input = inputObject(args.arg(2), "ie.arc_furnace");
            int time = energy(args.arg(3), 200);
            int energyPerTick = energy(args.arg(4), 100);
            ItemStack slag = args.arg(5).isnil() ? ItemStack.EMPTY : stack(args.arg(5));
            queue("ie arc furnace " + name(output), () -> ieArcFurnace(output, input, time, energyPerTick, slag));
        }));
        root.set("ie", ie);

        boolean mekanismLoaded = Loader.isModLoaded(MEKANISM_MODID);
        LuaValue mekanism = LuaValue.tableOf();

        mekanism.set("crusher", machine(mekanismLoaded, "Mekanism", args -> {
            ItemStack output = stack(args.arg(1));
            List<ItemStack> inputs = inputStacks(args.arg(2), "mekanism.crusher");
            queue("mekanism crusher " + name(output), () -> mekanismCrusher(output, inputs));
        }));

        mekanism.set("enrichment_chamber", machine(mekanismLoaded, "Mekanism", args -> {
            ItemStack output = stack(args.arg(1));
            List<ItemStack> inputs = inputStacks(args.arg(2), "mekanism.enrichment_chamber");
            queue("mekanism enrichment chamber " + name(output), () -> mekanismEnrichmentChamber(output, inputs));
        }));

        mekanism.set("smelter", machine(mekanismLoaded, "Mekanism", args -> {
            ItemStack output = stack(args.arg(1));
            List<ItemStack> inputs = inputStacks(args.arg(2), "mekanism.smelter");
            queue("mekanism smelter " + name(output), () -> mekanismSmelter(output, inputs));
        }));

        root.set("mekanism", mekanism);

        g.set("moonexpansion", root);
    }

    private interface MachineHandler {
        void handle(Varargs args);
    }

    private static LuaValue machine(boolean loaded, String modName, MachineHandler handler) {
        LuaValue table = LuaValue.tableOf();
        table.set("add_recipe", recipe(loaded, modName, handler));
        return table;
    }

    private static LuaValue recipe(boolean loaded, String modName, MachineHandler handler) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                if ("registry".equals(MoonTweakerApi.currentStage)) {
                    throw new LuaError("moonexpansion: use lua_scripts/server/ or lua_scripts/startup/");
                }
                if (applied) {
                    throw new LuaError("moonexpansion: machine recipes are applied at startup; restart the game");
                }
                if (!loaded) throw new LuaError("MoonTweaker: " + modName + " is not installed");
                handler.handle(args);
                return LuaValue.NIL;
            }
        };
    }

    private static void queue(String description, Runnable action) {
        ops.add(new Op(description, action));
    }

    private static void thermalPulverizer(ItemStack output, List<ItemStack> inputs, int energy, ItemStack secondary, int chance) {
        for (ItemStack input : inputs) {
            Object result;
            if (secondary == null) {
                result = invokeStatic(PULVERIZER, "addRecipe", energy, input, output);
            } else {
                result = invokeStatic(PULVERIZER, "addRecipe", energy, input, output, secondary, chance);
            }
            checkAdded(result, "thermal pulverizer " + name(input));
        }
    }

    private static void thermalSmelter(ItemStack output, List<ItemStack> primaryInputs, List<ItemStack> secondaryInputs, int energy, ItemStack secondaryOutput, int chance) {
        for (ItemStack primary : primaryInputs) {
            for (ItemStack secondary : secondaryInputs) {
                Object result = invokeStatic(SMELTER, "addRecipe", energy, primary, secondary, output, secondaryOutput != null ? secondaryOutput : ItemStack.EMPTY, chance);
                checkAdded(result, "thermal smelter " + name(primary) + " + " + name(secondary));
            }
        }
    }

    private static void thermalCrucible(FluidStack output, List<ItemStack> inputs, int energy) {
        for (ItemStack input : inputs) {
            Object result = invokeStatic(CRUCIBLE, "addRecipe", energy, input, output);
            checkAdded(result, "thermal crucible " + name(input));
        }
    }

    private static void checkAdded(Object result, String description) {
        if (result == null) {
            throw new IllegalStateException(description + ": recipe already exists or arguments are invalid");
        }
    }

    private static void eioSagMill(ItemStack output, List<ItemStack> inputs, int energy, ItemStack secondary, int chance) {
        try {
            Class<?> recipeInputClass = Class.forName(EIO_RECIPE_INPUT);
            Class<?> recipeInputInterface = Class.forName(EIO_RECIPE_INPUT_INTERFACE);
            Class<?> recipeOutputClass = Class.forName(EIO_RECIPE_OUTPUT);
            Class<?> recipeClass = Class.forName(EIO_RECIPE);
            Class<?> bonusClass = Class.forName(EIO_BONUS);
            Class<?> levelClass = Class.forName(EIO_LEVEL);
            Object bonus;
            Object level;
            try {
                bonus = bonusClass.getField("CHANCE_ONLY").get(null);
                level = levelClass.getField("IGNORE").get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            for (ItemStack input : inputs) {
                Object recipeInput = recipeInputClass.getConstructor(ItemStack.class).newInstance(input);
                Object[] outputs = (Object[]) Array.newInstance(recipeOutputClass, secondary == null ? 1 : 2);

                Array.set(outputs, 0,
                    recipeOutputClass.getConstructor(ItemStack.class, Float.TYPE, Float.TYPE)
                        .newInstance(output, 1.0f, 0.0f)
                );
                if (secondary != null) {
                    Array.set(outputs, 1,
                        recipeOutputClass.getConstructor(ItemStack.class, Float.TYPE, Float.TYPE)
                            .newInstance(secondary, chance / 100.0f, 0.0f)
                    );
                }

                Object recipe = recipeClass
                    .getConstructor(recipeInputInterface, Integer.TYPE, bonusClass, levelClass, outputs.getClass())
                    .newInstance(recipeInput, energy, bonus, level, outputs);
                Object manager = Class.forName(SAG_MILL).getMethod("getInstance").invoke(null);
                if (manager == null) throw new IllegalStateException("SagMillRecipeManager unavailable");
                manager.getClass().getMethod("addRecipe", recipeClass).invoke(manager, recipe);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Ender IO classes not found", e);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void mekanismCrusher(ItemStack output, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            invokeStatic(MEKANISM_RECIPE_HANDLER, "addCrusherRecipe", input, output);
        }
    }

    private static void mekanismEnrichmentChamber(ItemStack output, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            invokeStatic(MEKANISM_RECIPE_HANDLER, "addEnrichmentChamberRecipe", input, output);
        }
    }

    private static void mekanismSmelter(ItemStack output, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            // Coba panggil addSmelterRecipe; jika gagal, fallback ke furnace vanilla
            try {
                invokeStatic(MEKANISM_RECIPE_HANDLER, "addSmelterRecipe", input, output);
            } catch (Exception e) {
                // Fallback: daftarkan sebagai resep furnace vanilla
                FurnaceRecipes.instance().addSmeltingRecipe(input, output, 0.0F);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void eioAlloySmelter(ItemStack output, List<List<ItemStack>> slots, int energy) {
        try {
            Class<?> recipeInputClass = Class.forName(EIO_RECIPE_INPUT);
            Class<?> levelClass = Class.forName(EIO_LEVEL);
            Class<?> listClass = Class.forName(EIO_LIST);
            Object level;
            try {
                level = levelClass.getField("IGNORE").get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            Object manager = Class.forName(ALLOY_SMELTER).getMethod("getInstance").invoke(null);
            if (manager == null) throw new IllegalStateException("AlloyRecipeManager unavailable");

            for (List<ItemStack> combination : combinations(slots)) {
                Object list = listClass.getConstructor().newInstance();
                for (int index = 0; index < combination.size(); index++) {
                    ItemStack stack = combination.get(index);
                    Object input = recipeInputClass.getConstructor(
                        ItemStack.class, boolean.class, Float.TYPE, Integer.TYPE
                    ).newInstance(stack, stack.getItemDamage() != OreDictionary.WILDCARD_VALUE, 1.0f, index);
                    ((List<Object>) list).add(input);
                }

                manager.getClass().getMethod(
                    "addRecipe", boolean.class, listClass, ItemStack.class, Integer.TYPE, Float.TYPE, levelClass
                ).invoke(manager, false, list, output, energy, 0.0f, level);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Ender IO classes not found", e);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    private static List<List<ItemStack>> combinations(List<List<ItemStack>> slots) {
        List<List<ItemStack>> present = new ArrayList<>();
        for (List<ItemStack> slot : slots) {
            if (!slot.isEmpty()) present.add(slot);
        }
        if (present.isEmpty()) return Collections.emptyList();
        List<List<ItemStack>> result = Collections.singletonList(Collections.emptyList());
        for (List<ItemStack> slot : present) {
            List<List<ItemStack>> next = new ArrayList<>();
            for (List<ItemStack> prefix : result) {
                for (ItemStack stack : slot) {
                    List<ItemStack> combined = new ArrayList<>(prefix);
                    combined.add(stack);
                    next.add(combined);
                }
            }
            result = next;
        }
        return result;
    }

    private static void ieCrusher(ItemStack output, Object input, int energy) {
        invokeStatic(IE_CRUSHER, "addRecipe", output, input, energy);
    }

    private static void ieBlastFurnace(ItemStack output, Object input, int time, ItemStack slag) {
        invokeStatic(IE_BLAST, "addRecipe", output, input, time, slag);
    }

    private static void ieArcFurnace(ItemStack output, Object input, int time, int energyPerTick, ItemStack slag) {
        invokeStatic(IE_ARC, "addRecipe", output, input, slag, time, energyPerTick, new Object[0]);
    }

    private static Object invokeStatic(String className, String methodName, Object... args) {
        try {
            Class<?> type = Class.forName(className);
            java.lang.reflect.Method method = null;
            for (java.lang.reflect.Method m : type.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    method = m;
                    break;
                }
            }
            if (method == null) {
                throw new IllegalStateException(className + "." + methodName + "/" + args.length + " not found");
            }
            try {
                return method.invoke(null, args);
            } catch (InvocationTargetException e) {
                Throwable target = e.getTargetException();
                if (target != null) {
                    if (target instanceof RuntimeException) throw (RuntimeException) target;
                    if (target instanceof Error) throw (Error) target;
                    throw new RuntimeException(target);
                }
                throw new RuntimeException(e);
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class not found: " + className, e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static ItemStack stack(LuaValue value) {
        String text = value.checkjstring().trim();
        String[] parts = text.split("\\*", 2);
        String id = parts[0].trim();
        int count = parts.length == 2
            ? parseInt(parts[1].trim(), () -> { throw new LuaError("bad count in '" + text + "'"); })
            : 1;
        if (count < 1 || count > 64) throw new LuaError("bad count " + count + " in '" + text + "'");

        String[] idParts = id.split(":");
        if (idParts.length < 2) throw new LuaError("bad item id '" + id + "'");
        net.minecraft.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(idParts[0], idParts[1]));
        if (item == null) throw new LuaError("unknown item '" + id + "'");
        int meta = idParts.length >= 3
            ? parseInt(idParts[2], () -> { throw new LuaError("bad metadata in '" + id + "'"); })
            : 0;
        return new ItemStack(item, count, meta);
    }

    private static List<ItemStack> inputStacks(LuaValue value, String label) {
        String text = value.checkjstring().trim();
        if (text.startsWith("ore:")) {
            String name = text.substring(4);
            List<ItemStack> ores = OreDictionary.getOres(name, false);
            if (ores.isEmpty()) throw new LuaError(label + ": unknown ore '" + name + "'");
            List<ItemStack> result = new ArrayList<>();
            for (ItemStack s : ores) result.add(new ItemStack(s.getItem(), 1, s.getMetadata()));
            return result;
        }
        return Collections.singletonList(stack(value));
    }

    private static Object inputObject(LuaValue value, String label) {
        String text = value.checkjstring().trim();
        if (text.startsWith("ore:")) {
            String name = text.substring(4);
            if (OreDictionary.getOres(name, false).isEmpty()) throw new LuaError(label + ": unknown ore '" + name + "'");
            return name;
        }
        return stack(value);
    }

    private static FluidStack fluid(LuaValue value) {
        String text = value.checkjstring().trim();
        if (!text.startsWith("fluid:")) throw new LuaError("expected fluid:<name> [* amount]");
        String[] parts = text.substring(6).split("\\*", 2);
        String name = parts[0].trim();
        int amount = parts.length == 2
            ? parseInt(parts[1].trim(), () -> { throw new LuaError("bad fluid amount in '" + text + "'"); })
            : 1000;
        if (amount < 1) throw new LuaError("bad fluid amount " + amount);
        FluidStack registered = FluidRegistry.getFluidStack(name, amount);
        if (registered == null) throw new LuaError("unknown fluid '" + name + "'");
        return registered;
    }

    private static int energy(LuaValue value, int defaultValue) {
        if (value.isnil()) return defaultValue;
        if (!value.isnumber()) throw new LuaError("expected a number, got '" + value.tojstring() + "'");
        int number = value.toint();
        if (number < 1) throw new LuaError("value must be at least 1");
        return number;
    }

    private static int chance(LuaValue value) {
        int number = value.optint(100);
        if (number < 0 || number > 100) throw new LuaError("chance must be between 0 and 100");
        return number;
    }

    private static String name(ItemStack stack) {
        return stack.getItem().getRegistryName() != null ? stack.getItem().getRegistryName().toString() : "unknown";
    }

    private static String name(FluidStack stack) {
        return stack.getFluid().getName();
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.toString();
    }

    private static int parseInt(String s, java.util.function.Supplier<LuaError> onFail) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw onFail.get();
        }
    }
}
