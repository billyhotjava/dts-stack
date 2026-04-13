import type { Engine } from "../editor/SqlEditor";

export interface CursorPosition {
  line: number;
  column: number;
}

export interface MonacoRange {
  startLineNumber: number;
  startColumn: number;
  endLineNumber: number;
  endColumn: number;
}

export interface ColumnMeta {
  name: string;
  dataType?: string;
}

export interface ResultSnapshot {
  rows: Array<Record<string, unknown>>;
  columns: ColumnMeta[];
  rowCount: number;
  elapsedMs: number;
  status: "idle" | "running" | "success" | "failed" | "canceled";
  viewMode: "grid" | "chart" | "pivot" | "plan" | "log";
}

export interface TabState {
  id: string;                     // uuid
  title: string;                  // "Query N" by default, user-editable
  sqlText: string;
  engine: Engine;
  datasourceId: string | null;
  schemaContext: string | null;
  cursor: CursorPosition;
  selection: MonacoRange | null;
  lastExecutionId: string | null;
  resultSnapshot: ResultSnapshot | null;  // memory-only, not persisted
  dirty: boolean;                 // pending server sync
  sortOrder: number;
  updatedAt: string | null;       // ISO-8601, server's lastModifiedDate
  createdLocally: boolean;        // true until first successful POST
}

/** Server DTO matching SqlIdeTabDto record on Java side. */
export interface TabDto {
  id: string;
  title: string | null;
  sqlText: string | null;
  engine: string | null;
  datasourceId: string | null;
  schemaCtx: string | null;
  cursorLine: number | null;
  cursorCol: number | null;
  selectionJson: string | null;  // stringified MonacoRange
  lastExecutionId: string | null;
  sortOrder: number;
  active: boolean;
  updatedAt: string;  // ISO-8601
}
