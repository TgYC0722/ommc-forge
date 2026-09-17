package com.plusls.ommc.util;

import net.minecraft.client.Minecraft;

/**
 * 客户端渲染刷新工具。
 *
 * <p>有些特性依赖<b>区块重建</b>才能生效 —— 典型的是那些改变流体/方块渲染外观的特性。
 * 原因是方块与流体的几何数据是在<b>区块构建期</b>被烘进区块的顶点缓冲的
 * （例如 {@code LiquidBlockRenderer.tesselate} 只在构建区块时被调用，不是每帧渲染），
 * 所以开关切换后已构建的区块不会自己重画，玩家看不到任何变化。
 *
 * <p>对应 Fabric 版配置初始化里的 {@code Minecraft.getInstance().levelRenderer.allChanged()}。
 * 目前有两个特性需要它：
 * <ul>
 *   <li>{@code highlightLavaSource} —— 换掉岩浆源头的贴图</li>
 *   <li>{@code worldEaterMineHelper} —— 给白名单方块渲染镜像</li>
 * </ul>
 */
public final class ClientRenderUtils {

    private ClientRenderUtils() {
    }

    /**
     * 强制重建全部区块渲染数据，让依赖区块重建的特性立即生效。
     *
     * <p>调用方不必自己判空：配置值变更回调有可能在配置加载阶段就触发，
     * 那时候 {@code levelRenderer} 还是 null。这里统一兜住，避免在回调里到处写判空。
     */
    public static void reloadAllChunks() {
        Minecraft client = Minecraft.getInstance();
        if (client != null && client.levelRenderer != null) {
            client.levelRenderer.allChanged();
        }
    }
}
