import { RAG_API_BASE_URL } from "@/lib/config";
import { ApiError } from "@/lib/api";
import { authorizationHeaders } from "@/lib/access-token";
import type { ApiResponse } from "@/lib/types";
import type {
  ChatQueryResult,
  ConversationListResult,
  ConversationMessagesResult,
  ConversationSummary,
  LlmProviderOption,
} from "@/lib/rag.types";

async function parseJson<T>(response: Response): Promise<ApiResponse<T> | null> {
  const text = await response.text();
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text) as ApiResponse<T>;
  } catch {
    return null;
  }
}

async function ragRequest<T>(path: string, init: RequestInit = {}): Promise<ApiResponse<T>> {
  const headers = new Headers(init.headers || {});
  headers.set("Accept", "application/json");
  if (init.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const auth = authorizationHeaders();
  Object.entries(auth).forEach(([key, value]) => headers.set(key, value));

  const response = await fetch(`${RAG_API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers,
  });

  const payload = await parseJson<T>(response);
  if (!response.ok || !payload?.success) {
    const detail =
      payload?.message ||
      (response.status === 401
        ? "Authentication required. Sign in again, then retry chat."
        : response.status === 0
          ? "Network error talking to RAG API."
          : `RAG API request failed (HTTP ${response.status}).`);
    throw new ApiError(response.status, detail, payload);
  }
  return payload;
}

export async function chatQuery(input: {
  question: string;
  documentIds?: string[];
  conversationId?: string | null;
  llmProvider?: LlmProviderOption;
}): Promise<ChatQueryResult> {
  const response = await ragRequest<ChatQueryResult>("/api/v1/chat/query", {
    method: "POST",
    body: JSON.stringify({
      question: input.question,
      documentIds: input.documentIds?.length ? input.documentIds : undefined,
      conversationId: input.conversationId || null,
      llmProvider: input.llmProvider || undefined,
    }),
  });
  if (!response.data) {
    throw new ApiError(500, "Chat response payload missing.");
  }
  return response.data;
}

export async function listConversations(page = 0, size = 20): Promise<ConversationListResult> {
  const response = await ragRequest<ConversationListResult>(
    `/api/v1/conversations?page=${page}&size=${size}`
  );
  if (!response.data) {
    throw new ApiError(500, "Conversation list payload missing.");
  }
  return response.data;
}

export async function createConversation(title?: string): Promise<ConversationSummary> {
  const response = await ragRequest<{
    conversationId: string;
    title: string | null;
    createdAt: string | null;
    updatedAt: string | null;
  }>("/api/v1/conversations", {
    method: "POST",
    body: JSON.stringify(title ? { title } : {}),
  });
  if (!response.data) {
    throw new ApiError(500, "Create conversation payload missing.");
  }
  return {
    conversationId: response.data.conversationId,
    title: response.data.title,
    createdAt: response.data.createdAt,
    updatedAt: response.data.updatedAt,
  };
}

export async function getConversationMessages(
  conversationId: string
): Promise<ConversationMessagesResult> {
  const response = await ragRequest<ConversationMessagesResult>(
    `/api/v1/conversations/${conversationId}/messages`
  );
  if (!response.data) {
    throw new ApiError(500, "Conversation messages payload missing.");
  }
  return response.data;
}

export async function deleteConversation(conversationId: string): Promise<void> {
  await ragRequest<{ conversationId: string }>(`/api/v1/conversations/${conversationId}`, {
    method: "DELETE",
  });
}
