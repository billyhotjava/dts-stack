import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { toast } from "sonner";
import type {
	SqlCatalogNode,
	SqlResultPreview,
	SqlStatusResponse,
	SqlValidateResponse,
} from "@/api/sql-workbench";
import {
	cancelSql,
	fetchCatalogTree,
	getSqlStatus,
	submitSql,
	validateSql,
} from "@/api/sql-workbench";
import dataSourcesService, { type InfraDataSource } from "@/api/services/dataSourcesService";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { ScrollArea } from "@/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Badge } from "@/ui/badge";
import {
	Select,
	SelectContent,
	SelectItem,
	SelectTrigger,
	SelectValue,
} from "@/ui/select";
import { Input } from "@/ui/input";

const DEFAULT_SQL = "-- 选择数据源后，输入 SQL 语句\nSELECT 1";
const HISTORY_KEY = "sql-workbench-history";
const MAX_HISTORY = 20;

type QueryHistoryItem = {
	sql: string;
	datasourceId?: string;
	datasourceName?: string;
	timestamp: number;
	status: "success" | "failed";
	rowCount?: number;
	elapsedMs?: number;
};

// 从 localStorage 加载历史
const loadHistory = (): QueryHistoryItem[] => {
	try {
		const raw = localStorage.getItem(HISTORY_KEY);
		return raw ? JSON.parse(raw) : [];
	} catch {
		return [];
	}
};

// 保存历史到 localStorage
const saveHistory = (history: QueryHistoryItem[]) => {
	try {
		localStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(0, MAX_HISTORY)));
	} catch {
		// ignore storage errors
	}
};

