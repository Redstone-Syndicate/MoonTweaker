## Priority 5 (Nice-to-have) Implementation Summary

### 14. Advancement rewards support ✅
```lua
advancement.add("mod:my_adv", {
    -- ... display, criteria ...
    rewards = {
        experience = 100,
        recipes = {
            "minecraft:iron_sword",
            "minecraft:iron_pickaxe"
        },
        loot = {
            "minecraft:entities/zombie",
            "minecraft:chests/simple_dungeon"
        },
        function = "mod:my_reward_function"
    }
})
```
- Supports: `experience`, `recipes` (array), `loot` (array), `function` (string)
- Rewards are validated and passed through to vanilla JSON

### 15. Custom ore dictionary registration ✅
```lua
-- Registry stage only (lua_scripts/registry/)
registry.oreDict("oreCopper", "minecraft:gold_ingot", 0)
registry.oreDict("oreTin", "minecraft:iron_ingot", 0)
```
- Registers items to Forge Ore Dictionary
- Supports metadata (3rd parameter, default 0)

### 16. Fuel values ✅
```lua
-- Registry stage only (lua_scripts/registry/)
registry.fuel("minecraft:coal", 1600, 0)      -- default 1600 ticks
registry.fuel("minecraft:stick", 100, 0)      -- sticks as fuel
registry.fuel("minecraft:blaze_rod", 2400, 0) -- longer burn
```
- Uses Forge's `FurnaceFuelBurnTimeEvent` for compatibility
- Supports metadata (3rd parameter, default 0)

### 17. AND/OR criteria requirements ✅
```lua
advancement.add("mod:test_and_or", {
    -- ... display ...
    criteria = {
        has_iron = { trigger = "minecraft:inventory_changed", ... },
        has_gold = { trigger = "minecraft:inventory_changed", ... },
        has_diamond = { trigger = "minecraft:inventory_changed", ... }
    },
    requirements = {
        first_tier = { "has_iron", "has_gold" },  -- AND group
        second_tier = { "has_diamond" }            -- OR with first_tier
    }
})
```
- Named groups converted to vanilla array-of-arrays format
- Inner arrays = AND, outer array = OR
- Validates criterion names exist

### 18. Batch API for bulk registration ✅
```lua
-- Registry stage only (lua_scripts/registry/)
registry.batch({
    copper_ingot = {
        type = "item",
        options = { maxStackSize = 64, creativeTab = "misc" }
    },
    tin_ingot = {
        type = "item",
        options = { maxStackSize = 64, creativeTab = "misc" }
    },
    copper_block = {
        type = "block",
        options = { material = "iron", hardness = 3.0, resistance = 10.0 }
    },
    copper_pickaxe = {
        type = "tool",
        material = "copper",
        options = { attackDamage = 2.0, maxDamage = 250 }
    },
    copper_armor = {
        type = "armor",
        options = { 
            material = "copper",
            helmet = true, chestplate = true, leggings = true, boots = true 
        }
    }
})
```
- Supports: item, block, tool, armor, fluid
- Requires material definitions for tools/armor
- Reduces boilerplate for bulk registration

---

### Test Files
- `run/lua_scripts/registry/test_priority5.lua` - Registry stage features
- `run/lua_scripts/server/test_priority5.lua` - Server/startup stage features

### Implementation Details
- **AdvancementApi.java**: Added rewards validation, requirements conversion (named groups → vanilla array-of-arrays)
- **RegistryApi.java**: Added `oreDict()`, `fuel()`, `batch()` methods + event handler for fuel
- **MoonTweaker.java**: Registers RegistryApi as event handler for fuel events