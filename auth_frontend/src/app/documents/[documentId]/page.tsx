"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Alert } from "@/components/Alert";
import { DocumentDetailsView } from "@/components/documents/DocumentDetails";
import { DocumentStatusView } from "@/components/documents/DocumentStatus";
import { PiiConfirmation } from "@/components/documents/PiiConfirmation";
import { useAuth } from "@/context/AuthContext";
import { ApiError } from "@/lib/api";
import {
  createDownloadUrl,
  deleteDocument,
  getDocument,
  pollDocumentStatus,
} from "@/lib/document.service";
import type { DocumentDetails, DocumentStatusResult } from "@/lib/document.types";

export default function DocumentDetailPage() {
  const params = useParams<{ documentId: string }>();
  const documentId = params.documentId;
  const { status: authStatus } = useAuth();
  const router = useRouter();

  const [document, setDocument] = useState<DocumentDetails | null>(null);
  const [liveStatus, setLiveStatus] = useState<DocumentStatusResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (authStatus === "anonymous") {
      router.replace("/login");
    }
  }, [authStatus, router]);

  async function refreshDetails() {
    const details = await getDocument(documentId);
    setDocument(details);
    return details;
  }

  useEffect(() => {
    if (authStatus !== "authenticated" || !documentId) {
      return;
    }

    const controller = new AbortController();
    let cancelled = false;

    (async () => {
      setError(null);
      try {
        await refreshDetails();
        await pollDocumentStatus(documentId, {
          signal: controller.signal,
          onUpdate: (status) => {
            if (!cancelled) {
              setLiveStatus(status);
            }
          },
        });
        if (!cancelled) {
          await refreshDetails();
        }
      } catch (err) {
        if (cancelled || (err instanceof DOMException && err.name === "AbortError")) {
          return;
        }
        setError(
          err instanceof ApiError
            ? err.message
            : err instanceof Error
              ? err.message
              : "Unable to load document."
        );
      }
    })();

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [authStatus, documentId]);

  async function onDownload() {
    setBusy(true);
    setError(null);
    setInfo(null);
    try {
      const result = await createDownloadUrl(documentId);
      window.open(result.downloadUrl, "_blank", "noopener,noreferrer");
      setInfo(`Download URL expires in ${result.expiresInSeconds}s.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Download failed.");
    } finally {
      setBusy(false);
    }
  }

  async function onDelete() {
    if (!window.confirm("Delete this document and its vectors?")) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await deleteDocument(documentId);
      router.replace("/documents");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Delete failed.");
      setBusy(false);
    }
  }

  if (authStatus === "loading" || !document) {
    return (
      <div className="docs-page">
        <div className="docs-shell">
          <h1>{error ? "Document" : "Loading document…"}</h1>
          {error && <Alert tone="error">{error}</Alert>}
          <Link href="/documents">Back to list</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="docs-page">
      <div className="docs-shell">
        <header className="docs-header">
          <div>
            <Link href="/documents" className="brand-mark">
              Auth<span>Backend</span>
            </Link>
            <h1>{document.originalFilename}</h1>
            <p className="docs-muted mono">{document.documentId}</p>
          </div>
          <div className="row-actions" style={{ justifyContent: "flex-end" }}>
            <Link href="/documents" className="btn btn-secondary" style={{ width: "auto" }}>
              All documents
            </Link>
            {document.downloadAvailable && (
              <button
                type="button"
                className="btn btn-primary"
                style={{ width: "auto" }}
                disabled={busy}
                onClick={() => void onDownload()}
              >
                Download original
              </button>
            )}
            <button
              type="button"
              className="btn btn-secondary"
              style={{ width: "auto" }}
              disabled={busy}
              onClick={() => void onDelete()}
            >
              Delete
            </button>
          </div>
        </header>

        {error && <Alert tone="error">{error}</Alert>}
        {info && <Alert tone="info">{info}</Alert>}

        <div className="docs-grid">
          <DocumentStatusView status={liveStatus} />
          <DocumentDetailsView document={document} />
        </div>

        <PiiConfirmation
          document={document}
          onDecided={() => {
            void (async () => {
              await refreshDetails();
              const controller = new AbortController();
              try {
                await pollDocumentStatus(documentId, {
                  signal: controller.signal,
                  onUpdate: setLiveStatus,
                });
                await refreshDetails();
              } catch (err) {
                if (!(err instanceof DOMException && err.name === "AbortError")) {
                  setError(
                    err instanceof ApiError
                      ? err.message
                      : "Unable to refresh after decision."
                  );
                }
              }
            })();
          }}
        />
      </div>
    </div>
  );
}
