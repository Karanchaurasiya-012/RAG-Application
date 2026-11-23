export type LlmProviderOption = "ollama" | "gemini";

export type RagCitation = {
  sourceKey: string;
  chunkId: string;
  documentId: string;
  documentName: string | null;
  pageStart: number | null;
  pageEnd: number | null;
  similarity: number | null;
};

export type ChatQueryResult = {
  conversationId: string;
  answer: string;
  grounded: boolean;
  noContext: boolean;
  llmProvider: string | null;
  llmModel: string | null;
  promptVersion: string | null;
  answerCacheHit: boolean;
  embeddingCacheHit?: boolean;
  retrievalCacheHit?: boolean;
  rejectedSourceKeys: string[];
  citations: RagCitation[];
  retrieval?: {
    embeddingProvider: string;
    embeddingModel: string;
    candidatesConsidered: number;
    sourceCount: number;
  };
};

export type ConversationSummary = {
  conversationId: string;
  title: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type ConversationListResult = {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  items: ConversationSummary[];
};

export type ConversationMessageView = {
  id: string;
  conversationId: string;
  role: "USER" | "ASSISTANT" | "SYSTEM";
  content: string;
  llmProvider: string | null;
  llmModel: string | null;
  createdAt: string | null;
};

export type ConversationMessagesResult = {
  conversationId: string;
  messages: ConversationMessageView[];
};
