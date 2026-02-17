import { Badge, Button, Card, Input, Modal, Segmented, Select, Space, Table, Tabs, Tag, message } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import {
	createScript,
	getScriptRun,
	listScriptRuns,
	listScripts,
	listScriptVersions,
	runScript,
	saveScriptVersion,
	type ScriptAsset,
	type ScriptRun,
	type ScriptType,
	type ScriptVersion,
} from "@/api/script-studio";

type ScriptStatusFilter = "ALL" | ScriptType;

type CreateFormState = {
	name: string;
	description: string;
	scriptType: ScriptType;
	content: string;
};

const initialCreateForm: CreateFormState = {
	name: "",
	description: "",
	scriptType: "PYTHON",
	content: "# 输入脚本内容\n",
};

const formatTime = (value?: string | null) => {
	if (!value) {
		return "-";
	}
	return new Date(value).toLocaleString("zh-CN", {
		year: "numeric",
		month: "2-digit",
		day: "2-digit",
		hour: "2-digit",
		minute: "2-digit",
		second: "2-digit",
	});
};

const formatDuration = (value?: number | null) => {
	if (typeof value !== "number" || value < 0) {
		return "-";
	}
	if (value < 1000) {
		return `${value}ms`;
	}
	return `${(value / 1000).toFixed(2)}s`;
};

const statusColor = (status?: string | null) => {
	if (status === "SUCCESS" || status === "READY") {
		return "green";
	}
	if (status === "FAILED" || status === "DISABLED") {
		return "red";
	}
	if (status === "RUNNING") {
		return "blue";
	}
	if (status === "CANCELED") {
		return "default";
	}
	return "gold";
};

