package com.plusls.ommc.config;

import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.plusls.ommc.OhMyMinecraftClient;
import fi.dy.masa.malilib.config.ConfigUtils;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigHandler;
import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigBooleanHotkeyed;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigInteger;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.util.FileUtils;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.malilib.util.StringUtils;

import java.io.File;

/**
 * OMMC 的全部配置项。
 *
 * <p>分类、条目、默认值都照搬 Fabric 版的 {@code Configs.java}，只是把 magiclib 的注解式配置
 * （{@code @Config} / {@code @Hotkey}）换成了 malilib 原生的显式字段写法 —— mafglib 是 malilib
 * 的移植，没有 magiclib 那套注解。语义上两者一一对应：
 * <ul>
 *   <li>Fabric 版的 {@code @Config boolean x} → {@link ConfigBoolean}</li>
 *   <li>Fabric 版的 {@code @Hotkey @Config boolean x} → {@link ConfigBooleanHotkeyed}</li>
 *   <li>Fabric 版的 {@code @Config ConfigHotkey x} → {@link ConfigHotkey}</li>
 * </ul>
 *
 * <p>界面上的显示名与说明由 malilib 按 {@code config.name.<小写名>} 与
 * {@code config.comment.<小写名>} 去语言文件里查（见 {@code IConfigBase} / {@code ConfigBase}）。
 */
public class Configs implements IConfigHandler {

    private static final String CONFIG_FILE_NAME = OhMyMinecraftClient.MOD_ID + ".json";

    /** 整理仓库时潜影盒的排序位置 */
    public enum ShulkerBoxLastType implements IConfigOptionListEntry {
        FALSE("false", "ommc.gui.label.sort_inventory_shulker_box_last_type.false"),
        TRUE("true", "ommc.gui.label.sort_inventory_shulker_box_last_type.true"),
        AUTO("auto", "ommc.gui.label.sort_inventory_shulker_box_last_type.auto");

        private final String configString;
        private final String translationKey;

        ShulkerBoxLastType(String configString, String translationKey) {
            this.configString = configString;
            this.translationKey = translationKey;
        }

        @Override
        public String getStringValue() {
            return this.configString;
        }

        @Override
        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }

        @Override
        public IConfigOptionListEntry cycle(boolean forward) {
            int id = this.ordinal() + (forward ? 1 : -1);
            if (id >= values().length) {
                id = 0;
            } else if (id < 0) {
                id = values().length - 1;
            }
            return values()[id];
        }

        @Override
        public IConfigOptionListEntry fromString(String value) {
            for (ShulkerBoxLastType type : values()) {
                if (type.configString.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return AUTO;
        }
    }

    // ==================== 通用 ====================
    public static class Generic {
        public static final ConfigHotkey CLEAR_WAYPOINT =
                new ConfigHotkey("clearWaypoint", "C", "A hotkey to clear the highlighted waypoint.");
        public static final ConfigBoolean DEBUG =
                new ConfigBoolean("debug", false, "Print debug messages to the log.");
        public static final ConfigBoolean DONT_CLEAR_CHAT_HISTORY =
                new ConfigBoolean("dontClearChatHistory", false, "Don't clear the chat history and the input history.");
        public static final ConfigBooleanHotkeyed FORCE_PARSE_WAYPOINT_FROM_CHAT =
                new ConfigBooleanHotkeyed("forceParseWaypointFromChat", false, "", "Force parsing waypoints from chat, overriding any existing click event.");
        public static final ConfigHotkey OPEN_CONFIG_GUI =
                new ConfigHotkey("openConfigGui", "O,C", "A hotkey to open the in-game config GUI.");
        public static final ConfigBooleanHotkeyed PARSE_WAYPOINT_FROM_CHAT =
                new ConfigBooleanHotkeyed("parseWaypointFromChat", true, "", "Parse waypoints from chat messages.");
        public static final ConfigHotkey SEND_LOOKING_AT_BLOCK_POS =
                new ConfigHotkey("sendLookingAtBlockPos", "O,P", "A hotkey to send the position of the block you are looking at.");
        public static final ConfigHotkey SORT_INVENTORY =
                new ConfigHotkey("sortInventory", "R", "A hotkey to sort the inventory.");
        public static final ConfigOptionList SORT_INVENTORY_SHULKER_BOX_LAST =
                new ConfigOptionList("sortInventoryShulkerBoxLast", ShulkerBoxLastType.AUTO, "Where to put shulker boxes when sorting the inventory.");
        public static final ConfigBooleanHotkeyed SORT_INVENTORY_SUPPORT_EMPTY_SHULKER_BOX_STACK =
                new ConfigBooleanHotkeyed("sortInventorySupportEmptyShulkerBoxStack", false, "", "Support empty shulker box stacks when sorting the inventory.");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                CLEAR_WAYPOINT,
                DEBUG,
                DONT_CLEAR_CHAT_HISTORY,
                FORCE_PARSE_WAYPOINT_FROM_CHAT,
                OPEN_CONFIG_GUI,
                PARSE_WAYPOINT_FROM_CHAT,
                SEND_LOOKING_AT_BLOCK_POS,
                SORT_INVENTORY,
                SORT_INVENTORY_SHULKER_BOX_LAST,
                SORT_INVENTORY_SUPPORT_EMPTY_SHULKER_BOX_STACK);
    }

