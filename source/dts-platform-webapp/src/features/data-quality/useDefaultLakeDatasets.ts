import { useCallback, useEffect, useState } from "react";
import { ingestionTaskAPI } from "@/api/ingestion";
import { listDatasets } from "@/api/platformApi";
import { normalizeDatasetDomain } from "./datasetDomains";
import { collectDatasetPages } from "./datasetPaging";
import type { QualityDataset } from "./qualityTypes";

export function useDefaultLakeDatasets() {
	const [datasets, setDatasets] = useState<QualityDataset[]>([]);
	const [loading, setLoading] = useState(true);
	const [message, setMessage] = useState<string>();
	const [lakeName, setLakeName] = useState("默认数据湖");

	const reload = useCallback(async () => {
		setLoading(true);
		try {
			const lake = await ingestionTaskAPI.getDefaultDestinationStatus();
			setLakeName(lake?.destinationName?.trim() || "默认数据湖");
			if (!lake?.available || !lake.dataSourceId) {
				setDatasets([]);
				setMessage(lake?.message || "未识别默认数据湖连接");
				return;
			}
			const allRows = await collectDatasetPages<Record<string, unknown>>(
				async (page, size) =>
					(await listDatasets({ page, size, enabledOnly: true, sourceId: lake.dataSourceId })) as {
						content?: Record<string, unknown>[];
						total?: number;
						totalElements?: number;
						totalPages?: number;
					},
			);
			const next = allRows.map((item) => ({
				id: String(item.id),
				name: String(item.name || item.tableName || item.id),
				schemaName: item.schemaName ? String(item.schemaName) : undefined,
				tableName: item.tableName ? String(item.tableName) : undefined,
				hiveDatabase: item.hiveDatabase ? String(item.hiveDatabase) : undefined,
				hiveTable: item.hiveTable ? String(item.hiveTable) : undefined,
				sourceId: String(lake.dataSourceId),
				...normalizeDatasetDomain(item),
			}));
			setDatasets(next);
			setMessage(next.length ? undefined : "默认数据湖下暂无可用数据资产");
		} catch (error) {
			setDatasets([]);
			setMessage(error instanceof Error ? error.message : "默认数据湖连接读取失败");
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void reload();
	}, [reload]);

	return { datasets, lakeName, loading, message, reload };
}
