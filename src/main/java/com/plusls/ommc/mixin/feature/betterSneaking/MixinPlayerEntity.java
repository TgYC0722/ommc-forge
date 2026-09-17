package com.plusls.ommc.mixin.feature.betterSneaking;

import com.plusls.ommc.config.Configs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 更好的潜行：让玩家在潜行时也能从方块边缘往外移动一格。
 *
 * <p>原理：原版 {@code maybeBackOffFromEdge} 会在潜行接近边缘时阻止玩家走出去。
 * 它判定「能不能站住」用的是 {@link Entity#maxUpStep()}（默认 0.6），
 * 所以在这次检查期间把 maxUpStep 临时抬到 {@link #MAX_STEP_HEIGHT}（1.25），
 * 让原版以为玩家能迈上更高的坎，潜行就能多挪一格。
 *
 * <p>移植自 Fabric 版同名文件（oh-my-minecraft-client-nyan-work-dev.49），
 * 与之的差异及原因见下面各处注释。
 *
 * <p><b>关于 {@code Entity.maxUpStep()} 已被 Forge 标记为过时</b>：
 * Forge 提供了 {@code IForgeEntity.getStepHeight()} 作为替代，但两者<b>并不等价</b> ——
 * {@code getStepHeight()} 的实现是
 * {@code Math.max(0, maxUpStep() + STEP_HEIGHT_ADDITION 属性值)}，
 * 而本特性的逻辑是「保存原值 → 设成 1.25 → 恢复原值」，写入只能走
 * {@code setMaxUpStep()}（Forge 没有提供对应的 setter）。若读取用 getStepHeight()、
 * 写入用 setMaxUpStep()，一旦某个模组把 STEP_HEIGHT_ADDITION 调成非 0，
 * 恢复阶段就会把玩家的步高写成错误的值，反而破坏原始状态；
 * 方法内 {@code myIsSpaceEmpty} 靠「当前值 - 保存值」推算盒子上抬量的算法也会失准。
 * 因此这里有意继续使用 maxUpStep()，并显式抑制弃用警告。
 */
@SuppressWarnings("deprecation")
@Mixin(Player.class)
public abstract class MixinPlayerEntity {
    /** 临时提高到的最大台阶高度 */
    private static final float MAX_STEP_HEIGHT = 1.25f;

    /** 哨兵值：表示「当前没有正在生效的临时修改」，避免与任何真实的 maxUpStep 撞上 */
    private static final float DEFAULT_STEP_HEIGHT = 114514;

    /**
     * 上一 tick 保存下来的原始 maxUpStep。
     *
     * <p>每个 {@code Player} 实例各有一份（mixin 的非 static 字段会被合并进目标类）。
     */
    private float prevStepHeight = DEFAULT_STEP_HEIGHT;

    /**
     * 客户端判定：只有客户端才需要做这套客户端侧的位移修正，服务端不做任何改动。
     */
    private static boolean shouldApply(Entity entity) {
        return Configs.FeatureToggle.BETTER_SNEAKING.getBooleanValue() && entity.level().isClientSide();
    }

    /**
     * 注入点 1：{@code maybeBackOffFromEdge} 内首次读取 {@code Vec3.x} 之前，抬高 maxUpStep。
     *
     * <p>ordinal = 0 的依据（1.20.1 实际字节码）：{@code maybeBackOffFromEdge} 中带
     * {@code GETFIELD Vec3.x:D} 的指令**只有 1 处**（偏移 48），另两处 GETFIELD 是
     * 偏移 11 的 {@code Vec3.y} 与偏移 368 的 {@code Vec3.y}。
     *
     * <p>位置很关键：偏移 48 之后、偏移 80/89 处原版就会读 maxUpStep 并调用
     * {@code Level.noCollision}，所以必须在那之前改掉才生效。
     */
    @Inject(method = "maybeBackOffFromEdge",
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/phys/Vec3;x:D",
                    opcode = Opcodes.GETFIELD, ordinal = 0))
    private void setStepHeightForBackOff(Vec3 movement, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        Entity thisObj = (Entity) (Object) this;
        if (!shouldApply(thisObj)) {
            return;
        }
        this.prevStepHeight = thisObj.maxUpStep();
        thisObj.setMaxUpStep(MAX_STEP_HEIGHT);
    }

    /**
     * 注入点 2：{@code maybeBackOffFromEdge} 构造返回值 {@code new Vec3(...)} 之前，还原 maxUpStep。
     *
     * <p>ordinal = 0 的依据：该方法内 {@code Vec3.<init>(DDD)V} 只出现 1 处（偏移 373）。
     * 放在这里而不是 RETURN，是为了让「原版还在用抬高后的值」这一段尽量短。
     */
    @Inject(method = "maybeBackOffFromEdge",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;<init>(DDD)V",
                    ordinal = 0))
    private void restoreStepHeightForBackOff(Vec3 movement, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        Entity thisObj = (Entity) (Object) this;
        if (!shouldApply(thisObj) || Math.abs(this.prevStepHeight - DEFAULT_STEP_HEIGHT) <= 0.001) {
            return;
        }
        thisObj.setMaxUpStep(this.prevStepHeight);
        this.prevStepHeight = DEFAULT_STEP_HEIGHT;
    }

    /**
     * 熔岩边缘特判：把所有 {@code Level.noCollision} 调用改走这里。
     *
     * <p>抬高 maxUpStep 之后，原版用来检测的盒子会被抬高一截。于是可以反推：
     * <ul>
     *   <li>{@code retOld} —— 用**抬高后的盒子**（即原版实际在查的盒子）的结果；</li>
     *   <li>{@code retNew} —— 用**原始盒子**的结果；</li>
     *   <li>若「高盒子能过、原盒子不能过」，说明玩家正悬在边缘外一截；</li>
     *   <li>此时若脚下是岩浆，就返回 true 放行 —— 潜行在岩浆边不会掉下去。</li>
     * </ul>
     *
     * <p>注意这里的 {@code prevStepHeight} 是「改动前的原值」，而 {@code maxUpStep()}
     * 已经是抬高后的 1.25，两者相减正好是盒子被抬高的量。
     *
     * <p>ordinal = -1 表示匹配方法内**全部** {@code noCollision} 调用点
     * （1.20.1 的 {@code maybeBackOffFromEdge} 里有 3 处：偏移 89 / 168 / 261）。
     */
    @Redirect(method = "maybeBackOffFromEdge",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z",
                    ordinal = -1))
    private boolean myIsSpaceEmpty(Level world, Entity entity, AABB box) {
        Entity thisObj = (Entity) (Object) this;
        boolean retOld = world.noCollision(entity, box.move(0, thisObj.maxUpStep() - this.prevStepHeight, 0));
        boolean retNew = world.noCollision(entity, box);
        if (shouldApply(thisObj) && retOld && !retNew &&
                world.getFluidState(thisObj.blockPosition().below()).getType() instanceof LavaFluid) {
            return true;
        }
        return retNew;
    }

    /**
     * 注入点 3/4：{@code isAboveGround}（private）的 HEAD / RETURN，同样是临时抬高 maxUpStep。
     *
     * <p>该方法用 {@code fallDistance < maxUpStep()} 判断玩家是否算「站住了」，
     * 抬高 maxUpStep 能让潜行下边缘判定更宽松。
     *
     * <p>与 Fabric 版的差异：那边的这两个方法与上面两个<b>重名</b>
     * （{@code setStepHeight} / {@code restoreStepHeight} 各出现两次）。
     * 在 1.14/1.15 上两组条件编译互斥所以不冲突，但 1.20.1 上两组都生效，
     * 同名方法会直接编译失败，因此这里加了 {@code ForAboveGround} 后缀。
     */
    @Inject(method = "isAboveGround", at = @At("HEAD"))
    private void setStepHeightForAboveGround(CallbackInfoReturnable<Boolean> cir) {
        Entity thisObj = (Entity) (Object) this;
        if (!shouldApply(thisObj)) {
            return;
        }
        this.prevStepHeight = thisObj.maxUpStep();
        thisObj.setMaxUpStep(MAX_STEP_HEIGHT);
    }

    @Inject(method = "isAboveGround", at = @At("RETURN"))
    private void restoreStepHeightForAboveGround(CallbackInfoReturnable<Boolean> cir) {
        Entity thisObj = (Entity) (Object) this;
        if (!shouldApply(thisObj) || Math.abs(this.prevStepHeight - DEFAULT_STEP_HEIGHT) <= 0.001) {
            return;
        }
        thisObj.setMaxUpStep(this.prevStepHeight);
        this.prevStepHeight = DEFAULT_STEP_HEIGHT;
    }
}
