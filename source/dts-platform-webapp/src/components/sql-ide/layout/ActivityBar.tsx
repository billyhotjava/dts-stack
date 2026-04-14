import { type FC } from "react";
import { useShallow } from "zustand/react/shallow";
import { useLayoutStore, type ActivityId } from "./useLayoutStore";
import { useUiModeStore } from "../store/useUiModeStore";

interface ActivityIconDef {
  id: ActivityId;
  label: string;
  emoji: string;
}

const ACTIVITIES: ActivityIconDef[] = [
  { id: "schema", label: "Schema", emoji: "🗂" },
  { id: "history", label: "History", emoji: "📋" },
  { id: "saved", label: "Saved", emoji: "💾" },
  { id: "search", label: "Search", emoji: "🔍" },
  { id: "copilot", label: "Copilot", emoji: "🤖" },
];

export const ActivityBar: FC = () => {
  const { activeActivity, setActivity } = useLayoutStore(
    useShallow((s) => ({ activeActivity: s.activeActivity, setActivity: s.setActivity })),
  );
  const mode = useUiModeStore((s) => s.mode);

  const visibleActivities = ACTIVITIES.filter((a) => {
    if (mode === "simple" && (a.id === "search" || a.id === "copilot")) return false;
    return true;
  });

  return (
    <div
      role="toolbar"
      aria-orientation="vertical"
      aria-label="SQL IDE Activity Bar"
      data-testid="sqlide-activity-bar"
      style={{
        width: 44,
        flexShrink: 0,
        height: "100%",
        borderRight: "1px solid var(--ant-color-border)",
        background: "var(--ant-color-bg-container)",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        paddingTop: 8,
        gap: 6,
      }}
    >
      {visibleActivities.map((a) => {
        const active = activeActivity === a.id;
        return (
          <button
            key={a.id}
            type="button"
            data-testid={`sqlide-activity-${a.id}`}
            aria-label={a.label}
            aria-pressed={active}
            title={a.label}
            onClick={() => setActivity(a.id)}
            style={{
              width: 36,
              height: 36,
              border: "none",
              cursor: "pointer",
              background: active ? "var(--ant-color-bg-elevated)" : "transparent",
              color: active ? "var(--ant-color-primary)" : "var(--ant-color-text-secondary)",
              borderLeft: active ? "2px solid var(--ant-color-primary)" : "2px solid transparent",
              borderRadius: 0,
              fontSize: 18,
            }}
          >
            {a.emoji}
          </button>
        );
      })}
    </div>
  );
};
