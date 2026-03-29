import { useState } from "react";
import type { Locale } from "../../i18n";
import { ErrorNotice } from "../../components/ErrorNotice";
import { Input, Spin, Button, Card, Tag, Select, Tabs } from "antd";
import type {
	ProjectCockpitOption,
	ProjectCockpitSettingsResponse,
	ProjectCockpitSummaryResponse,
} from "../../api/analyticsApi";
import {
	createProjectCockpitScopeResetPatch,
	type ProjectCockpitTheme,
} from "./projectCockpitQueryState";
import { useProjectCockpitContext } from "./ProjectCockpitContext";
import OverviewTrendView from "./views/OverviewTrendView";
import ExecutionView from "./views/ExecutionView";
import RiskAttributionView from "./views/RiskAttributionView";
import MajorProjectTreeView from "./views/MajorProjectTreeView";
import DataSupportView from "./views/DataSupportView";
import { DrillDownDrawer } from "./components/DrillDownDrawer";
import "./projectCockpit.css";

const THEME_ITEMS: Array<{ id: ProjectCockpitTheme; label: string }> = [
	{ id: "overview", label: "总览趋势" },
	{ id: "execution", label: "计划执行" },
	{ id: "risk", label: "风险归因" },
	{ id: "tree", label: "项目树" },
	{ id: "support", label: "口径支撑" },
];

type Props = {
	locale: Locale;
	settings: ProjectCockpitSettingsResponse | null;
	settingsLoading: boolean;
	settingsSaving: boolean;
	settingsError: unknown;
	onPublishPeriod: (periodStart: string, periodEnd: string) => Promise<void>;
	summary: ProjectCockpitSummaryResponse | null;
	summaryLoading: boolean;
	summaryError: unknown;
};

function optionList(options?: ProjectCockpitOption[]) {
	return [
		{ value: "", label: "全部" },
		...(options ?? []).map((item) => ({
			value: String(item.value ?? ""),
			label: String(item.label ?? item.value ?? ""),
		})),
	];
}

