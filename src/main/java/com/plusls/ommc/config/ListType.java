package com.plusls.ommc.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;

/**
 * 名单类型：白名单 / 黑名单。
 *
 * <p>对应 Fabric 版里 malilib 自带的 {@code UsageRestriction.ListType}。
 * mafglib 把 malilib 的 {@code util/restrictions} 也带过来了，如果那边有现成的可以直接换成它；
 * 这里自己实现是为了不依赖那个包的可见性。
 */
public enum ListType implements IConfigOptionListEntry {
    WHITELIST("whitelist", "ommc.gui.label.list_type.whitelist"),
    BLACKLIST("blacklist", "ommc.gui.label.list_type.blacklist");

    private final String configString;
    private final String translationKey;

    ListType(String configString, String translationKey) {
        this.configString = configString;
        this.translationKey = translationKey;
    }

    @Override
    public String getStringValue() {
        return this.configString;
    }

    @Override
    public String getDisplayName() {
        return StringUtils.translate(this.translationKey);
    }

    @Override
    public IConfigOptionListEntry cycle(boolean forward) {
        int id = this.ordinal() + (forward ? 1 : -1);
        if (id >= values().length) {
            id = 0;
        } else if (id < 0) {
            id = values().length - 1;
        }
        return values()[id];
    }

    @Override
    public IConfigOptionListEntry fromString(String value) {
        for (ListType type : values()) {
            if (type.configString.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return WHITELIST;
    }
}
