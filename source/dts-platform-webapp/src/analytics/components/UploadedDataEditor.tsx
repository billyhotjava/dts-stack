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
				<div className="flex items-start gap-2 px-3 py-2 mb-2 rounded-md text-xs" style={{ background: '#fffbe6', border: '1px solid #ffe58f', color: '#d46b08' }}>
					<span>⚠</span>
					<span>非密模块禁止上传涉密数据</span>
				</div>
				<div
					className={`border-2 border-dashed rounded-lg py-5 px-3 text-center cursor-pointer text-text-muted text-xs leading-relaxed transition-colors ${dragOver ? 'border-brand bg-brand/5' : 'border-border-default'}`}
					onDrop={handleDrop}
					onDragOver={handleDragOver}
					onDragLeave={handleDragLeave}
					onClick={() => fileInputRef.current?.click()}
				>
					<div className="text-xl mb-1 opacity-60">[ + ]</div>
					<div>拖拽文件到此处 或 点击选择</div>
					<div className="text-[10px] text-text-muted mt-1">
						支持 .xlsx / .xls / .csv，最大 50MB
					</div>
				</div>
				<input
					ref={fileInputRef}
					type="file"
					accept=".xlsx,.xls,.csv"
					className="hidden"
					onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f); }}
				/>
				{error && <div className="text-error text-[11px] mt-1.5">{error}</div>}
			</>
		);
	}

	// File loaded — show editor
	return (
		<>
			{/* File info bar */}
			<div className="flex items-center justify-between px-2.5 py-1.5 bg-surface-muted rounded-sm text-[11px] text-text-primary mt-2">
				<span className="overflow-hidden text-ellipsis whitespace-nowrap mr-2">
					{file.name} ({formatFileSize(file.size)})
				</span>
				<button type="button" className="bg-transparent border-0 text-text-muted cursor-pointer text-sm p-0 leading-none" onClick={clearFile} title="清除文件">&times;</button>
			</div>

			{/* Hidden file input for re-upload */}
			<input
				ref={fileInputRef}
				type="file"
				accept=".xlsx,.xls,.csv"
				className="hidden"
				onChange={(e) => { const f = e.target.files?.[0]; if (f) handleFile(f); }}
			/>

			{parsing && (
				<div className="text-center py-5 text-text-muted text-xs">
					解析中...
				</div>
			)}

			{!parsing && (
				<>
					{/* Sheet selector (Excel only) */}
					{sheetNames.length > 1 && (
						<div className="mt-2.5">
							<label className="block text-[11px] text-text-muted mb-1">工作表</label>
							<select
								className="w-full px-2.5 py-1.5 text-xs border border-border-default rounded-sm bg-surface-card text-text-primary outline-none"
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
					<div className="mt-2.5">
						<label className="block text-[11px] text-text-muted mb-1">表头行号</label>
						<input
							type="number"
							min={1}
							className="w-20 px-2.5 py-1.5 text-xs border border-border-default rounded-sm bg-surface-card text-text-primary outline-none"
							value={headerRow}
							onChange={(e) => {
								const v = parseInt(e.target.value, 10);
								if (!isNaN(v) && v >= 1) handleHeaderRowChange(v);
							}}
						/>
					</div>

					{/* Dataset name */}
					<div className="mt-2.5">
						<label className="block text-[11px] text-text-muted mb-1">数据集名称</label>
						<input
							type="text"
							className="w-full px-2.5 py-1.5 text-xs border border-border-default rounded-sm bg-surface-card text-text-primary outline-none"
							value={datasetName}
							onChange={(e) => setDatasetName(e.target.value)}
							placeholder="输入数据集名称"
						/>
					</div>

					{/* Column editor */}
					{columns.length > 0 && (
						<div className="mt-2.5">
							<label className="block text-[11px] text-text-muted mb-1">列定义 ({activeColumns.length} 列{excludedCount > 0 ? `，已排除 ${excludedCount}` : ''})</label>
							<div className="border border-border-default rounded-sm overflow-auto max-h-[260px]">
								<table className="w-full border-collapse text-[11px]">
									<thead>
										<tr>
											<th className="px-1.5 py-1 bg-surface-muted border-b border-border-default text-left font-semibold text-text-muted whitespace-nowrap min-w-[60px]">字段名</th>
											<th className="px-1.5 py-1 bg-surface-muted border-b border-border-default text-left font-semibold text-text-muted whitespace-nowrap min-w-[60px]">显示名</th>
											<th className="px-1.5 py-1 bg-surface-muted border-b border-border-default text-left font-semibold text-text-muted whitespace-nowrap min-w-[50px]">类型</th>
											<th className="px-1.5 py-1 bg-surface-muted border-b border-border-default text-left font-semibold text-text-muted whitespace-nowrap w-7"></th>
										</tr>
									</thead>
									<tbody>
										{columns.map((col, ci) => (
											<tr key={ci} style={col.excluded ? { opacity: 0.4, textDecoration: 'line-through' } : undefined}>
												<td className="border-b border-border-default/50 p-0">
													<input
														type="text"
														className="w-full border-0 bg-transparent px-1.5 py-1 text-[11px] text-text-primary outline-none"
														value={col.originalName}
														disabled={col.excluded}
														onChange={(e) => updateColumn(ci, { originalName: e.target.value })}
														title="修改字段名（实际数据库列名）"
													/>
												</td>
												<td className="border-b border-border-default/50 p-0">
													<input
														type="text"
														className="w-full border-0 bg-transparent px-1 py-0.5 text-[11px] text-text-primary outline-none"
														value={col.displayName}
														disabled={col.excluded}
														onChange={(e) => updateColumn(ci, { displayName: e.target.value })}
													/>
												</td>
												<td className="px-1 py-0.5 border-b border-border-default/50">
													<select
														className="border-0 bg-transparent px-0.5 py-0.5 text-[11px] text-text-primary outline-none cursor-pointer"
														value={col.type}
														disabled={col.excluded}
														onChange={(e) => updateColumn(ci, { type: e.target.value as ColType })}
													>
														{ALL_TYPES.map((t) => (
															<option key={t} value={t}>{TYPE_LABELS[t]}</option>
														))}
													</select>
												</td>
												<td className="border-b border-border-default/50 text-center p-0">
													{col.excluded ? (
														<button type="button" className="bg-transparent border-0 text-brand cursor-pointer text-[11px] px-1 leading-none" onClick={() => updateColumn(ci, { excluded: false })} title="恢复">
															&#8635;
														</button>
													) : (
														<button type="button" className="bg-transparent border-0 text-text-muted cursor-pointer text-[13px] px-1 leading-none" onClick={() => updateColumn(ci, { excluded: true })} title="排除此列">
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
						<div className="mt-2.5">
							<label className="block text-[11px] text-text-muted mb-1">数据预览 (前 {Math.min(10, allRows.length)} 行)</label>
							<div className="border border-border-default rounded-sm overflow-auto max-h-[260px]">
								<table className="w-full border-collapse text-[11px]">
									<thead>
										<tr>
											<th className="px-1.5 py-1 bg-surface-muted border-b border-border-default text-left font-semibold text-text-muted whitespace-nowrap w-6">#</th>
											{activeColumns.map((col, ci) => (
												<th key={ci} className="px-1.5 py-1 bg-surface-muted border-b border-border-default text-left font-semibold text-text-muted whitespace-nowrap">{col.displayName}</th>
											))}
										</tr>
									</thead>
									<tbody>
										{previewRows.map((row, ri) => (
											<tr key={ri}>
												<td className="px-1.5 py-1 border-b border-border-default/50 text-text-muted text-[10px]">{ri + 1}</td>
												{row.map((cell, ci) => (
													<td key={ci} className="px-1.5 py-1 border-b border-border-default/50 text-text-primary whitespace-nowrap max-w-[120px] overflow-hidden text-ellipsis" title={cell != null ? String(cell) : ''}>
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
					<div className="text-[10px] text-text-muted py-1 leading-normal">
						共 {allRows.length} 行 &middot; {activeColumns.length} 列 &middot; {formatFileSize(file.size)}
					</div>

					{/* Error */}
					{error && <div className="text-error text-[11px] mt-1.5">{error}</div>}

					{/* Upload button */}
					<button
						type="button"
						className={`w-full py-2 text-[13px] font-semibold border-0 rounded-sm bg-brand text-white mt-2.5 ${uploading || activeColumns.length === 0 ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
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
