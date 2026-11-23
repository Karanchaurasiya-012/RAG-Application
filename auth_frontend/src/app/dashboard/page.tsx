"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Alert } from "@/components/Alert";
import { useAuth } from "@/context/AuthContext";

export default function DashboardPage() {
  const { user, status, logout } = useAuth();
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    if (status === "anonymous") {
      router.replace("/login");
    }
  }, [status, router]);

  async function onLogout() {
    setLoggingOut(true);
    setError(null);
    try {
      await logout();
      router.replace("/login");
    } catch {
      setError("Logout failed. Try again.");
      setLoggingOut(false);
    }
  }

  if (status === "loading" || !user) {
    return (
      <div className="dashboard-page">
        <div className="dashboard-card">
          <h1>Loading session…</h1>
          <p className="dashboard-meta">Checking your signed-in state.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="dashboard-page">
      <div className="dashboard-card">
        <div className="auth-brand">
          <Link href="/" className="brand-mark">
            Auth<span>Backend</span>
          </Link>
        </div>
        <h1>Hello, {user.fullName}</h1>
        <p className="dashboard-meta">You are signed in with a secure cookie session.</p>

        {error && <Alert tone="error">{error}</Alert>}

        <dl className="user-panel">
          <div>
            <dt>Email</dt>
            <dd>{user.email}</dd>
          </div>
          <div>
            <dt>Email verified</dt>
            <dd>
              <span className="badge">{user.emailVerified ? "Verified" : "Pending"}</span>
            </dd>
          </div>
          <div>
            <dt>Roles</dt>
            <dd>{user.roles?.length ? user.roles.join(", ") : "USER"}</dd>
          </div>
        </dl>

        <div className="stack" style={{ marginTop: "1.25rem" }}>
          <Link href="/chat" className="btn btn-primary">
            RAG Chat
          </Link>
          <Link href="/documents" className="btn btn-secondary">
            Documents
          </Link>
          <button
            type="button"
            className="btn btn-secondary"
            onClick={onLogout}
            disabled={loggingOut}
          >
            {loggingOut ? "Signing out…" : "Sign out"}
          </button>
        </div>
      </div>
    </div>
  );
}
