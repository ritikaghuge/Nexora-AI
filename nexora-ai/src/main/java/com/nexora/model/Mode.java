package com.nexora.model;

public enum Mode {
    SMART, GENERAL, PROGRAMMING, DOCUMENT, RESEARCH, DEEP_RESEARCH, WRITING, MATH, IMAGE, LEARNING, PROJECT_BUILDER;

    public static Mode parse(String s) {
        if (s == null || s.isBlank()) return GENERAL;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return GENERAL; }
    }
}
