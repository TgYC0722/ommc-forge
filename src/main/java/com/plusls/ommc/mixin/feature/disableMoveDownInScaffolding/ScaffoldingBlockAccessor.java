package com.plusls.ommc.mixin.feature.disableMoveDownInScaffolding;

import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取 {@link ScaffoldingBlock} 的私有静态字段 {@code STABLE_SHAPE}。
 *
 * <p><b>为什么不用 {@code @Shadow} 字段</b>：Forge 侧的 Mixin 注解处理器写不出
 * refmap 的<b>字段</b>条目（TSRG 解析字段时描述符为空，而 {@code MappingField} 的
 * equals/hashCode 都基于含描述符的 {@code toString()}，两边对不上），
 * 而 {@code STABLE_SHAPE} 在 {@code getCollisionShape} 里是真实的 {@code getstatic}
 * 字段访问（不是编译期常量内联），正式环境里它的名字是 SRG {@code f_56015_}，
 * 缺 refmap 条目就会在运行时抛
 * {@code @Shadow field STABLE_SHAPE was not located in the target class} ——
 * 这与 autoSwitchElytra 里 {@code minecraft} 字段踩的是同一个坑。
 *
 * <p>改用 accessor 后，处理器需要生成的是<b>方法</b>映射（{@code ommc$getStableShape}），
 * 而方法映射的生成链路是正常的。
 *
 * <p><b>必须是实例方法，不能写成 {@code static}</b>：若声明为 {@code static}，
 * 在 mixin 里只能通过接口名调用，javac 会直接报「静态接口方法调用非法」；
 * 即便绕过，调用的也是本接口里那个占位方法体，Mixin 无法接管。
 * 直接用实例 accessor，调用点写成 {@code ((ScaffoldingBlockAccessor) this).ommc$getStableShape()}，
 * 编译期是接口实例方法、运行期由 Mixin 注入的实现接管。
 *
 * <p>方法名带 {@code ommc$} 前缀是为了避免与目标类或其它模组的 accessor 撞名
 * （{@code ScaffoldingBlock} 自身并没有 {@code getStableShape}，但加前缀更稳妥）。
 */
@Mixin(ScaffoldingBlock.class)
public interface ScaffoldingBlockAccessor {

    @Accessor("STABLE_SHAPE")
    VoxelShape ommc$getStableShape();
}
