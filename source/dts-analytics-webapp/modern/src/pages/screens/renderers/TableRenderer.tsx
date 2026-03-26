/**
 * Table-family renderer: table, scroll-board, scroll-ranking.
 * Extracted verbatim from ComponentRenderer.tsx — do not modify rendering logic.
 */
import type { ReactNode, ComponentType, Dispatch, SetStateAction } from 'react';
import type { CardData, ScreenComponent } from '../types';
import type { ScreenThemeTokens } from '../screenThemes';
import type { ScreenTheme } from '../types';
import { resolveTextColor } from './shared/chartUtils';
import {
    compareTableValues, resolveTableConditionalStyle,
    ThemedScrollTable, resolveBoundTableData,
} from './shared/tableUtils';
import {
    buildTableRowActionParams,
    resolvePreferredDrillValue,
} from './shared/actionUtils';
import { DelayReasonMatrix } from '../../project-cockpit/components/DelayReasonMatrix';

// Re-export the DrillState shape used by this renderer
interface DrillState {
    canDrillDown: boolean;
    breadcrumbs: Array<{ value: string }>;
    handleDrill: (value: string) => void;
}

interface ScreenRuntime {
    trackEvent: (event: { kind: string; key: string; value: string; source: string; meta?: string }) => void;
}

export interface TableRendererProps {
    type: string;
    c: Record<string, unknown>;
    t: ScreenThemeTokens;
    width: number;
    height: number;
    theme?: ScreenTheme;
    mode: 'designer' | 'preview';
    component: ScreenComponent;
    runtime: ScreenRuntime;
    cardData: CardData | null;
    tableSort: { colIndex: number; order: 'asc' | 'desc' } | null;
    setTableSort: Dispatch<SetStateAction<{ colIndex: number; order: 'asc' | 'desc' } | null>>;
    tablePage: number;
    setTablePage: Dispatch<SetStateAction<number>>;
    drillState: DrillState;
    drillActive: boolean;
    drillRuntimeEnabled: boolean;
    componentActions: Array<Record<string, unknown>>;
    executeComponentActions: (params: Record<string, unknown>) => void;
    dataViewModule: Record<string, ComponentType<Record<string, unknown>>> | null;
    renderUnavailableState: (title: string, detail?: string) => ReactNode;
}

