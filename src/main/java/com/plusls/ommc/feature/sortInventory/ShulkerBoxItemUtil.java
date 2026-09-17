package com.plusls.ommc.feature.sortInventory;

import com.plusls.ommc.config.Configs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 潜影盒相关的判断与比较，供 {@link SortInventoryUtil} 使用。
 *
 * <p>移植自 Fabric 版同名类。与原版的唯一差别是<b>不再依赖 magiclib 的
 * {@code TagCompatApi}</b>：那个常量只是为了兼容老版本 NBT API，
 * 在 1.20.1 上直接用 {@link Tag#TAG_COMPOUND} / {@link Tag#TAG_LIST} 即可。
 */
public final class ShulkerBoxItemUtil {

    /** 开启「支持空潜影盒堆叠」时，空潜影盒被视作可以堆到 64。 */
    public static final int SHULKERBOX_MAX_STACK_AMOUNT = 64;

    private ShulkerBoxItemUtil() {
    }

    /**
     * 判断是不是「空潜影盒」。
     *
     * <p>空的定义是「没有 {@code BlockEntityTag}」或「{@code BlockEntityTag.Items} 为空列表」。
     */
    public static boolean isEmptyShulkerBoxItem(ItemStack itemStack) {
        if (!isShulkerBoxBlockItem(itemStack)) {
            return false;
        }

        CompoundTag nbt = itemStack.getTag();

        if (nbt == null || !nbt.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            return true;
        }

        CompoundTag tag = nbt.getCompound("BlockEntityTag");

        if (tag.contains("Items", Tag.TAG_LIST)) {
            ListTag tagList = tag.getList("Items", Tag.TAG_COMPOUND);

            return tagList.isEmpty();
        }

        return true;
    }

    /** 判断是不是潜影盒方块物品（含未染色的那种）。 */
    public static boolean isShulkerBoxBlockItem(@NotNull ItemStack itemStack) {
        return itemStack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    /**
     * 比较两个潜影盒「装了多少东西」—— 取 {@code BlockEntityTag.Items} 的条目数。
     *
     * <p>返回 {@code aSize - bSize}，所以<b>内容少的（含空盒）排在前面</b>。
     * 注意 {@link SortInventoryUtil.ItemStackComparator} 里是用
     * {@code -compareShulkerBox(...)} 调用的，方向在那里被翻转。
     */
    public static int compareShulkerBox(@Nullable CompoundTag a, @Nullable CompoundTag b) {
        return countItems(a) - countItems(b);
    }

    private static int countItems(@Nullable CompoundTag tag) {
        if (tag == null) {
            return 0;
        }

        CompoundTag blockEntityTag = tag.getCompound("BlockEntityTag");

        if (blockEntityTag.contains("Items", Tag.TAG_LIST)) {
            return blockEntityTag.getList("Items", Tag.TAG_COMPOUND).size();
        }

        return 0;
    }

    /**
     * 该物品在「整理合并」时最多能堆到多少。
     *
     * <p>普通物品就是原版的 {@code getMaxStackSize()}（通常 64，工具是 1）。
     * 但若开启了「整理仓库时支持空潜影盒堆叠」，<b>空潜影盒</b>会被当作能堆 64 ——
     * 原版潜影盒无论空的与否都是 1，这个开关就是绕开它，
     * 让 27 个空盒子能自动并成一组（配合 PCA 之类的模组使用时才真正有意义）。
     */
    public static int getMaxCount(ItemStack itemStack) {
        if (Configs.Generic.SORT_INVENTORY_SUPPORT_EMPTY_SHULKER_BOX_STACK.getBooleanValue()
                && isEmptyShulkerBoxItem(itemStack)) {
            return SHULKERBOX_MAX_STACK_AMOUNT;
        }

        return itemStack.getMaxStackSize();
    }

    /**
     * 判断能否参与合并。
     *
     * <p>两个条件：能堆叠（上限 &gt; 1），以及「不是已损坏的可损坏物品」——
     * 后者是为了不把两把用过的剑并到一起。
     */
    public static boolean isStackable(ItemStack itemStack) {
        return getMaxCount(itemStack) > 1 && (!itemStack.isDamageableItem() || !itemStack.isDamaged());
    }
}
