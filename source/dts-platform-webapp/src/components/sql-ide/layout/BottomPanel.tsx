import type { FC, PropsWithChildren } from "react";

export const BottomPanel: FC<PropsWithChildren<{ height?: number }>> = ({ height = 260, children }) => (
  <div
    style={{
      height,
      flexShrink: 0,
      borderTop: "1px solid var(--ant-color-border)",
      background: "var(--ant-color-bg-container)",
      overflow: "auto",
    }}
    data-testid="sqlide-bottom-panel"
  >
    {children}
  </div>
);
