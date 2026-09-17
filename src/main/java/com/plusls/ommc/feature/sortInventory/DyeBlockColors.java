package com.plusls.ommc.feature.sortInventory;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 读取「染色方块」的染料颜色。
 *
 * <h2>为什么需要它</h2>
 * 整理仓库时要让床、潜影盒、染色玻璃这类方块<b>按 16 色顺序</b>排列，这需要知道每个方块
 * 具体是什么颜色。麻烦在于这些方块的 {@code color} 字段是 {@code private final DyeColor}，
 * 且<b>没有公开 getter</b> —— 拿不到它，排序就只能退而用 {@code defaultMapColor()}，
 * 而多种染料色会映射到同一个 MapColor 上，排出来是乱的
 * （例如白色羊毛与淡灰色羊毛同为 {@code MapColor.SNOW}）。
 *
 * <h2>为什么用反射而不是 Mixin</h2>
 * 常规做法是给 6 个方块各写一个 {@code @Mixin implements IDyeBlock}，再各配一个
 * {@code @Accessor} 接口读字段 —— 那是 12 个文件。而用反射只需一个类：
 * <ul>
 *   <li>字段名 {@code color} 在运行时就是这个（本模组用 Mojang 官方映射，不存在 SRG 混淆问题，
 *       也就没有 refmap 那条链路要操心）；</li>
 *   <li>调用频率极低 —— 只在玩家<b>按下整理热键</b>时、为参与排序的方块各查一次，
 *       且结果按方块类缓存，对性能没有可感知影响。</li>
 * </ul>
 *
 * <h2>缓存</h2>
 * 用 {@link Optional} 缓存解析结果：{@code Optional.empty()} 表示「确认过没有 color 字段」，
 * 这样查不到的方块类也只会反射一次，不会每次调用都重试一遍。
 */
public final class DyeBlockColors {

    /** 方块类 → color 字段（{@code empty} 表示确认没有）。 */
    private static final Map<Class<?>, Optional<Field>> FIELD_CACHE = new ConcurrentHashMap<>();

    private DyeBlockColors() {
    }

    /**
     * 取方块的染料颜色，取不到返回 {@code null}。
     *
     * <p>{@code null} 有三重含义，但调用方都不需要区分：
     * 「这不是染色方块」、「这个方块本来就没有染料色」（未染色的潜影盒）、
     * 「字段读不出来」。三者在排序里的处理完全一样。
     */
    @Nullable
    public static DyeColor getColor(@Nullable Block block) {
        if (block == null) {
            return null;
        }

        Optional<Field> field = FIELD_CACHE.computeIfAbsent(block.getClass(), DyeBlockColors::findColorField);

        if (field.isEmpty()) {
            return null;
        }

        try {
            Object value = field.get().get(block);

            return value instanceof DyeColor color ? color : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 字段在但读不动（理论上不会），当作没有颜色，别让整次排序崩掉
            return null;
        }
    }

    /**
     * 在方块类里找 {@code color} 字段。
     *
     * <p>必须沿继承链往上找：例如 {@code StainedGlassPaneBlock} 的 {@code color}
     * 就声明在父类 {@code StainedGlassBlock} 上，直接 {@code getDeclaredField} 找不到。
     */
    private static Optional<Field> findColorField(Class<?> blockClass) {
        Class<?> current = blockClass;

        while (current != null && current != Block.class) {
            try {
                Field field = current.getDeclaredField("color");

                if (field.getType() == DyeColor.class) {
                    field.setAccessible(true);

                    return Optional.of(field);
                }
            } catch (NoSuchFieldException ignored) {
                // 继续往父类找
            }

            current = current.getSuperclass();
        }

        return Optional.empty();
    }
}
