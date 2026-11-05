# NEXORA AI — One Intelligence. Endless Possibilities.

A multifunctional AI assistant built with **Java 21 + Spring Boot 3**, **MySQL**, and a plain **HTML/CSS/JavaScript** front-end.
It runs on **open-source models through Ollama** (or any OpenAI-compatible free API) — no paid subscription required.

> **Honest status:** the source was written and the pure-logic parts (calculator, statistics, Smart-Mode router,
> document chunker, project-ZIP builder) were executed and verified. The full Maven build and the Spring test-suite
> could **not** be run in the environment that generated this project (no internet / no Maven there).
> Run `mvn clean verify` once on your machine (Step 7). If something fails, send the error text and it can be fixed quickly.

## Features

| Area | What it does |
|---|---|
| General chat | Streaming answers (SSE), history, new/rename/delete/search chats, copy, regenerate, stop, Markdown + code highlighting |
| **Smart Mode** | Router picks the right mode per question (code / math / web / documents / images / writing / learning / project) and shows *why* |
| **Deep Research** | Model plans 3 queries → searches → reads pages → writes a structured report with `[n]` citations and a References list |
| **Project Builder** | Describe an app → full file tree + every file's code + setup steps; **Download project ZIP** button builds a real ZIP |
| Programming | Code generation, debugging, review; optional separate *code model* |
| Documents | Upload PDF / DOCX / TXT / MD, relevant-chunk retrieval, summary, key info, notes, Q&A set buttons |
| Web research | Wikipedia (zero setup), SearXNG (free, self-hosted) or Brave Search API; sources are shown separately from AI analysis |
| Writing, Learning | Emails, resumes, reports, grammar, translation; MCQs, quizzes, notes, interview prep |
| Math | A real Java calculator + statistics engine feeds exact results to the model |
| Voice | Browser speech-to-text, text-to-speech, hands-free "Voice chat" loop (Chrome / Edge) |
| Images | Upload images; answered by a vision model (e.g. `llava`, `llama3.2-vision`) |
| Memory | Saved facts (say "Remember that …"), toggle, clear; chat history clear |
| Security | BCrypt, session cookies (HttpOnly, SameSite), validation, encrypted API keys (AES-GCM), upload type + magic-byte checks, SSRF guard, CSP |

**Uncertainty:** every answer carries an "AI-generated, not verified" note, and the model is instructed to end factual answers with a `Confidence: High|Medium|Low` line.
**Not included:** image *generation* (marked optional in the brief).

## Quick start (Windows, PowerShell)

### 1. Install Java 21
Download "Temurin 21 (LTS)" from https://adoptium.net, install, then:
```powershell
java -version      # must say 21
```

### 2. Install Maven
Download the binary zip from https://maven.apache.org/download.cgi, extract (e.g. `C:\tools\maven`), add `C:\tools\maven\bin` to the PATH, reopen PowerShell:
```powershell
mvn -version
```
(IntelliJ IDEA and VS Code's "Extension Pack for Java" also bundle Maven.)

### 3. Install MySQL
Install **MySQL Community Server 8** + **MySQL Workbench** from https://dev.mysql.com/downloads/ and remember the root password.
The app creates the database `nexora_ai` automatically. To create it by hand instead, open `database/schema.sql` in Workbench and run it.

### 4. Install Ollama and a model
Install from https://ollama.com/download, then:
```powershell
ollama pull llama3.2          # small general model (~2 GB)
ollama pull llava             # optional: vision model for image questions
ollama pull qwen2.5-coder:7b  # optional: stronger coding model
ollama list
```
Ollama listens on `http://localhost:11434`.

### 5. Configure
```powershell
Copy-Item .env.example .env
notepad .env
```
Set at least `DB_PASSWORD` (your MySQL root password). Optionally set `APP_ENCRYPTION_KEY` to a long random string.
Do not put quotes around values. Never commit `.env`.

### 6. Run
```powershell
mvn spring-boot:run
```
Open **http://localhost:8080**, create an account, choose **Smart** mode and start chatting.
(In VS Code: open the folder, run `NexoraApplication`. In IntelliJ: open `pom.xml` as a project, run `NexoraApplication`.)

### 7. Test / build a JAR
```powershell
mvn clean verify            # runs all tests (uses an in-memory H2 database, no MySQL needed for tests)
java -jar target\nexora-ai-1.0.0.jar
```

## Using the AI providers
Open **Settings → AI Providers**. Each account starts with *Local Ollama*. Use **Test** to check the connection and list models.
Presets: **Ollama**, **LM Studio**, **Groq** (free API key), **OpenRouter** (has free models). API keys are encrypted in the database and never sent back to the browser.
If a provider is down, chat shows a clear error; switch the default provider in Settings without restarting.

## Web search providers (`.env`)
| `SEARCH_PROVIDER` | Setup |
|---|---|
| `wikipedia` (default) | none — encyclopedic results only |
| `searxng` | run SearXNG (Docker) with JSON output enabled, set `SEARXNG_URL` |
| `brave` | free key from https://brave.com/search/api/ → `BRAVE_API_KEY` |

## Project structure
```
src/main/java/com/nexora
  config/       AppProperties, executor (virtual threads)
  security/     SecurityConfig, user details, current-user helper
  model/        JPA entities (users, conversations, messages, files, settings, providers, memory)
  repository/   Spring Data repositories
  dto/          API request/response records
  ai/           AiProvider (Ollama, OpenAI-compatible), AiGateway, streaming
  service/      ChatService (orchestration), SmartRouter, ResearchService, SearchService, MathTools, ...
  controller/   REST + SSE endpoints
src/main/resources/static   index.html, css/style.css, js/app.js (front-end)
src/test                    unit + integration tests (H2)
database/schema.sql         MySQL schema
docs/API.md                 endpoint reference
```

## Troubleshooting
| Symptom | Fix |
|---|---|
| `Cannot connect to Local Ollama` | Start Ollama (`ollama serve`), check `OLLAMA_BASE_URL`, press **Test** in Settings |
| `model 'xyz' not found` | `ollama pull xyz` or set an installed model name in Settings → Providers |
| `Access denied for user 'root'` | Wrong `DB_PASSWORD` in `.env` |
| `Communications link failure` | MySQL not running (Windows: Services → MySQL80 → Start) |
| `Unsupported class file major version` / compile error about release 21 | `java -version` and `mvn -version` must both show Java 21 |
| Port 8080 busy | set `SERVER_PORT=8081` in `.env` |
| Code is not highlighted / Markdown looks plain | The page loads marked, DOMPurify, highlight.js from cdnjs — needs internet in the browser |
| Microphone button does nothing | Use Chrome/Edge, allow the mic, open via `http://localhost` |
| Slow first answer | Ollama is loading the model into memory; later answers are faster |
| Image question fails | Set a *vision model* (e.g. `llava`) on the provider |

## Security notes
- Passwords: BCrypt. Sessions: server-side, HttpOnly + SameSite=Lax cookie. CSRF tokens are disabled because the API only accepts JSON and SameSite cookies; add CSRF tokens before exposing this on the public internet.
- SQL injection: all queries use JPA / bound parameters.
- Uploads: extension allow-list, 15 MB limit, magic-byte check, random stored names, per-user folders.
- Deep Research page fetching refuses localhost/private addresses.
- There is no login rate-limiting; put the app behind a reverse proxy with HTTPS and rate limits for public use.
