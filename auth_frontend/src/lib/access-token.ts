const STORAGE_KEY = "rag_access_token";

export function getAccessToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }
  const value = sessionStorage.getItem(STORAGE_KEY);
  return value && value.length > 0 ? value : null;
}

export function setAccessToken(token: string | null | undefined) {
  if (typeof window === "undefined") {
    return;
  }
  if (!token) {
    sessionStorage.removeItem(STORAGE_KEY);
    return;
  }
  sessionStorage.setItem(STORAGE_KEY, token);
}

export function clearAccessToken() {
  setAccessToken(null);
}

export function authorizationHeaders(): HeadersInit {
  const token = getAccessToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}
