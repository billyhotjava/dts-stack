import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Progress, Select, Space, Spin, Tag, Timeline, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { useRouter } from "@/routes/hooks";
import goldenChainService, {
	type GoldenChainDetail,
	type GoldenChainStage,
	type GoldenChainStageSnapshot,
	type GoldenChainStageStatus,
	type GoldenChainSummary,
} from "@/api/services/goldenChainService";

const { Text } = Typography;

const STAGE_SEQUENCE: Record<GoldenChainStage, number> = {
	DRAFT: 0,
	SOURCE_READY: 10,
	INGESTION_READY: 20,
	ODS_READY: 30,
	MODEL_READY: 40,
	GOVERNANCE_READY: 50,
	RELEASE_READY: 60,
	CONSUMABLE: 70,
	OPERATED: 80,
};

const STATUS_LABELS: Record<GoldenChainStageStatus, string> = {
	PENDING: "待上游",
	READY: "已完成",
	BLOCKED: "阻断",
	SKIPPED: "已跳过",
};

const STATUS_COLORS: Record<GoldenChainStageStatus, string> = {
	PENDING: "default",
	READY: "green",
	BLOCKED: "red",
	SKIPPED: "blue",
};

const consumptionEntries = [
	{
		title: "报表数据集",
		desc: "发布到报表工厂，业务人员按主题选择字段、筛选条件和展示方式。",
		route: "/bi/report-factory",
		action: "进入报表工厂",
		stage: "CONSUMABLE" as GoldenChainStage,
	},
	{
		title: "指标入口",
		desc: "统一沉淀经营指标、维度口径和最近更新状态，减少重复解释。",
		route: "/bi-apps/metrics/center",
		action: "查看指标中心",
		stage: "RELEASE_READY" as GoldenChainStage,
	},
	{
		title: "数据 API",
		desc: "把治理后的资产发布成接口目录，支持调用审计、授权和限流。",
		route: "/services/apis",
		action: "管理接口目录",
		stage: "CONSUMABLE" as GoldenChainStage,
	},
	{
		title: "数据产品",
		desc: "面向固定消费场景管理版本、说明、关联数据集和服务等级。",
		route: "/services/products",
		action: "查看数据产品",
		stage: "CONSUMABLE" as GoldenChainStage,
	},
];

const permissionItems = [
	{ target: "资产门户", scope: "目录可见、字段可见、申请入口" },
	{ target: "指标入口", scope: "指标可见、维度可见、口径可见" },
	{ target: "BI 报表", scope: "数据集授权、行列裁剪、导出审计" },
	{ target: "数据大屏", scope: "大屏引用资产与访问审计" },
	{ target: "数据 API", scope: "接口授权、调用令牌、配额控制" },
	{ target: "数据产品", scope: "产品订阅、版本发布、服务边界" },
];

const acceptanceItems = [
	{ name: "客户验收包", value: "业务场景、数据链路、访问路径和责任人汇总" },
	{ name: "演示数据", value: "按传统行业常见主题准备样例记录与统计口径" },
	{ name: "截图清单", value: "覆盖接入、治理、报表、接口、运维五类界面" },
	{ name: "调用统计", value: "展示接口调用、报表访问、指标查看的近况" },
	{ name: "失败恢复", value: "补数入口、告警记录和任务实例可追踪" },
];

