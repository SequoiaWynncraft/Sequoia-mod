package com.seqwawa.seq.ui.unlock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.IngredientGuideManager;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.map.GatheringNodeService;
import com.seqwawa.seq.map.GatheringNodeSource;
import com.seqwawa.seq.map.WorldEventService;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafEvents;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.ui.QuestBoardScreen;
import com.seqwawa.seq.ui.WorldMapScreen;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * A scripted tour for development builds, so the quest board and the unlock
 * popups can be seen in a real game without a Wynncraft account. Run the
 * client with {@code -Dseq.unlockPreview=true}: it creates a world, opens the
 * quest board with sample data, plays every kind of popup, saves screenshots
 * under {@code run/screenshots/} and quits. Release builds never run it.
 */
public final class UnlockPreview {

    private static final long SEED = 4_247_016_918_311L;
    private static final int FRAME_EVERY_TICKS = 2;

    private enum Stage { WAIT_FOR_TITLE, WAIT_FOR_WORLD, SETTLE, BOARD, POPUPS, MAP, DONE }

    private static Stage stage = Stage.WAIT_FOR_TITLE;
    private static int ticks;
    private static int stageStart;
    private static int frame;
    private static volatile boolean running;
    /** Screenshots asked for since the last frame was drawn. */
    private static final List<String> pendingShots = new ArrayList<>();
    /** Ticks the script has waited for a frame to screenshot; past {@link #MAX_HOLD_TICKS} the shot is given up. */
    private static int heldTicks;
    private static final int MAX_HOLD_TICKS = 200;
    /**
     * The popups' clock while the tour runs: one tick's worth for each tick the
     * script moves on, so every screenshot shows the animation at a fixed step
     * however slowly the frames come.
     */
    private static volatile long animationMs = 1_000_000;

    private UnlockPreview() {}

