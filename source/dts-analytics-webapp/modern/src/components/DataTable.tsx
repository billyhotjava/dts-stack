import type { ReactNode } from "react";

type Props = {
	cols: Array<Record<string, unknown>>;
	rows: unknown[];
	maxRows?: number;
};

function colLabel(col: Record<string, unknown>, index: number): string {
	const display = col["display_name"];
	if (typeof display === "string" && display.trim()) return display;
	const name = col["name"];
	if (typeof name === "string" && name.trim()) return name;
	return `col_${index + 1}`;
}

function renderCell(value: unknown): ReactNode {
	if (value === null || value === undefined) return "";
	if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") return String(value);
	try {
		return JSON.stringify(value);
	} catch {
		return String(value);
	}
}

export function DataTable({ cols, rows, maxRows = 200 }: Props) {
	const safeRows = Array.isArray(rows) ? rows : [];
	const visibleRows = safeRows.slice(0, Math.max(0, maxRows));

	return (
		<div style={{ overflowX: "auto" }}>
			<table className="table">
				<thead>
					<tr>
						{cols.map((c, idx) => (
							<th key={idx}>{colLabel(c, idx)}</th>
						))}
					</tr>
				</thead>
				<tbody>
					{visibleRows.map((row, rIdx) => {
						const cells = Array.isArray(row) ? row : [];
						return (
							<tr key={rIdx}>
								{cols.map((_, cIdx) => (
									<td key={cIdx}>{renderCell(cells[cIdx])}</td>
								))}
							</tr>
						);
					})}
				</tbody>
			</table>
			{safeRows.length > visibleRows.length && (
				<div className="muted" style={{ marginTop: 8 }}>
					Showing {visibleRows.length} / {safeRows.length} rows
				</div>
			)}
		</div>
	);
}

