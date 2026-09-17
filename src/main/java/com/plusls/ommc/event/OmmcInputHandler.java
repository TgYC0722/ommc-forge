package com.plusls.ommc.event;

import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;

/**
 * 把 OMMC 的热键交给 malilib 统一管理。
 *
 * <p>这样做的好处是组合键（例如打开界面的 {@code O,C}）由 malilib 的 {@code KeybindMulti}
 * 处理，不必自己监听按键事件判断「O 是否按下」—— Fabric 版就是这么做的。
 */
public class OmmcInputHandler implements IKeybindProvider {

    private static final OmmcInputHandler INSTANCE = new OmmcInputHandler();

    private OmmcInputHandler() {
    }

    public static OmmcInputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Configs.ALL_HOTKEYS) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        // 让这些热键出现在 malilib 的「全部热键」汇总列表里
        manager.addHotkeysForCategory(
                OhMyMinecraftClient.MOD_NAME,
                "ommc.hotkeys.category.generic_hotkeys",
                Configs.ALL_HOTKEYS);
    }
}
