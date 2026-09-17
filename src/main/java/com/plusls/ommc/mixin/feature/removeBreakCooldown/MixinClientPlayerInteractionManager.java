package com.plusls.ommc.mixin.feature.removeBreakCooldown;

import com.plusls.ommc.config.Configs;
import com.plusls.ommc.mixin.feature.forceBreakingCooldown.MultiPlayerGameModeAccessor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 移除挖掘冷却：破掉方块后不再进入冷却，可以立刻继续挖。
 *
 * <p>移植自 Fabric 版同名文件（oh-my-minecraft-client-nyan-work-dev.49）。
 * 它把 {@code destroyDelay}（破坏冷却计时器）在<b>挖掘完成那一刻</b>置 0，
 * 而原版那时会把它设为 5 —— 于是后续的冷却被取消。
 *
 * <p><b>注入点</b>：{@code continueDestroyBlock} 内对 {@code destroyDelay} 的第 3 个
 * PUTFIELD 之后。1.20.1 该字段共 3 个写入点，已逐一核对：
 * <ol>
 *   <li>偏移 18 —— 冷却递减（{@code destroyDelay--}），此时正处于冷却中；</li>
 *   <li>偏移 52 —— 创造模式分支里设为 5；</li>
 *   <li>偏移 361 —— <b>挖掘完成</b>分支里设为 5（紧跟 {@code startPrediction} 之后），
 *       即本 mixin 的目标，也就是真正的「破坏成功」路径。</li>
 * </ol>
 * 选对这一处很重要：若打到前面两个写入点，等于是「冷却中不断清零」，
 * 会让冷却机制彻底失效（而不只是移除本次破坏后的冷却）。
 *
 * <p><b>与 Fabric 版的差异</b>：不使用 {@code @Shadow} 字段，改用
 * {@link MultiPlayerGameModeAccessor}（原因同其它特性：Forge 的 Mixin 注解处理器
 * 生成不了 refmap 的字段条目，而 {@code @Accessor} 可以）。
 * 判定里保留 {@code !forceBreakingCooldown} —— 两个开关同时打开时以「强制冷却」优先。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {

    @Inject(method = "continueDestroyBlock",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;destroyDelay:I",
                    opcode = Opcodes.PUTFIELD,
                    ordinal = 2, shift = At.Shift.AFTER))
    private void removeBreakingCooldown(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (Configs.FeatureToggle.REMOVE_BREAKING_COOLDOWN.getBooleanValue()
                && !Configs.FeatureToggle.FORCE_BREAKING_COOLDOWN.getBooleanValue()) {
            ((MultiPlayerGameModeAccessor) this).ommc$setDestroyDelay(0);
        }
    }
}
