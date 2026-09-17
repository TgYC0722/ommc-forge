package com.plusls.ommc.feature.highlightLavaSource;

import com.plusls.ommc.OhMyMinecraftClient;
import net.minecraft.resources.ResourceLocation;

/**
 * 「高亮岩浆源」用到的贴图位置常量。
 *
 * <p>这两个贴图通过 {@code assets/minecraft/atlases/blocks.json} 显式声明进 blocks 图集 ——
 * 它们没有任何模型引用，不会被模型加载器自动带进图集，必须显式声明。
 * （该 JSON 与原版/Forge 的同名文件是<b>合并</b>关系而非覆盖：1.19.3+ 的图集加载器
 * {@code SpriteResourceLoader.load()} 会遍历 {@code ResourceManager.getResourceStack()}
 * 返回的所有资源包内同名文件，逐个解析后 {@code addAll} 累积到同一个 source 列表里。）
 */
public final class LavaSourceSprites {

    /** 源头岩浆用的静态贴图 */
    public static final ResourceLocation STILL_ID =
            ResourceLocation.fromNamespaceAndPath(OhMyMinecraftClient.MOD_ID, "block/lava_still");

    /** 源头岩浆用的流动贴图 */
    public static final ResourceLocation FLOW_ID =
            ResourceLocation.fromNamespaceAndPath(OhMyMinecraftClient.MOD_ID, "block/lava_flow");

    private LavaSourceSprites() {
    }
}
