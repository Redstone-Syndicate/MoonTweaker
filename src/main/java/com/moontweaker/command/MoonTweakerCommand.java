package com.moontweaker.command;

import com.moontweaker.MoonTweaker;
import com.moontweaker.advancement.AdvancementApi;
import com.moontweaker.registry.RegistryApi;
import com.moontweaker.script.ScriptManager;
import net.minecraft.block.Block;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

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
        return "/moontweaker <reload|errors|dump>";
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

    private int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        if (args.length == 1) {
            List<String> completions = new java.util.ArrayList<>();
            for (String cmd : Arrays.asList("reload", "errors", "dump")) {
                if (cmd.startsWith(args[0])) completions.add(cmd);
            }
            return completions;
        }
        if (args.length == 2 && "dump".equals(args[0])) {
            List<String> completions = new java.util.ArrayList<>();
            for (String type : Arrays.asList("items", "blocks", "fluids", "recipes", "ores", "registry")) {
                if (type.startsWith(args[1])) completions.add(type);
            }
            return completions;
        }
        return Collections.emptyList();
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