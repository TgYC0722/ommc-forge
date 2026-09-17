package com.plusls.ommc.mixin.feature.worldEaterMineHelper;

import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 读写 {@link BlockModel} 的私有字段 {@code elements}（模型元素列表）。
 *
 * <p>世吞挖矿助手需要在烘焙「镜像模型」时让 {@code BlockModel.bake} 看到一组替换过的元素。
 *
 * <p><b>为什么是替换字段引用，而不是修改列表内容</b>：原 Fabric 版的做法是
 * {@code getElements().clear()} / {@code add(...)}，但 1.20.1 的
 * {@code BlockModel.getElements()} 是这么实现的：
 * <pre>
 *     if (customData.hasCustomGeometry()) return Collections.emptyList();
 *     if (this.elements.isEmpty() &amp;&amp; this.parent != null) {
 *         return this.parent.getElements();      // ← 返回【父模型】的内部列表
 *     }
 *     return this.elements;
 * </pre>
 * 也就是说：当方块模型自身没有 elements、靠 parent 继承时（例如 iron_ore → cube_all），
 * {@code getElements()} 返回的是<b>父模型共享的那个列表</b>。对它 clear/add 会污染所有
 * 继承同一父模板的方块。改成替换「自己的 {@code elements} 字段引用」就绕开了这个共享状态。
 *
 * <p>字段是 {@code private final}，所以需要 {@code @Mutable} 才能换引用。
 *
 * <p><b>当前状态：功能暂缓（未完成）。</b>本 accessor 随「世吞挖矿助手」一并停用，
 * 已从 {@code ommc.mixins.json} 移除注册，当前无人调用。
 */
@Mixin(BlockModel.class)
public interface BlockModelAccessor {

    @Mutable
    @Accessor("elements")
    void ommc$setElements(List<BlockElement> elements);

    @Accessor("elements")
    List<BlockElement> ommc$getElements();
}
