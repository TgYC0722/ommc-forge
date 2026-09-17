package com.plusls.ommc;

import com.mojang.logging.LogUtils;
import com.plusls.ommc.config.Configs;
import com.plusls.ommc.event.OmmcInitHandler;
import com.plusls.ommc.feature.highlightLavaSource.LavaFluidExtensions;
import com.plusls.ommc.gui.GuiConfigs;
import fi.dy.masa.malilib.event.InitializationHandler;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;

/**
 * OMMC 的 Forge 入口。
 *
 * <p>配置系统与配置界面都由 MaFgLib（malilib 的 Forge 移植）提供，所以这里只做一件事：
 * 把 {@link OmmcInitHandler} 登记给 malilib，剩下的注册时序由 malilib 安排 ——
 * 它用 mixin 挂在客户端构造完成处回调，因此回调时 malilib 自身必然已经就绪。
 */
@Mod(OhMyMinecraftClient.MOD_ID)
public class OhMyMinecraftClient {
    public static final String MOD_ID = "ommc";
    public static final String MOD_NAME = "Oh My Minecraft Client";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OhMyMinecraftClient() {
        if (FMLEnvironment.dist.isClient()) {
            InitializationHandler.getInstance().registerInitializationHandler(new OmmcInitHandler());
            LOGGER.info("OMMC (Forge) 已加载，配置界面与热键交由 MaFgLib 管理。");
        }
    }

    /**
     * 按「调试模式」配置项决定 OMMC 自己的日志级别。
     *
     * <p>与 Fabric 版 {@code Configs.init()} 里这段一一对应：
     * <pre>
     *     cm.setValueChangeCallback("debug", option -&gt;
     *             Configurator.setLevel(OhMyMinecraftClientReference.getModIdentifier(),
     *                                   Configs.debug ? Level.DEBUG : Level.INFO));
     * </pre>
     *
     * <p><b>注意这里用 {@link #LOGGER}{@code .getName()} 而不是硬编码的模组 id。</b>
     * Fabric 版的 logger 是 {@code LogManager.getLogger("ommc")}，名字就是模组 id；
     * 而本模组用的是 {@code LogUtils.getLogger()}，它的实现是
     * {@code LoggerFactory.getLogger(StackWalker.getCallerClass())} ——
     * 也就是按<b>调用者类名</b>取名，所以实际名字是
     * {@code com.plusls.ommc.OhMyMinecraftClient}。
     * 若照抄 Fabric 写死 {@code "ommc"}，{@code setLevel} 会作用在一个不存在的 logger 名上：
     * 开关看起来正常、不报任何错，但日志级别毫无变化，是个很难发现的坑。
     */
    public static void applyDebugLogLevel() {
        Configurator.setLevel(LOGGER.getName(),
                Configs.Generic.DEBUG.getBooleanValue() ? Level.DEBUG : Level.INFO);
    }

    /**
     * 客户端侧事件。
     */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientEvents {
        @SubscribeEvent
        public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            // 把 OMMC 的岩浆客户端渲染扩展装到 Forge 自带的 ForgeMod.LAVA_TYPE 上。
            // 只能在这里做：FluidType 的 renderProperties 是构造期写入的私有字段，
            // 而 LAVA_TYPE 早在 ForgeMod 类加载时就构造完了，没有参与注册的时机。
            LavaFluidExtensions.replace();
            LOGGER.info("OMMC (Forge) 客户端初始化完成。");
        }

        /**
         * 加载完成时把 Forge 实际识别到的模组列表打出来。
         * 移植期间用来快速确认本模组与 MaFgLib 是否都被 FML 注册。
         */
        @SubscribeEvent
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            StringBuilder sb = new StringBuilder();
            ModList.get().forEachModContainer((id, container) ->
                    sb.append(System.lineSeparator())
                      .append("  - ")
                      .append(id)
                      .append(" @ ")
                      .append(container.getModInfo().getVersion()));
            LOGGER.info("Forge 已加载 {} 个模组:{}", ModList.get().size(), sb);
        }
    }
}
