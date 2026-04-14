import { useQuery } from "@tanstack/react-query";
import { Empty, Spin, Switch } from "antd";
import { type FC, useState } from "react";
import { getExecutionLog } from "../api/sqlIdeLog";

export interface LogPanelProps {
  executionId: string;
  showAdvanced?: boolean;
}

function formatBytes(bytes: number | null): string {
  if (bytes == null) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

function formatDuration(ms: number | null): string {
  if (ms == null) return "—";
  if (ms < 1000) return `${ms} ms`;
  return `${(ms / 1000).toFixed(2)} s`;
}

function formatTimestamp(ts: string | null): string {
  if (!ts) return "—";
  try {
    return new Date(ts).toLocaleString();
  } catch {
    return ts;
  }
}

export const LogPanel: FC<LogPanelProps> = ({ executionId, showAdvanced = false }) => {
  const [advancedOpen, setAdvancedOpen] = useState(showAdvanced);

  const { data: log, isLoading, isError } = useQuery({
    queryKey: ["sqlide", "execution", "log", executionId],
    queryFn: () => getExecutionLog(executionId),
    staleTime: 60_000,
  });

  if (isLoading) {
    return (
      <div style={{ padding: 16, display: "flex", justifyContent: "center" }}>
        <Spin />
      </div>
    );
  }

  if (isError || !log) {
    return (
      <div style={{ padding: 16 }}>
        <Empty description="日志加载失败" />
      </div>
    );
  }

  const metaLines: Array<{ label: string; value: string }> = [
    { label: "执行 ID", value: log.executionId },
    { label: "状态", value: log.status ?? "—" },
    { label: "开始时间", value: formatTimestamp(log.startedAt) },
    { label: "结束时间", value: formatTimestamp(log.finishedAt) },
    { label: "耗时", value: formatDuration(log.elapsedMs) },
    { label: "行数", value: log.rowCount != null ? String(log.rowCount) : "—" },
    { label: "扫描字节", value: formatBytes(log.bytesProcessed) },
  ];

  if (log.errorMessage) {
    metaLines.push({ label: "错误信息", value: log.errorMessage });
  }

  return (
    <div style={{ padding: 12, height: "100%", overflow: "auto", fontFamily: "monospace", fontSize: 12 }}>
      {/* Metadata section */}
      <div
        style={{
          marginBottom: 12,
          background: "var(--ant-color-fill-quaternary)",
          borderRadius: 4,
          padding: "8px 12px",
        }}
      >
        {metaLines.map(({ label, value }) => (
          <div key={label} style={{ display: "flex", gap: 8, lineHeight: "22px" }}>
            <span style={{ color: "var(--ant-color-text-secondary)", minWidth: 80 }}>{label}</span>
            <span
              style={{
                color: label === "错误信息" ? "var(--ant-color-error)" : "var(--ant-color-text)",
                wordBreak: "break-all",
              }}
            >
              {value}
            </span>
          </div>
        ))}
      </div>

      {/* Original SQL */}
      <div style={{ marginBottom: 8 }}>
        <span style={{ color: "var(--ant-color-text-secondary)", fontSize: 11, marginBottom: 4, display: "block" }}>
          原始 SQL
        </span>
        <pre
          style={{
            margin: 0,
            padding: "8px 12px",
            background: "var(--ant-color-fill-quaternary)",
            borderRadius: 4,
            whiteSpace: "pre-wrap",
            wordBreak: "break-all",
            fontSize: 12,
            fontFamily: "monospace",
            color: "var(--ant-color-text)",
          }}
        >
          {log.originalSql}
        </pre>
      </div>

      {/* Advanced toggle — rewritten SQL (F6 will wire showAdvanced) */}
      <div style={{ marginTop: 8 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
          <span style={{ fontSize: 11, color: "var(--ant-color-text-secondary)" }}>高级模式（改写 SQL）</span>
          <Switch size="small" checked={advancedOpen} onChange={setAdvancedOpen} />
        </div>
        {advancedOpen && (
          <pre
            style={{
              margin: 0,
              padding: "8px 12px",
              background: "var(--ant-color-fill-quaternary)",
              borderRadius: 4,
              whiteSpace: "pre-wrap",
              wordBreak: "break-all",
              fontSize: 12,
              fontFamily: "monospace",
              color: "var(--ant-color-text-secondary)",
            }}
          >
            {log.rewrittenSql}
          </pre>
        )}
      </div>
    </div>
  );
};
