package com.plusls.ommc.mixin.feature.worldEaterMineHelper;

import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读写 {@link BlockBehaviour.BlockStateBase} 的私有字段 {@code lightEmission}（方块自发光等级）。
 *
 * <p>世吞挖矿助手在渲染镜像时，会临时把该值改成 15 再还原 —— 让镜像<b>自发光</b>，
 * 这样在黑暗的矿洞里也能看清（对应 Fabric 版的 {@code AccessorBlockStateBase}，
 * 用法见其 {@code WorldEaterMineHelperUtil.emitCustomBlockQuads}）。
 *
 * <p>字段是 {@code private final int}，所以需要 {@code @Mutable} 才能写入。
 * 注意读写要成对，渲染后必须还原，否则会永久改变该方块状态的发光属性。
 *
 * <p><b>当前状态：功能暂缓（未完成）。</b>本 accessor 随「世吞挖矿助手」一并停用，
 * 已从 {@code ommc.mixins.json} 移除注册，当前无人调用（它本就是给尚未实现的渲染层准备的）。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public interface BlockStateBaseAccessor {

    @Accessor("lightEmission")
    int ommc$getLightEmission();

    @Mutable
    @Accessor("lightEmission")
    void ommc$setLightEmission(int lightEmission);
}
