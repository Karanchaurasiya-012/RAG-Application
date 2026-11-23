"use client";

import { useState } from "react";
import { Alert } from "@/components/Alert";
import { ApiError } from "@/lib/api";
import { uploadDocument } from "@/lib/document.service";
import type { DocumentUploadResult } from "@/lib/document.types";

type PdfUploadProps = {
  onUploaded: (result: DocumentUploadResult) => void;
};

export function PdfUpload({ onUploaded }: PdfUploadProps) {
  const [file, setFile] = useState<File | null>(null);
  const [provider, setProvider] = useState<"ollama" | "gemini">("ollama");
  const [uploadRatio, setUploadRatio] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (!file) {
      setError("Choose a PDF file first.");
      return;
    }
    if (file.type && file.type !== "application/pdf") {
      setError("Only PDF files are accepted.");
      return;
    }

    setBusy(true);
    setError(null);
    setUploadRatio(0);
    try {
      const result = await uploadDocument(file, provider, (ratio) => {
        setUploadRatio(ratio);
      });
      setUploadRatio(1);
      onUploaded(result);
    } catch (err) {
      const message =
        err instanceof ApiError ? err.message : "Upload failed. Try again.";
      setError(message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="stack docs-panel" onSubmit={onSubmit}>
      <div>
        <h2>Upload PDF</h2>
        <p className="docs-muted">
          Processing runs asynchronously after validation. Masking never changes
          the original PDF bytes.
        </p>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      <label className="field">
        <span>PDF file</span>
        <input
          type="file"
          accept="application/pdf,.pdf"
          disabled={busy}
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
      </label>

      <label className="field">
        <span>Embedding provider</span>
        <select
          value={provider}
          disabled={busy}
          onChange={(e) => setProvider(e.target.value as "ollama" | "gemini")}
        >
          <option value="ollama">Ollama (BGE-M3)</option>
          <option value="gemini">Gemini</option>
        </select>
      </label>

      {uploadRatio !== null && (
        <div className="upload-progress" aria-live="polite">
          <div className="upload-progress-bar">
            <span style={{ width: `${Math.round(uploadRatio * 100)}%` }} />
          </div>
          <p className="docs-muted">
            Upload transfer {Math.round(uploadRatio * 100)}%
            {uploadRatio >= 1 ? " — server accepted file" : ""}
          </p>
        </div>
      )}

      <button type="submit" className="btn btn-primary" disabled={busy || !file}>
        {busy ? "Uploading…" : "Upload document"}
      </button>
    </form>
  );
}
