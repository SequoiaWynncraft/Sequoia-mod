package com.seqwawa.seq.images;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * A test that runs the bundled decoder helper, so it needs the helper packs from
 * {@code native/out/sandbox} in the build. A build made with
 * {@code -Pallow_partial_native} and no packs skips it and says how to get them.
 * Every other build runs it, and it fails there if the packs are missing, as CI's do.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(RequiresDecoderPacks.Condition.class)
@interface RequiresDecoderPacks {

    /** Skips the test only where the build allowed the packs to be missing and they are. */
    final class Condition implements ExecutionCondition {
        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
            boolean optional = "optional".equals(System.getProperty("seq.decoderPacks"));
            boolean present = Condition.class.getResource("/sandbox/provenance.json") != null;
            return !optional || present
                    ? ConditionEvaluationResult.enabled("the decoder packs are in this build, or required")
                    : ConditionEvaluationResult.disabled(
                            "No decoder packs in this build: run `mise run native:fetch` to test the decoder too");
        }
    }
}