export function renderTable(props: TableRendererProps): ReactNode {
    const {
        type,
        c,
        t,
        width,
        height,
        theme,
        mode,
        component,
        runtime,
        cardData,
        tableSort,
        setTableSort,
        tablePage,
        setTablePage,
        drillState,
        drillRuntimeEnabled,
        componentActions,
        executeComponentActions,
        dataViewModule,
        renderUnavailableState,
    } = props;

    const ScrollBoard = dataViewModule?.ScrollBoard as ComponentType<{ config: Record<string, unknown>; style?: React.CSSProperties }> | undefined;
    const ScrollRankingBoard = dataViewModule?.ScrollRankingBoard as ComponentType<{ config: Record<string, unknown>; style?: React.CSSProperties }> | undefined;

    switch (type) {
        case 'scroll-board': {
            const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'center' });
            const filteredConfig = { ...c, header: displayHeader, data: displayData, _columnMeta: columnMeta };
            const canRunScrollBoardActions = mode === 'preview' && componentActions.length > 0;
            const canRunScrollBoardDefaultDrill = mode === 'preview' && !canRunScrollBoardActions && drillRuntimeEnabled && drillState.canDrillDown;
            const handleScrollBoardRowClick = (row: string[]) => {
                const params = buildTableRowActionParams(displayHeader, row);
                if (canRunScrollBoardActions) {
                    executeComponentActions(params);
                    return;
                }
                if (!canRunScrollBoardDefaultDrill) {
                    return;
                }
                const clickedValue = resolvePreferredDrillValue(params);
                if (!clickedValue) {
                    return;
                }
                runtime.trackEvent({
                    kind: 'drill-down',
                    key: 'drillValue',
                    value: clickedValue,
                    source: `drill:${component.id}:scroll-board`,
                    meta: `depth=${drillState.breadcrumbs.length}`,
                });
                drillState.handleDrill(clickedValue);
            };

            // DataV ScrollBoard 硬编码 color:#fff 且无法通过 CSS/style 覆盖
            // 非 legacy-dark 主题使用自定义表格组件
            if (theme && theme !== 'legacy-dark') {
                return (
                    <ThemedScrollTable
                        config={filteredConfig}
                        tokens={t}
                        isRowInteractive={canRunScrollBoardActions || canRunScrollBoardDefaultDrill}
                        onRowClick={handleScrollBoardRowClick}
                    />
                );
            }
            if (!ScrollBoard) return renderUnavailableState('DataV 运行时未就绪');
            const allHaveWidth = columnMeta.length > 0 && columnMeta.every((col) => typeof col.width === 'number');
            const columnWidth = allHaveWidth
                ? columnMeta.map((col) => Math.max(40, Math.round((width * Number(col.width)) / 100)))
                : undefined;
            return (
                <ScrollBoard
                    config={{
                        header: displayHeader,
                        data: displayData,
                        rowNum: c.rowNum as number,
                        headerBGC: c.headerBGC as string,
                        oddRowBGC: c.oddRowBGC as string,
                        evenRowBGC: c.evenRowBGC as string,
                        waitTime: c.waitTime as number || 2000,
                        headerHeight: 35,
                        align: columnMeta.map((col) => col.align || 'center'),
                        ...(columnWidth ? { columnWidth } : {}),
                    }}
                    style={{ width: '100%', height: '100%' }}
                />
            );
        }

        case 'table': {
            const tableRenderMode = String(c.renderMode ?? '').trim().toLowerCase();
            if (tableRenderMode === 'delay-reason-matrix') {
                const sourceCols = Array.isArray(cardData?.cols) ? cardData.cols : [];
                const sourceRows = Array.isArray(cardData?.rows) ? cardData.rows : [];
                const matrixRows = sourceRows.map((row) => Object.fromEntries(
                    sourceCols.map((col, index) => [col.name, row[index]]),
                ));
                const canRunMatrixActions = mode === 'preview' && componentActions.length > 0;
                if (matrixRows.length === 0) {
                    return (
                        <div style={{
                            width: '100%',
                            height: '100%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            borderRadius: 16,
                            border: '1px dashed rgba(148, 163, 184, 0.3)',
                            background: 'rgba(248, 250, 252, 0.85)',
                            color: t.textSecondary,
                            fontSize: 13,
                        }}>
                            当前筛选范围暂无归因矩阵数据。
                        </div>
                    );
                }
                return (
                    <div style={{ width: '100%', height: '100%', overflow: 'auto' }}>
                        <DelayReasonMatrix
                            rows={matrixRows}
                            onDrillDept={canRunMatrixActions
                                ? (dept) => {
                                    executeComponentActions({
                                        name: dept,
                                        dept,
                                        data: { dept },
                                    });
                                }
                                : undefined}
                            onDrillReason={canRunMatrixActions
                                ? (dept, reason) => {
                                    executeComponentActions({
                                        name: reason,
                                        dept,
                                        reason,
                                        data: { dept, reason },
                                    });
                                }
                                : undefined}
                        />
                    </div>
                );
            }
            const { header: displayHeader, data: displayData, columnMeta } = resolveBoundTableData(c, { defaultAlign: 'left' });
            const fontSize = (c.fontSize as number) || 16;
            const headerFontSize = (c.headerFontSize as number) || fontSize;
            const headerColor = resolveTextColor(c.headerColor as string | undefined, t.textPrimary);
            const headerBackground = (c.headerBackground as string) || 'rgba(148, 163, 184, 0.16)';
            const bodyColor = resolveTextColor(c.bodyColor as string | undefined, t.textSecondary);
            const bodyBackground = (c.bodyBackground as string) || 'transparent';
            const borderColor = (c.borderColor as string) || 'rgba(148, 163, 184, 0.24)';
            const oddRowBackground = (c.oddRowBackground as string) || bodyBackground;
            const evenRowBackground = (c.evenRowBackground as string) || 'rgba(148, 163, 184, 0.06)';
            const enableSort = c.enableSort !== false;
            const enablePagination = c.enablePagination === true;
            const freezeHeader = c.freezeHeader !== false;
            const freezeFirstColumn = c.freezeFirstColumn === true;
            const pageSize = Math.max(1, Number(c.pageSize || 10));
            const conditionalRules = c.conditionalRules;

            const sortedRows = tableSort && enableSort
                ? [...displayData].sort((a, b) => {
                    const v = compareTableValues(a[tableSort.colIndex], b[tableSort.colIndex]);
                    return tableSort.order === 'asc' ? v : -v;
                })
                : displayData;
            const totalPages = enablePagination ? Math.max(1, Math.ceil(sortedRows.length / pageSize)) : 1;
            const safePage = Math.max(1, Math.min(tablePage, totalPages));
            const pageRows = enablePagination
                ? sortedRows.slice((safePage - 1) * pageSize, safePage * pageSize)
                : sortedRows;
            const canRunTableActions = mode === 'preview' && componentActions.length > 0;
            const canRunTableDefaultDrill = mode === 'preview' && !canRunTableActions && drillRuntimeEnabled && drillState.canDrillDown;

            return (
                <div style={{ width: '100%', height: '100%', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
                    <div style={{ flex: 1, overflow: 'auto' }}>
                    <table style={{ width: '100%', borderCollapse: 'collapse', tableLayout: 'fixed', fontSize }}>
                        {displayHeader.length > 0 && (
                            <thead>
                                <tr style={{ background: headerBackground }}>
                                    {displayHeader.map((title, i) => (
                                        <th key={i} style={{
                                            color: headerColor,
                                            fontSize: headerFontSize,
                                            width: columnMeta[i]?.width ? `${columnMeta[i].width}%` : undefined,
                                            borderBottom: '1px solid ' + borderColor,
                                            borderRight: i < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                            padding: '8px 10px',
                                            textAlign: columnMeta[i]?.headerAlign ?? columnMeta[i]?.align ?? 'left',
                                            fontWeight: 600,
                                            whiteSpace: columnMeta[i]?.wrap ? 'normal' : 'nowrap',
                                            overflow: 'hidden',
                                            textOverflow: columnMeta[i]?.wrap ? undefined : 'ellipsis',
                                            overflowWrap: columnMeta[i]?.wrap ? 'anywhere' : undefined,
                                            wordBreak: columnMeta[i]?.wrap ? 'break-word' : undefined,
                                            lineHeight: columnMeta[i]?.wrap ? 1.35 : undefined,
                                            ...(freezeHeader ? { position: 'sticky', top: 0, zIndex: 3 } : {}),
                                            ...(freezeFirstColumn && i === 0
                                                ? {
                                                    position: 'sticky',
                                                    left: 0,
                                                    zIndex: freezeHeader ? 5 : 2,
                                                    background: headerBackground,
                                                    boxShadow: `1px 0 0 ${borderColor}`,
                                                }
                                                : {}),
                                        }}>
                                            <button
                                                type="button"
                                                disabled={!enableSort}
                                                onClick={() => {
                                                    if (!enableSort) return;
                                                    setTablePage(1);
                                                    setTableSort((prev) => {
                                                        if (!prev || prev.colIndex !== i) {
                                                            return { colIndex: i, order: 'asc' };
                                                        }
                                                        if (prev.order === 'asc') {
                                                            return { colIndex: i, order: 'desc' };
                                                        }
                                                        return null;
                                                    });
                                                }}
                                                style={{
                                                    border: 'none',
                                                    background: 'transparent',
                                                    color: headerColor,
                                                    fontWeight: 600,
                                                    cursor: enableSort ? 'pointer' : 'default',
                                                    display: 'inline-flex',
                                                    alignItems: 'center',
                                                    gap: 4,
                                                    padding: 0,
                                                }}
                                            >
                                                <span>{title}</span>
                                                {columnMeta[i]?.masked && (
                                                    <span style={{ fontSize: 9, opacity: 0.5, marginLeft: 2 }} title="此列数据已脱敏">*</span>
                                                )}
                                                {tableSort?.colIndex === i ? (
                                                    <span style={{ fontSize: 10 }}>{tableSort.order === 'asc' ? '▲' : '▼'}</span>
                                                ) : null}
                                            </button>
                                        </th>
                                    ))}
                                </tr>
                            </thead>
                        )}
                        <tbody>
                            {pageRows.map((row, rowIndex) => (
                                <tr
                                    key={rowIndex}
                                    onClick={() => {
                                        const params = buildTableRowActionParams(displayHeader, row);
                                        if (canRunTableActions) {
                                            executeComponentActions(params);
                                            return;
                                        }
                                        if (canRunTableDefaultDrill) {
                                            const clickedValue = resolvePreferredDrillValue(params);
                                            if (!clickedValue) return;
                                            runtime.trackEvent({
                                                kind: 'drill-down',
                                                key: 'drillValue',
                                                value: clickedValue,
                                                source: `drill:${component.id}:table`,
                                                meta: `depth=${drillState.breadcrumbs.length}`,
                                            });
                                            drillState.handleDrill(clickedValue);
                                        }
                                    }}
                                    style={{
                                        background: rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground,
                                        cursor: canRunTableActions || canRunTableDefaultDrill ? 'pointer' : 'default',
                                    }}
                                >
                                    {displayHeader.map((_, colIndex) => {
                                        const conditional = resolveTableConditionalStyle(
                                            conditionalRules,
                                            colIndex,
                                            row[colIndex],
                                            columnMeta[colIndex],
                                        );
                                        const rowBackground = rowIndex % 2 === 0 ? oddRowBackground : evenRowBackground;
                                        const cellBackground = conditional.background || rowBackground;
                                        return (
                                            <td key={colIndex} style={{
                                                color: conditional.color || bodyColor,
                                                background: cellBackground,
                                                borderBottom: '1px solid ' + borderColor,
                                                borderRight: colIndex < displayHeader.length - 1 ? '1px solid ' + borderColor : 'none',
                                                padding: '8px 10px',
                                                textAlign: columnMeta[colIndex]?.align || 'left',
                                                whiteSpace: columnMeta[colIndex]?.wrap ? 'normal' : 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: columnMeta[colIndex]?.wrap ? undefined : 'ellipsis',
                                                overflowWrap: columnMeta[colIndex]?.wrap ? 'anywhere' : undefined,
                                                wordBreak: columnMeta[colIndex]?.wrap ? 'break-word' : undefined,
                                                lineHeight: columnMeta[colIndex]?.wrap ? 1.35 : undefined,
                                                ...(freezeFirstColumn && colIndex === 0
                                                    ? {
                                                        position: 'sticky',
                                                        left: 0,
                                                        zIndex: 1,
                                                        boxShadow: `1px 0 0 ${borderColor}`,
                                                        background: cellBackground,
                                                    }
                                                    : {}),
                                            }}>
                                                {columnMeta[colIndex]?.masked ? (
                                                    <span
                                                        style={{ color: 'rgba(148,163,184,0.6)', fontStyle: 'italic' }}
                                                        title="数据已脱敏"
                                                    >
                                                        {row[colIndex] ?? '***'}
                                                    </span>
                                                ) : (
                                                    row[colIndex] ?? ''
                                                )}
                                            </td>
                                        );
                                    })}
                                </tr>
                            ))}
                        </tbody>
                    </table>
                    </div>
                    {enablePagination && totalPages > 1 ? (
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'flex-end',
                            gap: 6,
                            paddingTop: 8,
                            color: t.textSecondary,
                            fontSize: 12,
                        }}>
                            <button
                                type="button"
                                disabled={safePage <= 1}
                                onClick={() => setTablePage((p) => Math.max(1, p - 1))}
                                style={{ border: '1px solid rgba(148,163,184,0.35)', background: 'transparent', color: t.textPrimary, borderRadius: 4, padding: '2px 8px', cursor: 'pointer' }}
                            >
                                上一页
                            </button>
                            <span>{safePage}/{totalPages}</span>
                            <button
                                type="button"
                                disabled={safePage >= totalPages}
                                onClick={() => setTablePage((p) => Math.min(totalPages, p + 1))}
                                style={{ border: '1px solid rgba(148,163,184,0.35)', background: 'transparent', color: t.textPrimary, borderRadius: 4, padding: '2px 8px', cursor: 'pointer' }}
                            >
                                下一页
                            </button>
                        </div>
                    ) : null}
                </div>
            );
        }
        case 'scroll-ranking':
            if (!ScrollRankingBoard) return renderUnavailableState('DataV 运行时未就绪');
            return (
                <ScrollRankingBoard
                    config={{
                        data: c.data as Array<{ name: string; value: number }>,
                        rowNum: c.rowNum as number || 5,
                        waitTime: c.waitTime as number || 2000,
                        carousel: 'single',
                    }}
                    style={{ width: '100%', height: '100%' }}
                />
            );

        default:
            return null;
    }
}
