package com.xinian.KryptonHybrid;

import com.xinian.KryptonHybrid.command.KryptonStatsCommand;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

public class kryptonhybrid implements ModInitializer {
    public static final String MODID = "krypton_hybrid";

    @Override
    public void onInitialize() {
        KryptonFabricConfig.load();
        ZstdUtil.reloadDictionary();

        boolean isClient = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
        KryptonSharedBootstrap.run(isClient);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                KryptonStatsCommand.register(dispatcher));
    }
}
