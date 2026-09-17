package com.plusls.ommc.feature.realSneaking;

import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 真潜行：潜行时禁止上升或下降（无法走上/走下地毯、半砖、楼梯这类非满方块）。
 *
 * <p>原理是把玩家的 {@code maxUpStep}（最大台阶高度）压到近似 0 —— 这样原版就认为玩家
 * 迈不上任何台阶，于是潜行时既上不去也下不来。每一个 tick 重新求值，松开 Shift 立刻还原。
 *
 * <p><b>为什么在 tick 开始（START）而不是结束</b>：潜行状态与会用到 {@code maxUpStep}
 * 的移动判定都发生在 tick 过程中，必须在那之前设好。
 *
 * <p><b>与 Fabric 版的差异</b>：Fabric 版通过 fabric-api 的
 * {@code ClientTickEvents.START_CLIENT_TICK} 注册（见其 {@code RealSneakingEventHandler}），
 * Forge 没有该事件，这里改用 {@link TickEvent.PlayerTickEvent}。由此带来一个必须处理的点：
 * 玩家 tick 事件在<b>两侧都会触发</b>（服务端玩家也会），所以要显式过滤出客户端侧，
 * 否则会给服务端玩家也设置台阶高度。
 *
 * <p>其余逻辑与 Fabric 版逐行对应，包括用 static 字段保存原值（客户端只有一个本地玩家，
 * 语义上没有问题）。
 */
@Mod.EventBusSubscriber(modid = OhMyMinecraftClient.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RealSneakingHandler {

    /** 压到的台阶高度（近似 0，语义上「迈不上任何台阶」） */
    private static final float MIN_STEP_HEIGHT = 0.001f;

    /** 潜行前的原始台阶高度 */
    private static float prevStepHeight;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 只在 tick 开始处理，且只处理客户端侧的本地玩家
        if (event.phase != TickEvent.Phase.START || !event.player.level().isClientSide()) {
            return;
        }
        Player player = event.player;
        if (!(player instanceof LocalPlayer)) {
            return;
        }

        // 记录在被我们改小之前的真实值 —— 被压到 0.001 时不去覆盖它
        if (player.maxUpStep() - MIN_STEP_HEIGHT >= 0.00001) {
            prevStepHeight = player.maxUpStep();
        }

        if (Configs.FeatureToggle.REAL_SNEAKING.getBooleanValue() && player.isShiftKeyDown()) {
            player.setMaxUpStep(MIN_STEP_HEIGHT);
        } else {
            player.setMaxUpStep(prevStepHeight);
        }
    }
}
