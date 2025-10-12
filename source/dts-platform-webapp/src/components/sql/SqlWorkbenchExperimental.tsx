import { useEffect, useMemo, useState } from "react";
import type { ChangeEvent, ReactElement } from "react";
import { toast } from "sonner";
import type {
	SqlCatalogNode,
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
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { ScrollArea } from "@/ui/scroll-area";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { Badge } from "@/ui/badge";

const DEFAULT_SQL = "SELECT 1";

const renderCatalogTree = (node: SqlCatalogNode, depth = 0): ReactElement => {
	const padding = depth * 12;
	return (
		<div key={`${node.type}-${node.id}`} className="space-y-1">
			<div className="flex items-center gap-2" style={{ paddingLeft: padding }}>
				<span className="text-xs uppercase text-muted-foreground">{node.type.toLowerCase()}</span>
				<span className="font-medium text-sm text-text-primary">{node.label}</span>
			</div>
			{node.children?.map((child) => renderCatalogTree(child, depth + 1))}
		</div>
	);
};

const summarizeValidation = (validation: SqlValidateResponse | null) => {
	if (!validation) return "waiting for validation";
	if (!validation.executable) return "blocked";
	if (validation.warnings?.length) return `${validation.warnings.length} warning(s)`;
	return "ready";
};

const statusLabel = (status: SqlStatusResponse | null) => {
	if (!status) return "idle";
	return status.status.toLowerCase();
};

export const SqlWorkbenchExperimental = () => {
	const [sqlText, setSqlText] = useState(DEFAULT_SQL);
	const [validation, setValidation] = useState<SqlValidateResponse | null>(null);
	const [isValidating, setIsValidating] = useState(false);
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [catalogRoot, setCatalogRoot] = useState<SqlCatalogNode | null>(null);
	const [execution, setExecution] = useState<SqlStatusResponse | null>(null);
	const [executionId, setExecutionId] = useState<string | null>(null);

	useEffect(() => {
		fetchCatalogTree()
			.then((node) => setCatalogRoot(node))
			.catch((error) => {
				console.error(error);
				toast.error("无法加载数据目录");
			});
	}, []);

	const handleValidate = async () => {
		setIsValidating(true);
		try {
			const response = await validateSql({ sqlText });
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
		setIsSubmitting(true);
		try {
			const response = await submitSql({ sqlText });
			setExecutionId(response.executionId);
			toast.success("已提交查询");
			const status = await refreshStatus(response.executionId);
			if (status && status.status === "PENDING") {
				toast.info("查询在队列中");
			}
		} catch (error) {
			console.error(error);
			toast.error("提交查询失败");
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

	const validationSummary = useMemo(() => summarizeValidation(validation), [validation]);
	const executionSummary = useMemo(() => statusLabel(execution), [execution]);

	return (
		<div className="space-y-4">
			<Card>
				<CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
					<CardTitle className="text-lg">实验版 SQL Workbench</CardTitle>
					<div className="flex flex-wrap gap-2">
						<Button onClick={handleValidate} disabled={isValidating} variant="secondary">
							{isValidating ? "校验中..." : "校验 SQL"}
						</Button>
						<Button onClick={handleSubmit} disabled={isSubmitting}>
							{isSubmitting ? "提交中..." : "执行查询"}
						</Button>
						<Button onClick={handleCancel} variant="ghost" disabled={!executionId}>
							取消执行
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-3">
					<div className="grid gap-2 sm:grid-cols-2">
						<div className="flex items-center gap-2 text-sm text-muted-foreground">
							<span>校验状态</span>
							<Badge variant="outline">{validationSummary}</Badge>
						</div>
						<div className="flex items-center gap-2 text-sm text-muted-foreground">
							<span>执行状态</span>
							<Badge variant="outline">{executionSummary}</Badge>
						</div>
					</div>
					<div className="border rounded-md">
						<textarea
							className="min-h-[320px] w-full resize-y bg-background p-3 font-mono text-sm text-text-primary outline-none"
							value={sqlText}
							onChange={(event: ChangeEvent<HTMLTextAreaElement>) => setSqlText(event.target.value)}
							spellCheck={false}
						/>
					</div>
				</CardContent>
			</Card>

			<div className="grid gap-4 lg:grid-cols-2">
				<Card>
					<CardHeader>
						<CardTitle className="text-base">语法与策略</CardTitle>
					</CardHeader>
					<CardContent>
						<Tabs defaultValue="violations" className="space-y-3">
							<TabsList className="grid grid-cols-3">
								<TabsTrigger value="violations">违规项</TabsTrigger>
								<TabsTrigger value="warnings">提示</TabsTrigger>
								<TabsTrigger value="plan">执行计划</TabsTrigger>
							</TabsList>
							<TabsContent value="violations" className="min-h-[120px] text-sm">
								{validation?.violations?.length ? (
									<ul className="list-disc space-y-2 pl-5">
										{validation.violations.map((item) => (
											<li key={`${item.code}-${item.message}`} className={item.blocking ? "text-destructive" : "text-muted-foreground"}>
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
										{validation.warnings.map((item) => (
											<li key={item}>{item}</li>
										))}
									</ul>
								) : (
									<p className="text-muted-foreground">暂无提示</p>
								)}
							</TabsContent>
							<TabsContent value="plan" className="min-h-[120px] text-sm">
								{validation?.plan?.text ? (
									<pre className="whitespace-pre-wrap text-xs text-muted-foreground">{validation.plan.text}</pre>
								) : (
									<p className="text-muted-foreground">暂未生成计划</p>
								)}
							</TabsContent>
						</Tabs>
					</CardContent>
				</Card>

				<Card>
					<CardHeader>
						<CardTitle className="text-base">数据目录</CardTitle>
					</CardHeader>
					<CardContent>
						<ScrollArea className="h-64">
							{catalogRoot ? (
								renderCatalogTree(catalogRoot)
							) : (
								<p className="text-sm text-muted-foreground">目录加载中...</p>
							)}
						</ScrollArea>
					</CardContent>
				</Card>
			</div>

			<Card>
				<CardHeader>
					<CardTitle className="text-base">执行结果</CardTitle>
				</CardHeader>
				<CardContent className="text-sm text-muted-foreground space-y-2">
					{execution ? (
						<div className="space-y-1">
							<p>状态：{execution.status}</p>
							{execution.rows != null && <p>行数：{execution.rows}</p>}
							{execution.bytes != null && <p>字节：{execution.bytes}</p>}
							{execution.queuePosition != null && <p>队列位置：{execution.queuePosition}</p>}
							{execution.errorMessage && <p className="text-destructive">错误：{execution.errorMessage}</p>}
						</div>
					) : (
						<p>尚未执行查询</p>
					)}
				</CardContent>
			</Card>
		</div>
	);
};
