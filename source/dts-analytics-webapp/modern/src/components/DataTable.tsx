import { type ReactNode, useCallback, useMemo, useState } from "react";
import { Button, Tag } from "antd";

type Props = {
	cols: Array<Record<string, unknown>>;
	rows: unknown[];
	maxRows?: number;
	pageSize?: number;
	onRowClick?: (row: unknown[], rowIndex: number) => void;
};

type SortState = {
	columnIndex: number;
	direction: "asc" | "desc";
} | null;

function colLabel(col: Record<string, unknown>, index: number): string {
	const display = col["display_name"];
	if (typeof display === "string" && display.trim()) return display;
	const name = col["name"];
	if (typeof name === "string" && name.trim()) return name;
	return `col_${index + 1}`;
}

function renderCell(value: unknown): ReactNode {
	if (value === null || value === undefined) return <span className="text-text-muted">(null)</span>;
	if (typeof value === "boolean") return value ? "true" : "false";
	if (typeof value === "number") {
		return (
			<span style={{ fontVariantNumeric: "tabular-nums" }}>
				{value.toLocaleString()}
			</span>
		);
	}
	if (typeof value === "string") return value;
	try {
		return JSON.stringify(value);
	} catch {
		return String(value);
	}
}

function compareValues(a: unknown, b: unknown): number {
	if (a === null || a === undefined) return 1;
	if (b === null || b === undefined) return -1;
	if (typeof a === "number" && typeof b === "number") return a - b;
	return String(a).localeCompare(String(b), undefined, { numeric: true });
}

// Sort icon component
function SortIcon({ direction }: { direction: "asc" | "desc" | null }) {
	if (direction === "asc") {
		return (
			<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
				<polyline points="18 15 12 9 6 15" />
			</svg>
		);
	}
	if (direction === "desc") {
		return (
			<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
				<polyline points="6 9 12 15 18 9" />
			</svg>
		);
	}
	// Neutral - show both arrows faintly
	return (
		<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="opacity-30">
			<polyline points="18 15 12 9 6 15" />
		</svg>
	);
}

export function DataTable({ cols, rows, maxRows = 5000, pageSize = 50, onRowClick }: Props) {
	const [sort, setSort] = useState<SortState>(null);
	const [page, setPage] = useState(0);

	const safeRows = useMemo(() => {
		const arr = Array.isArray(rows) ? rows : [];
		return arr.slice(0, Math.max(0, maxRows));
	}, [rows, maxRows]);

	const sortedRows = useMemo(() => {
		if (!sort) return safeRows;
		const { columnIndex, direction } = sort;
		return [...safeRows].sort((a, b) => {
			const cellA = Array.isArray(a) ? a[columnIndex] : undefined;
			const cellB = Array.isArray(b) ? b[columnIndex] : undefined;
			const cmp = compareValues(cellA, cellB);
			return direction === "asc" ? cmp : -cmp;
		});
	}, [safeRows, sort]);

	const totalPages = Math.ceil(sortedRows.length / pageSize);
	const paginatedRows = useMemo(() => {
		const start = page * pageSize;
		return sortedRows.slice(start, start + pageSize);
	}, [sortedRows, page, pageSize]);

	const handleSort = useCallback((columnIndex: number) => {
		setSort((prev) => {
			if (prev?.columnIndex === columnIndex) {
				if (prev.direction === "asc") return { columnIndex, direction: "desc" };
				if (prev.direction === "desc") return null;
			}
			return { columnIndex, direction: "asc" };
		});
		setPage(0);
	}, []);

	const showPagination = safeRows.length > pageSize;

	return (
		<div className="flex flex-col gap-2">
			<div className="overflow-x-auto overflow-y-auto max-h-[600px] border border-border-default rounded-sm">
				<table className="w-full border-collapse">
					<thead>
						<tr>
							{cols.map((c, idx) => {
								const sortDir = sort?.columnIndex === idx ? sort.direction : null;
								return (
									<th
										key={idx}
										onClick={() => handleSort(idx)}
										className="sticky top-0 z-10 bg-surface-muted cursor-pointer select-none hover:bg-[var(--color-bg-hover,#f0f2f5)] px-md py-sm text-left text-sm font-semibold text-text-secondary uppercase tracking-wide border-b border-border-default"
										title={`Sort by ${colLabel(c, idx)}`}
									>
										<div className="flex items-center gap-1">
											<span>{colLabel(c, idx)}</span>
											<SortIcon direction={sortDir} />
										</div>
									</th>
								);
							})}
						</tr>
					</thead>
					<tbody>
						{paginatedRows.map((row, rIdx) => {
							const cells = Array.isArray(row) ? row : [];
							const globalIdx = page * pageSize + rIdx;
							return (
								<tr
									key={rIdx}
									onClick={onRowClick ? () => onRowClick(cells, globalIdx) : undefined}
									className="hover:bg-[var(--color-bg-hover,#f5f7fa)]"
									style={onRowClick ? { cursor: "pointer" } : undefined}
								>
									{cols.map((_, cIdx) => (
										<td key={cIdx} className="max-w-[300px] overflow-hidden text-ellipsis whitespace-nowrap px-md py-sm border-b border-border-default">{renderCell(cells[cIdx])}</td>
									))}
								</tr>
							);
						})}
						{paginatedRows.length === 0 && (
							<tr>
								<td colSpan={cols.length} className="text-center text-text-muted py-6 px-md border-b border-border-default">
									No rows to display
								</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>

			{/* Pagination & Info */}
			{showPagination && (
				<div className="flex items-center justify-between gap-4 text-xs text-text-secondary">
					<div>
						<Tag>
							{safeRows.length.toLocaleString()} rows
						</Tag>
						{safeRows.length < (Array.isArray(rows) ? rows.length : 0) && (
							<span className="ml-1">
								(truncated from {(Array.isArray(rows) ? rows.length : 0).toLocaleString()})
							</span>
						)}
					</div>
					<div className="flex items-center gap-2">
						<Button
							type="text"
							size="small"
							onClick={() => setPage(0)}
							disabled={page === 0}
						>
							First
						</Button>
						<Button
							type="text"
							size="small"
							onClick={() => setPage(Math.max(0, page - 1))}
							disabled={page === 0}
						>
							Prev
						</Button>
						<span>
							{page + 1} / {totalPages}
						</span>
						<Button
							type="text"
							size="small"
							onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
							disabled={page >= totalPages - 1}
						>
							Next
						</Button>
						<Button
							type="text"
							size="small"
							onClick={() => setPage(totalPages - 1)}
							disabled={page >= totalPages - 1}
						>
							Last
						</Button>
					</div>
				</div>
			)}
		</div>
	);
}
