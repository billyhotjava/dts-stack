import { useQuery } from "@tanstack/react-query";
import {
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  type ColumnDef,
  type SortingState,
  useReactTable,
} from "@tanstack/react-table";
import { useVirtualizer } from "@tanstack/react-virtual";
import { Empty, Pagination, Spin, message } from "antd";
import { type FC, useEffect, useMemo, useRef, useState } from "react";
import { getExecutionPage, postCopyAudit, type ColumnMeta, type ResultPage } from "../api/sqlIdeExecution";
import { rowsToTSV } from "./cellCopy";
import { applyColumnAction, type GridColumnState } from "./columnState";

export interface ResultGridProps {
  executionId: string;
  gridState: GridColumnState;
  onGridStateChange: (next: GridColumnState) => void;
}

const ROW_HEIGHT = 32;
const DEFAULT_COL_WIDTH = 160;

const BORDER_COLOR = "var(--ant-color-border, rgba(5, 5, 5, 0.12))";
const BORDER_SOFT = "var(--ant-color-border-secondary, rgba(5, 5, 5, 0.06))";
const HEADER_BG = "var(--ant-color-fill-alter, #fafafa)";
const ROW_STRIPE_BG = "var(--ant-color-fill-quaternary, rgba(0, 0, 0, 0.02))";
const ROW_HOVER_BG = "var(--ant-color-primary-bg, rgba(22, 119, 255, 0.06))";

