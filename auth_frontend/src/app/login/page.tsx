"use client";

import Link from "next/link";
import { FormEvent, Suspense, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Alert } from "@/components/Alert";
import { AuthShell } from "@/components/AuthShell";
import { FormField } from "@/components/FormField";
import { SubmitButton } from "@/components/SubmitButton";
import { useAuth } from "@/context/AuthContext";
import { ApiError } from "@/lib/api";
import { login, resendVerification } from "@/lib/auth-api";
import { GOOGLE_AUTH_URL } from "@/lib/config";
import { oauthErrorMessage } from "@/lib/oauth-errors";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setUser, status } = useAuth();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [resending, setResending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [needsVerification, setNeedsVerification] = useState(false);

  const oauthError = useMemo(
    () => oauthErrorMessage(searchParams.get("error")),
    [searchParams]
  );

  useEffect(() => {
    if (status === "authenticated") {
      router.replace("/dashboard");
    }
  }, [status, router]);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    setInfo(null);
    setNeedsVerification(false);
    try {
      const response = await login({ email, password });
      if (response.data?.user) {
        setUser(response.data.user);
      }
      router.replace("/dashboard");
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Unable to sign in.";
      setError(message);
      setNeedsVerification(message.toLowerCase().includes("email verification required"));
    } finally {
      setLoading(false);
    }
  }

  async function onResendVerification() {
    if (!email.trim()) {
      setError("Enter your email first, then resend verification.");
      return;
    }
    setResending(true);
    setError(null);
    setInfo(null);
    try {
      const response = await resendVerification(email.trim());
      setInfo(response.message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Unable to resend verification email.");
    } finally {
      setResending(false);
    }
  }

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Sign in with email or continue with Google."
      footer={
        <>
          New here? <Link href="/register">Create an account</Link>
        </>
      }
    >
      {(error || oauthError) && <Alert tone="error">{error || oauthError || ""}</Alert>}
      {info && <Alert tone="success">{info}</Alert>}

      {needsVerification && (
        <button
          type="button"
          className="btn btn-secondary"
          onClick={onResendVerification}
          disabled={resending}
        >
          {resending ? "Sending…" : "Resend verification email"}
        </button>
      )}

      <form className="stack" onSubmit={onSubmit}>
        <FormField
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <FormField
          label="Password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          minLength={8}
          maxLength={72}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <div style={{ textAlign: "right", marginTop: "-0.35rem" }}>
          <Link href="/forgot-password">Forgot password?</Link>
        </div>
        <SubmitButton loading={loading}>Sign in</SubmitButton>
      </form>

      <div className="divider">or</div>

      <a className="btn btn-secondary" href={GOOGLE_AUTH_URL}>
        Continue with Google
      </a>
    </AuthShell>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <AuthShell title="Welcome back" subtitle="Loading sign-in…">
          <Alert tone="info">Preparing the form…</Alert>
        </AuthShell>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
