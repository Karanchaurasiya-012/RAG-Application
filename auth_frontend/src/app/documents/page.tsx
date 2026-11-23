"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Alert } from "@/components/Alert";
import { DocumentList } from "@/components/documents/DocumentList";
import { useAuth } from "@/context/AuthContext";
import { ApiError } from "@/lib/api";
import { listDocuments } from "@/lib/document.service";
import type { DocumentListItem } from "@/lib/document.types";

export default function DocumentsPage() {
  const { status } = useAuth();
  const router = useRouter();
  const [items, setItems] = useState<DocumentListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (status === "anonymous") {
      router.replace("/login");
    }
  }, [status, router]);

  useEffect(() => {
    if (status !== "authenticated") {
      return;
    }
    let cancelled = false;
    (async () => {
      setLoading(true);
      setError(null);
      try {
        const data = await listDocuments(0, 50);
        if (!cancelled) {
          setItems(data.items);
          setTotal(data.totalElements);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof ApiError
              ? err.message
              : "Unable to load documents."
          );
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [status]);

  if (status === "loading") {
    return (
      <div className="docs-page">
        <div className="docs-shell">
          <h1>Loading…</h1>
        </div>
      </div>
    );
  }

  return (
    <div className="docs-page">
      <div className="docs-shell">
        <header className="docs-header">
          <div>
            <Link href="/" className="brand-mark">
              Auth<span>Backend</span>
            </Link>
            <h1>Documents</h1>
            <p className="docs-muted">{total} document{total === 1 ? "" : "s"}</p>
          </div>
          <div className="row-actions" style={{ justifyContent: "flex-end" }}>
            <Link href="/chat" className="btn btn-secondary" style={{ width: "auto" }}>
              RAG Chat
            </Link>
            <Link href="/dashboard" className="btn btn-secondary" style={{ width: "auto" }}>
              Dashboard
            </Link>
            <Link href="/documents/upload" className="btn btn-primary" style={{ width: "auto" }}>
              Upload PDF
            </Link>
          </div>
        </header>

        {error && <Alert tone="error">{error}</Alert>}
        {loading ? (
          <p className="docs-muted">Loading documents…</p>
        ) : (
          <DocumentList items={items} />
        )}
      </div>
    </div>
  );
}
