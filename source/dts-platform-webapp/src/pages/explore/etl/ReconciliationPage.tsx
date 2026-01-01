import EtlExternalEntryPage from "@/pages/explore/etl/EtlExternalEntryPage";

export default function ReconciliationPage() {
	return (
		<EtlExternalEntryPage
			entryKey="EXPLORE_ETL_RECONCILIATION"
			title="数据装载与对账校验"
			description="装载对账校验由外部平台（星环）实施，本页面提供统一入口。"
		/>
	);
}
