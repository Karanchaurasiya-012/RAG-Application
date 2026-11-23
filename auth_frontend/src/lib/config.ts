export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ||
  "http://localhost:8080";

export const PROCESSING_API_BASE_URL =
  process.env.NEXT_PUBLIC_PROCESSING_API_BASE_URL?.replace(/\/$/, "") ||
  "http://localhost:8081";

export const RAG_API_BASE_URL =
  process.env.NEXT_PUBLIC_RAG_API_BASE_URL?.replace(/\/$/, "") ||
  "http://localhost:8090";

export const GOOGLE_AUTH_URL = `${API_BASE_URL}/oauth2/authorization/google`;
