import { PageHeader } from "@/components/page-header";
import QualityReportTab from "./components/QualityReportTab";

export default function QualityReportPage() {
	return (
		<div className="space-y-4 p-5" data-testid="governance-quality-report-page">
			<PageHeader title="数据治理中心 · 质量报告" />
			<QualityReportTab />
		</div>
	);
}
