import {
	Alert,
	Button,
	Descriptions,
	Drawer,
	Input,
	message,
	Modal,
	Space,
	Table,
	Tabs,
	Tag,
} from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
	applyClassificationMigrationBatch,
	approveCatalogLifecycleAction,
	type AssetGovernanceWorkspace,
	type ClassificationMigrationItem,
	type ClassificationMigrationReconciliation,
	type ClassificationMigrationRun,
	type ClassificationWriteFreeze,
	type ClassificationFactView,
	createClassificationMigrationDryRun,
	freezeLegacyClassificationWrites,
	getCatalogAssetGovernanceWorkspace,
	getCatalogGovernanceIssues,
	getCatalogLifecycleMetrics,
	getClassificationMigrationRun,
	type GovernanceIssueView,
	type LifecycleActionView,
	type LifecycleMetrics,
	listClassificationMigrationItems,
	listClassificationWriteFreezes,
	pauseClassificationMigration,
	reconcileClassificationMigration,
	rejectCatalogLifecycleAction,
	resumeClassificationMigration,
	retryCatalogLifecycleDestruction,
	submitCatalogLifecycleAction,
} from "@/api/platformApi";
import { useRouter } from "@/routes/hooks";
import type { AssetRow } from "./assetPageShared";
import { classificationText, formatTime } from "./assetPageShared";

type Props = {
	open: boolean;
	asset: AssetRow | null;
	classificationFact?: ClassificationFactView;
	onClose: () => void;
	onChanged?: () => void;
};

const ACTION_LABELS: Record<string, string> = {
	ARCHIVE: "申请归档",
	TRASH: "申请临时销毁",
	RESTORE: "申请恢复",
	PERMANENT_DESTROY: "申请永久销毁",
};

const LIFECYCLE_LABELS: Record<string, string> = {
	IN_USE: "在用",
	SHARED: "共享",
	ARCHIVED: "归档",
	TRASH: "回收站",
	DESTROYED: "永久销毁",
};

const digestPayload = async (value: string) => {
	const bytes = new TextEncoder().encode(value);
	const digest = await crypto.subtle.digest("SHA-256", bytes);
	return Array.from(new Uint8Array(digest))
		.map((part) => part.toString(16).padStart(2, "0"))
		.join("");
};

const promptReason = (title: string, warning?: string) =>
	new Promise<string>((resolve, reject) => {
		let reason = "";
		Modal.confirm({
			title,
			width: 560,
			okText: "提交审批",
			cancelText: "取消",
			okButtonProps: warning ? { danger: true } : undefined,
			content: (
				<div className="space-y-3 pt-2">
					{warning ? <Alert type="warning" showIcon message={warning} /> : null}
					<Input.TextArea
						rows={4}
						maxLength={500}
						showCount
						placeholder="请填写业务原因和影响范围"
						onChange={(event) => {
							reason = event.target.value.trim();
						}}
					/>
				</div>
			),
			onOk: () => {
				if (reason.length < 5) {
					message.error("审批原因至少填写 5 个字符");
					return Promise.reject();
				}
				resolve(reason);
			},
			onCancel: () => reject(new Error("cancelled")),
		});
	});

