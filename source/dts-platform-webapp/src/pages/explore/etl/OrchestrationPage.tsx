import { Button, message, Space, Tabs } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router";
import { type IngestionTaskDTO, ingestionTaskAPI } from "@/api/ingestion";
import { PageHeader } from "@/components/page-header";
import { useRouter } from "@/routes/hooks";
import {
	BlockSelectorPanel,
	deserializeDsl,
	serializeDsl,
	useWorkflowStore,
	WorkflowCanvas,
	type WorkflowDsl,
} from "@/components/workflow";
import OrchestrationRunsTab from "./OrchestrationRunsTab";

type OrchestrationTabKey = "canvas" | "runs";

const VALID_TABS: ReadonlyArray<OrchestrationTabKey> = ["canvas", "runs"];
const LOCAL_DSL_KEY = "workflow.orchestration.local-dsl.v1";

function isValidTab(value: string): value is OrchestrationTabKey {
	return (VALID_TABS as ReadonlyArray<string>).includes(value);
}

function parseTaskId(value: string | null): number | null {
	if (!value) return null;
	const parsed = Number(value);
	return Number.isFinite(parsed) && parsed > 0 ? Math.floor(parsed) : null;
}

function readLocalDsl(): WorkflowDsl | null {
	if (typeof window === "undefined") return null;
	const raw = window.localStorage.getItem(LOCAL_DSL_KEY);
	if (!raw) return null;
	try {
		return JSON.parse(raw) as WorkflowDsl;
	} catch {
		return null;
	}
}

export default function OrchestrationPage() {
	const router = useRouter();
	const [searchParams] = useSearchParams();
	const taskId = parseTaskId(searchParams.get("taskId"));
	const [activeKey, setActiveKey] = useState<OrchestrationTabKey>("canvas");
	const [initialDsl, setInitialDsl] = useState<WorkflowDsl | null>(readLocalDsl);
	const [currentTask, setCurrentTask] = useState<IngestionTaskDTO | null>(null);

	const loadTaskDsl = useCallback(async () => {
		if (!taskId) {
			setCurrentTask(null);
			setInitialDsl(readLocalDsl());
			return;
		}
		try {
			const task = await ingestionTaskAPI.getTask(taskId);
			setCurrentTask(task);
			const graphDsl = task.graphDsl as WorkflowDsl | undefined;
			if (graphDsl) {
				const parsed = deserializeDsl(graphDsl);
				if (parsed.success) {
					setInitialDsl(graphDsl);
				} else {
					message.warning(`任务 DSL 无法加载：${parsed.error}`);
				}
				return;
			}
			setInitialDsl(null);
		} catch (error: unknown) {
			setCurrentTask(null);
			const err = error as { message?: string };
			message.error(err.message || "任务 DSL 加载失败");
		}
	}, [taskId]);

	const handleSaveDsl = useCallback(() => {
		const state = useWorkflowStore.getState();
		const dsl = serializeDsl({
			nodes: state.nodes,
			edges: state.edges,
			viewport: state.viewport,
		});
		window.localStorage.setItem(LOCAL_DSL_KEY, JSON.stringify(dsl));
		setInitialDsl(dsl);
		if (!taskId || !currentTask) {
			message.success("DSL 草稿已保存");
			return;
		}
		void ingestionTaskAPI
			.updateTask(taskId, { ...currentTask, graphDsl: dsl as unknown as Record<string, any> })
			.then((task) => {
				setCurrentTask(task);
				message.success("DSL 已保存到任务");
			})
			.catch((error: unknown) => {
				const err = error as { message?: string };
				message.error(err.message || "DSL 保存失败");
			});
	}, [currentTask, taskId]);

	const handleResetDsl = useCallback(() => {
		window.localStorage.removeItem(LOCAL_DSL_KEY);
		setInitialDsl(null);
		const state = useWorkflowStore.getState();
		state.setNodes([]);
		state.setEdges([]);
		state.setSelectedNodeId(null);
		state.setSelectedEdgeId(null);
		state.setPanelOpen(false);
		state.clearHistory();
		message.success("画布已重置");
	}, []);

	useEffect(() => {
		void loadTaskDsl();
	}, [loadTaskDsl]);

	const tabItems = useMemo(
		() => [
			{
				key: "canvas" as const,
				label: "编排画布",
				// destroyInactiveTabPane=false 默认行为；保留 viewport / store
				children: (
					<div
						style={{
							display: "flex",
							height: "calc(100vh - 220px)",
							minHeight: 480,
							border: "1px solid #e2e8f0",
							borderRadius: 8,
							overflow: "hidden",
							background: "#ffffff",
						}}
					>
						<BlockSelectorPanel />
						<div style={{ flex: 1, minWidth: 0 }}>
							<WorkflowCanvas readonly={false} initialDsl={initialDsl} onSave={handleSaveDsl} />
						</div>
					</div>
				),
			},
			{
				key: "runs" as const,
				label: "运行实例",
				children: <OrchestrationRunsTab />,
			},
		],
		[handleSaveDsl, initialDsl],
	);

	return (
		<div className="space-y-4">
			<PageHeader
				title="数据开发中心 / 任务编排"
				actions={
					<Space wrap>
						<Button type="primary" onClick={handleResetDsl}>新建 DAG</Button>
						<Button disabled title="当前编排接口未开放启用调度动作，请先保存 DAG 并在调度服务中生效">
							启用调度
						</Button>
						<Button disabled title="当前编排接口未开放暂停动作，请在调度服务中处理">
							暂停
						</Button>
						<Button onClick={() => router.push("/ops/backfill")}>补数</Button>
						<Button onClick={() => router.push("/ops/alerts")}>查看告警</Button>
					</Space>
				}
			/>
			<Space>
				<Button type="primary" onClick={handleSaveDsl}>
					{taskId ? "保存到任务" : "保存 DSL"}
				</Button>
				<Button onClick={handleResetDsl}>重置画布</Button>
			</Space>
			<Tabs
				activeKey={activeKey}
				onChange={(key) => {
					if (isValidTab(key)) {
						setActiveKey(key);
					}
				}}
				items={tabItems}
				destroyInactiveTabPane={false}
			/>
		</div>
	);
}
