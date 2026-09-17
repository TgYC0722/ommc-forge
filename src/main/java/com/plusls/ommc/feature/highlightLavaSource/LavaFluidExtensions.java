package com.plusls.ommc.feature.highlightLavaSource;

import com.plusls.ommc.config.Configs;
import com.plusls.ommc.mixin.feature.highlightLavaSource.FluidTypeAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.ForgeMod;

/**
 * 岩浆的客户端渲染扩展：让<b>源头</b>岩浆用自定义贴图，其余岩浆保持原版。
 *
 * <p>这是 Forge 1.20.1 的正统做法 —— {@code LiquidBlockRenderer.tesselate} 取贴图走的是
 * {@code ForgeHooksClient.getFluidSprites(getter, pos, fluidState)}，而它的实现是：
 * <pre>
 *     IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluidState);
 *     ResourceLocation overlay = ext.getOverlayTexture(fluidState, getter, pos);
 *     new TextureAtlasSprite[] {
 *         atlas.apply(ext.getStillTexture(fluidState, getter, pos)),      // ← 带位置，且【无判空】
 *         atlas.apply(ext.getFlowingTexture(fluidState, getter, pos)),    // ← 同上
 *         overlay == null ? null : atlas.apply(overlay)                   // ← 只有这项判了空
 *     }
 * </pre>
 * 所以只要在 {@code getStillTexture}/{@code getFlowingTexture} 里按位置返回不同贴图即可，
 * 效果与 Fabric 版注册的 {@code FluidRenderHandler} 完全对应。
 *
 * <p>（曾经走过的弯路：以为要替换 {@code LiquidBlockRenderer.lavaIcons} 字段。实际
 * {@code tesselate} 根本不读那个字段，替换它没有任何作用。）
 *
 * <p>注意 {@code getStillTexture} 的三个参数都可能为 null —— Forge 的
 * {@code IClientFluidTypeExtensions.DEFAULT} 实现本身就会无条件调用无参的
 * {@code getStillTexture()}，所以那些参数是允许缺省的，这里必须判空后再做源头判定。
 *
 * <p><b>两个纹理方法必须永远返回非 null（实测踩过的崩溃点）</b>：
 * 不要在图省事时把「非源头」分支委托给 {@code IClientFluidTypeExtensions.DEFAULT} ——
 * 它的 {@code getStillTexture()} / {@code getFlowingTexture()} 都是 {@code return null}，
 * 而 {@code ForgeHooksClient.getFluidSprites} 对这两项是<b>无条件</b>
 * {@code atlas.apply(...)}（只有第三项 overlay 做了判空），于是
 * {@code atlas.apply(null)} 会在 {@code ImmutableMap.get(null)} 处抛 NPE，
 * 表现为「Tesselating liquid in world」崩溃。
 *
 * <p>由于「源头 / 流动」正好由 {@code LiquidBlock.LEVEL} 区分，这个 bug 的现象很迷惑：
 * 静止的源头岩浆正常渲染，一旦旁边的方块被挖开、岩浆开始流动（出现 LEVEL != 0 的方块）
 * 就立刻崩溃。</p>
 */
public class LavaFluidExtensions implements IClientFluidTypeExtensions {

    /** 原版岩浆静态贴图（与 ModelBakery.LAVA_STILL 的 Material 一致） */
    private static final ResourceLocation VANILLA_STILL =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/lava_still");

    /** 原版岩浆流动贴图（与 ModelBakery.LAVA_FLOW 的 Material 一致） */
    private static final ResourceLocation VANILLA_FLOW =
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/lava_flow");

    /**
     * 把自定义扩展装到 Forge 自带的 {@code ForgeMod.LAVA_TYPE} 上。
     *
     * <p>必须在客户端初始化后调用：{@code renderProperties} 是在 {@code FluidType} 构造期
     * 由私有的 {@code initClient()} 写入的，而 {@code ForgeMod.LAVA_TYPE} 早在
     * {@code ForgeMod} 类加载时就构造完了，我们没有任何「参与注册」的时机，只能事后覆盖。
     */
    public static void replace() {
        ((FluidTypeAccessor) (Object) ForgeMod.LAVA_TYPE.get()).ommc$setRenderProperties(new LavaFluidExtensions());
    }

    /**
     * 该位置的岩浆是否为源头，且功能已开启。
     *
     * <p>源头判定与 Fabric 版一致：岩浆方块的 {@code LiquidBlock.LEVEL} 为 0。
     * 流动岩浆没有 LEVEL 属性，因此直接返回 false（用原版贴图）。
     */
    private static boolean shouldHighlight(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        if (!Configs.FeatureToggle.HIGHLIGHT_LAVA_SOURCE.getBooleanValue()) {
            return false;
        }
        if (state == null || getter == null || pos == null) {
            return false;
        }
        var blockState = getter.getBlockState(pos);
        return blockState.hasProperty(LiquidBlock.LEVEL) && blockState.getValue(LiquidBlock.LEVEL) == 0;
    }

    @Override
    public ResourceLocation getStillTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        if (shouldHighlight(state, getter, pos)) {
            return LavaSourceSprites.STILL_ID;
        }
        return VANILLA_STILL;
    }

    @Override
    public ResourceLocation getFlowingTexture(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
        if (shouldHighlight(state, getter, pos)) {
            return LavaSourceSprites.FLOW_ID;
        }
        return VANILLA_FLOW;
    }
}
