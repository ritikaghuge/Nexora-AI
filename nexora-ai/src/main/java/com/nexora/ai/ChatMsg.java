package com.nexora.ai;

import java.util.List;

public record ChatMsg(String role, String content, List<ImageData> images) {
    public static ChatMsg of(String role, String content) { return new ChatMsg(role, content, List.of()); }
}
