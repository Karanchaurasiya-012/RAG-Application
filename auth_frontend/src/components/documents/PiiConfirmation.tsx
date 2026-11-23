"use client";

import { useState } from "react";
import { Alert } from "@/components/Alert";
import { ApiError } from "@/lib/api";
import { submitPiiDecision } from "@/lib/document.service";
import type { DocumentDetails, PiiDecision } from "@/lib/document.types";

type PiiConfirmationProps = {
  document: DocumentDetails;
  onDecided: () => void;
};

export function PiiConfirmation({ document, onDecided }: PiiConfirmationProps) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (document.status !== "AWAITING_PII_DECISION") {
    return null;
  }

  async function choose(decision: PiiDecision) {
    setBusy(true);
    setError(null);
    try {
      await submitPiiDecision(document.documentId, decision);
      onDecided();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Decision failed.");
      setBusy(false);
    }
  }

  return (
    <div className="docs-panel docs-panel-accent">
      <h2>Confirm PII handling</h2>
      <p className="docs-muted">
        Potential PII was found in extracted text. Choose how embeddings should
        treat that text. The original PDF is never modified or overwritten.
      </p>

      {document.piiSummary?.length > 0 && (
        <ul className="pii-list">
          {document.piiSummary.map((item) => (
            <li key={item.type}>
              <strong>{item.type}</strong> × {item.count}
              {item.pages?.length ? ` (pages ${item.pages.join(", ")})` : ""}
            </li>
          ))}
        </ul>
      )}

      {error && <Alert tone="error">{error}</Alert>}

      <div className="row-actions" style={{ justifyContent: "flex-start" }}>
        <button
          type="button"
          className="btn btn-primary"
          style={{ width: "auto", minWidth: "10rem" }}
          disabled={busy}
          onClick={() => void choose("MASK")}
        >
          MASK text
        </button>
        <button
          type="button"
          className="btn btn-secondary"
          style={{ width: "auto", minWidth: "10rem" }}
          disabled={busy}
          onClick={() => void choose("KEEP_ORIGINAL")}
        >
          KEEP_ORIGINAL
        </button>
      </div>
    </div>
  );
}
