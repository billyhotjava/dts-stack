import { type FC, useState } from "react";
import { ActivityBar } from "./layout/ActivityBar";
import { SidePanel } from "./layout/SidePanel";
import { BottomPanel } from "./layout/BottomPanel";
import { SqlEditor } from "./editor/SqlEditor";

export const SqlIde: FC = () => {
  const [sql, setSql] = useState<string>("-- SQL IDE v2\nSELECT 1;");
  return (
    <div
      data-testid="sqlide-root"
      style={{ display: "flex", width: "100%", height: "100%", minHeight: 0 }}
    >
      <ActivityBar />
      <SidePanel>
        <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Schema · 骨架</div>
      </SidePanel>
      <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <div style={{ flex: 1, minHeight: 0 }}>
          <SqlEditor
            value={sql}
            onChange={setSql}
            engine="generic"
            mode="simple"
            isDark={true}
            onExecute={(s) => console.info("[SqlIde] execute placeholder:", s)}
          />
        </div>
        <BottomPanel>
          <div style={{ padding: 12 }}>Bottom · 骨架</div>
        </BottomPanel>
      </div>
    </div>
  );
};
