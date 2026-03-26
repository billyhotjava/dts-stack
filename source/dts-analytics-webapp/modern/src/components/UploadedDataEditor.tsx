import React, { useState, useRef, useCallback, useMemo } from 'react';
import * as XLSX from 'xlsx';
import Papa from 'papaparse';
import { analyticsApi } from '../api/analyticsApi';

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

export interface UploadedDataEditorProps {
    databaseId: number | string;
    onComplete: (result: { tableName: string; schema: string; rowCount: number }) => void;
}

type ColType = 'text' | 'number' | 'date' | 'boolean';

interface ColumnDef {
    originalName: string;
    displayName: string;
    type: ColType;
    excluded: boolean;
}

const TYPE_LABELS: Record<ColType, string> = {
    text: '文本',
    number: '数值',
    date: '日期',
    boolean: '布尔',
};

const ALL_TYPES: ColType[] = ['text', 'number', 'date', 'boolean'];

/* ------------------------------------------------------------------ */
/*  Type inference                                                     */
/* ------------------------------------------------------------------ */

function inferType(values: unknown[]): ColType {
    const samples = values.filter((v) => v != null && v !== '').slice(0, 20);
    if (samples.length === 0) return 'text';

    const allNumeric = samples.every((v) => {
        const n = Number(v);
        return !isNaN(n) && isFinite(n);
    });
    if (allNumeric) return 'number';

    const dateRe = /^\d{4}[-/]\d{1,2}[-/]\d{1,2}$/;
    if (samples.every((v) => dateRe.test(String(v).trim()))) return 'date';

    const boolSet = new Set(['true', 'false', '是', '否', '1', '0']);
    if (samples.every((v) => boolSet.has(String(v).trim().toLowerCase()))) return 'boolean';

    return 'text';
}

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

function filenameWithoutExt(name: string): string {
    return name.replace(/\.[^.]+$/, '');
}

/* ------------------------------------------------------------------ */
/*  Styles                                                             */
/* ------------------------------------------------------------------ */

const S = {
    dropZone: {
        border: '2px dashed rgba(148,163,184,0.3)',
        borderRadius: 8,
        padding: '20px 12px',
        textAlign: 'center' as const,
        cursor: 'pointer',
        color: '#94a3b8',
        fontSize: 12,
        lineHeight: 1.6,
        transition: 'border-color 0.2s',
    },
    dropZoneActive: {
        borderColor: 'var(--color-primary, #509EE3)',
        background: 'rgba(80,158,227,0.06)',
    },
    label: {
        fontSize: 11,
        color: '#94a3b8',
        marginBottom: 4,
        display: 'block' as const,
    },
    input: {
        width: '100%',
        padding: '6px 10px',
        fontSize: 12,
        border: '1px solid rgba(148,163,184,0.2)',
        borderRadius: 6,
        background: 'rgba(255,255,255,0.05)',
        color: '#e2e8f0',
        outline: 'none',
        boxSizing: 'border-box' as const,
    },
    select: {
        width: '100%',
        padding: '6px 10px',
        fontSize: 12,
        border: '1px solid rgba(148,163,184,0.2)',
        borderRadius: 6,
        background: 'rgba(255,255,255,0.05)',
        color: '#e2e8f0',
        outline: 'none',
        boxSizing: 'border-box' as const,
    },
    section: {
        marginTop: 10,
    },
    tableWrap: {
        border: '1px solid rgba(148,163,184,0.2)',
        borderRadius: 6,
        overflow: 'auto' as const,
        maxHeight: 260,
    },
    table: {
        width: '100%',
        borderCollapse: 'collapse' as const,
        fontSize: 11,
    },
    th: {
        padding: '5px 6px',
        background: 'rgba(255,255,255,0.04)',
        borderBottom: '1px solid rgba(148,163,184,0.15)',
        textAlign: 'left' as const,
        fontWeight: 600,
        color: '#94a3b8',
        whiteSpace: 'nowrap' as const,
    },
    td: {
        padding: '4px 6px',
        borderBottom: '1px solid rgba(148,163,184,0.1)',
        color: '#e2e8f0',
        whiteSpace: 'nowrap' as const,
        maxWidth: 120,
        overflow: 'hidden' as const,
        textOverflow: 'ellipsis' as const,
    },
    smallInput: {
        width: '100%',
        border: 'none',
        background: 'transparent',
        padding: '3px 4px',
        fontSize: 11,
        color: '#e2e8f0',
        outline: 'none',
        boxSizing: 'border-box' as const,
    },
    smallSelect: {
        border: 'none',
        background: 'transparent',
        padding: '2px 2px',
        fontSize: 11,
        color: '#e2e8f0',
        outline: 'none',
        cursor: 'pointer',
    },
    excludeBtn: {
        background: 'none',
        border: 'none',
        color: '#94a3b8',
        cursor: 'pointer',
        fontSize: 13,
        padding: '0 4px',
        lineHeight: 1,
    },
    restoreBtn: {
        background: 'none',
        border: 'none',
        color: 'var(--color-primary, #509EE3)',
        cursor: 'pointer',
        fontSize: 11,
        padding: '0 4px',
        lineHeight: 1,
    },
    stats: {
        fontSize: 10,
        color: '#94a3b8',
        padding: '4px 0',
        lineHeight: 1.5,
    },
    uploadBtn: {
        width: '100%',
        padding: '8px 0',
        fontSize: 13,
        fontWeight: 600,
        border: 'none',
        borderRadius: 6,
        background: 'var(--color-primary, #509EE3)',
        color: '#fff',
        cursor: 'pointer',
        marginTop: 10,
    },
    uploadBtnDisabled: {
        opacity: 0.5,
        cursor: 'not-allowed',
    },
    error: {
        color: '#ef4444',
        fontSize: 11,
        marginTop: 6,
    },
    fileInfo: {
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '6px 10px',
        background: 'rgba(255,255,255,0.04)',
        borderRadius: 6,
        fontSize: 11,
        color: '#e2e8f0',
        marginTop: 8,
    },
    clearBtn: {
        background: 'none',
        border: 'none',
        color: '#94a3b8',
        cursor: 'pointer',
        fontSize: 14,
        padding: 0,
        lineHeight: 1,
    },
};

const MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function UploadedDataEditor({ databaseId, onComplete }: UploadedDataEditorProps) {
    const fileInputRef = useRef<HTMLInputElement>(null);

    // File state
    const [file, setFile] = useState<File | null>(null);
    const [dragOver, setDragOver] = useState(false);

    // Parsed data
    const [sheetNames, setSheetNames] = useState<string[]>([]);
    const [selectedSheet, setSelectedSheet] = useState<string>('');
    const [headerRow, setHeaderRow] = useState(1);
    const [columns, setColumns] = useState<ColumnDef[]>([]);
    const [allRows, setAllRows] = useState<unknown[][]>([]);
    const [datasetName, setDatasetName] = useState('');

    // Raw workbook for re-parsing on sheet/headerRow change
    const workbookRef = useRef<XLSX.WorkBook | null>(null);
    const csvDataRef = useRef<string[][] | null>(null);

    // UI state
    const [parsing, setParsing] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);

    /* ---------- Parse helpers ---------- */

    const applySheetData = useCallback((rawRows: unknown[][], startRow: number) => {
        if (rawRows.length === 0) {
            setColumns([]);
            setAllRows([]);
            return;
        }
        const hdrIdx = Math.max(0, startRow - 1);
        const headerRaw = rawRows[hdrIdx] as unknown[] | undefined;
        if (!headerRaw) {
            setColumns([]);
            setAllRows([]);
            return;
        }
        const dataRows = rawRows.slice(hdrIdx + 1);
        const headers = headerRaw.map((v, i) => (v != null && String(v).trim() !== '') ? String(v) : `列${i + 1}`);

        const cols: ColumnDef[] = headers.map((name, ci) => {
            const colValues = dataRows.map((r) => (r as unknown[])[ci]);
            return {
                originalName: name,
                displayName: name,
                type: inferType(colValues),
                excluded: false,
            };
        });
        setColumns(cols);
        setAllRows(dataRows);
    }, []);

    const parseExcel = useCallback((arrayBuffer: ArrayBuffer, hdrRow: number, sheet?: string) => {
        const wb = XLSX.read(arrayBuffer, { type: 'array' });
        workbookRef.current = wb;
        csvDataRef.current = null;
        setSheetNames(wb.SheetNames);
        const sheetName = sheet || wb.SheetNames[0];
        setSelectedSheet(sheetName);
        const ws = wb.Sheets[sheetName];
        const rawRows: unknown[][] = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
        applySheetData(rawRows, hdrRow);
    }, [applySheetData]);

    const parseCsv = useCallback((text: string, hdrRow: number) => {
        workbookRef.current = null;
        setSheetNames([]);
        setSelectedSheet('');
        const result = Papa.parse<string[]>(text, { skipEmptyLines: true });
        const rawRows = result.data;
        csvDataRef.current = rawRows;
        applySheetData(rawRows, hdrRow);
    }, [applySheetData]);

    const handleFile = useCallback((f: File) => {
        setError(null);
        if (f.size > MAX_FILE_SIZE) {
            setError(`文件大小 ${formatFileSize(f.size)} 超过 50MB 限制`);
            return;
        }
        const ext = f.name.split('.').pop()?.toLowerCase() || '';
        if (!['xlsx', 'xls', 'csv'].includes(ext)) {
            setError('仅支持 .xlsx、.xls、.csv 格式');
            return;
        }
        setFile(f);
        setDatasetName(filenameWithoutExt(f.name));
        setHeaderRow(1);
        setParsing(true);

        if (ext === 'csv') {
            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    parseCsv(e.target?.result as string, 1);
                } catch (err) {
                    setError('CSV 解析失败：' + (err instanceof Error ? err.message : String(err)));
                } finally {
                    setParsing(false);
                }
            };
            reader.onerror = () => { setError('文件读取失败'); setParsing(false); };
            reader.readAsText(f);
        } else {
            const reader = new FileReader();
            reader.onload = (e) => {
                try {
                    parseExcel(e.target?.result as ArrayBuffer, 1);
                } catch (err) {
                    setError('Excel 解析失败：' + (err instanceof Error ? err.message : String(err)));
                } finally {
                    setParsing(false);
                }
            };
            reader.onerror = () => { setError('文件读取失败'); setParsing(false); };
            reader.readAsArrayBuffer(f);
        }
    }, [parseCsv, parseExcel]);

    /* ---------- Sheet / header row changes ---------- */

    const handleSheetChange = useCallback((sheetName: string) => {
        setSelectedSheet(sheetName);
        const wb = workbookRef.current;
        if (!wb) return;
        const ws = wb.Sheets[sheetName];
        const rawRows: unknown[][] = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
        applySheetData(rawRows, headerRow);
    }, [applySheetData, headerRow]);

    const handleHeaderRowChange = useCallback((row: number) => {
        setHeaderRow(row);
        if (workbookRef.current) {
            const ws = workbookRef.current.Sheets[selectedSheet];
            const rawRows: unknown[][] = XLSX.utils.sheet_to_json(ws, { header: 1, defval: '' });
            applySheetData(rawRows, row);
        } else if (csvDataRef.current) {
            applySheetData(csvDataRef.current, row);
        }
    }, [applySheetData, selectedSheet]);

    /* ---------- Column editing ---------- */

    const updateColumn = useCallback((idx: number, updates: Partial<ColumnDef>) => {
        setColumns((prev) => prev.map((c, i) => i === idx ? { ...c, ...updates } : c));
    }, []);

    /* ---------- Derived data ---------- */

    const activeColumns = useMemo(() => columns.filter((c) => !c.excluded), [columns]);
    const excludedCount = columns.length - activeColumns.length;

    const previewRows = useMemo(() => {
        const activeCols = columns.map((c, i) => ({ ...c, origIdx: i })).filter((c) => !c.excluded);
        return allRows.slice(0, 10).map((row) =>
            activeCols.map((c) => (row as unknown[])[c.origIdx])
        );
    }, [allRows, columns]);

    /* ---------- Upload ---------- */

    const handleUpload = useCallback(async () => {
        if (!file || activeColumns.length === 0) return;
        setUploading(true);
        setError(null);
        try {
            const activeCols = columns.map((c, i) => ({ ...c, origIdx: i })).filter((c) => !c.excluded);
            const activeIndices = activeCols.map((c) => c.origIdx);
            const result = await analyticsApi.uploadTable(databaseId, {
                tableName: datasetName || 'imported_data',
                columns: activeCols.map(c => ({ name: c.originalName, displayName: c.displayName, type: c.type })),
                rows: allRows.map(row => activeIndices.map(i => (row as unknown[])[i])),
            });
            onComplete(result);
        } catch (err) {
            setError('上传失败：' + (err instanceof Error ? err.message : String(err)));
        } finally {
            setUploading(false);
        }
    }, [file, activeColumns, columns, allRows, datasetName, databaseId, onComplete]);

    /* ---------- Drag and drop ---------- */

    const handleDrop = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(false);
        const f = e.dataTransfer.files[0];
        if (f) handleFile(f);
    }, [handleFile]);

    const handleDragOver = useCallback((e: React.DragEvent) => {
        e.preventDefault();
        setDragOver(true);
    }, []);

    const handleDragLeave = useCallback(() => {
        setDragOver(false);
    }, []);

    const clearFile = useCallback(() => {
        setFile(null);
        setColumns([]);
        setAllRows([]);
        setSheetNames([]);
        setSelectedSheet('');
        setDatasetName('');
        setError(null);
        workbookRef.current = null;
        csvDataRef.current = null;
        if (fileInputRef.current) fileInputRef.current.value = '';
    }, []);

    /* ---------- Render ---------- */

    // No file selected yet — show drop zone
    if (!file) {
        return (
            <>
                <div
                    style={{ ...S.dropZone, ...(dragOver ? S.dropZoneActive : {}) }}
                    onDrop={handleDrop}
                    onDragOver={handleDragOver}
                    onDragLeave={handleDragLeave}
                    onClick={() => fileInputRef.current?.click()}
                >
                    <div style={{ fontSize: 20, marginBottom: 4, opacity: 0.6 }}>[ + ]</div>
                    <div>拖拽文件到此处 或 点击选择</div>
                    <div style={{ fontSize: 10, color: '#64748b', marginTop: 4 }}>
                        支持 .xlsx / .xls / .csv，最大 50MB
                    </div>
                </div>
                <input
                    ref={fileInputRef}
                    type="file"
                    accept=".xlsx,.xls,.csv"
                    style={{ display: 'none' }}
                    onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f); }}
                />
                {error && <div style={S.error}>{error}</div>}
            </>
        );
    }

    // File loaded — show editor
    return (
        <>
            {/* File info bar */}
            <div style={S.fileInfo}>
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginRight: 8 }}>
                    {file.name} ({formatFileSize(file.size)})
                </span>
                <button type="button" style={S.clearBtn} onClick={clearFile} title="清除文件">×</button>
            </div>

            {/* Hidden file input for re-upload */}
            <input
                ref={fileInputRef}
                type="file"
                accept=".xlsx,.xls,.csv"
                style={{ display: 'none' }}
                onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f); }}
            />

            {parsing && (
                <div style={{ textAlign: 'center', padding: '20px 0', color: '#94a3b8', fontSize: 12 }}>
                    解析中...
                </div>
            )}

            {!parsing && (
                <>
                    {/* Sheet selector (Excel only) */}
                    {sheetNames.length > 1 && (
                        <div style={S.section}>
                            <label style={S.label}>工作表</label>
                            <select
                                style={S.select}
                                value={selectedSheet}
                                onChange={(e) => handleSheetChange(e.target.value)}
                            >
                                {sheetNames.map((name) => (
                                    <option key={name} value={name}>{name}</option>
                                ))}
                            </select>
                        </div>
                    )}

                    {/* Header row */}
                    <div style={S.section}>
                        <label style={S.label}>表头行号</label>
                        <input
                            type="number"
                            min={1}
                            style={{ ...S.input, width: 80 }}
                            value={headerRow}
                            onChange={(e) => {
                                const v = parseInt(e.target.value, 10);
                                if (!isNaN(v) && v >= 1) handleHeaderRowChange(v);
                            }}
                        />
                    </div>

                    {/* Dataset name */}
                    <div style={S.section}>
                        <label style={S.label}>数据集名称</label>
                        <input
                            type="text"
                            style={S.input}
                            value={datasetName}
                            onChange={(e) => setDatasetName(e.target.value)}
                            placeholder="输入数据集名称"
                        />
                    </div>

                    {/* Column editor */}
                    {columns.length > 0 && (
                        <div style={S.section}>
                            <label style={S.label}>列定义 ({activeColumns.length} 列{excludedCount > 0 ? `，已排除 ${excludedCount}` : ''})</label>
                            <div style={S.tableWrap}>
                                <table style={S.table}>
                                    <thead>
                                        <tr>
                                            <th style={{ ...S.th, minWidth: 60 }}>原始列名</th>
                                            <th style={{ ...S.th, minWidth: 60 }}>显示名</th>
                                            <th style={{ ...S.th, minWidth: 50 }}>类型</th>
                                            <th style={{ ...S.th, width: 28 }}></th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {columns.map((col, ci) => (
                                            <tr key={ci} style={col.excluded ? { opacity: 0.4, textDecoration: 'line-through' } : undefined}>
                                                <td style={S.td} title={col.originalName}>
                                                    {col.originalName}
                                                </td>
                                                <td style={{ ...S.td, padding: 0 }}>
                                                    <input
                                                        type="text"
                                                        style={S.smallInput}
                                                        value={col.displayName}
                                                        disabled={col.excluded}
                                                        onChange={(e) => updateColumn(ci, { displayName: e.target.value })}
                                                    />
                                                </td>
                                                <td style={{ ...S.td, padding: '2px 4px' }}>
                                                    <select
                                                        style={S.smallSelect}
                                                        value={col.type}
                                                        disabled={col.excluded}
                                                        onChange={(e) => updateColumn(ci, { type: e.target.value as ColType })}
                                                    >
                                                        {ALL_TYPES.map((t) => (
                                                            <option key={t} value={t}>{TYPE_LABELS[t]}</option>
                                                        ))}
                                                    </select>
                                                </td>
                                                <td style={{ ...S.td, textAlign: 'center', padding: 0 }}>
                                                    {col.excluded ? (
                                                        <button type="button" style={S.restoreBtn} onClick={() => updateColumn(ci, { excluded: false })} title="恢复">
                                                            &#8635;
                                                        </button>
                                                    ) : (
                                                        <button type="button" style={S.excludeBtn} onClick={() => updateColumn(ci, { excluded: true })} title="排除此列">
                                                            &times;
                                                        </button>
                                                    )}
                                                </td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    {/* Data preview */}
                    {previewRows.length > 0 && (
                        <div style={S.section}>
                            <label style={S.label}>数据预览 (前 {Math.min(10, allRows.length)} 行)</label>
                            <div style={S.tableWrap}>
                                <table style={S.table}>
                                    <thead>
                                        <tr>
                                            <th style={{ ...S.th, width: 24 }}>#</th>
                                            {activeColumns.map((col, ci) => (
                                                <th key={ci} style={S.th}>{col.displayName}</th>
                                            ))}
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {previewRows.map((row, ri) => (
                                            <tr key={ri}>
                                                <td style={{ ...S.td, color: '#64748b', fontSize: 10 }}>{ri + 1}</td>
                                                {row.map((cell, ci) => (
                                                    <td key={ci} style={S.td} title={cell != null ? String(cell) : ''}>
                                                        {cell != null ? String(cell) : ''}
                                                    </td>
                                                ))}
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    )}

                    {/* Statistics */}
                    <div style={S.stats}>
                        共 {allRows.length} 行 &middot; {activeColumns.length} 列 &middot; {formatFileSize(file.size)}
                    </div>

                    {/* Error */}
                    {error && <div style={S.error}>{error}</div>}

                    {/* Upload button */}
                    <button
                        type="button"
                        style={{
                            ...S.uploadBtn,
                            ...(uploading || activeColumns.length === 0 ? S.uploadBtnDisabled : {}),
                        }}
                        disabled={uploading || activeColumns.length === 0}
                        onClick={handleUpload}
                    >
                        {uploading ? '上传中...' : '上传并绑定'}
                    </button>
                </>
            )}
        </>
    );
}