export default function Page() {
	const router = useRouter();
	const [chains, setChains] = useState<GoldenChainSummary[]>([]);
	const [selectedChainKey, setSelectedChainKey] = useState<string>();
	const [detail, setDetail] = useState<GoldenChainDetail | null>(null);
	const [loading, setLoading] = useState(false);
	const [detailLoading, setDetailLoading] = useState(false);

	useEffect(() => {
		const loadChains = async () => {
			setLoading(true);
			try {
				const list = await goldenChainService.list();
				const nextChains = Array.isArray(list) ? list : [];
				setChains(nextChains);
				setSelectedChainKey((current) => current || nextChains[0]?.chainKey);
			} catch {
				setChains([]);
			} finally {
				setLoading(false);
			}
		};
		void loadChains();
	}, []);

	useEffect(() => {
		if (!selectedChainKey) {
			setDetail(null);
			return;
		}
		const loadDetail = async () => {
			setDetailLoading(true);
			try {
				const nextDetail = await goldenChainService.detail(selectedChainKey);
				setDetail(nextDetail || null);
			} catch {
				setDetail(null);
			} finally {
				setDetailLoading(false);
			}
		};
		void loadDetail();
	}, [selectedChainKey]);

	const selectedChain = useMemo(
		() => chains.find((item) => item.chainKey === selectedChainKey) || chains[0],
		[chains, selectedChainKey],
	);

	const stageSnapshots = useMemo(() => detail?.stages || [], [detail]);
	const stageByName = useMemo(() => {
		return stageSnapshots.reduce<Partial<Record<GoldenChainStage, GoldenChainStageSnapshot>>>((acc, item) => {
			acc[item.stage] = item;
			return acc;
		}, {});
	}, [stageSnapshots]);

	const completionPercent = useMemo(() => {
		if (stageSnapshots.length > 0) {
			const completed = stageSnapshots.filter((item) => item.status === "READY" || item.status === "SKIPPED").length;
			return Math.round((completed / stageSnapshots.length) * 100);
		}
		if (selectedChain?.currentStage) {
			return Math.round((STAGE_SEQUENCE[selectedChain.currentStage] / STAGE_SEQUENCE.OPERATED) * 100);
		}
		return 0;
	}, [selectedChain, stageSnapshots]);

	const readyCount = chains.filter((item) => item.status === "READY").length;
	const blockedCount = chains.filter((item) => item.status === "BLOCKED").length;
	const blockedStages = stageSnapshots.filter((item) => item.status === "BLOCKED");

	const statusForStage = (stage: GoldenChainStage) => {
		const snapshot = stageByName[stage];
		return (
			snapshot?.status ||
			(selectedChain && STAGE_SEQUENCE[selectedChain.currentStage] >= STAGE_SEQUENCE[stage] ? "READY" : "PENDING")
		);
	};

	const renderStatusTag = (status?: GoldenChainStageStatus) => {
		const safeStatus = status || "PENDING";
		return <Tag color={STATUS_COLORS[safeStatus]}>{STATUS_LABELS[safeStatus]}</Tag>;
	};

	return (
		<div className="space-y-6">
			<PageHeader
				title="业务消费工作台"
				actions={
					<Space wrap>
						<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
						<Button onClick={() => router.push("/ops/overview")}>查看运维</Button>
						<Button type="primary" onClick={() => router.push("/bi/report-factory")}>
							生成报表
						</Button>
					</Space>
				}
			/>

			<Alert
				type={blockedCount > 0 ? "warning" : "info"}
				showIcon
				message={blockedCount > 0 ? "存在待处理的黄金链路阻断" : "从数据入湖到业务消费的一站式视图"}
				description={
					selectedChain
						? `${selectedChain.displayName} 当前处于「${selectedChain.currentStageLabel}」，负责人：${selectedChain.owner || "-"}。`
						: "当前还没有可展示的黄金链路实例。"
				}
			/>

			<div className="grid gap-4 xl:grid-cols-[minmax(0,1.4fr)_minmax(360px,0.8fr)]">
				<Card title="交付链路状态">
					<div className="mb-4 grid gap-4 md:grid-cols-4">
						<div>
							<Text type="secondary">链路完成度</Text>
							<div className="mt-2">
								<Progress percent={completionPercent} status={blockedStages.length > 0 ? "exception" : "active"} />
							</div>
						</div>
						<div>
							<Text type="secondary">链路实例</Text>
							<div className="mt-2 text-2xl font-semibold">{chains.length}</div>
						</div>
						<div>
							<Text type="secondary">已就绪</Text>
							<div className="mt-2 text-2xl font-semibold">{readyCount}</div>
						</div>
						<div>
							<Text type="secondary">待处理</Text>
							<div className="mt-2 text-2xl font-semibold">{blockedCount}</div>
						</div>
					</div>
					<div className="mb-4">
						<Select
							loading={loading}
							value={selectedChain?.chainKey}
							placeholder="选择黄金链路"
							className="w-full"
							options={chains.map((item) => ({
								label: `${item.displayName} (${item.sourceKind})`,
								value: item.chainKey,
							}))}
							onChange={setSelectedChainKey}
						/>
					</div>
					<Spin spinning={detailLoading}>
						{stageSnapshots.length > 0 ? (
							<Timeline
								items={stageSnapshots.map((item) => ({
									color: item.status === "BLOCKED" ? "red" : item.status === "READY" ? "green" : "gray",
									children: (
										<div className="space-y-1">
											<div className="flex flex-wrap items-center gap-2">
												<span className="font-medium">{item.stageLabel}</span>
												{renderStatusTag(item.status)}
												<Text type="secondary">{item.owner || "-"}</Text>
											</div>
											{item.failureReason && <div className="text-sm text-red-600">{item.failureReason}</div>}
											{item.nextAction && <Text type="secondary">{item.nextAction}</Text>}
											{item.evidenceRef && <div className="text-xs text-gray-500">{item.evidenceRef}</div>}
										</div>
									),
								}))}
							/>
						) : (
							<EmptyState
								compact
								title={loading ? "正在加载黄金链路" : "暂无阶段快照"}
								description="黄金链路实例创建后，这里会展示每个阶段的状态、证据和下一步动作。"
							/>
						)}
					</Spin>
				</Card>

				<Card title="客户验收包" extra={renderStatusTag(statusForStage("CONSUMABLE"))}>
					{selectedChain ? (
						<div className="space-y-3">
							{acceptanceItems.map((item) => (
								<div key={item.name} className="rounded border border-dashed border-gray-200 p-3">
									<div className="flex items-center justify-between gap-3">
										<span className="font-medium">{item.name}</span>
										<Tag color={completionPercent >= 80 ? "blue" : "default"}>
											{completionPercent >= 80 ? "可展示" : "待补齐"}
										</Tag>
									</div>
									<Text type="secondary">{item.value}</Text>
								</div>
							))}
						</div>
					) : (
						<EmptyState compact title="暂无验收包" description="选择黄金链路后展示客户验收材料状态。" />
					)}
				</Card>
			</div>

			<div className="grid gap-4 lg:grid-cols-4 md:grid-cols-2">
				{consumptionEntries.map((item) => (
					<Card key={item.title} title={item.title} extra={renderStatusTag(statusForStage(item.stage))}>
						<div className="flex min-h-[172px] flex-col justify-between gap-4">
							<Text type="secondary">{item.desc}</Text>
							<Button block onClick={() => router.push(item.route)}>
								{item.action}
							</Button>
						</div>
					</Card>
				))}
			</div>

			<Card title="权限一致" extra={renderStatusTag(statusForStage("RELEASE_READY"))}>
				<div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
					{permissionItems.map((item) => (
						<div key={item.target} className="rounded border border-gray-200 p-3">
							<div className="flex items-center justify-between gap-3">
								<span className="font-medium">{item.target}</span>
								{renderStatusTag(statusForStage("RELEASE_READY"))}
							</div>
							<Text type="secondary">{item.scope}</Text>
						</div>
					))}
				</div>
			</Card>
		</div>
	);
}
