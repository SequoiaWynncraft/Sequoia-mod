package com.seqwawa.seq.raids.tna;

import com.collarmc.pounce.EventBus;
import com.collarmc.pounce.Preference;
import com.collarmc.pounce.Subscribe;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.events.SoundPlayedEvent;
import com.seqwawa.seq.events.TnaSahurSoundProcEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.minecraft.resources.Identifier;

/** Detects and groups the sounds replaced by sahursahur.zip during Challenges: 3/4. */
public final class TnaSahurSoundDetector {
    static final int SAHUR_CHALLENGE = 3;
    static final long DUPLICATE_WINDOW_MS = 300L;
    static final long SEQUENCE_TIMEOUT_MS = 6_000L;
    static final long DANGER_DISPLAY_MS = 1_200L;

    private static final long UNSET = -1L;
    private static final TnaSahurSoundDetector INSTANCE = new TnaSahurSoundDetector();
    private static final DateTimeFormatter LOG_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final Map<Identifier, Set<Identifier>> SAHUR_SOUNDS = Map.of(
            id("item.trident.thunder"),
                    Set.of(id("item/trident/thunder1"), id("item/trident/thunder2")),
            id("entity.evoker.prepare_summon"), Set.of(id("mob/evocation_illager/prepare_summon")));

    private final BeamTracker tracker = new BeamTracker();

    private TnaSahurSoundDetector() {}

    public static void initialize(EventBus eventBus) {
        eventBus.subscribeStrongly(INSTANCE);
        ClientTickEvents.END_CLIENT_TICK.register(client -> INSTANCE.tick(monotonicMillis()));
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> INSTANCE.tracker.reset());
    }

    @Subscribe(Preference.CALLER)
    public void onSoundPlayed(SoundPlayedEvent sound) {
        int challenge = TnaLineupHelper.activeChallenge();
        BeamKind kind = beamKind(sound.eventId(), sound.soundId());
        if (challenge != SAHUR_CHALLENGE || kind == null) {
            if (challenge != SAHUR_CHALLENGE) {
                tracker.reset();
            }
            return;
        }

        long nowMs = monotonicMillis();
        ProcResult proc = tracker.record(kind, nowMs);
        SeqClient.LOGGER.info(
                "[TNA] Watched beam sound at {} rawDelta={} sequenceElapsed={} logical={} timerBeams={} event={} sound={}",
                LOG_TIME.format(Instant.ofEpochMilli(System.currentTimeMillis())),
                elapsed(proc.rawDeltaMs()),
                elapsed(proc.sequenceElapsedMs()),
                proc.accepted() ? kind.name().toLowerCase(java.util.Locale.ROOT) : "duplicate",
                proc.timerBeams(),
                sound.eventId(),
                sound.soundId());
        if (proc.accepted()) {
            SeqClient.getEventBus().dispatch(new TnaSahurSoundProcEvent(sound));
        }
    }

    static IndicatorState indicatorState(long nowMs) {
        return INSTANCE.tracker.snapshot(TnaLineupHelper.activeChallenge(), nowMs);
    }

    static boolean isSahurProc(int challenge, Identifier eventId, Identifier soundId) {
        return challenge == SAHUR_CHALLENGE && beamKind(eventId, soundId) != null;
    }

    private void tick(long nowMs) {
        tracker.expire(TnaLineupHelper.activeChallenge(), nowMs);
    }

    private static BeamKind beamKind(Identifier eventId, Identifier soundId) {
        if (!SAHUR_SOUNDS.getOrDefault(eventId, Set.of()).contains(soundId)) {
            return null;
        }
        return eventId.equals(id("item.trident.thunder")) ? BeamKind.DANGER : BeamKind.TIMER;
    }

    private static String elapsed(long milliseconds) {
        return milliseconds < 0 ? "n/a" : "+" + milliseconds + "ms";
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    enum BeamKind {
        TIMER,
        DANGER
    }

    static final class BeamTracker {
        private int timerBeams;
        private long sequenceStartedAtMs = UNSET;
        private long lastLogicalProcAtMs = UNSET;
        private long lastRawProcAtMs = UNSET;
        private long lastTimerProcAtMs = UNSET;
        private long lastDangerProcAtMs = UNSET;
        private long dangerStartedAtMs = UNSET;

        ProcResult record(BeamKind kind, long nowMs) {
            long rawDeltaMs = since(lastRawProcAtMs, nowMs);
            if (lastLogicalProcAtMs != UNSET && nowMs - lastLogicalProcAtMs >= SEQUENCE_TIMEOUT_MS) {
                reset();
            }
            lastRawProcAtMs = nowMs;

            long previousKindProc = kind == BeamKind.TIMER ? lastTimerProcAtMs : lastDangerProcAtMs;
            boolean accepted = previousKindProc == UNSET || nowMs - previousKindProc >= DUPLICATE_WINDOW_MS;
            if (accepted) {
                if (sequenceStartedAtMs == UNSET || dangerStartedAtMs != UNSET && kind == BeamKind.TIMER) {
                    sequenceStartedAtMs = nowMs;
                    timerBeams = 0;
                    dangerStartedAtMs = UNSET;
                }
                lastLogicalProcAtMs = nowMs;
                if (kind == BeamKind.TIMER) {
                    lastTimerProcAtMs = nowMs;
                    timerBeams = Math.min(2, timerBeams + 1);
                } else {
                    lastDangerProcAtMs = nowMs;
                    dangerStartedAtMs = nowMs;
                }
            }
            return new ProcResult(
                    accepted,
                    rawDeltaMs,
                    since(sequenceStartedAtMs, nowMs),
                    timerBeams);
        }

        IndicatorState snapshot(int challenge, long nowMs) {
            expire(challenge, nowMs);
            return new IndicatorState(
                    challenge == SAHUR_CHALLENGE && (timerBeams > 0 || dangerStartedAtMs != UNSET),
                    timerBeams,
                    dangerStartedAtMs != UNSET,
                    since(dangerStartedAtMs, nowMs));
        }

        void expire(int challenge, long nowMs) {
            if (challenge != SAHUR_CHALLENGE
                    || dangerStartedAtMs != UNSET && nowMs - dangerStartedAtMs >= DANGER_DISPLAY_MS
                    || lastLogicalProcAtMs != UNSET && nowMs - lastLogicalProcAtMs >= SEQUENCE_TIMEOUT_MS) {
                reset();
            }
        }

        void reset() {
            timerBeams = 0;
            sequenceStartedAtMs = UNSET;
            lastLogicalProcAtMs = UNSET;
            lastRawProcAtMs = UNSET;
            lastTimerProcAtMs = UNSET;
            lastDangerProcAtMs = UNSET;
            dangerStartedAtMs = UNSET;
        }

        private static long since(long startedAtMs, long nowMs) {
            return startedAtMs == UNSET ? UNSET : Math.max(0L, nowMs - startedAtMs);
        }
    }

    record ProcResult(
            boolean accepted,
            long rawDeltaMs,
            long sequenceElapsedMs,
            int timerBeams) {}

    record IndicatorState(
            boolean visible,
            int timerBeams,
            boolean danger,
            long dangerElapsedMs) {}
}
