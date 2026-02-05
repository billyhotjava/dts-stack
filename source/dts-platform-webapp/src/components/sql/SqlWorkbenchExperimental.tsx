import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { toast } from "sonner";
import type { TableInfo, SqlResultPreview } from "@/api/sql-workbench";
import {
	cancelSql,
	getSqlStatus,
	listTables,
	submitSql,
} from "@/api/sql-workbench";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { Button } from "@/ui/button";
import { ScrollArea } from "@/ui/scroll-area";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/ui/select";
import { Input } from "@/ui/input";
import { cn } from "@/utils";

const DEFAULT_SQL = `-- 选择数据源，输入 SQL 语句
SELECT * FROM your_table LIMIT 100;`;

// 导出 CSV
const downloadCsv = (headers: string[], rows: Record<string, unknown>[], filename: string) => {
	const escapeCell = (val: unknown) => {
		if (val == null) return "";
		const str = String(val);
		if (str.includes(",") || str.includes('"') || str.includes("\n")) {
			return `"${str.replace(/"/g, '""')}"`;
		}
		return str;
	};
	const csvContent = [
		headers.map(escapeCell).join(","),
		...rows.map((row) => headers.map((h) => escapeCell(row[h])).join(",")),
	].join("\n");
	const blob = new Blob(["\ufeff" + csvContent], { type: "text/csv;charset=utf-8" });
	const url = URL.createObjectURL(blob);
	const link = document.createElement("a");
	link.href = url;
	link.download = filename;
	link.click();
	URL.revokeObjectURL(url);
};

// 复制到剪贴板
const copyToClipboard = (headers: string[], rows: Record<string, unknown>[]) => {
	const text = [
		headers.join("\t"),
		...rows.map((row) => headers.map((h) => row[h] ?? "").join("\t")),
	].join("\n");
	navigator.clipboard.writeText(text).then(() => {
		toast.success("已复制到剪贴板");
	});
};

// 按 schema 分组表
const groupTablesBySchema = (tables: TableInfo[]) => {
	const grouped: Record<string, TableInfo[]> = {};
	for (const table of tables) {
		const schema = table.schema || "public";
		if (!grouped[schema]) {
			grouped[schema] = [];
		}
		grouped[schema].push(table);
	}
	return grouped;
};