export default function ScriptStudioPage() {
	const [scripts, setScripts] = useState<ScriptAsset[]>([]);
	const [scriptsLoading, setScriptsLoading] = useState(false);
	const [activeType, setActiveType] = useState<ScriptStatusFilter>("ALL");
	const [selectedId, setSelectedId] = useState<string | null>(null);
	const [versions, setVersions] = useState<ScriptVersion[]>([]);
	const [versionsLoading, setVersionsLoading] = useState(false);
	const [runs, setRuns] = useState<ScriptRun[]>([]);
	const [runsLoading, setRunsLoading] = useState(false);
	const [editorContent, setEditorContent] = useState("");
	const [creating, setCreating] = useState(false);
	const [savingVersion, setSavingVersion] = useState(false);
	const [running, setRunning] = useState(false);
	const [createModalOpen, setCreateModalOpen] = useState(false);
	const [createForm, setCreateForm] = useState<CreateFormState>(initialCreateForm);
	const [versionNote, setVersionNote] = useState("");
	const [runLogOpen, setRunLogOpen] = useState(false);
	const [activeRun, setActiveRun] = useState<ScriptRun | null>(null);
	const pollTimerRef = useRef<number | null>(null);

	const stopPolling = useCallback(() => {
		if (pollTimerRef.current != null) {
			window.clearInterval(pollTimerRef.current);
			pollTimerRef.current = null;
		}
	}, []);

	useEffect(() => () => stopPolling(), [stopPolling]);

	const loadScripts = useCallback(async () => {
		setScriptsLoading(true);
		try {
			const data = await listScripts();
			setScripts(data);
			if (!selectedId && data.length > 0) {
				setSelectedId(data[0].id);
			}
			if (selectedId && !data.some((item) => item.id === selectedId)) {
				setSelectedId(data.length > 0 ? data[0].id : null);
			}
		} catch (error: unknown) {
			const err = error as { message?: string };
			message.error(err?.message || "加载脚本失败");
		} finally {
			setScriptsLoading(false);
		}
	}, [selectedId]);

	useEffect(() => {
		void loadScripts();
	}, [loadScripts]);

	const selected = useMemo(
		() => scripts.find((item) => item.id === selectedId) || null,
		[scripts, selectedId],
	);

	const filteredScripts = useMemo(() => {
		if (activeType === "ALL") {
			return scripts;
		}
		return scripts.filter((item) => item.scriptType === activeType);
	}, [scripts, activeType]);

	const loadVersions = useCallback(async (scriptId: string) => {
		setVersionsLoading(true);
		try {
			const data = await listScriptVersions(scriptId);
			setVersions(data);
			setEditorContent(data.length > 0 ? data[0].content || "" : "");
		} catch (error: unknown) {
			const err = error as { message?: string };
			message.error(err?.message || "加载版本失败");
		} finally {
			setVersionsLoading(false);
		}
	}, []);

	const loadRuns = useCallback(async (scriptId: string) => {
		setRunsLoading(true);
		try {
			const data = await listScriptRuns(scriptId);
			setRuns(data);
		} catch (error: unknown) {
			const err = error as { message?: string };
			message.error(err?.message || "加载运行记录失败");
		} finally {
			setRunsLoading(false);
		}
	}, []);

	useEffect(() => {
		if (!selectedId) {
			setVersions([]);
			setRuns([]);
			setEditorContent("");
			return;
		}
		void loadVersions(selectedId);
		void loadRuns(selectedId);
	}, [selectedId, loadVersions, loadRuns]);

	const startRunPolling = useCallback(
		(runId: string, scriptId: string) => {
			stopPolling();
			pollTimerRef.current = window.setInterval(async () => {
				try {
					const current = await getScriptRun(runId);
					setRuns((prev) => {
						const next = [...prev];
						const index = next.findIndex((item) => item.id === runId);
						if (index >= 0) {
							next[index] = current;
						} else {
							next.unshift(current);
						}
						return next;
					});
					if (activeRun?.id === runId) {
						setActiveRun(current);
					}
					if (current.status === "SUCCESS" || current.status === "FAILED" || current.status === "CANCELED") {
						stopPolling();
						void loadRuns(scriptId);
						void loadScripts();
					}
				} catch {
					stopPolling();
				}
			}, 1500);
		},
		[activeRun?.id, loadRuns, loadScripts, stopPolling]
	);

	const summary = useMemo(
		() => ({
			published: scripts.filter((item) => item.status === "READY").length,
			draft: scripts.filter((item) => item.status === "DRAFT").length,
			running: runs.filter((item) => item.status === "RUNNING" || item.status === "PENDING").length,
			failed: runs.filter((item) => item.status === "FAILED").length,
		}),
		[scripts, runs]
	);

	const handleCreateScript = async () => {
		if (!createForm.name.trim()) {
			message.error("请输入脚本名称");
			return;
		}
		if (!createForm.content.trim()) {
			message.error("请输入脚本内容");
			return;
		}
		setCreating(true);
		try {
			const created = await createScript({
				name: createForm.name.trim(),
				description: createForm.description.trim() || undefined,
				scriptType: createForm.scriptType,
				content: createForm.content,
			});
			message.success("脚本已创建");
			setCreateModalOpen(false);
			setCreateForm(initialCreateForm);
			setSelectedId(created.id);
			await loadScripts();
		} catch (error: unknown) {
			const err = error as { message?: string };
			message.error(err?.message || "创建脚本失败");
		} finally {
			setCreating(false);
		}
	};

	const handleSaveVersion = async () => {
		if (!selectedId) {
			message.error("请先选择脚本");
			return;
		}
		if (!editorContent.trim()) {
			message.error("脚本内容不能为空");
			return;
		}
		setSavingVersion(true);
		try {
			await saveScriptVersion(selectedId, {
				content: editorContent,
				changeSummary: versionNote.trim() || undefined,
				status: "READY",
			});
			setVersionNote("");
			message.success("已保存新版本");
			await loadVersions(selectedId);
			await loadScripts();
		} catch (error: unknown) {
			const err = error as { message?: string };
			message.error(err?.message || "保存版本失败");
		} finally {
			setSavingVersion(false);
		}
	};

	const handleRunScript = async () => {
		if (!selectedId) {
			message.error("请先选择脚本");
			return;
		}
		setRunning(true);
		try {
			const latestVersionNo = versions.length > 0 ? versions[0].versionNo : undefined;
			const run = await runScript(selectedId, latestVersionNo ? { versionNo: latestVersionNo } : {});
			message.success(`已触发运行，executionId=${run.executionId}`);
			setRuns((prev) => [run, ...prev]);
			startRunPolling(run.id, selectedId);
		} catch (error: unknown) {
			const err = error as { message?: string };
			message.error(err?.message || "触发运行失败");
		} finally {
			setRunning(false);
		}
	};

	const openRunLog = async (run: ScriptRun) => {
		setRunLogOpen(true);
		setActiveRun(run);
		try {
			const latest = await getScriptRun(run.id);
			setActiveRun(latest);
		} catch {
			// noop
		}
	};

	const scriptColumns: ColumnsType<ScriptAsset> = [
		{
			title: "脚本名称",
			dataIndex: "name",
			key: "name",
			width: 240,
			render: (_, row) => (
				<div className="space-y-1">
					<div className="font-medium text-text-primary">{row.name}</div>
					<div className="text-xs text-text-tertiary">{row.description || "暂无说明"}</div>
				</div>
			),
		},
		{
			title: "类型",
			dataIndex: "scriptType",
			key: "scriptType",
			width: 100,
			render: (v) => <Tag>{v}</Tag>,
		},
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 120,
			render: (v) => <Tag color={statusColor(v)}>{v}</Tag>,
		},
		{
			title: "版本",
			dataIndex: "latestVersionNo",
			key: "latestVersionNo",
			width: 100,
			render: (v) => `v${v}`,
		},
		{
			title: "更新时间",
			dataIndex: "lastModifiedDate",
			key: "lastModifiedDate",
			width: 200,
			render: (v) => formatTime(v),
		},
	];

	const runColumns: ColumnsType<ScriptRun> = [
		{ title: "执行ID", dataIndex: "executionId", key: "executionId", width: 220 },
		{
			title: "状态",
			dataIndex: "status",
			key: "status",
			width: 120,
			render: (value) => <Tag color={statusColor(value)}>{value}</Tag>,
		},
		{
			title: "版本",
			dataIndex: "versionNo",
			key: "versionNo",
			width: 100,
			render: (v) => `v${v}`,
		},
		{
			title: "触发时间",
			dataIndex: "createdDate",
			key: "createdDate",
			width: 200,
			render: (v) => formatTime(v),
		},
		{
			title: "耗时",
			dataIndex: "durationMs",
			key: "durationMs",
			width: 100,
			render: (v) => formatDuration(v),
		},
		{
			title: "操作",
			key: "actions",
			width: 120,
			render: (_, row) => (
				<Button size="small" type="link" onClick={() => void openRunLog(row)}>
					查看日志
				</Button>
			),
		},
	];

	return (
		<div className="space-y-5">
			<PageHeader
				title="数据开发中心 · 脚本开发（Python/Spark）"
				description="支持脚本资产管理、版本保存、手动运行与日志追踪。"
				actions={
					<Space>
						<Button onClick={() => void loadScripts()}>刷新</Button>
						<Button type="primary" onClick={() => setCreateModalOpen(true)}>
							新建脚本
						</Button>
					</Space>
				}
			/>

			<div className="grid gap-4 lg:grid-cols-4">
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">已发布</div>
					<div className="mt-2 text-2xl font-semibold">{summary.published}</div>
				</Card>
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">草稿</div>
					<div className="mt-2 text-2xl font-semibold">{summary.draft}</div>
				</Card>
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">运行中</div>
					<div className="mt-2 text-2xl font-semibold">{summary.running}</div>
				</Card>
				<Card className="border border-slate-200/80">
					<div className="text-xs text-text-tertiary">失败</div>
					<div className="mt-2 text-2xl font-semibold">{summary.failed}</div>
				</Card>
			</div>

			<Tabs
				defaultActiveKey="scripts"
				items={[
					{
						key: "scripts",
						label: "我的脚本",
						children: (
							<div className="space-y-4">
								<Card
									title="脚本清单"
									extra={
										<Segmented
											options={[
												{ label: "全部", value: "ALL" },
												{ label: "Python", value: "PYTHON" },
												{ label: "Spark", value: "SPARK" },
											]}
											value={activeType}
											onChange={(value) => setActiveType(value as ScriptStatusFilter)}
										/>
									}
								>
									{filteredScripts.length ? (
										<Table
											loading={scriptsLoading}
											columns={scriptColumns}
											dataSource={filteredScripts}
											pagination={false}
											rowKey="id"
											onRow={(record) => ({
												onClick: () => setSelectedId(record.id),
											})}
										/>
									) : (
										<EmptyState title="暂无脚本" description="新建脚本后即可进行版本管理与运行。" />
									)}
								</Card>

								<Card
									title="脚本预览"
									extra={
										<Space>
											<Input
												placeholder="版本说明（可选）"
												value={versionNote}
												onChange={(e) => setVersionNote(e.target.value)}
												style={{ width: 220 }}
											/>
											<Button size="small" onClick={() => void handleSaveVersion()} loading={savingVersion} disabled={!selectedId}>
												保存版本
											</Button>
											<Button size="small" type="primary" onClick={() => void handleRunScript()} loading={running} disabled={!selectedId}>
												运行
											</Button>
										</Space>
									}
								>
									{selected ? (
										<div className="space-y-4">
											<div className="flex items-start justify-between">
												<div>
													<div className="text-lg font-semibold">{selected.name}</div>
													<div className="text-xs text-text-secondary">{selected.description || "暂无描述"}</div>
													<div className="mt-2 flex flex-wrap gap-2">
														<Tag>{selected.scriptType}</Tag>
														<Tag color="blue">v{selected.latestVersionNo}</Tag>
													</div>
												</div>
												<Badge status={selected.status === "READY" ? "success" : "processing"} text={selected.status} />
											</div>
											<Input.TextArea
												value={editorContent}
												onChange={(e) => setEditorContent(e.target.value)}
												autoSize={{ minRows: 10, maxRows: 20 }}
											/>
											<div className="grid gap-2 text-sm">
												<div className="flex items-center justify-between">
													<span className="text-xs text-text-tertiary">创建人</span>
													<span>{selected.createdBy || "-"}</span>
												</div>
												<div className="flex items-center justify-between">
													<span className="text-xs text-text-tertiary">最近更新</span>
													<span>{formatTime(selected.lastModifiedDate)}</span>
												</div>
												<div className="flex items-center justify-between">
													<span className="text-xs text-text-tertiary">版本数</span>
													<span>{versionsLoading ? "-" : versions.length}</span>
												</div>
											</div>
										</div>
									) : (
										<EmptyState title="请选择脚本" description="从脚本清单选择一个脚本。" compact />
									)}
								</Card>

								<Card title="运行历史">
									{runs.length ? (
										<Table
											loading={runsLoading}
											columns={runColumns}
											dataSource={runs}
											pagination={false}
											size="small"
											rowKey="id"
										/>
									) : (
										<EmptyState title="暂无运行记录" description="运行脚本后将在此处展示 executionId 与日志。" compact />
									)}
								</Card>
							</div>
						),
					},
				]}
			/>

			<Modal
				title="新建脚本"
				open={createModalOpen}
				onCancel={() => {
					setCreateModalOpen(false);
					setCreateForm(initialCreateForm);
				}}
				onOk={() => void handleCreateScript()}
				confirmLoading={creating}
				width={820}
			>
				<div className="space-y-3">
					<div className="grid grid-cols-2 gap-3">
						<div>
							<div className="mb-1 text-sm">脚本名称</div>
							<Input
								value={createForm.name}
								onChange={(e) => setCreateForm((prev) => ({ ...prev, name: e.target.value }))}
								placeholder="例如：patent_cleaning_job"
							/>
						</div>
						<div>
							<div className="mb-1 text-sm">脚本类型</div>
							<Select
								value={createForm.scriptType}
								onChange={(value) => setCreateForm((prev) => ({ ...prev, scriptType: value }))}
								options={[
									{ label: "Python", value: "PYTHON" },
									{ label: "Spark", value: "SPARK" },
								]}
								style={{ width: "100%" }}
							/>
						</div>
					</div>
					<div>
						<div className="mb-1 text-sm">描述</div>
						<Input
							value={createForm.description}
							onChange={(e) => setCreateForm((prev) => ({ ...prev, description: e.target.value }))}
							placeholder="脚本用途说明"
						/>
					</div>
					<div>
						<div className="mb-1 text-sm">脚本内容</div>
						<Input.TextArea
							value={createForm.content}
							onChange={(e) => setCreateForm((prev) => ({ ...prev, content: e.target.value }))}
							autoSize={{ minRows: 12, maxRows: 24 }}
						/>
					</div>
				</div>
			</Modal>

			<Modal
				title={activeRun ? `运行日志 · ${activeRun.executionId}` : "运行日志"}
				open={runLogOpen}
				onCancel={() => setRunLogOpen(false)}
				footer={null}
				width={920}
			>
				{activeRun ? (
					<div className="space-y-3">
						<div className="flex items-center gap-2 text-sm">
							<Tag color={statusColor(activeRun.status)}>{activeRun.status}</Tag>
							<span>版本: v{activeRun.versionNo}</span>
							<span>耗时: {formatDuration(activeRun.durationMs)}</span>
							{activeRun.failureType ? <Tag color="red">{activeRun.failureType}</Tag> : null}
						</div>
						{activeRun.errorMessage ? (
							<div className="rounded border border-red-300 bg-red-50 p-2 text-red-600">
								{activeRun.errorMessage}
							</div>
						) : null}
						<pre className="max-h-[460px] overflow-auto rounded bg-slate-950 p-3 text-xs leading-5 text-slate-100">
							{activeRun.logText || "暂无日志"}
						</pre>
					</div>
				) : (
					<EmptyState title="暂无日志" compact />
				)}
			</Modal>
		</div>
	);
}
