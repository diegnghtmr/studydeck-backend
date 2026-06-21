# StudyDeck AI — Agent Skill

StudyDeck AI is a spaced-repetition flashcard service. This skill tells an AI agent how to drive it
through the HTTP API and the MCP transport. Cards are organized into **decks**; each deck holds
**notes** (one of five types) that expand into reviewable **cards** scheduled with FSRS.

## Authentication

All `/v1/**` and `/mcp` endpoints require an OAuth2 bearer JWT. Scopes gate every operation:

- `study.read`, `study.write` — decks/notes/cards
- `review.write` — submit reviews
- `import.write`, `export.read` — import/export
- `documents.read`, `documents.write`, `rag.query` — document corpus + RAG
- `ai.generate` — AI flashcard generation/improvement
- `mcp.invoke` — MCP tool invocation

Send `Authorization: Bearer <jwt>`. A request missing the required scope returns `403`.

## Core REST endpoints

- `GET /v1/auth/me` — current principal.
- `POST /v1/decks`, `GET /v1/decks`, `GET/PATCH/DELETE /v1/decks/{id}` — decks
  (`{title, description?, tags?, defaultDesiredRetention?}`).
- `POST /v1/notes`, `GET /v1/notes/{id}` — notes. `noteType` ∈ `basic | reversed | cloze |
  multiple-choice | free-text`; content shape depends on the type.
- `GET /v1/cards/due?deckId=` — next due cards. `POST /v1/reviews` — submit a rating
  (`again | hard | good | easy`); the scheduler returns the next interval.
- `POST /v1/imports/flashcards` (FlashcardImportV1) and `GET /v1/exports/decks/{id}.json` —
  round-trippable import/export.
- Documents/RAG: `POST /v1/documents`, `POST /v1/documents/{id}/ingest`,
  `POST /v1/rag/search`, `POST /v1/rag/chat`.
- AI: `POST /v1/ai/generate-flashcards`, `POST /v1/ai/improve-flashcard`.

## AI generation contract (important)

`POST /v1/ai/generate-flashcards` takes a **nested** body:

```json
{"source": {"type": "text", "content": "..."}, "preferredTypes": ["basic"], "maxItems": 10,
 "language": "es", "difficulty": "medium"}
```

It returns `{"generated": [{"noteType": "...", "content": {...}, "tags": [...]}], "warnings": []}`.
Generated cards are **proposals** — they are validated server-side against the FlashcardImportV1
schema and never auto-persisted. Route them through `POST /v1/imports/flashcards` to save.

If no chat provider is configured the AI endpoints return `503 application/problem+json`
("AI Chat Provider Not Configured") — degrade gracefully, do not retry blindly.

## MCP transport

`POST /mcp` speaks JSON-RPC 2.0 (`initialize`, `tools/list`, `tools/call`, `resources/list`).
Tools are also invocable over REST: `GET /v1/mcp/tools` and
`POST /v1/mcp/tools/{toolName}:invoke` with `{"arguments": {...}}`. Tool invocation requires
`mcp.invoke` plus the scope of the underlying operation (e.g. `deck_create` needs `study.write`).

## Errors

Errors use RFC 9457 `application/problem+json` with `type`, `title`, `status`, `detail`. Unknown
request fields are rejected with `400` (strict schemas). Validation failures of AI output return
`422` with a `violations` array.
