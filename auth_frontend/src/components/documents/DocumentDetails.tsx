"use client";

import type { DocumentDetails as DocumentDetailsType } from "@/lib/document.types";

type DocumentDetailsProps = {
  document: DocumentDetailsType;
};

export function DocumentDetailsView({ document }: DocumentDetailsProps) {
  return (
    <div className="docs-panel">
      <h2>Details</h2>
      <dl className="docs-meta-grid">
        <div>
          <dt>Filename</dt>
          <dd>{document.originalFilename}</dd>
        </div>
        <div>
          <dt>Status</dt>
          <dd>
            <span className="badge">{document.status}</span>
          </dd>
        </div>
        <div>
          <dt>Size</dt>
          <dd>{Math.round(document.fileSize / 1024)} KB</dd>
        </div>
        <div>
          <dt>Pages</dt>
          <dd>{document.pageCount ?? "—"}</dd>
        </div>
        <div>
          <dt>Chunks</dt>
          <dd>{document.chunkCount ?? "—"}</dd>
        </div>
        <div>
          <dt>Embedding</dt>
          <dd>
            {document.embeddingProvider || "—"}
            {document.embeddingModel ? ` / ${document.embeddingModel}` : ""}
            {document.embeddingDimensions != null
              ? ` (${document.embeddingDimensions}d)`
              : ""}
          </dd>
        </div>
        <div>
          <dt>PII decision</dt>
          <dd>{document.piiDecision || "—"}</dd>
        </div>
        <div>
          <dt>Masking applied</dt>
          <dd>{document.maskingApplied ? "Yes (extracted text only)" : "No"}</dd>
        </div>
        <div>
          <dt>Checksum</dt>
          <dd className="mono">{document.checksumSha256}</dd>
        </div>
      </dl>

      <AlertNote />

      {document.piiSummary?.length > 0 && (
        <>
          <h3>PII categories</h3>
          <ul className="pii-list">
            {document.piiSummary.map((item) => (
              <li key={item.type}>
                <strong>{item.type}</strong> × {item.count}
                {item.pages?.length ? ` (pages ${item.pages.join(", ")})` : ""}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

function AlertNote() {
  return (
    <p className="docs-note">
      Original PDF remains unchanged. Masking applies only to extracted text used
      for chunking and embeddings.
    </p>
  );
}