export function ProjectCockpitLayout({
	locale,
	settings,
	settingsLoading,
	settingsSaving,
	settingsError,
	onPublishPeriod,
	summary,
	summaryLoading,
	summaryError,
}: Props) {
	const { queryState, effectiveQueryState, updateQueryState, setTheme } = useProjectCockpitContext();
	const hero = summary?.hero;
	const filters = summary?.filters;
	const spotlight = summary?.spotlight;
	const [filterOpen, setFilterOpen] = useState(false);
	const canPublish = Boolean(settings?.canPublish);
	const hasPeriod = Boolean(effectiveQueryState.dateFrom && effectiveQueryState.dateTo);
	const publishedLabel = settings?.updatedAt
		? `已发布 ${settings.updatedAt}${settings.updatedBy ? ` · ${settings.updatedBy}` : ""}`
		: "尚未发布统一统计周期";

	return (
		<div className="flex flex-col gap-3.5">
			{/* Period card */}
			<Card style={{ background: "radial-gradient(circle at top left, rgba(37,99,235,0.1), transparent 40%), linear-gradient(135deg, rgba(255,255,255,0.98), rgba(244,249,255,0.96))" }}>
				<div className="grid grid-cols-[minmax(220px,1.1fr)_minmax(280px,1.4fr)_minmax(180px,0.8fr)] gap-4 items-end max-[1200px]:grid-cols-1">
					<div className="flex flex-col gap-2">
						<div className="text-xl font-bold tracking-tight">统一统计周期</div>
						<div className="text-[13px] leading-relaxed text-text-secondary">
							项目看板按 t1/t2 统一口径计算，保存后所有人看到同一版报表。
						</div>
						<div className="text-[13px] leading-relaxed text-text-secondary">{publishedLabel}</div>
					</div>
					<div className="grid grid-cols-2 gap-3 max-[768px]:grid-cols-1">
						<div>
							<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>t1 统计开始</label>
							<Input
								type="date"
								value={effectiveQueryState.dateFrom}
								onChange={(event) => updateQueryState({ dateFrom: event.target.value })}
								disabled={settingsLoading}
							/>
						</div>
						<div>
							<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>t2 统计结束</label>
							<Input
								type="date"
								value={effectiveQueryState.dateTo}
								onChange={(event) => updateQueryState({ dateTo: event.target.value })}
								disabled={settingsLoading}
							/>
						</div>
					</div>
					<div className="flex flex-col gap-2">
						{canPublish ? (
							<Button
								type="primary"
								size="small"
								loading={settingsSaving}
								disabled={!hasPeriod || settingsLoading}
								onClick={() => {
									if (!effectiveQueryState.dateFrom || !effectiveQueryState.dateTo) {
										return;
									}
									void onPublishPeriod(effectiveQueryState.dateFrom, effectiveQueryState.dateTo).catch(() => {});
								}}
							>
								保存统一口径
							</Button>
						) : null}
						<span className="text-[13px] leading-relaxed text-text-secondary">
							{canPublish ? "保存后刷新即可同步到所有用户。" : "当前账号可预览统一口径，但不能发布。"}
						</span>
					</div>
				</div>
			</Card>

			{settingsError ? <ErrorNotice locale={locale} error={settingsError} /> : null}

			{/* Filter toggle */}
			<div className="flex items-center justify-between">
				<h2 className="text-xl font-bold cursor-pointer select-none text-brand m-0 hover:opacity-80" onClick={() => setFilterOpen((prev) => !prev)} role="button" tabIndex={0}>
					条件筛选 <span className="text-sm text-text-secondary">{filterOpen ? "▾" : "▸"}</span>
				</h2>
			</div>

			{/* Filter bar */}
			{filterOpen ? (
				<Card>
					<div className="flex items-end gap-3 flex-wrap [&>*]:min-w-[120px] [&>*]:flex-[1_1_120px] [&>*]:max-w-[200px] [&>button]:flex-[0_0_auto] [&>button]:min-w-0 [&>button]:max-w-none">
						<div>
							<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>项目</label>
							<Select
								value={queryState.majorProjectId}
								onChange={(value) => updateQueryState({ majorProjectId: value })}
								options={optionList(filters?.majorProjects)}
								style={{ width: "100%" }}
							/>
						</div>
						<div>
							<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>责任科室</label>
							<Select
								value={queryState.deptId}
								onChange={(value) => updateQueryState({ deptId: value })}
								options={optionList(filters?.depts)}
								style={{ width: "100%" }}
							/>
						</div>
						<div>
							<label style={{ display: "block", marginBottom: 4, fontWeight: 500 }}>风险等级</label>
							<Select
								value={queryState.riskLevel}
								onChange={(value) => updateQueryState({ riskLevel: value })}
								options={optionList(filters?.riskLevels)}
								style={{ width: "100%" }}
							/>
						</div>
						<Button
							type="text"
							size="small"
							onClick={() => updateQueryState(createProjectCockpitScopeResetPatch(queryState))}
						>
							重置
						</Button>
					</div>
				</Card>
			) : null}

			{/* Hero grid */}
			<div className="grid grid-cols-[2fr_1.2fr] gap-4 max-[1200px]:grid-cols-1">
				<Card style={{ background: "radial-gradient(circle at top left, rgba(37,99,235,0.12), transparent 42%), linear-gradient(135deg, rgba(255,255,255,0.98), rgba(240,247,255,0.95))" }}>
					<div className="flex items-center gap-4 flex-wrap px-4 py-3">
						<div className="text-xl font-bold tracking-tight">{hero?.title ?? "项目看板"}</div>
						<span className="text-[13px] text-text-secondary">{hero?.scope ?? ""}</span>
						<span className="text-[13px] text-text-secondary">更新: {hero?.updatedAt ?? "--"}</span>
					</div>
				</Card>
				<Card>
					<div className="px-4 py-3">
						<div className="flex items-center gap-3.5 flex-wrap">
							<strong>重点盯防</strong>
							<span className="text-[15px] font-semibold">{spotlight?.majorProjectName ?? "暂无"}</span>
							<div className="flex flex-wrap gap-2">
								<Tag color="error">高风险 {spotlight?.highRiskCount ?? 0}</Tag>
								<Tag color="warning">延期 {spotlight?.delayCount ?? 0}</Tag>
								<Tag>下一里程碑 {spotlight?.nextMilestone ?? "--"}</Tag>
							</div>
							{spotlight?.majorProjectId ? (
								<Button
									type="primary"
									size="small"
									onClick={() =>
										updateQueryState({
											theme: "tree",
											majorProjectId: String(spotlight.majorProjectId ?? ""),
										})
									}
								>
									进入项目树
								</Button>
							) : null}
						</div>
					</div>
				</Card>
			</div>

			{summaryError ? <ErrorNotice locale={locale} error={summaryError} /> : null}

			<Tabs
				activeKey={queryState.theme}
				onChange={(key) => setTheme(key as ProjectCockpitTheme)}
				className="gap-3.5 flex-1 [&_[role=tabpanel]]:min-h-[600px]"
				items={THEME_ITEMS.map((item) => ({
					key: item.id,
					label: item.label,
					children:
						item.id === "overview" ? (
							summaryLoading && !summary ? (
								<div className="flex items-center justify-center min-h-[220px] border border-dashed border-border-default rounded-2xl bg-surface-muted text-text-secondary">
									<Spin size="large" />
								</div>
							) : (
								<OverviewTrendView summary={summary} summaryLoading={summaryLoading} locale={locale} />
							)
						) : item.id === "execution" ? (
							<ExecutionView locale={locale} />
						) : item.id === "risk" ? (
							<RiskAttributionView locale={locale} />
						) : item.id === "tree" ? (
							<MajorProjectTreeView locale={locale} />
						) : item.id === "support" ? (
							<DataSupportView locale={locale} />
						) : null,
				}))}
			/>
			<DrillDownDrawer />
		</div>
	);
}
