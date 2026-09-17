package com.plusls.ommc.feature.autoSwitchElytra;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * 自动切换鞘翅的工具方法。
 *
 * <p>逻辑照搬 Fabric 版（oh-my-minecraft-client-nyan-work-dev.49）同名文件，
 * 只有一处因映射体系不同而改名：Fabric 版用 Yarn 名
 * {@code currentScreenHandler != playerScreenHandler}，官方映射下是
 * {@code containerMenu != inventoryMenu}。
 *
 * <p>换装手法与 Fabric 版一致：三次 {@code PICKUP} 点击（拿起 → 放进目标槽 → 放下）。
 * 这是纯客户端操作，由 {@code MultiPlayerGameMode} 转成 {@code ServerboundContainerClickPacket}
 * 发给服务端，因此服务端视角就是玩家真的一次换装，不需要服务端装任何模组。
 */
public class AutoSwitchElytraUtil {

    /** 玩家背包界面里胸甲槽的固定索引（0 合成结果、1-4 合成格、5 头盔、6 胸甲、7 护腿、8 靴子） */
    public static final int CHEST_SLOT_IDX = 6;

    /**
     * 是否处于「适合展开鞘翅」的状态：不在地面、未在滑翔、不在水里、没有漂浮效果。
     */
    public static boolean myCheckFallFlying(Player player) {
        return !player.onGround() && !player.isFallFlying() && !player.isInWater() && !player.hasEffect(MobEffects.LEVITATION);
    }

    /**
     * 在当前打开的容器里找一件满足 {@code check} 的物品，与 {@code sourceSlot} 槽位对调。
     *
     * @param sourceSlot 要被换掉的槽位索引（本特性里固定是胸甲槽）
     * @param check      目标物品的判定条件（找鞘翅、或找胸甲）
     */
    public static void autoSwitch(int sourceSlot, Minecraft client, LocalPlayer clientPlayerEntity, Predicate<ItemStack> check) {
        if (client.gameMode == null) {
            return;
        }

        // 玩家若开着别的界面（箱子等），先关掉，保证下面操作的是玩家背包。
        // 注意顺序：必须先关闭，再取 containerMenu —— 关闭动作会把 containerMenu 复位回 inventoryMenu。
        if (clientPlayerEntity.containerMenu != clientPlayerEntity.inventoryMenu) {
            clientPlayerEntity.closeContainer();
        }

        AbstractContainerMenu screenHandler = clientPlayerEntity.containerMenu;

        // 先做一份快照再查找：点击过程中槽位内容会变，直接在原列表上边找边改会取到错位的物品
        ArrayList<ItemStack> itemStacks = new ArrayList<>();
        for (int i = 0; i < screenHandler.slots.size(); ++i) {
            itemStacks.add(screenHandler.slots.get(i).getItem().copy());
        }

        int idxToSwitch = -1;
        for (int i = 0; i < itemStacks.size(); ++i) {
            if (check.test(itemStacks.get(i))) {
                idxToSwitch = i;
                break;
            }
        }

        if (idxToSwitch != -1) {
            client.gameMode.handleInventoryMouseClick(screenHandler.containerId, idxToSwitch, 0, ClickType.PICKUP, clientPlayerEntity);
            client.gameMode.handleInventoryMouseClick(screenHandler.containerId, sourceSlot, 0, ClickType.PICKUP, clientPlayerEntity);
            client.gameMode.handleInventoryMouseClick(screenHandler.containerId, idxToSwitch, 0, ClickType.PICKUP, clientPlayerEntity);
        }
    }
}
