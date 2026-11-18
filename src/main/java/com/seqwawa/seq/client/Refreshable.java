package com.seqwawa.seq.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A value fetched in the background and kept for the game: the one implementation of
 * "fetch, throttle, keep the last good value, ignore stale results, give up after a
 * deadline".
 * <p>
 * Everything but the readers belongs to the game thread. A fetch runs wherever its
 * future runs, and its result is handed back through the builder's executor
 * ({@link ClientThread#executor()} in the game), so the value only ever changes on the
 * game thread. Readers on any thread see one consistent {@link State}.
 * <p>
 * {@link #reset()} and the deadline overtake a running fetch: its result is dropped when
 * it arrives, and its {@link Ticket} stops being current, so a reply meant for an account
 * that has since signed out never lands.
 */
public final class Refreshable<T> {

    /** Where a refreshable is, as the readers see it. */
    public enum Phase {
        /** No value, and nothing running: never fetched, or reset since. */
        EMPTY,
        /** The first fetch, or the first since a reset or a cleared failure, is running. */
        LOADING,
        /** There is a value. A fetch may be refreshing it, and the last one may have failed. */
        READY,
        /** No value, because the last fetch failed. */
        FAILED
    }

    /**
     * Everything a reader needs, as one immutable value.
     *
     * @param value the value, or the builder's empty value when there is none
     * @param hasValue whether a fetch or {@link #seed} produced a value that is still kept
     * @param version goes up whenever the value changes, including when it is cleared, and never goes back
     * @param loading whether a fetch is running
     * @param failure why the last finished fetch failed; null when it succeeded or none has finished
     */
    public record State<T>(T value, boolean hasValue, long version, boolean loading, Throwable failure) {

        public Phase phase() {
            if (hasValue) {
                return Phase.READY;
            }
            if (loading) {
                return Phase.LOADING;
            }
            return failure == null ? Phase.EMPTY : Phase.FAILED;
        }
    }

    /** One fetch's way to ask whether its result is still wanted. Any thread may ask. */
    @FunctionalInterface
    public interface Ticket {
        /** False once {@link #reset()} or the deadline has overtaken the fetch. */
        boolean current();
    }

    private final String name;
    /** What {@link ClientThread#check} reports when this is used off the game thread. */
    private final String owner;
    private final Function<Ticket, CompletableFuture<T>> fetch;
    private final LongSupplier clockMs;
    private final Executor handBack;
    private final long refreshEveryMs;
    private final long retryAfterMs;
    private final long deadlineMs;
    private final Predicate<Throwable> clearOnFailure;
    private final boolean keepEqualValue;
    private final T emptyValue;
    private final Consumer<T> whenFetched;
    private final Consumer<Throwable> whenFailed;

    private volatile State<T> state;
    /** The running fetch. Volatile only so tickets can read it from other threads. */
    private volatile Fetch running;
    private boolean attempted;
    private boolean lastFailed;
    private long lastStartedAtMs;
    private boolean stale;
    private CompletableFuture<State<T>> settled;

    private Refreshable(Builder<T> builder) {
        this.name = builder.name;
        this.owner = "Refreshable '" + builder.name + "'";
        this.fetch = Objects.requireNonNull(builder.fetch, "fetch");
        this.clockMs = builder.clockMs;
        this.handBack = builder.handBack;
        this.refreshEveryMs = builder.refreshEveryMs;
        this.retryAfterMs = builder.retryAfterMs < 0 ? builder.refreshEveryMs : builder.retryAfterMs;
        this.deadlineMs = builder.deadlineMs;
        this.clearOnFailure = builder.clearOnFailure;
        this.keepEqualValue = builder.keepEqualValue;
        this.emptyValue = builder.emptyValue;
        this.whenFetched = builder.whenFetched;
        this.whenFailed = builder.whenFailed;
        this.state = new State<>(emptyValue, false, 0, false, null);
    }

    /** A refreshable named {@code name} in logs, whose fetch doesn't need its ticket. */
    public static <T> Builder<T> builder(String name, Supplier<CompletableFuture<T>> fetch) {
        Objects.requireNonNull(fetch, "fetch");
        return new Builder<>(name, ticket -> fetch.get());
    }

    /** A refreshable named {@code name} in logs, whose fetch checks its ticket, say before it signs in. */
    public static <T> Builder<T> builderWithTicket(String name, Function<Ticket, CompletableFuture<T>> fetch) {
        return new Builder<>(name, fetch);
    }

    public State<T> state() {
        return state;
    }

    public T value() {
        return state.value();
    }

    public boolean hasValue() {
        return state.hasValue();
    }

    public boolean isLoading() {
        return state.loading();
    }

    /** Whether {@link #request()} would fetch now: none is running and the interval has passed. Game thread only. */
    public boolean isDue() {
        ClientThread.check(owner);
        if (running != null) {
            return false;
        }
        return !attempted || stale || clockMs.getAsLong() - lastStartedAtMs >= (lastFailed ? retryAfterMs : refreshEveryMs);
    }

    /**
     * Fetches unless a fetch is running or the last one started too recently: within the
     * refresh interval after a success, the retry interval after a failure. The first
     * request, and the first after {@link #reset()} or {@link #markStale()}, always
     * fetches. A fetch that has run past the deadline fails first. Returns whether a
     * fetch started.
     */
    public boolean request() {
        ClientThread.check(owner);
        long now = clockMs.getAsLong();
        Fetch current = running;
        if (current != null) {
            if (deadlineMs <= 0 || now - current.startedAtMs < deadlineMs) {
                return false;
            }
            finish(current, null, new TimeoutException(name + " took longer than " + deadlineMs + " ms"));
        }
        if (attempted && !stale && now - lastStartedAtMs < (lastFailed ? retryAfterMs : refreshEveryMs)) {
            return false;
        }
        start(now);
        return true;
    }

    /** Fetches now, whatever the interval, unless a fetch is running. A refused force changes nothing. */
    public boolean force() {
        ClientThread.check(owner);
        boolean wasStale = stale;
        stale = true;
        if (request()) {
            return true;
        }
        stale = wasStale;
        return false;
    }

    /**
     * Makes the next {@link #request()} fetch whatever the interval. Called while a fetch is
     * running, it applies after that fetch, whose answer may predate the change.
     */
    public void markStale() {
        ClientThread.check(owner);
        stale = true;
    }

    /**
     * Drops the running fetch's answer when it arrives, and keeps the value: for when the
     * connection the fetch needs went away. The interval still counts from that fetch's start.
     */
    public void cancel() {
        ClientThread.check(owner);
        if (running == null) {
            return;
        }
        running = null;
        State<T> current = state;
        state = new State<>(current.value(), current.hasValue(), current.version(), false, current.failure());
        settle();
    }

    /** Forgets the value, drops the running fetch's result, and lets the next request fetch at once. */
    public void reset() {
        ClientThread.check(owner);
        running = null;
        attempted = false;
        lastFailed = false;
        stale = false;
        State<T> current = state;
        state = new State<>(emptyValue, false, current.hasValue() ? current.version() + 1 : current.version(), false, null);
        settle();
    }

    /**
     * Shows {@code value}, kept from an earlier run, until a fetch replaces it. Ignored when
     * there already is a value, so a cache read can't overwrite a fresher fetch. It isn't a
     * fetch: the first request still fetches, and {@code whenFetched} isn't told. Returns
     * whether the value was used.
     */
    public boolean seed(T value) {
        ClientThread.check(owner);
        Objects.requireNonNull(value, "value");
        State<T> current = state;
        if (current.hasValue()) {
            return false;
        }
        state = new State<>(value, true, current.version() + 1, current.loading(), current.failure());
        return true;
    }

    /**
     * Changes the value now, for a change the server confirmed outside a fetch: a profile
     * it pushed, one the player saved. A fetch running now may have been answered before
     * the change, so the change is made again to whatever that fetch brings back, and a
     * slower, older answer can't undo it. With no value yet, it changes the empty value,
     * which then counts as one.
     */
    public void mutate(UnaryOperator<T> change) {
        ClientThread.check(owner);
        Objects.requireNonNull(change, "change");
        State<T> current = state;
        T changed = Objects.requireNonNull(change.apply(current.value()), "the change returned no value");
        state = new State<>(changed, true, current.version() + 1, current.loading(), current.failure());
        Fetch fetch = running;
        if (fetch != null) {
            fetch.changesSince.add(change);
        }
    }

    /** Completes on the game thread once no fetch is running, with the state then; at once when none is. */
    public CompletableFuture<State<T>> settled() {
        ClientThread.check(owner);
        if (running == null) {
            return CompletableFuture.completedFuture(state);
        }
        if (settled == null) {
            settled = new CompletableFuture<>();
        }
        return settled;
    }

    private void start(long now) {
        attempted = true;
        stale = false;
        lastStartedAtMs = now;
        Fetch started = new Fetch(now);
        running = started;
        State<T> current = state;
        state = new State<>(current.value(), current.hasValue(), current.version(), true, current.failure());
        CompletableFuture<T> future;
        try {
            future = Objects.requireNonNull(fetch.apply(started), "the fetch returned no future");
        } catch (RuntimeException failure) {
            finish(started, null, failure);
            return;
        }
        future.whenComplete((value, failure) -> handBack.execute(() -> finish(started, value, failure)));
    }

    private void finish(Fetch finished, T value, Throwable failure) {
        if (running != finished) {
            return;
        }
        running = null;
        Throwable cause = unwrap(failure);
        if (cause == null && value == null) {
            cause = new IllegalStateException(name + " fetched nothing");
        }
        if (cause == null) {
            try {
                for (UnaryOperator<T> change : finished.changesSince) {
                    value = Objects.requireNonNull(change.apply(value), "the change returned no value");
                }
            } catch (RuntimeException replayFailure) {
                cause = replayFailure;
            }
        }
        State<T> current = state;
        if (cause == null) {
            lastFailed = false;
            boolean same = keepEqualValue && current.hasValue() && current.value().equals(value);
            T kept = same ? current.value() : value;
            state = new State<>(kept, true, same ? current.version() : current.version() + 1, false, null);
            notify(whenFetched, kept);
        } else {
            lastFailed = true;
            state = clearOnFailure.test(cause) && current.hasValue()
                    ? new State<>(emptyValue, false, current.version() + 1, false, cause)
                    : new State<>(current.value(), current.hasValue(), current.version(), false, cause);
            notify(whenFailed, cause);
        }
        settle();
    }

    private void settle() {
        CompletableFuture<State<T>> waiting = settled;
        settled = null;
        if (waiting != null) {
            waiting.complete(state);
        }
    }

    private <V> void notify(Consumer<V> listener, V value) {
        try {
            listener.accept(value);
        } catch (RuntimeException failure) {
            SeqClient.LOGGER.warn("[{}] A listener failed", name, failure);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private final class Fetch implements Ticket {
        private final long startedAtMs;
        /** Changes {@link #mutate} made while this fetch ran, to make again to its answer. */
        private final List<UnaryOperator<T>> changesSince = new ArrayList<>();

        private Fetch(long startedAtMs) {
            this.startedAtMs = startedAtMs;
        }

        @Override
        public boolean current() {
            return running == this;
        }
    }

    public static final class Builder<T> {
        private final String name;
        private final Function<Ticket, CompletableFuture<T>> fetch;
        private LongSupplier clockMs = Clocks::monotonicMillis;
        private Executor handBack = ClientThread.executor();
        private long refreshEveryMs = Long.MAX_VALUE;
        private long retryAfterMs = -1;
        private long deadlineMs;
        private Predicate<Throwable> clearOnFailure = failure -> false;
        private boolean keepEqualValue;
        private T emptyValue;
        private Consumer<T> whenFetched = value -> {};
        private Consumer<Throwable> whenFailed = failure -> {};

        private Builder(String name, Function<Ticket, CompletableFuture<T>> fetch) {
            this.name = Objects.requireNonNull(name, "name");
            this.fetch = Objects.requireNonNull(fetch, "fetch");
        }

        /** The milliseconds intervals and the deadline are measured in. {@link Clocks#monotonicMillis()} by default. */
        public Builder<T> clock(LongSupplier clockMs) {
            this.clockMs = Objects.requireNonNull(clockMs, "clockMs");
            return this;
        }

        /** Where results are handed to the game thread. {@link ClientThread#executor()} by default. */
        public Builder<T> handBack(Executor handBack) {
            this.handBack = Objects.requireNonNull(handBack, "handBack");
            return this;
        }

        /** How long after a successful fetch started the next may start. Never, by default. */
        public Builder<T> refreshEveryMs(long refreshEveryMs) {
            this.refreshEveryMs = refreshEveryMs;
            return this;
        }

        /** How long after a failed fetch started the next may start. The refresh interval, by default. */
        public Builder<T> retryAfterMs(long retryAfterMs) {
            this.retryAfterMs = retryAfterMs;
            return this;
        }

        /**
         * How long a fetch may run before the next request fails it with a
         * {@link TimeoutException} and drops its result. No limit, by default.
         */
        public Builder<T> deadlineMs(long deadlineMs) {
            this.deadlineMs = deadlineMs;
            return this;
        }

        /** Drops the value when a fetch fails, where an old answer is worse than none. Kept, by default. */
        public Builder<T> clearOnFailure() {
            return clearOnFailure(failure -> true);
        }

        /** Drops the value when a fetch fails in a way {@code when} accepts, such as "not signed in"; keeps it otherwise. */
        public Builder<T> clearOnFailure(Predicate<Throwable> when) {
            this.clearOnFailure = Objects.requireNonNull(when, "when");
            return this;
        }

        /** Keeps the current value, and its version, when a fetch returns an equal one, so readers needn't redo work. */
        public Builder<T> keepEqualValue() {
            this.keepEqualValue = true;
            return this;
        }

        /** What {@link State#value()} holds while there is no value, instead of null. */
        public Builder<T> emptyValue(T emptyValue) {
            this.emptyValue = emptyValue;
            return this;
        }

        /** Told each fetched value, on the game thread, after the state has it. */
        public Builder<T> whenFetched(Consumer<T> whenFetched) {
            this.whenFetched = Objects.requireNonNull(whenFetched, "whenFetched");
            return this;
        }

        /** Told why each fetch failed, on the game thread, after the state has it. */
        public Builder<T> whenFailed(Consumer<Throwable> whenFailed) {
            this.whenFailed = Objects.requireNonNull(whenFailed, "whenFailed");
            return this;
        }

        public Refreshable<T> build() {
            return new Refreshable<>(this);
        }
    }
}
