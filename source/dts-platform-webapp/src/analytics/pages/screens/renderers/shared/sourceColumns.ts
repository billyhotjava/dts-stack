import type { CardData } from '../../types';

export interface SourceColumnMeta {
    name: string;
    displayName: string;
    baseType: string;
}

export function resolveSourceColumnsMeta(cardData: CardData | null | undefined): SourceColumnMeta[] {
    if (!cardData?.cols?.length) {
        return [];
    }
    return cardData.cols.map((column) => ({
        name: column.name,
        displayName: column.display_name || column.name,
        baseType: column.base_type,
    }));
}

export function resolveSourceColumnsKey(columns: Array<{ name?: string }> | null | undefined): string {
    if (!Array.isArray(columns) || columns.length === 0) {
        return '';
    }
    return columns
        .map((column) => String(column?.name ?? '').trim())
        .filter((name) => name.length > 0)
        .join(',');
}

export function shouldPersistSourceColumns(
    previousColumns: Array<{ name?: string }> | null | undefined,
    nextColumns: SourceColumnMeta[],
): boolean {
    return nextColumns.length > 0 && resolveSourceColumnsKey(previousColumns) !== resolveSourceColumnsKey(nextColumns);
}
