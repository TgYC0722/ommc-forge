package com.plusls.ommc.mixin.feature.disableBreakScaffolding;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 禁止破坏脚手架：只有在手持「白名单」内物品时才允许破坏脚手架。
 *
 * <p>移植自 Fabric 版同名文件（oh-my-minecraft-client-nyan-work-dev.49），
 * 注入点与判定规则与原版一致，仅替换了因 API 弃用 / 配置体系不同而必须改动的部分。
 *
 * <p><b>注意这是白名单，判定方向与「禁止破坏特定方块」相反</b>：
 * 那个特性是「方块命中黑名单就拦」（{@code anyMatch}），
 * 这个特性是「手持物品<b>不</b>命中白名单才拦」（{@code noneMatch}），
 * 并且先要求目标方块确实是脚手架 —— 其他方块完全不受本特性影响。
 *
 * <p><b>默认白名单里 {@code minecraft:air} 的含义是「允许空手破坏」</b>：
 * 空手时 {@code getMainHandItem()} 返回 {@link net.minecraft.world.item.ItemStack#EMPTY}，
 * 它的 {@code getItem()} 就是 {@code Items.AIR}，注册名正好是 {@code minecraft:air}。
 *
 * <p>为什么要同时注入两个方法：原版破坏流程是「{@code startDestroyBlock} 起步 →
 * 每 tick 由 {@code continueDestroyBlock} 推进」，只拦前者会被「先挖别的方块再移过来按住」
 * 绕过，与上一个特性同理。
 *
 * <p><b>与 Fabric 版的差异</b>：
 * <ul>
 *   <li>取物品注册名用 {@code ForgeRegistries.ITEMS} 而非 {@code BuiltInRegistries.ITEM}
 *       —— 后者在 1.20.1 已被 Forge 标记为弃用；Forge 注册表语义等价且含全部模组物品。</li>
 *   <li>配置读取改为 malilib 的显式字段：{@code DISABLE_BREAK_SCAFFOLDING.getBooleanValue()} 与
 *       {@code BREAK_SCAFFOLDING_WHITELIST.getStrings()}。</li>
 * </ul>
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinClientPlayerInteractionManager {

    /** 起步破坏：按住鼠标的第一下 */
    @Inject(method = "startDestroyBlock", at = @At(value = "HEAD"), cancellable = true)
    private void disableBreakScaffolding(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (shouldDisableBreakScaffolding(pos)) {
            cir.setReturnValue(false);
        }
    }

    /** 持续破坏：按住不放时每 tick 推进一次进度 */
    @Inject(method = "continueDestroyBlock", at = @At(value = "HEAD"), cancellable = true)
    private void disableBreakScaffolding1(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (shouldDisableBreakScaffolding(pos)) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 是否应当禁止破坏该位置的脚手架。
     *
     * <p>匹配规则与 Fabric 版一致，都是<b>子串匹配</b>（{@code contains} 而非相等），
     * 且同时比对两个名字：注册名（形如 {@code minecraft:scaffolding}）与本地化显示名
     * （形如 {@code 脚手架}）—— 所以玩家既可以填注册名片段，也可以直接填中文名。
     */
    private boolean shouldDisableBreakScaffolding(BlockPos pos) {
        Level world = Minecraft.getInstance().level;
        Player player = Minecraft.getInstance().player;
        if (Configs.FeatureToggle.DISABLE_BREAK_SCAFFOLDING.getBooleanValue() &&
                world != null && world.getBlockState(pos).is(Blocks.SCAFFOLDING) &&
                player != null) {
            String itemId = ForgeRegistries.ITEMS.getKey(player.getMainHandItem().getItem()).toString();
            String itemName = player.getMainHandItem().getItem().getDescription().getString();
            return Configs.Lists.BREAK_SCAFFOLDING_WHITELIST.getStrings().stream()
                    .noneMatch(s -> itemId.contains(s) || itemName.contains(s));
        }
        return false;
    }
}
