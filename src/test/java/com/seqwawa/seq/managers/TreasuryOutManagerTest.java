package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.network.TreasuryOutErrorMessage;
import com.seqwawa.seq.network.TreasuryOutRecordedMessage;
import com.seqwawa.seq.network.TreasuryOutRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TreasuryOutManagerTest {

    @Test
    void rejectsAnotherMinecraftAccountWithoutCreatingOrSendingARequest() {
        TestContext context = contextWithIds("unused");

        boolean submitted = context.manager.submit(
                "SomeoneElse", "SomeoneElse", true, "2stx", "Solo", "season payout", context.feedback::add);

        assertFalse(submitted);
        assertEquals(0, context.sent.size());
        assertEquals(0, context.idCalls.get());
        assertTrue(context.feedback.getLast().contains("playing as cinfrascitizen"));
    }

    @Test
    void rejectsDisconnectedOrUnauthenticatedSession() {
        TestContext context = contextWithIds("unused");

        boolean submitted = context.manager.submit(
                "cinfrascitizen", "cinfrascitizen", false, "2stx", "Solo", "season payout", context.feedback::add);

        assertFalse(submitted);
        assertEquals(0, context.sent.size());
        assertEquals(0, context.idCalls.get());
        assertTrue(context.feedback.getLast().contains("authenticated Sequoia connection"));
    }

    @Test
    void allowsPersonalOperatorSessionWhilePlayingAsCinfrascitizen() {
        TestContext context = contextWithIds("request-1");

        boolean submitted = context.manager.submit(
                "cinfrascitizen", "AuthorizedOperator", true, "2stx", "Solo", "season payout", context.feedback::add);

        assertTrue(submitted);
        assertEquals(1, context.sent.size());
        assertEquals("request-1", context.sent.getFirst().requestId());
    }

    @Test
    void rejectsUnidentifiedOperatorSession() {
        TestContext context = contextWithIds("unused");

        boolean submitted = context.manager.submit(
                "cinfrascitizen", null, true, "2stx", "Solo", "season payout", context.feedback::add);

        assertFalse(submitted);
        assertEquals(0, context.sent.size());
        assertTrue(context.feedback.getLast().contains("identified operator session"));
    }

    @Test
    void rejectsBlankFieldsBeforeGeneratingARequestId() {
        TestContext context = contextWithIds("unused");

        boolean submitted = context.manager.submit(
                "cinfrascitizen", "cinfrascitizen", true, "2stx", "Solo", "  ", context.feedback::add);

        assertFalse(submitted);
        assertEquals(0, context.idCalls.get());
        assertEquals(0, context.sent.size());
        assertTrue(context.feedback.getLast().contains("all required"));
    }

    @Test
    void confirmationIncludesEverySensitiveFieldBeforeSend() {
        AtomicReference<String> feedbackAtSend = new AtomicReference<>();
        List<String> feedback = new ArrayList<>();
        FakeTimeoutScheduler scheduler = new FakeTimeoutScheduler();
        TreasuryOutManager manager = new TreasuryOutManager(
                () -> "request-1",
                request -> {
                    feedbackAtSend.set(feedback.getLast());
                    return true;
                },
                scheduler);

        assertTrue(manager.submit(
                "cinfrascitizen",
                "cinfrascitizen",
                true,
                "32le",
                "cinfrascitizen",
                "guild event prizes",
                feedback::add));
        assertEquals(
                "Submitting Treasury OUT: 32le — cinfrascitizen — guild event prizes", feedbackAtSend.get());
    }

    @Test
    void correlatesRecordedResponseByRequestId() {
        TestContext context = contextWithIds("request-1", "request-2");
        assertTrue(submit(context, "first reason"));
        assertTrue(submit(context, "second reason"));

        assertFalse(context.manager.handleRecorded(new TreasuryOutRecordedMessage(
                "treasury_out_recorded", "unknown", "Season 32", 7, "2STX", "Solo", "ignored", "2026-08-01")));
        assertEquals(2, context.manager.pendingCount());

        assertTrue(context.manager.handleRecorded(new TreasuryOutRecordedMessage(
                "treasury_out_recorded",
                "request-2",
                "Season 32",
                7,
                "2STX",
                "Solo",
                "second reason",
                "2026-08-01")));
        assertTrue(context.manager.isPending("request-1"));
        assertFalse(context.manager.isPending("request-2"));
        assertEquals("Treasury OUT recorded: 2STX — Season 32, row 7 (2026-08-01)", context.feedback.getLast());
    }

    @ParameterizedTest
    @MethodSource("backendErrors")
    void mapsAndCompletesRelevantBackendErrors(String code, String message, String expectedText) {
        TestContext context = contextWithIds("request-1");
        assertTrue(submit(context, "season payout"));

        assertTrue(context.manager.handleError(new TreasuryOutErrorMessage("request-1", code, message)));

        assertFalse(context.manager.isPending("request-1"));
        assertTrue(context.feedback.getLast().contains(expectedText));
        assertTrue(context.scheduler.cancelled);
    }

    @Test
    void timeoutReportsUncertainResultWithoutRetryingOrGeneratingANewRequestId() {
        TestContext context = contextWithIds("request-1", "must-not-be-used");
        assertTrue(submit(context, "season payout"));

        context.scheduler.runTimeout();

        assertFalse(context.manager.isPending("request-1"));
        assertEquals(1, context.sent.size());
        assertEquals("request-1", context.sent.getFirst().requestId());
        assertEquals(1, context.idCalls.get());
        assertTrue(context.feedback.getLast().contains("result is uncertain"));
    }

    @Test
    void failedTransportRemovesPendingRequest() {
        FakeTimeoutScheduler scheduler = new FakeTimeoutScheduler();
        TreasuryOutManager manager = new TreasuryOutManager(() -> "request-1", request -> false, scheduler);
        List<String> feedback = new ArrayList<>();

        assertFalse(manager.submit(
                "cinfrascitizen",
                "cinfrascitizen",
                true,
                "2stx",
                "Solo",
                "season payout",
                feedback::add));
        assertEquals(0, manager.pendingCount());
        assertTrue(feedback.getLast().contains("was not sent"));
    }

    private static Stream<Arguments> backendErrors() {
        return Stream.of(
                Arguments.of("token_invalid", "expired", "authenticate and reconnect first"),
                Arguments.of("treasury_forbidden", "forbidden", "Treasury Discord role"),
                Arguments.of("invalid_request", "amount must use a supported denomination", "supported denomination"),
                Arguments.of("treasury_unavailable", "sheet update failed", "Google Sheet could not be updated"),
                Arguments.of("mod_version_unsupported", "old client", "update SeqMod"));
    }

    private static boolean submit(TestContext context, String reason) {
        return context.manager.submit(
                "cinfrascitizen", "cinfrascitizen", true, "2stx", "Solo", reason, context.feedback::add);
    }

    private static TestContext contextWithIds(String... ids) {
        AtomicInteger idCalls = new AtomicInteger();
        Supplier<String> factory = () -> ids[idCalls.getAndIncrement()];
        List<TreasuryOutRequest> sent = new ArrayList<>();
        FakeTimeoutScheduler scheduler = new FakeTimeoutScheduler();
        TreasuryOutManager manager = new TreasuryOutManager(factory, request -> {
            sent.add(request);
            return true;
        }, scheduler);
        return new TestContext(manager, sent, new ArrayList<>(), scheduler, idCalls);
    }

    private record TestContext(
            TreasuryOutManager manager,
            List<TreasuryOutRequest> sent,
            List<String> feedback,
            FakeTimeoutScheduler scheduler,
            AtomicInteger idCalls) {}

    private static final class FakeTimeoutScheduler implements TreasuryOutManager.TimeoutScheduler {
        private Runnable timeout;
        private Duration delay;
        private boolean cancelled;

        @Override
        public TreasuryOutManager.Cancellable schedule(Runnable task, Duration delay) {
            this.timeout = task;
            this.delay = delay;
            return () -> cancelled = true;
        }

        private void runTimeout() {
            assertEquals(TreasuryOutManager.REQUEST_TIMEOUT, delay);
            timeout.run();
        }
    }
}
