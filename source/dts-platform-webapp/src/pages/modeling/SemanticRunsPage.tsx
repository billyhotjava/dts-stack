import { useCallback, useEffect, useRef, useState } from "react";
import { Button, Drawer, Select, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
	listSemanticModels,
	listSemanticModelRuns,
	triggerSemanticModelRun,
	type SemanticModel,
	type SemanticModelRun,
} from "@/api/semanticModelingApi";

const RUN_STATUS_COLOR: Record<string, string> = {
	PENDING: "default", RUNNING: "processing", SUCCESS: "success",
	FAILED: "error", CANCELLED: "warning",
};

const POLL_INTERVAL_MS = 10_000;

export default function SemanticRunsPage() {
	const [models, setModels] = useState<SemanticModel[]>([]);
	const [selectedModelId, setSelectedModelId] = useState<string | null>(null);
	const [runs, setRuns] = useState<SemanticModelRun[]>([]);
	const [loading, setLoading] = useState(false);
	const [triggering, setTriggering] = useState(false);
	const [logRun, setLogRun] = useState<SemanticModelRun | null>(null);
	const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

	useEffect(() => {
		void listSemanticModels().then((list) => {
			setModels(Array.isArray(list) ? (list as SemanticModel[]) : []);
		});
	}, []);

	const loadRuns = useCallback(async (modelId: string) => {
		setLoading(true);
		try {
			const list = await listSemanticModelRuns(modelId);
			setRuns(Array.isArray(list) ? (list as SemanticModelRun[]) : []);
		} catch {
			setRuns([]);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		if (intervalRef.current) clearInterval(intervalRef.current);
		if (!selectedModelId) return;
		void loadRuns(selectedModelId);
		return () => {
			if (intervalRef.current) clearInterval(intervalRef.current);
		};
	}, [selectedModelId, loadRuns]);

	// 有 RUNNING 时开启轮询
	useEffect(() => {
		if (intervalRef.current) clearInterval(intervalRef.current);
		const hasRunning = runs.some((r) => r.status === "RUNNING" || r.status === "PENDING");
		if (hasRunning && selectedModelId) {
			intervalRef.current = setInterval(() => {
				void loadRuns(selectedModelId);
			}, POLL_INTERVAL_MS);
		}
		return () => {
			if (intervalRef.current) clearInterval(intervalRef.current);
		};
	}, [runs, selectedModelId, loadRuns]);

	const handleTrigger = async () => {
		if (!selectedModelId) return;
		setTriggering(true);
		try {
			await triggerSemanticModelRun(selectedModelId, { runType: "MANUAL" });
			toast.success("运行已触发");
			void loadRuns(selectedModelId);
		} catch {
			/* global interceptor */
		} finally {
			setTriggering(false);
		}
	};

	const columns: ColumnsType<SemanticModelRun> = [
		{ title: "运行类型", dataIndex: "runType", width: 100 },
		{
			title: "状态", dataIndex: "status", width: 120,
			render: (v?: string) => <Tag color={RUN_STATUS_COLOR[v ?? ""] ?? "default"}>{v ?? "-"}</Tag>,
		},
		{ title: "触发人", dataIndex: "triggeredBy", width: 120 },
		{ title: "开始时间", dataIndex: "startedAt", width: 160, render: (v?: string) => v?.slice(0, 16) ?? "-" },
		{ title: "耗时(ms)", dataIndex: "durationMs", width: 100 },
		{
			title: "操作", key: "actions", width: 80,
			render: (_: unknown, row: SemanticModelRun) => (
				<Button type="link" size="small" onClick={() => setLogRun(row)}>日志</Button>
			),
		},
	];

	return (
		<div className="space-y-4" data-testid="semantic-runs-page">
			<PageHeader title="语义建模 · 运行监控" />
			<div className="flex items-center gap-3">
				<Select
					style={{ width: 280 }}
					placeholder="选择模型"
					value={selectedModelId ?? undefined}
					onChange={(v) => setSelectedModelId(v)}
					options={models.map((m) => ({ label: `[${m.type ?? "-"}] ${m.name}`, value: m.id }))}
				/>
				{selectedModelId && (
					<Button loading={triggering} onClick={handleTrigger}>手动触发</Button>
				)}
			</div>
			{selectedModelId && (
				<CompactTable<SemanticModelRun>
					rowKey="id"
					columns={columns}
					dataSource={runs}
					loading={loading}
				/>
			)}
			<Drawer
				title="运行日志"
				open={logRun !== null}
				onClose={() => setLogRun(null)}
			>
				{logRun && (
					<div className="space-y-2 text-sm">
						<div><span className="text-gray-500">状态: </span>
							<Tag color={RUN_STATUS_COLOR[logRun.status ?? ""] ?? "default"}>
								{logRun.status}
							</Tag>
						</div>
						{logRun.message && (
							<pre
								style={{
									background: "#f5f5f5",
									borderRadius: 4,
									padding: 8,
									fontSize: 12,
									whiteSpace: "pre-wrap",
									wordBreak: "break-all",
								}}
							>
								{logRun.message}
							</pre>
						)}
					</div>
				)}
			</Drawer>
		</div>
	);
}
