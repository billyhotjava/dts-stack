export const FILE_SYSTEM_COLUMN_PREFIX = "_dts_";

const FILE_SYSTEM_COLUMN_NAMES = new Set(["id", "source_system", "import_time"]);

const normalizeColumnName = (value?: string | null) => String(value || "").trim().toLowerCase();

export const isFileSystemColumnName = (value?: string | null) => {
	const normalized = normalizeColumnName(value);
	return Boolean(normalized) && (normalized.startsWith(FILE_SYSTEM_COLUMN_PREFIX) || FILE_SYSTEM_COLUMN_NAMES.has(normalized));
};

export const filterBusinessFileMappingColumns = <T extends { name?: string | null }>(columns?: T[]) =>
	(Array.isArray(columns) ? columns : []).filter((column) => !isFileSystemColumnName(column?.name));

export const filterBusinessFileMappingFieldNames = (fields?: string[]) =>
	(Array.isArray(fields) ? fields : []).filter((field) => !isFileSystemColumnName(field));
