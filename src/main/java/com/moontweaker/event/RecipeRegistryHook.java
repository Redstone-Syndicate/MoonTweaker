package com.moontweaker.event;

import com.moontweaker.script.ScriptManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class RecipeRegistryHook {

    private final ScriptManager scripts;

    public RecipeRegistryHook(ScriptManager scripts) {
        this.scripts = scripts;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onRegisterRecipes(RegistryEvent.Register<IRecipe> event) {
        scripts.loadServer();
    }
}