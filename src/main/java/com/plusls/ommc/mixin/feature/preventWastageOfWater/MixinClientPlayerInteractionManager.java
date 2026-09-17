package com.plusls.ommc.mixin.feature.preventWastageOfWater;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 防止浪费水：在下界（{@code ultraWarm} 维度）禁止使用水桶。
 *
 * <p><b>与 Fabric 版的机制差异（本特性唯一的移植改动）</b>：Fabric 版通过 fabric-api 的
 * {@code UseItemCallback} 事件实现（见其 {@code PreventWastageOfWaterHandler}），
 * <b>Forge 没有等价事件</b>，因此这里改成注入 {@code MultiPlayerGameMode.useItem} 的 HEAD，
 * 判定命中时返回 {@link InteractionResult#FAIL} 取消这次使用。
 *
 * <p>放在 HEAD 是安全的：{@code useItem} 的开头只是旁观者判断与
 * {@code ensureHasSentCarriedItem()} / 发送玩家位置包，真正的「使用物品」与
 * {@code ServerboundUseItemPacket} 发送都在其后。提前返回不会发出任何包，
 * 服务端也就不会执行水桶逻辑 —— 水不会被消耗，玩家手里仍是水桶。
 *
 * <p>其余与 Fabric 版逐行对应，包括那句 {@code level.isClientSide()} 判断 ——
 * 在客户端 mixin 里它恒为 true（冗余），但保留以与上游保持一致。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {

    @Inject(method = "useItem", at = @At(value = "HEAD"), cancellable = true)
    private void preventWastageOfWater(Player player, InteractionHand hand,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        if (Configs.FeatureToggle.PREVENT_WASTAGE_OF_WATER.getBooleanValue()
                && player.level().isClientSide()
                && player.getItemInHand(hand).getItem() == Items.WATER_BUCKET
                && player.level().dimensionType().ultraWarm()) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
