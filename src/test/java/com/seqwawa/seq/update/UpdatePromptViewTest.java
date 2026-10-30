package com.seqwawa.seq.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.UpdatePromptScreen;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** What the update prompt shows and offers in each state, as drawn and clicked. */
class UpdatePromptViewTest {
    private static final UpdateState.Release RELEASE = new UpdateState.Release("v1.12.0", "1.12.0",
            URI.create("https://example.invalid/v1.12.0"),
            new UpdateState.Asset("sequoia-1.12.0.jar", URI.create("https://example.invalid/jar"), 18_000_000), null, null);
    private static final UpdateState.Release DOWNLOADED = new UpdateState.Release("v1.12.0", "1.12.0",
            URI.create("https://example.invalid/v1.12.0"), new UpdateState.Asset("sequoia-1.12.0.jar", null, 18_000_000),
            null, "a".repeat(64));

    private static UpdatePromptScreen.Content view(UpdateState state) {
        return UpdatePromptScreen.content(state, "1.11.1", null);
    }

    private static List<String> buttons(UpdateState state) {
        return view(state).buttons().stream().map(UpdatePromptScreen.Button::label).toList();
    }

    /** The prompt's lines as one text; wrapping to the panel width is checked separately. */
    private static String text(UpdateState state) {
        return String.join(" ", view(state).lines());
    }

    @Test
    void anOfferedUpdateShowsBothVersionsItsSizeAndTheThreeChoices() {
        UpdateState offered = new UpdateState.Available(RELEASE, null);
        assertEquals("Sequoia update available", view(offered).title());
        assertTrue(text(offered).startsWith("Current: 1.11.1   Latest: v1.12.0 18.0 MB download."), text(offered));
        assertEquals(List.of("Ignore", "Update", "Update + Exit"), buttons(offered));
        assertEquals(List.of("Ignore", "Install", "Install + Exit"), buttons(new UpdateState.Available(DOWNLOADED, null)));
    }

    @Test
    void aDownloadShowsProgressAStallAndOnlyCancel() {
        var timer = Executors.newSingleThreadScheduledExecutor();
        try {
            var transfer = new ReleaseDownload.Transfer(URI.create("http://127.0.0.1:1/jar"), 18_000_000, null, null,
                    Path.of("unused"), ReleaseDownload.Limits.DEFAULT, timer);
            UpdateState downloading = new UpdateState.Downloading(RELEASE, true, transfer);
            assertEquals("Downloading v1.12.0: 0.0 MB of 18.0 MB (0%) Minecraft will close when the update is installed.",
                    text(downloading));
            assertEquals(0f, view(downloading).progress());
            assertEquals(List.of("Cancel"), buttons(downloading));
        } finally {
            timer.shutdownNow();
        }
    }

    @Test
    void installingOffersNothingToClick() {
        assertEquals(List.of(), buttons(new UpdateState.Installing(RELEASE, false)));
    }

    @Test
    void outcomesSayWhatHappenedAndWhatCanBeDoneNext() {
        assertEquals(List.of("Close", "Exit now"), buttons(new UpdateState.Installed(RELEASE)));
        assertEquals("Restart Minecraft to use it.", text(new UpdateState.Installed(RELEASE)));
        assertEquals(List.of("Cancel install", "Close", "Exit now"), buttons(new UpdateState.Staged(RELEASE)));
        UpdateState failed = new UpdateState.Failed(RELEASE, false, "No data received for 45 s (7.4 MB of 18.0 MB)");
        assertEquals("No data received for 45 s (7.4 MB of 18.0 MB) Details are in logs/latest.log.", text(failed));
        assertEquals(List.of("Close", "Release page", "Retry"), buttons(failed));
        assertEquals(List.of("Close", "Release page", "Mods folder"),
                buttons(new UpdateState.ManualOnly(RELEASE, "Sequoia is loaded from /x, outside this instance's mods folder.")));
        assertEquals("Sequoia 1.12.0 needs minecraft 1.21.12; this game has 1.21.11",
                text(new UpdateState.Incompatible(RELEASE, "Sequoia 1.12.0 needs minecraft 1.21.12; this game has 1.21.11")));
    }

    @Test
    void anEarlierAttemptIsReportedWithoutGoingOnline() {
        var note = new UpdateState.Attempt("v1.12.0", UpdateRecord.Phase.FAILED, "Last update to v1.12.0 failed: file in use");
        assertEquals("Last update to v1.12.0 failed: file in use", text(new UpdateState.Attention(note)));
        assertEquals(List.of("Close", "Check for updates"), buttons(new UpdateState.Attention(note)));
        UpdateState waiting = new UpdateState.WaitingForInstaller(
                new UpdateState.Attempt("v1.12.0", UpdateRecord.Phase.APPLYING, "An earlier install of v1.12.0 is still running"),
                4242, Instant.now());
        assertEquals("An earlier install of v1.12.0 is still running (installer process 4242). "
                + "Sequoia will check again when it finishes.", text(waiting));
        assertEquals(List.of("Close"), buttons(waiting));
    }

    @Test
    void longTextIsWrappedWithinThePanelAndATestSourceIsNamed() {
        UpdateState failed = new UpdateState.Failed(RELEASE, false, "word ".repeat(200));
        List<String> lines = view(failed).lines();
        assertEquals(7, lines.size());
        assertTrue(lines.stream().allMatch(line -> line.length() <= 57), lines.toString());
        assertTrue(lines.getLast().endsWith("..."));
        assertEquals("(test source: owner/fork)",
                UpdatePromptScreen.content(new UpdateState.Available(RELEASE, null), "1.11.1", "owner/fork").lines().getLast());
    }

    @Test
    void aLongPathIsBrokenAcrossLinesInsteadOfRunningOffThePanel() {
        String path = "/home/player/.local/share/PrismLauncher/instances/Wynncraft/minecraft/mods/sequoia-1.11.0.jar";
        UpdateState manual = new UpdateState.ManualOnly(RELEASE, "Sequoia is loaded from " + path + ", outside this instance's mods folder.");
        List<String> lines = view(manual).lines();
        assertTrue(lines.stream().allMatch(line -> line.length() <= 54), lines.toString());
        assertTrue(String.join("", lines).replace(" ", "").contains(path.replace(" ", "")), lines.toString());
    }

    @Test
    void buttonsAreLaidOutInOneRowWithoutOverlap() {
        for (int count = 1; count <= 3; count++) {
            var buttons = view(new UpdateState.Available(RELEASE, null)).buttons().subList(0, count);
            var placed = UpdatePromptScreen.layout(buttons, 960, 540);
            for (int i = 1; i < placed.size(); i++) assertTrue(placed.get(i).x() > placed.get(i - 1).x() + 100);
            float center = (placed.getFirst().x() + placed.getLast().x() + 108) / 2f;
            assertEquals(480f, center, 0.01f);
        }
    }
}
