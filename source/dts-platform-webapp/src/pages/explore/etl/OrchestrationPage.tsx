import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { listDbtModels, listDbtRuns, getDbtConfig, triggerDbtRun } from "@/api/platformApi";
import { syncInfraDataSource } from "@/api/services/infraService";

export default function OrchestrationPage() {
	const [loading, setLoading] = useState(false);
	const [runsLoading, setRunsLoading] = useState(false);
	const [config, setConfig] = useState<any | null>(null);
	const [models, setModels] = useState<any[]>([]);
	const [runs, setRuns] = useState<any[]>([]);
	const [keyword, setKeyword] = useState("");
	const [selectedModel, setSelectedModel] = useState("");
	const [running, setRunning] = useState(false);
	const [syncing, setSyncing] = useState(false);

	const loadAll = useCallback(async () => {
		setLoading(true);
		try {
			const [cfg, modelResult] = await Promise.all([(getDbtConfig() as any).catch(() => null), (listDbtModels() as any).catch(() => null)]);
			setConfig(cfg || null);
			const list = Array.isArray(modelResult?.models) ? modelResult.models : [];
			setModels(list);
			if (!selectedModel && list.length) {
				setSelectedModel(list[0].name);
			}
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "加载 dbt 模型失败");
		} finally {
			setLoading(false);
		}
	}, [selectedModel]);

	const loadRuns = useCallback(async () => {
		setRunsLoading(true);
		try {
			const resp = (await listDbtRuns(20)) as any;
			const list = Array.isArray(resp?.dag_runs) ? resp.dag_runs : [];
			setRuns(list);
		} catch (e: any) {
			console.error(e);
			setRuns([]);
		} finally {
			setRunsLoading(false);
		}
	}, []);

	useEffect(() => {
		void loadAll();
		void loadRuns();
	}, [loadAll, loadRuns]);

	const filteredModels = useMemo(() => {
		if (!keyword.trim()) return models;
		const kw = keyword.trim().toLowerCase();
		return models.filter((m) => String(m.name || "").toLowerCase().includes(kw));
	}, [keyword, models]);

	const targetId = config?.config?.targetDataSourceId;
	const targetName = config?.config?.targetName || "dev";

	const runDbt = useCallback(async () => {
		if (!selectedModel) {
			toast.error("请选择模型");
			return;
		}
		setRunning(true);
		try {
			await triggerDbtRun({ models: selectedModel, target: targetName, vars: config?.config?.vars || {} });
			toast.success("已触发加载任务");
			await loadRuns();
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "触发失败");
		} finally {
			setRunning(false);
		}
	}, [config, loadRuns, selectedModel, targetName]);

	const syncAssets = useCallback(async () => {
		if (!targetId) {
			toast.error("请先在 dbt 配置中选择目标数仓");
			return;
		}
		setSyncing(true);
		try {
			await syncInfraDataSource(targetId);
			toast.success("已触发资产同步");
		} catch (e: any) {
			console.error(e);
			toast.error(e?.message || "触发同步失败");
		} finally {
			setSyncing(false);
		}
	}, [targetId]);

	if (loading) {
		return <div className="text-sm text-muted-foreground">加载中…</div>;
	}

	return (
		<div className="space-y-6">
			<Card>
				<CardHeader className="flex items-center justify-between">
					<CardTitle>加载任务（dbt）</CardTitle>
					<div className="flex gap-2">
						<Button variant="outline" onClick={loadAll} disabled={loading}>
							刷新模型
						</Button>
						<Button variant="outline" onClick={loadRuns} disabled={runsLoading}>
							刷新运行
						</Button>
					</div>
				</CardHeader>
				<CardContent className="space-y-4">
					<div className="grid gap-4 md:grid-cols-2">
						<div className="space-y-2">
							<Label>模型搜索</Label>
							<Input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="输入模型名称" />
						</div>
						<div className="space-y-2">
							<Label>选择模型</Label>
							<Select value={selectedModel || "__NONE__"} onValueChange={(v) => setSelectedModel(v === "__NONE__" ? "" : v)}>
								<SelectTrigger>
									<SelectValue placeholder="请选择模型" />
								</SelectTrigger>
								<SelectContent>
									<SelectItem value="__NONE__">未选择</SelectItem>
									{filteredModels.map((m) => (
										<SelectItem key={m.uniqueId || m.name} value={m.name}>
											{m.name}
										</SelectItem>
									))}
								</SelectContent>
							</Select>
						</div>
					</div>
					<div className="flex flex-wrap gap-2">
						<Button onClick={runDbt} disabled={running || !selectedModel}>
							{running ? "执行中…" : "触发加载"}
						</Button>
						<Button variant="outline" onClick={syncAssets} disabled={syncing}>
							{syncing ? "同步中…" : "同步资产/元数据"}
						</Button>
					</div>
					<div className="text-xs text-muted-foreground">
						目标数仓：{targetId ? `${targetId} / ${targetName}` : "未配置"}
					</div>
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>运行记录</CardTitle>
				</CardHeader>
				<CardContent>
					{runsLoading ? (
						<div className="text-sm text-muted-foreground">加载中…</div>
					) : runs.length ? (
						<div className="overflow-auto">
							<table className="w-full text-sm">
								<thead className="text-xs text-muted-foreground">
									<tr className="border-b">
										<th className="py-2 text-left font-medium">Run ID</th>
										<th className="py-2 text-left font-medium">状态</th>
										<th className="py-2 text-left font-medium">开始时间</th>
									</tr>
								</thead>
								<tbody>
									{runs.map((r: any) => (
										<tr key={r.dag_run_id} className="border-b last:border-none">
											<td className="py-2 pr-4">{r.dag_run_id}</td>
											<td className="py-2 pr-4">{r.state || "-"}</td>
											<td className="py-2 pr-4">{r.execution_date ? new Date(r.execution_date).toLocaleString() : "-"}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					) : (
						<div className="text-sm text-muted-foreground">暂无运行记录</div>
					)}
				</CardContent>
			</Card>
		</div>
	);
}
