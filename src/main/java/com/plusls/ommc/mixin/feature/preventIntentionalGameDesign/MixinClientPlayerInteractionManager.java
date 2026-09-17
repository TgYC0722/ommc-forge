package com.plusls.ommc.mixin.feature.preventIntentionalGameDesign;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 防止刻意的游戏设计：阻止床与重生锚在会爆炸的维度被使用。
 *
 * <p>床在下界/末地、重生锚在主世界/末地会爆炸（Mojang 有意为之的「刻意的游戏设计」）。
 * 这里在交互入口直接返回 {@link InteractionResult#SUCCESS}，让客户端认为交互已完成、
 * <b>不再发送交互包</b>给服务端，于是服务端不会执行爆炸逻辑，玩家看到的是「点了一下没反应」。
 *
 * <p>移植自 Fabric 版同名文件（oh-my-minecraft-client-nyan-work-dev.49）。
 * 与它的差异只有配置读取方式：{@code Configs.preventIntentionalGameDesign} →
 * {@code Configs.FeatureToggle.PREVENT_INTENTIONAL_GAME_DESIGN.getBooleanValue()}。
 * 判定所用的 {@code DimensionType.bedWorks()} / {@code respawnAnchorWorks()} 在 1.20.1
 * 上同名可用，因此其余部分逐行对应（含 Fabric 版里那句
 * {@code ClientLevel world = (ClientLevel) player.level();}）。
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {

    @Inject(method = "useItemOn", at = @At(value = "HEAD"), cancellable = true)
    private void preventIntentionalGameDesign(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult,
                                              CallbackInfoReturnable<InteractionResult> cir) {
        if (!Configs.FeatureToggle.PREVENT_INTENTIONAL_GAME_DESIGN.getBooleanValue()) {
            return;
        }
        ClientLevel world = (ClientLevel) player.level();
        BlockPos blockPos = hitResult.getBlockPos();
        BlockState blockState = world.getBlockState(blockPos);
        if ((blockState.getBlock() instanceof BedBlock && !world.dimensionType().bedWorks())
                || (blockState.getBlock() instanceof RespawnAnchorBlock && !world.dimensionType().respawnAnchorWorks())) {
            cir.setReturnValue(InteractionResult.SUCCESS);
        }
    }
}
