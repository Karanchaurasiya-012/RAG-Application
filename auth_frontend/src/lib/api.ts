import { API_BASE_URL } from "@/lib/config";
import type { ApiResponse, CsrfPayload } from "@/lib/types";

export class ApiError extends Error {
  status: number;
  body: ApiResponse<unknown> | null;

  constructor(status: number, message: string, body: ApiResponse<unknown> | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  csrf?: boolean;
  retryOnUnauthorized?: boolean;
};

type ResolvedCsrf = {
  headerName: string;
  token: string;
};

/** In-memory CSRF from GET /csrf JSON (works cross-origin; document.cookie cannot). */
let csrfCache: ResolvedCsrf | null = null;

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

/**
 * Cross-origin SPA (localhost / Vercel → Render Auth): read token from JSON body.
 * Auth uses XOR CSRF handler so this value matches when sent as X-XSRF-TOKEN with credentials.
 */
export async function fetchCsrf(force = false): Promise<ResolvedCsrf> {
  if (!force && csrfCache) {
    return csrfCache;
  }

  const response = await fetch(`${API_BASE_URL}/api/v1/auth/csrf`, {
    method: "GET",
    credentials: "include",
    headers: { Accept: "application/json" },
  });
  const payload = await parseJson<CsrfPayload>(response);
  if (!response.ok || !payload?.success || !payload.data?.headerName || !payload.data.token) {
    throw new ApiError(
      response.status,
      payload?.message || "Unable to obtain CSRF token.",
      payload
    );
  }

  csrfCache = {
    headerName: payload.data.headerName,
    token: payload.data.token,
  };
  return csrfCache;
}

export function clearCsrfCache() {
  csrfCache = null;
}

async function refreshSession(): Promise<boolean> {
  try {
    await apiRequest<null>("/api/v1/auth/refresh", {
      method: "POST",
      csrf: true,
      retryOnUnauthorized: false,
    });
    return true;
  } catch {
    return false;
  }
}

export async function apiRequest<T>(
  path: string,
  options: RequestOptions = {}
): Promise<ApiResponse<T>> {
  const {
    method = "GET",
    body,
    csrf = method !== "GET" && method !== "HEAD",
    retryOnUnauthorized = true,
  } = options;

  const headers: Record<string, string> = {
    Accept: "application/json",
  };

  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  if (csrf) {
    const token = await fetchCsrf();
    headers[token.headerName] = token.token;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method,
    credentials: "include",
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  if (response.status === 403 && csrf) {
    clearCsrfCache();
    const retryToken = await fetchCsrf(true);
    headers[retryToken.headerName] = retryToken.token;
    const retryResponse = await fetch(`${API_BASE_URL}${path}`, {
      method,
      credentials: "include",
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const retryPayload = await parseJson<T>(retryResponse);
    if (!retryResponse.ok || !retryPayload?.success) {
      throw new ApiError(
        retryResponse.status,
        retryPayload?.message || "Request failed.",
        retryPayload
      );
    }
    return retryPayload;
  }

  if (response.status === 401 && retryOnUnauthorized && path !== "/api/v1/auth/refresh") {
    const refreshed = await refreshSession();
    if (refreshed) {
      return apiRequest<T>(path, { ...options, retryOnUnauthorized: false });
    }
  }

  const payload = await parseJson<T>(response);
  if (!response.ok || !payload?.success) {
    throw new ApiError(
      response.status,
      payload?.message || "Request failed.",
      payload
    );
  }
  return payload;
}
