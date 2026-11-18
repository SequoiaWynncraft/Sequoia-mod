package com.seqwawa.seq.client;

import com.seqwawa.seq.network.WynncraftServerPolicy.Scope;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Which server the game is on, as far as the mod's features care: Wynncraft's main servers,
 * somewhere it cannot tell yet, or anywhere else. It is the one place that notices the server
 * changing, leaving it, joining one, or the world changing, and tells whoever asked; classes
 * listen here instead of each watching for themselves.
 * <p>
 * Everything it tells runs on the game thread. A disconnect arrives on the network thread,
 * where Minecraft also drops its queued tasks, so it is handed over through {@link ClientThread}.
 */
public final class ServerScopeTracker {

    /** One tick's scope, and the last tick's. */
    public record Tick(Scope previous, Scope current, String previousHost, String host) {
        public boolean changed() {
            return previous != current || !Objects.equals(previousHost, host);
        }

        /** Whether this tick left Wynncraft's main servers. */
        public boolean leftMain() {
            return previous == Scope.MAIN && current != Scope.MAIN;
        }
    }

    private static final ServerScopeTracker INSTANCE = new ServerScopeTracker(ClientThread.executor());

    private final Executor gameThread;
    private final List<Consumer<Tick>> scopeChanges = new CopyOnWriteArrayList<>();
    private final List<Consumer<Tick>> whileBlocked = new CopyOnWriteArrayList<>();
    private final List<Consumer<Tick>> whileUnknown = new CopyOnWriteArrayList<>();
    private final List<Runnable> worldChanges = new CopyOnWriteArrayList<>();
    private final List<Runnable> joins = new CopyOnWriteArrayList<>();
    private final List<Runnable> disconnects = new CopyOnWriteArrayList<>();
    private boolean installed;
    /** Until the first tick says otherwise, nothing is allowed. Game thread only. */
    private Scope scope = Scope.BLOCKED;
    private String host;

    ServerScopeTracker(Executor gameThread) {
        this.gameThread = Objects.requireNonNull(gameThread, "gameThread");
    }

    public static ServerScopeTracker get() {
        return INSTANCE;
    }

    /** Hears the game's join, disconnect and world-change events. Called once, when the mod starts. */
    public synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joined());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnected());
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> worldChanged());
    }

    /** The scope as of the last tick. Game thread only. */
    public Scope current() {
        return scope;
    }

    /**
     * Takes this tick's scope and host, tells the scope-change listeners when either moved,
     * and returns both with the last tick's. Called at the start of every client tick's work.
     */
    public Tick update(Scope current, String currentHost) {
        Tick tick = new Tick(scope, current, host, currentHost);
        scope = current;
        host = currentHost;
        if (tick.changed()) {
            SeqClient.LOGGER.info(
                    "[Seq] Wynncraft scope changed {} -> {} host={} previousHost={}",
                    tick.previous(),
                    tick.current(),
                    tick.host(),
                    tick.previousHost());
            scopeChanges.forEach(listener -> listener.accept(tick));
        }
        return tick;
    }

    /**
     * Tells the listeners for a tick spent away from Wynncraft's main servers, every such tick:
     * whatever a non-Wynncraft server or a transfer fed in since the last one is cleared again.
     * The tick calls it once it has done what it does on any server.
     */
    public void away(Tick tick) {
        switch (tick.current()) {
            case BLOCKED -> whileBlocked.forEach(listener -> listener.accept(tick));
            case UNKNOWN -> whileUnknown.forEach(listener -> listener.accept(tick));
            case MAIN -> {}
        }
    }

    /** Runs {@code listener} on each tick whose scope or host differs from the last one's. */
    public void onScopeChange(Consumer<Tick> listener) {
        scopeChanges.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Runs {@code listener} on every tick spent on a server the mod does not serve. */
    public void whileBlocked(Consumer<Tick> listener) {
        whileBlocked.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Runs {@code listener} on every tick spent where the server is not known yet, such as a transfer. */
    public void whileUnknown(Consumer<Tick> listener) {
        whileUnknown.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Runs {@code listener} each time the client's world changes, such as a world switch or a teleport. */
    public void onWorldChange(Runnable listener) {
        worldChanges.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Runs {@code listener} each time the client joins a server. */
    public void onJoin(Runnable listener) {
        joins.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Runs {@code listener} on the game thread each time the client leaves a server. */
    public void onDisconnect(Runnable listener) {
        disconnects.add(Objects.requireNonNull(listener, "listener"));
    }

    void worldChanged() {
        worldChanges.forEach(Runnable::run);
    }

    void joined() {
        joins.forEach(Runnable::run);
    }

    /** Any thread: the network thread, as Minecraft tells it. */
    void disconnected() {
        gameThread.execute(() -> disconnects.forEach(Runnable::run));
    }
}
