import type { SourceColumnMeta } from './ColumnStyleEditor';

function asDisplayText(value: unknown): string | undefined {
  if (typeof value !== 'string' && typeof value !== 'number') return undefined;
  const text = String(value).trim();
  return text.length > 0 ? text : undefined;
}

function resolveStaticRowWidth(data: unknown): number {
  if (!Array.isArray(data)) return 0;
  return data.reduce((max, row) => {
    if (!Array.isArray(row)) return max;
    return Math.max(max, row.length);
  }, 0);
}

function inferStaticColumnBaseType(data: unknown, columnIndex: number): string {
  if (!Array.isArray(data)) return 'text';
  for (const row of data) {
    if (!Array.isArray(row)) continue;
    const value = row[columnIndex];
    if (value == null || value === '') continue;
    if (typeof value === 'number') return 'number';
    if (typeof value === 'boolean') return 'boolean';
    if (value instanceof Date) return 'date';
    if (typeof value === 'string' && /^\d{4}[-/]\d{1,2}[-/]\d{1,2}/.test(value.trim())) {
      return 'date';
    }
    return 'text';
  }
  return 'text';
}

export function resolveColumnStyleSourceColumns(config: Record<string, unknown>): SourceColumnMeta[] | undefined {
  const dynamicSourceColumns = config._sourceColumns;
  if (Array.isArray(dynamicSourceColumns) && dynamicSourceColumns.length > 0) {
    return dynamicSourceColumns
      .filter((item): item is SourceColumnMeta => Boolean(item) && typeof item === 'object' && typeof (item as SourceColumnMeta).name === 'string')
      .map((item) => ({
        name: item.name,
        displayName: item.displayName || item.name,
        baseType: item.baseType,
      }));
  }

  const header = Array.isArray(config.header) ? config.header : [];
  const staticColumnCount = Math.max(header.length, resolveStaticRowWidth(config.data));
  if (staticColumnCount <= 0) return undefined;

  return Array.from({ length: staticColumnCount }, (_, index) => ({
    name: String(index),
    displayName: asDisplayText(header[index]) ?? `列${index + 1}`,
    baseType: inferStaticColumnBaseType(config.data, index),
  }));
}
