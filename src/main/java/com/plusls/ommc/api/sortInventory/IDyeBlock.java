package com.plusls.ommc.api.sortInventory;

import net.minecraft.world.item.DyeColor;

/**
 * 给「有染料颜色」的方块挂一个取颜色的方法。
 *
 * <p>整理仓库时要按颜色排序，而 {@code BedBlock} / {@code ShulkerBoxBlock} 这类方块的
 * {@code color} 字段是 {@code private final}、也没有公开 getter，所以只能靠 Mixin 补一个接口出来。
 *
 * <p><b>为什么不直接用 {@code Minecraft.getInstance()} 或地图色推断</b>：
 * 这些方块的颜色是构造期确定的染料色（16 色一一对应），比 {@code defaultMapColor()}
 * 精确 —— 后者会把多种颜色归到同一个 MapColor 上，排出来是乱的。
 *
 * <p>实现方见 {@code com.plusls.ommc.mixin.generic.sortInventory} 下的 6 个 Mixin。
 */
public interface IDyeBlock {

    /** 返回该方块的颜色；潜影盒可以是 {@code null}（未染色的那种）。 */
    DyeColor ommc$getColor();
}
