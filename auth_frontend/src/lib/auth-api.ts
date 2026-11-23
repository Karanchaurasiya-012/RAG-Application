import { apiRequest, clearCsrfCache } from "@/lib/api";
import { clearAccessToken, setAccessToken } from "@/lib/access-token";
import type { AccessTokenPayload, AuthPayload, User } from "@/lib/types";

export async function register(input: {
  fullName: string;
  email: string;
  password: string;
}) {
  return apiRequest<null>("/api/v1/auth/register", {
    method: "POST",
    body: input,
  });
}

export async function login(input: { email: string; password: string }) {
  const response = await apiRequest<AuthPayload>("/api/v1/auth/login", {
    method: "POST",
    body: input,
  });
  setAccessToken(response.data?.accessToken);
  return response;
}

export async function fetchAccessToken(): Promise<string | null> {
  const response = await apiRequest<AccessTokenPayload>("/api/v1/auth/access-token", {
    method: "GET",
    csrf: false,
  });
  const token = response.data?.accessToken ?? null;
  setAccessToken(token);
  return token;
}

export async function verifyEmail(token: string) {
  return apiRequest<null>("/api/v1/auth/verify-email", {
    method: "POST",
    body: { token },
  });
}

export async function resendVerification(email: string) {
  return apiRequest<null>("/api/v1/auth/resend-verification", {
    method: "POST",
    body: { email },
  });
}

export async function forgotPassword(email: string) {
  return apiRequest<null>("/api/v1/auth/forgot-password", {
    method: "POST",
    body: { email },
  });
}

export async function resetPassword(input: { token: string; newPassword: string }) {
  return apiRequest<null>("/api/v1/auth/reset-password", {
    method: "POST",
    body: input,
  });
}

export async function logout() {
  try {
    return await apiRequest<null>("/api/v1/auth/logout", {
      method: "POST",
      retryOnUnauthorized: false,
    });
  } finally {
    clearCsrfCache();
    clearAccessToken();
  }
}

export async function fetchMe(): Promise<User> {
  const response = await apiRequest<User>("/api/v1/auth/me", {
    method: "GET",
    csrf: false,
  });
  if (!response.data) {
    throw new Error("User payload missing.");
  }
  return response.data;
}
