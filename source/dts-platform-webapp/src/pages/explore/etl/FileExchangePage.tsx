import EtlExternalEntryPage from "@/pages/explore/etl/EtlExternalEntryPage";

export default function FileExchangePage() {
	return (
		<EtlExternalEntryPage
			entryKey="EXPLORE_ETL_FILE_EXCHANGE"
			title="数据交换与文件接入"
			description="文件接入/交换流程由外部平台（星环）实施，本页面提供统一入口。"
		/>
	);
}
