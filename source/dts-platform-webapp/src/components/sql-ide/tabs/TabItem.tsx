import { type FC, useCallback, useRef, useState } from "react";

export interface TabItemProps {
  id: string;
  title: string;
  active: boolean;
  dirty: boolean;
  onActivate: (id: string) => void;
  onClose: (id: string) => void;
  onRename: (id: string, title: string) => void;
  onContextMenu?: (id: string, event: React.MouseEvent) => void;
}

export const TabItem: FC<TabItemProps> = ({
  id,
  title,
  active,
  dirty,
  onActivate,
  onClose,
  onRename,
  onContextMenu,
}) => {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(title);
  const inputRef = useRef<HTMLInputElement>(null);

  const commit = useCallback(() => {
    const t = draft.trim() || title;
    setEditing(false);
    if (t !== title) onRename(id, t);
  }, [draft, id, onRename, title]);

  return (
    <div
      data-testid={`sqlide-tab-${id}`}
      role="tab"
      aria-selected={active}
      onClick={() => !editing && onActivate(id)}
      onDoubleClick={() => {
        setDraft(title);
        setEditing(true);
        setTimeout(() => inputRef.current?.select(), 0);
      }}
      onContextMenu={(e) => {
        e.preventDefault();
        onContextMenu?.(id, e);
      }}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: 6,
        padding: "4px 14px",
        height: 32,
        cursor: "pointer",
        fontSize: 12,
        color: active ? "var(--ant-color-text)" : "var(--ant-color-text-secondary)",
        background: active ? "var(--ant-color-bg-container)" : "transparent",
        borderBottom: active
          ? "2px solid var(--ant-color-primary)"
          : "2px solid transparent",
        userSelect: "none",
      }}
    >
      {editing ? (
        <input
          ref={inputRef}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onBlur={commit}
          onKeyDown={(e) => {
            if (e.key === "Enter") commit();
            else if (e.key === "Escape") {
              setEditing(false);
              setDraft(title);
            }
          }}
          style={{
            font: "inherit",
            color: "inherit",
            background: "transparent",
            border: "1px solid var(--ant-color-border)",
            padding: "0 4px",
            width: 120,
          }}
        />
      ) : (
        <>
          <span>{title}</span>
          {dirty && (
            <span
              aria-label="未同步"
              style={{ color: "var(--ant-color-warning)", marginLeft: 2 }}
            >
              ●
            </span>
          )}
          <span
            aria-label="关闭"
            role="button"
            onClick={(e) => {
              e.stopPropagation();
              onClose(id);
            }}
            style={{
              width: 16,
              height: 16,
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              borderRadius: 3,
              marginLeft: 4,
              fontSize: 11,
              color: "var(--ant-color-text-tertiary)",
            }}
          >
            ✕
          </span>
        </>
      )}
    </div>
  );
};
