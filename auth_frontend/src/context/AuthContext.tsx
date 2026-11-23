"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { fetchAccessToken, fetchMe, logout as logoutRequest } from "@/lib/auth-api";
import { clearAccessToken } from "@/lib/access-token";
import type { User } from "@/lib/types";

type AuthStatus = "loading" | "authenticated" | "anonymous";

type AuthContextValue = {
  user: User | null;
  status: AuthStatus;
  refreshUser: () => Promise<User | null>;
  setUser: (user: User | null) => void;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUserState] = useState<User | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");

  const refreshUser = useCallback(async () => {
    try {
      const me = await fetchMe();
      // Auth cookie works on Auth host; bridge JWT for processing/output on other hosts.
      await fetchAccessToken();
      setUserState(me);
      setStatus("authenticated");
      return me;
    } catch {
      clearAccessToken();
      setUserState(null);
      setStatus("anonymous");
      return null;
    }
  }, []);

  useEffect(() => {
    void refreshUser();
  }, [refreshUser]);

  const setUser = useCallback((next: User | null) => {
    setUserState(next);
    setStatus(next ? "authenticated" : "anonymous");
  }, []);

  const logout = useCallback(async () => {
    try {
      await logoutRequest();
    } finally {
      clearAccessToken();
      setUser(null);
    }
  }, [setUser]);

  const value = useMemo(
    () => ({
      user,
      status,
      refreshUser,
      setUser,
      logout,
    }),
    [user, status, refreshUser, setUser, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return ctx;
}
