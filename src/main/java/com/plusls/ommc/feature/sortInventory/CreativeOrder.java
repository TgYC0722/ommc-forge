package com.plusls.ommc.feature.sortInventory;

import com.plusls.ommc.OhMyMinecraftClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「创造模式物品栏顺序」查询表。
 *
 * <p>整理仓库时若只按注册名排序，同类物品虽然会聚在一起，但<b>相对顺序是随机的</b>
 * （注册名与创造物品栏的编排顺序毫无关系）。本类把创造模式物品栏的编排顺序抓下来，
 * 让整理结果与创造模式里看到的排列一致 —— 找东西时更符合直觉。
 *
 * <h2>顺序从哪来</h2>
 * 原版已经把这件事做好了：{@link CreativeModeTab#getDisplayItems()} 返回的是
 * <b>按既定顺序排好的物品列表</b>。把所有分类标签页按 {@link CreativeModeTabs#allTabs()}
 * 的顺序连起来，就是一份完整的「物品 → 序号」表。
 *
 * <h2>为什么只取 CATEGORY</h2>
 * {@code allTabs()} 返回的不只是分类页，还有几种特殊页，它们的 {@code getDisplayItems()}
 * 要么是空的、要么语义完全不同，混进来会污染顺序：
 * <ul>
 *   <li>{@code SEARCH} —— 搜索页，内容是「所有可搜索物品」的合集；</li>
 *   <li>{@code INVENTORY} —— 生存物品栏页，玩家自己的背包；</li>
 *   <li>{@code HOTBAR} —— 快捷栏页。</li>
 * </ul>
 * 所以只收 {@link CreativeModeTab.Type#CATEGORY}。
 *
 * <h2>⚠ 迭代顺序不保证稳定的坑</h2>
 * {@code displayItems} 的运行时类型是 {@code ObjectLinkedOpenCustomHashSet}
 * （见 {@code ItemStackLinkedSet.createTypeAndTagSet}），其哈希策略 {@code TYPE_AND_TAG}
 * 用的是 {@code ItemStack.hashCode()} 与 {@code CompoundTag.hashCode()}。
 * 也就是说这是一个<b>基于哈希的集合</b>，迭代顺序会随内部表容量 / 扩容时机而变。
 *
 * <p>后果很直接：如果直接拿「遍历到第几个」当序号，<b>同一个箱子整理两次可能得到不同顺序</b>，
 * 而且与箱子里物品的初始摆法纠缠在一起，表现为「整理结果不可复现」。
 *
 * <p>因此本类<b>不直接使用遍历序号</b>，而是先用它建立「物品 → 序号」的原始映射，
 * 再把序号<b>按 (标签页序号, 物品注册名) 重排成稠密排名</b>（见 {@link #normalize}）。
 * 这样即使集合迭代顺序抖动，最终排名也只由「属于哪个标签页」+「注册名字典序」决定，
 * 与迭代顺序无关。
 *
 * <h2>缓存</h2>
 * 建表要遍历上千个 {@code ItemStack}，绝不能放进比较器里现算 ——
 * 比较器在一次排序中会被调用成百上千次。这里做<b>懒加载 + 全量缓存</b>。
 */
public final class CreativeOrder {

    /** 未收录物品的序号 —— 排在所有已知物品之后。 */
    public static final int UNKNOWN_ORDER = Integer.MAX_VALUE;

    /** 物品 → 稳定排名。用 {@link IdentityHashMap}：{@code Item} 是单例且未覆写 {@code equals}。 */
    private static Map<Item, Integer> orderByItem = null;

    private CreativeOrder() {
    }

    /**
     * 取物品的创造模式排名，未收录返回 {@link #UNKNOWN_ORDER}。
     *
     * <p>同一个物品可能出现在多个分类页（例如各类原木既在建筑方块也在自然方块），
     * 取<b>首次出现</b>的那个标签页 —— 也就是它最主要的那一页。
     */
    public static int orderOf(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return UNKNOWN_ORDER;
        }

        return ensureBuilt().getOrDefault(stack.getItem(), UNKNOWN_ORDER);
    }

    /** 表是否已经建好。 */
    public static boolean isBuilt() {
        return orderByItem != null;
    }

    private static Map<Item, Integer> ensureBuilt() {
        if (orderByItem == null) {
            orderByItem = build();
        }

        return orderByItem;
    }

    /**
     * 遍历所有分类标签页建表，并对序号做一次归一化。
     *
     * <p>整体捕获异常：创造物品栏内容由原版在注册表同步后重建，
     * 万一在此时序之外被调用（拿到的标签页还没内容），
     * 也应当是「表建不全」而不是把整理功能整个搞崩。
     */
    private static Map<Item, Integer> build() {
        // 先记录「物品 → 首次出现的原始遍历序号」，同时记下它属于哪个标签页
        Map<Item, Integer> rawOrder = new IdentityHashMap<>();
        Map<Item, Integer> tabOfItem = new IdentityHashMap<>();
        int rawIndex = 0;
        int tabIndex = 0;
        int tabCount = 0;

        try {
            List<CreativeModeTab> tabs = CreativeModeTabs.allTabs();

            for (CreativeModeTab tab : tabs) {
                // 只要普通分类页；搜索页/物品栏页/快捷栏页的内容不是「分类编排」
                if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                    continue;
                }

                tabCount++;
                int currentTab = tabIndex++;

                for (ItemStack stack : tab.getDisplayItems()) {
                    if (stack.isEmpty()) {
                        continue;
                    }

                    Item item = stack.getItem();

                    // 只记首次出现：同一物品出现在多页时，由它最主要的那一页定位
                    if (!rawOrder.containsKey(item)) {
                        rawOrder.put(item, rawIndex++);
                        tabOfItem.put(item, currentTab);
                    }
                }
            }
        } catch (Exception e) {
            OhMyMinecraftClient.LOGGER.warn("整理仓库: 建立创造模式物品顺序表失败，将退回按注册名排序", e);
        }

        Map<Item, Integer> result = normalize(rawOrder, tabOfItem);

        OhMyMinecraftClient.LOGGER.info("整理仓库: 已载入创造模式物品顺序（{} 个分类页，{} 个物品）",
                tabCount, result.size());

        return result;
    }

    /**
     * 把「原始遍历序号」重排成与迭代顺序无关的稠密排名。
     *
     * <p>排序依据是 <b>(标签页序号, 物品注册名)</b>：
     * <ul>
     *   <li>标签页序号来自 {@code allTabs()} 的下标 —— 那是注册顺序，确定的；</li>
     *   <li>同一标签页内按物品注册名的字典序 —— 也完全确定。</li>
     * </ul>
     * 于是无论 {@code ObjectLinkedOpenCustomHashSet} 的迭代顺序怎么抖，
     * 同一组物品永远得到同一套排名。
     *
     * <p>代价是「同一标签页内的精细编排顺序」被字典序取代了。这是<b>刻意的取舍</b>：
     * 稳定且可复现，比「贴近创造物品栏的页内微调」更重要 ——
     * 何况页内的颜色 / 材质类顺序，本来就由整理规则里的颜色表负责。
     */
    private static Map<Item, Integer> normalize(Map<Item, Integer> rawOrder, Map<Item, Integer> tabOfItem) {
        List<Item> items = new ArrayList<>(rawOrder.keySet());

        items.sort(Comparator
                .comparingInt((Item item) -> tabOfItem.getOrDefault(item, Integer.MAX_VALUE))
                .thenComparing(item -> {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);

                    return id == null ? "" : id.toString();
                }));

        Map<Item, Integer> result = new IdentityHashMap<>();

        for (int i = 0; i < items.size(); i++) {
            result.put(items.get(i), i);
        }

        return result;
    }
}
