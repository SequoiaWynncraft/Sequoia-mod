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
    private static final UpdateState.Release RELEASE = release(ReleaseNotes.EMPTY);
    private static final UpdateState.Release DOWNLOADED = new UpdateState.Release("v1.12.0", "1.12.0",
            URI.create("https://example.invalid/v1.12.0"), new UpdateState.Asset("sequoia-1.12.0.jar", null, 18_000_000),
            null, "a".repeat(64), ReleaseNotes.EMPTY);
    private static final ReleaseNotes WITH_SUMMARY = ReleaseNotes.parse("""
            ## Sequoia 1.12.0

            Reroll a daily or weekly quest from its card.
            The quest board shows your newest Leaf first.

            ### New

            - **Rerolls.** The first reroll of each slot each day is free.
            """);
    /** Notes as every release before the summary was written: items only. */
    private static final ReleaseNotes ITEMS_ONLY = ReleaseNotes.parse("""
            ## Sequoia 1.12.0

            - Reroll a daily or weekly quest from its card.
            """);

    private static UpdateState.Release release(ReleaseNotes notes) {
        return new UpdateState.Release("v1.12.0", "1.12.0", URI.create("https://example.invalid/v1.12.0"),
                new UpdateState.Asset("sequoia-1.12.0.jar", URI.create("https://example.invalid/jar"), 18_000_000), null, null,
                notes);
    }

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
        UpdatePromptScreen.Content offered = view(new UpdateState.Available(release(WITH_SUMMARY), null));
        for (int count = 1; count <= 4; count++) {
            var content = new UpdatePromptScreen.Content(offered.title(), offered.lines(), -1, offered.buttons().subList(0, count));
            var frame = UpdatePromptScreen.frame(content, 960, 540);
            var placed = frame.buttons();
            for (int i = 1; i < placed.size(); i++)
                assertTrue(placed.get(i).x() >= placed.get(i - 1).x() + placed.get(i - 1).width() + 8, "count " + count);
            float center = (placed.getFirst().x() + placed.getLast().x() + placed.getLast().width()) / 2f;
            assertEquals(480f, center, 0.01f);
            assertTrue(placed.getFirst().x() >= frame.panel().x() && placed.getLast().x() + placed.getLast().width() <= frame.panel().right(),
                    "count " + count);
            assertEquals(count < 4 ? 108 : 83, placed.getFirst().width(), 0.01f, "count " + count);
        }
    }

    @Test
    void anOfferedReleaseShowsItsSummaryUnderTheTitleAndWhatsNewFirst() {
        UpdatePromptScreen.Content offered = view(new UpdateState.Available(release(WITH_SUMMARY), null));
        assertEquals("Sequoia update available", offered.title());
        assertEquals(List.of("Reroll a daily or weekly quest from its card.", "The quest board shows your newest Leaf first."),
                offered.summary());
        assertTrue(text(new UpdateState.Available(release(WITH_SUMMARY), null)).startsWith("Current: 1.11.1   Latest: v1.12.0"));
        assertEquals(List.of("What's new", "Ignore", "Update", "Update + Exit"),
                buttons(new UpdateState.Available(release(WITH_SUMMARY), null)));
        // Under the title, then the version lines a gap further down, all inside the panel at its usual size.
        var frame = UpdatePromptScreen.frame(offered, 960, 540);
        assertEquals(offered.summary(), frame.summary());
        assertEquals(210, frame.panel().height(), 0.01f);
        assertEquals(frame.panel().y() + 52 + 2 * 15 + 6, frame.linesY(), 0.01f);
    }

    @Test
    void notesWithoutASummaryAddOnlyWhatsNewAndNoNotesAddNothing() {
        UpdatePromptScreen.Content itemsOnly = view(new UpdateState.Available(release(ITEMS_ONLY), null));
        assertEquals(List.of(), itemsOnly.summary());
        assertEquals(List.of("What's new", "Ignore", "Update", "Update + Exit"),
                buttons(new UpdateState.Available(release(ITEMS_ONLY), null)));
        assertEquals(List.of("Ignore", "Update", "Update + Exit"), buttons(new UpdateState.Available(RELEASE, null)));
        assertEquals(List.of(), view(new UpdateState.Available(RELEASE, null)).summary());
        assertEquals(UpdatePromptScreen.frame(view(new UpdateState.Available(RELEASE, null)), 960, 540).linesY(),
                UpdatePromptScreen.frame(itemsOnly, 960, 540).linesY());
    }

    @Test
    void theNotesComeWithTheReleaseWhereverItIsOfferedOrInstalledButNotWhenSomethingWentWrong() {
        UpdateState.Release noted = release(WITH_SUMMARY);
        assertEquals("What's new", view(new UpdateState.Installed(noted)).buttons().getFirst().label());
        assertEquals("What's new", view(new UpdateState.Staged(noted)).buttons().getFirst().label());
        assertEquals("What's new", view(new UpdateState.ManualOnly(noted, "Install it by hand.")).buttons().getFirst().label());
        assertEquals(List.of("Close", "Release page", "Retry"), buttons(new UpdateState.Failed(noted, false, "file in use")));
        assertEquals(List.of(), view(new UpdateState.Failed(noted, false, "file in use")).summary());
        assertEquals(List.of("Close", "Release page"), buttons(new UpdateState.Incompatible(noted, "needs 1.21.12")));
    }

    @Test
    void upToDateOffersTheRunningReleasesNotesWhenItIsTheLatest() {
        UpdatePromptScreen.Content upToDate = view(new UpdateState.UpToDate("1.11.1", WITH_SUMMARY));
        assertEquals(List.of("What's new", "Close"), upToDate.buttons().stream().map(UpdatePromptScreen.Button::label).toList());
        assertEquals(2, upToDate.summary().size());
        assertEquals(List.of("Close"), buttons(new UpdateState.UpToDate("1.11.1", ReleaseNotes.EMPTY)));
    }

    @Test
    void aLongSummaryMakesThePanelTallerAndAShortWindowKeepsOnlyWhatFits() {
        ReleaseNotes wordy = ReleaseNotes.parse("## Sequoia 1.12.0\n\n" + "A summary line that is long enough to wrap onto a second line here.\n".repeat(4));
        UpdatePromptScreen.Content offered = view(new UpdateState.Available(release(wordy), null));
        assertEquals(6, offered.summary().size());
        assertTrue(offered.summary().getLast().endsWith("..."));
        var tall = UpdatePromptScreen.frame(offered, 960, 540);
        assertEquals(6, tall.summary().size());
        assertEquals(52 + 7 * 15 + 6 + 68, tall.panel().height(), 0.01f);
        // A window 240 high: the panel stays 8 inside it, and the version lines all show.
        var narrow = UpdatePromptScreen.frame(offered, 427, 240);
        assertTrue(narrow.panel().height() <= 224, String.valueOf(narrow.panel().height()));
        assertEquals(5, narrow.summary().size());
        assertTrue(narrow.summary().getLast().endsWith("..."));
        assertEquals(narrow.panel().bottom() - 42, narrow.buttons().getFirst().y(), 0.01f);
    }
}
