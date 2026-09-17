package com.plusls.ommc.mixin.feature.highlightLavaSource;

import net.minecraftforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读写 {@link FluidType} 的私有字段 {@code renderProperties} —— Forge 存放该流体「客户端渲染
 * 扩展」的地方（类型为 {@code IClientFluidTypeExtensions}）。
 *
 * <p>为什么必须用 Mixin 写这个字段：1.20.1 的 Forge <b>没有</b>
 * {@code RegisterClientExtensionsEvent}（那是 1.20.2+ 才加入的），而 {@code FluidType} 也没有
 * 任何公开 setter —— 该字段只在构造期由私有的 {@code initClient()} 经
 * {@code initializeClient(Consumer)} 写入一次。所以要给 Forge 自带的 {@code ForgeMod.LAVA_TYPE}
 * 换上自定义扩展，只能直接改这个字段。
 *
 * <p>该字段不是 final，但仍显式加 {@code @Mutable}：一旦将来 Forge 把它改成 final，
 * 缺了这个注解会在运行时抛 {@code InvalidMixinException}，而加上它没有副作用。
 *
 * <p>不使用 {@code @Shadow} 的原因同其它特性：Forge 的 Mixin 注解处理器生成不了 refmap 的
 * <b>字段</b>条目，而 {@code @Accessor} 可以。
 *
 * <p><b>{@code remap = false} 必不可少</b>：{@code renderProperties} 是 Forge 自己给
 * {@code FluidType} 加的字段，不是 Mojang 的，因此官方映射（ForgeGradle 生成的
 * {@code createMcpToSrg/output.tsrg}）里<b>没有它的 SRG 名</b> —— 它在正式环境里也叫
 * {@code renderProperties}，本来就不需要映射。若保持默认的 {@code remap = true}，
 * 注解处理器会去找一个不存在的映射并报
 * {@code Unable to locate obfuscation mapping for @Accessor target renderProperties}，
 * 直接让编译失败。（对照：Mojang 自己的字段如 {@code LiquidBlockRenderer.lavaIcons}
 * 有 SRG 名 {@code f_110940_}，那种就必须 remap。）
 */
@Mixin(value = FluidType.class, remap = false)
public interface FluidTypeAccessor {

    @Mutable
    @Accessor(value = "renderProperties", remap = false)
    Object ommc$getRenderProperties();

    @Mutable
    @Accessor(value = "renderProperties", remap = false)
    void ommc$setRenderProperties(Object properties);
}
