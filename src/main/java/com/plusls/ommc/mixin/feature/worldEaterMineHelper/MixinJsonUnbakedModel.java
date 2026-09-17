package com.plusls.ommc.mixin.feature.worldEaterMineHelper;

import com.plusls.ommc.feature.worldEaterMineHelper.WorldEaterMineHelperUtil;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 世吞挖矿助手（第一部分：模型烘焙）：为每个方块额外烘出「镜像模型」。
 *
 * <p>对应 Fabric 版的 {@code MixinJsonUnbakedModel}。原理：把模型的每个
 * {@link BlockElement} 绕 X 轴旋转 45°、绕原点 {@code (0, 5, 11.25)} 格
 * （即 {@code Vector3f(0, 80, 180) * 0.0625}），得到一面「斜放在远处的镜子」里的影像。
 * 然后烘两次：
 * <ol>
 *   <li>只含镜像元素 → {@code customModels}</li>
 *   <li>镜像 + 原元素 → {@code customFullModels}</li>
 * </ol>
 * 最后把原元素还原并正常烘焙，返回给原版。
 *
 * <p><b>方块身份从哪来 —— 这里踩过一个坑，务必留意</b>：
 * Fabric 版是从 {@code getParentLocation()} 取路径末段当方块 ID 的，但那在 Forge 1.20.1 上
 * <b>行不通</b>：实测 {@code getParentLocation()} 返回的是<b>共享几何模板</b>而不是方块，
 * 例如
 * <pre>
 *     this   = minecraft:block/diorite_wall_post      ← 方块模型自己
 *     parent = minecraft:block/template_wall_post     ← 模板，永远不是已注册方块
 * </pre>
 * 于是 {@code ForgeRegistries.BLOCKS.getValue("template_wall_post")} 恒为 null，
 * 绝大多数模型会被挡掉（实测整个游戏只烘出 1 个方块）。
 * 正确做法是用注入方法的 {@code resourceLocation} 参数（它才是方块模型自己的 ID），
 * 并逐段剥掉变体后缀（{@code _post} / {@code _side} / {@code _side_tall} 等）直到命中已注册方块。
 *
 * <p><b>另外两处与 Fabric 版的差异</b>：
 * <ol>
 *   <li>不修改 {@code getElements()} 返回的列表内容，而是<b>替换自己的 elements 字段引用</b>
 *       （见 {@link BlockModelAccessor}：{@code getElements()} 在靠 parent 继承时会返回
 *       父模型的共享列表，直接改会污染其它方块）。</li>
 *   <li>暂不改动 {@code hasAmbientOcclusion}（原版用它让镜像更亮）；先保证几何正确，
 *       亮度留到渲染层跑通后再调。</li>
 * </ol>
 * 用 {@code ThreadLocal} 防止自定义模型的烘焙再次触发本注入（递归）。
 *
 * <p><b>当前状态：功能暂缓（未完成）。</b>渲染层尚未实现，本 mixin 已从
 * {@code ommc.mixins.json} 的 client 列表移除，注入入口另有提前 return 作双保险。
 */
@Mixin(value = BlockModel.class, priority = 999)
public abstract class MixinJsonUnbakedModel {

    private final ThreadLocal<Boolean> ommc$bakeTag = ThreadLocal.withInitial(() -> Boolean.TRUE);

