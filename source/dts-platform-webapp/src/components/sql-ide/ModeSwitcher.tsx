import { Segmented } from "antd";
import { type FC } from "react";
import { useUiModeStore } from "./store/useUiModeStore";

export const ModeSwitcher: FC = () => {
  const mode = useUiModeStore((s) => s.mode);
  const setMode = useUiModeStore((s) => s.setMode);

  return (
    <Segmented
      size="small"
      value={mode}
      onChange={(v) => setMode(v as "simple" | "advanced")}
      options={[
        { label: "简洁", value: "simple" },
        { label: "高级", value: "advanced" },
      ]}
      aria-label="SQL IDE 显示模式"
    />
  );
};