    // ==================== 特性开关 ====================
    public static class FeatureToggle {
        public static final ConfigBooleanHotkeyed AUTO_SWITCH_ELYTRA =
                new ConfigBooleanHotkeyed("autoSwitchElytra", false, "", "Automatically switch between the elytra and the chestplate.");
        public static final ConfigBooleanHotkeyed BETTER_SNEAKING =
                new ConfigBooleanHotkeyed("betterSneaking", false, "", "The player can move down one block while sneaking.");
        public static final ConfigBooleanHotkeyed DISABLE_BLOCKLIST_CHECK =
                new ConfigBooleanHotkeyed("disableBlocklistCheck", false, "", "Workaround for MC-218167: keep the network request from blocking the render thread.");
        public static final ConfigBooleanHotkeyed DISABLE_BREAK_BLOCK =
                new ConfigBooleanHotkeyed("disableBreakBlock", false, "", "Prevent breaking the blocks listed in breakBlockBlackList.");
        public static final ConfigBooleanHotkeyed DISABLE_BREAK_SCAFFOLDING =
                new ConfigBooleanHotkeyed("disableBreakScaffolding", false, "", "Only allow breaking scaffolding while holding an item from breakScaffoldingWhiteList.");
        public static final ConfigBooleanHotkeyed DISABLE_MOVE_DOWN_IN_SCAFFOLDING =
                new ConfigBooleanHotkeyed("disableMoveDownInScaffolding", false, "", "Only allow descending in scaffolding while holding an item from moveDownInScaffoldingWhiteList.");
        public static final ConfigBooleanHotkeyed DISABLE_PISTON_PUSH_ENTITY =
                new ConfigBooleanHotkeyed("disablePistonPushEntity", false, "", "Prevent pistons from pushing entities (except the player) on the client to reduce lag. May cause entity position rendering errors.");
        public static final ConfigBooleanHotkeyed FLAT_DIGGER =
                new ConfigBooleanHotkeyed("flatDigger", false, "", "Only allow mining blocks below you while sneaking.");
        public static final ConfigBooleanHotkeyed FORCE_BREAKING_COOLDOWN =
                new ConfigBooleanHotkeyed("forceBreakingCooldown", false, "", "Add a 5gt breaking cooldown after instantly breaking a block.");
        public static final ConfigBooleanHotkeyed HIGHLIGHT_LAVA_SOURCE =
                new ConfigBooleanHotkeyed("highlightLavaSource", false, "", "Highlight lava sources with a special texture.");
        public static final ConfigBooleanHotkeyed HIGHLIGHT_PERSISTENT_MOB =
                new ConfigBooleanHotkeyed("highlightPersistentMob", false, "", "Highlight mobs that will not despawn. Limited by client-side data: only mobs holding items or with custom names can be detected.");
        public static final ConfigBoolean HIGHLIGHT_PERSISTENT_MOB_CLIENT_MODE =
                new ConfigBoolean("highlightPersistentMobClientMode", false, "Use client-side data (held item, custom name) to decide whether a mob will despawn. May misjudge. Keep this off in singleplayer; on servers MasaGadget's syncAllEntityData can be used.");
        public static final ConfigBooleanHotkeyed PREVENT_INTENTIONAL_GAME_DESIGN =
                new ConfigBooleanHotkeyed("preventIntentionalGameDesign", false, "", "Prevent Intentional Game Design (bed and respawn anchor explosions).");
        public static final ConfigBooleanHotkeyed PREVENT_WASTAGE_OF_WATER =
                new ConfigBooleanHotkeyed("preventWastageOfWater", false, "", "Prevent wasting water buckets in the Nether.");
        public static final ConfigBooleanHotkeyed REAL_SNEAKING =
                new ConfigBooleanHotkeyed("realSneaking", false, "", "Prevent ascending or descending non-full blocks (carpets, slabs, stairs) while sneaking.");
        public static final ConfigBooleanHotkeyed REMOVE_BREAKING_COOLDOWN =
                new ConfigBooleanHotkeyed("removeBreakingCooldown", false, "", "Remove the cooldown after breaking a non-instantly-breakable block (5gt by default). Has no effect while forceBreakingCooldown is enabled.");
        public static final ConfigBooleanHotkeyed WORLD_EATER_MINE_HELPER =
                new ConfigBooleanHotkeyed("worldEaterMineHelper", false, "", "Render a mirror image above the blocks in worldEaterMineHelperWhitelist when they are exposed to air.");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                AUTO_SWITCH_ELYTRA,
                BETTER_SNEAKING,
                DISABLE_BLOCKLIST_CHECK,
                DISABLE_BREAK_BLOCK,
                DISABLE_BREAK_SCAFFOLDING,
                DISABLE_MOVE_DOWN_IN_SCAFFOLDING,
                DISABLE_PISTON_PUSH_ENTITY,
                FLAT_DIGGER,
                FORCE_BREAKING_COOLDOWN,
                HIGHLIGHT_LAVA_SOURCE,
                HIGHLIGHT_PERSISTENT_MOB,
                HIGHLIGHT_PERSISTENT_MOB_CLIENT_MODE,
                PREVENT_INTENTIONAL_GAME_DESIGN,
                PREVENT_WASTAGE_OF_WATER,
                REAL_SNEAKING,
                REMOVE_BREAKING_COOLDOWN,
                WORLD_EATER_MINE_HELPER);
    }

    // ==================== 列表 ====================
    public static class Lists {
        public static final ConfigStringList BLOCK_MODEL_NO_OFFSET_BLACKLIST =
                new ConfigStringList("blockModelNoOffsetBlacklist", ImmutableList.of(), "Blacklist for the block model no-offset feature.");
        public static final ConfigOptionList BLOCK_MODEL_NO_OFFSET_LIST_TYPE =
                new ConfigOptionList("blockModelNoOffsetListType", ListType.WHITELIST, "Whether blockModelNoOffsetWhitelist/Blacklist is treated as a whitelist or a blacklist.");
        public static final ConfigStringList BLOCK_MODEL_NO_OFFSET_WHITELIST =
                new ConfigStringList("blockModelNoOffsetWhitelist", ImmutableList.of(
                        "minecraft:wither_rose", "minecraft:poppy", "minecraft:dandelion"),
                        "Whitelist for the block model no-offset feature.");
        public static final ConfigStringList BREAK_BLOCK_BLACKLIST =
                new ConfigStringList("breakBlockBlackList", ImmutableList.of(
                        "minecraft:budding_amethyst", "_bud"),
                        "Blocks that cannot be broken while disableBreakBlock is enabled.");
        public static final ConfigStringList BREAK_SCAFFOLDING_WHITELIST =
                new ConfigStringList("breakScaffoldingWhiteList", ImmutableList.of(
                        "minecraft:air", "minecraft:scaffolding"),
                        "Items that allow breaking scaffolding while disableBreakScaffolding is enabled.");
        public static final ConfigStringList HIGHLIGHT_ENTITY_BLACKLIST =
                new ConfigStringList("highlightEntityBlackList", ImmutableList.of(), "Blacklist for the entity highlight feature.");
        public static final ConfigOptionList HIGHLIGHT_ENTITY_LIST_TYPE =
                new ConfigOptionList("highlightEntityListType", ListType.WHITELIST, "Whether highlightEntityWhiteList/BlackList is treated as a whitelist or a blacklist.");
        public static final ConfigStringList HIGHLIGHT_ENTITY_WHITELIST =
                new ConfigStringList("highlightEntityWhiteList", ImmutableList.of(
                        "minecraft:wandering_trader"),
                        "Whitelist for the entity highlight feature.");
        public static final ConfigStringList MOVE_DOWN_IN_SCAFFOLDING_WHITELIST =
                new ConfigStringList("moveDownInScaffoldingWhiteList", ImmutableList.of(
                        "minecraft:air", "minecraft:scaffolding"),
                        "Items that allow descending in scaffolding while disableMoveDownInScaffolding is enabled.");
        public static final ConfigStringList WORLD_EATER_MINE_HELPER_WHITELIST =
                new ConfigStringList("worldEaterMineHelperWhitelist", ImmutableList.of(
                        "_ore", "minecraft:ancient_debris", "minecraft:obsidian"),
                        "Blocks that get the mirrored render while worldEaterMineHelper is enabled.");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                BLOCK_MODEL_NO_OFFSET_BLACKLIST,
                BLOCK_MODEL_NO_OFFSET_LIST_TYPE,
                BLOCK_MODEL_NO_OFFSET_WHITELIST,
                BREAK_BLOCK_BLACKLIST,
                BREAK_SCAFFOLDING_WHITELIST,
                HIGHLIGHT_ENTITY_BLACKLIST,
                HIGHLIGHT_ENTITY_LIST_TYPE,
                HIGHLIGHT_ENTITY_WHITELIST,
                MOVE_DOWN_IN_SCAFFOLDING_WHITELIST,
                WORLD_EATER_MINE_HELPER_WHITELIST);
    }

    // ==================== 本地服务器设置 ====================
    public static class AdvancedIntegratedServer {
        public static final ConfigBoolean FLIGHT =
                new ConfigBoolean("flight", true, "Allow flight on the integrated server.");
        public static final ConfigBooleanHotkeyed ONLINE_MODE =
                new ConfigBooleanHotkeyed("onlineMode", true, "", "Use online mode (Mojang authentication) on the integrated server.");
        public static final ConfigInteger PORT =
                new ConfigInteger("port", 0, 0, 65535, "LAN port of the integrated server. 0 uses a random port.");
        public static final ConfigBoolean PVP =
                new ConfigBoolean("pvp", true, "Allow PVP on the integrated server.");

        public static final ImmutableList<IConfigBase> OPTIONS = ImmutableList.of(
                FLIGHT,
                ONLINE_MODE,
                PORT,
                PVP);
    }

    /** 需要注册到 malilib 的全部热键（含「布尔 + 热键」组合项里的热键） */
    public static final ImmutableList<IHotkey> ALL_HOTKEYS = ImmutableList.of(
            Generic.CLEAR_WAYPOINT,
            Generic.FORCE_PARSE_WAYPOINT_FROM_CHAT,
            Generic.OPEN_CONFIG_GUI,
            Generic.PARSE_WAYPOINT_FROM_CHAT,
            Generic.SEND_LOOKING_AT_BLOCK_POS,
            Generic.SORT_INVENTORY,
            Generic.SORT_INVENTORY_SUPPORT_EMPTY_SHULKER_BOX_STACK,
            FeatureToggle.AUTO_SWITCH_ELYTRA,
            FeatureToggle.BETTER_SNEAKING,
            FeatureToggle.DISABLE_BLOCKLIST_CHECK,
            FeatureToggle.DISABLE_BREAK_BLOCK,
            FeatureToggle.DISABLE_BREAK_SCAFFOLDING,
            FeatureToggle.DISABLE_MOVE_DOWN_IN_SCAFFOLDING,
            FeatureToggle.DISABLE_PISTON_PUSH_ENTITY,
            FeatureToggle.FLAT_DIGGER,
            FeatureToggle.FORCE_BREAKING_COOLDOWN,
            FeatureToggle.HIGHLIGHT_LAVA_SOURCE,
            FeatureToggle.HIGHLIGHT_PERSISTENT_MOB,
            FeatureToggle.PREVENT_INTENTIONAL_GAME_DESIGN,
            FeatureToggle.PREVENT_WASTAGE_OF_WATER,
            FeatureToggle.REAL_SNEAKING,
            FeatureToggle.REMOVE_BREAKING_COOLDOWN,
            FeatureToggle.WORLD_EATER_MINE_HELPER,
            AdvancedIntegratedServer.ONLINE_MODE);

    // ==================== 读写 ====================

    public static void loadFromFile() {
        File configFile = new File(FileUtils.getConfigDirectory(), CONFIG_FILE_NAME);

        if (configFile.exists() && configFile.isFile() && configFile.canRead()) {
            JsonElement element = JsonUtils.parseJsonFile(configFile);

            if (element != null && element.isJsonObject()) {
                JsonObject root = element.getAsJsonObject();
                ConfigUtils.readConfigBase(root, "Generic", Generic.OPTIONS);
                ConfigUtils.readConfigBase(root, "FeatureToggle", FeatureToggle.OPTIONS);
                ConfigUtils.readConfigBase(root, "Lists", Lists.OPTIONS);
                ConfigUtils.readConfigBase(root, "AdvancedIntegratedServer", AdvancedIntegratedServer.OPTIONS);
            }
        }
    }

    public static void saveToFile() {
        File dir = FileUtils.getConfigDirectory();

        if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) {
            JsonObject root = new JsonObject();
            ConfigUtils.writeConfigBase(root, "Generic", Generic.OPTIONS);
            ConfigUtils.writeConfigBase(root, "FeatureToggle", FeatureToggle.OPTIONS);
            ConfigUtils.writeConfigBase(root, "Lists", Lists.OPTIONS);
            ConfigUtils.writeConfigBase(root, "AdvancedIntegratedServer", AdvancedIntegratedServer.OPTIONS);
            JsonUtils.writeJsonToFile(root, new File(dir, CONFIG_FILE_NAME));
        }
    }

    @Override
    public void load() {
        loadFromFile();
    }

    @Override
    public void save() {
        saveToFile();
    }
}