    @Inject(
            method = "bake(Lnet/minecraft/client/resources/model/ModelBaker;Lnet/minecraft/client/renderer/block/model/BlockModel;Ljava/util/function/Function;Lnet/minecraft/client/resources/model/ModelState;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/client/resources/model/BakedModel;",
            at = @At(value = "HEAD"), cancellable = true)
    private void generateCustomBakedModel(ModelBaker baker, BlockModel parentModel,
                                          Function<Material, TextureAtlasSprite> textureGetter,
                                          ModelState modelSettings, ResourceLocation resourceLocation,
                                          boolean hasDepth, CallbackInfoReturnable<BakedModel> cir) {
        // ===== 功能暂缓（未完成）=====
        // 「世吞挖矿助手」的渲染层尚未实现，模型烘焙暂时停用。
        // 同时该 mixin 已从 ommc.mixins.json 的 client 列表中移除，所以本方法当前不会被调用；
        // 这里的提前返回是双保险 —— 即使将来误把它加回注册表，也不会产生任何烘焙副作用。
        // 恢复步骤：删掉下面这行 return，并把 mixins.json 里记载的 3 条注册移回 client 数组。
        if (true) {
            return;
        }
        // ===== 以下为原有实现，保留以备恢复 =====

        if (!this.ommc$bakeTag.get()) {
            return;
        }

        // 用「模型自己的 ID」解析方块 —— 不能用 getParentLocation()，那返回的是共享模板（见类注释）
        Block block = ommc$resolveBlock(resourceLocation);
        if (block == null) {
            return;
        }

        BlockModel self = (BlockModel) (Object) this;
        List<BlockElement> originalElements = new ArrayList<>(((BlockModelAccessor) this).ommc$getElements());

        // 烘任意一套自定义模型期间关掉标记，避免自定义模型的 bake 再次进入本注入
        this.ommc$bakeTag.set(false);
        try {
            List<BlockElement> mirrorElements = ommc$createMirrorElements(originalElements);

            // ① 只含镜像
            ((BlockModelAccessor) this).ommc$setElements(mirrorElements);
            WorldEaterMineHelperUtil.customModels.put(block,
                    self.bake(baker, parentModel, textureGetter, modelSettings, resourceLocation, hasDepth));

            // ② 镜像 + 原模型
            List<BlockElement> mirroredPlusOriginal = new ArrayList<>(mirrorElements);
            mirroredPlusOriginal.addAll(originalElements);
            ((BlockModelAccessor) this).ommc$setElements(mirroredPlusOriginal);
            BakedModel customFullBakedModel = self.bake(baker, parentModel, textureGetter, modelSettings, resourceLocation, hasDepth);
            WorldEaterMineHelperUtil.customFullModels.put(block, customFullBakedModel);

            WorldEaterMineHelperUtil.onModelBaked();

            // ③ 还原为原元素，正常烘焙一次返回给原版
            ((BlockModelAccessor) this).ommc$setElements(originalElements);
            cir.setReturnValue(self.bake(baker, parentModel, textureGetter, modelSettings, resourceLocation, hasDepth));
        } finally {
            this.ommc$bakeTag.set(true);
        }
    }

    /**
     * 从模型 ID 解析出对应的方块。
     *
     * <p>模型 ID 形如 {@code minecraft:block/diorite_wall_post}，而方块注册名是
     * {@code minecraft:diorite_wall} —— 多出来的 {@code _post} 是<b>模型变体后缀</b>
     * （墙/栅栏/台阶/门这类方块会把不同形态拆成多个模型）。
     * 因此这里逐段剥掉结尾的 {@code _xxx}，直到在方块注册表里命中为止。
     *
     * @return 命中的方块；始终没能命中则返回 null（表示这不是某个方块的模型）
     */
    private static Block ommc$resolveBlock(ResourceLocation modelLocation) {
        if (modelLocation == null) {
            return null;
        }
        String path = modelLocation.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);

        while (true) {
            Block block = ForgeRegistries.BLOCKS.getValue(
                    ResourceLocation.fromNamespaceAndPath(modelLocation.getNamespace(), name));
            // 注意：ForgeRegistries.BLOCKS.getValue() 在查不到时返回的是 Blocks.AIR 而不是 null
            // （实测日志：minecraft:diorite_wall_post → minecraft:air）。
            // 若只判 null 就会把 air 当成命中、直接返回，永远走不到下面的剥后缀逻辑 ——
            // 那样只有「模型 ID 恰好等于方块 ID」的无变体方块能成功（实测只有 1 个）。
            if (block != null && block != Blocks.AIR) {
                return block;
            }
            int cut = name.lastIndexOf('_');
            if (cut <= 0) {
                return null;
            }
            name = name.substring(0, cut);
        }
    }

    /**
     * 把每个元素复制一份并绕 X 轴旋转 45°。
     *
     * <p>旋转原点与角度与 Fabric 版一致：{@code Vector3f(0, 80, 180) * 0.0625} → {@code (0, 5, 11.25)}，
     * 即「斜放的镜面」所在位置。
     */
    private List<BlockElement> ommc$createMirrorElements(List<BlockElement> originalElements) {
        Vector3f origin = new Vector3f(0F, 80F, 180F);
        origin.mul(0.0625F);
        BlockElementRotation newModelRotation =
                new BlockElementRotation(origin, Direction.Axis.X, 45, false);

        List<BlockElement> result = new ArrayList<>(originalElements.size());
        for (BlockElement modelElement : originalElements) {
            Map<Direction, BlockElementFace> faces = new HashMap<>();
            for (Map.Entry<Direction, BlockElementFace> entry : modelElement.faces.entrySet()) {
                BlockElementFace originalFace = entry.getValue();
                // 复制面：丢掉 cullForDirection（传 null），保留 tintIndex / 贴图 / UV
                faces.put(entry.getKey(), new BlockElementFace(null, originalFace.tintIndex,
                        originalFace.texture, originalFace.uv));
            }
            result.add(new BlockElement(modelElement.from, modelElement.to, faces, newModelRotation, modelElement.shade));
        }
        return result;
    }
}
