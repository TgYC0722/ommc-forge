package com.plusls.ommc.event;

import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import com.plusls.ommc.feature.sortInventory.SortInventoryUtil;
import com.plusls.ommc.gui.GuiConfigs;
import com.plusls.ommc.util.ClientRenderUtils;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * OMMC 的注册入口。
 *
 * <p>由 malilib 在客户端初始化完成时回调（它自己用 mixin 挂在
 * {@code MinecraftClient.<init>} 的返回处），所以这里拿到的 malilib 一定是已经就绪的。
 * 注册顺序照搬 malilib 自带的 {@code MaLiLibInitHandler}：
 * 配置 → 热键 → 热键回调。
 */
public class OmmcInitHandler implements IInitializationHandler {

    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(OhMyMinecraftClient.MOD_ID, new Configs());

        InputEventHandler.getKeybindManager().registerKeybindProvider(OmmcInputHandler.getInstance());

        Configs.Generic.OPEN_CONFIG_GUI.getKeybind().setCallback(new CallbackOpenConfigGui());
        Configs.Generic.SEND_LOOKING_AT_BLOCK_POS.getKeybind().setCallback(new CallbackSendLookingAtBlockPos());

        // 整理仓库的热键要在「打开着界面」时也能触发，所以用 GUI 上下文
        Configs.Generic.SORT_INVENTORY.getKeybind().setSettings(KeybindSettings.GUI);
        Configs.Generic.SORT_INVENTORY.getKeybind().setCallback(new CallbackSortInventory());

        // 调试模式：把 OMMC 自己的日志级别在 DEBUG / INFO 之间切换。
        // 这里要主动套用一次 —— malilib 只会在「值被改动」时回调，
        // 而配置文件里已经存着 true 的情况不会有任何改动，不主动套用就会漏掉。
        // Fabric 版把这一次放在 postDeserialize() 里（配置反序列化之后），时机等价。
        OhMyMinecraftClient.applyDebugLogLevel();
        Configs.Generic.DEBUG.setValueChangeCallback(config -> {
            OhMyMinecraftClient.applyDebugLogLevel();
            OhMyMinecraftClient.LOGGER.debug("set debug {}", config.getBooleanValue());
        });

