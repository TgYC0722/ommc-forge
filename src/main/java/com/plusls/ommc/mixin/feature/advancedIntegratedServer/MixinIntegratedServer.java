package com.plusls.ommc.mixin.feature.advancedIntegratedServer;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 本地服务器设置（前 3 项）：让集成服务器启动时采用配置里的正版验证 / PVP / 飞行开关。
 *
 * <p>移植自 Fabric 版 {@code MixinIntegratedServer}，实现方式完全一致 ——
 * 原版 {@code IntegratedServer.initServer()} 会把这三个能力<b>写死为 true</b>：
 * <pre>
 *     iconst_1 / invokevirtual setUsesAuthentication(Z)V     (偏移 20)
 *     iconst_1 / invokevirtual setPvpAllowed(Z)V             (偏移 25)
 *     iconst_1 / invokevirtual setFlightAllowed(Z)V          (偏移 30)
 * </pre>
 * 三个 {@code @ModifyArg} 分别把这三次调用的实参换成配置值。
 * 已用字节码核对：这三个 setter 在 {@code initServer} 中各只出现一次，因此 {@code ordinal = 0} 成立。
 *
 * <p>与 Fabric 版的差异只有配置读取方式：
 * {@code Configs.onlineMode / pvp / flight} →
 * {@code Configs.AdvancedIntegratedServer.ONLINE_MODE / PVP / FLIGHT} 的 {@code getBooleanValue()}。
 */
@Mixin(IntegratedServer.class)
public abstract class MixinIntegratedServer {

    @ModifyArg(method = "initServer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/server/IntegratedServer;setUsesAuthentication(Z)V",
                    ordinal = 0),
            index = 0)
    private boolean modifySetOnlineModeArg(boolean onlineMode) {
        return Configs.AdvancedIntegratedServer.ONLINE_MODE.getBooleanValue();
    }

    @ModifyArg(method = "initServer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/server/IntegratedServer;setPvpAllowed(Z)V",
                    ordinal = 0),
            index = 0)
    private boolean modifySetPvpEnabledArg(boolean pvp) {
        return Configs.AdvancedIntegratedServer.PVP.getBooleanValue();
    }

    @ModifyArg(method = "initServer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/server/IntegratedServer;setFlightAllowed(Z)V",
                    ordinal = 0),
            index = 0)
    private boolean modifySetFlightEnabledArg(boolean flight) {
        return Configs.AdvancedIntegratedServer.FLIGHT.getBooleanValue();
    }
}
