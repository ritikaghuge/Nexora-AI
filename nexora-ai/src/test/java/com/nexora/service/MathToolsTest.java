package com.nexora.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MathToolsTest {
    private final MathTools tools = new MathTools();

    @Test void formatsIntegersWithoutDecimals() { assertEquals("14", MathTools.format(14.0)); }
    @Test void formatsFractions() { assertEquals("0.333333333333", MathTools.format(1.0 / 3)); }

    @Test
    void detectsPureExpression() {
        String n = tools.note("calculate (12.5 * 4 + sqrt(144)) / 2").orElseThrow();
        assertTrue(n.contains("= 31"), n);
    }

    @Test
    void computesStatistics() {
        String n = tools.note("What is the mean and standard deviation of 2, 4, 4, 4, 5, 5, 7, 9").orElseThrow();
        assertTrue(n.contains("mean=5"), n);
        assertTrue(n.contains("population std dev=2"), n);
    }

    @Test void ignoresPlainText() { assertTrue(tools.note("Explain the quadratic formula").isEmpty()); }
}
