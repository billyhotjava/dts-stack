import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { toast } from "sonner";
import type { TableInfo, SqlResultPreview, SavedQueryResponse } from "@/api/sql-workbench";
import {
	auditCopy,
	cancelSql,
	createQueryDatasetFromExecution,
	deleteSavedQuery,
	getSqlStatus,
	listSavedQueries,
	listTables,
	saveQuery,
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
import {
	Dialog,
	DialogContent,
	DialogDescription,
	DialogFooter,
	DialogHeader,
	DialogTitle,
} from "@/ui/dialog";
import { cn } from "@/utils";
import { writeTextToClipboard } from "@/utils/clipboard";

const DEFAULT_SQL = `SELECT * FROM your_table LIMIT 100;`;

// SQL 格式化（简单实现）
const formatSql = (sql: string): string => {
	const keywords = [
		"SELECT", "FROM", "WHERE", "AND", "OR", "ORDER BY", "GROUP BY",
		"HAVING", "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "OUTER JOIN",
		"ON", "AS", "LIMIT", "OFFSET", "INSERT INTO", "VALUES", "UPDATE", "SET",
		"DELETE FROM", "CREATE TABLE", "ALTER TABLE", "DROP TABLE", "UNION", "UNION ALL"
	];

	let formatted = sql.trim();

	// 先规范化空白
	formatted = formatted.replace(/\s+/g, " ");

	// 在关键字前换行
	for (const keyword of keywords) {
		const regex = new RegExp(`\\s+(${keyword})\\s+`, "gi");
		formatted = formatted.replace(regex, `\n${keyword} `);
	}

	// SELECT 后的字段换行
	formatted = formatted.replace(/SELECT\s+/gi, "SELECT\n    ");
	formatted = formatted.replace(/,\s*/g, ",\n    ");

	// 清理开头
	formatted = formatted.replace(/^\n+/, "");

	return formatted;
};

// 复制到剪贴板
const copyToClipboard = async (
	headers: string[],
	rows: Record<string, unknown>[],
	executionId: string | null
) => {
	const text = [
		headers.join("\t"),
		...rows.map((row) => headers.map((h) => row[h] ?? "").join("\t")),
	].join("\n");

	try {
		const copied = await writeTextToClipboard(text);
		if (!copied) {
			throw new Error("copy failed");
		}
		// 记录审计日志
		await auditCopy({
			rowCount: rows.length,
			columnCount: headers.length,
			executionId: executionId || undefined,
		});
		toast.success("已复制到剪贴板");
	} catch {
		toast.error("复制失败");
	}
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

// 格式化时间
const formatTime = (isoString: string) => {
	const date = new Date(isoString);
	return date.toLocaleString("zh-CN", {
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
	});
};

export const SqlWorkbenchExperimental = () => {
	const [sqlText, setSqlText] = useState(DEFAULT_SQL);
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [savingDataset, setSavingDataset] = useState(false);
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

	// 保存的查询
	const [savedQueries, setSavedQueries] = useState<SavedQueryResponse[]>([]);
	const [savedQueriesLoading, setSavedQueriesLoading] = useState(false);
	const [showSaveDialog, setShowSaveDialog] = useState(false);
	const [saveQueryName, setSaveQueryName] = useState("");
	const [saveQueryDesc, setSaveQueryDesc] = useState("");
	const [showSavedQueries, setShowSavedQueries] = useState(false);

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

	// 加载保存的查询
	const loadSavedQueries = useCallback(() => {
		setSavedQueriesLoading(true);
		listSavedQueries()
			.then(setSavedQueries)
			.catch(() => toast.error("加载保存的查询失败"))
			.finally(() => setSavedQueriesLoading(false));
	}, []);

	useEffect(() => {
		loadSavedQueries();
	}, [loadSavedQueries]);

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
				const schemas = Object.keys(groupTablesBySchema(data));
				if (schemas.length > 0) {
					setExpandedSchemas(new Set([schemas[0]]));
				}
			})
			.catch(() => {
				setTables([]);
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

	// 格式化 SQL
	const handleFormat = () => {
		const formatted = formatSql(sqlText);
		setSqlText(formatted);
		toast.success("SQL 已格式化");
	};

	// 保存查询
	const handleSave = async () => {
		if (!saveQueryName.trim()) {
			toast.error("请输入查询名称");
			return;
		}
		try {
			await saveQuery({
				name: saveQueryName.trim(),
				description: saveQueryDesc.trim() || undefined,
				sqlText,
				datasourceId: selectedDatasourceId || undefined,
				datasourceName: selectedDatasource?.name,
			});
			toast.success("查询已保存");
			setShowSaveDialog(false);
			setSaveQueryName("");
			setSaveQueryDesc("");
			loadSavedQueries();
		} catch {
			toast.error("保存失败");
		}
	};

	const handleSaveAsDataset = async () => {
		if (!executionId) {
			toast.error("请先执行查询并生成结果");
			return;
		}
		const defaultName = "query_dataset_" + executionId.substring(0, 8);
		const name = window.prompt("请输入数据集名称", defaultName);
		if (!name || !name.trim()) return;
		setSavingDataset(true);
		try {
			const created = await createQueryDatasetFromExecution(executionId, {
				name: name.trim(),
				refreshStrategy: "MANUAL",
			});
			toast.success("已沉淀数据集: " + created.name);
		} catch (error: unknown) {
			const err = error as { response?: { data?: { message?: string } }; message?: string };
			toast.error(err?.response?.data?.message || err?.message || "沉淀数据集失败");
		} finally {
			setSavingDataset(false);
		}
	};

	// 加载保存的查询
	const handleLoadSavedQuery = (query: SavedQueryResponse) => {
		setSqlText(query.sqlText);
		if (query.datasourceId && dataSources.some((ds) => ds.id === query.datasourceId)) {
			setSelectedDatasourceId(query.datasourceId);
		}
		setShowSavedQueries(false);
		toast.success(`已加载: ${query.name}`);
	};

	// 删除保存的查询
	const handleDeleteSavedQuery = async (id: string, e: React.MouseEvent) => {
		e.stopPropagation();
		if (!confirm("确定要删除这个查询吗？")) return;
		try {
			await deleteSavedQuery(id);
			toast.success("已删除");
			loadSavedQueries();
		} catch {
			toast.error("删除失败");
		}
	};

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
				<aside className="w-64 border-r bg-card flex flex-col flex-shrink-0">
					{/* 数据源选择 */}
					<div className="p-3 border-b">
						<label className="block text-xs font-medium text-muted-foreground mb-1.5">
							数据源
						</label>
						<Select
							value={selectedDatasourceId}
							onValueChange={setSelectedDatasourceId}
							disabled={datasourcesLoading}
						>
							<SelectTrigger className="w-full h-8 text-sm">
								<SelectValue placeholder={datasourcesLoading ? "加载中..." : "选择数据源"} />
							</SelectTrigger>
							<SelectContent>
								{dataSources.map((ds) => (
									<SelectItem key={ds.id} value={ds.id}>
										<div className="flex items-center gap-2">
											<span
												className={cn(
													"w-1.5 h-1.5 rounded-full",
													ds.status === "ACTIVE" ? "bg-green-500" : "bg-gray-400"
												)}
											/>
											<span className="truncate">{ds.name}</span>
										</div>
									</SelectItem>
								))}
							</SelectContent>
						</Select>
					</div>

					{/* 搜索 */}
					<div className="p-3 border-b">
						<Input
							placeholder="搜索表..."
							value={tableSearch}
							onChange={(e) => setTableSearch(e.target.value)}
							className="h-8 text-sm"
						/>
					</div>

					{/* 表列表 */}
					<ScrollArea className="flex-1">
						<div className="p-2">
							{tablesLoading ? (
								<div className="text-xs text-muted-foreground p-3 text-center">加载中...</div>
							) : !selectedDatasourceId ? (
								<div className="text-xs text-muted-foreground p-3 text-center">请选择数据源</div>
							) : Object.keys(groupedTables).length === 0 ? (
								<div className="text-xs text-muted-foreground p-3 text-center">
									{tableSearch ? "无匹配" : "暂无表"}
								</div>
							) : (
								<ul className="text-xs">
									{Object.entries(groupedTables).map(([schema, schemaTables]) => (
										<li key={schema} className="mb-1">
											<div
												className="flex items-center font-medium cursor-pointer hover:bg-muted p-1 rounded"
												onClick={() => toggleSchema(schema)}
											>
												<svg
													className={cn(
														"w-3 h-3 mr-1 transition-transform text-muted-foreground",
														expandedSchemas.has(schema) && "rotate-90"
													)}
													fill="currentColor"
													viewBox="0 0 20 20"
												>
													<path d="M6 6L14 10L6 14V6Z" />
												</svg>
												<span className="truncate">{schema}</span>
												<span className="ml-auto text-muted-foreground">{schemaTables.length}</span>
											</div>
											{expandedSchemas.has(schema) && (
												<ul className="ml-4 mt-0.5 space-y-px">
													{schemaTables.map((table) => (
														<li
															key={`${table.schema}.${table.name}`}
															className={cn(
																"flex items-center p-1 rounded cursor-pointer",
																selectedTable === `${table.schema}.${table.name}`
																	? "bg-primary/10 text-primary"
																	: "hover:bg-muted"
															)}
															onClick={() => handleTableClick(table)}
															title="点击插入表名"
														>
															<span className="truncate">{table.name}</span>
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

					{/* 保存的查询 */}
					<div className="border-t">
						<button
							className="w-full p-2 text-xs text-left hover:bg-muted flex items-center justify-between"
							onClick={() => setShowSavedQueries(!showSavedQueries)}
						>
							<span className="font-medium">保存的查询</span>
							<span className="text-muted-foreground">{savedQueries.length}</span>
						</button>
						{showSavedQueries && (
							<ScrollArea className="h-32 border-t">
								<div className="p-1">
									{savedQueriesLoading ? (
										<div className="text-xs text-muted-foreground p-2 text-center">加载中...</div>
									) : savedQueries.length === 0 ? (
										<div className="text-xs text-muted-foreground p-2 text-center">暂无保存的查询</div>
									) : (
										savedQueries.map((q) => (
											<div
												key={q.id}
												className="p-1.5 rounded hover:bg-muted cursor-pointer text-xs group flex items-center justify-between"
												onClick={() => handleLoadSavedQuery(q)}
											>
												<div className="min-w-0 flex-1">
													<div className="font-medium truncate">{q.name}</div>
													<div className="text-muted-foreground truncate">
														{formatTime(q.updatedAt)} · {q.createdBy}
													</div>
												</div>
												<button
													className="opacity-0 group-hover:opacity-100 text-muted-foreground hover:text-destructive p-1"
													onClick={(e) => handleDeleteSavedQuery(q.id, e)}
												>
													<svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
														<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
													</svg>
												</button>
											</div>
										))
									)}
								</div>
							</ScrollArea>
						)}
					</div>
				</aside>

				{/* 主区域 */}
				<main className="flex-1 flex flex-col min-w-0">
					{/* 工具栏 */}
					<div className="h-10 border-b flex items-center justify-between px-3 bg-muted/30">
						<div className="flex items-center gap-1.5">
							<Button
								onClick={handleSubmit}
								disabled={isSubmitting || !selectedDatasourceId}
								size="sm"
								className="h-7 gap-1.5 text-xs"
							>
								{isSubmitting ? (
									<>
										<svg className="animate-spin h-3 w-3" viewBox="0 0 24 24">
											<circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
											<path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
										</svg>
										执行中
									</>
								) : (
									<>
										<svg className="h-3 w-3" fill="currentColor" viewBox="0 0 20 20">
											<path d="M6.3 2.841A1.5 1.5 0 004 4.11V15.89a1.5 1.5 0 002.3 1.269l9.344-5.89a1.5 1.5 0 000-2.538L6.3 2.84z" />
										</svg>
										运行
									</>
								)}
							</Button>
							{isSubmitting && (
								<Button variant="outline" size="sm" className="h-7 text-xs" onClick={handleCancel}>
									取消
								</Button>
							)}
							<Button variant="outline" size="sm" className="h-7 text-xs" onClick={handleFormat}>
								格式化
							</Button>
							<Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setShowSaveDialog(true)}>
								保存
							</Button>
							<Button
								variant="outline"
								size="sm"
								className="h-7 text-xs"
								onClick={handleSaveAsDataset}
								disabled={!executionId || savingDataset}
							>
								{savingDataset ? "沉淀中" : "沉淀为数据集"}
							</Button>
						</div>
						<div className="flex items-center gap-3 text-xs text-muted-foreground">
							<span className="flex items-center gap-1">
								行数限制:
								<Input
									type="number"
									value={rowLimit}
									onChange={(e) => setRowLimit(Number(e.target.value) || 1000)}
									className="w-16 h-6 text-xs"
								/>
							</span>
						</div>
					</div>

					{/* SQL 编辑器 - 减小高度 */}
					<div className="h-64 min-h-[240px] relative border-b">
						<div className="absolute inset-0 flex">
							<div className="w-8 bg-muted/50 border-r text-right pr-2 pt-2 text-xs font-mono text-muted-foreground select-none overflow-hidden">
								{lineNumbers.map((num) => (
									<div key={num} className="leading-5">{num}</div>
								))}
							</div>
							<textarea
								ref={textareaRef}
								spellCheck={false}
								className="flex-1 p-2 font-mono text-sm leading-5 bg-background text-foreground focus:outline-none resize-none"
								value={sqlText}
								onChange={(e: ChangeEvent<HTMLTextAreaElement>) => setSqlText(e.target.value)}
								placeholder="输入 SQL 语句..."
							/>
						</div>
					</div>

					{/* 结果区 */}
					<div className="flex-1 flex flex-col min-h-0">
						{/* 选项卡 */}
						<div className="flex border-b bg-muted/30">
							<button
								className={cn(
									"px-4 py-1.5 text-xs font-medium transition-colors",
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
									"px-4 py-1.5 text-xs font-medium transition-colors",
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
							<div className="px-3 py-1.5 border-b flex justify-between items-center text-xs">
								<div className="text-muted-foreground flex gap-3">
									{result.elapsedMs != null && (
										<span>耗时: <span className="text-green-600 font-medium">{result.elapsedMs}ms</span></span>
									)}
									{result.rowCount != null && (
										<span>行数: <span className="font-medium">{result.rowCount}</span></span>
									)}
									<span>
										状态:{" "}
										<span className={cn(
											"font-medium",
											result.status === "SUCCESS" ? "text-green-600" : result.status === "FAILED" ? "text-red-600" : "text-yellow-600"
										)}>
											{result.status === "SUCCESS" ? "成功" : result.status === "FAILED" ? "失败" : result.status}
										</span>
									</span>
								</div>
								{preview?.headers?.length ? (
									<button
										className="text-muted-foreground hover:text-primary flex items-center gap-1"
										onClick={() => copyToClipboard(preview.headers, preview.rows, executionId)}
									>
										<svg className="h-3.5 w-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
											<path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 16H6a2 2 0 01-2-2V6a2 2 0 012-2h8a2 2 0 012 2v2m-6 12h8a2 2 0 002-2v-8a2 2 0 00-2-2h-8a2 2 0 00-2 2v8a2 2 0 002 2z" />
										</svg>
										复制
									</button>
								) : null}
							</div>
						)}

						{/* 内容 */}
						<ScrollArea className="flex-1">
							{activeTab === "results" ? (
								!result ? (
									<div className="h-full flex items-center justify-center text-muted-foreground text-sm p-8">
										选择数据源，输入 SQL 语句，点击"运行"
									</div>
								) : preview?.headers?.length ? (
									<table className="w-full text-xs text-left border-collapse">
										<thead className="bg-muted/50 sticky top-0">
											<tr>
												<th className="px-2 py-1.5 font-mono text-muted-foreground border-r w-10 text-center">#</th>
												{preview.headers.map((header) => (
													<th key={header} className="px-2 py-1.5 font-semibold border-r whitespace-nowrap">{header}</th>
												))}
											</tr>
										</thead>
										<tbody className="divide-y font-mono">
											{preview.rows.map((row, idx) => (
												<tr key={idx} className="hover:bg-muted/30">
													<td className="px-2 py-1 text-muted-foreground bg-muted/30 border-r text-center">{idx + 1}</td>
													{preview.headers.map((header) => (
														<td key={header} className="px-2 py-1 border-r">
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
								<div className="p-3 font-mono text-xs">
									{result?.errorMessage ? (
										<div className="p-3 rounded bg-red-50 dark:bg-red-950 border border-red-200 dark:border-red-800">
											<div className="font-semibold text-red-600 mb-1">执行错误</div>
											<pre className="whitespace-pre-wrap text-red-700 dark:text-red-400">{result.errorMessage}</pre>
										</div>
									) : result ? (
										<div className="text-green-600">查询执行成功</div>
									) : (
										<div className="text-muted-foreground">暂无日志</div>
									)}
								</div>
							)}
						</ScrollArea>
					</div>
				</main>
			</div>

			{/* 状态栏 */}
			<footer className="h-5 bg-muted border-t px-3 flex justify-between items-center text-[10px] text-muted-foreground">
				<div className="flex gap-3">
					{selectedDatasource && <span>{selectedDatasource.name}</span>}
					<span className="text-green-500 flex items-center gap-1">
						<span className="w-1 h-1 rounded-full bg-green-500" />
						已连接
					</span>
				</div>
				<div>SQL</div>
			</footer>

			{/* 保存对话框 */}
			<Dialog open={showSaveDialog} onOpenChange={setShowSaveDialog}>
				<DialogContent className="sm:max-w-md">
					<DialogHeader>
						<DialogTitle>保存查询</DialogTitle>
						<DialogDescription>为当前 SQL 语句命名并保存，方便下次使用</DialogDescription>
					</DialogHeader>
					<div className="space-y-3 py-3">
						<div>
							<label className="text-sm font-medium">名称 *</label>
							<Input
								value={saveQueryName}
								onChange={(e) => setSaveQueryName(e.target.value)}
								placeholder="例如：每日销售统计"
								className="mt-1"
							/>
						</div>
						<div>
							<label className="text-sm font-medium">描述</label>
							<Input
								value={saveQueryDesc}
								onChange={(e) => setSaveQueryDesc(e.target.value)}
								placeholder="可选，简要说明查询用途"
								className="mt-1"
							/>
						</div>
					</div>
					<DialogFooter>
						<Button variant="outline" onClick={() => setShowSaveDialog(false)}>取消</Button>
						<Button onClick={handleSave}>保存</Button>
					</DialogFooter>
				</DialogContent>
			</Dialog>
		</div>
	);
};
