package com.company.platform.scheduler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {
    @Test
    void evaluatesSupportedBooleanAndStatusExpressions() {
        assertTrue(ConditionEvaluator.evaluate("true", "SUCCESS"));
        assertFalse(ConditionEvaluator.evaluate("false", "SUCCESS"));
        assertTrue(ConditionEvaluator.evaluate("status == 'SUCCESS'", "SUCCESS"));
        assertFalse(ConditionEvaluator.evaluate("status != 'SUCCESS'", "SUCCESS"));
        assertTrue(ConditionEvaluator.evaluate("row_count > 0", "SUCCESS", java.util.Map.of("row_count", 12)));
        assertFalse(ConditionEvaluator.evaluate("row_count <= 0", "SUCCESS", java.util.Map.of("row_count", 12)));
        assertTrue(ConditionEvaluator.evaluate("amount >= 99.5", "SUCCESS", java.util.Map.of("amount", "100.25")));
        assertThrows(IllegalStateException.class, () -> ConditionEvaluator.evaluate("missing > 0", "SUCCESS", java.util.Map.of()));
    }
}
