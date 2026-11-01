---
id: machines
title: Machine Recipes
sidebar_position: 5
description: How MoonTweaker queues and applies Thermal Expansion, Ender IO and Immersive Engineering recipes through reflection.
---

# Machine recipes

Machine integration is deliberately loose: MoonTweaker does not compile against
Thermal Expansion, Ender IO or Immersive Engineering. It resolves their recipe
managers by class name at apply time and finds methods by name and parameter
count.

## Queue model

```java
private static class Op {
    final String description;
    final Runnable action;
}
private static final List<Op> ops = new ArrayList<>();
private static boolean applied = false;
```

Each Lua call:

1. Checks the stage (`registry/` rejected).
2. Checks `applied`.
3. Checks the mod is loaded.
4. Parses and validates arguments **immediately**.
5. Creates an `Op` whose `Runnable` performs the reflection later.

Argument errors therefore surface as script errors during load. Semantic errors
(duplicate Thermal input, missing class, changed overload) surface at
`FMLLoadCompleteEvent` and are logged per recipe.

## Apply

```java
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
```

- One `try` per operation.
- The final count is successful operations, not generated recipes. An ore
  input that expands to ten items still counts as one `Op`.
- `ops.clear()` and `applied = true` happen even if every operation fails.
- Empty queue: `applied = true`, no log line.

`reset()` clears `ops` and sets `applied = false`. It is called when `server/`
loads, including `/moontweaker reload`. Because `apply()` is only called at
`FMLLoadCompleteEvent`, operations queued after a reload are never applied.

## Stage gate

```java
if ("registry".equals(MoonTweakerApi.currentStage)) {
    throw new LuaError("moonexpansion: use lua_scripts/server/ or lua_scripts/startup/");
}
if (applied) {
    throw new LuaError("moonexpansion: machine recipes are applied at startup; restart the game");
}
if (!loaded) {
    throw new LuaError("MoonTweaker: " + modName + " is not installed");
}
```

The mod-loaded flags are computed once when `Globals` is built:

```java
boolean thermalLoaded = Loader.isModLoaded("thermalexpansion");
boolean enderioLoaded = Loader.isModLoaded("enderio");
boolean ieLoaded = Loader.isModLoaded("immersiveengineering");
```

They are exposed as `moonexpansion.mods.thermal`, `.enderio` and `.ie`.

## Argument parsing

### Item stacks

```java
private static ItemStack stack(LuaValue value) {
    String text = value.checkjstring().trim();
    String[] parts = text.split("\\*", 2);
    String id = parts[0].trim();
    int count = parts.length == 2 ? parseInt(parts[1].trim(), ...) : 1;
    if (count < 1 || count > 64) throw new LuaError("bad count " + count + " in '" + text + "'");
    ...
}
```

- String form only; machine APIs do not accept stack tables.
- `*` splits at the first occurrence.
- Count 1..64.
- Metadata is the third colon-separated part.
- Unknown item and bad metadata are errors.

### Ore inputs

```java
if (text.startsWith("ore:")) {
    String name = text.substring(4);
    List<ItemStack> ores = OreDictionary.getOres(name, false);
    if (ores.isEmpty()) throw new LuaError(label + ": unknown ore '" + name + "'");
    List<ItemStack> result = new ArrayList<>();
    for (ItemStack s : ores) result.add(new ItemStack(s.getItem(), 1, s.getMetadata()));
    return result;
}
```

- `getOres(name, false)` excludes wildcard entries.
- Each matching stack is copied with count **1**.
- A count cannot be attached to an `ore:` input; `ore:ingotIron * 3` is not
  parsed as a count and fails as an unknown ore name.
- An empty ore dictionary name is an error at queue time.

### Fluid output

```java
if (!text.startsWith("fluid:")) throw new LuaError("expected fluid:<name> [* amount]");
...
int amount = parts.length == 2 ? parseInt(...) : 1000;
if (amount < 1) throw new LuaError("bad fluid amount " + amount);
FluidStack registered = FluidRegistry.getFluidStack(name, amount);
if (registered == null) throw new LuaError("unknown fluid '" + name + "'");
```

