package com.seqwawa.seq.ui.widget;

import com.seqwawa.seq.config.Setting;
import java.util.List;

public final class ChoiceWidget extends SelectionWidget<Setting.ChoiceSetting> {
    public ChoiceWidget(Setting.ChoiceSetting setting) { super(setting); }
    @Override protected List<String> options() { return setting.getOptions(); }
    @Override protected int selectedIndex() { return options().indexOf(setting.getValue()); }
    @Override protected void select(int index) { setting.setValue(options().get(index)); }
}
