"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/context/AuthContext";

export default function HomePage() {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === "authenticated") {
      router.replace("/dashboard");
    }
  }, [status, router]);

  return (
    <div className="home-page">
      <div className="home-card">
        <div className="auth-brand">
          <span className="brand-mark">
            Auth<span>Backend</span>
          </span>
        </div>
        <h1>Sign in securely. Stay signed in safely.</h1>
        <p>
          Cookie-based sessions, CSRF protection, email verification, password
          reset, and Google sign-in — ready for production flows.
        </p>
        <div className="row-actions" style={{ marginTop: "1.5rem" }}>
          <Link className="btn btn-primary" href="/login" style={{ width: "auto", minWidth: 140 }}>
            Sign in
          </Link>
          <Link className="btn btn-secondary" href="/register" style={{ width: "auto", minWidth: 140 }}>
            Create account
          </Link>
        </div>
      </div>
    </div>
  );
}
