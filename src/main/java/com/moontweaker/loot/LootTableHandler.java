package com.moontweaker.loot;

import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.Logger;

public class LootTableHandler {

    private final Logger logger;

    public LootTableHandler(Logger logger) {
        this.logger = logger;
    }

    @SubscribeEvent
    public void onLootTableLoad(LootTableLoadEvent event) {
        int applied = LootApi.apply(event.getName().toString(), event.getTable(), logger);
        if (applied > 0) {
            logger.info("MoonTweaker: applied {} loot changes to {}", applied, event.getName());
        }
    }
}