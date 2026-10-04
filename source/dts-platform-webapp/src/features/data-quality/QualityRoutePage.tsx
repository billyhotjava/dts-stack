import {
	BatchConfigurationPage,
	RulesByTablePage,
	RulesByTemplatePage,
	TableQualityDetailPage,
} from "./ConfigurationPages";
import { MonitorDetailPage, MonitorEditorPage, MonitorListPage } from "./MonitorPages";
import { OverviewPage } from "./OverviewPage";
import { QualityWorkspace } from "./QualityWorkspace";
import type { QualityRouteKey } from "./qualityRoutes";
import { ReportEditorPage, ReportPage, ReportPreviewPage } from "./ReportPages";
import { RuleEditorPage } from "./RuleEditorPage";
import { RuleDetailPage, RuleListPage } from "./RulesPages";
import { NoiseManagementPage, RunDetailPage, RunListPage } from "./RunPages";
import { TemplateDetailPage, TemplateListPage } from "./TemplatePages";

const ROUTE_COMPONENTS: Record<QualityRouteKey, () => React.ReactNode> = {
	overview: OverviewPage,
	"rule-list": RuleListPage,
	"rule-template": TemplateListPage,
	"rule-by-table": RulesByTablePage,
	"rule-by-template": RulesByTemplatePage,
	monitor: MonitorListPage,
	"run-records": RunListPage,
	report: ReportPage,
	"rule-detail": RuleDetailPage,
	"rule-editor": RuleEditorPage,
	"template-detail": TemplateDetailPage,
	"table-detail": TableQualityDetailPage,
	"batch-wizard": BatchConfigurationPage,
	"monitor-detail": MonitorDetailPage,
	"monitor-editor": MonitorEditorPage,
	"run-detail": RunDetailPage,
	noise: NoiseManagementPage,
	"report-editor": ReportEditorPage,
	"report-preview": ReportPreviewPage,
};

export default function QualityRoutePage({ routeKey }: { routeKey: QualityRouteKey }) {
	const Content = ROUTE_COMPONENTS[routeKey];
	return (
		<QualityWorkspace routeKey={routeKey}>
			<Content />
		</QualityWorkspace>
	);
}
