package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.TreasuryOutErrorMessage;
import com.seqwawa.seq.network.TreasuryOutRecordedMessage;
import com.seqwawa.seq.network.TreasuryOutRequest;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the lifecycle and user feedback for treasury OUT submissions. */
public final class TreasuryOutManager {

    public static final String TREASURY_MINECRAFT_ACCOUNT = "cinfrascitizen";
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "seq-treasury-timeouts");
                thread.setDaemon(true);
                return thread;
            });

    private final Supplier<String> requestIdFactory;
    private final Transport transport;
    private final TimeoutScheduler timeoutScheduler;
    private final Map<String, PendingRequest> pendingRequests = new ConcurrentHashMap<>();

    public TreasuryOutManager() {
        this(
                () -> UUID.randomUUID().toString(),
                request -> ConnectionManager.getInstance().sendTreasuryOut(request),
                (task, delay) -> {
                    var future = TIMEOUT_EXECUTOR.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
                    return () -> future.cancel(false);
                });
    }

    TreasuryOutManager(Supplier<String> requestIdFactory, Transport transport, TimeoutScheduler timeoutScheduler) {
        this.requestIdFactory = requestIdFactory;
        this.transport = transport;
        this.timeoutScheduler = timeoutScheduler;
    }

    public boolean submit(
            String activeMinecraftUsername,
            boolean websocketConnected,
            String amount,
            String payouter,
            String reason,
            Consumer<String> feedback) {
        Consumer<String> safeFeedback = feedback == null ? ignored -> {} : feedback;
        if (!isTreasuryMinecraftAccount(activeMinecraftUsername)) {
            safeFeedback.accept("Treasury OUT can only be used while playing as cinfrascitizen.");
            return false;
        }
        if (!websocketConnected) {
            safeFeedback.accept("Treasury OUT requires a connected Sequoia WebSocket. Run /seq connect first.");
            return false;
        }

        String normalizedAmount = normalize(amount);
        String normalizedPayouter = normalize(payouter);
        String normalizedReason = normalize(reason);
        if (normalizedAmount == null || normalizedPayouter == null || normalizedReason == null) {
            safeFeedback.accept("Amount, payouter, and reason are all required for Treasury OUT.");
            return false;
        }

        String requestId = requestIdFactory.get();
        TreasuryOutRequest request =
                new TreasuryOutRequest(requestId, normalizedAmount, normalizedPayouter, normalizedReason);
        PendingRequest pending = new PendingRequest(request, safeFeedback);
        pendingRequests.put(requestId, pending);

        safeFeedback.accept("Submitting Treasury OUT: " + normalizedAmount + " — " + normalizedPayouter
                + " — " + normalizedReason);
        boolean sent;
        try {
            sent = transport.send(request);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[Treasury] Failed to send requestId={}", requestId, exception);
            sent = false;
        }
        if (!sent) {
            pendingRequests.remove(requestId, pending);
            safeFeedback.accept("Treasury OUT was not sent. Reconnect, then try again.");
            return false;
        }

        pending.timeout = timeoutScheduler.schedule(() -> handleTimeout(requestId), REQUEST_TIMEOUT);
        SeqClient.LOGGER.debug("[Treasury] Submitted requestId={}", requestId);
        return true;
    }

    public boolean handleRecorded(TreasuryOutRecordedMessage recorded) {
        if (recorded == null || recorded.requestId() == null) {
            return false;
        }
        PendingRequest pending = pendingRequests.remove(recorded.requestId());
        if (pending == null) {
            return false;
        }
        pending.cancelTimeout();
        SeqClient.LOGGER.debug(
                "[Treasury] Correlated requestId={} responseType={}", recorded.requestId(), recorded.type());
        pending.feedback.accept("Treasury OUT recorded: " + display(recorded.amount(), pending.request.amount()) + " — "
                + display(recorded.sheetName(), "unknown sheet") + ", row " + recorded.row() + " ("
                + display(recorded.date(), "unknown date") + ")");
        return true;
    }

    public boolean handleError(TreasuryOutErrorMessage error) {
        if (error == null || error.requestId() == null) {
            return false;
        }
        PendingRequest pending = pendingRequests.remove(error.requestId());
        if (pending == null) {
            return false;
        }
        pending.cancelTimeout();
        String code = error.code() == null ? "" : error.code().trim().toLowerCase(Locale.ROOT);
        SeqClient.LOGGER.debug("[Treasury] Correlated requestId={} responseType=error code={}", error.requestId(), code);
        pending.feedback.accept(readableError(code, error.message()));
        return true;
    }

    public boolean isPending(String requestId) {
        return requestId != null && pendingRequests.containsKey(requestId);
    }

    int pendingCount() {
        return pendingRequests.size();
    }

    public static boolean isTreasuryMinecraftAccount(String username) {
        return username != null && TREASURY_MINECRAFT_ACCOUNT.equalsIgnoreCase(username.trim());
    }

    static String readableError(String code, String backendMessage) {
        return switch (code) {
            case "token_invalid" -> "Treasury OUT failed: reconnect first.";
            case "treasury_forbidden" -> "Treasury OUT failed: this client is not allowed to update the treasury.";
            case "invalid_request" -> "Treasury OUT rejected: "
                    + display(backendMessage, "the backend rejected the submitted values.");
            case "treasury_unavailable" ->
                "Treasury OUT failed: the Google Sheet could not be updated. The entry was not confirmed.";
            case "mod_version_unsupported" -> "Treasury OUT failed: update SeqMod and try again.";
            default -> "Treasury OUT failed: " + display(backendMessage, "unknown backend error.");
        };
    }

    private void handleTimeout(String requestId) {
        PendingRequest pending = pendingRequests.remove(requestId);
        if (pending == null) {
            return;
        }
        SeqClient.LOGGER.debug("[Treasury] Timed out requestId={}", requestId);
        pending.feedback.accept("Treasury OUT timed out. The result is uncertain; check the sheet before submitting again.");
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String display(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @FunctionalInterface
    interface Transport {
        boolean send(TreasuryOutRequest request);
    }

    @FunctionalInterface
    interface TimeoutScheduler {
        Cancellable schedule(Runnable task, Duration delay);
    }

    @FunctionalInterface
    interface Cancellable {
        void cancel();
    }

    private static final class PendingRequest {
        private final TreasuryOutRequest request;
        private final Consumer<String> feedback;
        private volatile Cancellable timeout;

        private PendingRequest(TreasuryOutRequest request, Consumer<String> feedback) {
            this.request = request;
            this.feedback = feedback;
        }

        private void cancelTimeout() {
            Cancellable current = timeout;
            if (current != null) {
                current.cancel();
            }
        }
    }
}