    public static void initializeIfRequested() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("seq.unlockPreview")) return;
        SeqClient.LOGGER.info("[Unlock preview] Running the scripted tour");
        running = true;
        ClientTickEvents.END_CLIENT_TICK.register(UnlockPreview::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(UnlockPreview::restore);
    }

    private static void tick(Minecraft mc) {
        ticks++;
        if (!pendingShots.isEmpty()) {
            if (++heldTicks <= MAX_HOLD_TICKS) {
                // Hold the script, and the popups' clock, until the frame to screenshot is drawn.
                stageStart++;
                return;
            }
            SeqClient.LOGGER.warn("[Unlock preview] Gave up on {}: no frame was drawn without a loading screen", pendingShots);
            pendingShots.clear();
            heldTicks = 0;
        }
        animationMs += 50;
        int inStage = ticks - stageStart;
        if (inStage > 4 * 60 * 20) {
            SeqClient.LOGGER.warn("[Unlock preview] Stuck at {}, giving up", stage);
            mc.stop();
            return;
        }
        switch (stage) {
            case WAIT_FOR_TITLE -> {
                // A new run folder opens Minecraft's accessibility onboarding first. Close it as Esc would.
                if (mc.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose();
                } else if (mc.screen instanceof TitleScreen && SeqClient.assetManager != null
                        && SeqClient.assetManager.getAsset("unlock_medal_gold") != null && inStage > 60) {
                    // Nor should the movement tutorial's hint sit over the screenshots.
                    mc.getTutorial().setStep(TutorialSteps.NONE);
                    createWorld(mc);
                    next(Stage.WAIT_FOR_WORLD);
                }
            }
            case WAIT_FOR_WORLD -> {
                if (mc.level != null && mc.player != null && mc.screen == null && mc.getConnection() != null) {
                    mc.getConnection().sendCommand("time set 1000");
                    mc.getConnection().sendCommand("weather clear");
                    next(Stage.SETTLE);
                }
            }
            case SETTLE -> {
                if (inStage > 140) {
                    LeafQuestService.getInstance().showPreview(sampleBoard());
                    mc.setScreen(new QuestBoardScreen(null));
                    next(Stage.BOARD);
                }
            }
            case BOARD -> {
                if (inStage == 40) grab("preview-quest-board.png");
                if (inStage == 44 && mc.screen instanceof QuestBoardScreen board) {
                    // Down to the recent Leaf under the cards.
                    board.scrollBy(10_000);
                }
                if (inStage == 52) grab("preview-quest-board-recent.png");
                if (inStage == 54) {
                    // A narrow board: the same window with the mod's UI at its largest size, for the preview only.
                    MinecraftUiRenderer.previewUiScale = 1.5f;
                    mc.resizeDisplay();
                }
                if (inStage == 62) grab("preview-quest-board-narrow.png");
                if (inStage == 64) {
                    MinecraftUiRenderer.previewUiScale = null;
                    mc.resizeDisplay();
                }
                if (inStage == 70) {
                    mc.setScreen(null);
                    for (UnlockPopup popup : samplePopups()) UnlockOverlay.getInstance().enqueue(popup);
                    next(Stage.POPUPS);
                }
            }
            case POPUPS -> {
                if (inStage % FRAME_EVERY_TICKS == 0) grab(String.format("preview-unlock-%04d.png", frame++));
                if (inStage > 40 && UnlockOverlay.getInstance().isIdle()) {
                    // The world map loads the bundled gathering nodes, the ingredients and the world events
                    // in the background, and their results reach it through ClientThread: this runs that path
                    // in the real game.
                    mc.setScreen(new WorldMapScreen(null));
                    // Whichever node source the run folder's settings chose, the bundled one is what this checks.
                    GatheringNodeService.getInstance().requestRefresh(GatheringNodeSource.STATIC);
                    WorldEventService.getInstance().requestRefresh();
                    next(Stage.MAP);
                }
            }
            case MAP -> {
                List<String> statuses = List.of(
                        GatheringNodeService.getInstance().status(GatheringNodeSource.STATIC),
                        IngredientGuideManager.getInstance().status(),
                        WorldEventService.getInstance().status());
                if (inStage > 20 && statuses.stream().noneMatch(each -> each.endsWith("...") || each.equals("Not loaded"))) {
                    SeqClient.LOGGER.info("[Unlock preview] World map data: {}", String.join("; ", statuses));
                    grab("preview-world-map.png");
                    next(Stage.DONE);
                }
            }
            case DONE -> {
                if (inStage == 20) {
                    SeqClient.LOGGER.info("[Unlock preview] Done, {} frames saved", frame);
                    mc.stop();
                }
            }
        }
    }

    /**
     * Whether the tour is running. While it is, the game does not pause when the
     * window loses focus, so the popups play on; the setting itself is never
     * touched, so nothing the preview does can be saved into it.
     */
    public static boolean isRunning() {
        return running;
    }

    /** Puts back what the preview changed for itself. */
    private static void restore(Minecraft mc) {
        MinecraftUiRenderer.previewUiScale = null;
    }

    private static void next(Stage nextStage) {
        stage = nextStage;
        stageStart = ticks;
    }

    /**
     * Asks for a screenshot of the next frame. Minecraft can run several ticks
     * before it draws again, so the shot is taken once that frame is drawn,
     * and the script waits for it before changing anything else.
     */
    private static void grab(String name) {
        pendingShots.add(name);
    }

    /** The popups' clock while the tour runs, in milliseconds. */
    static long animationMs() {
        return animationMs;
    }

    /**
     * Called by the game renderer after each frame: saves the screenshots asked
     * for since the last one, unless a loading screen covered that frame.
     */
    public static void afterFrame(Minecraft mc) {
        if (pendingShots.isEmpty() || mc.getOverlay() != null) return;
        heldTicks = 0;
        for (String name : pendingShots) {
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> {});
        }
        pendingShots.clear();
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("Sequoia unlock preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                true, new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("sequoia-unlock-preview-" + System.currentTimeMillis(), settings,
                new WorldOptions(SEED, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
    }

    /**
     * A member two dailies into their bank, partway through each quest: a daily and a
     * endless quest that raids or wars can finish, and a weekly for season rating.
     */
    static LeafQuestService.Snapshot sampleBoard() {
        Instant now = Instant.now();
        LeafQuests quests = new LeafQuests(
                new LeafQuests.Daily(2, 3, now.plus(Duration.ofMinutes(372))),
                List.of(
                        quest("daily", "Complete 3 NOL raids or 9 wars", 300, 150, null,
                                new LeafQuests.Goal("raids", "nol", "Complete 3 NOL raids", 1, 3),
                                new LeafQuests.Goal("wars", null, "Take part in 9 wars", 4, 9)),
                        quest("weekly", "Earn 6,000 season rating", 800, 400, now.plus(Duration.ofHours(70)),
                                new LeafQuests.Goal("season_rating", null, "Earn 6,000 season rating", 2_350, 6_000)),
                        quest("repeatable", "Complete 8 raids or 24 wars", 200, 100, null,
                                new LeafQuests.Goal("raids", null, "Complete 8 raids", 3, 8),
                                new LeafQuests.Goal("wars", null, "Take part in 24 wars", 0, 24))),
                List.of());
        Instant at = Instant.now();
        List<LeafEvents.Event> recent = List.of(
                event(6, "leaf_earned", at.minus(Duration.ofMinutes(12)),
                        "{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\"}"),
                event(5, "leaf_earned", at.minus(Duration.ofMinutes(41)),
                        "{\"source\":\"war\",\"leaf\":146,\"xp\":146,\"detail\":\"Detlas\",\"in_claim\":true}"),
                event(4, "quest_completed", at.minus(Duration.ofMinutes(75)), FINISHED_BY_WARS),
                event(3, "achievement_paid", at.minus(Duration.ofHours(3)),
                        "{\"ladder\":\"raid:NOTG\",\"milestone\":\"gold\",\"tier\":\"gold\",\"threshold\":100,"
                                + "\"label\":\"Gold\",\"leaf\":1000,\"xp\":1000}"),
                event(2, "level_up", at.minus(Duration.ofHours(3)), "{\"level\":7,\"leaf\":225}"),
                event(1, "leaf_earned", at.minus(Duration.ofHours(5)),
                        "{\"source\":\"war\",\"leaf\":26,\"xp\":26,\"detail\":\"Ragni\",\"in_claim\":false}"));
        return new LeafQuestService.Snapshot(new LeafAccount(12_480, 13_120, new LeafAccount.Level(7, 640, 2_500)), quests,
                recent);
    }

    private static LeafEvents.Event event(long id, String type, Instant at, String data) {
        return new LeafEvents.Event(id, type, at, JsonParser.parseString(data).getAsJsonObject());
    }

    /** A daily that raids or wars could finish, finished by wars, as Leaf's event sends it. */
    private static final String FINISHED_BY_WARS = "{\"quest\":{\"slot\":\"daily\","
            + "\"title\":\"Complete 3 NOL raids or 9 wars\",\"objective\":{\"type\":\"wars\",\"raid\":null},"
            + "\"progress\":9,\"target\":9,\"goals\":["
            + "{\"type\":\"raids\",\"raid\":\"nol\",\"title\":\"Complete 3 NOL raids\",\"progress\":1,\"target\":3},"
            + "{\"type\":\"wars\",\"raid\":null,\"title\":\"Take part in 9 wars\",\"progress\":9,\"target\":9}],"
            + "\"reward\":{\"leaf\":300,\"xp\":150}}}";

    /** A quest as Leaf sends it: its goals, and the one nearest to being met as its objective. */
    private static LeafQuests.Quest quest(String slot, String title, long leaf, long xp, Instant expiresAt,
            LeafQuests.Goal... goals) {
        Instant now = Instant.now();
        LeafQuests.Goal leading = goals[0];
        for (LeafQuests.Goal goal : goals) {
            if ((double) goal.progress() / goal.target() > (double) leading.progress() / leading.target()) leading = goal;
        }
        return new LeafQuests.Quest(slot + ":1", slot, title, new LeafQuests.Objective(leading.type(), leading.raid()),
                leading.progress(), leading.target(), List.of(goals), new LeafQuests.Reward(leaf, xp),
                now.minus(Duration.ofHours(3)), expiresAt, null);
    }

    /** One of each popup, made from the same JSON Leaf sends, so the preview also exercises the mapping. */
    static List<UnlockPopup> samplePopups() {
        return List.of(
                popup("leaf_earned", "{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\"}"),
                popup("leaf_earned", "{\"source\":\"war\",\"leaf\":146,\"xp\":146,\"detail\":\"Detlas\",\"in_claim\":true}"),
                popup("quest_completed", FINISHED_BY_WARS),
                popup("achievement_paid", "{\"ladder\":\"raid:NOTG\",\"milestone\":\"gold\",\"tier\":\"gold\","
                        + "\"threshold\":100,\"label\":\"Gold\",\"leaf\":1000,\"xp\":1000}"),
                popup("achievement_paid", "{\"ladder\":\"raid:TNA\",\"milestone\":\"diamond\",\"tier\":\"diamond\","
                        + "\"threshold\":1000,\"label\":\"Diamond\",\"leaf\":5000,\"xp\":5000}"),
                popup("level_up", "{\"level\":8,\"leaf\":250}"));
    }

    private static UnlockPopup popup(String type, String data) {
        JsonObject json = JsonParser.parseString(data).getAsJsonObject();
        return UnlockPopup.fromEvent(type, json);
    }
}
