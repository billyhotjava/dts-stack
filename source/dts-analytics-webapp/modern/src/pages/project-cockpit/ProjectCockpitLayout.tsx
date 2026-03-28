import { useState } from "react";
import type { Locale } from "../../i18n";
import { ErrorNotice } from "../../components/ErrorNotice";
import { Button } from "../../ui/Button/Button";
import { Card, Tag } from "antd";
import { Spinner } from "../../ui/Loading/Spinner";
import { Tab, TabList, TabPanel, TabPanels, Tabs } from "../../ui/Tabs/Tabs";
import { NativeSelect } from "../../ui/Input/Select";
import { Input } from "../../ui/Input/Input";
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
		<div className="project-cockpit">
			<Card className="project-cockpit__period-card">
				<div className="project-cockpit__period-bar">
					<div className="project-cockpit__period-meta">
						<div className="project-cockpit__period-title">统一统计周期</div>
						<div className="project-cockpit__period-hint">
							项目看板按 t1/t2 统一口径计算，保存后所有人看到同一版报表。
						</div>
						<div className="project-cockpit__period-status">{publishedLabel}</div>
					</div>
					<div className="project-cockpit__period-inputs">
						<Input
							type="date"
							label="t1 统计开始"
							value={effectiveQueryState.dateFrom}
							onChange={(event) => updateQueryState({ dateFrom: event.target.value })}
							disabled={settingsLoading}
						/>
						<Input
							type="date"
							label="t2 统计结束"
							value={effectiveQueryState.dateTo}
							onChange={(event) => updateQueryState({ dateTo: event.target.value })}
							disabled={settingsLoading}
						/>
					</div>
					<div className="project-cockpit__period-actions">
						{canPublish ? (
							<Button
								color="blue"
								size="sm"
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
						<span className="project-cockpit__period-tip">
							{canPublish ? "保存后刷新即可同步到所有用户。" : "当前账号可预览统一口径，但不能发布。"}
						</span>
					</div>
				</div>
			</Card>

			{settingsError ? <ErrorNotice locale={locale} error={settingsError} /> : null}

			<div className="project-cockpit__topbar">
				<h2 className="project-cockpit__topbar-title" onClick={() => setFilterOpen((prev) => !prev)} role="button" tabIndex={0}>
					条件筛选 <span className="project-cockpit__topbar-arrow">{filterOpen ? "▾" : "▸"}</span>
				</h2>
			</div>

			{filterOpen ? (
				<Card className="project-cockpit__filter-card">
					<div className="project-cockpit__filter-bar">
						<NativeSelect
							label="项目"
							value={queryState.majorProjectId}
							onChange={(event) => updateQueryState({ majorProjectId: event.target.value })}
							options={optionList(filters?.majorProjects)}
						/>
						<NativeSelect
							label="责任科室"
							value={queryState.deptId}
							onChange={(event) => updateQueryState({ deptId: event.target.value })}
							options={optionList(filters?.depts)}
						/>
						<NativeSelect
							label="风险等级"
							value={queryState.riskLevel}
							onChange={(event) => updateQueryState({ riskLevel: event.target.value })}
							options={optionList(filters?.riskLevels)}
						/>
						<Button
							variant="tertiary"
							size="sm"
							onClick={() => updateQueryState(createProjectCockpitScopeResetPatch(queryState))}
						>
							重置
						</Button>
					</div>
				</Card>
			) : null}

			<div className="project-cockpit__hero-grid">
				<Card className="project-cockpit__hero-card project-cockpit__hero-card--compact">
					<div className="project-cockpit__hero-body--compact">
						<div className="project-cockpit__hero-title--compact">{hero?.title ?? "项目看板"}</div>
						<span className="project-cockpit__hero-scope">{hero?.scope ?? ""}</span>
						<span className="project-cockpit__hero-scope">更新: {hero?.updatedAt ?? "--"}</span>
					</div>
				</Card>
				<Card className="project-cockpit__spotlight-card--compact">
					<div className="project-cockpit__spotlight-body--compact">
						<div className="project-cockpit__spotlight-row">
							<strong>重点盯防</strong>
							<span className="project-cockpit__spotlight-name--compact">{spotlight?.majorProjectName ?? "暂无"}</span>
							<div className="project-cockpit__spotlight-metrics">
								<Tag color="error">高风险 {spotlight?.highRiskCount ?? 0}</Tag>
								<Tag color="warning">延期 {spotlight?.delayCount ?? 0}</Tag>
								<Tag>下一里程碑 {spotlight?.nextMilestone ?? "--"}</Tag>
							</div>
							{spotlight?.majorProjectId ? (
								<Button
									color="blue"
									size="sm"
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
				value={queryState.theme}
				onChange={(value) => setTheme(value as ProjectCockpitTheme)}
				variant="pill"
				className="project-cockpit__tabs"
			>
				<TabList aria-label="项目看板主题">
					{THEME_ITEMS.map((item) => (
						<Tab key={item.id} value={item.id}>
							{item.label}
						</Tab>
					))}
				</TabList>
				<TabPanels>
					<TabPanel value="overview">
						{summaryLoading && !summary ? (
							<div className="project-cockpit__loading-card">
								<Spinner size="lg" />
							</div>
						) : (
							<OverviewTrendView summary={summary} summaryLoading={summaryLoading} locale={locale} />
						)}
					</TabPanel>
					<TabPanel value="execution">
						<ExecutionView locale={locale} />
					</TabPanel>
					<TabPanel value="risk">
						<RiskAttributionView locale={locale} />
					</TabPanel>
					<TabPanel value="tree">
						<MajorProjectTreeView locale={locale} />
					</TabPanel>
					<TabPanel value="support">
						<DataSupportView locale={locale} />
					</TabPanel>
				</TabPanels>
			</Tabs>
			<DrillDownDrawer />
		</div>
	);
}
