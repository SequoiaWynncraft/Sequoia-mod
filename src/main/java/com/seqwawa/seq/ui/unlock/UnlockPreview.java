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
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.ui.AchievementsScreen;
import com.seqwawa.seq.ui.ConnectionScreen;
import com.seqwawa.seq.ui.GuildMembersScreen;
import com.seqwawa.seq.ui.IngredientGuideScreen;
import com.seqwawa.seq.ui.PartyFinderScreen;
import com.seqwawa.seq.ui.PremadePartyEditorScreen;
import com.seqwawa.seq.ui.QuestBoardScreen;
import com.seqwawa.seq.ui.RaidProfileSetupScreen;
import com.seqwawa.seq.ui.SequoiaScreen;
import com.seqwawa.seq.ui.SettingsScreen;
import com.seqwawa.seq.ui.ThemeEditorScreen;
import com.seqwawa.seq.ui.UpdatePromptScreen;
import com.seqwawa.seq.ui.WarQueueHudEditorScreen;
import com.seqwawa.seq.ui.WorldMapScreen;
import com.seqwawa.seq.utils.PlayerNameCache;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

/**
 * A scripted tour for development builds, so the quest board and the unlock
 * popups can be seen in a real game without a Wynncraft account. Run the
 * client with {@code -Dseq.unlockPreview=true}: it creates a world, opens the
 * quest board with sample data, plays every kind of popup, shows every Sequoia
 * screen that opens without live data (the gallery), saves screenshots under
 * {@code run/screenshots/} and quits. Release builds never run it.
 *
 * <p>The gallery's shots are made to come out the same on every run, so two runs
 * can be compared pixel for pixel: the player stands in a closed box with the HUD
 * hidden, so nothing moves behind the screens, and the pointer is held at one spot.
 */
public final class UnlockPreview {

    private static final long SEED = 4_247_016_918_311L;
    private static final int FRAME_EVERY_TICKS = 2;

    private enum Stage { WAIT_FOR_TITLE, WAIT_FOR_WORLD, SETTLE, BOARD, POPUPS, MAP, GALLERY, DONE }

    /** A screen the gallery shows, by the name its screenshots take. */
    private record GalleryScreen(String name, Function<Screen, Screen> open) {}

    /**
     * Every Sequoia screen that opens without data the preview can't fake. The war
     * planner and its editors need an authorized guild member, so they are left out.
     */
    private static final List<GalleryScreen> GALLERY = List.of(
            new GalleryScreen("main-menu", parent -> new SequoiaScreen()),
            new GalleryScreen("settings", SettingsScreen::new),
            new GalleryScreen("connection", ConnectionScreen::new),
            new GalleryScreen("party-finder", PartyFinderScreen::new),
            new GalleryScreen("achievements", AchievementsScreen::new),
            new GalleryScreen("quests", QuestBoardScreen::new),
            new GalleryScreen("ingredients", IngredientGuideScreen::new),
            new GalleryScreen("members", GuildMembersScreen::new),
            new GalleryScreen("theme-editor", ThemeEditorScreen::new),
            new GalleryScreen("raid-profile-setup", parent -> new RaidProfileSetupScreen(parent, true)),
            new GalleryScreen("premade-party-editor", parent -> new PremadePartyEditorScreen(parent, null)),
            new GalleryScreen("update-prompt", UpdatePromptScreen::new),
            new GalleryScreen("hud-editor", WarQueueHudEditorScreen::new));
    /** Party Finder's create modal, and a party the player leads. Each starts from no party. */
    private static final List<GalleryScreen> PARTY_GALLERY = List.of(
            new GalleryScreen("party-finder-create", parent -> {
                SeqClient.getPartyFinderManager().reset();
                return new PartyFinderScreen(parent, true);
            }),
            new GalleryScreen("party-finder-party", parent -> {
                SeqClient.getPartyFinderManager().reset();
                showSampleParties();
                return new PartyFinderScreen(parent);
            }));

    /** A gallery screenshot: which screen, and whether at the narrow size. */
    private record Shot(GalleryScreen screen, boolean narrow) {}

