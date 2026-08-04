import type { ManagedFileColumn } from "@/api/ingestion";

export type FileStructureMode = "manual" | "reference_existing";
export type FileLandingMode = "create_new" | "recreate_existing";

export type TargetSchemaColumn = {
	name: string;
	type: string;
	description?: string;
	defaultValue?: string;
	nullable?: boolean;
	autoIncrement?: boolean;
	ordinalPosition?: number;
	columnSize?: number;
	decimalDigits?: number;
};

export type FileLandingSpec = {
	version: 1;
	structureMode: FileStructureMode;
	landingMode: FileLandingMode;
	referenceDataSourceId?: string;
	referenceTable?: string;
	targetTable: string;
	recreateConfirmed?: boolean;
	columns: ManagedFileColumn[];
};

export type FileTargetColumnValidationIssue = {
	index: number;
	code: "REQUIRED" | "INVALID_IDENTIFIER" | "DUPLICATE";
	message: string;
};

const TARGET_IDENTIFIER_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;

export const normalizeTargetFieldKey = (value: string): string =>
	value
		.trim()
		.toLocaleLowerCase()
		.replace(/[\s_]+/g, "");

const orderedTargetColumns = (columns: TargetSchemaColumn[]): TargetSchemaColumn[] =>
	columns
		.map((column, index) => ({ ...column, ordinalPosition: column.ordinalPosition ?? index + 1 }))
		.sort((left, right) => (left.ordinalPosition ?? 0) - (right.ordinalPosition ?? 0));

const applyTargetColumn = (source: ManagedFileColumn, target: TargetSchemaColumn): ManagedFileColumn => {
	const type = target.type || source.type;
	const normalizedType = type.trim().toLocaleLowerCase();
	const stringLike = /char|text|string/.test(normalizedType);
	const numeric = /numeric|decimal/.test(normalizedType);
	return {
		...source,
		name: target.name,
		type,
		description: target.description,
		length: stringLike && target.columnSize ? target.columnSize : source.length,
		precision: numeric && target.columnSize ? target.columnSize : source.precision,
		scale: numeric && target.decimalDigits !== undefined ? target.decimalDigits : source.scale,
		_odsMatched: true,
	};
};

export const applyTargetSchemaTemplate = (
	fileColumns: ManagedFileColumn[],
	targetColumns: TargetSchemaColumn[],
): ManagedFileColumn[] => {
	const ordered = orderedTargetColumns(targetColumns);
	const consumed = new Set<string>();

	return fileColumns.map((source) => {
		const sourceName = source.name.trim();
		const sourceLabel = source.label?.trim();
		const exact = ordered.find(
			(target) =>
				!consumed.has(target.name) &&
				(target.name === sourceName || Boolean(sourceLabel && target.name === sourceLabel)),
		);
		const normalized = exact
			? undefined
			: ordered.find((target) => {
					if (consumed.has(target.name)) return false;
					const targetKey = normalizeTargetFieldKey(target.name);
					return (
						targetKey === normalizeTargetFieldKey(sourceName) ||
						Boolean(sourceLabel && targetKey === normalizeTargetFieldKey(sourceLabel))
					);
				});
		const target = exact ?? normalized;
		if (!target) return { ...source, _odsMatched: false };
		consumed.add(target.name);
		return applyTargetColumn(source, target);
	});
};

export const fillUnmatchedByPosition = (
	fileColumns: ManagedFileColumn[],
	targetColumns: TargetSchemaColumn[],
): ManagedFileColumn[] => {
	const ordered = orderedTargetColumns(targetColumns);
	const alreadyMatched = new Set(
		fileColumns.filter((column) => column._odsMatched).map((column) => column.name.toLocaleLowerCase()),
	);
	const available = ordered.filter((column) => !alreadyMatched.has(column.name.toLocaleLowerCase()));
	let availableIndex = 0;

	return fileColumns.map((source) => {
		if (source._odsMatched) return source;
		const target = available[availableIndex++];
		return target ? applyTargetColumn(source, target) : source;
	});
};

export const validateFileTargetColumns = (columns: ManagedFileColumn[]): FileTargetColumnValidationIssue[] => {
	const normalizedNames = columns.map((column) => column.name.trim().toLocaleLowerCase());
	const duplicateNames = new Set(
		normalizedNames.filter((name, index) => Boolean(name) && normalizedNames.indexOf(name) !== index),
	);

	return columns.flatMap<FileTargetColumnValidationIssue>((column, index) => {
		const name = column.name.trim();
		if (!name) return [{ index, code: "REQUIRED" as const, message: "目标字段名不能为空" }];
		if (!TARGET_IDENTIFIER_PATTERN.test(name)) {
			return [
				{
					index,
					code: "INVALID_IDENTIFIER" as const,
					message: "目标字段名只能包含字母、数字和下划线，且不能以数字开头",
				},
			];
		}
		if (duplicateNames.has(name.toLocaleLowerCase())) {
			return [{ index, code: "DUPLICATE" as const, message: "目标字段名不能重复" }];
		}
		return [];
	});
};
