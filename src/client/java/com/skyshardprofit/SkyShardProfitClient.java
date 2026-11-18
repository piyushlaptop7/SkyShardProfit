package com.skyshardprofit;

import com.mojang.blaze3d.platform.InputConstants;
import com.skyshardprofit.data.ShardFusionService;
import com.skyshardprofit.gui.ShardProfitScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class SkyShardProfitClient implements ClientModInitializer {
    private static final ShardFusionService SERVICE = new ShardFusionService();

    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("skyshardprofit", "main")
    );

    private static final KeyMapping OPEN_KEY = KeyMappingHelper.registerKeyMapping(
            new KeyMapping(
                    "key.skyshardprofit.open",
                    InputConstants.Type.KEYSYM,
                    InputConstants.KEY_O,
                    CATEGORY
            )
    );

    @Override
    public void onInitializeClient() {
        SERVICE.start();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            SERVICE.tick();
            while (OPEN_KEY.consumeClick()) {
                if (client.player != null) {
                    Minecraft.getInstance().setScreen(new ShardProfitScreen(SERVICE));
                }
            }
        });
    }
}
