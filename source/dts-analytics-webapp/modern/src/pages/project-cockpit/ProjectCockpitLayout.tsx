import { Link } from "react-router";
import type { Locale } from "../../i18n";
import { ErrorNotice } from "../../components/ErrorNotice";
import { PageHeader } from "../../components/PageContainer/PageContainer";
import { Badge } from "../../ui/Badge/Badge";
import { Button } from "../../ui/Button/Button";
import { Card, CardBody, CardHeader } from "../../ui/Card/Card";
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

const THEME_ITEMS: Array<{ id: ProjectCockpitTheme; label: string; description: string }> = [
	{ id: "overview", label: "总览趋势", description: "领导层先看项目群态势、预警和趋势变化。" },
	{ id: "execution", label: "计划执行", description: "围绕甘特、节点推进和科室负载看执行状态。" },
	{ id: "risk", label: "风险归因", description: "看延期原因、风险分布和重点拖期项目。" },
	{ id: "tree", label: "重大项目树", description: "按重大项目 -> 子项目 -> 节点下钻穿透。" },
	{ id: "support", label: "口径支撑", description: "查看覆盖率、指标口径和待客户补充清单。" },
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

function themeLabel(theme: ProjectCockpitTheme) {
	return THEME_ITEMS.find((item) => item.id === theme)?.label ?? "项目看板";
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

	return (
		<div className="project-cockpit">
			<PageHeader
				title={
					<div className="project-cockpit__header-title">
						<div className="project-cockpit__header-kicker">项目管理专题系统</div>
						<div>{hero?.title ?? "项目看板系统"}</div>
					</div>
				}
				actions={
					<div className="project-cockpit__header-actions">
						<Badge variant="info">{themeLabel(queryState.theme)}</Badge>
						<Link to="/screens">
							<Button variant="tertiary" size="sm">大屏工厂</Button>
						</Link>
					</div>
				}
			/>

			<div className="project-cockpit__hero-grid">
				<Card className="project-cockpit__hero-card" shadow="md">
					<CardBody className="project-cockpit__hero-body">
						<div>
							<div className="project-cockpit__hero-title">{hero?.title ?? "项目看板系统"}</div>
							<div className="project-cockpit__hero-subtitle">
								{hero?.subtitle ?? "统一入口查看项目计划、执行、延期归因和重大项目树进展。"}
							</div>
						</div>
						<div className="project-cockpit__hero-meta">
							<div>
								<span className="project-cockpit__meta-label">覆盖范围</span>
								<strong>{hero?.scope ?? "读取演示数据中"}</strong>
							</div>
							<div>
								<span className="project-cockpit__meta-label">最新更新时间</span>
								<strong>{hero?.updatedAt ?? "--"}</strong>
							</div>
						</div>
					</CardBody>
				</Card>
				<Card className="project-cockpit__spotlight-card" shadow="md">
					<CardHeader
						title="重点盯防"
						subtitle="给领导和科长的统一关注点"
						action={
							spotlight?.majorProjectId ? (
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
							) : null
						}
					/>
					<CardBody className="project-cockpit__spotlight-body">
						<div className="project-cockpit__spotlight-name">
							{spotlight?.majorProjectName ?? "暂无重点项目"}
						</div>
						<p className="project-cockpit__spotlight-summary">
							{spotlight?.summary ?? "当前筛选范围暂无需要重点盯防的项目。"}
						</p>
						<div className="project-cockpit__spotlight-metrics">
							<Badge variant="error">高风险 {spotlight?.highRiskCount ?? 0}</Badge>
							<Badge variant="warning">延期 {spotlight?.delayCount ?? 0}</Badge>
							<Badge variant="default">下一里程碑 {spotlight?.nextMilestone ?? "--"}</Badge>
						</div>
					</CardBody>
				</Card>
			</div>

			<Card className="project-cockpit__filter-card">
				<CardHeader
					title="统一入口筛选"
					subtitle="所有主题共用同一组条件，避免来回切换和重复点击。"
					action={
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
							重置筛选
						</Button>
					}
				/>
				<CardBody>
					<div className="project-cockpit__filter-grid">
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
					</div>
				</CardBody>
			</Card>

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
				<div className="project-cockpit__tab-hint">
					{THEME_ITEMS.find((item) => item.id === queryState.theme)?.description}
				</div>
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
