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
import { getExecutionPage, type ColumnMeta, type ResultPage } from "../api/sqlIdeExecution";
import { rowsToTSV } from "./cellCopy";
import { applyColumnAction, type GridColumnState } from "./columnState";

export interface ResultGridProps {
  executionId: string;
  gridState: GridColumnState;
  onGridStateChange: (next: GridColumnState) => void;
}

const ROW_HEIGHT = 28;
const DEFAULT_COL_WIDTH = 140;

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
    } catch {
      void message.warning("复制失败");
    }
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          padding: "4px 8px",
          fontSize: 11,
          borderBottom: "1px solid var(--ant-color-border-secondary)",
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
          }}
        >
          复制全部 (TSV)
        </button>
      </div>
      <div ref={containerRef} style={{ flex: 1, overflow: "auto" }}>
        <table
          style={{ borderCollapse: "collapse", width: "100%", tableLayout: "fixed" }}
        >
          <thead
            style={{
              position: "sticky",
              top: 0,
              background: "var(--ant-color-bg-elevated)",
              zIndex: 1,
            }}
          >
            {table.getHeaderGroups().map((hg) => (
              <tr key={hg.id}>
                {hg.headers.map((header) => (
                  <th
                    key={header.id}
                    style={{
                      width: header.getSize(),
                      padding: "4px 8px",
                      borderBottom: "1px solid var(--ant-color-border)",
                      fontSize: 11,
                      textAlign: "left",
                      cursor: "pointer",
                    }}
                    onClick={() =>
                      onGridStateChange(
                        applyColumnAction(gridState, {
                          type: "setSort",
                          name: header.column.id,
                          direction:
                            gridState.sort?.name === header.column.id &&
                            gridState.sort.direction === "asc"
                              ? "desc"
                              : "asc",
                        }),
                      )
                    }
                  >
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {gridState.sort?.name === header.column.id && (
                      <span style={{ marginLeft: 4, color: "var(--ant-color-text-secondary)" }}>
                        {gridState.sort.direction === "asc" ? "▲" : "▼"}
                      </span>
                    )}
                  </th>
                ))}
              </tr>
            ))}
          </thead>
          <tbody
            style={{ position: "relative", height: rowVirtualizer.getTotalSize() }}
          >
            {rowVirtualizer.getVirtualItems().map((virtualRow) => {
              const row = table.getRowModel().rows[virtualRow.index];
              if (!row) return null;
              return (
                <tr
                  key={row.id}
                  style={{
                    position: "absolute",
                    top: virtualRow.start,
                    left: 0,
                    height: virtualRow.size,
                    width: "100%",
                  }}
                >
                  {row.getVisibleCells().map((cell) => (
                    <td
                      key={cell.id}
                      style={{
                        padding: "2px 8px",
                        borderBottom: "1px solid var(--ant-color-border-secondary)",
                        fontSize: 12,
                        whiteSpace: "nowrap",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        width: cell.column.getSize(),
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
      <div style={{ padding: "4px 8px", borderTop: "1px solid var(--ant-color-border-secondary)" }}>
        <Pagination
          current={page}
          pageSize={data.pageSize}
          total={data.total}
          onChange={(p) => setPage(p)}
          showSizeChanger={false}
          size="small"
        />
      </div>
    </div>
  );
};
