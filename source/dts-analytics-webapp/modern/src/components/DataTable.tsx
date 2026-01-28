import { type ReactNode, useCallback, useMemo, useState } from "react";
import { Button } from "../ui/Button/Button";
import { Badge } from "../ui/Badge/Badge";

type Props = {
	cols: Array<Record<string, unknown>>;
	rows: unknown[];
	maxRows?: number;
	pageSize?: number;
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
	if (value === null || value === undefined) return <span className="text-muted">(null)</span>;
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
		<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ opacity: 0.3 }}>
			<polyline points="18 15 12 9 6 15" />
		</svg>
	);
}

export function DataTable({ cols, rows, maxRows = 5000, pageSize = 50 }: Props) {
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
		<div style={{ display: "flex", flexDirection: "column", gap: "var(--spacing-sm, 8px)" }}>
			<div className="data-table-wrapper">
				<table className="table data-table">
					<thead>
						<tr>
							{cols.map((c, idx) => {
								const sortDir = sort?.columnIndex === idx ? sort.direction : null;
								return (
									<th
										key={idx}
										onClick={() => handleSort(idx)}
										style={{ cursor: "pointer", userSelect: "none" }}
										title={`Sort by ${colLabel(c, idx)}`}
									>
										<div style={{ display: "flex", alignItems: "center", gap: "var(--spacing-xs, 4px)" }}>
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
							return (
								<tr key={rIdx}>
									{cols.map((_, cIdx) => (
										<td key={cIdx}>{renderCell(cells[cIdx])}</td>
									))}
								</tr>
							);
						})}
						{paginatedRows.length === 0 && (
							<tr>
								<td colSpan={cols.length} style={{ textAlign: "center", color: "var(--color-text-tertiary, #999)", padding: "var(--spacing-lg, 24px)" }}>
									No rows to display
								</td>
							</tr>
						)}
					</tbody>
				</table>
			</div>

			{/* Pagination & Info */}
			{showPagination && (
				<div style={{
					display: "flex",
					alignItems: "center",
					justifyContent: "space-between",
					gap: "var(--spacing-md, 16px)",
					fontSize: "var(--font-size-sm, 12px)",
					color: "var(--color-text-secondary, #666)",
				}}>
					<div>
						<Badge variant="default" size="sm">
							{safeRows.length.toLocaleString()} rows
						</Badge>
						{safeRows.length < (Array.isArray(rows) ? rows.length : 0) && (
							<span style={{ marginLeft: "var(--spacing-xs, 4px)" }}>
								(truncated from {(Array.isArray(rows) ? rows.length : 0).toLocaleString()})
							</span>
						)}
					</div>
					<div style={{ display: "flex", alignItems: "center", gap: "var(--spacing-sm, 8px)" }}>
						<Button
							variant="tertiary"
							size="sm"
							onClick={() => setPage(0)}
							disabled={page === 0}
						>
							First
						</Button>
						<Button
							variant="tertiary"
							size="sm"
							onClick={() => setPage(Math.max(0, page - 1))}
							disabled={page === 0}
						>
							Prev
						</Button>
						<span>
							{page + 1} / {totalPages}
						</span>
						<Button
							variant="tertiary"
							size="sm"
							onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
							disabled={page >= totalPages - 1}
						>
							Next
						</Button>
						<Button
							variant="tertiary"
							size="sm"
							onClick={() => setPage(totalPages - 1)}
							disabled={page >= totalPages - 1}
						>
							Last
						</Button>
					</div>
				</div>
			)}

			<style>{`
				.data-table-wrapper {
					overflow-x: auto;
					max-height: 600px;
					overflow-y: auto;
					border: 1px solid var(--color-border, #eee);
					border-radius: var(--radius-sm, 6px);
				}

				.data-table thead th {
					position: sticky;
					top: 0;
					z-index: 1;
					background: var(--color-bg-secondary, #f9fbfc);
				}

				.data-table thead th:hover {
					background: var(--color-bg-hover, #f0f2f5);
				}

				.data-table tbody tr:hover {
					background: var(--color-bg-hover, #f5f7fa);
				}

				.data-table td {
					max-width: 300px;
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}
			`}</style>
		</div>
	);
}
