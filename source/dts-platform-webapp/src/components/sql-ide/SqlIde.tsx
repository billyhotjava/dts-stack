import { Button } from "antd";
import { type FC, useState } from "react";
import { ActivityBar } from "./layout/ActivityBar";
import { SidePanel } from "./layout/SidePanel";
import { BottomPanel } from "./layout/BottomPanel";
import { SqlEditor } from "./editor/SqlEditor";
import { ShortcutsHelp } from "./ShortcutsHelp";

export const SqlIde: FC = () => {
  const [sql, setSql] = useState<string>("-- SQL IDE v2\nSELECT 1;");
  const [helpOpen, setHelpOpen] = useState(false);

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
            onExecute={(s) => console.info("[SqlIde] execute:", s)}
            onExecuteInNewTab={(s) => console.info("[SqlIde] executeInNewTab:", s)}
            onFormat={() => console.info("[SqlIde] format placeholder (T06)")}
            onSaveAsQuery={(s) => console.info("[SqlIde] saveAsQuery:", s)}
            onToggleBottomPanel={() => console.info("[SqlIde] toggleBottomPanel")}
          />
        </div>
        <BottomPanel>
          <div style={{ padding: "8px 12px", display: "flex", alignItems: "center", gap: 8 }}>
            <span style={{ flex: 1 }}>Bottom · 骨架</span>
            <Button size="small" onClick={() => setHelpOpen(true)}>
              ⌨ Shortcuts
            </Button>
          </div>
        </BottomPanel>
      </div>
      <ShortcutsHelp open={helpOpen} onClose={() => setHelpOpen(false)} />
    </div>
  );
};
