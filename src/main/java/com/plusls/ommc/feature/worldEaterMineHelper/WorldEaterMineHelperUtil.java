package com.plusls.ommc.feature.worldEaterMineHelper;

import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * 世吞挖矿助手的共享状态与判定逻辑。
 *
 * <p>对应 Fabric 版的同名类，差别只在取方块注册名改用 {@code ForgeRegistries.BLOCKS}
 * （{@code BuiltInRegistries.BLOCK} 在 1.20.1 已被 Forge 标记弃用）。
 *
 * <p><b>两套自定义模型</b>（由 {@code MixinJsonUnbakedModel} 在烘焙期填进来）：
 * <ul>
 *   <li>{@link #customModels} —— 只含<b>镜像</b>元素；</li>
 *   <li>{@link #customFullModels} —— <b>镜像 + 原模型</b>元素。</li>
 * </ul>
 * 之所以要两套：镜像元素被旋转到方块之外，用哪一套取决于该方块自身的渲染方式
 * （完整方块与半透明/非完整方块的处理不同）。渲染时会分别对应到
 * 「替换原模型」与「在原模型之外补发镜像」两条路径。
 *
 * <p><b>当前状态：功能暂缓（未完成）。</b>渲染层尚未实现，本类目前只有
 * {@code MixinJsonUnbakedModel} 会调用（而它也已停用），因此两套模型 Map 是空的。
 */
public final class WorldEaterMineHelperUtil {

    /** 只含镜像元素的模型，按方块索引 */
    public static final Map<Block, BakedModel> customModels = new HashMap<>();

    /** 含镜像 + 原模型元素的模型，按方块索引 */
    public static final Map<Block, BakedModel> customFullModels = new HashMap<>();

    /** TODO 临时诊断用：保证汇总日志只打印一次 */
    private static boolean bakedLogged;

    private WorldEaterMineHelperUtil() {
    }

    /** 方块是否命中白名单（子串匹配注册名或本地化显示名） */
    public static boolean blockInWorldEaterMineHelperWhitelist(Block block) {
        String blockName = block.getName().getString();
        String blockId = ForgeRegistries.BLOCKS.getKey(block).toString();
        return Configs.Lists.WORLD_EATER_MINE_HELPER_WHITELIST.getStrings().stream()
                .anyMatch(s -> blockId.contains(s) || blockName.contains(s));
    }

    /**
     * 该位置的方块是否应该改用自定义（镜像）模型。
     *
     * <p>判定条件：开关开启 + 方块在白名单内 + <b>上方到世界表面之间没有实心方块</b>
     * （即暴露在空气中），向上最多检查 20 格。
     */
    public static boolean shouldUseCustomModel(BlockState blockState, BlockPos pos) {
        Block block = blockState.getBlock();
        if (Configs.FeatureToggle.WORLD_EATER_MINE_HELPER.getBooleanValue()
                && blockInWorldEaterMineHelperWhitelist(block)) {
            ClientLevel world = Minecraft.getInstance().level;
            if (world != null) {
                int x = pos.getX();
                int y = pos.getY();
                int z = pos.getZ();
                int yMax = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                if (y < yMax) {
                    int j = 0;
                    for (int i = y + 1; i <= yMax; ++i) {
                        // Fabric 版这里用 isSolid()，但它在 1.20.1 已弃用 —— 其实现只是返回
                        // 一个名为 legacySolid 的遗留标志。改用 canOcclude()：
                        // 语义是「该方块状态能否遮挡视线」，正是本判定要问的问题。
                        if (world.getBlockState(new BlockPos(x, i, z)).canOcclude() && j < 20) {
                            return false;
                        }
                        ++j;
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * 记录一次成功烘焙（供诊断日志统计用）。
     *
     * <p>只打印<b>一条汇总</b>而不是逐方块打印：资源加载时会把所有方块模型都烘一遍，
     * 逐个打印会刷屏几十行。这里以第一次调用为触发点报告总量。
     *
     * <p>TODO 临时诊断日志，渲染层跑通后删除。
     */
    public static void onModelBaked() {
        if (!bakedLogged) {
            bakedLogged = true;
            OhMyMinecraftClient.LOGGER.info(
                    "[OMMC] 世吞助手：镜像模型烘焙链路已生效（本次已烘出 {} 个方块的镜像模型）",
                    customFullModels.size());
        }
    }
}
