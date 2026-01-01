import EtlExternalEntryPage from "@/pages/explore/etl/EtlExternalEntryPage";

export default function WarehouseLayersPage() {
	return (
		<EtlExternalEntryPage
			entryKey="EXPLORE_ETL_WAREHOUSE_LAYERS"
			title="数据仓储分层管理"
			description="数仓分层规划与表清单由外部平台（星环）实施，本页面提供统一入口。"
		/>
	);
}
