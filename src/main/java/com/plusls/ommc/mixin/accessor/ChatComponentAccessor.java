package com.plusls.ommc.mixin.accessor;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 读取 {@link ChatComponent} 的私有字段 {@code allMessages}（聊天框里的全部消息）。
 *
 * <p><b>为什么必须用 Mixin</b>：{@code ChatComponent} 没有任何公开方法能取出消息列表 ——
 * 唯一的 {@code getRecentChat()} 返回的是<b>输入历史</b>（按 ↑ 翻的那些字符串），
 * 不是消息本身。所以「把聊天记录存盘」这件事，读的那一步绕不开访问私有字段。
 *
 * <p>这只是<b>读一个字段</b>，不改变任何原版行为；本功能的其余部分
 * （序列化、按世界隔离存盘、恢复显示）全部走公开 API 与 Forge 事件
 * （{@link net.minecraftforge.client.event.ClientPlayerNetworkEvent}）。
 *
 * <p><b>为什么用 {@code @Accessor} 而不是 {@code @Shadow} 字段</b>：
 * Forge 侧的 Mixin 注解处理器写不出 refmap 的<b>字段</b>条目，缺条目的字段访问
 * 会在运行时抛 {@code @Shadow field ... was not located in the target class}；
 * 而 {@code @Accessor} 生成的是<b>方法</b>，方法映射的生成链路是正常的。
 * 详见 {@code ScaffoldingBlockAccessor} 里记录的同一个坑。
 */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {

    @Accessor("allMessages")
    List<GuiMessage> ommc$getAllMessages();
}
