"use client";

import Link from "next/link";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Alert } from "@/components/Alert";
import { AuthShell } from "@/components/AuthShell";
import { ApiError } from "@/lib/api";
import { verifyEmail } from "@/lib/auth-api";

function VerifyEmailContent() {
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [status, setStatus] = useState<"idle" | "loading" | "success" | "error">(
    token ? "loading" : "error"
  );
  const [message, setMessage] = useState(
    token ? "Verifying your email…" : "Missing verification token."
  );

  useEffect(() => {
    if (!token) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const response = await verifyEmail(token);
        if (!cancelled) {
          setStatus("success");
          setMessage(response.message);
        }
      } catch (err) {
        if (!cancelled) {
          setStatus("error");
          setMessage(err instanceof ApiError ? err.message : "Verification failed.");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token]);

  return (
    <AuthShell
      title="Email verification"
      subtitle="Confirming your account email."
      footer={
        <>
          Ready to continue? <Link href="/login">Sign in</Link>
        </>
      }
    >
      <Alert tone={status === "success" ? "success" : status === "error" ? "error" : "info"}>
        {message}
      </Alert>
    </AuthShell>
  );
}

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <AuthShell title="Email verification" subtitle="Checking your link…">
          <Alert tone="info">Verifying…</Alert>
        </AuthShell>
      }
    >
      <VerifyEmailContent />
    </Suspense>
  );
}
