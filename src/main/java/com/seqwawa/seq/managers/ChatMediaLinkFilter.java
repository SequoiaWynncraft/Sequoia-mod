package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.ComponentTextEditor;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Removes supported media URLs from displayed chat when their embed replaces them. */
public final class ChatMediaLinkFilter {
    private static final int MAX_FILTERED_LINKS = 4;

    private ChatMediaLinkFilter() {
    }

    public static Component filter(Component message) {
        if (message == null || !isEnabled()) {
            return message;
        }

        List<ChatLinkExtractor.LinkMatch> matches = ChatLinkExtractor.extractMatches(
                        message.getString(), MAX_FILTERED_LINKS)
                .stream()
                .filter(match -> ChatMediaUrlResolver.isDisplayableCandidate(match.uri()))
                .toList();
        Component filtered = message;
        for (int index = matches.size() - 1; index >= 0; index--) {
            ChatLinkExtractor.LinkMatch match = matches.get(index);
            Component replacement = ComponentTextEditor.replaceRange(
                    ComponentTextEditor.flatten(filtered),
                    match.start(),
                    match.endExclusive(),
                    Component.empty());
            if (replacement != null) {
                filtered = replacement;
            }
        }
        return filtered;
    }

    /** Whether a bridge message still has visible content after media-link removal. */
    public static boolean hasVisibleText(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String filtered = filter(Component.literal(message)).getString();
        return filtered.codePoints().anyMatch(codePoint -> !Character.isWhitespace(codePoint)
                && !Character.isISOControl(codePoint)
                && Character.getType(codePoint) != Character.FORMAT);
    }

    private static boolean isEnabled() {
        Setting.BooleanSetting embedSetting = SeqClient.getShowChatMediaEmbedsSetting();
        Setting.IntSetting durationSetting = SeqClient.getChatMediaEmbedDurationSetting();
        Setting.BooleanSetting filterSetting = SeqClient.getHideEmbeddedMediaLinksSetting();
        return embedSetting != null
                && embedSetting.getValue()
                && (durationSetting == null || durationSetting.getValue() > 0)
                && filterSetting != null
                && filterSetting.getValue();
    }
}
