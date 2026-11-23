"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Alert } from "@/components/Alert";
import { CitationList } from "@/components/chat/CitationList";
import { ConversationSidebar } from "@/components/chat/ConversationSidebar";
import { DocumentPicker } from "@/components/chat/DocumentPicker";
import { useAuth } from "@/context/AuthContext";
import { ApiError } from "@/lib/api";
import { listDocuments } from "@/lib/document.service";
import type { DocumentListItem } from "@/lib/document.types";
import {
  chatQuery,
  createConversation,
  deleteConversation,
  getConversationMessages,
  listConversations,
} from "@/lib/rag.service";
import type {
  ConversationMessageView,
  ConversationSummary,
  LlmProviderOption,
  RagCitation,
} from "@/lib/rag.types";

type UiMessage = {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  citations?: RagCitation[];
  noContext?: boolean;
  llmProvider?: string | null;
  llmModel?: string | null;
};

export default function ChatPage() {
  const { status } = useAuth();
  const router = useRouter();

  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<string | null>(null);
  const [messages, setMessages] = useState<UiMessage[]>([]);
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [selectedDocumentIds, setSelectedDocumentIds] = useState<string[]>([]);
  const [llmProvider, setLlmProvider] = useState<LlmProviderOption>("ollama");
  const [question, setQuestion] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loadingDocs, setLoadingDocs] = useState(true);
  const [loadingConvos, setLoadingConvos] = useState(true);
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (status === "anonymous") {
      router.replace("/login");
    }
  }, [status, router]);

  const refreshConversations = useCallback(async () => {
    const data = await listConversations(0, 50);
    setConversations(data.items);
    return data.items;
  }, []);

  useEffect(() => {
    if (status !== "authenticated") {
      return;
    }
    let cancelled = false;
    (async () => {
      setLoadingDocs(true);
      setLoadingConvos(true);
      setError(null);
      try {
        const [docs, convos] = await Promise.all([
          listDocuments(0, 100),
          listConversations(0, 50),
        ]);
        if (!cancelled) {
          setDocuments(docs.items);
          setConversations(convos.items);
        }
      } catch (err) {
        if (!cancelled) {
          setError(err instanceof ApiError ? err.message : "Unable to load chat workspace.");
        }
      } finally {
        if (!cancelled) {
          setLoadingDocs(false);
          setLoadingConvos(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [status]);

  async function loadConversation(conversationId: string) {
    setError(null);
    setSending(true);
    try {
      const data = await getConversationMessages(conversationId);
      setActiveConversationId(conversationId);
      setMessages(
        data.messages
          .filter((m) => m.role === "USER" || m.role === "ASSISTANT")
          .map((m: ConversationMessageView) => ({
            id: m.id,
            role: m.role === "ASSISTANT" ? "ASSISTANT" : "USER",
            content: m.content,
            llmProvider: m.llmProvider,
            llmModel: m.llmModel,
          }))
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to load conversation.");
    } finally {
      setSending(false);
    }
  }

  async function onCreateConversation() {
    setError(null);
    setSending(true);
    try {
      const created = await createConversation();
      await refreshConversations();
      setActiveConversationId(created.conversationId);
      setMessages([]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to create conversation.");
    } finally {
      setSending(false);
    }
  }

  async function onDeleteConversation(conversationId: string) {
    setError(null);
    setSending(true);
    try {
      await deleteConversation(conversationId);
      const items = await refreshConversations();
      if (activeConversationId === conversationId) {
        setActiveConversationId(null);
        setMessages([]);
        if (items[0]) {
          await loadConversation(items[0].conversationId);
        }
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to delete conversation.");
    } finally {
      setSending(false);
    }
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = question.trim();
    if (!trimmed || sending) {
      return;
    }

    setError(null);
    setSending(true);
    const optimisticId = `local-user-${Date.now()}`;
    setMessages((prev) => [
      ...prev,
      { id: optimisticId, role: "USER", content: trimmed },
    ]);
    setQuestion("");

    try {
      const result = await chatQuery({
        question: trimmed,
        documentIds: selectedDocumentIds,
        conversationId: activeConversationId,
        llmProvider,
      });

      setActiveConversationId(result.conversationId);
      setMessages((prev) => [
        ...prev,
        {
          id: `local-assistant-${Date.now()}`,
          role: "ASSISTANT",
          content: result.answer,
          citations: result.citations,
          noContext: result.noContext,
          llmProvider: result.llmProvider,
          llmModel: result.llmModel,
        },
      ]);
      await refreshConversations();
    } catch (err) {
      setMessages((prev) => prev.filter((m) => m.id !== optimisticId));
      setQuestion(trimmed);
      setError(err instanceof ApiError ? err.message : "Chat request failed.");
    } finally {
      setSending(false);
    }
  }

  const title = useMemo(() => {
    const match = conversations.find((c) => c.conversationId === activeConversationId);
    return match?.title || "New chat";
  }, [conversations, activeConversationId]);

  if (status === "loading") {
    return (
      <div className="docs-page">
        <div className="docs-shell">
          <h1>Loading…</h1>
        </div>
      </div>
    );
  }

  return (
    <div className="docs-page chat-page">
      <div className="chat-shell">
        <header className="docs-header">
          <div>
            <Link href="/" className="brand-mark">
              Auth<span>Backend</span>
            </Link>
            <h1>RAG Chat</h1>
            <p className="docs-muted">{title}</p>
          </div>
          <div className="row-actions">
            <Link href="/documents" className="btn btn-secondary" style={{ width: "auto" }}>
              Documents
            </Link>
            <Link href="/dashboard" className="btn btn-ghost" style={{ width: "auto" }}>
              Dashboard
            </Link>
          </div>
        </header>

        {error && <Alert tone="error">{error}</Alert>}

        <div className="chat-layout">
          <ConversationSidebar
            items={conversations}
            activeId={activeConversationId}
            onSelect={(id) => void loadConversation(id)}
            onCreate={() => void onCreateConversation()}
            onDelete={(id) => void onDeleteConversation(id)}
            busy={sending || loadingConvos}
          />

          <section className="chat-main docs-panel">
            <div className="chat-toolbar">
              <label className="field chat-provider-field">
                LLM provider
                <select
                  value={llmProvider}
                  onChange={(e) => setLlmProvider(e.target.value as LlmProviderOption)}
                  disabled={sending}
                >
                  <option value="ollama">Ollama (Qwen)</option>
                  <option value="gemini">Gemini</option>
                </select>
              </label>
            </div>

            <div className="chat-messages" aria-live="polite">
              {messages.length === 0 ? (
                <p className="docs-muted">
                  Ask a question about your completed documents. Answers stay grounded in
                  retrieved context with source citations.
                </p>
              ) : (
                messages.map((message) => (
                  <article
                    key={message.id}
                    className={`chat-bubble chat-bubble-${message.role.toLowerCase()}`}
                  >
                    <header>
                      <strong>{message.role === "USER" ? "You" : "Assistant"}</strong>
                      {message.llmProvider && (
                        <span className="docs-muted">
                          {message.llmProvider}
                          {message.llmModel ? ` · ${message.llmModel}` : ""}
                        </span>
                      )}
                    </header>
                    <p>{message.content}</p>
                    {message.noContext && (
                      <p className="docs-muted">No document context was found for this turn.</p>
                    )}
                    {message.citations && <CitationList citations={message.citations} />}
                  </article>
                ))
              )}
            </div>

            <form className="chat-composer" onSubmit={onSubmit}>
              <label className="field">
                Question
                <textarea
                  value={question}
                  onChange={(e) => setQuestion(e.target.value)}
                  rows={3}
                  maxLength={8000}
                  placeholder="What is the notice period in my agreement?"
                  disabled={sending}
                  required
                />
              </label>
              <button type="submit" className="btn btn-primary" disabled={sending || !question.trim()}>
                {sending ? "Thinking…" : "Ask"}
              </button>
            </form>
          </section>

          <aside className="docs-panel chat-side-panel">
            <DocumentPicker
              documents={documents}
              selectedIds={selectedDocumentIds}
              onChange={setSelectedDocumentIds}
              loading={loadingDocs}
            />
          </aside>
        </div>
      </div>
    </div>
  );
}
