import type { DocumentListItem } from "@/lib/document.types";

type Props = {
  documents: DocumentListItem[];
  selectedIds: string[];
  onChange: (ids: string[]) => void;
  loading?: boolean;
};

export function DocumentPicker({ documents, selectedIds, onChange, loading }: Props) {
  const eligible = documents.filter((doc) => doc.status === "COMPLETED");

  function toggle(id: string) {
    if (selectedIds.includes(id)) {
      onChange(selectedIds.filter((value) => value !== id));
      return;
    }
    onChange([...selectedIds, id]);
  }

  return (
    <div className="chat-doc-picker">
      <div className="chat-doc-picker-head">
        <h3>Documents</h3>
        <p className="docs-muted">
          Optional. Empty = search all eligible docs you own.
        </p>
      </div>
      {loading ? (
        <p className="docs-muted">Loading documents…</p>
      ) : eligible.length === 0 ? (
        <p className="docs-muted">No completed documents yet.</p>
      ) : (
        <ul className="chat-doc-list">
          {eligible.map((doc) => {
            const checked = selectedIds.includes(doc.documentId);
            return (
              <li key={doc.documentId}>
                <label>
                  <input
                    type="checkbox"
                    checked={checked}
                    onChange={() => toggle(doc.documentId)}
                  />
                  <span>
                    <strong>{doc.originalFilename}</strong>
                    <span className="docs-muted">
                      {doc.chunkCount ?? 0} chunks
                      {doc.embeddingProvider ? ` · ${doc.embeddingProvider}` : ""}
                    </span>
                  </span>
                </label>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
