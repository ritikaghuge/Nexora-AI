package com.nexora.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nexora.model.Mode;
import org.junit.jupiter.api.Test;

class SmartRouterTest {
    private final SmartRouter r = new SmartRouter();

    private Mode route(String s) { return r.route(s, false, false).mode(); }

    @Test void code() { assertEquals(Mode.PROGRAMMING, route("Why does my Java code throw a NullPointerException?")); }
    @Test void math() { assertEquals(Mode.MATH, route("Solve the equation 3x^2 - 12x + 9 = 0")); }
    @Test void pureArithmeticIsMath() { assertEquals(Mode.MATH, route("12 * (3 + 4)")); }
    @Test void web() { assertEquals(Mode.RESEARCH, route("What is the latest news about open source LLMs?")); }
    @Test void deepResearch() { assertEquals(Mode.DEEP_RESEARCH, route("Do deep research on vector databases")); }
    @Test void projectBuilder() { assertEquals(Mode.PROJECT_BUILDER, route("Build me a library management web app")); }
    @Test void writing() { assertEquals(Mode.WRITING, route("Write an email asking for leave")); }
    @Test void learning() { assertEquals(Mode.LEARNING, route("Create a quiz on photosynthesis")); }
    @Test void general() { assertEquals(Mode.GENERAL, route("Tell me something interesting about octopuses")); }
    @Test void attachmentsWin() {
        assertEquals(Mode.IMAGE, r.route("what is this", true, false).mode());
        assertEquals(Mode.DOCUMENT, r.route("summarize it", false, true).mode());
    }
}
