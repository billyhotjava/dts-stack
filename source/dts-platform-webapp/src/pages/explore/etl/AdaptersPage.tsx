import EtlExternalEntryPage from "@/pages/explore/etl/EtlExternalEntryPage";

export default function AdaptersPage() {
	return (
		<EtlExternalEntryPage
			entryKey="EXPLORE_ETL_ADAPTERS"
			title="多源集成适配器"
			description="多源接入由外部平台（星环）实施，本页面提供统一入口。"
		/>
	);
}
