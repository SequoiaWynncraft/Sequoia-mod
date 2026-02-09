package org.sequoia.seq.events;

import com.collarmc.pounce.EventInfo;
import com.collarmc.pounce.Preference;
import net.minecraft.client.gui.GuiGraphics;

@EventInfo(preference = Preference.CALLER)
public record Render2DEvent(GuiGraphics context, float delta) {
}
