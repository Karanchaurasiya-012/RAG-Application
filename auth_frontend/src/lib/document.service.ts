import { PROCESSING_API_BASE_URL } from "@/lib/config";
import { ApiError } from "@/lib/api";
import { authorizationHeaders } from "@/lib/access-token";
import type { ApiResponse } from "@/lib/types";
import type {
  DocumentDetails,
  DocumentDownloadResult,
  DocumentListResult,
  DocumentStatusResult,
  DocumentUploadResult,
  PiiDecision,
  PiiDecisionResult,
} from "@/lib/document.types";

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

async function processingRequest<T>(
  path: string,
  init: RequestInit = {}
): Promise<ApiResponse<T>> {
  const headers = new Headers(init.headers || {});
  headers.set("Accept", "application/json");
  const auth = authorizationHeaders();
  Object.entries(auth).forEach(([key, value]) => headers.set(key, value));

  const response = await fetch(`${PROCESSING_API_BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers,
  });

  const payload = await parseJson<T>(response);
  if (!response.ok || !payload?.success) {
    throw new ApiError(
      response.status,
      payload?.message || "Processing API request failed.",
      payload
    );
  }
  return payload;
}

export async function listDocuments(page = 0, size = 20) {
  const response = await processingRequest<DocumentListResult>(
    `/api/v1/documents?page=${page}&size=${size}`
  );
  if (!response.data) {
    throw new ApiError(500, "Document list payload missing.");
  }
  return response.data;
}

export async function getDocument(documentId: string) {
  const response = await processingRequest<DocumentDetails>(
    `/api/v1/documents/${documentId}`
  );
  if (!response.data) {
    throw new ApiError(500, "Document details payload missing.");
  }
  return response.data;
}

export async function getDocumentStatus(documentId: string) {
  const response = await processingRequest<DocumentStatusResult>(
    `/api/v1/documents/${documentId}/status`
  );
  if (!response.data) {
    throw new ApiError(500, "Document status payload missing.");
  }
  return response.data;
}

export async function uploadDocument(
  file: File,
  embeddingProvider: "ollama" | "gemini" = "ollama",
  onProgress?: (ratio: number) => void
): Promise<DocumentUploadResult> {
  const form = new FormData();
  form.append("file", file);

  // XHR gives upload progress; fetch does not.
  const data = await new Promise<DocumentUploadResult>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(
      "POST",
      `${PROCESSING_API_BASE_URL}/api/v1/documents?embeddingProvider=${encodeURIComponent(embeddingProvider)}`
    );
    xhr.withCredentials = true;
    xhr.responseType = "text";
    const auth = authorizationHeaders() as Record<string, string>;
    Object.entries(auth).forEach(([key, value]) => {
      xhr.setRequestHeader(key, value);
    });

    xhr.upload.onprogress = (event) => {
      if (!onProgress || !event.lengthComputable || event.total <= 0) {
        return;
      }
      onProgress(Math.min(1, event.loaded / event.total));
    };

    xhr.onload = () => {
      let payload: ApiResponse<DocumentUploadResult> | null = null;
      try {
        payload = xhr.responseText
          ? (JSON.parse(xhr.responseText) as ApiResponse<DocumentUploadResult>)
          : null;
      } catch {
        payload = null;
      }
      if (xhr.status < 200 || xhr.status >= 300 || !payload?.success || !payload.data) {
        reject(
          new ApiError(
            xhr.status,
            payload?.message || "Upload failed.",
            payload
          )
        );
        return;
      }
      resolve(payload.data);
    };

    xhr.onerror = () => reject(new ApiError(0, "Network error during upload."));
    xhr.send(form);
  });

  return data;
}

export async function submitPiiDecision(documentId: string, decision: PiiDecision) {
  const response = await processingRequest<PiiDecisionResult>(
    `/api/v1/documents/${documentId}/pii-decision`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ decision }),
    }
  );
  if (!response.data) {
    throw new ApiError(500, "PII decision payload missing.");
  }
  return response.data;
}

export async function createDownloadUrl(documentId: string) {
  const response = await processingRequest<DocumentDownloadResult>(
    `/api/v1/documents/${documentId}/download`
  );
  if (!response.data) {
    throw new ApiError(500, "Download payload missing.");
  }
  return response.data;
}

export async function deleteDocument(documentId: string) {
  await processingRequest<{ documentId: string; deleted: boolean }>(
    `/api/v1/documents/${documentId}`,
    { method: "DELETE" }
  );
}

/** Bounded status polling with cleanup via AbortSignal. Does not invent progress %. */
export function pollDocumentStatus(
  documentId: string,
  options: {
    intervalMs?: number;
    maxAttempts?: number;
    signal?: AbortSignal;
    onUpdate: (status: DocumentStatusResult) => void;
  }
): Promise<DocumentStatusResult> {
  const intervalMs = options.intervalMs ?? 2000;
  const maxAttempts = options.maxAttempts ?? 90;

  return new Promise((resolve, reject) => {
    let attempts = 0;
    let timer: ReturnType<typeof setTimeout> | null = null;
    let settled = false;

    const finish = (fn: () => void) => {
      if (settled) {
        return;
      }
      settled = true;
      if (timer) {
        clearTimeout(timer);
      }
      fn();
    };

    const tick = async () => {
      if (options.signal?.aborted) {
        finish(() => reject(new DOMException("Polling aborted", "AbortError")));
        return;
      }
      attempts += 1;
      try {
        const status = await getDocumentStatus(documentId);
        options.onUpdate(status);
        const terminal =
          status.status === "COMPLETED" ||
          status.status === "AWAITING_PII_DECISION" ||
          status.status.endsWith("_FAILED") ||
          status.status === "CANCELLED" ||
          status.status === "EXPIRED";
        if (terminal) {
          finish(() => resolve(status));
          return;
        }
        if (attempts >= maxAttempts) {
          finish(() =>
            reject(new Error("Status polling timed out. Refresh to check again."))
          );
          return;
        }
        timer = setTimeout(() => void tick(), intervalMs);
      } catch (error) {
        finish(() => reject(error));
      }
    };

    options.signal?.addEventListener("abort", () => {
      finish(() => reject(new DOMException("Polling aborted", "AbortError")));
    });

    void tick();
  });
}
