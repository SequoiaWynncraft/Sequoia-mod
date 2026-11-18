package com.seqwawa.seq.images;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A test that runs the bundled decoder helper, so it needs this machine's helpers in the build's
 * packs from {@code native/out/sandbox}. A build made with {@code -Pallow_partial_native} whose
 * packs lack them, or that has none, skips it and says how to get them. Every other build runs
 * it, and it fails there if they are missing, as CI's do. An index that cannot be read fails the
 * test in either build rather than skipping it.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresDecoderPacks.Condition.class)
@interface RequiresDecoderPacks {

    /** Skips the test only where the build allowed missing packs and this machine's helpers are missing. */
    final class Condition implements ExecutionCondition {
        private static final String FETCH = "run `mise run native:fetch` to test the decoder too";

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            if (!"optional".equals(System.getProperty("seq.decoderPacks"))) {
                return ConditionEvaluationResult.enabled("the decoder packs are required in this build");
            }
            return missingHostHelper()
                    .map(why -> ConditionEvaluationResult.disabled(why + ": " + FETCH))
                    .orElse(ConditionEvaluationResult.enabled("this machine's helpers are in the packs"));
        }

        /** Why this machine's helpers are not in the build's packs, or empty when they are. */
        static Optional<String> missingHostHelper() {
            String platform;
            try {
                platform = IsolatedDecoder.platform(System.getProperty("os.name"), System.getProperty("os.arch"));
            } catch (IOException unsupported) {
                return Optional.of("There is no decoder helper for this OS and CPU");
            }
            try (InputStream index = resource("provenance.json")) {
                if (index == null) return Optional.of("No decoder packs in this build");
                List<BundledHelpers.Pack> packs =
                        BundledHelpers.parse(new String(index.readAllBytes(), StandardCharsets.UTF_8));
                for (String helper : IsolatedDecoder.helpers(platform)) {
                    String key = platform + "/" + helper;
                    Optional<BundledHelpers.Pack> pack = packs.stream()
                            .filter(candidate -> candidate.members().stream().anyMatch(member -> member.path().equals(key)))
                            .findFirst();
                    if (pack.isEmpty()) return Optional.of("This build's packs have no " + key);
                    try (InputStream file = resource(pack.get().file())) {
                        if (file == null) return Optional.of("This build has no " + pack.get().file() + " for " + key);
                    }
                }
                return Optional.empty();
            } catch (IOException unreadable) {
                // A missing pack skips; an index that will not parse is a real failure.
                throw new UncheckedIOException("The decoder packs' index cannot be read", unreadable);
            }
        }

        private static InputStream resource(String name) {
            return Condition.class.getResourceAsStream("/sandbox/" + name);
        }
    }
}
