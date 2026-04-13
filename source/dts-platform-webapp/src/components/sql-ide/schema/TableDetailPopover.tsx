import { Popover, Spin } from "antd";
import { type FC, type PropsWithChildren } from "react";
import { useColumnsQuery } from "./useSchemaTreeData";

interface TableDetailPopoverProps {
  dsId: string;
  schema: string;
  table: string;
}

export const TableDetailPopover: FC<PropsWithChildren<TableDetailPopoverProps>> = ({
  dsId, schema, table, children,
}) => {
  const { data, isLoading } = useColumnsQuery(dsId, `${schema}.${table}`);

  return (
    <Popover
      placement="rightTop"
      mouseEnterDelay={0.5}
      mouseLeaveDelay={0.2}
      title={`${schema}.${table}`}
      content={
        isLoading ? (
          <Spin size="small" />
        ) : data && data.length > 0 ? (
          <div style={{ maxHeight: 280, overflow: "auto", minWidth: 220 }}>
            {data.map((c) => (
              <div key={c.name} style={{ fontSize: 12, padding: "2px 0" }}>
                <span style={{ fontFamily: "monospace" }}>{c.name}</span>
                <span style={{ color: "var(--ant-color-text-tertiary)", marginLeft: 8 }}>
                  {c.dataType}
                </span>
                {c.nullable && (
                  <span style={{ color: "var(--ant-color-text-quaternary)", marginLeft: 6 }}>
                    NULL
                  </span>
                )}
              </div>
            ))}
          </div>
        ) : (
          <span style={{ color: "var(--ant-color-text-tertiary)" }}>无列信息</span>
        )
      }
    >
      {children as React.ReactElement}
    </Popover>
  );
};
