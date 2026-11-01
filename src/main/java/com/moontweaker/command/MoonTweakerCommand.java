package com.moontweaker.command;

import com.moontweaker.MoonTweaker;
import com.moontweaker.advancement.AdvancementApi;
import com.moontweaker.registry.RegistryApi;
import com.moontweaker.script.ScriptManager;
import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.BaseLib;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.CoroutineLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.jse.JseMathLib;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MoonTweakerCommand implements ICommand {

    private final ScriptManager scripts;

    public MoonTweakerCommand(ScriptManager scripts) {
        this.scripts = scripts;
    }

    @Override
    public String getName() {
        return "moontweaker";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/moontweaker <reload|errors|dump|info|check|trace>";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("mt");
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            sender.sendMessage(new TextComponentString(getUsage(sender)));
            return;
        }
        switch (args[0]) {
            case "reload":
                scripts.reload();
                AdvancementApi.apply(server, MoonTweaker.LOGGER);
                sender.sendMessage(new TextComponentString(
                    "MoonTweaker reloaded: " + scripts.getLoadedCount() + " scripts, " + scripts.errors.size() + " errors"
                ));
                if (!scripts.errors.isEmpty()) {
                    sender.sendMessage(new TextComponentString("Run /moontweaker errors for details"));
                }
                break;
            case "errors":
                if (scripts.errors.isEmpty()) {
                    sender.sendMessage(new TextComponentString("No script errors"));
                } else {
                    for (ScriptManager.ScriptError err : scripts.errors) {
                        sender.sendMessage(new TextComponentString(err.file + ": " + err.message));
                    }
                }
                break;
            case "dump":
                handleDump(sender, args);
                break;
            case "info":
                handleInfo(sender, server, args);
                break;
            case "check":
                handleCheck(sender, args);
                break;
            case "trace":
                handleTrace(sender, args);
                break;
            default:
                sender.sendMessage(new TextComponentString(getUsage(sender)));
        }
    }

    private void handleDump(ICommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1] : "items";
        int limit = args.length > 2 ? parseInt(args[2], 50) : 50;

        switch (type.toLowerCase()) {
            case "items":
                dumpItems(sender, limit);
                break;
            case "blocks":
                dumpBlocks(sender, limit);
                break;
            case "fluids":
                dumpFluids(sender, limit);
                break;
            case "recipes":
                dumpRecipes(sender, limit);
                break;
            case "ores":
                dumpOres(sender, limit);
                break;
            case "registry":
                dumpRegistry(sender, limit);
                break;
            default:
                sender.sendMessage(new TextComponentString("Unknown dump type. Use: items, blocks, fluids, recipes, ores, registry"));
        }
    }

    private void dumpItems(ICommandSender sender, int limit) {
        sender.sendMessage(new TextComponentString("=== Items (max " + limit + ") ==="));
        int count = 0;
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (count++ >= limit) break;
            sender.sendMessage(new TextComponentString(
                ForgeRegistries.ITEMS.getKey(item).toString() + " (maxStack=" + new ItemStack(item).getMaxStackSize() + ")"
            ));
        }
        sender.sendMessage(new TextComponentString("Total: " + ForgeRegistries.ITEMS.getValues().size()));
    }

    private void dumpBlocks(ICommandSender sender, int limit) {
        sender.sendMessage(new TextComponentString("=== Blocks (max " + limit + ") ==="));
        int count = 0;
        for (Block block : ForgeRegistries.BLOCKS.getValues()) {
            if (count++ >= limit) break;
            sender.sendMessage(new TextComponentString(
                ForgeRegistries.BLOCKS.getKey(block).toString()
            ));
        }
        sender.sendMessage(new TextComponentString("Total: " + ForgeRegistries.BLOCKS.getValues().size()));
    }

    private void dumpFluids(ICommandSender sender, int limit) {
        sender.sendMessage(new TextComponentString("=== Fluids (max " + limit + ") ==="));
        int count = 0;
        for (String name : FluidRegistry.getRegisteredFluids().keySet()) {
            if (count++ >= limit) break;
            Fluid fluid = FluidRegistry.getFluid(name);
            sender.sendMessage(new TextComponentString(
                name + " (density=" + fluid.getDensity() + ")"
            ));
        }
        sender.sendMessage(new TextComponentString("Total: " + FluidRegistry.getRegisteredFluids().size()));
    }

    private void dumpRecipes(ICommandSender sender, int limit) {
        sender.sendMessage(new TextComponentString("=== Recipes (max " + limit + ") ==="));
        int count = 0;
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValues()) {
            if (count++ >= limit) break;
            ItemStack output = recipe.getRecipeOutput();
            String outStr = output.isEmpty() ? "none" : output.getItem().getRegistryName().toString() + " x" + output.getCount();
            sender.sendMessage(new TextComponentString(
                ForgeRegistries.RECIPES.getKey(recipe).toString() + " -> " + outStr
            ));
        }
        sender.sendMessage(new TextComponentString("Total: " + ForgeRegistries.RECIPES.getValues().size()));
    }

    private void dumpOres(ICommandSender sender, int limit) {
        sender.sendMessage(new TextComponentString("=== Ore Dictionary (max " + limit + ") ==="));
        int count = 0;
        for (String name : OreDictionary.getOreNames()) {
            if (count++ >= limit) break;
            List<ItemStack> stacks = OreDictionary.getOres(name);
            StringBuilder sb = new StringBuilder(name + " [");
            for (int i = 0; i < Math.min(stacks.size(), 5); i++) {
                if (i > 0) sb.append(", ");
                sb.append(stacks.get(i).getItem().getRegistryName().toString());
            }
            if (stacks.size() > 5) sb.append(", ...");
            sb.append("]");
            sender.sendMessage(new TextComponentString(sb.toString()));
        }
        sender.sendMessage(new TextComponentString("Total: " + OreDictionary.getOreNames().length));
    }

    private void dumpRegistry(ICommandSender sender, int limit) {
        sender.sendMessage(new TextComponentString("=== Pending Registry (from scripts) ==="));
        sender.sendMessage(new TextComponentString("Items: " + RegistryApi.pendingItems().size()));
        for (Item item : RegistryApi.pendingItems()) {
            sender.sendMessage(new TextComponentString("  " + item.getRegistryName()));
        }
        sender.sendMessage(new TextComponentString("Blocks: " + RegistryApi.pendingBlocks().size()));
        for (Block block : RegistryApi.pendingBlocks()) {
            sender.sendMessage(new TextComponentString("  " + block.getRegistryName()));
        }
    }

    private void handleInfo(ICommandSender sender, MinecraftServer server, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("/moontweaker info <held|player>"));
            return;
        }
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString("This command must be run by a player"));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;
        String sub = args[1].toLowerCase();
        switch (sub) {
            case "held":
                ItemStack held = player.getHeldItemMainhand();
                if (held.isEmpty()) {
                    sender.sendMessage(new TextComponentString("Tidak memegang item"));
                    return;
                }
                String id = held.getItem().getRegistryName() != null ? held.getItem().getRegistryName().toString() : "unknown";
                sender.sendMessage(new TextComponentString("=== Item di Tangan ==="));
                sender.sendMessage(new TextComponentString("ID: " + id));
                sender.sendMessage(new TextComponentString("Count: " + held.getCount()));
                sender.sendMessage(new TextComponentString("Meta/Damage: " + held.getMetadata()));
                sender.sendMessage(new TextComponentString("Max Stack: " + held.getMaxStackSize()));
                sender.sendMessage(new TextComponentString("Display Name: " + held.getDisplayName()));
                if (held.hasTagCompound()) {
                    NBTTagCompound tag = held.getTagCompound();
                    sender.sendMessage(new TextComponentString("NBT: " + tag.toString()));
                }
                // Ore dict
                int[] oreIds = OreDictionary.getOreIDs(held);
                if (oreIds.length > 0) {
                    StringBuilder sb = new StringBuilder("Ore Dict: ");
                    for (int i = 0; i < oreIds.length; i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(OreDictionary.getOreName(oreIds[i]));
                    }
                    sender.sendMessage(new TextComponentString(sb.toString()));
                }
                break;
            case "player":
                sender.sendMessage(new TextComponentString("=== Player Info ==="));
                sender.sendMessage(new TextComponentString("Name: " + player.getName()));
                sender.sendMessage(new TextComponentString("UUID: " + player.getUniqueID()));
                sender.sendMessage(new TextComponentString("Pos: " + (int)player.posX + ", " + (int)player.posY + ", " + (int)player.posZ));
                sender.sendMessage(new TextComponentString("Dim: " + player.dimension));
                sender.sendMessage(new TextComponentString("Health: " + player.getHealth() + "/" + player.getMaxHealth()));
                sender.sendMessage(new TextComponentString("Gamemode: " + player.interactionManager.getGameType().getName()));
                break;
            default:
                sender.sendMessage(new TextComponentString("Unknown info type: " + sub + ". Use: held, player"));
        }
    }

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void handleCheck(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("/moontweaker check <file.lua>"));
            return;
        }
        String fileName = args[1];
        File scriptDir = scripts.scriptDir();
        File file = new File(scriptDir, fileName);
        if (!file.isFile()) {
            sender.sendMessage(new TextComponentString("File not found: " + file.getAbsolutePath()));
            return;
        }
        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Globals g = new Globals();
            g.load(new org.luaj.vm2.lib.BaseLib());
            g.load(new org.luaj.vm2.lib.PackageLib());
            g.load(new org.luaj.vm2.lib.Bit32Lib());
            g.load(new org.luaj.vm2.lib.TableLib());
            g.load(new org.luaj.vm2.lib.StringLib());
            g.load(new org.luaj.vm2.lib.CoroutineLib());
            g.load(new org.luaj.vm2.lib.jse.JseMathLib());
            org.luaj.vm2.LoadState.install(g);
            org.luaj.vm2.compiler.LuaC.install(g);
            
            // Load and compile only - don't execute
            org.luaj.vm2.LuaValue chunk = g.load(content, "@" + fileName);
            sender.sendMessage(new TextComponentString("§aSyntax OK: " + fileName));
            sender.sendMessage(new TextComponentString("§7Chunk: " + chunk.tojstring()));
        } catch (org.luaj.vm2.LuaError e) {
            sender.sendMessage(new TextComponentString("§cSyntax Error: " + e.getMessage()));
        } catch (Exception e) {
            sender.sendMessage(new TextComponentString("§cError: " + e.getMessage()));
        }
    }

    private void handleTrace(ICommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(new TextComponentString("/moontweaker trace <on|off>"));
            return;
        }
        String mode = args[1].toLowerCase();
        if ("on".equals(mode)) {
            MoonTweaker.LOGGER.info("MoonTweaker: Trace mode enabled (verbose logging)");
            // Could set a static flag in ScriptManager
            sender.sendMessage(new TextComponentString("§aTrace mode ON"));
        } else if ("off".equals(mode)) {
            MoonTweaker.LOGGER.info("MoonTweaker: Trace mode disabled");
            sender.sendMessage(new TextComponentString("§7Trace mode OFF"));
        } else {
            sender.sendMessage(new TextComponentString("Usage: /moontweaker trace <on|off>"));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            List<String> completions = new java.util.ArrayList<>();
            for (String cmd : Arrays.asList("reload", "errors", "dump", "info")) {
                if (cmd.startsWith(args[0])) completions.add(cmd);
            }
            return completions;
        }
        if (args.length == 2) {
            if ("dump".equals(args[0])) {
                List<String> completions = new java.util.ArrayList<>();
                for (String type : Arrays.asList("items", "blocks", "fluids", "recipes", "ores", "registry")) {
                    if (type.startsWith(args[1])) completions.add(type);
                }
                return completions;
            }
            if ("info".equals(args[0])) {
                List<String> completions = new java.util.ArrayList<>();
                for (String type : Arrays.asList("held", "player")) {
                    if (type.startsWith(args[1])) completions.add(type);
                }
                return completions;
            }
            if ("trace".equals(args[0])) {
                List<String> completions = new java.util.ArrayList<>();
                for (String type : Arrays.asList("on", "off")) {
                    if (type.startsWith(args[1])) completions.add(type);
                }
                return completions;
            }
            if ("check".equals(args[0])) {
                // List .lua files in script dir
                File scriptDir = scripts.scriptDir();
                List<String> completions = new java.util.ArrayList<>();
                collectLuaFiles(scriptDir, completions, "");
                for (String type : completions) {
                    if (type.startsWith(args[1])) completions.add(type);
                }
                return completions;
            }
        }
        if (args.length == 3 && "check".equals(args[0])) {
            // Subdirectory completion
            File scriptDir = scripts.scriptDir();
            List<String> completions = new java.util.ArrayList<>();
            collectLuaFiles(scriptDir, completions, args[1] + "/");
            return completions;
        }
        return Collections.emptyList();
    }

    private void collectLuaFiles(File dir, List<String> out, String prefix) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isFile() && child.getName().endsWith(".lua")) {
                out.add(prefix + child.getName());
            } else if (child.isDirectory()) {
                collectLuaFiles(child, out, prefix + child.getName() + "/");
            }
        }
    }

    @Override
    public boolean isUsernameIndex(String[] args, int index) {
        return false;
    }

    @Override
    public int compareTo(ICommand other) {
        return getName().compareTo(other.getName());
    }
}