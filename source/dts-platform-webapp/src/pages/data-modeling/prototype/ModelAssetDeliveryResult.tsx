import { useCallback, useEffect, useMemo, useState } from "react";
import { getModelServingSyncStatuses, type ModelServingSyncStatus } from "@/api/modelSpecApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useRouter } from "@/routes/hooks";
import { MODEL_SERVING_SYNC_PRESENTATION, resolveModelServingPhysicalAssetId } from "./modelServingSyncPresentation";
import { Button, Status } from "./PrototypePrimitives";

type DeliveryRow = Pick<ModelSpecView, "id" | "name"> & { status: ModelServingSyncStatus | null };

export function ModelAssetDeliveryResult({ models }: { models: ModelSpecView[] }) {
	const router = useRouter();
	const modelIdsKey = models.map((model) => model.id).join(",");
	const [statuses, setStatuses] = useState<Map<string, ModelServingSyncStatus>>(() => new Map());
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const load = useCallback(async () => {
		const modelSpecIds = modelIdsKey.split(",").filter(Boolean);
		if (!modelSpecIds.length) return;
		setLoading(true);
		setFailed(false);
		try {
			const result = await getModelServingSyncStatuses(modelSpecIds);
			setStatuses(new Map(result.map((status) => [status.modelSpecId, status])));
		} catch {
			setStatuses(new Map());
			setFailed(true);
		} finally {
			setLoading(false);
		}
	}, [modelIdsKey]);
	useEffect(() => {
		void load();
	}, [load]);
	const rows = models.map((model) => ({ id: model.id, name: model.name, status: statuses.get(model.id) || null }));
	const columns = useMemo<CompactColumns<DeliveryRow>>(
		() => [
			{ title: "模型", dataIndex: "name" },
			{
				title: "目录状态",
				key: "syncStatus",
				render: (_, row) => {
					const presentation = row.status
						? MODEL_SERVING_SYNC_PRESENTATION[row.status.syncStatus]
						: MODEL_SERVING_SYNC_PRESENTATION.NOT_REGISTERED;
					return (
						<Status tone={failed && !row.status ? "danger" : presentation.tone}>
							{loading && !row.status
								? "目录同步读取中"
								: failed && !row.status
									? "目录同步读取失败"
									: presentation.label}
						</Status>
					);
				},
			},
			{
				title: "资产",
				key: "asset",
				render: (_, row) => row.status?.catalogAssetKey || "尚未登记",
			},
			{
				title: "操作",
				key: "action",
				render: (_, row) => {
					const physicalAssetId = resolveModelServingPhysicalAssetId(row.status);
					return physicalAssetId ? (
						<Button onClick={() => router.push(`/catalog/datasets/${encodeURIComponent(physicalAssetId)}`)} type="text">
							查看资产
						</Button>
					) : (
						"—"
					);
				},
			},
		],
		[failed, loading, router],
	);
	return (
		<section aria-label="资产登记结果">
			<div className="dmx-materialization-plan-toolbar">
				<strong>资产登记结果</strong>
				<Button disabled={loading} onClick={() => void load()}>
					{loading ? "读取中…" : "刷新资产状态"}
				</Button>
			</div>
			<p className="dmx-capability-note">发布登记与物理表构建分别核验；目录同步成功后可直接进入治理资产详情。</p>
			<div className="dmx-table-scroll">
				<CompactTable<DeliveryRow> columns={columns} dataSource={rows} pagination={false} rowKey="id" />
			</div>
		</section>
	);
}
