"use client";

import type { DocumentStatusResult } from "@/lib/document.types";

type DocumentStatusProps = {
  status: DocumentStatusResult | null;
};

export function DocumentStatusView({ status }: DocumentStatusProps) {
  if (!status) {
    return (
      <div className="docs-panel">
        <h2>Status</h2>
        <p className="docs-muted">Waiting for status updates…</p>
      </div>
    );
  }

  return (
    <div className="docs-panel">
      <h2>Status</h2>
      <dl className="docs-meta-grid">
        <div>
          <dt>Pipeline</dt>
          <dd>
            <span className="badge">{status.status}</span>
          </dd>
        </div>
        <div>
          <dt>Job</dt>
          <dd>
            {status.jobStatus || "—"}
            {status.jobAttempts != null ? ` (attempt ${status.jobAttempts})` : ""}
          </dd>
        </div>
        <div>
          <dt>PII</dt>
          <dd>{status.piiDetected ? "Detected" : "None reported"}</dd>
        </div>
        <div>
          <dt>Decision</dt>
          <dd>{status.piiDecision || "—"}</dd>
        </div>
        <div>
          <dt>Chunks</dt>
          <dd>{status.chunkCount ?? "—"}</dd>
        </div>
        <div>
          <dt>Download ready</dt>
          <dd>{status.downloadAvailable ? "Yes" : "Not yet"}</dd>
        </div>
      </dl>
      {status.lastErrorCode && (
        <p className="docs-muted">Last job error code: {status.lastErrorCode}</p>
      )}
    </div>
  );
}
