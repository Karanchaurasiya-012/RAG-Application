"use client";

import Link from "next/link";
import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { PdfUpload } from "@/components/documents/PdfUpload";
import { useAuth } from "@/context/AuthContext";
import type { DocumentUploadResult } from "@/lib/document.types";

export default function DocumentUploadPage() {
  const { status } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (status === "anonymous") {
      router.replace("/login");
    }
  }, [status, router]);

  function onUploaded(result: DocumentUploadResult) {
    router.push(`/documents/${result.documentId}`);
  }

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
      <div className="docs-shell docs-shell-narrow">
        <header className="docs-header">
          <div>
            <Link href="/documents" className="brand-mark">
              Auth<span>Backend</span>
            </Link>
            <h1>Upload</h1>
          </div>
          <Link href="/documents" className="btn btn-secondary" style={{ width: "auto" }}>
            Back to list
          </Link>
        </header>
        <PdfUpload onUploaded={onUploaded} />
      </div>
    </div>
  );
}