export const SqlWorkbenchExperimental = () => {
	const [sqlText, setSqlText] = useState(DEFAULT_SQL);
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [executionId, setExecutionId] = useState<string | null>(null);

	// 数据源
	const [dataSources, setDataSources] = useState<InfraDataSource[]>([]);
	const [selectedDatasourceId, setSelectedDatasourceId] = useState<string>("");
	const [datasourcesLoading, setDatasourcesLoading] = useState(true);

	// 表列表
	const [tables, setTables] = useState<TableInfo[]>([]);
	const [tablesLoading, setTablesLoading] = useState(false);
	const [tableSearch, setTableSearch] = useState("");
	const [expandedSchemas, setExpandedSchemas] = useState<Set<string>>(new Set());
	const [selectedTable, setSelectedTable] = useState<string | null>(null);

	// 结果
	const [activeTab, setActiveTab] = useState<"results" | "logs">("results");
	const [result, setResult] = useState<{
		preview: SqlResultPreview | null;
		elapsedMs: number | null;
		rowCount: number | null;
		status: string;
		errorMessage: string | null;
	} | null>(null);

	// 行数限制
	const [rowLimit, setRowLimit] = useState(1000);

	const textareaRef = useRef<HTMLTextAreaElement>(null);

	// 加载数据源
	useEffect(() => {
		setDatasourcesLoading(true);
		dataSourcesService
			.list()
			.then((sources) => {
				const activeSources = sources.filter((s) => s.status === "ACTIVE");
				setDataSources(activeSources);
				const pgSource = activeSources.find((s) => s.type?.toLowerCase() === "postgres");
				if (pgSource) {
					setSelectedDatasourceId(pgSource.id);
				} else if (activeSources.length > 0) {
					setSelectedDatasourceId(activeSources[0].id);
				}
			})
			.catch(() => toast.error("加载数据源失败"))
			.finally(() => setDatasourcesLoading(false));
	}, []);

	// 加载表列表
	useEffect(() => {
		if (!selectedDatasourceId) {
			setTables([]);
			return;
		}
		setTablesLoading(true);
		listTables(selectedDatasourceId)
			.then((data) => {
				setTables(data);
				// 自动展开第一个 schema
				const schemas = Object.keys(groupTablesBySchema(data));
				if (schemas.length > 0) {
					setExpandedSchemas(new Set([schemas[0]]));
				}
			})
			.catch(() => {
				setTables([]);
				toast.error("加载表列表失败");
			})
			.finally(() => setTablesLoading(false));
	}, [selectedDatasourceId]);

	const selectedDatasource = useMemo(
		() => dataSources.find((ds) => ds.id === selectedDatasourceId),
		[dataSources, selectedDatasourceId]
	);

	// 过滤和分组表
	const groupedTables = useMemo(() => {
		const filtered = tableSearch
			? tables.filter((t) => t.name.toLowerCase().includes(tableSearch.toLowerCase()))
			: tables;
		return groupTablesBySchema(filtered);
	}, [tables, tableSearch]);

	// 切换 schema 展开
	const toggleSchema = (schema: string) => {
		setExpandedSchemas((prev) => {
			const next = new Set(prev);
			if (next.has(schema)) {
				next.delete(schema);
			} else {
				next.add(schema);
			}
			return next;
		});
	};

	// 点击表名插入
	const handleTableClick = useCallback(
		(table: TableInfo) => {
			setSelectedTable(`${table.schema}.${table.name}`);
			const textarea = textareaRef.current;
			if (!textarea) return;

			const tableName = table.schema !== "public" ? `${table.schema}.${table.name}` : table.name;
			const start = textarea.selectionStart;
			const end = textarea.selectionEnd;
			const newText = sqlText.substring(0, start) + tableName + sqlText.substring(end);
			setSqlText(newText);

			setTimeout(() => {
				textarea.focus();
				const newPos = start + tableName.length;
				textarea.setSelectionRange(newPos, newPos);
			}, 0);

			toast.success(`已插入: ${tableName}`);
		},
		[sqlText]
	);

	// 执行查询
	const handleSubmit = async () => {
		if (!selectedDatasourceId) {
			toast.error("请先选择数据源");
			return;
		}
		if (!sqlText.trim()) {
			toast.error("请输入 SQL 语句");
			return;
		}

		setIsSubmitting(true);
		setResult(null);
		setActiveTab("results");

		try {
			const response = await submitSql({
				sqlText,
				datasource: selectedDatasourceId,
				fetchSize: rowLimit,
			});
			setExecutionId(response.executionId);

			const status = await getSqlStatus(response.executionId);
			setResult({
				preview: status.preview ?? null,
				elapsedMs: status.elapsedMs ?? null,
				rowCount: status.rows ?? null,
				status: status.status,
				errorMessage: status.errorMessage ?? null,
			});

			if (status.status === "SUCCESS") {
				toast.success(`查询成功，返回 ${status.rows ?? 0} 行`);
			} else if (status.status === "FAILED") {
				toast.error("查询失败");
				setActiveTab("logs");
			}
		} catch (error: unknown) {
			const err = error as { response?: { data?: { detail?: string } }; message?: string };
			const errorMsg = err?.response?.data?.detail || err?.message || "执行失败";
			setResult({
				preview: null,
				elapsedMs: null,
				rowCount: null,
				status: "FAILED",
				errorMessage: errorMsg,
			});
			toast.error(errorMsg);
			setActiveTab("logs");
		} finally {
			setIsSubmitting(false);
		}
	};

	// 取消查询
	const handleCancel = async () => {
		if (!executionId) return;
		try {
			await cancelSql(executionId);
			toast.success("已取消查询");
		} catch {
			toast.error("取消失败");
		}
	};

	const preview = result?.preview;
	const lineNumbers = sqlText.split("\n").map((_, i) => i + 1);

	return (
		<div className="h-full flex flex-col bg-background">
			{/* 主体 */}
			<div className="flex-1 flex overflow-hidden">
				{/* 左侧边栏 */}
				<aside className="w-72 border-r bg-card flex flex-col flex-shrink-0">
					{/* 数据源选择 */}
					<div className="p-4 border-b">
						<label className="block text-xs font-semibold text-muted-foreground uppercase mb-2">
							数据源
						</label>
						<Select
							value={selectedDatasourceId}
							onValueChange={setSelectedDatasourceId}
							disabled={datasourcesLoading}
						>
							<SelectTrigger className="w-full">
								<SelectValue placeholder={datasourcesLoading ? "加载中..." : "选择数据源"} />
							</SelectTrigger>
							<SelectContent>
								{dataSources.map((ds) => (
									<SelectItem key={ds.id} value={ds.id}>
										<div className="flex items-center gap-2">
											<span
												className={cn(
													"w-2 h-2 rounded-full",
													ds.status === "ACTIVE" ? "bg-green-500" : "bg-gray-400"
												)}
											/>
											<span>{ds.name}</span>
										</div>
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>

					{/* 搜索 */}
					<div className="p-4 border-b">
						<div className="relative">
							<Input
								placeholder="搜索表..."
								value={tableSearch}
								onChange={(e) => setTableSearch(e.target.value)}
								className="pl-8"
							/>
							<svg
								className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground"
								fill="none"
								stroke="currentColor"
								viewBox="0 0 24 24"
							>
								<path
									strokeLinecap="round"
									strokeLinejoin="round"
									strokeWidth={2}
									d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
								/>
							</svg>
						</div>
					</div>

					{/* 表列表 */}
					<ScrollArea className="flex-1">
						<div className="p-2">
							{tablesLoading ? (
								<div className="text-sm text-muted-foreground p-4 text-center">加载中...</div>
							) : !selectedDatasourceId ? (
								<div className="text-sm text-muted-foreground p-4 text-center">请选择数据源</div>
							) : Object.keys(groupedTables).length === 0 ? (
								<div className="text-sm text-muted-foreground p-4 text-center">
									{tableSearch ? "无匹配的表" : "暂无表"}
								</div>
							) : (
								<ul className="text-sm">
									{Object.entries(groupedTables).map(([schema, schemaTables]) => (
										<li key={schema} className="mb-2">
											<div
												className="flex items-center font-semibold cursor-pointer hover:bg-muted p-1.5 rounded"
												onClick={() => toggleSchema(schema)}
											>
												<svg
													className={cn(
														"w-3 h-3 mr-2 transition-transform text-muted-foreground",
														expandedSchemas.has(schema) && "rotate-90"
													)}
													fill="currentColor"
													viewBox="0 0 20 20"
												>
													<path d="M6 6L14 10L6 14V6Z" />
												</svg>
												<svg
													className="w-4 h-4 mr-2 text-blue-500"
													fill="none"
													stroke="currentColor"
													viewBox="0 0 24 24"
												>
													<path
														strokeLinecap="round"
														strokeLinejoin="round"
														strokeWidth={2}
														d="M4 7v10c0 2 1 3 3 3h10c2 0 3-1 3-3V7c0-2-1-3-3-3H7c-2 0-3 1-3 3z"
													/>
												</svg>
												{schema}
												<span className="ml-auto text-xs text-muted-foreground">
													{schemaTables.length}
												</span>
											</div>
											{expandedSchemas.has(schema) && (
												<ul className="ml-6 mt-1 space-y-0.5">
													{schemaTables.map((table) => (
														<li
															key={`${table.schema}.${table.name}`}
															className={cn(
																"flex items-center p-1.5 rounded cursor-pointer group",
																selectedTable === `${table.schema}.${table.name}`
																	? "bg-blue-50 text-blue-600 dark:bg-blue-950"
																	: "hover:bg-muted"
															)}
															onClick={() => handleTableClick(table)}
															title="点击插入表名到 SQL"
														>
															<svg
																className={cn(
																	"w-4 h-4 mr-2",
																	table.type === "VIEW"
																		? "text-purple-500"
																		: "text-muted-foreground"
																)}
																fill="none"
																stroke="currentColor"
																viewBox="0 0 24 24"
															>
																<path
																	strokeLinecap="round"
																	strokeLinejoin="round"
																	strokeWidth={2}
																	d="M3 10h18M3 14h18m-9-4v8m-7 0h14a2 2 0 002-2V8a2 2 0 00-2-2H5a2 2 0 00-2 2v8a2 2 0 002 2z"
																/>
															</svg>
															<span className="truncate">{table.name}</span>
															{table.type === "VIEW" && (
																<span className="ml-auto text-xs text-purple-500">VIEW</span>
															)}
														</li>
													))}
												</ul>
											)}
										</li>
									))}
								</ul>
							)}
						</div>
					</ScrollArea>
				</aside>

				{/* 主区域 */}
				<main className="flex-1 flex flex-col min-w-0">
					{/* 工具栏 */}
					<div className="h-12 border-b flex items-center justify-between px-4 bg-muted/30">
						<div className="flex items-center gap-2">
							<Button
								onClick={handleSubmit}
								disabled={isSubmitting || !selectedDatasourceId}
								size="sm"
								className="gap-2"
							>
								{isSubmitting ? (
									<>
										<svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
											<circle
												className="opacity-25"
												cx="12"
												cy="12"
												r="10"
												stroke="currentColor"
												strokeWidth="4"
												fill="none"
											/>
											<path
												className="opacity-75"
												fill="currentColor"
												d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
											/>
										</svg>
										执行中...
									</>
								) : (
									<>
										<svg className="h-4 w-4" fill="currentColor" viewBox="0 0 20 20">
											<path d="M6.3 2.841A1.5 1.5 0 004 4.11V15.89a1.5 1.5 0 002.3 1.269l9.344-5.89a1.5 1.5 0 000-2.538L6.3 2.84z" />
										</svg>
										运行查询
									</>
								)}
							</Button>
							{isSubmitting && (
								<Button variant="outline" size="sm" onClick={handleCancel}>
									取消
								</Button>
							)}
						</div>
						<div className="flex items-center gap-4 text-xs text-muted-foreground">
							<span className="flex items-center gap-1">
								限制行数:
								<Input
									type="number"
									value={rowLimit}
									onChange={(e) => setRowLimit(Number(e.target.value) || 1000)}
									className="w-20 h-7 text-xs"
								/>
							</span>
							{selectedDatasource && (
								<span>
									数据源:{" "}
									<span className="font-mono bg-muted px-1.5 py-0.5 rounded">
										{selectedDatasource.name}
									</span>
								</span>
							)}
						</div>
					</div>

					{/* SQL 编辑器 */}
					<div className="flex-1 relative min-h-[200px]">
						<div className="absolute inset-0 flex">
							{/* 行号 */}
							<div className="w-12 bg-muted/50 border-r text-right pr-3 pt-4 text-xs font-mono text-muted-foreground select-none overflow-hidden">
								{lineNumbers.map((num) => (
									<div key={num} className="leading-6">
										{num}
									</div>
								))}
							</div>
							{/* 编辑区 */}
							<textarea
								ref={textareaRef}
								spellCheck={false}
								className="flex-1 p-4 font-mono text-sm leading-6 bg-background text-foreground focus:outline-none resize-none"
								value={sqlText}
								onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setSqlText(e.target.value)}
								placeholder="输入 SQL 语句..."
							/>
						</div>
					</div>

					{/* 结果区 */}
					<div className="h-[45%] min-h-[200px] border-t flex flex-col">
						{/* 选项卡 */}
						<div className="flex border-b bg-muted/30">
							<button
								className={cn(
									"px-6 py-2 text-sm font-medium transition-colors",
									activeTab === "results"
										? "bg-background border-t-2 border-primary text-primary"
										: "text-muted-foreground hover:text-foreground"
								)}
								onClick={() => setActiveTab("results")}
							>
								查询结果
							</button>
							<button
								className={cn(
									"px-6 py-2 text-sm font-medium transition-colors",
									activeTab === "logs"
										? "bg-background border-t-2 border-primary text-primary"
										: "text-muted-foreground hover:text-foreground"
								)}
								onClick={() => setActiveTab("logs")}
							>
								执行日志
							</button>
						</div>

						{/* 结果工具栏 */}
						{result && activeTab === "results" && (
							<div className="px-4 py-2 border-b flex justify-between items-center text-xs">
								<div className="text-muted-foreground flex gap-4">
									{result.elapsedMs != null && (
										<span>
											耗时: <span className="text-green-600 font-semibold">{result.elapsedMs}ms</span>
										</span>
									)}
									{result.rowCount != null && (
										<span>
											结果行数: <span className="font-semibold">{result.rowCount} 行</span>
										</span>
									)}
									<span>
										状态:{" "}
										<span
											className={cn(
												"font-semibold",
												result.status === "SUCCESS"
													? "text-green-600"
													: result.status === "FAILED"
														? "text-red-600"
														: "text-yellow-600"
											)}
										>
											{result.status === "SUCCESS"
												? "成功"
												: result.status === "FAILED"
													? "失败"
													: result.status}
										</span>
									</span>
								</div>
								{preview?.headers?.length ? (
									<div className="flex gap-3">
										<button
											className="text-muted-foreground hover:text-primary flex items-center gap-1"
											onClick={() =>
												downloadCsv(
													preview.headers,
													preview.rows,
													`query-${new Date().toISOString().slice(0, 10)}.csv`
												)
											}
										>
											<svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
												<path
													strokeLinecap="round"
													strokeLinejoin="round"
													strokeWidth={2}
													d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
												/>
											</svg>
											下载 CSV
										</button>
										<button
											className="text-muted-foreground hover:text-primary flex items-center gap-1"
											onClick={() => copyToClipboard(preview.headers, preview.rows)}
										>
											<svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
												<path
													strokeLinecap="round"
													strokeLinejoin="round"
													strokeWidth={2}
													d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z"
												/>
											</svg>
											复制
										</button>
									</div>
								) : null}
							</div>
						)}

						{/* 内容 */}
						<div className="flex-1 overflow-auto">
							{activeTab === "results" ? (
								!result ? (
									<div className="h-full flex items-center justify-center text-muted-foreground text-sm">
										选择数据源，输入 SQL 语句，点击"运行查询"
									</div>
								) : preview?.headers?.length ? (
									<table className="w-full text-sm text-left border-collapse">
										<thead className="bg-muted/50 sticky top-0">
											<tr>
												<th className="px-4 py-2 font-mono text-muted-foreground border-r w-12 text-center">
													#
												</th>
												{preview.headers.map((header) => (
													<th
														key={header}
														className="px-4 py-2 font-semibold border-r whitespace-nowrap"
													>
														{header}
													</th>
												))}
											</tr>
										</thead>
										<tbody className="divide-y font-mono text-xs">
											{preview.rows.map((row, idx) => (
												<tr key={idx} className="hover:bg-muted/30 transition-colors">
													<td className="px-4 py-2 text-muted-foreground bg-muted/30 border-r text-center">
														{idx + 1}
													</td>
													{preview.headers.map((header) => (
														<td key={header} className="px-4 py-2 border-r">
															{row[header] == null ? (
																<span className="text-muted-foreground italic">NULL</span>
															) : (
																String(row[header])
															)}
														</td>
													))}
												</tr>
											))}
										</tbody>
									</table>
								) : result.status === "SUCCESS" ? (
									<div className="h-full flex items-center justify-center text-muted-foreground text-sm">
										查询成功，无返回数据
									</div>
								) : null
							) : (
								<div className="p-4 font-mono text-xs">
									{result?.errorMessage ? (
										<div className="p-4 rounded bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800">
											<div className="font-semibold text-red-600 mb-2">执行错误</div>
											<pre className="whitespace-pre-wrap text-red-700 dark:text-red-400">
												{result.errorMessage}
											</pre>
										</div>
									) : result ? (
										<div className="text-green-600">查询执行成功</div>
									) : (
										<div className="text-muted-foreground">暂无日志</div>
									)}
								</div>
							)}
						</div>
					</div>
				</main>
			</div>

			{/* 状态栏 */}
			<footer className="h-6 bg-muted border-t px-4 flex justify-between items-center text-[10px] text-muted-foreground uppercase tracking-wider">
				<div className="flex gap-4">
					{selectedDatasource && <span>数据源: {selectedDatasource.name}</span>}
					<span className="text-green-500 flex items-center gap-1">
						<span className="w-1.5 h-1.5 rounded-full bg-green-500" />
						已连接
					</span>
				</div>
				<div>UTF-8 | SQL</div>
			</footer>
		</div>
	);
};
