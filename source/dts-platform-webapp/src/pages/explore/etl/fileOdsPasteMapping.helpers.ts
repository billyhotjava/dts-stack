export type FileColumnLike = {
	name?: string;
	_odsMatched?: boolean;
	_odsExtra?: boolean;
	[key: string]: any;
};

export type PastedOdsMappingResult<T extends FileColumnLike> = {
	columns: T[];
	matchedCount: number;
	unmatchedFields: string[];
};

export function parsePastedOdsFields(text: string): string[] {
	return String(text || "")
		.split(/[\n\r\t,，]+/)
		.map((item) => item.trim())
		.filter((item) => item.length > 0);
}

export function applyPastedOdsFieldsToFileColumns<T extends FileColumnLike>(
	columns: T[],
	pastedFields: string[]
): PastedOdsMappingResult<T> {
	const nextColumns = (Array.isArray(columns) ? columns : []).map((column, index) => {
		if (index < pastedFields.length) {
			return {
				...column,
				name: pastedFields[index],
				_odsMatched: true,
				_odsExtra: false,
			};
		}
		return {
			...column,
			_odsMatched: false,
			_odsExtra: true,
		};
	}) as T[];

	return {
		columns: nextColumns,
		matchedCount: Math.min(nextColumns.length, pastedFields.length),
		unmatchedFields: pastedFields.slice(nextColumns.length),
	};
}
