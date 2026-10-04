import { Dropdown, type MenuProps, message } from "antd";
import { useQueryClient } from "@tanstack/react-query";
import { type FC, type PropsWithChildren } from "react";
import { listColumns } from "../api/sqlIdeCatalog";
import { copyName, generateInsert, generateSelect } from "./sqlGenerators";
import { useUiModeStore } from "../store/useUiModeStore";

interface TableContextMenuProps {
  dsId: string;
  schema: string;
  table: string;
  onInsertSqlAtCursor: (sql: string) => void;
}

export const TableContextMenu: FC<PropsWithChildren<TableContextMenuProps>> = ({
  dsId, schema, table, onInsertSqlAtCursor, children,
}) => {
  const qc = useQueryClient();
  const mode = useUiModeStore((s) => s.mode);

  const items: MenuProps["items"] = mode === "simple"
    ? [
        { key: "select", label: "Generate SELECT" },
        { key: "copy-name", label: "Copy Name" },
      ]
    : [
        { key: "select", label: "Generate SELECT" },
        { key: "insert", label: "Generate INSERT" },
        { key: "copy-name", label: "Copy Name" },
      ];
  const onClick: MenuProps["onClick"] = async ({ key }) => {
    switch (key) {
      case "select":
        onInsertSqlAtCursor(generateSelect(schema, table, null));
        break;
      case "insert": {
        try {
          const cols = await qc.fetchQuery({
            queryKey: ["sqlide", "catalog", "columns", dsId, `${schema}.${table}`],
            queryFn: () => listColumns(dsId, `${schema}.${table}`),
            staleTime: 5 * 60 * 1000,
          });
          const colNames = cols.map((c) => c.name);
          const sql = generateInsert(schema, table, colNames);
          if (sql) onInsertSqlAtCursor(sql);
          else void message.info("该表暂无列信息");
        } catch (err) {
          void message.warning("无法加载列信息");
        }
        break;
      }
      case "copy-name":
        navigator.clipboard.writeText(copyName(schema, table)).catch(() => {
          void message.warning("复制失败");
        });
        break;
    }
  };
  return (
    <Dropdown trigger={["contextMenu"]} menu={{ items, onClick }}>
      <span>{children}</span>
    </Dropdown>
  );
};
