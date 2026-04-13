import { Input, Select, Spin } from "antd";
import { type FC, useEffect, useMemo, useState } from "react";
import { Tree, type NodeApi, type NodeRendererProps } from "react-arborist";
import { TableContextMenu } from "./SchemaContextMenu";
import { TableDetailPopover } from "./TableDetailPopover";
import { useDatasourcesQuery, useSchemasQuery, useTablesQuery } from "./useSchemaTreeData";

type TreeNode =
  | { id: string; name: string; kind: "schema"; schema: string; children?: TreeNode[] }
  | { id: string; name: string; kind: "table"; schema: string; table: string };

export interface SchemaTreeProps {
  /** Called when user double-clicks a table — receives "schema.table". */
  onInsertIdentifier?: (qualifiedName: string) => void;
  /** Called when context-menu item generates SQL — receives the SQL string. */
  onInsertSqlAtCursor?: (sql: string) => void;
}

export const SchemaTree: FC<SchemaTreeProps> = ({ onInsertIdentifier, onInsertSqlAtCursor }) => {
  const [dsId, setDsId] = useState<string | null>(null);
  const [filter, setFilter] = useState("");

  const { data: datasources } = useDatasourcesQuery();
  const { data: schemas, isLoading: schemasLoading } = useSchemasQuery(dsId);

  // Default-pick first datasource
  useEffect(() => {
    if (dsId === null && datasources && datasources.length > 0) {
      setDsId(datasources[0].id);
    }
  }, [dsId, datasources]);

  const treeData: TreeNode[] = useMemo(() => {
    if (!schemas) return [];
    return schemas.map((s) => ({
      id: `schema:${s.name}`,
      name: s.name,
      kind: "schema" as const,
      schema: s.name,
      children: [],
    }));
  }, [schemas]);

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div style={{ padding: "8px 12px", display: "flex", flexDirection: "column", gap: 6 }}>
        <Select
          size="small"
          value={dsId ?? undefined}
          onChange={(v) => setDsId(v)}
          options={(datasources ?? []).map((d) => ({ value: d.id, label: d.label }))}
          style={{ width: "100%" }}
          placeholder="选择数据源"
        />
        <Input
          size="small"
          placeholder="搜索表/列…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          allowClear
        />
      </div>
      <div style={{ flex: 1, minHeight: 0, padding: "0 4px" }}>
        {schemasLoading ? (
          <div style={{ textAlign: "center", padding: 20 }}><Spin /></div>
        ) : (
          <Tree<TreeNode>
            data={treeData}
            openByDefault={false}
            width="100%"
            height={500}
            indent={16}
            rowHeight={26}
            disableMultiSelection
            searchTerm={filter || undefined}
          >
            {(nodeProps: NodeRendererProps<TreeNode>) => (
              <SchemaTreeRow
                {...nodeProps}
                dsId={dsId ?? ""}
                onInsertIdentifier={onInsertIdentifier}
                onInsertSqlAtCursor={onInsertSqlAtCursor}
              />
            )}
          </Tree>
        )}
      </div>
    </div>
  );
};

interface RowProps extends NodeRendererProps<TreeNode> {
  dsId: string;
  onInsertIdentifier?: (qualifiedName: string) => void;
  onInsertSqlAtCursor?: (sql: string) => void;
}

const SchemaTreeRow: FC<RowProps> = ({ node, style, dragHandle, dsId, onInsertIdentifier, onInsertSqlAtCursor }) => {
  return (
    <div ref={dragHandle} style={{ ...style, display: "flex", alignItems: "center", paddingLeft: 4 }}>
      <span
        onClick={() => node.toggle()}
        style={{ cursor: "pointer", width: 14, fontSize: 10, color: "var(--ant-color-text-tertiary)" }}
      >
        {node.isInternal ? (node.isOpen ? "▾" : "▸") : ""}
      </span>
      {node.data.kind === "schema" ? (
        <SchemaRow
          node={node}
          dsId={dsId}
          onInsertIdentifier={onInsertIdentifier}
          onInsertSqlAtCursor={onInsertSqlAtCursor}
        />
      ) : (() => {
        const d = node.data as Extract<TreeNode, { kind: "table" }>;
        return (
          <TableContextMenu
            dsId={dsId}
            schema={d.schema}
            table={d.table}
            onInsertSqlAtCursor={(sql) => onInsertSqlAtCursor?.(sql)}
          >
            <TableDetailPopover dsId={dsId} schema={d.schema} table={d.table}>
              <span
                style={{ fontSize: 12, color: "var(--ant-color-text)", cursor: "pointer", userSelect: "none" }}
                onDoubleClick={() => onInsertIdentifier?.(`${d.schema}.${d.table}`)}
              >
                📄 {d.name}
              </span>
            </TableDetailPopover>
          </TableContextMenu>
        );
      })()}
    </div>
  );
};

const SchemaRow: FC<{
  node: NodeApi<TreeNode>;
  dsId: string;
  onInsertIdentifier?: (qualifiedName: string) => void;
  onInsertSqlAtCursor?: (sql: string) => void;
}> = ({ node, dsId }) => {
  const { data: tables } = useTablesQuery(
    node.isOpen ? dsId : null,
    node.isOpen ? node.data.schema : null,
  );

  // Populate children lazily when expanded and tables are loaded
  const schemaData = node.data as Extract<TreeNode, { kind: "schema" }>;
  if (node.isOpen && tables && (schemaData.children?.length ?? 0) === 0) {
    schemaData.children = tables.map((t) => ({
      id: `table:${schemaData.schema}.${t.name}`,
      name: t.name,
      kind: "table" as const,
      schema: schemaData.schema,
      table: t.name,
    }));
  }

  return (
    <span style={{ fontSize: 12, color: "var(--ant-color-text)", userSelect: "none" }}>
      📁 {node.data.name}
    </span>
  );
};