export function AssetLifecycleWorkbenchDrawer({
	open,
	asset,
	classificationFact,
	onClose,
	onChanged,
}: Props) {
	const router = useRouter();
	const [workspace, setWorkspace] = useState<AssetGovernanceWorkspace | null>(null);
	const [metrics, setMetrics] = useState<LifecycleMetrics | null>(null);
	const [issues, setIssues] = useState<GovernanceIssueView[]>([]);
	const [migrationRun, setMigrationRun] = useState<ClassificationMigrationRun | null>(null);
	const [migrationItems, setMigrationItems] = useState<ClassificationMigrationItem[]>([]);
	const [reconciliation, setReconciliation] = useState<ClassificationMigrationReconciliation | null>(null);
	const [writeFreezes, setWriteFreezes] = useState<ClassificationWriteFreeze[]>([]);
	const [loading, setLoading] = useState(false);
	const [actingId, setActingId] = useState<string | null>(null);

	const datasetId = asset?.legacyDatasetId || (asset?.metadataSource === "dts-catalog" ? asset.id : undefined);
	const subjectKey = asset?.assetKey;
	const fact = workspace?.classification || classificationFact;

	const load = useCallback(async () => {
		if (!open || !asset) return;
		setLoading(true);
		const [workspaceResult, metricsResult, issuesResult] = await Promise.allSettled([
			datasetId && subjectKey
				? getCatalogAssetGovernanceWorkspace(datasetId, subjectKey)
				: Promise.resolve(null),
			getCatalogLifecycleMetrics({ days: 30 }),
			getCatalogGovernanceIssues(100),
		]);
		setWorkspace(workspaceResult.status === "fulfilled" ? workspaceResult.value : null);
		setMetrics(metricsResult.status === "fulfilled" ? metricsResult.value : null);
		setIssues(issuesResult.status === "fulfilled" && Array.isArray(issuesResult.value) ? issuesResult.value : []);
		setLoading(false);
	}, [asset, datasetId, open, subjectKey]);

	useEffect(() => {
		void load();
	}, [load]);

	const refreshMigration = useCallback(async (runId: string) => {
		const [runResult, itemsResult, reconciliationResult, freezesResult] = await Promise.allSettled([
			getClassificationMigrationRun(runId),
			listClassificationMigrationItems(runId, { limit: 200 }),
			reconcileClassificationMigration(runId),
			listClassificationWriteFreezes(),
		]);
		setMigrationRun(runResult.status === "fulfilled" ? runResult.value : null);
		setMigrationItems(itemsResult.status === "fulfilled" && Array.isArray(itemsResult.value) ? itemsResult.value : []);
		setReconciliation(reconciliationResult.status === "fulfilled" ? reconciliationResult.value : null);
		setWriteFreezes(freezesResult.status === "fulfilled" && Array.isArray(freezesResult.value) ? freezesResult.value : []);
	}, []);

	useEffect(() => {
		if (!open) return;
		const runId = window.localStorage.getItem("catalog.classification.migration.run");
		if (runId) void refreshMigration(runId);
	}, [open, refreshMigration]);

	const availableActions = useMemo(() => {
		if (!workspace?.lifecycle || !fact?.sealed || !datasetId) return [];
		const lifecycleStatus = String(workspace.lifecycle.lifecycleStatus || "").toUpperCase();
		const trashStatus = String(workspace.lifecycle.trash?.status || "").toUpperCase();
		const actions: string[] = [];
		if (lifecycleStatus !== "ARCHIVED" && !trashStatus) actions.push("ARCHIVE");
		if (!trashStatus || trashStatus === "RESTORED") actions.push("TRASH");
		if (["TRASHED", "DESTRUCTION_CANDIDATE", "RESTORE_REQUESTED"].includes(trashStatus)) {
			actions.push("RESTORE");
		}
		if (trashStatus === "DESTRUCTION_CANDIDATE") actions.push("PERMANENT_DESTROY");
		return actions;
	}, [datasetId, fact?.sealed, workspace?.lifecycle]);

	const submitAction = async (actionType: string) => {
		if (!datasetId || !fact?.snapshotId || fact.snapshotVersion == null) {
			message.error("当前资产缺少可验证的密级快照，不能提交生命周期审批");
			return;
		}
		try {
			const reason = await promptReason(
				ACTION_LABELS[actionType] || actionType,
				actionType === "PERMANENT_DESTROY"
					? "永久销毁不可恢复，且只删除 DTS 管理副本；必须经过不同人员双重审批。"
					: undefined,
			);
			setActingId(actionType);
			const payloadChecksum = await digestPayload(
				JSON.stringify({
					datasetId,
					actionType,
					sealId: fact.snapshotId,
					sealVersion: fact.snapshotVersion,
					reason,
				}),
			);
			await submitCatalogLifecycleAction({
				datasetId,
				actionType,
				payloadChecksum,
				sealId: fact.snapshotId,
				sealVersion: fact.snapshotVersion,
				reason,
				retentionDays: actionType === "TRASH" ? 30 : undefined,
			});
			message.success("生命周期申请已提交");
			await load();
			onChanged?.();
		} catch (error) {
			if (error instanceof Error && error.message === "cancelled") return;
			message.error(error instanceof Error ? error.message : "生命周期申请提交失败");
		} finally {
			setActingId(null);
		}
	};

	const decide = async (action: LifecycleActionView, decision: "approve" | "reject") => {
		try {
			const notes = await promptReason(decision === "approve" ? "批准生命周期申请" : "驳回生命周期申请");
			setActingId(action.id);
			if (decision === "approve") {
				await approveCatalogLifecycleAction(action.id, notes);
			} else {
				await rejectCatalogLifecycleAction(action.id, notes);
			}
			message.success(decision === "approve" ? "审批已提交" : "申请已驳回");
			await load();
			onChanged?.();
		} catch (error) {
			if (error instanceof Error && error.message === "cancelled") return;
			message.error(error instanceof Error ? error.message : "审批操作失败");
		} finally {
			setActingId(null);
		}
	};

	const retryDestruction = async (action: LifecycleActionView) => {
		setActingId(action.id);
		try {
			await retryCatalogLifecycleDestruction(action.id);
			message.success("永久销毁重试已提交");
			await load();
			onChanged?.();
		} catch (error) {
			message.error(error instanceof Error ? error.message : "重试失败");
		} finally {
			setActingId(null);
		}
	};

	const startMigrationDryRun = async () => {
		setActingId("migration-dry-run");
		try {
			const run = await createClassificationMigrationDryRun({
				idempotencyKey: `sprint72-${Date.now()}`,
				batchSize: 200,
			});
			window.localStorage.setItem("catalog.classification.migration.run", run.id);
			await refreshMigration(run.id);
			message.success("存量密级 dry-run 已生成；尚未写入密级事实");
		} catch (error) {
			message.error(error instanceof Error ? error.message : "dry-run 失败");
		} finally {
			setActingId(null);
		}
	};

	const operateMigration = async (operation: "apply" | "pause" | "resume" | "reconcile" | "freeze") => {
		if (!migrationRun?.id) return;
		setActingId(`migration-${operation}`);
		try {
			if (operation === "apply") {
				await applyClassificationMigrationBatch(migrationRun.id, migrationRun.batchSize || 200);
			} else if (operation === "pause") {
				await pauseClassificationMigration(migrationRun.id);
			} else if (operation === "resume") {
				await resumeClassificationMigration(migrationRun.id);
			} else if (operation === "reconcile") {
				await reconcileClassificationMigration(migrationRun.id);
			} else {
				await freezeLegacyClassificationWrites(migrationRun.id, {
					reason: "Sprint-72 双读对账通过，旧字段仅允许保持或升高密级",
				});
			}
			await refreshMigration(migrationRun.id);
			message.success("迁移控制状态已更新");
		} catch (error) {
			message.error(error instanceof Error ? error.message : "迁移操作失败");
		} finally {
			setActingId(null);
		}
	};

	return (
		<Drawer
			open={open}
			onClose={onClose}
			width={980}
			destroyOnClose
			title={`${asset?.name || "资产"} · 密级与生命周期工作台`}
			loading={loading}
		>
			<Tabs
				items={[
					{
						key: "classification",
						label: "密级事实",
						children: fact ? (
							<div className="space-y-4">
								{fact.sealed ? null : (
									<Alert type="error" showIcon message="密级尚未封存，资产消费与生命周期动作应被阻断" />
								)}
								<Descriptions bordered size="small" column={2}>
									<Descriptions.Item label="来源声明">
										{classificationText(fact.declaredLevel)}
									</Descriptions.Item>
									<Descriptions.Item label="识别结果">
										{classificationText(fact.detectedLevel)}
									</Descriptions.Item>
									<Descriptions.Item label="人工下限">
										{classificationText(fact.manualFloor)}
									</Descriptions.Item>
									<Descriptions.Item label="有效密级">
										<Tag color={fact.effectiveLevel ? "orange" : "red"}>
											{classificationText(fact.effectiveLevel)}
										</Tag>
									</Descriptions.Item>
									<Descriptions.Item label="最高来源类型">
										{fact.highestSourceType || fact.originType || "-"}
									</Descriptions.Item>
									<Descriptions.Item label="传播状态">
										<Tag color={fact.propagationStatus === "PROPAGATED" ? "green" : "orange"}>
											{fact.propagationStatus || "-"}
										</Tag>
									</Descriptions.Item>
									<Descriptions.Item label="快照版本">v{fact.snapshotVersion ?? 0}</Descriptions.Item>
									<Descriptions.Item label="封存时间">{formatTime(fact.sealedAt)}</Descriptions.Item>
								</Descriptions>
								<div className="grid gap-3 md:grid-cols-3">
									<div className="rounded border border-slate-200 p-3">
										<div className="text-xs text-slate-500">上游资产</div>
										<div className="mt-1 text-lg font-semibold">
											{workspace?.classificationImpact?.upstreamDatasetIds?.length ?? 0}
										</div>
									</div>
									<div className="rounded border border-slate-200 p-3">
										<div className="text-xs text-slate-500">受影响下游</div>
										<div className="mt-1 text-lg font-semibold">
											{workspace?.classificationImpact?.downstreamDatasetIds?.length ?? 0}
										</div>
									</div>
									<div className="rounded border border-slate-200 p-3">
										<div className="text-xs text-slate-500">传播阻断</div>
										<div className="mt-1 text-lg font-semibold">
											{workspace?.classificationImpact?.blockers?.length ?? 0}
										</div>
									</div>
								</div>
								<Table
									size="small"
									pagination={false}
									rowKey={(row, index) => `${row.snapshotVersion || 0}-${index}`}
									dataSource={fact.events || []}
									columns={[
										{ title: "时间", dataIndex: "occurredAt", render: formatTime },
										{ title: "事件", dataIndex: "eventType" },
										{ title: "候选密级", dataIndex: "candidateLevel", render: classificationText },
										{ title: "结果密级", dataIndex: "resultingLevel", render: classificationText },
										{ title: "来源类型", dataIndex: "triggerType" },
										{ title: "版本", dataIndex: "snapshotVersion", render: (value) => `v${value ?? 0}` },
									]}
								/>
							</div>
						) : (
							<Alert type="info" showIcon message="当前资产尚无规范资产键，无法读取密级事实" />
						),
					},
					{
						key: "lifecycle",
						label: "审批与回收站",
						children: datasetId && workspace?.lifecycle ? (
							<div className="space-y-4">
								<Descriptions bordered size="small" column={2}>
									<Descriptions.Item label="生命周期状态">
										{workspace.lifecycle.lifecycleStatus || "-"}
									</Descriptions.Item>
									<Descriptions.Item label="当前密级">
										{classificationText(workspace.lifecycle.effectiveLevel)}
									</Descriptions.Item>
									<Descriptions.Item label="回收站状态">
										{workspace.lifecycle.trash?.status || "未进入回收站"}
									</Descriptions.Item>
									<Descriptions.Item label="保留至">
										{formatTime(workspace.lifecycle.trash?.retainUntil)}
									</Descriptions.Item>
								</Descriptions>
								<Space wrap>
									{availableActions.map((actionType) => (
										<Button
											key={actionType}
											danger={actionType === "PERMANENT_DESTROY"}
											loading={actingId === actionType}
											onClick={() => void submitAction(actionType)}
										>
											{ACTION_LABELS[actionType]}
										</Button>
									))}
								</Space>
								<Table<LifecycleActionView>
									size="small"
									rowKey="id"
									pagination={{ pageSize: 8 }}
									dataSource={workspace.actions || []}
									columns={[
										{ title: "动作", dataIndex: "actionType" },
										{ title: "状态", dataIndex: "status", render: (value) => <Tag>{value}</Tag> },
										{ title: "申请人", dataIndex: "requester" },
										{ title: "密级", dataIndex: "effectiveLevel", render: classificationText },
										{ title: "申请时间", dataIndex: "createdAt", render: formatTime },
										{
											title: "操作",
											render: (_, row) => (
												<Space>
													{["PENDING", "SECOND_APPROVAL_PENDING"].includes(row.status) ? (
														<>
															<Button
																size="small"
																type="primary"
																loading={actingId === row.id}
																onClick={() => void decide(row, "approve")}
															>
																批准
															</Button>
															<Button size="small" onClick={() => void decide(row, "reject")}>
																驳回
															</Button>
														</>
													) : null}
													{row.actionType === "PERMANENT_DESTROY" && row.status === "FAILED" ? (
														<Button danger size="small" onClick={() => void retryDestruction(row)}>
															重试
														</Button>
													) : null}
												</Space>
											),
										},
									]}
								/>
								{Array.isArray(workspace.lifecycle.destructionProofs) &&
								workspace.lifecycle.destructionProofs.length > 0 ? (
									<Alert
										type="success"
										showIcon
										message={`已保留 ${workspace.lifecycle.destructionProofs.length} 份不可变销毁证明`}
										description="证明仅保留对象清单摘要、校验和、审批人与执行结果，不保留已销毁业务数据。"
									/>
								) : null}
							</div>
						) : (
							<Alert
								type="info"
								showIcon
								message="当前资产尚未映射到 DTS 生命周期主体"
								description="完成资产映射后，审批、回收站、恢复和销毁证明将在这里统一处理。"
							/>
						),
					},
					{
						key: "metrics",
						label: "生命周期监控",
						children: metrics ? (
							<div className="space-y-4">
								<Table
									size="small"
									pagination={false}
									rowKey={(row) => `${row.lifecycleBucket}-${row.effectiveLevel}`}
									dataSource={metrics.current || []}
									columns={[
										{
											title: "阶段",
											dataIndex: "lifecycleBucket",
											render: (value) => LIFECYCLE_LABELS[value] || value,
										},
										{ title: "密级", dataIndex: "effectiveLevel", render: classificationText },
										{ title: "资产数量", dataIndex: "assetCount" },
										{
											title: "已知数据量",
											dataIndex: "knownDataVolume",
											render: (value) => (value == null ? "未知" : Number(value).toLocaleString()),
										},
										{ title: "数据量未知资产", dataIndex: "unknownVolumeCount" },
									]}
								/>
								<Table
									size="small"
									pagination={{ pageSize: 10 }}
									rowKey={(row, index) => `${row.day}-${row.stage}-${row.status}-${index}`}
									dataSource={metrics.trends || []}
									columns={[
										{ title: "日期", dataIndex: "day", render: formatTime },
										{ title: "阶段", dataIndex: "stage" },
										{ title: "状态", dataIndex: "status" },
										{ title: "密级", dataIndex: "effectiveLevel", render: classificationText },
										{ title: "事件数", dataIndex: "eventCount" },
									]}
								/>
							</div>
						) : (
							<Alert type="info" showIcon message="当前账号无生命周期专项统计权限或统计尚未生成" />
						),
					},
					{
						key: "migration",
						label: "存量迁移",
						children: (
							<div className="space-y-4">
								<Alert
									type="info"
									showIcon
									message="先 dry-run、再分批 apply、最后双读对账和冻结旧降密入口"
									description="dry-run 只写迁移控制报告，不修改业务表或密级事实。存在缺密级、未知编码或候选降级时，对应记录会被阻断。"
								/>
								<Space wrap>
									<Button
										type="primary"
										loading={actingId === "migration-dry-run"}
										onClick={() => void startMigrationDryRun()}
									>
										新建 Dry-run
									</Button>
									<Button
										disabled={!migrationRun || migrationRun.status === "PAUSED"}
										loading={actingId === "migration-apply"}
										onClick={() => void operateMigration("apply")}
									>
										应用下一批
									</Button>
									<Button
										disabled={migrationRun?.status !== "APPLY_RUNNING"}
										onClick={() => void operateMigration("pause")}
									>
										暂停
									</Button>
									<Button
										disabled={migrationRun?.status !== "PAUSED"}
										onClick={() => void operateMigration("resume")}
									>
										恢复
									</Button>
									<Button disabled={!migrationRun} onClick={() => void operateMigration("reconcile")}>
										双读对账
									</Button>
									<Button
										danger
										disabled={!reconciliation?.readyToFreeze || migrationRun?.status !== "APPLY_COMPLETE"}
										onClick={() => void operateMigration("freeze")}
									>
										冻结旧降密入口
									</Button>
								</Space>
								{migrationRun ? (
									<Descriptions bordered size="small" column={3}>
										<Descriptions.Item label="状态">{migrationRun.status}</Descriptions.Item>
										<Descriptions.Item label="总记录">{migrationRun.totalItems}</Descriptions.Item>
										<Descriptions.Item label="可自动迁移">{migrationRun.eligibleItems}</Descriptions.Item>
										<Descriptions.Item label="已应用">{migrationRun.appliedItems}</Descriptions.Item>
										<Descriptions.Item label="阻断">{migrationRun.blockedItems}</Descriptions.Item>
										<Descriptions.Item label="双读差异">{reconciliation?.mismatchItems ?? "-"}</Descriptions.Item>
										<Descriptions.Item label="游标">{migrationRun.cursorPosition}</Descriptions.Item>
										<Descriptions.Item label="批次">{migrationRun.batchSize}</Descriptions.Item>
										<Descriptions.Item label="报告校验和">
											<span className="font-mono text-xs">{migrationRun.reportChecksum || "-"}</span>
										</Descriptions.Item>
									</Descriptions>
								) : null}
								<Table<ClassificationMigrationItem>
									size="small"
									rowKey="id"
									pagination={{ pageSize: 10 }}
									dataSource={migrationItems}
									columns={[
										{ title: "来源表", dataIndex: "sourceTable" },
										{ title: "旧密级", dataIndex: "legacyLevel", render: classificationText },
										{ title: "计算密级", dataIndex: "computedEffectiveLevel", render: classificationText },
										{
											title: "决策",
											dataIndex: "decision",
											render: (value) => (
												<Tag color={String(value).startsWith("BLOCKED_") ? "red" : "blue"}>{value}</Tag>
											),
										},
										{ title: "应用状态", dataIndex: "applyStatus" },
										{ title: "原因", dataIndex: "decisionReason", ellipsis: true },
									]}
								/>
								<Table<ClassificationWriteFreeze>
									size="small"
									pagination={false}
									rowKey="sourceTable"
									dataSource={writeFreezes}
									columns={[
										{ title: "旧写入口", dataIndex: "sourceTable" },
										{
											title: "冻结状态",
											dataIndex: "enabled",
											render: (value) => <Tag color={value ? "green" : "default"}>{value ? "只升不降" : "未冻结"}</Tag>,
										},
										{ title: "冻结人", dataIndex: "frozenBy" },
										{ title: "冻结时间", dataIndex: "frozenAt", render: formatTime },
									]}
								/>
							</div>
						),
					},
					{
						key: "issues",
						label: `治理通知${issues.length ? ` (${issues.length})` : ""}`,
						children: (
							<Table<GovernanceIssueView>
								size="small"
								rowKey={(row, index) => `${row.issueType}-${row.occurredAt}-${index}`}
								pagination={{ pageSize: 10 }}
								dataSource={issues}
								columns={[
									{ title: "问题类型", dataIndex: "issueType" },
									{ title: "状态", dataIndex: "status", render: (value) => <Tag color="orange">{value}</Tag> },
									{ title: "资产引用", dataIndex: "redactedSubjectRef" },
									{
										title: "责任人/部门",
										render: (_, row) => row.responsibleOwner || row.responsibleDept || "平台治理管理员",
									},
									{ title: "错误摘要", dataIndex: "errorMessage", ellipsis: true },
									{ title: "发生时间", dataIndex: "occurredAt", render: formatTime },
									{
										title: "处理",
										render: (_, row) => (
											<Button
												size="small"
												onClick={() => row.repairRoute && router.push(row.repairRoute)}
											>
												定位修复
											</Button>
										),
									},
								]}
							/>
						),
					},
				]}
			/>
		</Drawer>
	);
}