Only the Thermal Crucible uses a fluid output. The name is resolved through
`FluidRegistry`.

### Numbers

```java
private static int energy(LuaValue value, int defaultValue) {
    if (value.isnil()) return defaultValue;
    if (!value.isnumber()) throw new LuaError("expected a number, got '...'");
    int number = value.toint();
    if (number < 1) throw new LuaError("value must be at least 1");
    return number;
}
```

- `toint()` truncates fractions.
- Values below 1 are rejected.
- `chance()` accepts 0..100, default 100.

## Input expansion

| Machine | Input handling | Generated recipes |
|---|---|---|
| Thermal Pulverizer | `inputStacks` | One per ore item |
| Thermal Smelter | `inputStacks` × 2 | Cartesian product |
| Thermal Crucible | `inputStacks` | One per ore item |
| EIO SAG Mill | `inputStacks` | One per ore item |
| EIO Alloy Smelter | up to two `inputStacks` slots | Cartesian product of non-empty slots |
| IE Crusher | `inputObject` | Ore stays a string dictionary reference |
| IE Blast Furnace | `inputObject` | Ore stays a string dictionary reference |
| IE Arc Furnace | `inputObject` | Ore stays a string dictionary reference |

The EIO alloy smelter uses `combinations(slots)`, which drops empty slots and
builds every combination:

```java
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
```

## Thermal Expansion

### Pulverizer

```java
if (secondary == null) {
    invokeStatic(PULVERIZER, "addRecipe", energy, input, output);
} else {
    invokeStatic(PULVERIZER, "addRecipe", energy, input, output, secondary, chance);
}
checkAdded(result, "thermal pulverizer " + name(input));
```

Class: `cofh.thermalexpansion.util.managers.machine.PulverizerManager`.

`checkAdded` treats a `null` return as failure:

```java
if (result == null) {
    throw new IllegalStateException(description + ": recipe already exists or arguments are invalid");
}
```

### Smelter

```java
for (ItemStack primary : primaryInputs) {
    for (ItemStack secondary : secondaryInputs) {
        Object result = invokeStatic(SMELTER, "addRecipe",
            energy, primary, secondary, output,
            secondaryOutput != null ? secondaryOutput : ItemStack.EMPTY,
            chance);
        checkAdded(result, "thermal smelter " + name(primary) + " + " + name(secondary));
    }
}
```

Class: `cofh.thermalexpansion.util.managers.machine.SmelterManager`.

The secondary input is mandatory at the Lua level:

```lua
if (!args.arg(3).isstring()) throw new LuaError("thermal.smelter needs a secondary input");
```

`secondaryOutput` defaults to `ItemStack.EMPTY` and `chance` defaults to 100.

### Crucible

```java
invokeStatic(CRUCIBLE, "addRecipe", energy, input, output);
```

Class: `cofh.thermalexpansion.util.managers.machine.CrucibleManager`.

Duplicate inputs are rejected by Thermal Expansion and surface as a `null`
return.

## Ender IO

### SAG Mill

EIO needs real objects, so the code uses constructors directly:

```java
Object recipeInput = recipeInputClass.getConstructor(ItemStack.class).newInstance(input);
Object[] outputs = (Object[]) Array.newInstance(recipeOutputClass, secondary == null ? 1 : 2);
Array.set(outputs, 0, recipeOutputClass
    .getConstructor(ItemStack.class, Float.TYPE, Float.TYPE)
    .newInstance(output, 1.0f, 0.0f));
if (secondary != null) {
    Array.set(outputs, 1, recipeOutputClass
        .getConstructor(ItemStack.class, Float.TYPE, Float.TYPE)
        .newInstance(secondary, chance / 100.0f, 0.0f));
}
Object recipe = recipeClass
    .getConstructor(recipeInputInterface, Integer.TYPE, bonusClass, levelClass, outputs.getClass())
    .newInstance(recipeInput, energy, bonus, level, outputs);
Object manager = Class.forName(SAG_MILL).getMethod("getInstance").invoke(null);
manager.getClass().getMethod("addRecipe", recipeClass).invoke(manager, recipe);
```