export const ResultGrid: FC<ResultGridProps> = ({ executionId, gridState, onGridStateChange }) => {
  const [page, setPage] = useState(1);
  const { data, isLoading, isError } = useQuery<ResultPage>({
    queryKey: ["sqlide", "execution", "page", executionId, page],
    queryFn: () => getExecutionPage(executionId, page, 200),
    enabled: !!executionId,
    staleTime: 60_000,
  });

  const visibleColumns = useMemo(
    () => (data?.columns ?? []).filter((c) => !gridState.hiddenColumns.includes(c.name)),
    [data, gridState.hiddenColumns],
  );

  const tableColumns: ColumnDef<Record<string, unknown>>[] = useMemo(
    () =>
      visibleColumns.map((col: ColumnMeta) => ({
        accessorKey: col.name,
        header: col.name,
        size: gridState.columnWidths[col.name] ?? DEFAULT_COL_WIDTH,
        cell: (info) => {
          const v = info.getValue();
          if (v === null || v === undefined)
            return (
              <span style={{ color: "var(--ant-color-text-quaternary)", fontStyle: "italic" }}>
                NULL
              </span>
            );
          if (typeof v === "number")
            return <span style={{ textAlign: "right", display: "block" }}>{v}</span>;
          return String(v);
        },
      })),
    [visibleColumns, gridState.columnWidths],
  );

  const sortingState: SortingState = useMemo(
    () =>
      gridState.sort
        ? [{ id: gridState.sort.name, desc: gridState.sort.direction === "desc" }]
        : [],
    [gridState.sort],
  );

  const table = useReactTable({
    data: data?.rows ?? [],
    columns: tableColumns,
    state: { sorting: sortingState },
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    columnResizeMode: "onChange",
    manualSorting: false,
  });

  const containerRef = useRef<HTMLDivElement>(null);

  // Fix 3: reset scroll position when the user switches pages
  useEffect(() => {
    containerRef.current?.scrollTo({ top: 0 });
  }, [page]);
  const rowVirtualizer = useVirtualizer({
    count: data?.rows.length ?? 0,
    getScrollElement: () => containerRef.current,
    estimateSize: () => ROW_HEIGHT,
    overscan: 8,
  });

  if (isLoading) {
    return (
      <div style={{ display: "flex", justifyContent: "center", padding: 24 }}>
        <Spin />
      </div>
    );
  }
  if (isError) {
    return <Empty description="加载失败" />;
  }
  if (!data || data.rows.length === 0) {
    return <Empty description="无数据" />;
  }

  const handleCopySelection = async () => {
    const tsv = rowsToTSV(
      data.rows,
      visibleColumns.map((c) => c.name),
    );
    try {
      await navigator.clipboard.writeText(tsv);
      void message.success(`已复制 ${data.rows.length} 行 ${visibleColumns.length} 列`);
      void postCopyAudit(executionId, data.rows.length * visibleColumns.length);
    } catch {
      void message.warning("复制失败");
    }
  };

  return (
    <div
      className="sqlide-result-grid"
      style={{ display: "flex", flexDirection: "column", height: "100%", padding: 8, gap: 0 }}
    >
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "6px 10px",
          fontSize: 12,
          border: `1px solid ${BORDER_COLOR}`,
          borderBottom: "none",
          borderTopLeftRadius: 6,
          borderTopRightRadius: 6,
          background: HEADER_BG,
        }}
      >
        <span style={{ color: "var(--ant-color-text-secondary)" }}>
          {data.rows.length} rows · page {data.page}/
          {Math.max(1, Math.ceil(data.total / data.pageSize))} · total {data.total}
        </span>
        <button
          type="button"
          onClick={handleCopySelection}
          style={{
            border: "none",
            background: "transparent",
            cursor: "pointer",
            color: "var(--ant-color-primary)",
            fontSize: 12,
          }}
        >
          复制全部 (TSV)
        </button>
      </div>
      <div
        ref={containerRef}
        style={{
          flex: 1,
          overflow: "auto",
          border: `1px solid ${BORDER_COLOR}`,
          background: "var(--ant-color-bg-container, #fff)",
        }}
      >
        <table
          style={{
            borderCollapse: "separate",
            borderSpacing: 0,
            width: "100%",
            tableLayout: "fixed",
            fontVariantNumeric: "tabular-nums",
          }}
        >
          <colgroup>
            {visibleColumns.map((col) => (
              <col
                key={col.name}
                style={{ width: gridState.columnWidths[col.name] ?? DEFAULT_COL_WIDTH }}
              />
            ))}
          </colgroup>
          <thead
            style={{
              position: "sticky",
              top: 0,
              zIndex: 2,
            }}
          >
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((header, idx) => {
                  const isSorted = gridState.sort?.name === header.column.id;
                  return (
                    <th
                      key={header.id}
                      style={{
                        width: header.getSize(),
                        padding: "8px 12px",
                        background: HEADER_BG,
                        borderBottom: `1px solid ${BORDER_COLOR}`,
                        borderRight:
                          idx < hg.headers.length - 1 ? `1px solid ${BORDER_SOFT}` : "none",
                        fontSize: 12,
                        fontWeight: 600,
                        color: "var(--ant-color-text, rgba(0, 0, 0, 0.88))",
                        textAlign: "left",
                        cursor: "pointer",
                        userSelect: "none",
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                      }}
                      onClick={() =>
                        onGridStateChange(
                          applyColumnAction(gridState, {
                            type: "setSort",
                            name: header.column.id,
                            direction: isSorted && gridState.sort?.direction === "asc" ? "desc" : "asc",
                          }),
                        )
                      }
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {isSorted && (
                        <span
                          style={{
                            marginLeft: 4,
                            color: "var(--ant-color-primary, #1677ff)",
                            fontSize: 10,
                          }}
                        >
                          {gridState.sort?.direction === "asc" ? "▲" : "▼"}
                        </span>
                      )}
                    </th>
                  );
                })}
              </tr>
            ))}
          </thead>
          <tbody
            style={{
              position: "relative",
              height: rowVirtualizer.getTotalSize(),
            }}
          >
            {rowVirtualizer.getVirtualItems().map((virtualRow) => {
              const row = table.getRowModel().rows[virtualRow.index];
              if (!row) return null;
              const zebra = virtualRow.index % 2 === 1;
              return (
                <tr
                  key={row.id}
                  data-zebra={zebra ? "1" : "0"}
                  style={{
                    position: "absolute",
                    top: virtualRow.start,
                    left: 0,
                    height: virtualRow.size,
                    width: "100%",
                    background: zebra ? ROW_STRIPE_BG : "transparent",
                  }}
                >
                  {row.getVisibleCells().map((cell, idx) => (
                    <td
                      key={cell.id}
                      style={{
                        padding: "6px 12px",
                        borderBottom: `1px solid ${BORDER_SOFT}`,
                        borderRight:
                          idx < row.getVisibleCells().length - 1
                            ? `1px solid ${BORDER_SOFT}`
                            : "none",
                        fontSize: 12,
                        color: "var(--ant-color-text, rgba(0, 0, 0, 0.88))",
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        width: cell.column.getSize(),
                        verticalAlign: "middle",
                      }}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div
        style={{
          padding: "6px 10px",
          border: `1px solid ${BORDER_COLOR}`,
          borderTop: "none",
          borderBottomLeftRadius: 6,
          borderBottomRightRadius: 6,
          background: HEADER_BG,
          display: "flex",
          justifyContent: "flex-end",
        }}
      >
        <Pagination
          current={page}
          pageSize={data.pageSize}
          total={data.total}
          onChange={(p) => setPage(p)}
          showSizeChanger={false}
          size="small"
        />
      </div>
      <style>{`
        .sqlide-result-grid tbody tr:hover {
          background: ${ROW_HOVER_BG} !important;
        }
      `}</style>
    </div>
  );
};
