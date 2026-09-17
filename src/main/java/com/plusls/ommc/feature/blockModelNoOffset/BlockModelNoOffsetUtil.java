package com.plusls.ommc.feature.blockModelNoOffset;

import com.plusls.ommc.config.Configs;
import com.plusls.ommc.config.ListType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「方块模型没有偏移」的判定逻辑。
 *
 * <p>对应 Fabric 版的同名类。用途：原版会给部分方块（如各种花草）加一个随机位置偏移，
 * 让它们看起来自然；本特性可以抵消这个偏移，使模型精确对齐方块中心。
 *
 * <p><b>它没有独立的开关</b>（Fabric 版同样没有）—— 行为完全由「列表类型 + 两个列表」决定：
 * <ul>
 *   <li>{@code WHITELIST}：列表中的方块取消偏移；</li>
 *   <li>{@code BLACKLIST}：列表<b>之外</b>的方块取消偏移（列表留空即对所有方块生效）。</li>
 * </ul>
 * 因此界面上「方块模型没有偏移列表类型」那一项实际就是它的开关。
 *
 * <p>与 Fabric 版的差异（两处适配）：
 * <ol>
 *   <li>取方块注册名用 {@code ForgeRegistries.BLOCKS}（{@code BuiltInRegistries.BLOCK} 已弃用）；</li>
 *   <li>列表类型用本项目的 {@link ListType}，而非 magiclib 的 {@code UsageRestriction.ListType} ——
 *       两者语义相同，且前者正是配置项 {@code BLOCK_MODEL_NO_OFFSET_LIST_TYPE} 的类型。</li>
 * </ol>
 *
 * <p><b>当前状态：功能暂缓（未完成）。</b>本类的判定逻辑是完整可用的，问题出在渲染层的注入点：
 * 原版只有 Forge 新增的 <b>12 参</b> {@code ModelBlockRenderer.tesselateBlock} 重载里会调用
 * {@code BlockState.getOffset}，而该重载在官方映射（ForgeGradle 生成的
 * {@code createMcpToSrg/output.tsrg}）中<b>没有 SRG 名</b> —— 于是 refmap 无法为本 mixin 生成
 * {@code tesselateBlock} 条目，Mixin 在启动时报
 * {@code InjectionError: ... failed injection check, (0/1) succeeded} 并直接崩客户端。
 * 对应 mixin 已删除、注册项已从 {@code ommc.mixins.json} 摘除（其 {@code _comment_blockModelNoOffset}
 * 记录了原注册项）。恢复前需要先找到「让只存在于 SRG 环境之外的重载」可被注入的办法。
 */
public final class BlockModelNoOffsetUtil {

    private BlockModelNoOffsetUtil() {
    }

    /** 该方块状态是否应当取消随机偏移 */
    public static boolean shouldNoOffset(BlockState blockState) {
        String blockId = ForgeRegistries.BLOCKS.getKey(blockState.getBlock()).toString();
        String blockName = blockState.getBlock().getName().getString();

        if (Configs.Lists.BLOCK_MODEL_NO_OFFSET_LIST_TYPE.getOptionListValue() == ListType.WHITELIST) {
            return Configs.Lists.BLOCK_MODEL_NO_OFFSET_WHITELIST.getStrings().stream()
                    .anyMatch(s -> blockId.contains(s) || blockName.contains(s));
        } else if (Configs.Lists.BLOCK_MODEL_NO_OFFSET_LIST_TYPE.getOptionListValue() == ListType.BLACKLIST) {
            return Configs.Lists.BLOCK_MODEL_NO_OFFSET_BLACKLIST.getStrings().stream()
                    .noneMatch(s -> blockId.contains(s) || blockName.contains(s));
        }
        return false;
    }

    /**
     * 本次渲染实际应当使用的偏移量。
     *
     * <p>命中判定时返回 {@link Vec3#ZERO}（即「没有偏移」），否则沿用原版算出的偏移。
     */
    public static Vec3 blockModelNoOffset(BlockState blockState, BlockGetter world, BlockPos pos) {
        if (shouldNoOffset(blockState)) {
            return Vec3.ZERO;
        }
        return blockState.getOffset(world, pos);
    }
}
