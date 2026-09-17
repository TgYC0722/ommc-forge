package com.plusls.ommc.feature.chatHistory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plusls.ommc.OhMyMinecraftClient;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天记录的持久化存储 —— 按「世界 / 服务器」隔离。
 *
 * <h2>为什么要按世界隔离</h2>
 * 聊天记录里常有服务器地址、玩家名、坐标之类的东西。如果所有世界共用一个文件，
 * 换存档或换服务器时就会把别处的聊天串进来，既容易泄密也毫无意义。
 *
 * <h2>怎么标识一个「世界」</h2>
 * 两条互不相交的路径：
 * <ul>
 *   <li><b>单人存档</b>：取存档根目录（{@code saves/<存档名>}）的<b>绝对路径</b>。
 *       这是文件系统级的唯一标识 —— 存档不可能重名，改名或删档也会自然分家。</li>
 *   <li><b>多人服务器</b>：取 {@link ServerData#ip}（{@code 主机:端口}）。
 *       同一地址就是同一台服务器；不同端口 / 不同域名算不同服务器。</li>
 * </ul>
 * 两者都会带上前缀（{@code sp:} / {@code mp:}）后算一个 <b>SHA-1 指纹</b>，用它当文件名，
 * 因此既不会撞名，也不会因为世界名或路径里有 {@code : \ /} 之类字符而写不出文件。
 *
 * <p>文件里仍然记录了可读的世界名与原始标识，方便人工辨认（哈希名本身看不出是哪个世界）。
 * 读取时还会再校验一次文件内记录的 {@code worldId} 与请求的一致，
 * 万一文件被手工改名 / 复制过，也不会串档。
 *
 * <h2>存储位置</h2>
 * {@code <游戏配置目录>/ommc/chat-history/<指纹>.json}，
 * 配置目录取自 malilib 的 {@link FileUtils#getConfigDirectory()}（OMMC 配置文件就在那一层）。
 */
public final class ChatHistoryStorage {

    /**
     * 存档文件所在的子目录，挂在 OMMC 的配置目录下。
     *
     * <p>放在 {@code ommc} 子目录里与 OMMC 自己的配置文件（{@code config/ommc.json}）作伴，
     * 便于集中管理。
     */
    private static final String SUB_DIR = "ommc" + File.separator + "chat-history";

    /** 最多保留多少条消息。聊天框自己也就显示 100 行，存更多没有意义还占空间。 */
    public static final int MAX_MESSAGES = 100;

    private static final String PREFIX_SINGLEPLAYER = "sp:";
    private static final String PREFIX_MULTIPLAYER = "mp:";

    private ChatHistoryStorage() {
    }

    // ==================== 世界标识 ====================

    /**
     * 判断当前是否处于一个「已进入的世界」。
     *
     * <p>单人看集成服务器是否已起来，多人看连接信息是否存在。
     * 两者都没有时（主菜单、正在连接）不该做任何读写。
     */
    public static boolean inWorld() {
        Minecraft mc = Minecraft.getInstance();
        return mc.hasSingleplayerServer() || mc.getCurrentServer() != null;
    }

    /**
     * 取当前世界的隔离标识，拿不到就返回 {@code null}。
     *
     * <p>单人优先判断：集成服务器在「打开局域网」时 {@code getCurrentServer()} 也可能非空，
     * 若不优先判断单人，同一个存档会因为开没开局域网而被算成两个世界。
     */
    public static String currentWorldId() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.hasSingleplayerServer()) {
            IntegratedServer server = mc.getSingleplayerServer();

            if (server != null) {
                // 存档根目录的绝对路径，例如 F:\...\saves\新的世界
                return PREFIX_SINGLEPLAYER + server.getWorldPath(LevelResource.ROOT).toAbsolutePath();
            }
        }

        ServerData serverData = mc.getCurrentServer();

        if (serverData != null) {
            return PREFIX_MULTIPLAYER + serverData.ip;
        }

        return null;
    }

    /** 当前世界的可读名字，只用于写进文件里方便辨认。 */
    public static String currentWorldName() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.hasSingleplayerServer()) {
            IntegratedServer server = mc.getSingleplayerServer();

            if (server != null) {
                return server.getWorldData().getLevelName();
            }
        }

        ServerData serverData = mc.getCurrentServer();

        if (serverData != null) {
            return serverData.name + " (" + serverData.ip + ")";
        }

        return "unknown";
    }

    // ==================== 读写 ====================

    /**
     * 把已经组织好的存档写到对应世界的文件里。
     *
     * <p>调用方负责塞好 {@code worldId} / {@code worldName} / {@code messages}；
     * 本方法只补上 {@code savedAt} 并落盘。
     *
     * <p>三种结果都会留一条日志 —— 这个功能大部分时间在「退出世界」那一刻默默跑，
     * 若静默 return，出问题时从日志上根本分不出「开关没开」还是「代码没跑」。
     */
    public static void save(String worldId, JsonObject root) {
        if (worldId == null || root == null || !root.has("messages")) {
            OhMyMinecraftClient.LOGGER.warn("聊天记录: 存档内容不完整，跳过保存");
            return;
        }

        JsonElement messages = root.get("messages");

        // 一条消息都没有就别写 —— 免得把上次的记录覆盖成空
        if (!messages.isJsonArray() || messages.getAsJsonArray().isEmpty()) {
            OhMyMinecraftClient.LOGGER.info("聊天记录: 聊天框是空的，跳过保存（不覆盖上次的记录）");
            return;
        }

        root.addProperty("savedAt", System.currentTimeMillis());

        File file = fileFor(worldId);

        File parent = file.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            OhMyMinecraftClient.LOGGER.warn("聊天记录: 无法创建目录 {}", parent);
            return;
        }

        if (JsonUtils.writeJsonToFile(root, file)) {
            OhMyMinecraftClient.LOGGER.info("聊天记录: 已保存 {} 条到 {}",
                    root.getAsJsonArray("messages").size(), file.getAbsolutePath());
        } else {
            OhMyMinecraftClient.LOGGER.warn("聊天记录: 写入失败 {}", file.getAbsolutePath());
        }
    }

    /**
     * 读出指定世界的存档，没有或读不动就返回 {@code null}。
     *
     * <p>失败一律当作「没有存档」并只记一条日志 —— 这个功能是锦上添花，
     * 绝不能因为一个坏掉的 json 让玩家连游戏都进不去。
     *
     * @return 消息数组（元素为 {@code {"content": <组件JSON>, "system": <bool>}}），
     *         同时把世界名塞进返回数组之外是做不到的，所以这里直接返回整个 root
     */
    public static JsonObject load(String worldId) {
        if (worldId == null) {
            return null;
        }

        File file = fileFor(worldId);

        if (!file.isFile()) {
            return null;
        }

        JsonElement parsed = JsonUtils.parseJsonFile(file);

        if (parsed == null || !parsed.isJsonObject()) {
            OhMyMinecraftClient.LOGGER.warn("聊天记录: 解析失败 {}", file.getAbsolutePath());
            return null;
        }

        JsonObject root = parsed.getAsJsonObject();

        // 双保险：文件里记的 worldId 必须与请求的一致
        if (!JsonUtils.hasString(root, "worldId") || !worldId.equals(JsonUtils.getString(root, "worldId"))) {
            OhMyMinecraftClient.LOGGER.warn("聊天记录: {} 的 worldId 不匹配，已忽略", file.getName());
            return null;
        }

        if (!root.has("messages") || !root.get("messages").isJsonArray()
                || root.getAsJsonArray("messages").isEmpty()) {
            return null;
        }

        return root;
    }

    /**
     * 从存档里取出消息数组，并把条数截到 {@link #MAX_MESSAGES}（保留最新的那些）。
     *
     * <p>取末尾而不是开头：存的是时间正序，末尾才是最近的聊天。
     */
    public static List<JsonElement> readMessages(JsonObject root) {
        List<JsonElement> result = new ArrayList<>();

        if (root == null || !root.has("messages") || !root.get("messages").isJsonArray()) {
            return result;
        }

        JsonArray array = root.getAsJsonArray("messages");
        int from = Math.max(0, array.size() - MAX_MESSAGES);

        for (int i = from; i < array.size(); i++) {
            result.add(array.get(i));
        }

        return result;
    }

    /** 存档文件的完整路径。 */
    public static File fileFor(String worldId) {
        return new File(new File(FileUtils.getConfigDirectory(), SUB_DIR), fingerprint(worldId) + ".json");
    }

    /**
     * 用 SHA-1 把世界标识压成一个定长十六进制串当文件名。
     *
     * <p>不直接用世界名：可能含非法字符、可能重名、也可能过长；
     * 而绝对路径与服务器地址本身还带 {@code :} {@code \} 这类字符，根本不能当文件名。
     */
    private static String fingerprint(String worldId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest(worldId.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);

            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }

            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 是 JDK 必备算法，正常不可能走到这里
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }
}
