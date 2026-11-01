package com.moontweaker;

import com.moontweaker.advancement.AdvancementApi;
import com.moontweaker.command.MoonTweakerCommand;
import com.moontweaker.event.GameplayEvents;
import com.moontweaker.event.RecipeRegistryHook;
import com.moontweaker.expansion.ExpansionApi;
import com.moontweaker.loot.LootTableHandler;
import com.moontweaker.registry.RegistryHandler;
import com.moontweaker.script.ScriptManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = MoonTweaker.MODID,
    name = MoonTweaker.NAME,
    version = MoonTweaker.VERSION,
    dependencies = "required-after:forgelin"
)
public class MoonTweaker {

    public static final String MODID = "moontweaker";
    public static final String NAME = "MoonTweaker";
    public static final String VERSION = "0.1.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static final ScriptManager SCRIPTS = new ScriptManager(LOGGER);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        SCRIPTS.loadRegistry();
        MinecraftForge.EVENT_BUS.register(new RegistryHandler(LOGGER));
        MinecraftForge.EVENT_BUS.register(new LootTableHandler(LOGGER));
        MinecraftForge.EVENT_BUS.register(new RecipeRegistryHook(SCRIPTS));
        MinecraftForge.EVENT_BUS.register(new GameplayEvents());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        SCRIPTS.loadStartup();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        ExpansionApi.apply(LOGGER);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new MoonTweakerCommand(SCRIPTS));
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        AdvancementApi.apply(net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance(), LOGGER);
    }
}