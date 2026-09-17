package com.plusls.ommc.mixin.feature.forceBreakingCooldown;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 强制添加破坏冷却：破掉一个方块后，强制加上 5 游戏刻的破坏冷却。
 *
 * <p>{@code destroyDelay} 是 {@link MultiPlayerGameMode} 的破坏冷却计时器，
 * {@code continueDestroyBlock} 开头会先检查它：大于 0 就递减并直接返回，
 * 跳过整个挖掘逻辑，归零前无法继续破坏。
 *
 * <p><b>注入点的选择（这里踩过一个会导致「永远挖不动」的坑，务必留意）</b>：
 *
 * <p>不能注入 {@code continueDestroyBlock} 的 RETURN！该方法的开头是
 * <pre>
 *      4: getfield destroyDelay
 *      8: ifle 23                  // destroyDelay &gt; 0，即冷却中
 *     13: destroyDelay - 1
 *     18: putfield destroyDelay
 *     21: iconst_1
 *     22: ireturn                 // 冷却期间它返回 true
 * </pre>
 * 也就是说它的返回值 {@code true} <b>同时表示「冷却中」和「破坏成功」</b>，无法区分。
 * 若按「返回 true 就设冷却」来注入，就会变成：冷却中 → 递减 → 返回 true → 又设回 5
 * → 永远归不了零 → <b>完全挖不动方块</b>。（这是实测踩到的真实故障。）
 *
 * <p>因此这里改为精确命中「破坏真正发生」的那一刻：
 * <ul>
 *   <li>{@code startDestroyBlock}：注入 RETURN。该方法只在按下鼠标时调用一次，
 *       不会被每 tick 调用，冷却期间它直接返回 false（偏移 25/44），
 *       不存在自我续期的循环，用返回值判断是安全的。</li>
 *   <li>{@code continueDestroyBlock}：注入 {@code startPrediction} 调用点
 *       （偏移 346，位于「挖掘完成」分支内，冷却路径根本到不了这里）。</li>
 * </ul>
 *
 * <p><b>与 Fabric 版的差异</b>：Fabric 版注入的是 {@code method_41930}（Yarn 中间名，
 * 指向某个 {@code lambda$...} 合成方法）内部的 {@code destroyBlock} 调用。
 * 这条路在 Forge 上不可行：官方映射下没有 {@code method_41930}，而合成 lambda 的名字
 * 由编译器生成、跨环境会变。经字节码核对，1.20.1 上 {@code destroyDelay} 的全部写入点
 * 都在主方法体内，不需要进入任何 lambda。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {

    /** 冷却长度（游戏刻），与 Fabric 版一致 */
    private static final int BREAKING_COOLDOWN_TICKS = 5;

    /**
     * 起步破坏：按下鼠标的第一下（含瞬间破坏路径）。
     *
     * <p>返回 true 表示这次破坏动作成功。冷却期间该方法会走早退分支返回 false，
     * 因此不会出现自我续期。
     */
    @Inject(method = "startDestroyBlock", at = @At(value = "RETURN"))
    private void addBreakingCooldown(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Configs.FeatureToggle.FORCE_BREAKING_COOLDOWN.getBooleanValue() && cir.getReturnValue()) {
            ((MultiPlayerGameModeAccessor) this).ommc$setDestroyDelay(BREAKING_COOLDOWN_TICKS);
        }
    }

    /**
     * 持续破坏：注入挖掘完成、真正要破坏方块的那一刻。
     *
     * <p>{@code startPrediction} 在整个 {@code continueDestroyBlock} 中只出现一次
     * （偏移 346），且只在 {@code destroyProgress >= 1.0} 的完成分支里被调用，
     * 所以这里既不会误命中冷却路径，也不需要额外条件判断。
     */
    @Inject(method = "continueDestroyBlock",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V"))
    private void addBreakingCooldown1(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Configs.FeatureToggle.FORCE_BREAKING_COOLDOWN.getBooleanValue()) {
            ((MultiPlayerGameModeAccessor) this).ommc$setDestroyDelay(BREAKING_COOLDOWN_TICKS);
        }
    }
}
