import { FolderOutlined, FileOutlined } from "@ant-design/icons";
import { Button, Empty, Input, List, Popconfirm, Spin, Typography } from "antd";
import { type FC, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteSavedQuery, listSavedQueries, type SavedQueryItem } from "../api/sqlIdeSaved";
import { useTabStore } from "../tabs/useTabStore";

const { Text } = Typography;
const { Search } = Input;

const NO_FOLDER_KEY = "未分组";

export const SavedPanel: FC = () => {
  const [filterText, setFilterText] = useState("");
  const queryClient = useQueryClient();
  const openTab = useTabStore((s) => s.openTab);

  const { data, isLoading } = useQuery({
    queryKey: ["sqlide", "saved"],
    queryFn: listSavedQueries,
    staleTime: 60_000,
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteSavedQuery(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["sqlide", "saved"] });
    },
  });

  const filtered = useMemo(() => {
    if (!data) return [];
    const q = filterText.trim().toLowerCase();
    if (!q) return data;
    return data.filter(
      (item) =>
        item.name.toLowerCase().includes(q) ||
        (item.folder ?? "").toLowerCase().includes(q),
    );
  }, [data, filterText]);

  const grouped = useMemo(() => {
    const map = new Map<string, SavedQueryItem[]>();
    for (const item of filtered) {
      const key = item.folder?.trim() || NO_FOLDER_KEY;
      const arr = map.get(key) ?? [];
      arr.push(item);
      map.set(key, arr);
    }
    // Sort: named folders first (alphabetically), then "未分组"
    const entries = [...map.entries()].sort(([a], [b]) => {
      if (a === NO_FOLDER_KEY) return 1;
      if (b === NO_FOLDER_KEY) return -1;
      return a.localeCompare(b);
    });
    return entries;
  }, [filtered]);

  const handleOpen = (item: SavedQueryItem) => {
    openTab({
      title: item.name,
      sqlText: item.sqlText ?? "",
      engine: "generic", // TODO: map datasource → engine when datasource→engine registry exists (F3 followup)
    });
  };

  if (isLoading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: 24 }}>
        <Spin />
      </div>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
      <div style={{ padding: "8px 12px 4px" }}>
        <Search
          placeholder="搜索查询或文件夹"
          size="small"
          allowClear
          value={filterText}
          onChange={(e) => setFilterText(e.target.value)}
        />
      </div>
      <div style={{ flex: 1, overflowY: "auto" }}>
        {grouped.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description="暂无保存的查询"
            style={{ marginTop: 40 }}
          />
        ) : (
          grouped.map(([folder, items]) => (
            <div key={folder}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 6,
                  padding: "6px 12px 2px",
                  color: "var(--ant-color-text-secondary)",
                  fontSize: 12,
                  fontWeight: 600,
                  userSelect: "none",
                }}
              >
                <FolderOutlined style={{ fontSize: 12 }} />
                {folder}
              </div>
              <List
                size="small"
                dataSource={items}
                renderItem={(item) => (
                  <List.Item
                    style={{
                      padding: "4px 12px 4px 28px",
                      cursor: "pointer",
                    }}
                    onClick={() => handleOpen(item)}
                    actions={[
                      <Popconfirm
                        key="del"
                        title="确定删除此查询？"
                        onConfirm={(e) => {
                          e?.stopPropagation();
                          deleteMutation.mutate(item.id);
                        }}
                        onCancel={(e) => e?.stopPropagation()}
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{ danger: true }}
                      >
                        <Button
                          type="text"
                          size="small"
                          danger
                          onClick={(e) => e.stopPropagation()}
                          loading={deleteMutation.isPending && deleteMutation.variables === item.id}
                        >
                          删除
                        </Button>
                      </Popconfirm>,
                    ]}
                  >
                    <div style={{ display: "flex", alignItems: "center", gap: 6, minWidth: 0 }}>
                      <FileOutlined style={{ fontSize: 12, color: "var(--ant-color-text-secondary)", flexShrink: 0 }} />
                      <Text ellipsis style={{ fontSize: 13 }}>
                        {item.name}
                      </Text>
                    </div>
                  </List.Item>
                )}
              />
            </div>
          ))
        )}
      </div>
    </div>
  );
};
