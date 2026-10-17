package cn.blockforge.generated.buffinsight;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import org.lwjgl.glfw.GLFW;

public final class ClientBootstrap {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.buff_insight.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.buff_insight"
    );

    private ClientBootstrap() {
    }

    public static void initialize(IEventBus modBus) {
        BuffInsightConfig.load();
        BuffAnalysisService.loadCache();
        // 注册到 Forge 模组菜单的「配置」按钮
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new BuffInsightConfigScreen(parent)));
        modBus.addListener(ClientBootstrap::registerKeys);
        MinecraftForge.EVENT_BUS.register(ClientBootstrap.class);
        MinecraftForge.EVENT_BUS.register(EffectTracker.class);
        MinecraftForge.EVENT_BUS.register(BuffHudRenderer.class);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_CONFIG.consumeClick()) {
            minecraft.setScreen(new BuffInsightConfigScreen(minecraft.screen));
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        EffectTracker.reset();
        EffectTracker.attachPacketListener(event.getConnection());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EffectTracker.detachPacketListener(event.getConnection());
    }

    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        markLocalPlayerDirty(event.getEntity() instanceof LocalPlayer);
    }

    @SubscribeEvent
    public static void onEffectRemoved(MobEffectEvent.Remove event) {
        markLocalPlayerDirty(event.getEntity() instanceof LocalPlayer);
    }

    private static void markLocalPlayerDirty(boolean local) {
        if (local) EffectTracker.markDirty();
    }
}
