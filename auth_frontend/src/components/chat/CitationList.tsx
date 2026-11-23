import type { RagCitation } from "@/lib/rag.types";

type Props = {
  citations: RagCitation[];
};

export function CitationList({ citations }: Props) {
  if (!citations.length) {
    return null;
  }

  return (
    <div className="chat-citations">
      <h3>Sources</h3>
      <ul>
        {citations.map((citation) => {
          const pages =
            citation.pageStart == null
              ? null
              : citation.pageEnd != null && citation.pageEnd !== citation.pageStart
                ? `pp. ${citation.pageStart}–${citation.pageEnd}`
                : `p. ${citation.pageStart}`;
          const score =
            citation.similarity == null
              ? null
              : `${(citation.similarity * 100).toFixed(0)}%`;
          return (
            <li key={`${citation.sourceKey}-${citation.chunkId}`}>
              <span className="chat-source-key">{citation.sourceKey}</span>
              <span>{citation.documentName || citation.documentId}</span>
              {pages && <span className="docs-muted">{pages}</span>}
              {score && <span className="docs-muted">{score}</span>}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
