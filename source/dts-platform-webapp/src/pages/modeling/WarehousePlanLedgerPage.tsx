import { Alert, Button, Card, Empty, Input, Modal, Result, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { Archive, Edit3, Eye, Layers3, Plus, RefreshCw, Search } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import {
	archiveWarehousePlan,
	getWarehousePlan,
	getWarehousePlanStageProjection,
	listWarehousePlans,
	type WarehousePlanHeader,
	type WarehousePlanStageProjection,
} from "@/api/warehousePlanApi";
import { useUserRoles } from "@/store/userStore";
import { createLatestRequestGuard, hasWarehousePlanCreateAccess } from "./warehousePlanCreateFlow";
import {
	buildWarehousePlanRoute,
	canArchiveWarehousePlan,
	canEditWarehousePlanHeader,
	filterWarehousePlans,
	mergeWarehousePlanHeadersMonotonic,
	replaceWarehousePlanHeader,
	resolveWarehousePlanConflictVersion,
	type WarehousePlanLedgerLifecycleFilter,
	warehouseBlockerMessage,
	warehousePlanLifecycleLabel,
	warehouseStageLabel,
} from "./warehousePlanViewModel";

const { Paragraph, Text, Title } = Typography;

const lifecycleOptions: Array<{ value: WarehousePlanLedgerLifecycleFilter; label: string }> = [
	{ value: "ACTIVE", label: "进行中的规划（默认）" },
	{ value: "ALL", label: "全部生命周期" },
	{ value: "DRAFT", label: "规划中" },
	{ value: "BASELINE_READY", label: "基线已确认" },
	{ value: "DESIGNING", label: "设计中" },
	{ value: "VALIDATING", label: "验证中" },
	{ value: "READY_TO_PUBLISH", label: "待发布" },
	{ value: "PUBLISHED", label: "已发布" },
	{ value: "ARCHIVED", label: "已归档" },
];

const lifecycleColor = (status: WarehousePlanHeader["lifecycleStatus"]): string => {
	if (status === "ARCHIVED") return "default";
	if (status === "PUBLISHED") return "green";
	if (status === "READY_TO_PUBLISH" || status === "VALIDATING") return "gold";
	return "blue";
};

export default function WarehousePlanLedgerPage() {
	const navigate = useNavigate();
	const userRoles = useUserRoles();
	const canMaintainPlan = hasWarehousePlanCreateAccess(userRoles);
	const [plans, setPlans] = useState<WarehousePlanHeader[]>([]);
	const [projections, setProjections] = useState<Record<string, WarehousePlanStageProjection>>({});
	const [projectionFailures, setProjectionFailures] = useState<Set<string>>(new Set());
	const [loading, setLoading] = useState(true);
	const [refreshing, setRefreshing] = useState(false);
	const [projectionLoading, setProjectionLoading] = useState(false);
	const [initialError, setInitialError] = useState(false);
	const [retryWarning, setRetryWarning] = useState(false);
	const [keyword, setKeyword] = useState("");
	const [lifecycleStatus, setLifecycleStatus] = useState<WarehousePlanLedgerLifecycleFilter>("ACTIVE");
	const [ownerId, setOwnerId] = useState("");
	const [archivingPlanIds, setArchivingPlanIds] = useState<Set<string>>(new Set());
	const [writeDeniedPlanIds, setWriteDeniedPlanIds] = useState<Set<string>>(new Set());
	const listGuard = useMemo(() => createLatestRequestGuard(), []);
	const projectionGuard = useMemo(() => createLatestRequestGuard(), []);
	const archiveGuardsRef = useRef(new Map<string, ReturnType<typeof createLatestRequestGuard>>());

	const getArchiveGuard = useCallback((planId: string) => {
		let guard = archiveGuardsRef.current.get(planId);
		if (!guard) {
			guard = createLatestRequestGuard();
			archiveGuardsRef.current.set(planId, guard);
		}
		return guard;
	}, []);

	const loadProjections = useCallback(
		async (headers: WarehousePlanHeader[]) => {
			const isCurrent = projectionGuard.begin();
			setProjectionLoading(true);
			const results = await Promise.allSettled(
				headers.map(async (plan) => ({ planId: plan.id, value: await getWarehousePlanStageProjection(plan.id) })),
			);
			if (!isCurrent()) return;
			const nextProjections: Record<string, WarehousePlanStageProjection> = {};
			const nextFailures = new Set<string>();
			for (let index = 0; index < results.length; index += 1) {
				const result = results[index];
				const planId = headers[index].id;
				if (result.status === "fulfilled") nextProjections[planId] = result.value.value;
				else nextFailures.add(planId);
			}
			setProjections(nextProjections);
			setProjectionFailures(nextFailures);
			setProjectionLoading(false);
		},
		[projectionGuard],
	);

	const loadPlans = useCallback(
		async (preserveExisting: boolean) => {
			const isCurrent = listGuard.begin();
			if (preserveExisting) setRefreshing(true);
			else setLoading(true);
			setInitialError(false);
			setRetryWarning(false);
			try {
				const result = await listWarehousePlans();
				if (!isCurrent()) return;
				const headers = Array.isArray(result) ? result : [];
				setPlans((current) => mergeWarehousePlanHeadersMonotonic(current, headers));
				void loadProjections(headers);
			} catch {
				if (!isCurrent()) return;
				if (preserveExisting) setRetryWarning(true);
				else setInitialError(true);
			} finally {
				if (isCurrent()) {
					setLoading(false);
					setRefreshing(false);
				}
			}
		},
		[listGuard, loadProjections],
	);

	useEffect(() => {
		void loadPlans(false);
		return () => {
			listGuard.invalidate();
			projectionGuard.invalidate();
			for (const guard of archiveGuardsRef.current.values()) guard.invalidate();
			archiveGuardsRef.current.clear();
		};
	}, [listGuard, loadPlans, projectionGuard]);

	const filteredPlans = useMemo(
		() => filterWarehousePlans(plans, { keyword, lifecycleStatus, ownerId }),
		[keyword, lifecycleStatus, ownerId, plans],
	);

	const ownerOptions = useMemo(
		() => [
			{ value: "", label: "全部负责人" },
			...[...new Set(plans.map((plan) => plan.ownerId))]
				.sort((left, right) => left.localeCompare(right))
				.map((value) => ({ value, label: value })),
		],
		[plans],
	);

	const updatePlan = useCallback((updated: WarehousePlanHeader) => {
		setPlans((current) => replaceWarehousePlanHeader(current, updated));
	}, []);

	const verifyArchiveResult = useCallback(
		async (plan: WarehousePlanHeader, isCurrent: () => boolean) => {
			try {
				const latest = await getWarehousePlan(plan.id);
				if (!isCurrent()) return;
				updatePlan(latest);
				if (latest.lifecycleStatus === "ARCHIVED") toast.success("规划已归档（已核对服务端状态）");
				else toast.error("归档失败，已刷新服务端状态，请重试");
			} catch {
				if (!isCurrent()) return;
				toast.error("无法确认归档结果，请稍后重新加载规划列表");
			}
		},
		[updatePlan],
	);

	const archivePlan = useCallback(
		async (plan: WarehousePlanHeader) => {
			const isCurrent = getArchiveGuard(plan.id).begin();
			setArchivingPlanIds((current) => new Set(current).add(plan.id));
			try {
				const archived = await archiveWarehousePlan(plan.id, plan.version);
				if (!isCurrent()) return;
				updatePlan(archived);
				toast.success("规划已归档，可通过“已归档”筛选查看");
			} catch (error) {
				if (!isCurrent()) return;
				const response = (error as { response?: { status?: number } })?.response;
				if (response?.status === 403) {
					setWriteDeniedPlanIds((current) => new Set(current).add(plan.id));
					toast.error("当前账号没有归档规划的权限");
					return;
				}
				if (response?.status === 404) {
					toast.error("规划已不可访问，正在刷新列表");
					void loadPlans(true);
					return;
				}
				const currentVersion = resolveWarehousePlanConflictVersion(error);
				if (currentVersion != null) {
					try {
						const latest = await getWarehousePlan(plan.id);
						if (!isCurrent()) return;
						updatePlan(latest);
						if (latest.lifecycleStatus === "ARCHIVED") {
							toast.info("该规划已被其他用户归档");
							return;
						}
						Modal.confirm({
							title: "规划已变化，请重新确认归档",
							content: `“${latest.name}”当前为版本 ${currentVersion}。请核对最新状态后再次确认。`,
							okText: "基于最新版归档",
							cancelText: "取消",
							okButtonProps: { danger: true },
							onOk: () => archivePlan(latest),
						});
					} catch {
						if (isCurrent()) toast.error("最新版加载失败，本次未自动覆盖，请稍后重试");
					}
					return;
				}
				await verifyArchiveResult(plan, isCurrent);
			} finally {
				if (isCurrent()) {
					setArchivingPlanIds((current) => {
						const next = new Set(current);
						next.delete(plan.id);
						return next;
					});
				}
			}
		},
		[getArchiveGuard, loadPlans, updatePlan, verifyArchiveResult],
	);

	const confirmArchive = useCallback(
		(plan: WarehousePlanHeader) => {
			Modal.confirm({
				title: `归档“${plan.name}”？`,
				content: "归档后将从默认列表隐藏，历史计划仍可通过“已归档”筛选查看；本操作不会删除数据。",
				okText: "确认归档",
				cancelText: "取消",
				okButtonProps: { danger: true },
				onOk: () => archivePlan(plan),
			});
		},
		[archivePlan],
	);

	const columns = useMemo<ColumnsType<WarehousePlanHeader>>(
		() => [
			{
				title: "规划名称 / 编码",
				dataIndex: "name",
				key: "name",
				width: 230,
				render: (_, plan) => (
					<div className="min-w-0">
						<Button
							type="link"
							className="h-auto max-w-full p-0 text-left font-semibold"
							onClick={() => navigate(buildWarehousePlanRoute(plan.id, "overview", { mode: "view" }))}
						>
							<span className="block truncate">{plan.name}</span>
						</Button>
						<div className="mt-1 truncate font-mono text-xs text-slate-500">{plan.code}</div>
					</div>
				),
			},
			{
				title: "开始方式",
				dataIndex: "onboardingMode",
				key: "onboardingMode",
				width: 145,
				render: (value: WarehousePlanHeader["onboardingMode"]) =>
					value === "ASSET_FIRST" ? "从现有数据开始" : "从业务目标开始",
			},
			{
				title: "负责人 / 部门",
				dataIndex: "ownerId",
				key: "ownerId",
				width: 180,
				render: (_, plan) => (
					<div>
						<div>{plan.ownerId}</div>
						<Text type="secondary">{plan.ownerDepartmentId || "未设置"}</Text>
					</div>
				),
			},
			{
				title: "生命周期",
				dataIndex: "lifecycleStatus",
				key: "lifecycleStatus",
				width: 125,
				render: (status: WarehousePlanHeader["lifecycleStatus"]) => (
					<Tag color={lifecycleColor(status)}>{warehousePlanLifecycleLabel(status)}</Tag>
				),
			},
			{
				title: "当前阶段 / 首要阻塞",
				key: "stage",
				width: 260,
				render: (_, plan) => {
					if (projectionFailures.has(plan.id)) return <Tag>证据未知</Tag>;
					const projection = projections[plan.id];
					if (!projection) return <Text type="secondary">{projectionLoading ? "正在读取证据" : "证据未知"}</Text>;
					return (
						<div>
							<div className="font-medium text-slate-900">
								{projection.currentStage ? warehouseStageLabel(projection.currentStage) : "全部阶段已有当前证据"}
							</div>
							<Text type="secondary">
								{projection.primaryBlocker
									? `首要阻塞：${warehouseBlockerMessage(projection.primaryBlocker.code, projection.primaryBlocker.message)}`
									: "暂无首要阻塞"}
							</Text>
						</div>
					);
				},
			},
			{
				title: "操作",
				key: "actions",
				fixed: "right",
				width: 205,
				render: (_, plan) => (
					<Space size={0} wrap>
						<Button
							type="link"
							size="small"
							icon={<Eye size={14} />}
							onClick={() => navigate(buildWarehousePlanRoute(plan.id, "overview", { mode: "view" }))}
						>
							查看
						</Button>
						{canEditWarehousePlanHeader(canMaintainPlan, plan.lifecycleStatus) && !writeDeniedPlanIds.has(plan.id) ? (
							<Button
								type="link"
								size="small"
								icon={<Edit3 size={14} />}
								onClick={() => navigate(buildWarehousePlanRoute(plan.id, "overview", { mode: "edit" }))}
							>
								编辑
							</Button>
						) : null}
						{canArchiveWarehousePlan(canMaintainPlan, plan.lifecycleStatus) && !writeDeniedPlanIds.has(plan.id) ? (
							<Button
								type="link"
								danger
								size="small"
								icon={<Archive size={14} />}
								loading={archivingPlanIds.has(plan.id)}
								onClick={() => confirmArchive(plan)}
							>
								归档
							</Button>
						) : null}
						{writeDeniedPlanIds.has(plan.id) ? <Text type="secondary">只读</Text> : null}
					</Space>
				),
			},
		],
		[
			archivingPlanIds,
			canMaintainPlan,
			confirmArchive,
			navigate,
			projectionFailures,
			projectionLoading,
			projections,
			writeDeniedPlanIds,
		],
	);

	if (loading) {
		return (
			<div className="mx-auto w-full max-w-[1480px] p-6">
				<Card loading />
			</div>
		);
	}

	if (initialError) {
		return (
			<div className="mx-auto w-full max-w-[1180px] p-6" data-testid="warehouse-plan-ledger-load-error">
				<Result
					status="warning"
					title="建设规划暂时不可用"
					subTitle="规划列表读取失败，未把错误状态显示为空台账。"
					extra={
						<Button type="primary" icon={<RefreshCw size={15} />} onClick={() => void loadPlans(false)}>
							重新加载
						</Button>
					}
				/>
			</div>
		);
	}

	return (
		<div className="mx-auto w-full max-w-[1480px] space-y-4 p-4 md:p-6" data-testid="warehouse-plan-ledger">
			<header
				className="overflow-hidden rounded-[22px] border border-slate-200 px-5 py-5 md:px-7"
				style={{
					background:
						"linear-gradient(120deg, rgba(238,246,252,0.98) 0%, rgba(249,251,252,0.98) 58%, rgba(247,244,235,0.96) 100%)",
				}}
			>
				<div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
					<div className="max-w-3xl">
						<div className="mb-2 flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">
							<Layers3 size={15} /> Warehouse planning
						</div>
						<Title level={2} style={{ margin: 0 }}>
							建设规划
						</Title>
						<Paragraph className="mb-0 mt-2 text-slate-600">
							查找、修正规划责任信息并管理生命周期；阶段完成度仍由服务端证据计算。
						</Paragraph>
					</div>
					<Button
						type="primary"
						size="large"
						icon={<Plus size={16} />}
						data-testid="warehouse-plan-ledger-primary-action"
						disabled={!canMaintainPlan}
						title={canMaintainPlan ? undefined : "当前账号没有规划维护权限"}
						onClick={() => navigate("/modeling/workbench?create=1")}
					>
						新建规划
					</Button>
				</div>
			</header>

			{retryWarning ? (
				<Alert
					data-testid="warehouse-plan-ledger-retry-warning"
					type="warning"
					showIcon
					message="规划列表刷新失败"
					description="已有规划仍保留，可继续查看；稍后可重新刷新。"
					action={
						<Button loading={refreshing} onClick={() => void loadPlans(true)}>
							重新刷新
						</Button>
					}
				/>
			) : null}

			<Card className="overflow-hidden border-slate-200" styles={{ body: { padding: 0 } }}>
				<div className="grid grid-cols-1 gap-3 border-b border-slate-200 p-4 md:grid-cols-[minmax(220px,1fr)_220px_200px_auto] md:items-center">
					<Input
						aria-label="关键字"
						allowClear
						prefix={<Search size={15} />}
						placeholder="搜索规划名称、编码、目标或范围"
						value={keyword}
						onChange={(event) => setKeyword(event.target.value)}
					/>
					<Select
						aria-label="生命周期"
						value={lifecycleStatus}
						options={lifecycleOptions}
						onChange={setLifecycleStatus}
					/>
					<Select
						aria-label="负责人"
						showSearch
						optionFilterProp="label"
						value={ownerId}
						options={ownerOptions}
						onChange={setOwnerId}
					/>
					<Button icon={<RefreshCw size={15} />} loading={refreshing} onClick={() => void loadPlans(true)}>
						刷新
					</Button>
				</div>

				{plans.length === 0 ? (
					<div className="px-6 py-20" data-testid="warehouse-plan-ledger-empty">
						<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无建设规划；请使用页面右上角的新建规划开始。" />
					</div>
				) : filteredPlans.length === 0 ? (
					<div className="px-6 py-16">
						<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合当前筛选条件的规划" />
					</div>
				) : (
					<Table<WarehousePlanHeader>
						rowKey="id"
						columns={columns}
						dataSource={filteredPlans}
						pagination={{ pageSize: 10, showSizeChanger: false, showTotal: (total) => `共 ${total} 条规划` }}
						scroll={{ x: 1120 }}
					/>
				)}
			</Card>
		</div>
	);
}
