package com.plusls.ommc.mixin.feature.flatDigger;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 平坦挖掘：不按 Shift 时，无法挖掘比自己所在层更低的方块。
 *
 * <p>移植自 Fabric 版同名文件（oh-my-minecraft-client-nyan-work-dev.49），
 * 注入点与判定规则完全一致，仅改了配置读取方式。
 *
 * <p><b>判定语义</b>：目标是「挖平地时不会误挖脚下的方块」。只有当
 * ①目标方块所在层低于玩家所在层（{@code pos.getY() < player.getBlockY()}）且
 * ②玩家没有按 Shift 时，才禁止挖掘；按住 Shift 仍可正常向下挖。
 * 注意它<b>不限方块种类</b> —— 是纯高度判定，与黑/白名单类特性无关。
 *
 * <p>为什么要同时注入两个方法：原版破坏流程是「{@code startDestroyBlock} 起步 →
 * 每 tick 由 {@code continueDestroyBlock} 推进」，只拦前者会被「先挖同层方块、
 * 再把准星移到下方方块上按住不放」绕过，与 disableBreakBlock / disableBreakScaffolding 同理。
 *
 * <p><b>与 Fabric 版的差异</b>：仅配置读取改为 malilib 的显式字段
 * {@code Configs.FeatureToggle.FLAT_DIGGER.getBooleanValue()}。
 * 用到的 {@code Player.isShiftKeyDown()} 与 {@code Entity.getBlockY()} 在 1.20.1 上同名可用。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {

    /** 起步破坏：按住鼠标的第一下 */
    @Inject(method = "startDestroyBlock", at = @At(value = "HEAD"), cancellable = true)
    private void flatDigger(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (shouldFlatDigger(pos)) {
            cir.setReturnValue(false);
        }
    }

    /** 持续破坏：按住不放时每 tick 推进一次进度 */
    @Inject(method = "continueDestroyBlock", at = @At(value = "HEAD"), cancellable = true)
    private void flatDigger1(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (shouldFlatDigger(pos)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 是否应当禁止挖掘该位置。
     *
     * <p>按 Shift 放行、目标不在下方也放行，其余情况（未按 Shift 且目标更低）禁止。
     */
    private boolean shouldFlatDigger(BlockPos pos) {
        Level world = Minecraft.getInstance().level;
        Player player = Minecraft.getInstance().player;
        if (Configs.FeatureToggle.FLAT_DIGGER.getBooleanValue() && world != null && player != null) {
            return !player.isShiftKeyDown() && pos.getY() < player.getBlockY();
        }
        return false;
    }
}
