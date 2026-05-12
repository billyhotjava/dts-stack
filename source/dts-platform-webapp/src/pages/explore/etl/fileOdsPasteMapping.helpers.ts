import { filterBusinessFileMappingFieldNames } from "./fileColumnSystemFields.helpers";

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
	const businessFields = filterBusinessFileMappingFieldNames(pastedFields);
	const nextColumns = (Array.isArray(columns) ? columns : []).map((column, index) => {
		if (index < businessFields.length) {
			return {
				...column,
				name: businessFields[index],
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
		matchedCount: Math.min(nextColumns.length, businessFields.length),
		unmatchedFields: businessFields.slice(nextColumns.length),
	};
}
