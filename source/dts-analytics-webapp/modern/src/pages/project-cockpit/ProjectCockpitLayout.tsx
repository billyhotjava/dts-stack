import { useState } from "react";
import type { Locale } from "../../i18n";
import { ErrorNotice } from "../../components/ErrorNotice";
import { Badge } from "../../ui/Badge/Badge";
import { Button } from "../../ui/Button/Button";
import { Card, CardBody } from "../../ui/Card/Card";
import { Spinner } from "../../ui/Loading/Spinner";
import { Tab, TabList, TabPanel, TabPanels, Tabs } from "../../ui/Tabs/Tabs";
import { NativeSelect } from "../../ui/Input/Select";
import { Input } from "../../ui/Input/Input";
import type {
	ProjectCockpitOption,
	ProjectCockpitSummaryResponse,
} from "../../api/analyticsApi";
import type { ProjectCockpitTheme } from "./projectCockpitQueryState";
import { useProjectCockpitContext } from "./ProjectCockpitContext";
import OverviewTrendView from "./views/OverviewTrendView";
import ExecutionView from "./views/ExecutionView";
import RiskAttributionView from "./views/RiskAttributionView";
import MajorProjectTreeView from "./views/MajorProjectTreeView";
import DataSupportView from "./views/DataSupportView";
import "./projectCockpit.css";

const THEME_ITEMS: Array<{ id: ProjectCockpitTheme; label: string }> = [
	{ id: "overview", label: "总览趋势" },
	{ id: "execution", label: "计划执行" },
	{ id: "risk", label: "风险归因" },
	{ id: "tree", label: "重大项目树" },
	{ id: "support", label: "口径支撑" },
];

type Props = {
	locale: Locale;
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
	summary,
	summaryLoading,
	summaryError,
}: Props) {
	const { queryState, updateQueryState, setTheme } = useProjectCockpitContext();
	const hero = summary?.hero;
	const filters = summary?.filters;
	const spotlight = summary?.spotlight;
	const [filterOpen, setFilterOpen] = useState(false);

	return (
		<div className="project-cockpit">
			<div className="project-cockpit__topbar">
				<h2 className="project-cockpit__topbar-title" onClick={() => setFilterOpen((prev) => !prev)} role="button" tabIndex={0}>
					条件筛选 <span className="project-cockpit__topbar-arrow">{filterOpen ? "▾" : "▸"}</span>
				</h2>
			</div>

			{filterOpen ? (
				<Card className="project-cockpit__filter-card">
					<CardBody>
						<div className="project-cockpit__filter-bar">
							<NativeSelect
								label="项目群"
								value={queryState.programId}
								onChange={(event) => updateQueryState({ programId: event.target.value })}
								options={optionList(filters?.programs)}
							/>
							<NativeSelect
								label="重大项目"
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
							<Input
								type="date"
								label="计划起始"
								value={queryState.dateFrom}
								onChange={(event) => updateQueryState({ dateFrom: event.target.value })}
							/>
							<Input
								type="date"
								label="计划截止"
								value={queryState.dateTo}
								onChange={(event) => updateQueryState({ dateTo: event.target.value })}
							/>
							<Button
								variant="tertiary"
								size="sm"
								onClick={() =>
									updateQueryState({
										programId: "",
										majorProjectId: "",
										dateFrom: "",
										dateTo: "",
										deptId: "",
										riskLevel: "",
									})
								}
							>
								重置
							</Button>
						</div>
					</CardBody>
				</Card>
			) : null}

			<div className="project-cockpit__hero-grid">
				<Card className="project-cockpit__hero-card project-cockpit__hero-card--compact" shadow="sm">
					<CardBody className="project-cockpit__hero-body--compact">
						<div className="project-cockpit__hero-title--compact">{hero?.title ?? "项目看板"}</div>
						<span className="project-cockpit__hero-scope">{hero?.scope ?? ""}</span>
						<span className="project-cockpit__hero-scope">更新: {hero?.updatedAt ?? "--"}</span>
					</CardBody>
				</Card>
				<Card className="project-cockpit__spotlight-card--compact" shadow="sm">
					<CardBody className="project-cockpit__spotlight-body--compact">
						<div className="project-cockpit__spotlight-row">
							<strong>重点盯防</strong>
							<span className="project-cockpit__spotlight-name--compact">{spotlight?.majorProjectName ?? "暂无"}</span>
							<div className="project-cockpit__spotlight-metrics">
								<Badge variant="error">高风险 {spotlight?.highRiskCount ?? 0}</Badge>
								<Badge variant="warning">延期 {spotlight?.delayCount ?? 0}</Badge>
								<Badge variant="default">下一里程碑 {spotlight?.nextMilestone ?? "--"}</Badge>
							</div>
							{spotlight?.majorProjectId ? (
								<Button
									variant="primary"
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
					</CardBody>
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
		</div>
	);
}
