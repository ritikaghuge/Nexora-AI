package com.nexora.ai;

import com.nexora.exception.AiException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AiGateway {
    private final Map<String, AiProvider> providers;

    public AiGateway(List<AiProvider> list) {
        this.providers = list.stream().collect(Collectors.toMap(AiProvider::type, Function.identity()));
    }

    public AiProvider get(String type) {
        AiProvider p = type == null ? null : providers.get(type.toUpperCase());
        if (p == null) throw new AiException("Unknown AI provider type: " + type);
        return p;
    }
}
