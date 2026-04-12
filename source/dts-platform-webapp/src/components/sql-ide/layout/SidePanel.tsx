import type { FC, PropsWithChildren } from "react";

export const SidePanel: FC<PropsWithChildren<{ width?: number }>> = ({ width = 240, children }) => (
  <aside
    style={{
      width,
      flexShrink: 0,
      height: "100%",
      borderRight: "1px solid var(--ant-color-border)",
      overflow: "auto",
      background: "var(--ant-color-bg-container)",
    }}
    data-testid="sqlide-side-panel"
  >
    {children}
  </aside>
);
