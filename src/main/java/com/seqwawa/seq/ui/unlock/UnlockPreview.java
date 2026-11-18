package com.seqwawa.seq.ui.unlock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.LeafQuestService;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafEvents;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.ui.QuestBoardScreen;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
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

    private enum Stage { WAIT_FOR_TITLE, WAIT_FOR_WORLD, SETTLE, BOARD, POPUPS, DONE }

    private static Stage stage = Stage.WAIT_FOR_TITLE;
    private static int ticks;
    private static int stageStart;
    private static int frame;
    private static volatile boolean running;

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
        int inStage = ticks - stageStart;
        if (inStage > 4 * 60 * 20) {
            SeqClient.LOGGER.warn("[Unlock preview] Stuck at {}, giving up", stage);
            mc.stop();
            return;
        }
        switch (stage) {
            case WAIT_FOR_TITLE -> {
                if (mc.screen instanceof TitleScreen && SeqClient.assetManager != null
                        && SeqClient.assetManager.getAsset("unlock_medal_gold") != null && inStage > 60) {
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
                if (inStage == 40) grab(mc, "preview-quest-board.png");
                if (inStage == 44 && mc.screen instanceof QuestBoardScreen board) {
                    // Down to the recent Leaf under the cards.
                    board.scrollBy(10_000);
                }
                if (inStage == 52) grab(mc, "preview-quest-board-recent.png");
                if (inStage == 54) {
                    // A narrow board: the same window with the mod's UI at its largest size, for the preview only.
                    MinecraftUiRenderer.previewUiScale = 1.5f;
                    mc.resizeDisplay();
                }
                if (inStage == 62) grab(mc, "preview-quest-board-narrow.png");
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
                if (inStage % FRAME_EVERY_TICKS == 0) grab(mc, String.format("preview-unlock-%04d.png", frame++));
                if (inStage > 40 && UnlockOverlay.getInstance().isIdle()) next(Stage.DONE);
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

    private static void grab(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, message -> {});
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings("Sequoia unlock preview", GameType.SURVIVAL, false, Difficulty.PEACEFUL,
                true, new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("sequoia-unlock-preview-" + System.currentTimeMillis(), settings,
                new WorldOptions(SEED, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
    }

    /** A member two dailies into their bank, partway through each quest. */
    static LeafQuestService.Snapshot sampleBoard() {
        Instant now = Instant.now();
        LeafQuests quests = new LeafQuests(
                new LeafQuests.Daily(2, 3, now.plus(Duration.ofMinutes(372))),
                List.of(
                        quest("daily", "Complete 3 NOL raids", "raids", "nol", 1, 3, 300, 150, null),
                        quest("weekly", "Complete 12 TCC raids", "raids", "tcc", 5, 12, 800, 400,
                                now.plus(Duration.ofHours(70))),
                        quest("repeatable", "Complete 8 raids", "raids", null, 3, 8, 200, 100, null)),
                List.of());
        Instant at = Instant.now();
        List<LeafEvents.Event> recent = List.of(
                event(6, "leaf_earned", at.minus(Duration.ofMinutes(12)),
                        "{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\"}"),
                event(5, "leaf_earned", at.minus(Duration.ofMinutes(41)),
                        "{\"source\":\"war\",\"leaf\":146,\"xp\":146,\"detail\":\"Detlas\",\"in_claim\":true}"),
                event(4, "quest_completed", at.minus(Duration.ofMinutes(75)),
                        "{\"quest\":{\"slot\":\"daily\",\"title\":\"Complete 3 NOL raids\","
                                + "\"objective\":{\"type\":\"raids\",\"raid\":\"nol\"},\"reward\":{\"leaf\":300,\"xp\":150}}}"),
                event(3, "achievement_paid", at.minus(Duration.ofHours(3)),
                        "{\"ladder\":\"raid:NOTG\",\"milestone\":\"gold\",\"tier\":\"gold\",\"threshold\":100,"
                                + "\"label\":\"Gold\",\"leaf\":1000,\"xp\":1000}"),
                event(2, "level_up", at.minus(Duration.ofHours(3)), "{\"level\":7}"),
                event(1, "leaf_earned", at.minus(Duration.ofHours(5)),
                        "{\"source\":\"war\",\"leaf\":26,\"xp\":26,\"detail\":\"Ragni\",\"in_claim\":false}"));
        return new LeafQuestService.Snapshot(new LeafAccount(12_480, 13_120, new LeafAccount.Level(7, 640, 2_500)), quests,
                recent);
    }

    private static LeafEvents.Event event(long id, String type, Instant at, String data) {
        return new LeafEvents.Event(id, type, at, JsonParser.parseString(data).getAsJsonObject());
    }

    private static LeafQuests.Quest quest(String slot, String title, String type, String raid, long progress, long target,
            long leaf, long xp, Instant expiresAt) {
        Instant now = Instant.now();
        return new LeafQuests.Quest(slot + ":1", slot, title, new LeafQuests.Objective(type, raid), progress, target,
                new LeafQuests.Reward(leaf, xp), now.minus(Duration.ofHours(3)), expiresAt, null);
    }

    /** One of each popup, made from the same JSON Leaf sends, so the preview also exercises the mapping. */
    static List<UnlockPopup> samplePopups() {
        return List.of(
                popup("leaf_earned", "{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\"}"),
                popup("leaf_earned", "{\"source\":\"war\",\"leaf\":146,\"xp\":146,\"detail\":\"Detlas\",\"in_claim\":true}"),
                popup("quest_completed", "{\"quest\":{\"slot\":\"daily\",\"title\":\"Complete 3 NOL raids\","
                        + "\"objective\":{\"type\":\"raids\",\"raid\":\"nol\"},"
                        + "\"reward\":{\"leaf\":300,\"xp\":150}}}"),
                popup("achievement_paid", "{\"ladder\":\"raid:NOTG\",\"milestone\":\"gold\",\"tier\":\"gold\","
                        + "\"threshold\":100,\"label\":\"Gold\",\"leaf\":1000,\"xp\":1000}"),
                popup("achievement_paid", "{\"ladder\":\"raid:TNA\",\"milestone\":\"diamond\",\"tier\":\"diamond\","
                        + "\"threshold\":1000,\"label\":\"Diamond\",\"leaf\":5000,\"xp\":5000}"),
                popup("level_up", "{\"level\":8}"));
    }

    private static UnlockPopup popup(String type, String data) {
        JsonObject json = JsonParser.parseString(data).getAsJsonObject();
        return UnlockPopup.fromEvent(type, json);
    }
}
