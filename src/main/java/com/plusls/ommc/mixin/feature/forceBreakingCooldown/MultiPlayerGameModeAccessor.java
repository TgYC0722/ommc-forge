package com.plusls.ommc.mixin.feature.forceBreakingCooldown;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读写 {@link MultiPlayerGameMode} 的私有字段 {@code destroyDelay}（破坏冷却计时器）。
 *
 * <p><b>为什么不用 {@code @Shadow} 字段</b>：Forge 侧的 Mixin 注解处理器生成不了 refmap 的
 * <b>字段</b>条目（TSRG 解析字段时描述符为空，而 {@code MappingField} 的 equals/hashCode
 * 都基于含描述符的 {@code toString()}，两边对不上）。而 {@code destroyDelay} 在
 * {@code startDestroyBlock} / {@code continueDestroyBlock} 里都是真实的字段读写，
 * 正式环境里它的名字是 SRG {@code f_105195_}，缺 refmap 条目就会在运行时抛
 * {@code @Shadow field destroyDelay was not located} —— 与 autoSwitchElytra 的
 * {@code minecraft} 字段是同一个坑。
 *
 * <p>改用 accessor 后（实测于 disableMoveDownInScaffolding 验证过）：处理器会为
 * {@code @Accessor} 生成<b>字段</b>映射条目，字段名由 Mixin 运行时按 {@code "destroyDelay"}
 * 查找，因此正式环境也能正确解析。
 *
 * <p>getter/setter 成对声明 —— accessor 的字段名是从方法名反推的
 * （{@code getDestroyDelay} → {@code destroyDelay}，{@code setDestroyDelay} → 同名），
 * 只写 setter 也能工作，但成对更清晰、也便于将来排查。
 */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {

    @Accessor("destroyDelay")
    int ommc$getDestroyDelay();

    @Accessor("destroyDelay")
    void ommc$setDestroyDelay(int value);
}