        registerRenderRefreshCallbacks();
        registerIntegratedServerCallbacks();
    }

    /**
     * 给「改变渲染外观」的开关接上区块重建。
     *
     * <p>这类特性的效果是在<b>区块构建期</b>烘进顶点缓冲的，所以切换开关后必须让已构建的
     * 区块重画，否则玩家看不到变化。以岩浆高亮为例，贴图选择发生在
     * {@code IClientFluidTypeExtensions.getStillTexture(...)} 里，而它是由
     * {@code LiquidBlockRenderer.tesselate} 在构建区块时调用的 ——
     * 「每次渲染都实时查配置」并不成立，结果是缓存在区块里的。
     *
     * <p>表现就是：<b>开启后不重建则不生效；关闭后不重建则贴图不还原</b>
     * （后者实测踩到过 —— 必须退回主界面重新载入世界才会恢复原贴图）。
     *
     * <p>与 Fabric 版 {@code Configs.init()} 里的这段一一对应：
     * <pre>
     *     cm.setValueChangeCallback("highlightLavaSource", o -&gt; levelRenderer.allChanged());
     *     cm.setValueChangeCallback("worldEaterMineHelper",  o -&gt; levelRenderer.allChanged());
     * </pre>
     */
    private static void registerRenderRefreshCallbacks() {
        Configs.FeatureToggle.HIGHLIGHT_LAVA_SOURCE.setValueChangeCallback(config -> {
            OhMyMinecraftClient.LOGGER.debug("set highlightLavaSource {}", config.getBooleanValue());
            ClientRenderUtils.reloadAllChunks();
        });
        // 世吞挖矿助手尚未移植，但它同样依赖区块重建，先把回调接上（不影响任何现有行为）
        Configs.FeatureToggle.WORLD_EATER_MINE_HELPER.setValueChangeCallback(config -> {
            OhMyMinecraftClient.LOGGER.debug("set worldEaterMineHelper {}", config.getBooleanValue());
            ClientRenderUtils.reloadAllChunks();
        });
    }

    /**
     * 本地服务器开关：改动后即时应用到已运行的集成服务器，并打一条 debug 日志。
     *
     * <p>与 Fabric 版 {@code Configs.init()} 里这三段一一对应：
     * <pre>
     *     cm.setValueChangeCallback("onlineMode", option -&gt; {
     *         LOGGER.debug("set onlineMode {}", ...);
     *         if (Minecraft.getInstance().hasSingleplayerServer()) { ... }
     *     });
     * </pre>
     *
     * <p>{@code hasSingleplayerServer()} 为假（在多人服务器里）时什么也不做 ——
     * 这几个配置只对本地开的服有意义。
     */
    private static void registerIntegratedServerCallbacks() {
        Configs.AdvancedIntegratedServer.ONLINE_MODE.setValueChangeCallback(config -> {
            OhMyMinecraftClient.LOGGER.debug("set onlineMode {}", config.getBooleanValue());
            MinecraftServer server = withIntegratedServer();
            if (server != null) {
                server.setUsesAuthentication(config.getBooleanValue());
            }
        });

        Configs.AdvancedIntegratedServer.PVP.setValueChangeCallback(config -> {
            OhMyMinecraftClient.LOGGER.debug("set pvp {}", config.getBooleanValue());
            MinecraftServer server = withIntegratedServer();
            if (server != null) {
                server.setPvpAllowed(config.getBooleanValue());
            }
        });

        Configs.AdvancedIntegratedServer.FLIGHT.setValueChangeCallback(config -> {
            OhMyMinecraftClient.LOGGER.debug("set flight {}", config.getBooleanValue());
            MinecraftServer server = withIntegratedServer();
            if (server != null) {
                server.setFlightAllowed(config.getBooleanValue());
            }
        });
    }

    /** 取当前集成服务器；本地没开服时返回 {@code null}。 */
    private static MinecraftServer withIntegratedServer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer() ? mc.getSingleplayerServer() : null;
    }

    private static class CallbackOpenConfigGui implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            GuiBase.openGui(new GuiConfigs(null));
            return true;
        }
    }

    /**
     * 「发送当前注视的方块的坐标」热键：把准星指向的方块坐标显示到聊天框。
     *
     * <p>与 Fabric 版 {@code Configs.init()} 里这段一一对应：
     * <pre>
     *     sendLookingAtBlockPos.getKeybind().setCallback((keyAction, iKeybind) -&gt; {
     *         Entity cameraEntity = client.getCameraEntity();
     *         MultiPlayerGameMode manager = client.gameMode;
     *         if (cameraEntity != null &amp;&amp; manager != null) {
     *             HitResult hit = cameraEntity.pick(manager.getPickRange(), client.getFrameTime(), false);
     *             if (hit.getType() == HitResult.Type.BLOCK) {
     *                 BlockPos pos = ((BlockHitResult) hit).getBlockPos();
     *                 InfoUtil.sendChat(String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ()));
     *             }
     *         }
     *         return false;
     *     });
     * </pre>
     *
     * <p><b>三处刻意的选择</b>：
     * <ol>
     *   <li>用 {@code getCameraEntity()} 而不是 {@code player} —— 相机实体才是准星的主人，
     *       这样第三人称视角下取到的仍是准星正对的那个方块。</li>
     *   <li>用 {@code getPickRange()} 做射线检测 —— 就是生存 4.5 / 创造 5 格的那个距离，
     *       与玩家实际能够到的范围一致，不会读出「看得见但够不着」的方块。</li>
     *   <li>{@code pick} 的第三个参数传 {@code false}（不包含流体）—— 与原版一致，
     *       准星指在水上时不算命中方块。</li>
     * </ol>
     *
     * <p><b>与 Fabric 版的差异</b>：Fabric 用 magiclib 的 {@code InfoUtil.sendChat(String)}，
     * 而 Forge 端用的是 MaFgLib（malilib 的移植），没有 magiclib。
     * 这里改用 {@code Minecraft.getInstance().gui.getChat().addMessage(component)}
     * —— {@code ChatComponent.addMessage} 就是「往聊天框里追加一条消息」，与原版行为一致。
     *
     * <p><b>不要用 malilib 的 {@code InfoUtils.sendVanillaMessage}</b>：名字看着像「发原版消息」，
     * 但它内部是 {@code Gui.setOverlayMessage(component, false)}（字节码 {@code Gui.m_93063_}），
     * 也就是<b>物品栏上方那条覆盖提示</b>，几秒后自己消失，根本进不了聊天框。
     * {@code InfoUtils} 里其它的 {@code showInGameMessage} / {@code printActionbarMessage}
     * 走的又是 malilib 自己的 HUD 渲染，同样不行。
     *
     * <p>返回值取 {@code true}（按键已消费）。Fabric 那个 {@code false} 是 Fabric 键位 API 的语义，
     * 在 malilib 下会变成「继续传给下一个处理者」，与本项目其它回调保持一致改为已消费。
     */
    private static class CallbackSendLookingAtBlockPos implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Minecraft mc = Minecraft.getInstance();
            Entity cameraEntity = mc.getCameraEntity();
            MultiPlayerGameMode gameMode = mc.gameMode;

            if (cameraEntity == null || gameMode == null || mc.gui == null) {
                return true;
            }

            HitResult hitResult = cameraEntity.pick(gameMode.getPickRange(), mc.getFrameTime(), false);

            if (hitResult.getType() != HitResult.Type.BLOCK) {
                return true;
            }

            BlockPos lookPos = ((BlockHitResult) hitResult).getBlockPos();
            String message = String.format("[%d, %d, %d]", lookPos.getX(), lookPos.getY(), lookPos.getZ());

            // 直接往聊天框追加一条客户端消息
            mc.gui.getChat().addMessage(Component.literal(message));
            OhMyMinecraftClient.LOGGER.debug("注视方块坐标: {}", message);

            return true;
        }
    }

    /**
     * 「整理仓库」热键：重排鼠标所指的那一区物品。
     *
     * <p>与 Fabric 版 {@code Configs.init()} 里这段一一对应：
     * <pre>
     *     sortInventory.getKeybind().setSettings(KeybindSettings.GUI);
     *     sortInventory.getKeybind().setCallback((keyAction, iKeybind) -&gt; {
     *         Optional.ofNullable(SortInventoryUtil.sort()).ifPresent(Runnable::run);
     *         return false;
     *     });
     * </pre>
     *
     * <p>热键上下文用 {@link KeybindSettings#GUI}：整理本来就要在<b>容器界面打开着</b>的时候按，
     * 用默认的 IN_GAME 上下文按下去不会有任何反应。
     *
     * <p>{@code sort()} 返回的 Runnable 是「事后音效」，它同时承担「有没有实际改动」的反馈：
     * 排上号了放按钮点击音，没东西可整放发射器失败音。
     */
    private static class CallbackSortInventory implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            Runnable sound = SortInventoryUtil.sort();

            if (sound != null) {
                sound.run();
            }

            return true;
        }
    }
}
