import type { FC, PropsWithChildren } from "react";

export const BottomPanel: FC<PropsWithChildren<{ height?: number }>> = ({ height = 260, children }) => (
  <div
    style={{
      height,
      flexShrink: 0,
      borderTop: "1px solid var(--ant-color-border)",
      background: "var(--ant-color-bg-container)",
      // overflow: hidden (not auto) — the ResultGrid's internal containerRef owns
      // the scroll. If BottomPanel is itself scrollable, it becomes the nearest
      // scrolling ancestor for sticky <th>, causing the "header scrolls away with
      // data" bug (user-reported 2026-04-15): thead sticks to containerRef which
      // never moves, while BottomPanel scrolls everything including containerRef
      // out of view. Clipping here forces the inner grid to own the scroll.
      overflow: "hidden",
      display: "flex",
      flexDirection: "column",
      minHeight: 0,
    }}
    data-testid="sqlide-bottom-panel"
  >
    {children}
  </div>
);