Details:

- Bonus type: `RecipeBonusType.CHANCE_ONLY`.
- Recipe level: `RecipeLevel.IGNORE`.
- Primary output chance: `1.0`.
- Secondary output chance: `chance / 100.0f`.
- Manager obtained through static `getInstance()`.
- One recipe per expanded input.

### Alloy Smelter

```java
Object input = recipeInputClass.getConstructor(
    ItemStack.class, boolean.class, Float.TYPE, Integer.TYPE
).newInstance(stack, stack.getItemDamage() != OreDictionary.WILDCARD_VALUE, 1.0f, index);

manager.getClass().getMethod(
    "addRecipe", boolean.class, listClass, ItemStack.class,
    Integer.TYPE, Float.TYPE, levelClass
).invoke(manager, false, list, output, energy, 0.0f, level);
```

Details:

- Uses `com.enderio.core.common.util.NNList` as the input list.
- The boolean argument is `true` unless the stack metadata is the wildcard
  value.
- Float argument is `1.0f`; integer argument is the slot index.
- Energy is the fourth Lua argument; the float argument to `addRecipe` is `0.0f`.
- One recipe per combination of non-empty slots.

## Immersive Engineering

### Crusher

```java
invokeStatic(IE_CRUSHER, "addRecipe", output, input, energy);
```

Class: `blusunrize.immersiveengineering.api.crafting.CrusherRecipe`.

- `input` is an `ItemStack` or an ore dictionary `String`.
- Process time is fixed by IE; only RF cost is configurable.

### Blast Furnace

```java
invokeStatic(IE_BLAST, "addRecipe", output, input, time, slag);
```

Class: `blusunrize.immersiveengineering.api.crafting.BlastFurnaceRecipe`.

- `time` is in ticks, default 1200.
- `slag` defaults to `ItemStack.EMPTY`.

### Arc Furnace

```java
invokeStatic(IE_ARC, "addRecipe", output, input, slag, time, energyPerTick, new Object[0]);
```

Class: `blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe`.

- The last argument is an empty additives array.
- `time` defaults to 200 ticks.
- `energyPerTick` defaults to 100 RF/t.
- `slag` defaults to `ItemStack.EMPTY`.

## Method resolution

```java
private static Object invokeStatic(String className, String methodName, Object... args) {
    Class<?> type = Class.forName(className);
    Method method = null;
    for (Method m : type.getMethods()) {
        if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
            method = m;
            break;
        }
    }
    if (method == null) {
        throw new IllegalStateException(className + "." + methodName + "/" + args.length + " not found");
    }
    return method.invoke(null, args);
}
```

Properties:

- Matches by **name and parameter count only**.
- The first matching method in `getMethods()` order is used. If a mod adds an
  overload with the same count but different types, the wrong method may be
  selected.
- `InvocationTargetException` unwraps the target exception and rethrows it, so
  the log shows the mod's own error message.
- Class loading is deferred until apply time, so a missing optional mod does not
  break startup.

## Failure behaviour

| Failure | When | Result |
|---|---|---|
| Unknown item/ore/fluid | Queue time | Script error |
| Bad count/metadata | Queue time | Script error |
| Energy < 1 | Queue time | Script error |
| Mod not installed | Call time | `MoonTweaker: <mod> is not installed` |
| Call from `registry/` | Call time | Stage error |
| Call after apply | Call time | `machine recipes are applied at startup; restart the game` |
| Missing class | Apply time | Logged per recipe |
| Missing method/count | Apply time | Logged per recipe |
| Duplicate Thermal input | Apply time | Logged per recipe |
| Wrong overload | Apply time | Logged or silently wrong |

A single failure does not stop other recipes. The final log line counts
successful operations only.

## What comes next

The internals section ends here. The [API reference](/docs/api/data-types)
documents every Lua function, and [Guides](/docs/guides/advancement-trees)
provides complete examples.