    /**
     * Every screen wide, then every one narrow, then the party finder's states, narrow first:
     * the shots before those come at the same moments as before they were added.
     */
    private static final List<Shot> SHOTS = Stream.of(
                    GALLERY.stream().map(screen -> new Shot(screen, false)),
                    GALLERY.stream().map(screen -> new Shot(screen, true)),
                    PARTY_GALLERY.stream().map(screen -> new Shot(screen, true)),
                    PARTY_GALLERY.stream().map(screen -> new Shot(screen, false)))
            .flatMap(Function.identity())
            .toList();
    /** Ticks the gallery waits after closing the player in, for the blocks and their light to settle. */
    private static final int GALLERY_SETUP_TICKS = 40;
    /** Ticks each gallery screen has to load before its screenshot. */
    private static final int GALLERY_TICKS_PER_SCREEN = 20;
    /** Where the gallery holds the pointer: over a sidebar button at both UI sizes, and no main menu button. */
    private static final MinecraftUiRenderer.PreviewPointer GALLERY_POINTER = new MinecraftUiRenderer.PreviewPointer(70, 61);

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
                    next(Stage.GALLERY);
                }
            }
            case GALLERY -> {
                // A stage's first tick is 1: the tick that starts it belongs to the stage before.
                if (inStage == 1) {
                    mc.setScreen(null);
                    enclosePlayer(mc);
                    // The hotbar, hearts and hand move on their own; hide them as F1 does.
                    mc.options.hideGui = true;
                    MinecraftUiRenderer.previewPointer = GALLERY_POINTER;
                }
                int galleryTick = inStage - GALLERY_SETUP_TICKS;
                if (galleryTick < 0) return;
                int step = galleryTick / GALLERY_TICKS_PER_SCREEN;
                if (step >= SHOTS.size()) {
                    MinecraftUiRenderer.previewUiScale = null;
                    mc.resizeDisplay();
                    checkEscapeGoesBack(mc);
                    MinecraftUiRenderer.previewPointer = null;
                    mc.options.hideGui = false;
                    mc.setScreen(null);
                    next(Stage.DONE);
                    return;
                }
                Shot shot = SHOTS.get(step);
                if (galleryTick % GALLERY_TICKS_PER_SCREEN == 0) {
                    if (step > 0 && SHOTS.get(step - 1).narrow() != shot.narrow()) {
                        MinecraftUiRenderer.previewUiScale = shot.narrow() ? 1.5f : null;
                        mc.resizeDisplay();
                    }
                    // Fresh sample data, so the board's "in 6h 11m" and "12m ago" don't depend on how long the tour took.
                    LeafQuestService.getInstance().showPreview(sampleBoard());
                    mc.setScreen(shot.screen().open().apply(null));
                }
                if (galleryTick % GALLERY_TICKS_PER_SCREEN == GALLERY_TICKS_PER_SCREEN - 1) {
                    grab("gallery-" + shot.screen().name() + (shot.narrow() ? "-narrow" : "") + ".png");
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
        MinecraftUiRenderer.previewPointer = null;
    }

    /**
     * Builds a closed box of one block around the player, so the world behind the
     * see-through screens shows the same thing in every screenshot: no sky, clouds,
     * animals or water. The command runs on the world's server without a chat line.
     */
    private static void enclosePlayer(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        BlockPos feet = mc.player.blockPosition();
        String fill = String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:white_concrete hollow",
                feet.getX() - 3, feet.getY() - 1, feet.getZ() - 3, feet.getX() + 3, feet.getY() + 3, feet.getZ() + 3);
        server.execute(() -> {
            try {
                int blocks = server.getCommands().getDispatcher().execute(fill,
                        server.createCommandSourceStack().withSuppressedOutput());
                SeqClient.LOGGER.info("[Unlock preview] Closed the player in with {} blocks", blocks);
            } catch (Exception e) {
                SeqClient.LOGGER.warn("[Unlock preview] Couldn't close the player in: {}", e.getMessage());
            }
        });
    }

    /**
     * A party the player leads, open with its members, and more listings than fit, for
     * the party finder's cards, leader buttons and scrollbar. Names are cached first, so
     * no card waits for Mojang.
     */
    private static void showSampleParties() {
        var partyFinder = SeqClient.getPartyFinderManager();
        String me = Minecraft.getInstance().player.getUUID().toString();
        Instant at = Instant.parse("2026-11-19T00:00:00Z");
        String healer = "00000000-0000-4000-8000-000000000100";
        PlayerNameCache.put(healer, "Willowmere");
        partyFinder.handlePartyFinderUpdate("UPDATED", new Listing(40,
                List.of(new Activity(0, "NOTG", 4), new Activity(1, "TNA", 4)), null, me, PartyRegion.EU, null,
                PartyStatus.OPEN, null, null,
                List.of(new Member(me, PartyRole.TANK, WynnClassType.WARRIOR, at),
                        new Member(healer, PartyRole.HEALER, WynnClassType.SHAMAN, at)),
                List.of(new ReservedSlot(null, PartyRole.DPS, at)), at, PartyJoinPolicy.OPEN));
        String[] raids = {"NOTG", "TNA", "TCC", "NOL", "TWP", "ANNI"};
        WynnClassType[] classes = WynnClassType.values();
        for (int i = 1; i <= 9; i++) {
            String leader = String.format(Locale.ROOT, "00000000-0000-4000-8000-0000000000%02d", i);
            PlayerNameCache.put(leader, "Leader" + i);
            partyFinder.handlePartyFinderUpdate("UPDATED", new Listing(40 + i,
                    List.of(new Activity(i % raids.length, raids[i % raids.length], 4)), null, leader, PartyRegion.NA,
                    null, i % 3 == 0 ? PartyStatus.CLOSED : PartyStatus.OPEN, null, null,
                    List.of(new Member(leader, PartyRole.DPS, classes[i % classes.length], at)), List.of(), at,
                    i % 4 == 0 ? PartyJoinPolicy.INVITE_ONLY : PartyJoinPolicy.OPEN));
        }
        partyFinder.setExpanded(40, true);
        partyFinder.setExpanded(41, true);
    }

    /**
     * Presses Esc on the screens that used to close straight to the game, each opened
     * from the main menu, and logs where Esc went.
     */
    private static void checkEscapeGoesBack(Minecraft mc) {
        for (GalleryScreen entry : GALLERY) {
            if (!List.of("settings", "connection", "party-finder").contains(entry.name())) continue;
            Screen parent = new SequoiaScreen();
            mc.setScreen(parent);
            Screen screen = entry.open().apply(parent);
            mc.setScreen(screen);
            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
            String outcome = mc.screen == parent ? "went back to the main menu"
                    : mc.screen == null ? "closed to the game"
                    : "went to " + mc.screen.getClass().getSimpleName();
            SeqClient.LOGGER.info("[Unlock preview] Esc on {} {}", entry.name(), outcome);
        }
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
     * timed quest that raids or wars can finish, and a weekly for season rating.
     */
    static LeafQuestService.Snapshot sampleBoard() {
        Instant now = Instant.now();
        Instant timedEnds = now.plus(Duration.ofMinutes(97));
        LeafQuests quests = new LeafQuests(
                new LeafQuests.Daily(2, 3, now.plus(Duration.ofMinutes(372))),
                new LeafQuests.Timed(timedEnds, timedEnds.plus(Duration.ofHours(2))),
                List.of(
                        quest("daily", "Complete 3 NOL raids or 9 wars", 300, 150, null,
                                new LeafQuests.Goal("raids", "nol", "Complete 3 NOL raids", 1, 3),
                                new LeafQuests.Goal("wars", null, "Take part in 9 wars", 4, 9)),
                        quest("weekly", "Earn 6,000 season rating", 800, 400, now.plus(Duration.ofHours(70)),
                                new LeafQuests.Goal("season_rating", null, "Earn 6,000 season rating", 2_350, 6_000)),
                        quest("timed", "Complete 2 TNA raids or 6 wars", 500, 250, timedEnds,
                                new LeafQuests.Goal("raids", "tna", "Complete 2 TNA raids", 1, 2),
                                new LeafQuests.Goal("wars", null, "Take part in 6 wars", 2, 6))),
                List.of());
        Instant at = Instant.now();
        LeafEvents recent = LeafEvents.of(List.of(
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
                        "{\"source\":\"war\",\"leaf\":26,\"xp\":26,\"detail\":\"Ragni\",\"in_claim\":false}")));
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

    /**
     * A quest as Leaf sends it: its goals, and the one nearest to being met as its objective.
     * The daily's reroll is free, the weekly's costs 100 Leaf, and the timed quest has none.
     */
    private static LeafQuests.Quest quest(String slot, String title, long leaf, long xp, Instant expiresAt,
            LeafQuests.Goal... goals) {
        Instant now = Instant.now();
        LeafQuests.Goal leading = goals[0];
        for (LeafQuests.Goal goal : goals) {
            if ((double) goal.progress() / goal.target() > (double) leading.progress() / leading.target()) leading = goal;
        }
        return new LeafQuests.Quest(slot + ":1", slot, title, new LeafQuests.Objective(leading.type(), leading.raid()),
                leading.progress(), leading.target(), List.of(goals), new LeafQuests.Reward(leaf, xp),
                now.minus(Duration.ofHours(3)), expiresAt, null, switch (slot) {
                    case LeafQuests.DAILY -> 0L;
                    case LeafQuests.WEEKLY -> 100L;
                    default -> null;
                });
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
