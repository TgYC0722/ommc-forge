package com.plusls.ommc.mixin.feature.disableBreakBlock;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 禁止破坏特定方块：黑名单里的方块无法被破坏。
 *
 * <p>移植自 Fabric 版同名文件（oh-my-minecraft-client-nyan-work-dev.49），
 * 注入点、匹配规则与原版一致，仅替换了因映射体系/API 弃用而必须改动的部分。
 *
 * <p><b>为什么要注入两个方法</b>：原版的破坏流程是「{@code startDestroyBlock} 起步 →
 * 之后每 tick 由 {@code continueDestroyBlock} 推进进度」。只拦前者是不够的 ——
 * 玩家可以先挖一个允许的方块、再把准星移到黑名单方块上按住不放，
 * 破坏进度会继续累积，黑名单就形同虚设。因此两个入口都要拦。
 *
 * <p><b>与 Fabric 版的差异</b>：
 * <ul>
 *   <li>取方块注册名用 {@code ForgeRegistries.BLOCKS} 而非 {@code BuiltInRegistries.BLOCK} ——
 *       后者在 1.20.1 已被 Forge 标记为弃用；Forge 注册表语义等价且包含全部模组方块。</li>
 *   <li>配置读取改为 malilib 的显式字段：{@code DISABLE_BREAK_BLOCK.getBooleanValue()} 与
 *       {@code BREAK_BLOCK_BLACKLIST.getStrings()}（后者返回 {@code List<String>}，
 *       与 Fabric 版的 {@code ArrayList<String>} 用法一致）。</li>
 * </ul>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {

    /** 起步破坏：按住鼠标的第一下 */
    @Inject(method = "startDestroyBlock", at = @At(value = "HEAD"), cancellable = true)
    private void disableBreakBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (shouldDisableBreakBlock(pos)) {
            cir.setReturnValue(false);
        }
    }

    /** 持续破坏：按住不放时每 tick 推进一次进度 */
    @Inject(method = "continueDestroyBlock", at = @At(value = "HEAD"), cancellable = true)
    private void disableBreakBlock1(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (shouldDisableBreakBlock(pos)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 该位置的方块是否在黑名单中。
     *
     * <p>匹配规则与 Fabric 版一致，都是<b>子串匹配</b>（{@code contains} 而非相等），
     * 且同时比对两个名字：
     * <ul>
     *   <li>注册名，形如 {@code minecraft:budding_amethyst} ——
     *       所以默认列表里的 {@code "_bud"} 可以命中所有 {@code *_bud} 方块；</li>
     *   <li>本地化显示名，形如 {@code 紫水晶母岩} ——
     *       所以玩家也可以直接填中文名来屏蔽。</li>
     * </ul>
     */
    private boolean shouldDisableBreakBlock(BlockPos pos) {
        Level world = Minecraft.getInstance().level;
        Player player = Minecraft.getInstance().player;
        if (Configs.FeatureToggle.DISABLE_BREAK_BLOCK.getBooleanValue() && world != null && player != null) {
            String blockId = ForgeRegistries.BLOCKS.getKey(world.getBlockState(pos).getBlock()).toString();
            String blockName = world.getBlockState(pos).getBlock().getName().getString();
            return Configs.Lists.BREAK_BLOCK_BLACKLIST.getStrings().stream()
                    .anyMatch(s -> blockId.contains(s) || blockName.contains(s));
        }
        return false;
    }
}
