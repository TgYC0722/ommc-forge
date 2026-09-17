package com.plusls.ommc.event;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import com.plusls.ommc.feature.chatHistory.ChatHistoryStorage;
import com.plusls.ommc.mixin.accessor.ChatComponentAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Optional;

/**
 * 「不清空聊天历史记录」的持久化实现。
 *
 * <p>配置项沿用原来的 {@code dontClearChatHistory}。这个开关最早照搬 Fabric 版
 * 「取消 {@code allMessages.clear()}」的做法，但那条路在 1.20.1 上走不通：
 * 数据能保住，可 {@code trimmedMessages}（正在渲染的行）照样被清，
 * 而重建它的 {@code refreshTrimmedMessage()} 在清空流程里根本不会被调用 ——
 * 结果就是「历史留在内存里、屏幕上一片空」，观感上像是功能反了。
 *
 * <p>现在改为<b>持久化</b>：退出世界时把聊天记录写进文件，下次进同一个世界再读出来显示。
 * 这样也顺带不受 F3+D 影响 —— 那只是清掉内存里的显示。
 *
 * <h2>保存时机</h2>
 * 选在 {@link ClientPlayerNetworkEvent.LoggingOut}。这是有讲究的 ——
 * {@code Minecraft.clearLevel(Screen)} 的字节码顺序是：
 * <pre>
 *   偏移 14:  connection.close()
 *   偏移 65:  ForgeHooksClient.firePlayerLogout(...)            &lt;-- LoggingOut 在这里
 *   偏移 164: Gui.onDisconnected() → chat.clearMessages(true)   &lt;-- 聊天记录在这之后才被清
 * </pre>
 * 也就是说在 {@code LoggingOut} 里读消息列表，数据还是完整的。
 *
 * <h2>不串档</h2>
 * 存档文件按世界隔离，标识与文件名规则见 {@link ChatHistoryStorage}。
 * 本类只负责「取当前世界标识 → 存 / 取」以及组件与 JSON 之间的转换。
 */
