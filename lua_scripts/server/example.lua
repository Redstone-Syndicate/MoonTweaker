log.info("MoonTweaker example loaded")

-- items: "mod:item" or "mod:item:meta", ore dictionary: "ore:ingotIron"
-- recipes.remove("minecraft:stick")
-- recipes.remove({ output = "minecraft:stone" })
-- recipes.shaped("minecraft:diamond_block", { "###", "###", "###" }, { ["#"] = "minecraft:diamond" })
-- recipes.shapeless("minecraft:planks", { "minecraft:log" })
-- recipes.smelting("minecraft:cobblestone", "minecraft:stone", 0.1)

-- custom advancements (Triumph-style, defined in Lua, synced to clients):
-- advancement.add("mymod:root", {
--   display = {
--     icon = "minecraft:diamond",
--     title = "Diamonds!",
--     description = "Get a diamond",
--     frame = "task", -- task | goal | challenge
--     background = "minecraft:textures/gui/advancements/backgrounds/stone.png", -- root only
--     show_toast = true, hidden = false, announce_to_chat = true,
--   },
--   criteria = {
--     got_diamond = {
--       trigger = "minecraft:inventory_changed",
--       conditions = { items = { { item = "minecraft:diamond" } } },
--     },
--   },
--   requirements = { { "got_diamond" } }, -- optional, defaults to all criteria
-- })
-- advancement.add("mymod:child", { parent = "mymod:root", display = { icon = "minecraft:emerald", title = "More!", description = "...", frame = "goal" }, criteria = { got = { trigger = "minecraft:inventory_changed", conditions = { items = { { item = "minecraft:emerald" } } } } } })
-- advancement.remove("mymod:child")

-- Triumph-style vanilla removal (all of these write hidden tombstone files):
-- one advancement only:
-- advancement.removeVanillaAdvancement("minecraft:story/root")
-- one whole tree (root + children):
-- advancement.removeVanillaTree("minecraft:nether/root")
-- every vanilla advancement, then build your own trees from scratch:
-- advancement.removeAllVanilla()

local cfg = require("config.example")
log.info("example config value: " .. tostring(cfg.value))
