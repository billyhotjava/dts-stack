import type { FC } from "react";
import { ActivityBar } from "./layout/ActivityBar";
import { SidePanel } from "./layout/SidePanel";
import { BottomPanel } from "./layout/BottomPanel";

export const SqlIde: FC = () => (
  <div
    data-testid="sqlide-root"
    style={{
      display: "flex",
      width: "100%",
      height: "100%",
      minHeight: 0,
    }}
  >
    <ActivityBar />
    <SidePanel>
      <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Schema · 骨架</div>
    </SidePanel>
    <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
      <div style={{ flex: 1, padding: 16, color: "var(--ant-color-text-secondary)" }}>
        SQL IDE v2 骨架 · Monaco 将在 T03 挂载此处
      </div>
      <BottomPanel>
        <div style={{ padding: 12 }}>Bottom · 骨架</div>
      </BottomPanel>
    </div>
  </div>
);
