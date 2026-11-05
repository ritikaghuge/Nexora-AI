package com.nexora.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexora.ai.AiGateway;
import com.nexora.ai.AiProvider;
import com.nexora.ai.ChatMsg;
import com.nexora.ai.ImageData;
import com.nexora.ai.TokenSink;
import com.nexora.dto.Dtos.ChatRequest;
import com.nexora.dto.Dtos.SourceDto;
import com.nexora.exception.AiCancelledException;
import com.nexora.exception.AiException;
import com.nexora.exception.ApiException;
import com.nexora.model.Conversation;
import com.nexora.model.Message;
import com.nexora.model.Mode;
import com.nexora.model.UploadedFile;
import com.nexora.model.User;
import com.nexora.model.UserSettings;
import com.nexora.repository.ConversationRepository;
import com.nexora.repository.FileRepository;
import com.nexora.repository.MessageRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Orchestrates one chat turn: stores the message, routes the mode (Smart Mode), gathers context
 * (documents, web search, calculator, memory), streams the model's answer over SSE, then stores it.
 */
@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int HISTORY_MSGS = 30;
    private static final int HISTORY_CHARS = 28_000;
    private static final int DOC_CHARS = 14_000;
    private static final String DEFAULT_TITLE = "New chat";

    private final ConversationRepository convRepo;
    private final MessageRepository msgRepo;
    private final FileRepository fileRepo;
    private final SettingsService settingsService;
    private final ProviderService providerService;
    private final AiGateway gateway;
    private final PromptService prompts;
    private final SmartRouter router;
    private final SearchService search;
    private final ResearchService research;
    private final MathTools math;
    private final MemoryService memory;
    private final FileService fileService;
    private final ObjectMapper om;
    private final ExecutorService executor;

    public ChatService(ConversationRepository convRepo, MessageRepository msgRepo, FileRepository fileRepo,
                       SettingsService settingsService, ProviderService providerService, AiGateway gateway,
                       PromptService prompts, SmartRouter router, SearchService search, ResearchService research,
                       MathTools math, MemoryService memory, FileService fileService, ObjectMapper om,
                       @Qualifier("aiExecutor") ExecutorService executor) {
        this.convRepo = convRepo;
        this.msgRepo = msgRepo;
        this.fileRepo = fileRepo;
        this.settingsService = settingsService;
        this.providerService = providerService;
        this.gateway = gateway;
        this.prompts = prompts;
        this.router = router;
        this.search = search;
        this.research = research;
        this.math = math;
        this.memory = memory;
        this.fileService = fileService;
        this.om = om;
        this.executor = executor;
    }

    public SseEmitter stream(User user, ChatRequest req) {
        SseEmitter em = new SseEmitter(15 * 60_000L);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        em.onCompletion(() -> cancelled.set(true));
        em.onTimeout(() -> cancelled.set(true));
        em.onError(e -> cancelled.set(true));
        executor.execute(() -> run(user, req, em, cancelled));
        return em;
    }

    private void send(SseEmitter em, String name, Object payload) throws IOException {
        try {
            em.send(SseEmitter.event().name(name).data(om.writeValueAsString(payload)));
        } catch (IllegalStateException e) {
            throw new IOException("stream closed", e);
        }
    }

    private void run(User user, ChatRequest req, SseEmitter em, AtomicBoolean cancelled) {
        StringBuilder out = new StringBuilder();
        List<SourceDto> sources = new ArrayList<>();
        String modeUsed = null;
        String modelUsed = null;
        String errorMsg = null;
        Conversation conv = null;
        try {
            // ---- 1. conversation + user message --------------------------------------------------
            String userText;
            Message userMsg = null;
            List<Long> fileIds;
            if (req.regenerate()) {
                if (req.conversationId() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to regenerate");
                conv = requireConv(user, req.conversationId());
                List<Message> last = msgRepo.findByConversationIdOrderByIdDesc(conv.id, PageRequest.of(0, 2));
                Message m = last.isEmpty() ? null : last.get(0);
                if (m != null && "ASSISTANT".equals(m.role)) {
                    msgRepo.delete(m);
                    m = last.size() > 1 ? last.get(1) : null;
                }
                if (m == null || !"USER".equals(m.role)) throw new ApiException(HttpStatus.BAD_REQUEST, "Nothing to regenerate");
                userMsg = m;
                userText = m.content;
                fileIds = ConversationService.parseIds(m.fileIds);
            } else {
                userText = req.message() == null ? "" : req.message().trim();
                if (userText.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "Message is empty");
                conv = req.conversationId() == null ? newConversation(user) : requireConv(user, req.conversationId());
                fileIds = req.fileIds() == null ? List.of() : req.fileIds();
            }
            List<UploadedFile> owned = fileIds.isEmpty() ? List.of() : fileRepo.findAllByIdInAndUserId(fileIds, user.id);
            for (UploadedFile f : owned) {
                if (!conv.id.equals(f.conversationId)) { f.conversationId = conv.id; fileRepo.save(f); }
            }
            if (userMsg == null) {
                userMsg = new Message();
                userMsg.conversationId = conv.id;
                userMsg.role = "USER";
                userMsg.content = userText;
                userMsg.fileIds = owned.stream().map(f -> String.valueOf(f.id)).collect(Collectors.joining(","));
                msgRepo.save(userMsg);
                if (DEFAULT_TITLE.equals(conv.title)) conv.title = shorten(userText, 60);
            }
            Mode requested = Mode.parse(req.mode());
            conv.mode = requested.name();
            convRepo.save(conv);

            // ---- 2. settings, provider, mode, model ---------------------------------------------
            UserSettings st = settingsService.get(user.id);
            ProviderService.Resolved rp = providerService.resolve(user.id, req.providerId(), st);
            AiProvider provider = gateway.get(rp.cfg().type);
            List<UploadedFile> images = owned.stream().filter(f -> "IMAGE".equals(f.kind)).toList();
            List<UploadedFile> docs = fileRepo.findByConversationIdAndKind(conv.id, "DOCUMENT");

            Mode mode = requested;
            String routeReason = null;
            if (requested == Mode.SMART) {
                SmartRouter.RouteDecision d = router.route(userText, !images.isEmpty(), !docs.isEmpty());
                mode = d.mode();
                routeReason = d.reason();
            }
            String model = req.model();
            if (blank(model)) {
                if (!images.isEmpty() && !blank(rp.cfg().visionModel)) model = rp.cfg().visionModel;
                else if (mode == Mode.PROGRAMMING && !blank(rp.cfg().codeModel)) model = rp.cfg().codeModel;
            }
            if (blank(model)) model = st.defaultModel;
            if (blank(model)) model = rp.cfg().defaultModel;
            if (blank(model)) throw new AiException("No model selected. Open Settings > Providers and set a default model (for Ollama, e.g. llama3.2).");
            modeUsed = mode.name();
            modelUsed = model;

            memory.tryRemember(user.id, userText, st);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("conversationId", conv.id);
            meta.put("title", conv.title);
            meta.put("mode", mode.name());
            meta.put("requestedMode", requested.name());
            meta.put("routeReason", routeReason == null ? "" : routeReason);
            meta.put("model", model);
            meta.put("provider", rp.cfg().name);
            send(em, "meta", meta);

            // ---- 3. build context ------------------------------------------------------------------
            String memText = st.memoryEnabled ? memory.promptText(user.id) : "";
            StringBuilder sys = new StringBuilder(prompts.system(mode, st.customInstructions, memText));
            if (!docs.isEmpty()) {
                int per = Math.max(2000, DOC_CHARS / docs.size());
                sys.append("\n\nThe user uploaded these documents to this conversation:");
                for (UploadedFile d : docs) {
                    sys.append(prompts.documentsBlock(d.originalName, DocumentChunker.select(d.extractedText, userText, per)));
                }
            }
            if (mode == Mode.RESEARCH) {
                send(em, "status", Map.of("s", "Searching the web (" + search.providerName() + ")..."));
                try {
                    sources.addAll(search.search(userText, 6));
                    if (sources.isEmpty()) sys.append("\n\nThe web search returned no results. Say so and answer from your own knowledge, clearly marked as unverified.");
                    else sys.append(prompts.sourcesBlock(sources, "SEARCH RESULTS"));
                } catch (AiException e) {
                    send(em, "status", Map.of("s", "Web search unavailable: " + e.getMessage()));
                    sys.append("\n\nWeb search FAILED. Tell the user that live web search was unavailable and that your answer comes from your own training knowledge and is unverified.");
                }
            } else if (mode == Mode.DEEP_RESEARCH) {
                sources.addAll(research.gather(provider, rp.settings(), model, userText,
                        s -> send(em, "status", Map.of("s", s))));
                if (sources.isEmpty()) sys.append("\n\nNo sources could be retrieved. Say so; do not fabricate sources.");
                else sys.append(prompts.sourcesBlock(sources, "SOURCES"));
            } else if (mode == Mode.MATH) {
                Optional<String> note = math.note(userText);
                note.ifPresent(n -> sys.append("\n\n").append(n));
            }
            if (!sources.isEmpty()) send(em, "sources", sources);
            send(em, "status", Map.of("s", ""));

            // ---- 4. history ------------------------------------------------------------------------
            List<Message> recent = new ArrayList<>(msgRepo.findByConversationIdOrderByIdDesc(conv.id, PageRequest.of(0, HISTORY_MSGS)));
            List<ChatMsg> hist = new ArrayList<>();
            int chars = 0;
            for (Message m : recent) {
                chars += m.content.length();
                if (chars > HISTORY_CHARS && !hist.isEmpty()) break;
                List<ImageData> imgs = List.of();
                if (m.id.equals(userMsg.id) && !images.isEmpty()) imgs = images.stream().map(fileService::imageData).toList();
                hist.add(new ChatMsg(m.role.toLowerCase(), m.content, imgs));
            }
            Collections.reverse(hist);
            List<ChatMsg> msgs = new ArrayList<>();
            msgs.add(ChatMsg.of("system", sys.toString()));
            msgs.addAll(hist);

            // ---- 5. stream ---------------------------------------------------------------------------
            TokenSink sink = tok -> {
                if (cancelled.get()) throw new IOException("client disconnected");
                out.append(tok);
                send(em, "token", Map.of("t", tok));
            };
            provider.stream(rp.settings(), model, msgs, st.temperature, sink);
        } catch (AiCancelledException | IOException e) {
            log.debug("Stream stopped by client");
        } catch (ApiException | AiException e) {
            errorMsg = e.getMessage();
        } catch (Exception e) {
            log.error("Chat failed", e);
            errorMsg = "Unexpected server error while generating the answer.";
        }

        // ---- 6. persist + finish -------------------------------------------------------------------
        long assistantId = 0L;
        try {
            if (conv != null) {
                if (out.length() > 0) {
                    Message a = new Message();
                    a.conversationId = conv.id;
                    a.role = "ASSISTANT";
                    a.content = out.toString();
                    a.modelUsed = modelUsed;
                    a.modeUsed = modeUsed;
                    if (!sources.isEmpty()) a.sourcesJson = om.writeValueAsString(sources);
                    msgRepo.save(a);
                    assistantId = a.id;
                }
                conv.updatedAt = Instant.now();
                convRepo.save(conv);
            }
        } catch (Exception e) {
            log.error("Could not save assistant message", e);
        }
        try {
            if (errorMsg != null) send(em, "error", Map.of("message", errorMsg));
            send(em, "done", Map.of("messageId", assistantId, "conversationId", conv == null ? 0L : conv.id));
        } catch (Exception ignored) { /* client already gone */ }
        try { em.complete(); } catch (Exception ignored) { /* already completed */ }
    }

    private Conversation newConversation(User u) {
        Conversation c = new Conversation();
        c.userId = u.id;
        return convRepo.save(c);
    }

    private Conversation requireConv(User u, Long id) {
        return convRepo.findByIdAndUserId(id, u.id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static String shorten(String s, int max) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
