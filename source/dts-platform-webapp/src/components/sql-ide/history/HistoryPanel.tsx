import { CheckCircleOutlined, CloseCircleOutlined, LoadingOutlined, StopOutlined } from "@ant-design/icons";
import { Empty, Input, List, Select, Spin, Typography } from "antd";
import { type FC, useEffect, useState } from "react";
import type { HistoryStatus, QueryHistoryItem } from "../api/sqlIdeHistory";
import type { Engine } from "../editor/SqlEditor";
import { useTabStore } from "../tabs/useTabStore";
import { useHistoryQuery } from "./useHistoryQuery";

const { Text } = Typography;
const { Search } = Input;

const STATUS_OPTIONS: { label: string; value: HistoryStatus | "" }[] = [
  { label: "全部状态", value: "" },
  { label: "SUCCESS", value: "SUCCESS" },
  { label: "FAILED", value: "FAILED" },
  { label: "CANCELED", value: "CANCELED" },
  { label: "RUNNING", value: "RUNNING" },
];

function statusColor(status: HistoryStatus | null | undefined): string {
  switch (status) {
    case "SUCCESS": return "var(--ant-color-success)";
    case "FAILED": return "var(--ant-color-error)";
    case "CANCELED": return "var(--ant-color-warning)";
    default: return "var(--ant-color-text-secondary)";
  }
}

function StatusIcon({ status }: { status: HistoryStatus | null | undefined }) {
  const color = statusColor(status);
  switch (status) {
    case "SUCCESS": return <CheckCircleOutlined style={{ color }} />;
    case "FAILED": return <CloseCircleOutlined style={{ color }} />;
    case "CANCELED": return <StopOutlined style={{ color }} />;
    case "RUNNING": return <LoadingOutlined style={{ color }} spin />;
    default: return <span style={{ color }}>?</span>;
  }
}

export const HistoryPanel: FC = () => {
  const [statusFilter, setStatusFilter] = useState<HistoryStatus | "">("");
  const [qInput, setQInput] = useState("");
  const [qDebounced, setQDebounced] = useState("");

  useEffect(() => {
    const t = setTimeout(() => setQDebounced(qInput), 300);
    return () => clearTimeout(t);
  }, [qInput]);

  const { data: items, isLoading } = useHistoryQuery({
    status: statusFilter || undefined,
    q: qDebounced || undefined,
  });

  const openTab = useTabStore((s) => s.openTab);

  function handleDoubleClick(item: QueryHistoryItem) {
    const rawEngine = (item.engine ?? "").toLowerCase() as Engine;
    const engine: Engine = ["trino", "hive", "postgresql", "generic"].includes(rawEngine)
      ? rawEngine
      : "generic";
    openTab({
      title: `History: ${(item.sqlText ?? "").slice(0, 30)}…`,
      sqlText: item.sqlText ?? "",
      engine,
    });
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", overflow: "hidden" }}>
      <div style={{ padding: "8px 12px", display: "flex", flexDirection: "column", gap: 6 }}>
        <Select<HistoryStatus | "">
          size="small"
          value={statusFilter}
          onChange={setStatusFilter}
          options={STATUS_OPTIONS}
          style={{ width: "100%" }}
        />
        <Search
          size="small"
          placeholder="搜索 SQL…"
          allowClear
          value={qInput}
          onChange={(e) => setQInput(e.target.value)}
        />
      </div>

      {isLoading ? (
        <div style={{ display: "flex", justifyContent: "center", padding: 24 }}>
          <Spin size="small" />
        </div>
      ) : (items && items.length > 0) ? (
        <div style={{ flex: 1, overflowY: "auto" }}>
          <List<QueryHistoryItem>
            size="small"
            dataSource={items}
            renderItem={(item) => (
              <List.Item
                key={item.id}
                onDoubleClick={() => handleDoubleClick(item)}
                style={{
                  padding: "6px 12px",
                  cursor: "pointer",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "flex-start",
                  gap: 2,
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: 6, width: "100%" }}>
                  <StatusIcon status={item.status} />
                  <Text style={{ color: statusColor(item.status), fontSize: 12 }}>{item.status ?? "—"}</Text>
                  {item.elapsedMs != null && (
                    <Text type="secondary" style={{ fontSize: 11, marginLeft: "auto" }}>
                      {item.elapsedMs}ms
                    </Text>
                  )}
                </div>
                <Text
                  style={{
                    fontSize: 12,
                    color: "var(--ant-color-text-secondary)",
                    whiteSpace: "nowrap",
                    overflow: "hidden",
                    textOverflow: "ellipsis",
                    maxWidth: "100%",
                  }}
                  title={item.sqlText ?? ""}
                >
                  {(item.sqlText ?? "").slice(0, 60)}
                </Text>
              </List.Item>
            )}
          />
        </div>
      ) : (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="暂无历史记录"
          style={{ padding: "24px 12px" }}
        />
      )}
    </div>
  );
};
