package cn.blockforge.generated.buffinsight;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;

@Mod(GeneratedMod.MOD_ID)
public final class GeneratedMod {
    public static final String MOD_ID = "buff_insight";

    public GeneratedMod() {
        // 纯客户端模组：不要求服务器安装本模组，允许加入任意服务器
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY,
                        (remoteVersion, isServer) -> true));
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientBootstrap.initialize(FMLJavaModLoadingContext.get().getModEventBus()));
    }
}
