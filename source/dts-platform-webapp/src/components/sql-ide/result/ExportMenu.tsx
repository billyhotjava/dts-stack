import { Dropdown, type MenuProps, message } from "antd";
import { type FC, useCallback } from "react";

interface ExportMenuProps {
  executionId: string;
}

export const ExportMenu: FC<ExportMenuProps> = ({ executionId }) => {
  const triggerDownload = useCallback((format: "csv" | "json" | "xlsx") => {
    const url = `/api/sql/v2/executions/${executionId}/export?format=${format}`;
    // Use simple anchor click; browser will follow Content-Disposition
    const a = document.createElement("a");
    a.href = url;
    a.rel = "noopener";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    void message.success(`已请求 ${format.toUpperCase()} 导出`);
  }, [executionId]);

  const items: MenuProps["items"] = [
    { key: "csv", label: "导出 CSV" },
    { key: "xlsx", label: "导出 Excel" },
    { key: "json", label: "导出 JSON" },
  ];

  return (
    <Dropdown menu={{ items, onClick: ({ key }) => triggerDownload(key as "csv" | "json" | "xlsx") }}>
      <button
        type="button"
        style={{ border: "none", background: "transparent", cursor: "pointer", color: "var(--ant-color-primary)", fontSize: 11 }}
      >
        导出 ▾
      </button>
    </Dropdown>
  );
};
