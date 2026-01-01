import EtlExternalEntryPage from "@/pages/explore/etl/EtlExternalEntryPage";

export default function OrchestrationPage() {
	return (
		<EtlExternalEntryPage
			entryKey="EXPLORE_ETL_ORCHESTRATION"
			title="ETL流程编排与作业管理"
			description="作业编排/调度由外部平台（星环）实施，本页面提供统一入口。"
		/>
	);
}
