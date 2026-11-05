package com.nexora.ai;

import java.io.IOException;

/** Receives streamed tokens. Throw IOException to stop the stream (e.g. client disconnected). */
@FunctionalInterface
public interface TokenSink {
    void accept(String token) throws IOException;
}
