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
        assertThrows(IllegalStateException.class, () -> ConditionEvaluator.evaluate("row_count > 0", "SUCCESS"));
    }
}
