package com.nexora.ai;

import java.util.List;

public interface AiProvider {
    /** OLLAMA or OPENAI_COMPATIBLE */
    String type();
    void stream(ProviderSettings s, String model, List<ChatMsg> msgs, double temperature, TokenSink sink);
    String complete(ProviderSettings s, String model, List<ChatMsg> msgs, double temperature);
    List<String> listModels(ProviderSettings s);
}
