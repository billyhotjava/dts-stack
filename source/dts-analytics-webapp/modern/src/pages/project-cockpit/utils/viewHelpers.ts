import type { ProjectCockpitFilterQuery } from "../../../api/analyticsApi";

export function toFilters(state: {
	majorProjectId: string;
	dateFrom: string;
	dateTo: string;
	deptId: string;
	riskLevel: string;
}): ProjectCockpitFilterQuery {
	return {
		majorProjectId: state.majorProjectId || undefined,
		dateFrom: state.dateFrom || undefined,
		dateTo: state.dateTo || undefined,
		deptId: state.deptId || undefined,
		riskLevel: state.riskLevel || undefined,
	};
}

export function toTable(
	rows: Array<Record<string, unknown>>,
	columns: Array<{ key: string; label: string }>,
) {
	return {
		cols: columns.map((column) => ({ name: column.key, display_name: column.label })),
		rows: rows.map((row) => columns.map((column) => row[column.key] ?? "")),
	};
}
