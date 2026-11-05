package com.nexora.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ExpressionEvaluatorTest {
    @Test void precedence() { assertEquals(14, ExpressionEvaluator.eval("2+3*4")); }
    @Test void parentheses() { assertEquals(20, ExpressionEvaluator.eval("(2+3)*4")); }
    @Test void unaryMinusBindsLooserThanPower() { assertEquals(-4, ExpressionEvaluator.eval("-2^2")); }
    @Test void powerIsRightAssociative() { assertEquals(512, ExpressionEvaluator.eval("2^3^2")); }
    @Test void functions() { assertEquals(12, ExpressionEvaluator.eval("sqrt(144)")); assertEquals(5, ExpressionEvaluator.eval("max(2, 5)")); }
    @Test void constants() { assertEquals(Math.PI, ExpressionEvaluator.eval("pi"), 1e-12); }
    @Test void unicodeOperators() { assertEquals(6, ExpressionEvaluator.eval("12÷2")); assertEquals(6, ExpressionEvaluator.eval("2×3")); }
    @Test void divisionByZeroFails() { assertThrows(ArithmeticException.class, () -> ExpressionEvaluator.eval("1/0")); }
    @Test void garbageFails() {
        assertThrows(IllegalArgumentException.class, () -> ExpressionEvaluator.eval("2+"));
        assertThrows(IllegalArgumentException.class, () -> ExpressionEvaluator.eval("System.exit(0)"));
        assertThrows(IllegalArgumentException.class, () -> ExpressionEvaluator.eval("foo(1)"));
    }
}
