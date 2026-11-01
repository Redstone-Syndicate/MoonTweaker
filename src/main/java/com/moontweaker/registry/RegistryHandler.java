package com.moontweaker.registry;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

import java.util.List;

public class RegistryHandler {

    private final Logger logger;

    public RegistryHandler(Logger logger) {
        this.logger = logger;
    }

    @SubscribeEvent
    public void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        List<Block> blocks = RegistryApi.pendingBlocks();
        for (Block block : blocks) {
            event.getRegistry().register(block);
        }
        if (!blocks.isEmpty()) {
            logger.info("MoonTweaker: registered {} blocks", blocks.size());
        }
    }

    @SubscribeEvent
    public void onRegisterItems(RegistryEvent.Register<Item> event) {
        List<Item> items = RegistryApi.pendingItems();
        for (Item item : items) {
            event.getRegistry().register(item);
        }
        if (!items.isEmpty()) {
            logger.info("MoonTweaker: registered {} items", items.size());
        }
    }
}