// 导出 CSV
const downloadCsv = (headers: string[], rows: Record<string, any>[], filename: string) => {
	const escapeCell = (val: any) => {
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

// 目录树图标
const getNodeIcon = (type: string) => {
	switch (type) {
		case "CATALOG":
			return "📁";
		case "SCHEMA":
			return "📂";
		case "TABLE":
			return "📋";
		case "COLUMN":
			return "📊";
		default:
			return "📄";
	}
};

// 渲染目录树
const CatalogTreeNode = ({
	node,
	depth = 0,
	onInsert,
	searchTerm,
}: {
	node: SqlCatalogNode;
	depth?: number;
	onInsert: (text: string, type: string) => void;
	searchTerm: string;
}) => {
	const [expanded, setExpanded] = useState(depth < 2);
	const hasChildren = node.children && node.children.length > 0;
	const matchesSearch =
		!searchTerm || node.label.toLowerCase().includes(searchTerm.toLowerCase());
	const hasMatchingChildren =
		searchTerm &&
		node.children?.some(
			(child) =>
				child.label.toLowerCase().includes(searchTerm.toLowerCase()) ||
				child.children?.some((c) => c.label.toLowerCase().includes(searchTerm.toLowerCase()))
		);

	// 如果有搜索词且当前节点和子节点都不匹配，不显示
	if (searchTerm && !matchesSearch && !hasMatchingChildren) {
		return null;
	}

	// 如果有搜索词且有匹配的子节点，自动展开
	const shouldExpand = expanded || (searchTerm && hasMatchingChildren);

	const handleClick = () => {
		if (node.type === "TABLE" || node.type === "COLUMN") {
			onInsert(node.label, node.type);
		}
	};

	return (
		<div className="select-none">
			<div
				className={`flex items-center gap-1 py-0.5 px-1 rounded cursor-pointer hover:bg-muted/50 ${
					matchesSearch && searchTerm ? "bg-yellow-100 dark:bg-yellow-900/30" : ""
				}`}
				style={{ paddingLeft: depth * 16 }}
				onClick={() => hasChildren && setExpanded(!shouldExpand)}
			>
				{hasChildren ? (
					<span className="w-4 text-center text-xs text-muted-foreground">
						{shouldExpand ? "▼" : "▶"}
					</span>
				) : (
					<span className="w-4" />
				)}
				<span className="text-sm">{getNodeIcon(node.type)}</span>
				<span
					className={`text-sm truncate ${
						node.type === "TABLE" || node.type === "COLUMN"
							? "text-blue-600 dark:text-blue-400 hover:underline cursor-pointer"
							: "text-text-primary"
					}`}
					onClick={(e) => {
						if (node.type === "TABLE" || node.type === "COLUMN") {
							e.stopPropagation();
							handleClick();
						}
					}}
					title={node.type === "TABLE" || node.type === "COLUMN" ? "点击插入到 SQL" : undefined}
				>
					{node.label}
				</span>
				<span className="text-xs text-muted-foreground ml-1">
					{node.type.toLowerCase()}
				</span>
			</div>
			{shouldExpand &&
				hasChildren &&
				node.children?.map((child) => (
					<CatalogTreeNode
						key={`${child.type}-${child.id}`}
						node={child}
						depth={depth + 1}
						onInsert={onInsert}
						searchTerm={searchTerm}
					/>
				))}
		</div>
	);
};

const summarizeValidation = (validation: SqlValidateResponse | null) => {
	if (!validation) return "等待校验";
	if (!validation.executable) return "被阻断";
	if (validation.warnings?.length) return `${validation.warnings.length} 个警告`;
	return "就绪";
};

const statusLabel = (status: SqlStatusResponse | null) => {
	if (!status) return "空闲";
	const labels: Record<string, string> = {
		RUNNING: "执行中",
		SUCCESS: "成功",
		FAILED: "失败",
		CANCELED: "已取消",
		PENDING: "排队中",
	};
	return labels[status.status] || status.status.toLowerCase();
};

const formatTime = (timestamp: number) => {
	const date = new Date(timestamp);
	return date.toLocaleString("zh-CN", {
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
	});
};

export const SqlWorkbenchExperimental = () => {
	const [sqlText, setSqlText] = useState(DEFAULT_SQL);
	const [validation, setValidation] = useState<SqlValidateResponse | null>(null);
	const [isValidating, setIsValidating] = useState(false);
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [catalogRoot, setCatalogRoot] = useState<SqlCatalogNode | null>(null);
	const [catalogLoading, setCatalogLoading] = useState(false);
	const [execution, setExecution] = useState<SqlStatusResponse | null>(null);
	const [executionId, setExecutionId] = useState<string | null>(null);

	// 数据源相关
	const [dataSources, setDataSources] = useState<InfraDataSource[]>([]);
	const [selectedDatasourceId, setSelectedDatasourceId] = useState<string>("");
	const [datasourcesLoading, setDatasourcesLoading] = useState(true);

	// 目录搜索
	const [catalogSearch, setCatalogSearch] = useState("");

	// 查询历史
	const [history, setHistory] = useState<QueryHistoryItem[]>([]);
	const [showHistory, setShowHistory] = useState(false);

	const textareaRef = useRef<HTMLTextAreaElement>(null);

	// 加载数据源列表
	useEffect(() => {
		setDatasourcesLoading(true);
		dataSourcesService
			.list()
			.then((sources) => {
				// 只显示 ACTIVE 状态的数据源
				const activeSources = sources.filter((s) => s.status === "ACTIVE");
				setDataSources(activeSources);
				// 如果有 postgres 类型的数据源，默认选中
				const pgSource = activeSources.find(
					(s) => s.type?.toLowerCase() === "postgres"
				);
				if (pgSource) {
					setSelectedDatasourceId(pgSource.id);
				} else if (activeSources.length > 0) {
					setSelectedDatasourceId(activeSources[0].id);
				}
			})
			.catch((error) => {
				console.error(error);
				toast.error("加载数据源列表失败");
			})
			.finally(() => setDatasourcesLoading(false));

		// 加载历史
		setHistory(loadHistory());
	}, []);

	// 当数据源变化时加载目录
	useEffect(() => {
		if (!selectedDatasourceId) {
			setCatalogRoot(null);
			return;
		}
		setCatalogLoading(true);
		fetchCatalogTree({ datasource: selectedDatasourceId })
			.then((node) => setCatalogRoot(node))
			.catch((error) => {
				console.error(error);
				// 如果加载失败，尝试不带数据源参数加载
				return fetchCatalogTree({}).then((node) => setCatalogRoot(node));
			})
			.catch(() => {
				setCatalogRoot(null);
			})
			.finally(() => setCatalogLoading(false));
	}, [selectedDatasourceId]);

	const selectedDatasource = useMemo(
		() => dataSources.find((ds) => ds.id === selectedDatasourceId),
		[dataSources, selectedDatasourceId]
	);

	// 插入文本到 SQL 编辑器
	const handleInsertText = useCallback((text: string, type: string) => {
		const textarea = textareaRef.current;
		if (!textarea) return;

		const start = textarea.selectionStart;
		const end = textarea.selectionEnd;
		const before = sqlText.substring(0, start);
		const after = sqlText.substring(end);

		// 根据类型决定插入格式
		const insertText = type === "TABLE" ? text : text;
		const newText = before + insertText + after;
		setSqlText(newText);

		// 恢复光标位置
		setTimeout(() => {
			textarea.focus();
			textarea.setSelectionRange(start + insertText.length, start + insertText.length);
		}, 0);

		toast.success(`已插入: ${text}`);
	}, [sqlText]);

	const handleValidate = async () => {
		setIsValidating(true);
		try {
			const response = await validateSql({
				sqlText,
				datasource: selectedDatasourceId || undefined,
			});
			setValidation(response);
			if (response.executable) {
				toast.success("语法检查通过");
			} else {
				toast.warning("语句被策略阻断");
			}
		} catch (error) {
			console.error(error);
			toast.error("校验失败");
		} finally {
			setIsValidating(false);
		}
	};

	const refreshStatus = async (id: string) => {
		try {
			const status = await getSqlStatus(id);
			setExecution(status);
			return status;
		} catch (error) {
			console.error(error);
			toast.error("获取执行状态失败");
			return null;
		}
	};

	const handleSubmit = async () => {
		if (!selectedDatasourceId) {
			toast.error("请先选择数据源");
			return;
		}
		if (!sqlText.trim() || sqlText.trim() === DEFAULT_SQL.trim()) {
			toast.error("请输入 SQL 语句");
			return;
		}

		setIsSubmitting(true);
		setExecution(null);
		const startTime = Date.now();

		try {
			const response = await submitSql({
				sqlText,
				datasource: selectedDatasourceId,
			});
			setExecutionId(response.executionId);
			toast.success("已提交查询");

			const status = await refreshStatus(response.executionId);

			// 保存到历史
			const historyItem: QueryHistoryItem = {
				sql: sqlText.trim(),
				datasourceId: selectedDatasourceId,
				datasourceName: selectedDatasource?.name,
				timestamp: Date.now(),
				status: status?.status === "SUCCESS" ? "success" : "failed",
				rowCount: status?.rows,
				elapsedMs: status?.elapsedMs || Date.now() - startTime,
			};
			const newHistory = [historyItem, ...history.filter((h) => h.sql !== sqlText.trim())];
			setHistory(newHistory);
			saveHistory(newHistory);

			if (status && status.status === "PENDING") {
				toast.info("查询在队列中");
			}
		} catch (error: any) {
			console.error(error);
			const errorMsg = error?.response?.data?.detail || error?.message || "提交查询失败";
			toast.error(errorMsg);

			// 保存失败记录
			const historyItem: QueryHistoryItem = {
				sql: sqlText.trim(),
				datasourceId: selectedDatasourceId,
				datasourceName: selectedDatasource?.name,
				timestamp: Date.now(),
				status: "failed",
				elapsedMs: Date.now() - startTime,
			};
			const newHistory = [historyItem, ...history.filter((h) => h.sql !== sqlText.trim())];
			setHistory(newHistory);
			saveHistory(newHistory);
		} finally {
			setIsSubmitting(false);
		}
	};

	const handleCancel = async () => {
		if (!executionId) return;
		try {
			await cancelSql(executionId);
			toast.success("已发送取消请求");
			await refreshStatus(executionId);
		} catch (error) {
			console.error(error);
			toast.error("取消失败");
		}
	};

	const handleLoadHistory = (item: QueryHistoryItem) => {
		setSqlText(item.sql);
		if (item.datasourceId && dataSources.some((ds) => ds.id === item.datasourceId)) {
			setSelectedDatasourceId(item.datasourceId);
		}
		setShowHistory(false);
		toast.success("已加载历史查询");
	};

	const handleClearHistory = () => {
		setHistory([]);
		saveHistory([]);
		toast.success("已清空历史");
	};

	const handleDownloadCsv = () => {
		const preview = execution?.preview;
		if (!preview?.headers?.length || !preview?.rows?.length) {
			toast.error("没有可下载的数据");
			return;
		}
		const filename = `query-result-${new Date().toISOString().slice(0, 10)}.csv`;
		downloadCsv(preview.headers, preview.rows, filename);
		toast.success("已下载 CSV");
	};

	const validationSummary = useMemo(() => summarizeValidation(validation), [validation]);
	const executionSummary = useMemo(() => statusLabel(execution), [execution]);
	const preview: SqlResultPreview | null | undefined = execution?.preview;

	return (
		<div className="space-y-4">
			{/* 顶部工具栏 */}
			<Card>
				<CardHeader className="pb-3">
					<div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
						<CardTitle className="text-lg flex items-center gap-2">
							SQL 工作台
							<Badge variant="secondary" className="text-xs">
								Beta
							</Badge>
						</CardTitle>
						<div className="flex flex-wrap items-center gap-2">
							{/* 数据源选择 */}
							<Select
								value={selectedDatasourceId}
								onValueChange={setSelectedDatasourceId}
								disabled={datasourcesLoading}
							>
								<SelectTrigger className="w-[200px]">
									<SelectValue placeholder={datasourcesLoading ? "加载中..." : "选择数据源"} />
								</SelectTrigger>
								<SelectContent>
									{dataSources.map((ds) => (
										<SelectItem key={ds.id} value={ds.id}>
											<div className="flex items-center gap-2">
												<span
													className={`w-2 h-2 rounded-full ${
														ds.status === "ACTIVE" ? "bg-green-500" : "bg-gray-400"
													}`}
												/>
												<span>{ds.name}</span>
												<span className="text-xs text-muted-foreground">({ds.type})</span>
											</div>
										</SelectItem>
									))}
								</SelectContent>
							</Select>

							{/* 操作按钮 */}
							<Button
								onClick={handleValidate}
								disabled={isValidating || !sqlText.trim()}
								variant="outline"
								size="sm"
							>
								{isValidating ? "校验中..." : "校验"}
							</Button>
							<Button
								onClick={handleSubmit}
								disabled={isSubmitting || !selectedDatasourceId || !sqlText.trim()}
								size="sm"
							>
								{isSubmitting ? "执行中..." : "执行"}
							</Button>
							<Button
								onClick={handleCancel}
								variant="ghost"
								size="sm"
								disabled={!executionId || execution?.status === "SUCCESS"}
							>
								取消
							</Button>
							<Button
								onClick={() => setShowHistory(!showHistory)}
								variant="ghost"
								size="sm"
							>
								历史 ({history.length})
							</Button>
						</div>
					</div>
				</CardHeader>

				<CardContent className="space-y-3">
					{/* 状态栏 */}
					<div className="flex flex-wrap items-center gap-4 text-sm">
						{selectedDatasource && (
							<div className="flex items-center gap-2">
								<span className="text-muted-foreground">数据源:</span>
								<Badge variant="outline">{selectedDatasource.name}</Badge>
							</div>
						)}
						<div className="flex items-center gap-2">
							<span className="text-muted-foreground">校验:</span>
							<Badge
								variant={
									validation?.executable
										? "default"
										: validation
											? "destructive"
											: "secondary"
								}
							>
								{validationSummary}
							</Badge>
						</div>
						<div className="flex items-center gap-2">
							<span className="text-muted-foreground">执行:</span>
							<Badge
								variant={
									execution?.status === "SUCCESS"
										? "default"
										: execution?.status === "FAILED"
											? "destructive"
											: "secondary"
								}
							>
								{executionSummary}
							</Badge>
						</div>
						{execution?.elapsedMs != null && (
							<span className="text-muted-foreground">
								耗时: {execution.elapsedMs}ms
							</span>
						)}
						{execution?.rows != null && (
							<span className="text-muted-foreground">行数: {execution.rows}</span>
						)}
					</div>

					{/* SQL 编辑器 */}
					<div className="border rounded-md">
						<textarea
							ref={textareaRef}
							className="min-h-[200px] w-full resize-y bg-background p-3 font-mono text-sm text-text-primary outline-none focus:ring-2 focus:ring-ring focus:ring-offset-1"
							value={sqlText}
							onChange={(event: ChangeEvent<HTMLTextAreaElement>) =>
								setSqlText(event.target.value)
							}
							spellCheck={false}
							placeholder="输入 SQL 语句..."
						/>
					</div>
				</CardContent>
			</Card>

			{/* 历史记录面板 */}
			{showHistory && (
				<Card>
					<CardHeader className="py-3">
						<div className="flex items-center justify-between">
							<CardTitle className="text-base">查询历史</CardTitle>
							<Button variant="ghost" size="sm" onClick={handleClearHistory}>
								清空
							</Button>
						</div>
					</CardHeader>
					<CardContent>
						{history.length === 0 ? (
							<p className="text-sm text-muted-foreground">暂无历史记录</p>
						) : (
							<ScrollArea className="h-48">
								<div className="space-y-2">
									{history.map((item, idx) => (
										<div
											key={`${item.timestamp}-${idx}`}
											className="flex items-start gap-3 p-2 rounded border hover:bg-muted/50 cursor-pointer"
											onClick={() => handleLoadHistory(item)}
										>
											<Badge
												variant={item.status === "success" ? "default" : "destructive"}
												className="mt-0.5"
											>
												{item.status === "success" ? "成功" : "失败"}
											</Badge>
											<div className="flex-1 min-w-0">
												<p className="text-xs text-muted-foreground">
													{formatTime(item.timestamp)}
													{item.datasourceName && ` · ${item.datasourceName}`}
													{item.rowCount != null && ` · ${item.rowCount} 行`}
													{item.elapsedMs != null && ` · ${item.elapsedMs}ms`}
												</p>
												<p className="text-sm font-mono truncate">{item.sql}</p>
											</div>
										</div>
									))}
								</div>
							</ScrollArea>
						)}
					</CardContent>
				</Card>
			)}

			{/* 中间区域：目录 + 验证 */}
			<div className="grid gap-4 lg:grid-cols-2">
				{/* 数据目录 */}
				<Card>
					<CardHeader className="py-3">
						<div className="flex items-center justify-between gap-2">
							<CardTitle className="text-base">数据目录</CardTitle>
							<Input
								placeholder="搜索表名..."
								value={catalogSearch}
								onChange={(e) => setCatalogSearch(e.target.value)}
								className="w-40 h-8 text-sm"
							/>
						</div>
					</CardHeader>
					<CardContent>
						<ScrollArea className="h-64">
							{catalogLoading ? (
								<p className="text-sm text-muted-foreground">加载中...</p>
							) : !selectedDatasourceId ? (
								<p className="text-sm text-muted-foreground">请先选择数据源</p>
							) : catalogRoot ? (
								<CatalogTreeNode
									node={catalogRoot}
									onInsert={handleInsertText}
									searchTerm={catalogSearch}
								/>
							) : (
								<p className="text-sm text-muted-foreground">
									暂无目录数据，可能是数据源未配置或无权限访问
								</p>
							)}
						</ScrollArea>
						<p className="mt-2 text-xs text-muted-foreground">
							提示: 点击表名或列名可插入到 SQL
						</p>
					</CardContent>
				</Card>

				{/* 校验结果 */}
				<Card>
					<CardHeader className="py-3">
						<CardTitle className="text-base">校验结果</CardTitle>
					</CardHeader>
					<CardContent>
						<Tabs defaultValue="violations" className="space-y-3">
							<TabsList className="grid grid-cols-3">
								<TabsTrigger value="violations">
									违规{validation?.violations?.length ? ` (${validation.violations.length})` : ""}
								</TabsTrigger>
								<TabsTrigger value="warnings">
									警告{validation?.warnings?.length ? ` (${validation.warnings.length})` : ""}
								</TabsTrigger>
								<TabsTrigger value="plan">计划</TabsTrigger>
							</TabsList>
							<TabsContent value="violations" className="min-h-[120px] text-sm">
								{validation?.violations?.length ? (
									<ul className="list-disc space-y-2 pl-5">
										{validation.violations.map((item) => (
											<li
												key={`${item.code}-${item.message}`}
												className={
													item.blocking ? "text-destructive" : "text-muted-foreground"
												}
											>
												<span className="font-medium">[{item.code}]</span> {item.message}
											</li>
										))}
									</ul>
								) : (
									<p className="text-muted-foreground">暂无违规项</p>
								)}
							</TabsContent>
							<TabsContent value="warnings" className="min-h-[120px] text-sm">
								{validation?.warnings?.length ? (
									<ul className="list-disc space-y-2 pl-5">
										{validation.warnings.map((item, idx) => (
											<li key={`warn-${idx}`}>{item}</li>
										))}
									</ul>
								) : (
									<p className="text-muted-foreground">暂无警告</p>
								)}
							</TabsContent>
							<TabsContent value="plan" className="min-h-[120px] text-sm">
								{validation?.plan?.text ? (
									<pre className="whitespace-pre-wrap text-xs text-muted-foreground">
										{validation.plan.text}
									</pre>
								) : (
									<p className="text-muted-foreground">暂未生成执行计划</p>
								)}
							</TabsContent>
						</Tabs>
					</CardContent>
				</Card>
			</div>

			{/* 执行结果 */}
			<Card>
				<CardHeader className="py-3">
					<div className="flex items-center justify-between">
						<CardTitle className="text-base">执行结果</CardTitle>
						{preview?.headers?.length ? (
							<Button variant="outline" size="sm" onClick={handleDownloadCsv}>
								下载 CSV
							</Button>
						) : null}
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					{execution ? (
						<div className="space-y-2 text-sm">
							{/* 错误信息 */}
							{execution.errorMessage && (
								<div className="p-3 rounded-md bg-destructive/10 border border-destructive/20">
									<p className="font-medium text-destructive mb-1">执行错误</p>
									<p className="text-sm text-destructive/90 font-mono whitespace-pre-wrap">
										{execution.errorMessage}
									</p>
								</div>
							)}

							{/* 结果表格 */}
							{preview?.headers?.length ? (
								<div className="border rounded-md">
									<div className="flex items-center justify-between border-b px-3 py-2 text-xs text-muted-foreground bg-muted/30">
										<span>
											结果预览
											{preview.rowCount != null && ` (${preview.rowCount} 行)`}
										</span>
										{preview.truncated && (
											<Badge variant="secondary">已截断，仅显示前 100 行</Badge>
										)}
									</div>
									<ScrollArea className="h-80">
										<table className="min-w-full text-xs">
											<thead className="sticky top-0 bg-muted/80 backdrop-blur-sm">
												<tr>
													<th className="px-3 py-2 text-left font-medium text-muted-foreground w-10">
														#
													</th>
													{preview.headers.map((header) => (
														<th
															key={header}
															className="px-3 py-2 text-left font-medium text-text-primary"
														>
															{header}
														</th>
													))}
												</tr>
											</thead>
											<tbody>
												{preview.rows?.map((row, idx) => (
													<tr
														key={`row-${idx}`}
														className="border-t hover:bg-muted/30"
													>
														<td className="px-3 py-2 text-muted-foreground">
															{idx + 1}
														</td>
														{preview.headers.map((header) => (
															<td
																key={`${idx}-${header}`}
																className="px-3 py-2 align-top text-text-primary font-mono"
															>
																{row?.[header] == null ? (
																	<span className="text-muted-foreground italic">
																		NULL
																	</span>
																) : (
																	String(row[header])
																)}
															</td>
														))}
													</tr>
												))}
											</tbody>
										</table>
									</ScrollArea>
								</div>
							) : execution.status === "SUCCESS" ? (
								<p className="text-muted-foreground">查询成功，但无返回数据</p>
							) : null}
						</div>
					) : (
						<p className="text-sm text-muted-foreground py-8 text-center">
							选择数据源，输入 SQL 语句，然后点击"执行"按钮
						</p>
					)}
				</CardContent>
			</Card>
		</div>
	);
};
