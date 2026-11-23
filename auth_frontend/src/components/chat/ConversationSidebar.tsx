import type { ConversationSummary } from "@/lib/rag.types";

type Props = {
  items: ConversationSummary[];
  activeId: string | null;
  onSelect: (id: string) => void;
  onCreate: () => void;
  onDelete: (id: string) => void;
  busy?: boolean;
};

export function ConversationSidebar({
  items,
  activeId,
  onSelect,
  onCreate,
  onDelete,
  busy,
}: Props) {
  return (
    <aside className="chat-sidebar docs-panel">
      <div className="chat-sidebar-head">
        <h2>Chats</h2>
        <button
          type="button"
          className="btn btn-secondary chat-sidebar-new"
          onClick={onCreate}
          disabled={busy}
        >
          New
        </button>
      </div>
      {items.length === 0 ? (
        <p className="docs-muted">No conversations yet.</p>
      ) : (
        <ul className="chat-conv-list">
          {items.map((item) => {
            const active = item.conversationId === activeId;
            return (
              <li key={item.conversationId} className={active ? "active" : undefined}>
                <button
                  type="button"
                  className="chat-conv-item"
                  onClick={() => onSelect(item.conversationId)}
                  disabled={busy}
                >
                  <span>{item.title || "Untitled chat"}</span>
                  {item.updatedAt && (
                    <span className="docs-muted">
                      {new Date(item.updatedAt).toLocaleString()}
                    </span>
                  )}
                </button>
                <button
                  type="button"
                  className="chat-conv-delete"
                  aria-label="Delete conversation"
                  onClick={() => onDelete(item.conversationId)}
                  disabled={busy}
                >
                  ×
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </aside>
  );
}
