import EtlExternalEntryPage from "@/pages/explore/etl/EtlExternalEntryPage";

export default function TransformPage() {
	return (
		<EtlExternalEntryPage
			entryKey="EXPLORE_ETL_TRANSFORM"
			title="数据加工与转换"
			description="加工算子/脚本执行由外部平台（星环）实施，本页面提供统一入口。"
		/>
	);
}
