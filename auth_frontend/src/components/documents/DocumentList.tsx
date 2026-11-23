"use client";

import Link from "next/link";
import type { DocumentListItem } from "@/lib/document.types";

type DocumentListProps = {
  items: DocumentListItem[];
  emptyMessage?: string;
};

export function DocumentList({
  items,
  emptyMessage = "No documents yet.",
}: DocumentListProps) {
  if (!items.length) {
    return <p className="docs-muted">{emptyMessage}</p>;
  }

  return (
    <ul className="docs-list">
      {items.map((item) => (
        <li key={item.documentId}>
          <Link href={`/documents/${item.documentId}`} className="docs-list-row">
            <div>
              <strong>{item.originalFilename}</strong>
              <p className="docs-muted">
                {new Date(item.createdAt).toLocaleString()} ·{" "}
                {Math.round(item.fileSize / 1024)} KB
              </p>
            </div>
            <span className="badge">{item.status}</span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
