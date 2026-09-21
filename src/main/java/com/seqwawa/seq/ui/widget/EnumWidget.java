package com.seqwawa.seq.ui.widget;

import com.seqwawa.seq.config.Setting;
import java.util.Arrays;
import java.util.List;

public final class EnumWidget<E extends Enum<E>> extends SelectionWidget<Setting.EnumSetting<E>> {
    public EnumWidget(Setting.EnumSetting<E> setting) { super(setting); }
    @Override protected List<String> options() {
        return Arrays.stream(setting.getEnumClass().getEnumConstants()).map(Enum::name).toList();
    }
    @Override protected int selectedIndex() { return setting.getValue().ordinal(); }
    @Override protected void select(int index) { setting.setValue(setting.getEnumClass().getEnumConstants()[index]); }
}
