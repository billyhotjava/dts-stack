import { Dropdown, type MenuProps, message } from "antd";
import { type FC, type PropsWithChildren } from "react";
import { useColumnsQuery } from "./useSchemaTreeData";
import { copyName, generateInsert, generateSelect } from "./sqlGenerators";

interface TableContextMenuProps {
  dsId: string;
  schema: string;
  table: string;
  onInsertSqlAtCursor: (sql: string) => void;
}

export const TableContextMenu: FC<PropsWithChildren<TableContextMenuProps>> = ({
  dsId, schema, table, onInsertSqlAtCursor, children,
}) => {
  const { data: columns } = useColumnsQuery(dsId, `${schema}.${table}`);
  const items: MenuProps["items"] = [
    { key: "select", label: "Generate SELECT" },
    { key: "insert", label: "Generate INSERT" },
    { key: "copy-name", label: "Copy Name" },
  ];
  const onClick: MenuProps["onClick"] = ({ key }) => {
    switch (key) {
      case "select":
        onInsertSqlAtCursor(generateSelect(schema, table, null));
        break;
      case "insert": {
        const cols = (columns ?? []).map((c) => c.name);
        const sql = generateInsert(schema, table, cols);
        if (sql) onInsertSqlAtCursor(sql);
        else void message.info("列信息加载中，请稍后再试");
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
