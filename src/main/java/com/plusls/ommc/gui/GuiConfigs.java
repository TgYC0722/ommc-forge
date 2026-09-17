package com.plusls.ommc.gui;

import com.google.common.collect.ImmutableList;
import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * OMMC 的配置界面。
 *
 * <p>Fabric 版的这个类只有 23 行，因为整套界面（列表、控件、标签、按键绑定对话框、tooltip）
 * 都由 malilib 的 {@code GuiConfigsBase} 提供。这里沿用同样的思路：
 * 本类只负责「顶部标签按钮」和「当前标签对应哪些配置」两件事，其余全部继承而来。
 *
 * <p>标签按钮的写法照抄 mafglib 自带的 {@code MaLiLibConfigGui}：
 * 当前标签的按钮置灰，点击其它标签时重建列表控件并重新初始化。
 */
public class GuiConfigs extends GuiConfigsBase {

    /** 当前选中的标签。static 是为了在重开界面时保持上次的选择 */
    private static Tab tab = Tab.GENERIC;

    public GuiConfigs(Screen parent) {
        super(10, 50, OhMyMinecraftClient.MOD_ID, parent, "ommc.gui.title.configs", modVersion());
    }

    private static String modVersion() {
        return ModList.get()
                .getModContainerById(OhMyMinecraftClient.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public void initGui() {
        super.initGui();

        this.clearOptions();

        int x = 10;
        int y = 26;

        for (Tab tab : Tab.values()) {
            x += this.createButton(x, y, tab) + 2;
        }
    }

    private int createButton(int x, int y, Tab tab) {
        ButtonGeneric button = new ButtonGeneric(x, y, -1, 20, tab.getDisplayName());
        // 当前标签自身置灰
        button.setEnabled(GuiConfigs.tab != tab);
        this.addButton(button, new ButtonListener(tab, this));

        return button.getWidth();
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        return ConfigOptionWrapper.createFor(tab.getConfigs());
    }

    private static class ButtonListener implements IButtonActionListener {
        private final Tab tab;
        private final GuiConfigs parent;

        ButtonListener(Tab tab, GuiConfigs parent) {
            this.tab = tab;
            this.parent = parent;
        }

        @Override
        public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
            GuiConfigs.tab = this.tab;

            // 不同标签的控件宽度可能不同，所以整个列表控件要重建
            this.parent.reCreateListWidget();
            this.parent.getListWidget().resetScrollbarPosition();
            this.parent.initGui();
        }
    }

    /** 四个分类标签，顺序与 Fabric 版一致 */
    public enum Tab {
        GENERIC("ommc.gui.button.tab.generic", Configs.Generic.OPTIONS),
        FEATURE_TOGGLE("ommc.gui.button.tab.feature_toggle", Configs.FeatureToggle.OPTIONS),
        LISTS("ommc.gui.button.tab.lists", Configs.Lists.OPTIONS),
        ADVANCED_INTEGRATED_SERVER("ommc.gui.button.tab.advanced_integrated_server", Configs.AdvancedIntegratedServer.OPTIONS);

        private final String translationKey;
        private final ImmutableList<? extends IConfigBase> configs;

        Tab(String translationKey, ImmutableList<? extends IConfigBase> configs) {
            this.translationKey = translationKey;
            this.configs = configs;
        }

        public String getDisplayName() {
            return StringUtils.translate(this.translationKey);
        }

        public ImmutableList<? extends IConfigBase> getConfigs() {
            return this.configs;
        }
    }
}
