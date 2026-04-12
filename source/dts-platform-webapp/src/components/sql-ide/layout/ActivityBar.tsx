import type { FC } from "react";

export const ActivityBar: FC = () => (
  <div
    role="navigation"
    aria-label="SQL IDE Activity Bar"
    style={{
      width: 44,
      flexShrink: 0,
      height: "100%",
      borderRight: "1px solid var(--ant-color-border)",
      background: "var(--ant-color-bg-container)",
    }}
    data-testid="sqlide-activity-bar"
  >
    {/* Icons placeholder — filled in F3/T12 */}
  </div>
);
