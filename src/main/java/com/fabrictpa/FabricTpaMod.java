package com.fabrictpa;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public final class FabricTpaMod implements ModInitializer {
    public static final String MOD_ID = "fabric_tpa";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static volatile HomeStorage homeStorage;

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            TpaCommands.register(dispatcher)
        );

        ServerLifecycleEvents.SERVER_STARTED.register(server -> homeStorage = HomeStorage.load(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> homeStorage = null);
    }

    public static HomeStorage getHomeStorage(MinecraftServer server) {
        HomeStorage storage = homeStorage;
        if (storage == null) {
            synchronized (FabricTpaMod.class) {
                storage = homeStorage;
                if (storage == null) {
                    storage = HomeStorage.load(server);
                    homeStorage = storage;
                }
            }
        }
        return storage;
    }
}
