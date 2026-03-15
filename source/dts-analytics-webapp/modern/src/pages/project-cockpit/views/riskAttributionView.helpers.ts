type DelayReasonMatrixRow = {
	dept?: string;
	total?: number;
	technical?: number;
	quality?: number;
	change?: number;
	coordination?: number;
	supplier?: number;
	test?: number;
	archive?: number;
	normal?: number;
};

const REASON_KEYS = [
	"technical",
	"quality",
	"change",
	"coordination",
	"supplier",
	"test",
	"archive",
	"normal",
] as const;

type ReasonKey = (typeof REASON_KEYS)[number];

export function buildDelayReasonMatrixSummary(rows: DelayReasonMatrixRow[]) {
	const normalizedRows = [...rows]
		.map((row) => {
			const dominantReason = REASON_KEYS.reduce<ReasonKey>((winner, current) => {
				return (row[current] ?? 0) > (row[winner] ?? 0) ? current : winner;
			}, "technical");
			return {
				...row,
				total: row.total ?? 0,
				dominantReason,
			};
		})
		.sort((left, right) => (right.total ?? 0) - (left.total ?? 0));

	return {
		rows: normalizedRows,
		maxTotal: normalizedRows.reduce((max, row) => Math.max(max, row.total ?? 0), 0),
	};
}
