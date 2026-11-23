# RAG Application

Monorepo snapshot of the RAG stack (fresh publish; individual service repos remain separate).

## Projects

| Folder | Role | Default port |
|---|---|---|
| `AuthBackend` | Authentication / JWT | `8080` |
| `auth_frontend` | Next.js UI (auth, documents, chat) | `3000` |
| `processing_backend` | PDF ingest, PII, chunking, embeddings | `8081` |
| `output_backend` | RAG query, retrieval, LLM answers | `8090` |

## Quick start (local)

1. Copy each service `.env.example` → `.env` and fill secrets locally (never commit `.env`).
2. Place JWT public key where each backend expects it (`keys/public.pem`).
3. Start Postgres, Redis, and Ollama as needed.
4. Run backends with `./mvnw spring-boot:run`, frontend with `npm run dev`.

Secrets and private keys are intentionally excluded from this repository.
