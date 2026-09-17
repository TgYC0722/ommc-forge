package com.plusls.ommc.mixin.feature.advancedIntegratedServer;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 本地服务器设置（第 4 项）：用配置里的「局域网端口」替换集成服务器实际发布的端口。
 *
 * <p><b>这是与 Fabric 版实现方式差别最大的一项，需要说明来历</b>：
 * <ul>
 *   <li>Fabric 旧版（{@code oh-my-minecraft-client-multi}）注入的是
 *       {@code ShareToLanScreen.method_19851} 里 {@code HttpUtil.getAvailablePort()} 的返回值 ——
 *       而 {@code method_19851} 是 Yarn 的合成方法名，跨版本会漂移。</li>
 *   <li>Fabric 新版（{@code nyan-work-dev.49}）把它删成了空壳
 *       （{@code @Mixin(DummyClass.class)}），但配置项 {@code port} 还留着 ——
 *       也就是说新版这一项是<b>死配置</b>：界面有开关，却不生效。</li>
 * </ul>
 * 这里没有沿用旧版注入 UI 的做法，而是打在一个稳定得多的位置：
 * {@code IntegratedServer.publishServer} 把端口交给
 * {@code ServerConnectionListener.startTcpServerListener(InetAddress, int)} 的那一刻
 * （字节码：偏移 41 {@code iload_3} → 42 {@code invokevirtual startTcpServerListener}）。
 * 改这个实参即可让端口全程生效，且不依赖任何 lambda 名称。
 *
 * <p>语义与旧版一致：配置为 0 时保留原值（原版行为），非 0 时用配置值。
 */
@Mixin(IntegratedServer.class)
public abstract class MixinIntegratedServerPort {

    @ModifyArg(method = "publishServer",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerConnectionListener;startTcpServerListener(Ljava/net/InetAddress;I)V",
                    ordinal = 0),
            index = 1)
    private int modifyPublishedPort(int port) {
        int configured = Configs.AdvancedIntegratedServer.PORT.getIntegerValue();
        return configured == 0 ? port : configured;
    }
}
