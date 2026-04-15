import { Input, Select, Spin } from "antd";
import { type FC, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Tree, type NodeRendererProps } from "react-arborist";
import { TableContextMenu } from "./SchemaContextMenu";
import { TableDetailPopover } from "./TableDetailPopover";
import {
  useDatasourcesQuery,
  useSearchCatalogQuery,
  useSchemasQuery,
  useTablesQuery,
} from "./useSchemaTreeData";

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
  const [debouncedFilter, setDebouncedFilter] = useState("");
  const [treeData, setTreeData] = useState<TreeNode[]>([]);

  // B4: adaptive height via ResizeObserver
  const containerRef = useRef<HTMLDivElement | null>(null);
  const [treeHeight, setTreeHeight] = useState(500);

  useEffect(() => {
    const el = containerRef.current;
    if (!el) return;
    const ro = new ResizeObserver((entries) => {
      const h = entries[0]?.contentRect.height;
      if (h && h > 40) setTreeHeight(Math.floor(h));
    });
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  // B6: debounce search filter at 300 ms
  useEffect(() => {
    const id = setTimeout(() => setDebouncedFilter(filter), 300);
    return () => clearTimeout(id);
  }, [filter]);

  const { data: datasources } = useDatasourcesQuery();
  const { data: schemas, isLoading: schemasLoading } = useSchemasQuery(dsId);

  // B6: search mode when debounced filter has >= 2 chars
  const isSearchMode = debouncedFilter.length >= 2;
  const { data: searchHits, isFetching: searchFetching } = useSearchCatalogQuery(
    dsId,
    debouncedFilter,
  );

  // Default-pick first datasource
  useEffect(() => {
    if (dsId === null && datasources && datasources.length > 0) {
      setDsId(datasources[0].id);
    }
  }, [dsId, datasources]);

  // B1: build treeData from schemas into controlled state; reset when dsId or schemas change
  useEffect(() => {
    if (!schemas) {
      setTreeData([]);
      return;
    }
    setTreeData(
      schemas.map((s) => ({
        id: `schema:${s.name}`,
        name: s.name,
        kind: "schema" as const,
        schema: s.name,
        children: [],
      })),
    );
  }, [schemas]);

  // B6: when in search mode, replace treeData with search results
  const searchTreeData: TreeNode[] = useMemo(() => {
    if (!searchHits) return [];
    // Group hits by schema.table
    const byTable = new Map<string, TreeNode>();
    for (const hit of searchHits) {
      const tableId = `table:${hit.schema}.${hit.table}`;
      if (!byTable.has(tableId)) {
        byTable.set(tableId, {
          id: tableId,
          name: hit.table,
          kind: "table",
          schema: hit.schema,
          table: hit.table,
        });
      }
    }
    return Array.from(byTable.values());
  }, [searchHits]);

  // B1: callback for SchemaRow to populate children without mutation
  const onTablesLoaded = useCallback((schema: string, tables: TreeNode[]) => {
    setTreeData((prev) =>
      prev.map((node) =>
        node.kind === "schema" && node.schema === schema && (node.children?.length ?? 0) === 0
          ? { ...node, children: tables }
          : node,
      ),
    );
  }, []);

  const displayData = isSearchMode ? searchTreeData : treeData;
  const isLoading = schemasLoading || (isSearchMode && searchFetching);

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
      {/* B4: the flex-1 div is the target for ResizeObserver */}
      <div ref={containerRef} style={{ flex: 1, minHeight: 0, padding: "0 4px" }}>
        {isLoading ? (
          <div style={{ textAlign: "center", padding: 20 }}><Spin /></div>
        ) : displayData.length === 0 ? (
          // B5: empty-state messages
          <div style={{ textAlign: "center", padding: 20, fontSize: 12, color: "var(--ant-color-text-tertiary)" }}>
            {isSearchMode ? "无匹配结果" : !schemasLoading && schemas ? "暂无 schema" : null}
          </div>
        ) : (
          <Tree<TreeNode>
            data={displayData}
            openByDefault={false}
            width="100%"
            height={treeHeight}
            indent={16}
            rowHeight={26}
            disableMultiSelection
          >
            {(nodeProps: NodeRendererProps<TreeNode>) => (
              <SchemaTreeRow
                {...nodeProps}
                dsId={dsId ?? ""}
                onInsertIdentifier={onInsertIdentifier}
                onInsertSqlAtCursor={onInsertSqlAtCursor}
                onTablesLoaded={onTablesLoaded}
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
  onTablesLoaded: (schema: string, tables: TreeNode[]) => void;
}

const SchemaTreeRow: FC<RowProps> = ({
  node,
  style,
  dragHandle,
  dsId,
  onInsertIdentifier,
  onInsertSqlAtCursor,
  onTablesLoaded,
}) => {
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
          onTablesLoaded={onTablesLoaded}
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
  node: NodeRendererProps<TreeNode>["node"];
  dsId: string;
  onInsertIdentifier?: (qualifiedName: string) => void;
  onInsertSqlAtCursor?: (sql: string) => void;
  onTablesLoaded: (schema: string, tables: TreeNode[]) => void;
}> = ({ node, dsId, onTablesLoaded }) => {
  const schemaData = node.data as Extract<TreeNode, { kind: "schema" }>;

  const { data: tables } = useTablesQuery(
    node.isOpen ? dsId : null,
    node.isOpen ? schemaData.schema : null,
  );

  // B1: when tables arrive and children not yet populated, notify parent via callback (no mutation)
  useEffect(() => {
    if (node.isOpen && tables && (schemaData.children?.length ?? 0) === 0) {
      const childNodes: TreeNode[] = tables.map((t) => ({
        id: `table:${schemaData.schema}.${t.name}`,
        name: t.name,
        kind: "table" as const,
        schema: schemaData.schema,
        table: t.name,
      }));
      // B5: even if childNodes is empty the parent will re-render with an empty children array
      onTablesLoaded(schemaData.schema, childNodes);
    }
  }, [node.isOpen, tables, schemaData.schema, schemaData.children?.length, onTablesLoaded]);

  return (
    <span style={{ fontSize: 12, color: "var(--ant-color-text)", userSelect: "none" }}>
      📁 {node.data.name}
    </span>
  );
};
