package com.plusls.ommc.feature.sortInventory;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.WoolCarpetBlock;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 整理仓库：把鼠标所指那一格所在的「连续同类容器区段」按固定规则重排。
 *
 * <p>移植自 Fabric 版同名类。整体流程：
 * <ol>
 *   <li>{@link #sort()} —— 取鼠标所在槽位，算出要整理的区段；</li>
 *   <li>把所有槽位物品 {@code copy()} 出一个「影子物品栏」，先把手上的物品合并进去，
 *       再从后往前做同类堆叠合并（{@link #mergeItems}）；</li>
 *   <li>对影子物品栏做快速排序，算出需要交换的槽位对（{@link #quickSort}）；</li>
 *   <li>{@link #doClick} 用<b>模拟鼠标点击</b>把这两串队列真正执行出去。</li>
 * </ol>
 *
 * <h2>排序规则</h2>
 * 由 {@link ItemStackComparator} 定义，按优先级见该类注释。
 *
 * <h2>与 Fabric 版的差异</h2>
 * <ul>
 *   <li>{@code Item.getId(Item)} 在 1.20.1 已废弃，改用
 *       {@link BuiltInRegistries#ITEM}{@code .getId(...)}；</li>
 *   <li>{@code ItemStackCompatApi.isSameItemSameTags} 是 magiclib 的兼容包装，
 *       这里直接用原版的 {@link ItemStack#isSameItemSameTags}；</li>
 *   <li>取鼠标所指槽位改用公开的 {@link AbstractContainerScreen#getSlotUnderMouse()}，
 *       <b>不再需要 {@code @Invoker} 去调私有的 {@code findSlot}</b>；</li>
 *   <li>取染色方块的颜色改用 {@link DyeBlockColors}（反射），
 *       <b>不再需要 6 个 {@code @Mixin implements IDyeBlock}</b>。</li>
 * </ul>
 * 也就是说整个功能<b>不含任何 Mixin</b>。
 */
public final class SortInventoryUtil {

    /** 鼠标不在任何槽位上时，原版 {@code handleInventoryMouseClick} 用的哨兵值。 */
    public static final int SLOT_CLICKED_OUTSIDE = -999;

    /** 本次排序的区段是否「全都是潜影盒」，供 AUTO 模式判断。 */
    private static boolean allShulkerBox;

    /** 染料色 → 排序序号。见 {@link #fillColorMappings()} 的注释。 */
    private static final Map<DyeColor, Integer> DYE_COLOR_MAPPING = Maps.newHashMap();

    /** 地图色 → 排序序号。 */
    private static final Map<MapColor, Integer> MAP_COLOR_MAPPING = Maps.newHashMap();

    static {
        fillColorMappings();
    }

    private SortInventoryUtil() {
    }

    /**
     * 建立两张颜色顺序表。
     *
     * <p>顺序是<b>手工排的视觉渐变</b>，不是按枚举序号、也不是按注册 ID：
     * 白 → 浅灰 → 灰 → 黑 → 棕 → 红 → 橙 → 黄 → 黄绿 → 绿 → 青 → 淡蓝 → 蓝 → 紫 → 品红 → 粉。
     *
     * <p>{@code null} 映射到 0，代表「没有颜色」，所以未染色的潜影盒会排在最前面。
     *
     * <p>{@code MAP_COLOR_MAPPING} 里的 <b>陶瓦色被刻意映射到与染料一致的序号</b>
     * （{@code TERRACOTTA_WHITE} → 1，与 {@code DyeColor.WHITE} 同号），
     * 这样羊毛和陶瓦放到一起排时颜色能对齐，而不是各排各的。
     */
    private static void fillColorMappings() {
        DYE_COLOR_MAPPING.put(null, 0);
        DYE_COLOR_MAPPING.put(DyeColor.WHITE, 1);
        DYE_COLOR_MAPPING.put(DyeColor.LIGHT_GRAY, 2);
        DYE_COLOR_MAPPING.put(DyeColor.GRAY, 3);
        DYE_COLOR_MAPPING.put(DyeColor.BLACK, 4);
        DYE_COLOR_MAPPING.put(DyeColor.BROWN, 5);
        DYE_COLOR_MAPPING.put(DyeColor.RED, 6);
        DYE_COLOR_MAPPING.put(DyeColor.ORANGE, 7);
        DYE_COLOR_MAPPING.put(DyeColor.YELLOW, 8);
        DYE_COLOR_MAPPING.put(DyeColor.LIME, 9);
        DYE_COLOR_MAPPING.put(DyeColor.GREEN, 10);
        DYE_COLOR_MAPPING.put(DyeColor.CYAN, 11);
        DYE_COLOR_MAPPING.put(DyeColor.LIGHT_BLUE, 12);
        DYE_COLOR_MAPPING.put(DyeColor.BLUE, 13);
        DYE_COLOR_MAPPING.put(DyeColor.PURPLE, 14);
        DYE_COLOR_MAPPING.put(DyeColor.MAGENTA, 15);
        DYE_COLOR_MAPPING.put(DyeColor.PINK, 16);

        MAP_COLOR_MAPPING.put(null, 0);
        MAP_COLOR_MAPPING.put(MapColor.SNOW, 1);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_LIGHT_GRAY, 2);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_GRAY, 3);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_BLACK, 4);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_BROWN, 5);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_RED, 6);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_ORANGE, 7);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_YELLOW, 8);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_LIGHT_GREEN, 9);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_GREEN, 10);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_CYAN, 11);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_LIGHT_BLUE, 12);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_BLUE, 13);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_PURPLE, 14);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_MAGENTA, 15);
        MAP_COLOR_MAPPING.put(MapColor.COLOR_PINK, 16);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_WHITE, 1);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_LIGHT_GRAY, 2);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_GRAY, 3);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_BLACK, 4);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_BROWN, 5);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_RED, 6);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_ORANGE, 7);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_YELLOW, 8);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_LIGHT_GREEN, 9);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_GREEN, 10);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_CYAN, 11);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_LIGHT_BLUE, 12);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_BLUE, 13);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_PURPLE, 14);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_MAGENTA, 15);
        MAP_COLOR_MAPPING.put(MapColor.TERRACOTTA_PINK, 16);
    }

    // ==================== 区段判定 ====================

    /**
     * 算出「鼠标所指那一格」所属的整理区段 {@code [l, r)}，无法整理时返回 {@code null}。
     *
     * <p>做法是<b>以鼠标槽位为锚点向两侧扩展</b>，只要相邻槽位的
     * {@code slot.container} 是同一个对象就继续，从而得到一段连续的同类容器。
     *
     * <p>随后对玩家背包做特判 —— 这一步很关键，否则在背包界面里整理会把
     * 合成格、盔甲格、副手格一起卷进来：
     * <ul>
     *   <li>整个背包区段恰好是 {@code [5, 46)}（原版背包界面的完整玩家区）时：
     *       鼠标在<b>主背包</b>（9-35）就只整理这 27 格；在<b>快捷栏</b>（36-44）
     *       就只整理那 9 格；鼠标在盔甲/副手等位置则<b>不整理</b>（返回 null）。</li>
     *   <li>区段长度正好是 36（整个玩家背包）时，按鼠标落在前三排还是后一排拆成
     *       主背包 27 格 / 快捷栏 9 格。</li>
     * </ul>
     */
    @Nullable
    public static Tuple<Integer, Integer> getSortRange(AbstractContainerMenu screenHandler, @NotNull Slot mouseSlot) {
        int mouseIdx = mouseSlot.index;

        // 合成结果格的 index 与 containerSlot 不同，这里以 containerSlot 为准
        if (mouseIdx == 0 && mouseSlot.getContainerSlot() != 0) {
            mouseIdx = mouseSlot.getContainerSlot();
        }

        int l = mouseIdx;
        int r = mouseIdx + 1;

        Class<?> clazz = screenHandler.slots.get(mouseIdx).container.getClass();

        for (int i = mouseIdx - 1; i >= 0; i--) {
            if (clazz != screenHandler.slots.get(i).container.getClass()) {
                l = i + 1;
                break;
            } else if (i == 0) {
                l = 0;
            }
        }

        for (int i = mouseIdx + 1; i < screenHandler.slots.size(); i++) {
            if (clazz != screenHandler.slots.get(i).container.getClass()) {
                r = i;
                break;
            } else if (i == screenHandler.slots.size() - 1) {
                r = screenHandler.slots.size();
            }
        }

        if (mouseSlot.container instanceof Inventory) {
            if (l == 5 && r == 46) {
                // 原版背包：9-35 是主背包，36-44 是快捷栏，其余（合成/盔甲/副手）不整理
                if (mouseIdx >= 9 && mouseIdx < 36) {
                    return new Tuple<>(9, 36);
                } else if (mouseIdx >= 36 && mouseIdx < 45) {
                    return new Tuple<>(36, 45);
                }

                return null;
            } else if (r - l == 36) {
                // 其它界面里暴露出来的完整玩家背包，同样按前三排 / 后一排拆
                if (mouseIdx >= l && mouseIdx < l + 27) {
                    return new Tuple<>(l, l + 27);
                } else {
                    return new Tuple<>(l + 27, r);
                }
            }
        }

        // 区段只有一格，没什么可整理的
        if (l + 1 == r) {
            return null;
        }

        return new Tuple<>(l, r);
    }

    // ==================== 入口 ====================

    /**
     * 执行一次整理。
     *
     * <p>返回一个「事后播放音效」的 Runnable，交给调用方在点击全部发完之后执行 ——
     * 排上号了放按钮音，没东西可整放发射器失败音。
     * 界面不是容器界面、或者鼠标没指在可整理的区段上时返回 {@code null}。
     */
    @Nullable
    public static Runnable sort() {
        Minecraft client = Minecraft.getInstance();

        // 创造模式物品栏不整理：那是无限物品页，没有「堆叠」的概念
        if (!(client.screen instanceof AbstractContainerScreen<?> handledScreen)
                || client.screen instanceof CreativeModeInventoryScreen) {
            return null;
        }

        Slot mouseSlot = handledScreen.getSlotUnderMouse();

        if (mouseSlot == null) {
            return null;
        }

        LocalPlayer player = client.player;

        if (client.gameMode == null || player == null) {
            return null;
        }

        AbstractContainerMenu screenHandler = player.containerMenu;
        Tuple<Integer, Integer> sortRange = getSortRange(screenHandler, mouseSlot);

        if (sortRange == null) {
            return null;
        }

        List<ItemStack> itemStacks = Lists.newArrayList();

        // 影子物品栏：拿所有槽位物品的副本，先在内存里算出「移动方案」，再通过模拟点击落实
        ItemStack cursorStack = screenHandler.getCarried().copy();
        screenHandler.slots.stream().map(slot -> slot.getItem().copy()).forEach(itemStacks::add);

        // 先把手上的物品并进容器，免得它干扰后续的合并计算
        List<Integer> mergeQueue = mergeItems(cursorStack, itemStacks, sortRange.getA(), sortRange.getB());
        List<Tuple<Integer, Integer>> swapQueue = quickSort(itemStacks, sortRange.getA(), sortRange.getB());

        doClick(player, screenHandler.containerId, client.gameMode, mergeQueue, swapQueue);

        boolean changed = !mergeQueue.isEmpty() || !swapQueue.isEmpty();

        // 两种音效的类型不同，不能写成三元表达式：
        // DISPENSER_FAIL 是 SoundEvent，而 UI_BUTTON_CLICK 是 Holder.Reference<SoundEvent>
        return changed
                ? () -> playUiSound(SoundEvents.UI_BUTTON_CLICK)
                : () -> playUiSound(SoundEvents.DISPENSER_FAIL);
    }

    /** 播放一声界面音效。 */
    private static void playUiSound(net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
    }

    /** {@link #playUiSound} 的 {@code SoundEvent} 重载（同样是类型不同的缘故）。 */
    private static void playUiSound(net.minecraft.sounds.SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
    }

    /**
     * 把内存里算好的两串队列，翻译成真实的鼠标点击序列。
     *
     * <p>合并队列是单次点击：把某格的物品捡起来 / 放下。
     * <b>负数表示右键</b>（{@code -slotId}）—— 原版里往打捆包里塞东西必须用右键；
     * 而 {@link #SLOT_CLICKED_OUTSIDE} 这个特殊的负数是原版约定的「点到界面外」。
     *
     * <p>交换队列是标准的三次点击：<b>左键 A → 左键 B → 左键 A</b>。
     * 第一次把 A 捡到手上，第二次放到 B（此时手上的 A 换成了 B 原本的东西），
     * 第三次把手上那份放回 A —— 于是两格互换。
     */
    public static void doClick(Player player, int syncId, @NotNull MultiPlayerGameMode interactionManager,
                               @NotNull List<Integer> mergeQueue, List<Tuple<Integer, Integer>> swapQueue) {
        for (Integer slotId : mergeQueue) {
            if (slotId < 0 && slotId != SLOT_CLICKED_OUTSIDE) {
                // 放入打捆包需要右键
                interactionManager.handleInventoryMouseClick(syncId, -slotId, 1, ClickType.PICKUP, player);
            } else {
                interactionManager.handleInventoryMouseClick(syncId, slotId, 0, ClickType.PICKUP, player);
            }
        }

        for (Tuple<Integer, Integer> slotIdPair : swapQueue) {
            interactionManager.handleInventoryMouseClick(syncId, slotIdPair.getA(), 0, ClickType.PICKUP, player);
            interactionManager.handleInventoryMouseClick(syncId, slotIdPair.getB(), 0, ClickType.PICKUP, player);
            interactionManager.handleInventoryMouseClick(syncId, slotIdPair.getA(), 0, ClickType.PICKUP, player);
        }
    }

    // ==================== 合并 ====================

    /** 已经被拿走的那一格在影子里用空气占位，这样后续合并不会重复搬到它上面。 */
    private static boolean canStackAddMore(@NotNull ItemStack existingStack, ItemStack stack) {
        return !existingStack.isEmpty()
                && ItemStack.isSameItemSameTags(existingStack, stack)
                && ShulkerBoxItemUtil.isStackable(existingStack)
                && existingStack.getCount() < ShulkerBoxItemUtil.getMaxCount(existingStack)
                // 空潜影盒「可堆 64」是假的（原版上限仍是 1），所以再用 64 兜一道
                && existingStack.getCount() < 64;
    }

    /**
     * 把 {@code stackToAdd} 尽量合并进 {@code [beginSlot, endSlot)} 里已有的同类堆叠，
     * 返回需要点击的槽位序列。
     *
     * <p>注意它<b>同时修改</b> {@code itemStacks}（影子）与 {@code stackToAdd} 的数量 ——
     * 相当于在内存里预演一遍，真实点击由调用方按返回的队列发出。
     */
    public static @NotNull List<Integer> addItemStack(List<ItemStack> itemStacks, ItemStack stackToAdd,
                                                      int beginSlot, int endSlot) {
        List<Integer> ret = Lists.newArrayList();

        for (int i = beginSlot; i < endSlot; i++) {
            ItemStack stack = itemStacks.get(i);

            if (stack.isEmpty()) {
                continue;
            }

            if (canStackAddMore(stack, stackToAdd)) {
                int addNum = ShulkerBoxItemUtil.getMaxCount(stack) - stack.getCount();

                if (addNum <= 0) {
                    continue;
                }

                ret.add(i);

                if (addNum >= stackToAdd.getCount()) {
                    // 目标格能装下剩余全部，合并完就结束
                    stack.grow(stackToAdd.getCount());
                    stackToAdd.shrink(stackToAdd.getCount());
                    break;
                } else {
                    // 装不下，装满这一格继续找下一格
                    stack.grow(addNum);
                    stackToAdd.shrink(addNum);
                }
            }
        }

        return ret;
    }

    /**
     * 合并同类堆叠，返回需要点击的槽位序列。
     *
     * <p>顺序很讲究：<b>先手牌、再倒序遍历</b>。
     * <ul>
     *   <li>手牌先并入容器，免得它在后续每轮判断里都要单独考虑；</li>
     *   <li>倒序遍历配合「把当前格先置空、再拿它去和<b>它前面</b>的格子合并」，
     *       能保证每个物品只被搬一次，且最终「少的往后、多的往前」。</li>
     * </ul>
     * 最后若手上还有剩，找一个空格放进去。
     */
    private static @NotNull List<Integer> mergeItems(@NotNull ItemStack cursorStack, List<ItemStack> targetItemStacks,
                                                     int beginSlot, int endSlot) {
        List<Integer> ret = Lists.newArrayList();

        // 先把手中的物品尽量的放入背包或容器中，从而保证后续的整理不会被手中物品合并而影响
        if (!cursorStack.isEmpty()) {
            ret.addAll(addItemStack(targetItemStacks, cursorStack, beginSlot, endSlot));
        }

        for (int i = endSlot - 1; i >= beginSlot; i--) {
            ItemStack stack = targetItemStacks.get(i);

            if (stack.isEmpty()) {
                continue;
            }

            // 先把这一格从影子里挖空，避免 addItemStack 又把它自己当目标
            targetItemStacks.set(i, new ItemStack(Blocks.AIR));
            List<Integer> addItemStackClickList = addItemStack(targetItemStacks, stack, beginSlot, i + 1);

            if (!addItemStackClickList.isEmpty()) {
                ret.add(i);
                ret.addAll(addItemStackClickList);

                if (!stack.isEmpty()) {
                    // 没合并完，剩下的还放回原格
                    ret.add(i);
                    targetItemStacks.set(i, stack);
                } else if (!cursorStack.isEmpty()) {
                    // 全并完了，而手上还有东西 —— 顺手把它放到这个空出来的格子里
                    targetItemStacks.set(i, cursorStack);
                }
            } else {
                // 没找到可合并的目标，原样放回
                targetItemStacks.set(i, stack);
            }
        }

        // 在合并完后如果鼠标还有物品则尝试把鼠标的物品放进容器或箱子
        if (!cursorStack.isEmpty()) {
            for (int i = beginSlot; i < endSlot; i++) {
                if (targetItemStacks.get(i).isEmpty()) {
                    ret.add(i);
                    targetItemStacks.set(i, cursorStack.copy());
                    cursorStack.setCount(0);
                    break;
                }
            }
        }

        return ret;
    }

    // ==================== 排序 ====================

    /**
     * 对影子物品栏的 {@code [startSlot, endSlot)} 排序，返回需要交换的槽位对。
     *
     * <p>并不是真的在槽位之间做交换排序，而是：
     * <ol>
     *   <li>先给区段内的物品副本排序，得到「目标顺序」{@code sortedItemStacks}；</li>
     *   <li><b>倒序遍历每个位置</b>，看该位置当前放的物品是否就是目标物品；
     *       不是就找出目标物品现在在哪（{@code dstIdx}），把两者对调。</li>
     * </ol>
     *
     * <p><b>为什么倒序</b>：交换会把物品往前搬，正序遍历时后面的位置会被前面的操作破坏；
     * 倒序能保证已经摆好的位置不再被动。原版注释也点明了目的 ——
     * 「确保少的方块放在后面，多的方块放在前面」。
     *
     * <p>交换方向按数量决定（{@code count} 少的那格作为第一参数）：
     * 这样模拟点击时「左键 A → 左键 B → 左键 A」三次下来，
     * 手上先拿起量少的，落到量多的格子上，与堆叠上限的判定方向一致。
     */
    private static @NotNull List<Tuple<Integer, Integer>> quickSort(List<ItemStack> itemStacks,
                                                                    int startSlot, int endSlot) {
        List<Tuple<Integer, Integer>> ret = Lists.newArrayList();
        List<ItemStack> sortedItemStacks = Lists.newArrayList();
        allShulkerBox = true;

        for (int i = startSlot; i < endSlot; ++i) {
            ItemStack itemStack = itemStacks.get(i);

            // 记下这个区段是不是「清一色潜影盒」，供 AUTO 模式决定要不要把它们排到最后
            if (!itemStack.isEmpty() && !ShulkerBoxItemUtil.isShulkerBoxBlockItem(itemStack)) {
                allShulkerBox = false;
            }

            sortedItemStacks.add(itemStack);
        }

        sortedItemStacks.sort(new ItemStackComparator());

        // 倒序遍历来确保少的方块放在后面，多的方块放在前面
        for (int i = endSlot - 1; i >= startSlot; i--) {
            ItemStack dstStack = sortedItemStacks.get(i - startSlot);
            int dstIdx = -1;

            if (itemStacks.get(i) != dstStack) {
                for (int j = startSlot; j < endSlot; j++) {
                    if (itemStacks.get(j) == dstStack) {
                        dstIdx = j;
                        break;
                    }
                }

                if (dstIdx == -1) {
                    // 理论上不会发生（目标物品必然还在区段里）；真发生了就跳过这一格
                    OhMyMinecraftClient.LOGGER.warn("整理仓库: 影子物品栏状态异常，跳过一格");
                    continue;
                }

                if (itemStacks.get(i).getCount() < dstStack.getCount()) {
                    ret.add(new Tuple<>(dstIdx, i));
                } else {
                    ret.add(new Tuple<>(i, dstIdx));
                }

                // 同步更新影子，保证后续轮次看到的是交换后的状态
                itemStacks.set(dstIdx, itemStacks.get(i));
                itemStacks.set(i, dstStack);
            }
        }

        return ret;
    }

    // ==================== 比较器 ====================

    /**
     * 物品排序规则。<b>按下面的顺序依次判断，先命中的先用。</b>
     *
     * <ol>
     *   <li><b>潜影盒放最后</b>：开关为 {@code TRUE} 时总是；
     *       {@code AUTO} 时仅当区段里混有非潜影盒物品；
     *       {@code FALSE} 则不特殊处理。</li>
     *   <li><b>同种潜影盒按内容物多少</b>：{@code -compareShulkerBox}，
     *       于是空盒在前、装得多的在后。</li>
     *   <li><b>染色方块按 16 色顺序</b>：床 / 潜影盒 / 染色玻璃 / 玻璃板 / 地毯 / 旗帜，
     *       颜色由 {@link DyeBlockColors} 反射取出。</li>
     *   <li><b>羊毛 / 陶瓦 / 混凝土 / 蜡烛按地图色</b>：靠<b>注册名包含关键字</b>识别，
     *       再查 {@code MAP_COLOR_MAPPING}。</li>
     *   <li><b>染料按颜色</b>。</li>
     *   <li><b>空格排最后</b>。</li>
     *   <li><b>同物品时</b>：有 NBT 的在前；都有 NBT 则按 NBT 哈希排（让同类不同 NBT 的相邻）；
     *       最后<b>数量多的在前</b>。</li>
     *   <li><b>【本移植新增】按创造模式物品栏的编排顺序</b>（{@link CreativeOrder}）——
     *       让整理结果与创造模式里看到的排列一致。未收录的物品排在已收录的之后。</li>
     *   <li>兜底：两方都未收录时按注册名排序，保证结果确定。</li>
     * </ol>
     *
     * <p>关于第 8 条的位置：它<b>排在第 3、4 条之后</b>是有意的 ——
     * 16 色床、羊毛、陶瓦这些在创造物品栏里本就是按颜色编排的，
     * 交给前面那两张颜色表更精确（创造顺序表只知道「床在第几页第几位」，
     * 而颜色表能保证同一色系严格相邻）。
     */
    static class ItemStackComparator implements Comparator<ItemStack> {

        @Override
        public int compare(ItemStack a, ItemStack b) {
            boolean sameItem = a.getItem() == b.getItem();

            // 1) 潜影盒放最后
            if (Configs.Generic.SORT_INVENTORY_SHULKER_BOX_LAST.getOptionListValue() == Configs.ShulkerBoxLastType.TRUE
                    || (Configs.Generic.SORT_INVENTORY_SHULKER_BOX_LAST.getOptionListValue() == Configs.ShulkerBoxLastType.AUTO
                    && !allShulkerBox)) {
                boolean aShulker = ShulkerBoxItemUtil.isShulkerBoxBlockItem(a);
                boolean bShulker = ShulkerBoxItemUtil.isShulkerBoxBlockItem(b);

                if (aShulker && !bShulker) {
                    return 1;
                } else if (!aShulker && bShulker) {
                    return -1;
                }
            }

            // 2) 同种潜影盒：内容少的在前
            if (ShulkerBoxItemUtil.isShulkerBoxBlockItem(a) && ShulkerBoxItemUtil.isShulkerBoxBlockItem(b)
                    && a.getItem() == b.getItem()) {
                return -ShulkerBoxItemUtil.compareShulkerBox(a.getTag(), b.getTag());
            }

            if (a.getItem() instanceof BlockItem blockItemA && b.getItem() instanceof BlockItem blockItemB) {
                Block blockA = blockItemA.getBlock();
                Block blockB = blockItemB.getBlock();

                // 3) 染色方块（床/潜影盒/染色玻璃/玻璃板/地毯/旗帜）：按 16 色标准顺序
                if (isDyeBlock(blockA) && isDyeBlock(blockB)) {
                    return DYE_COLOR_MAPPING.getOrDefault(DyeBlockColors.getColor(blockA), 0)
                            - DYE_COLOR_MAPPING.getOrDefault(DyeBlockColors.getColor(blockB), 0);
                }

                // 4) 羊毛/陶瓦/混凝土/蜡烛：注册名含关键字，按地图色
                String ida = blockRegistryPath(blockA);
                String idb = blockRegistryPath(blockB);

                if (bothContains("wool", ida, idb)
                        || bothContains("terracotta", ida, idb)
                        || bothContains("concrete", ida, idb)
                        || bothContains("candle", ida, idb)) {
                    return MAP_COLOR_MAPPING.getOrDefault(blockA.defaultMapColor(), 0)
                            - MAP_COLOR_MAPPING.getOrDefault(blockB.defaultMapColor(), 0);
                }
            }

            // 5) 染料按颜色
            if (a.getItem() instanceof DyeItem dyeA && b.getItem() instanceof DyeItem dyeB) {
                return DYE_COLOR_MAPPING.getOrDefault(dyeA.getDyeColor(), 0)
                        - DYE_COLOR_MAPPING.getOrDefault(dyeB.getDyeColor(), 0);
            }

            // 6) 空格排最后
            if (a.isEmpty() && !b.isEmpty()) {
                return 1;
            } else if (!a.isEmpty() && b.isEmpty()) {
                return -1;
            } else if (a.isEmpty()) {
                return 0;
            }

            if (sameItem) {
                // 7) 同物品：有 NBT 的排前面
                if (!a.hasTag() && b.hasTag()) {
                    return 1;
                } else if (a.hasTag() && !b.hasTag()) {
                    return -1;
                } else if (a.hasTag()) {
                    // 都有 nbt：按 NBT 的序列化文本排。
                    //
                    // 这里刻意【不用】原版的 `Comparator.comparingInt(CompoundTag::hashCode)`：
                    // 哈希只是把顺序变得「看起来确定」，一旦碰撞两个不同的 NBT 就再也分不开，
                    // 排序结果会退化成依赖初始位置 —— 正是「同物品在箱子里换个摆法、整理结果就变」
                    // 的成因。用完整文本比较，代价只是同物品之间多几次字符串比较（很少发生）。
                    int byTag = a.getTag().toString().compareTo(b.getTag().toString());

                    if (byTag != 0) {
                        return byTag;
                    }
                }

                // 物品少的排在后面
                return b.getCount() - a.getCount();
            }

            // 8) 【本移植新增】按创造模式物品栏的编排顺序。
            // 放在染色/羊毛规则【之后】：那两类物品（16 色床、羊毛、陶瓦……）
            // 在创造物品栏里本来就是按颜色排的，交给它们自己那套更准；
            // 而放在注册名之前，是为了让其余物品的先后与创造模式里看到的一致。
            int aCreative = CreativeOrder.orderOf(a);
            int bCreative = CreativeOrder.orderOf(b);

            if (aCreative != bCreative) {
                // 有一方未收录时，已收录的排前面
                return Integer.compare(aCreative, bCreative);
            }

            // 9) 兜底：按注册名排序，保证结果确定
            return registryNameOf(a).compareTo(registryNameOf(b));
        }

        /**
         * 物品的创建模式排名。
         *
         * <p>Fabric 版用的是 {@code Item.getId(Item)}（取注册表里的数值 ID），
         * 但它在 1.20.1 已废弃；它的两个替代品（{@code BuiltInRegistries}、
         * {@code Item.builtInRegistryHolder()}）<b>也都被标成了废弃</b>。
         * 未废弃的入口是 Forge 自己的 {@link ForgeRegistries}，所以这里走它取<b>注册名</b>。
         */
        private static int getItemSortKey(@NotNull ItemStack itemStack) {
            return CreativeOrder.orderOf(itemStack);
        }

        /**
         * 物品注册名，用作最终的确定性排序键。
         *
         * <p>刻意返回<b>字符串</b>而不是它的 {@code hashCode()}：本类原先用哈希当排序键，
         * 一旦两个不同物品的哈希相同，比较器就会返回 0，它们的先后便退化为「由初始位置决定」。
         * 改为直接比较注册名字符串后，任何两个不同物品都必然分出先后，
         * 排序结果与物品在箱子里的摆法彻底无关。
         */
        private static String registryNameOf(@NotNull ItemStack itemStack) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(itemStack.getItem());

            return id == null ? "" : id.toString();
        }
    }

    /** 取方块的注册名路径（{@code namespace:path} 里的 {@code path}），用于「名字里含 wool/terracotta/...」的判断。 */
    private static String blockRegistryPath(@NotNull Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);

        return id == null ? "" : id.getPath();
    }

    /**
     * 判断是不是「有染料色的方块」。
     *
     * <p>Fabric 版是靠 {@code block instanceof IDyeBlock} 判断的，实现方是 6 个 Mixin。
     * 本移植改用反射取颜色（见 {@link DyeBlockColors}），所以这里改为按类判定。
     *
     * <p><b>为什么不直接看 {@link DyeBlockColors#getColor} 是否为 {@code null}</b>：
     * 未染色的潜影盒颜色就是 {@code null}，而 Fabric 版的语义是
     * 「<b>两个方块都是染色方块类型</b>就按颜色排」—— 未染色的那个查表得 0 排最前，
     * 也就是说未染色潜影盒之间、以及它们与染色潜影盒之间仍要参与颜色排序，
     * 不能因为拿到 null 就跳过这一步。
     */
    private static boolean isDyeBlock(@NotNull Block block) {
        return block instanceof BedBlock
                || block instanceof ShulkerBoxBlock
                || block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock
                || block instanceof WoolCarpetBlock
                || block instanceof AbstractBannerBlock;
    }

    private static boolean bothContains(String target, @NotNull String a, @NotNull String b) {
        return a.contains(target) && b.contains(target);
    }
}
