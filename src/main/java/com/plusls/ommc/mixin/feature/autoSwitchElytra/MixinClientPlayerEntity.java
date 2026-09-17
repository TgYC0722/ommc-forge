package com.plusls.ommc.mixin.feature.autoSwitchElytra;

import com.mojang.authlib.GameProfile;
import com.plusls.ommc.config.Configs;
import com.plusls.ommc.feature.autoSwitchElytra.AutoSwitchElytraUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 自动切换鞘翅。移植自 Fabric 版同名文件，两个注入点与目标 ordinal 完全一致。
 *
 * <p><b>注入点 1</b>（{@code autoSwitchElytra}）：原版 {@code aiStep} 判断「能否开始滑翔」时
 * 要读一次胸甲槽 ——
 * <pre>
 *     ItemStack itemstack = this.getItemBySlot(EquipmentSlot.CHEST);
 *     if (itemstack.canElytraFly(this) &amp;&amp; this.tryToStartFallFlying()) { ... }
 * </pre>
 * 原版只看胸甲槽里有没有鞘翅，所以必须注入在这次读取<b>之前</b>，把背包里的鞘翅换上，
 * 紧随其后的原版判断才能通过。
 *
 * <p><b>注入点 2</b>（{@code autoSwitchChest}）：注入在原版每 tick 末尾的
 * {@code this.wasFallFlying = this.isFallFlying();} 处，用于捕捉「上一 tick 在滑翔、
 * 这一 tick 不滑翔」的下降沿，也就是落地那一刻。
 *
 * <p>两个 ordinal 都已用 1.20.1 官方映射产物的字节码核对：{@code getItemBySlot} 在整个
 * {@code aiStep} 中只出现 1 次；{@code isFallFlying()} 在 {@code LocalPlayer} 中共出现 3 次，
 * 但只有 1 次落在 {@code aiStep} 内。因此两者都是 ordinal = 0。
 *
 * <p><b>为什么不使用任何 {@code @Shadow} 字段</b>（与 Fabric 版的一处刻意差异）：
 * Forge 侧的 Mixin 注解处理器在生成 refmap 时**写不出字段条目** ——
 * TSRG 解析字段用的是 {@code new MappingField(owner, name)}（描述符为空），
 * 而 {@code MappingField.equals/hashCode} 都基于 {@code toString()}，
 * 其格式串是 {@code "L%s;%s:%s"}（含描述符）。两边对不上，字段映射恒查询失败，
 * 于是 refmap 里只有方法条目。而正式发行版里字段名是 SRG 名（如 {@code f_108619_}），
 * 必须靠 refmap 才能翻译过去，缺条目就会在运行时抛
 * {@code @Shadow field minecraft was not located in the target class}。
 * 本特性其实一个字段都不需要 shadow：
 * <ul>
 *   <li>客户端实例改用 {@link Minecraft#getInstance()} 获取（静态调用，每 tick 两次，语义等价）；</li>
 *   <li>滑翔状态用本类自维护的 {@code prevFallFlying}，与 Fabric 版一致。</li>
 * </ul>
 * 方法映射的生成链路是正常的，因此只保留两个 {@code @Inject} 即可。
 */
@Mixin(LocalPlayer.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayer {

    /**
     * 上一 tick 是否处于滑翔状态。
     *
     * <p>说明：1.20.1 的原版 {@code LocalPlayer} 自己有一个 private 的 {@code wasFallFlying}
     * 字段，语义与这里的 {@code prevFallFlying} 相同。这里仍然照搬 Fabric 版自行维护一份，
     * 目的是与上游保持一致，方便后续对照与合并。
     */
    boolean prevFallFlying = false;

    public MixinClientPlayerEntity(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
            ordinal = 0))
    private void autoSwitchElytra(CallbackInfo ci) {
        if (!Configs.FeatureToggle.AUTO_SWITCH_ELYTRA.getBooleanValue()) {
            return;
        }
        ItemStack chestItemStack = this.getItemBySlot(EquipmentSlot.CHEST);
        if (chestItemStack.is(Items.ELYTRA) || !AutoSwitchElytraUtil.myCheckFallFlying(this)) {
            return;
        }
        AutoSwitchElytraUtil.autoSwitch(AutoSwitchElytraUtil.CHEST_SLOT_IDX, Minecraft.getInstance(), (LocalPlayer) (Object) this, itemStack -> itemStack.is(Items.ELYTRA));
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "aiStep", at = @At(value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/client/player/LocalPlayer;isFallFlying()Z",
            ordinal = 0))
    private void autoSwitchChest(CallbackInfo ci) {
        if (!Configs.FeatureToggle.AUTO_SWITCH_ELYTRA.getBooleanValue()) {
            return;
        }
        ItemStack chestItemStack = this.getItemBySlot(EquipmentSlot.CHEST);
        if (!chestItemStack.is(Items.ELYTRA) || !prevFallFlying || this.isFallFlying()) {
            prevFallFlying = this.isFallFlying();
            return;
        }
        prevFallFlying = this.isFallFlying();
        AutoSwitchElytraUtil.autoSwitch(AutoSwitchElytraUtil.CHEST_SLOT_IDX, Minecraft.getInstance(), (LocalPlayer) (Object) this, itemStack -> ForgeRegistries.ITEMS.getKey(itemStack.getItem()).toString().contains("_chestplate"));
    }
}
