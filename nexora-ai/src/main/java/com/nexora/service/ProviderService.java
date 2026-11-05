package com.nexora.service;

import com.nexora.ai.AiGateway;
import com.nexora.ai.ProviderSettings;
import com.nexora.config.AppProperties;
import com.nexora.dto.Dtos.ProviderDto;
import com.nexora.dto.Dtos.ProviderRequest;
import com.nexora.dto.Dtos.TestResult;
import com.nexora.exception.AiException;
import com.nexora.exception.ApiException;
import com.nexora.model.AiProviderConfig;
import com.nexora.model.UserSettings;
import com.nexora.repository.AiProviderConfigRepository;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderService {
    public record Resolved(AiProviderConfig cfg, ProviderSettings settings) {}

    private final AiProviderConfigRepository repo;
    private final CryptoService crypto;
    private final AiGateway gateway;
    private final AppProperties props;

    public ProviderService(AiProviderConfigRepository repo, CryptoService crypto, AiGateway gateway, AppProperties props) {
        this.repo = repo;
        this.crypto = crypto;
        this.gateway = gateway;
        this.props = props;
    }

    public ProviderDto toDto(AiProviderConfig c) {
        return new ProviderDto(c.id, c.name, c.type, c.baseUrl, c.apiKeyEnc != null && !c.apiKeyEnc.isBlank(),
                c.defaultModel, c.visionModel, c.codeModel, c.preferred);
    }

    public List<ProviderDto> list(Long uid) {
        return repo.findByUserIdOrderByIdAsc(uid).stream().map(this::toDto).toList();
    }

    public void createDefault(Long uid) {
        AiProviderConfig c = new AiProviderConfig();
        c.userId = uid;
        c.name = "Local Ollama";
        c.type = "OLLAMA";
        c.baseUrl = props.ollama().baseUrl();
        c.defaultModel = props.ollama().defaultModel();
        c.preferred = true;
        repo.save(c);
    }

    @Transactional
    public ProviderDto create(Long uid, ProviderRequest r) {
        AiProviderConfig c = new AiProviderConfig();
        c.userId = uid;
        apply(c, r);
        return finish(uid, c, r);
    }

    @Transactional
    public ProviderDto update(Long uid, Long id, ProviderRequest r) {
        AiProviderConfig c = require(uid, id);
        apply(c, r);
        return finish(uid, c, r);
    }

    private ProviderDto finish(Long uid, AiProviderConfig c, ProviderRequest r) {
        List<AiProviderConfig> all = repo.findByUserIdOrderByIdAsc(uid);
        if (Boolean.TRUE.equals(r.preferred()) || all.stream().noneMatch(x -> x.preferred && !x.equals(c))) {
            for (AiProviderConfig o : all) {
                if (o.preferred && !o.equals(c)) { o.preferred = false; repo.save(o); }
            }
            c.preferred = true;
        } else if (Boolean.FALSE.equals(r.preferred())) {
            c.preferred = false;
        }
        return toDto(repo.save(c));
    }

    private void apply(AiProviderConfig c, ProviderRequest r) {
        String url = r.baseUrl().trim();
        try {
            URI u = URI.create(url);
            if (u.getScheme() == null || !(u.getScheme().equals("http") || u.getScheme().equals("https")) || u.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Base URL must be a valid http:// or https:// address");
        }
        c.name = r.name().trim();
        c.type = r.type();
        c.baseUrl = url;
        c.defaultModel = blankToNull(r.defaultModel());
        c.visionModel = blankToNull(r.visionModel());
        c.codeModel = blankToNull(r.codeModel());
        if (Boolean.TRUE.equals(r.clearApiKey())) c.apiKeyEnc = null;
        if (r.apiKey() != null && !r.apiKey().isBlank()) c.apiKeyEnc = crypto.encrypt(r.apiKey().trim());
    }

    @Transactional
    public void delete(Long uid, Long id) {
        repo.delete(require(uid, id));
    }

    public AiProviderConfig require(Long uid, Long id) {
        return repo.findByIdAndUserId(id, uid).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Provider not found"));
    }

    public ProviderSettings settings(AiProviderConfig c) {
        String key = c.apiKeyEnc == null || c.apiKeyEnc.isBlank() ? null : crypto.decrypt(c.apiKeyEnc);
        return new ProviderSettings(c.type, c.baseUrl, key, c.name);
    }

    public Resolved resolve(Long uid, Long requestedId, UserSettings st) {
        List<AiProviderConfig> all = repo.findByUserIdOrderByIdAsc(uid);
        AiProviderConfig chosen = null;
        Long wanted = requestedId != null ? requestedId : st.defaultProviderId;
        if (wanted != null) chosen = all.stream().filter(p -> p.id.equals(wanted)).findFirst().orElse(null);
        if (chosen == null) chosen = all.stream().filter(p -> p.preferred).findFirst().orElse(all.isEmpty() ? null : all.get(0));
        if (chosen == null) throw new AiException("No AI provider configured. Open Settings > Providers and add one (for example Ollama).");
        return new Resolved(chosen, settings(chosen));
    }

    public TestResult test(Long uid, Long id) {
        AiProviderConfig c = require(uid, id);
        try {
            List<String> models = gateway.get(c.type).listModels(settings(c));
            String msg = models.isEmpty()
                    ? "Connected, but the provider lists no models. For Ollama run: ollama pull llama3.2"
                    : "Connected. " + models.size() + " model(s) available.";
            String dm = c.defaultModel;
            if (dm != null && !dm.isBlank() && !models.isEmpty()
                    && models.stream().noneMatch(m -> m.equals(dm) || m.startsWith(dm + ":"))) {
                msg += " Warning: default model '" + dm + "' was not found in that list.";
            }
            return new TestResult(true, msg, models);
        } catch (AiException e) {
            return new TestResult(false, e.getMessage(), List.of());
        }
    }

    private static String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
