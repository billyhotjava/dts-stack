import type { FC, PropsWithChildren } from "react";

// Default sized to comfortably fit a 10-row page at ROW_HEIGHT=32 in ResultGrid:
//   tab bar (~32) + grid toolbar (~36) + thead (~38) + 10×32 rows + pagination (~40) + paddings
//   ≈ 32 + 36 + 38 + 320 + 40 + 20 ≈ 486
export const BottomPanel: FC<PropsWithChildren<{ height?: number }>> = ({ height = 486, children }) => (
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
