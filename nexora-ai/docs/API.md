# NEXORA AI — REST API

All endpoints except register/login need the session cookie (`JSESSIONID`) from `POST /api/auth/login`.
Errors are JSON: `{"status": 400, "message": "..."}`.

## Auth
| Method | Path | Body | Notes |
|---|---|---|---|
| POST | /api/auth/register | `{email, password(>=8), displayName}` | 201, creates settings + default Ollama provider |
| POST | /api/auth/login | `{email, password}` | sets session cookie |
| POST | /api/auth/logout | – | |
| GET | /api/auth/me | – | 401 when not signed in |
| PUT | /api/auth/profile | `{displayName}` | |
| POST | /api/auth/password | `{currentPassword, newPassword}` | |

## Chat (Server-Sent Events)
`POST /api/chat/stream` body:
```json
{"conversationId": null, "message": "text", "mode": "SMART", "fileIds": [], "regenerate": false, "providerId": null, "model": null}
```
`mode`: SMART, GENERAL, PROGRAMMING, DOCUMENT, RESEARCH, DEEP_RESEARCH, WRITING, MATH, IMAGE, LEARNING, PROJECT_BUILDER.
`regenerate: true` re-answers the last user message of `conversationId`.
Events (`event:` name → JSON `data:`):
`meta {conversationId,title,mode,requestedMode,routeReason,model,provider}`, `status {s}`, `sources [{title,url,snippet}]`,
`token {t}`, `error {message}`, `done {messageId,conversationId}`.
Close the connection to stop generation; the partial answer is saved.

## Conversations & messages
| Method | Path | Notes |
|---|---|---|
| GET | /api/conversations?q= | search title and message text |
| PATCH | /api/conversations/{id} | `{title}` rename |
| DELETE | /api/conversations/{id} | |
| DELETE | /api/conversations | clear all history |
| GET | /api/conversations/{id}/messages | |
| GET | /api/messages/{id}/project.zip | builds a ZIP from `### File: path` blocks (Project Builder) |

## Files
| Method | Path | Notes |
|---|---|---|
| POST | /api/files | multipart `file`; pdf, docx, txt, md, png, jpg, webp, gif; max 15 MB |
| GET | /api/files | list |
| GET | /api/files/{id}/content | image bytes (owner only) |
| DELETE | /api/files/{id} | |

## Settings, providers, memory, tools
| Method | Path | Notes |
|---|---|---|
| GET/PUT | /api/settings | `{theme, memoryEnabled, defaultProviderId, defaultModel, temperature, customInstructions}` |
| GET/POST | /api/providers | `{name,type(OLLAMA\|OPENAI_COMPATIBLE),baseUrl,apiKey,defaultModel,visionModel,codeModel,preferred}` |
| PUT/DELETE | /api/providers/{id} | key is write-only (`hasApiKey` is returned) |
| POST | /api/providers/{id}/test | `{ok,message,models[]}` |
| GET/POST/DELETE | /api/memory, /api/memory/{id} | |
| POST | /api/tools/calc | `{expression}` → `{result}` |
| GET | /api/tools/search-provider | active web-search provider |
