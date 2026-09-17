package com.plusls.ommc.mixin.feature.disableMoveDownInScaffolding;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 禁止在脚手架中下降：只有在手持「白名单」内物品时才允许在脚手架里向下穿行。
 *
 * <p>移植自 Fabric 版同名文件（oh-my-minecraft-client-nyan-work-dev.49）。
 *
 * <p><b>原理</b>：脚手架在玩家按 Shift（{@code context.isDescending()}）时会返回
 * <i>不稳定</i>形状，好让人能向下穿过去。本特性在判定不通过时把返回值改回
 * {@code STABLE_SHAPE}（完整的方块形状），于是下降就被挡住了。
 * 与「禁止破坏脚手架」同向，都是白名单 + {@code anyMatch}：命中即放行。
 *
 * <p><b>与 Fabric 版的差异</b>：
 * <ul>
 *   <li>{@code STABLE_SHAPE} 不用 {@code @Shadow} 字段读取，改用
 *       {@link ScaffoldingBlockAccessor}（原因见该文件注释：Forge 的 Mixin 注解处理器
 *       生成不了字段条目，会在正式环境崩）。</li>
 *   <li>取物品注册名用 {@code ForgeRegistries.ITEMS} 而非 {@code BuiltInRegistries.ITEM}
 *       —— 后者在 1.20.1 已被 Forge 标记为弃用。</li>
 *   <li>配置读取改为 malilib 的显式字段：{@code DISABLE_MOVE_DOWN_IN_SCAFFOLDING.getBooleanValue()}
 *       与 {@code MOVE_DOWN_IN_SCAFFOLDING_WHITELIST.getStrings()}。</li>
 *   <li>Fabric 版对 {@code Minecraft.getInstance().player} 用的是 {@code assert}，
 *       而 assert 在正式发行版里默认不生效；这里改成显式的 null 检查，行为更确定。</li>
 * </ul>
 */
@Mixin(ScaffoldingBlock.class)
public abstract class MixinScaffoldingBlock {

    @Inject(method = "getCollisionShape", at = @At(value = "RETURN"), cancellable = true)
    private void setNormalOutlineShape(BlockState state, BlockGetter world, BlockPos pos,
                                       CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        // 原版只有在「允许向下穿行」时才会返回非稳定形状；返回稳定形状说明当前没在穿行，无需处理
        VoxelShape stableShape = ((ScaffoldingBlockAccessor) this).ommc$getStableShape();
        if (cir.getReturnValue() != stableShape) {
            if (Configs.FeatureToggle.DISABLE_MOVE_DOWN_IN_SCAFFOLDING.getBooleanValue() &&
                    context.isDescending() && context.isAbove(Shapes.block(), pos, true)) {
                if (Minecraft.getInstance().player == null) {
                    return;
                }
                Item item = Minecraft.getInstance().player.getMainHandItem().getItem();
                String itemId = ForgeRegistries.ITEMS.getKey(item).toString();
                String itemName = item.getDescription().getString();
                if (Configs.Lists.MOVE_DOWN_IN_SCAFFOLDING_WHITELIST.getStrings().stream()
                        .anyMatch(s -> itemId.contains(s) || itemName.contains(s))) {
                    return;
                }
                cir.setReturnValue(stableShape);
            }
        }
    }
}
