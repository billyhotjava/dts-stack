import { useEffect, useMemo, useState } from "react";
import { Alert, Button, Card, Progress, Space, Spin, Tag, Typography } from "antd";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { useRouter } from "@/routes/hooks";
import goldenChainService, {
	type GoldenChainDetail,
	type GoldenChainSummary,
} from "@/api/services/goldenChainService";
import {
	buildDataManagementThemes,
	type DataManagementThemeState,
	type DataManagementTone,
} from "./dataManagementThemeModel";

const { Text } = Typography;

const TONE_COLOR: Record<DataManagementTone, string> = {
	default: "default",
	processing: "blue",
	success: "green",
	warning: "orange",
	danger: "red",
};

const statusTag = (label: string, tone: DataManagementTone) => <Tag color={TONE_COLOR[tone]}>{label}</Tag>;

export type DataManagementWorkbenchPageProps = {
	embedded?: boolean;
};

export default function Page({ embedded = false }: DataManagementWorkbenchPageProps) {
	const router = useRouter();
	const [chains, setChains] = useState<GoldenChainSummary[]>([]);
	const [detailsByChainKey, setDetailsByChainKey] = useState<Record<string, GoldenChainDetail | undefined>>({});
	const [selectedThemeKey, setSelectedThemeKey] = useState<string>("");
	const [loading, setLoading] = useState(false);

	useEffect(() => {
		let cancelled = false;
		const load = async () => {
			setLoading(true);
			try {
				const list = await goldenChainService.list();
				const safeChains = Array.isArray(list) ? list : [];
				if (cancelled) return;
				setChains(safeChains);
				const detailEntries = await Promise.all(
					safeChains.map(async (chain) => {
						try {
							const detail = await goldenChainService.detail(chain.chainKey);
							return [chain.chainKey, detail] as const;
						} catch {
							return [chain.chainKey, undefined] as const;
						}
					}),
				);
				if (!cancelled) {
					setDetailsByChainKey(Object.fromEntries(detailEntries));
				}
			} catch {
				if (!cancelled) {
					setChains([]);
					setDetailsByChainKey({});
				}
			} finally {
				if (!cancelled) setLoading(false);
			}
		};
		void load();
		return () => {
			cancelled = true;
		};
	}, []);

	const themes = useMemo(() => buildDataManagementThemes(chains, detailsByChainKey), [chains, detailsByChainKey]);
	const selectedTheme = useMemo<DataManagementThemeState | undefined>(
		() => themes.find((theme) => theme.key === selectedThemeKey) || themes[0],
		[themes, selectedThemeKey],
	);
	const blockedThemes = themes.filter(
		(theme) => theme.governance.tone === "warning" || theme.operation.tone === "warning",
	);

	const hasThemes = themes.length > 0;
	const themeSummary = hasThemes ? "已加载现场配置主题" : "待现场定义业务主题";
	const failureReason = selectedTheme?.failureReason || "";
	const nextAction = selectedTheme?.nextAction || selectedTheme?.primaryAction.label;
	const evidenceRefs = selectedTheme?.evidenceRefs || [];
	const headerActions = (
		<Space wrap>
			<Button onClick={() => router.push("/foundation/data-sources")}>配置数据源</Button>
			<Button type={blockedThemes.length > 0 ? "primary" : "default"} onClick={() => router.push("/workbench/todo")}>
				处理阻断项
			</Button>
			<Button onClick={() => router.push("/governance/quality")}>治理检查</Button>
			<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
			<Button onClick={() => router.push("/bi/dashboards")}>创建报表</Button>
			<Button onClick={() => router.push("/services/apis")}>发布数据 API</Button>
			<Button onClick={() => router.push("/ops/overview")}>查看运行</Button>
		</Space>
	);

	return (
		<div className="space-y-6" data-testid="data-management-workbench-section">
			{embedded ? (
				<div className="flex flex-wrap items-start justify-between gap-4">
					<div>
						<Typography.Title level={4} style={{ margin: 0 }}>
							数据管理
						</Typography.Title>
						<Text type="secondary">从数据源、黄金链路、治理阻断到消费发布的现场配置闭环。</Text>
					</div>
					{headerActions}
				</div>
			) : (
				<PageHeader title="数据管理工作台" actions={headerActions} />
			)}

			<Alert
				type={blockedThemes.length > 0 ? "warning" : "info"}
				showIcon
				message={blockedThemes.length > 0 ? "存在需要处理的主题" : themeSummary}
				description="业务主题需在客户现场按组织、报表和资产口径定义，产品不内置演示场景；配置完成后再展示数据可用、治理状态、消费状态和运行健康。"
			/>

			<Spin spinning={loading}>
				{hasThemes ? (
					<div className="grid gap-4 xl:grid-cols-4 md:grid-cols-2">
						{themes.map((theme) => (
							<Card
								key={theme.key}
								hoverable
								title={theme.title}
								extra={<Tag>{theme.chainCount} 条链路</Tag>}
								className={selectedTheme?.key === theme.key ? "border-primary" : undefined}
								onClick={() => setSelectedThemeKey(theme.key)}
							>
								<div className="flex min-h-[248px] flex-col justify-between gap-4">
									<Text type="secondary">{theme.description}</Text>
									<Progress percent={theme.progressPercent} size="small" />
									<div className="grid grid-cols-2 gap-2 text-sm">
										<div>
											<div className="mb-1 text-muted-foreground">数据可用</div>
											{statusTag(theme.dataAvailability.label, theme.dataAvailability.tone)}
										</div>
										<div>
											<div className="mb-1 text-muted-foreground">治理状态</div>
											{statusTag(theme.governance.label, theme.governance.tone)}
										</div>
										<div>
											<div className="mb-1 text-muted-foreground">消费状态</div>
											{statusTag(theme.consumption.label, theme.consumption.tone)}
										</div>
										<div>
											<div className="mb-1 text-muted-foreground">运行健康</div>
											{statusTag(theme.operation.label, theme.operation.tone)}
										</div>
									</div>
									<Button block onClick={(event) => {
										event.stopPropagation();
										router.push(theme.primaryAction.route);
									}}>
										{theme.primaryAction.label}
									</Button>
								</div>
							</Card>
						))}
					</div>
				) : (
					<EmptyState
						compact
						title="待现场定义业务主题"
						description="数据管理工作台不内置演示场景。请在客户现场确认主题口径，并完成数据源、资产和交付链路配置后再展示主题卡片。"
						actions={
							<Space wrap>
								<Button type="primary" onClick={() => router.push("/foundation/data-sources")}>
									配置数据源
								</Button>
								<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
							</Space>
						}
					/>
				)}
			</Spin>

			<Card
				title={selectedTheme?.title || "现场主题配置"}
				extra={selectedTheme ? statusTag(selectedTheme.consumption.label, selectedTheme.consumption.tone) : null}
			>
				{selectedTheme && selectedTheme.chainCount > 0 ? (
					<div className="grid gap-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(320px,0.8fr)]">
						<div className="space-y-3">
							{selectedTheme.relatedChains.map((chain) => (
								<div key={chain.chainKey} className="rounded border border-gray-200 p-3">
									<div className="flex flex-wrap items-center justify-between gap-3">
										<div>
											<div className="font-medium">{chain.displayName}</div>
											<Text type="secondary">负责人：{chain.owner || "-"}</Text>
										</div>
										{statusTag(chain.currentStageLabel || chain.status, chain.status === "BLOCKED" ? "warning" : "success")}
									</div>
								</div>
							))}
						</div>
						<div className="space-y-3">
							<div className="rounded border border-dashed border-gray-200 p-3">
								<div className="font-medium">下一步动作</div>
								<Text type={failureReason ? "danger" : "secondary"}>{nextAction}</Text>
								{failureReason ? <div className="mt-2 text-sm text-red-600">{failureReason}</div> : null}
							</div>
							<div className="rounded border border-dashed border-gray-200 p-3">
								<div className="font-medium">验收证据</div>
								{evidenceRefs.length > 0 ? (
									<div className="mt-2 flex flex-wrap gap-2">
										{evidenceRefs.map((item) => (
											<Tag key={item}>{item}</Tag>
										))}
									</div>
								) : (
									<Text type="secondary">链路运行后自动汇总证据编号。</Text>
								)}
							</div>
							<Space wrap>
								<Button onClick={() => router.push("/foundation/data-sources")}>配置数据源</Button>
								<Button onClick={() => router.push("/workbench/todo")}>处理阻断项</Button>
								<Button onClick={() => router.push("/governance/quality")}>治理检查</Button>
								<Button onClick={() => router.push("/catalog/assets")}>查看资产</Button>
								<Button onClick={() => router.push("/bi/dashboards")}>创建报表</Button>
								<Button onClick={() => router.push("/services/apis")}>发布数据 API</Button>
								<Button onClick={() => router.push("/ops/overview")}>查看运行</Button>
							</Space>
						</div>
					</div>
				) : (
					<EmptyState
						compact
						title="暂无现场业务主题"
						description="当前环境还没有客户现场确认的业务主题。完成主题口径、数据来源和交付链路配置后，这里才会展示主题状态和证据。"
						actions={
							<Space wrap>
								<Button type="primary" onClick={() => router.push("/foundation/data-sources")}>
									配置数据源
								</Button>
								<Button onClick={() => router.push("/workbench/todo")}>处理阻断项</Button>
							</Space>
						}
					/>
				)}
			</Card>
		</div>
	);
}
