package com.nexora.service;

import com.nexora.model.Mode;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Smart Mode: inspects the request and picks the best specialised mode (prompt, tools, model override). */
@Service
public class SmartRouter {
    public record RouteDecision(Mode mode, String reason) {}

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Pattern PROJECT = Pattern.compile(
            "\\b(build|create|make|generate|develop)\\b.{0,40}\\b(app|application|website|web app|system|project|platform|api|backend|full[- ]?stack)\\b|project structure|scaffold", F);
    private static final Pattern DEEP = Pattern.compile("deep research|research report|in-depth research|comprehensive research|thorough research|literature review", F);
    private static final Pattern WEB = Pattern.compile(
            "\\b(latest|news|today|right now|this week|this month|recent(ly)?|breaking|stock price|weather|who won|release date|search the web|look up|google)\\b", F);
    private static final Pattern CODE = Pattern.compile(
            "```|\\b(java|python|javascript|typescript|c\\+\\+|sql|html|css|react|spring|maven|hibernate|regex|bug|stack ?trace|exception|compile[dr]?|function|class|api|json|git|docker|algorithm|code|debug|refactor|query|script|mysql)\\b", F);
    private static final Pattern MATH = Pattern.compile(
            "\\b(solve|equation|integral|derivative|calculate|compute|probability|variance|median|standard deviation|matrix|algebra|theorem|simplify|factori[sz]e|percentage)\\b|^[\\d\\s+\\-*/^().=%]+$|\\d\\s*[+\\-*/^]\\s*\\d", F);
    private static final Pattern WRITE = Pattern.compile(
            "\\b(email|e-mail|resume|cv|cover letter|rewrite|rephrase|paraphrase|grammar|proofread|translate|essay|letter|assignment|blog|story|poem|speech|caption|slogan)\\b", F);
    private static final Pattern LEARN = Pattern.compile(
            "\\b(quiz|mcqs?|multiple choice|flashcards?|study notes|interview|practice questions|teach me|lesson|explain .{0,40}(beginner|simple|like i'?m)|viva)\\b", F);

    public RouteDecision route(String text, boolean hasImage, boolean hasDocs) {
        if (hasImage) return new RouteDecision(Mode.IMAGE, "an image is attached, so a vision-capable model is used");
        if (hasDocs && !PROJECT.matcher(text).find()) return new RouteDecision(Mode.DOCUMENT, "a document is attached to this chat");
        if (PROJECT.matcher(text).find()) return new RouteDecision(Mode.PROJECT_BUILDER, "you asked to build an application/project");
        if (DEEP.matcher(text).find()) return new RouteDecision(Mode.DEEP_RESEARCH, "you asked for in-depth research");
        if (WEB.matcher(text).find()) return new RouteDecision(Mode.RESEARCH, "the question needs up-to-date information from the web");
        if (CODE.matcher(text).find()) return new RouteDecision(Mode.PROGRAMMING, "this looks like a programming question");
        if (MATH.matcher(text.trim()).find()) return new RouteDecision(Mode.MATH, "this looks like a maths problem, so the calculator tool is used");
        if (LEARN.matcher(text).find()) return new RouteDecision(Mode.LEARNING, "this looks like a learning/practice request");
        if (WRITE.matcher(text).find()) return new RouteDecision(Mode.WRITING, "this looks like a writing task");
        return new RouteDecision(Mode.GENERAL, "general question");
    }
}