@Mod.EventBusSubscriber(modid = OhMyMinecraftClient.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ChatHistoryPersistence {

    /** 本次进入世界是否已经恢复过，避免 {@code LoggingIn} 被多次触发时重复刷屏。 */
    private static boolean restoredThisSession = false;

    /** 待恢复的存档；{@code LoggingIn} 时字体/聊天框可能还没就绪，推迟到后面的 tick 再做。 */
    private static JsonObject pendingRestore = null;

    /**
     * 进入世界时缓存下来的世界标识与可读名。
     *
     * <p><b>为什么必须缓存，而不是在退出时现查</b>：保存发生在
     * {@link ClientPlayerNetworkEvent.LoggingOut}，而那个时刻集成服务器<b>已经在关闭过程中</b>
     * —— {@code Minecraft.getSingleplayerServer()} 此时很可能已经返回 {@code null}，
     * 于是 {@code currentWorldId()} 拿不到标识，整个保存流程会静默跳过（一条日志都没有）。
     * 进世界那会儿服务器必然是活的，所以标识在那里取一次、存下来用。
     */
    private static String cachedWorldId = null;
    private static String cachedWorldName = null;
    private static boolean worldIdCached = false;

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        restoredThisSession = false;
        pendingRestore = null;

        // 换世界了，缓存作废（世界标识留到 tick 里再取，那时集成服务器才真正就绪）
        worldIdCached = false;
        cachedWorldId = null;
        cachedWorldName = null;
    }

    /**
     * 每 tick 做两件事：补上世界标识缓存、执行待办的恢复。
     *
     * <p>用 tick 而不是直接在 {@code LoggingIn} 里做，是因为要等聊天框、字体与玩家实体可用 ——
     * {@code addMessage} 内部会用 {@code mc.font} 计算换行。
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null || mc.font == null || mc.gui == null) {
            return;
        }

        // 1) 世界标识只取一次，并打一条日志把「开关状态 / 世界识别 / 存档路径」都摊开
        if (!worldIdCached) {
            worldIdCached = true;

            if (Configs.Generic.DONT_CLEAR_CHAT_HISTORY.getBooleanValue()) {
                cachedWorldId = ChatHistoryStorage.currentWorldId();
                cachedWorldName = ChatHistoryStorage.currentWorldName();

                if (cachedWorldId != null) {
                    OhMyMinecraftClient.LOGGER.info("聊天记录: 世界「{}」标识={}, 存档={}",
                            cachedWorldName, cachedWorldId,
                            ChatHistoryStorage.fileFor(cachedWorldId));

                    if (pendingRestore == null) {
                        pendingRestore = ChatHistoryStorage.load(cachedWorldId);
                    }
                } else {
                    OhMyMinecraftClient.LOGGER.warn("聊天记录: 无法识别当前世界，本世界不会保存");
                }
            }
        }

        // 2) 执行待办的恢复
        if (pendingRestore != null && !restoredThisSession) {
            JsonObject snapshot = pendingRestore;
            pendingRestore = null;
            restoredThisSession = true;
            restore(mc, snapshot);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!Configs.Generic.DONT_CLEAR_CHAT_HISTORY.getBooleanValue()) {
            return;
        }

        // 用进世界时缓存下来的标识 —— 此刻集成服务器已在关闭，现场查会拿到 null
        if (cachedWorldId == null) {
            OhMyMinecraftClient.LOGGER.warn("聊天记录: 没有可用的世界标识，跳过保存");
            return;
        }

        // 到这里 allMessages 还没被 Gui.onDisconnected() 清掉
        JsonArray messages = collectCurrentMessages();

        JsonObject root = new JsonObject();
        root.addProperty("worldId", cachedWorldId);
        root.addProperty("worldName", cachedWorldName == null ? "unknown" : cachedWorldName);
        root.add("messages", messages);

        ChatHistoryStorage.save(cachedWorldId, root);

        pendingRestore = null;
        restoredThisSession = false;
        worldIdCached = false;
        cachedWorldId = null;
        cachedWorldName = null;
    }

    // ==================== 收集 / 恢复 ====================

    /** 把当前聊天框里的消息抽成 JSON。 */
    private static JsonArray collectCurrentMessages() {
        JsonArray array = new JsonArray();
        Minecraft mc = Minecraft.getInstance();

        if (mc.gui == null) {
            return array;
        }

        ChatComponent chat = mc.gui.getChat();

        // ChatComponent 没有公开的消息列表访问器，只能通过 @Accessor 读私有字段
        List<GuiMessage> messages = ((ChatComponentAccessor) chat).ommc$getAllMessages();

        for (GuiMessage message : messages) {
            Component content = message.content();

            if (content == null) {
                continue;
            }

            try {
                JsonElement json = Component.Serializer.toJsonTree(content);

                if (json == null) {
                    continue;
                }

                JsonObject entry = new JsonObject();
                entry.add("content", json);
                entry.addProperty("system", isSystemTag(message.tag()));
                array.add(entry);
            } catch (Exception e) {
                // 个别组件可能没法序列化（例如含自定义 contents），跳过即可，别拖垮整次保存
                OhMyMinecraftClient.LOGGER.debug("聊天记录: 跳过一条无法序列化的消息", e);
            }
        }

        return array;
    }

    /**
     * 判断一条消息是不是系统消息。
     *
     * <p>带图标的（{@code chatNotSecure} 的未签名警告、{@code chatModified} 的涂改标记）
     * 一律按非系统处理 —— 这些标记在离线恢复出来的消息上没有意义，
     * 统一挂 {@code system()} 标签能让它们安静显示，不会冒出 ⚠ 图标。
     */
    private static boolean isSystemTag(GuiMessageTag tag) {
        return tag == null || tag.icon() == null;
    }

    /**
     * 把存档里的消息灌回聊天框。
     *
     * <p>两条刻意的处理：
     * <ol>
     *   <li><b>逐条添加，{@code addedTime} 由原版置 0。</b>可见度由
     *       {@code getTimeFactor(addedTime) = 200 - addedTime} 决定，超过 200 tick（10 秒）
     *       就完全透明。若把保存时的原始时间戳塞回去，稍有历史的消息一恢复就是全透明的，
     *       等于没恢复；用新消息的身份添加则全部可见，10 秒后一起淡出 —— 正是想要的效果。</li>
     *   <li><b>剥掉点击 / 悬停事件。</b>这些事件绑在<em>服务器那条消息</em>上；
     *       离线恢复出来的组件若还带 {@code RUN_COMMAND}，点一下会往当前服务器发指令，
     *       既莫名其妙又有风险。只保留颜色与字形。</li>
     * </ol>
     */
    private static void restore(Minecraft mc, JsonObject snapshot) {
        ChatComponent chat = mc.gui.getChat();
        List<JsonElement> entries = ChatHistoryStorage.readMessages(snapshot);
        int restored = 0;

        // 分隔线：让人一眼看出下面是上次的记录
        chat.addMessage(Component.literal("── 上次会话的聊天记录 ──").withStyle(ChatFormatting.DARK_GRAY),
                null, GuiMessageTag.system());

        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject entry = element.getAsJsonObject();

            if (!entry.has("content")) {
                continue;
            }

            Component content = parseContent(entry.get("content"));

            if (content == null) {
                continue;
            }

            chat.addMessage(content, null, GuiMessageTag.system());
            restored++;
        }

        String worldName = snapshot.has("worldName") ? snapshot.get("worldName").getAsString() : "unknown";
        OhMyMinecraftClient.LOGGER.info("聊天记录: 已从 {} 恢复 {} 条", worldName, restored);
    }

    /** 解析存档里的组件 JSON，解析不了就退化成纯文本，绝不抛异常。 */
    private static Component parseContent(JsonElement json) {
        try {
            return stripInteraction(Component.Serializer.fromJsonLenient(json.toString()));
        } catch (Exception e) {
            OhMyMinecraftClient.LOGGER.debug("聊天记录: 组件解析失败，改用纯文本", e);

            return Component.literal(rawText(json));
        }
    }

    /**
     * 重新拼一个只保留「颜色 + 字形」的副本。
     *
     * <p>{@code Component.visit(StyledContentConsumer, Style)} 会把整棵组件树的样式合并后
     * 逐段吐出，正好用来做这件事 —— 不必自己递归 {@code getContents()}/{@code getSiblings()}，
     * 也就绕开了「{@code LiteralContents} 的 text 字段是私有的、读不到」这类麻烦。
     *
     * <p>两处签名细节：参数顺序是 <b>consumer 在前、Style 在后</b>；
     * 泛型必须显式写成 {@code <Void>} —— 这里的 lambda 只做副作用、返回
     * {@code Optional.empty()}，编译器否则推不出 {@code T}。
     */
    private static Component stripInteraction(Component source) {
        if (source == null) {
            return null;
        }

        MutableComponent result = Component.empty();

        try {
            source.<Void>visit((style, text) -> {
                if (!text.isEmpty()) {
                    result.append(Component.literal(text).withStyle(visualOnly(style)));
                }

                return Optional.empty();
            }, Style.EMPTY);
        } catch (Exception e) {
            return Component.literal(source.getString());
        }

        // visit 什么都没产出时（例如空组件）至少保住文本
        return result.getString().isEmpty() ? Component.literal(source.getString()) : result;
    }

    /** 只挑视觉属性，丢掉 clickEvent / hoverEvent / insertion。 */
    private static Style visualOnly(Style style) {
        Style result = Style.EMPTY;

        if (style.getColor() != null) {
            result = result.withColor(style.getColor());
        }

        return result
                .withBold(style.isBold())
                .withItalic(style.isItalic())
                .withUnderlined(style.isUnderlined())
                .withStrikethrough(style.isStrikethrough())
                .withObfuscated(style.isObfuscated());
    }

    /** 兜底：从 JSON 里粗暴抠出可见文本。 */
    private static String rawText(JsonElement json) {
        try {
            if (json.isJsonPrimitive()) {
                return json.getAsString();
            }

            if (json.isJsonObject() && json.getAsJsonObject().has("text")) {
                return json.getAsJsonObject().get("text").getAsString();
            }

            // 结构里只留下嵌套的 extra 时，把所有 text 字段拼起来
            if (json.isJsonObject() && json.getAsJsonObject().has("extra")) {
                StringBuilder sb = new StringBuilder();

                for (JsonElement child : json.getAsJsonObject().getAsJsonArray("extra")) {
                    sb.append(rawText(child));
                }

                return sb.toString();
            }
        } catch (Exception ignored) {
            // 下面直接返回原文
        }

        return json.toString();
    }
}
