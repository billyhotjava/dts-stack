import { useEffect, useMemo, useState } from "react";
import { getModelDeliveryStatus, type ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import { type CompactColumns, CompactTable } from "@/components/table";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useRouter } from "@/routes/hooks";
import { ModelDeliveryStatusCell, resolveModelDeliveryCell } from "./ModelDeliveryStatusCell";
import { Button } from "./PrototypePrimitives";

type DeliveryRow = Pick<ModelSpecView, "id" | "name" | "revision"> & { status: ModelDeliveryStatus | null };

/** Current-revision registration and serving state; it never reads legacy sync badges. */
export function ModelAssetDeliveryResult({ models }: { models: ModelSpecView[] }) {
	const router = useRouter();
	const modelIdsKey = models.map((model) => `${model.id}:${model.revision}`).join(",");
	const [statuses, setStatuses] = useState<Map<string, ModelDeliveryStatus>>(() => new Map());
	const [loading, setLoading] = useState(true);
	const [failed, setFailed] = useState(false);
	const [reload, setReload] = useState(0);
	const requestKey = `${modelIdsKey}#${reload}`;
	useEffect(() => {
		const ids = requestKey
			.split("#")[0]
			.split(",")
			.map((item) => item.split(":")[0])
			.filter(Boolean);
		if (!ids.length) {
			setStatuses(new Map());
			setLoading(false);
			return;
		}
		let active = true;
		setLoading(true);
		setFailed(false);
		void Promise.all(ids.map(async (id) => [id, await getModelDeliveryStatus(id)] as const))
			.then((results) => {
				if (active) setStatuses(new Map(results));
			})
			.catch(() => {
				if (active) {
					setStatuses(new Map());
					setFailed(true);
				}
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [requestKey]);
	const rows = models.map((model) => ({
		id: model.id,
		name: model.name,
		revision: model.revision,
		status: statuses.get(model.id) || null,
	}));
	const columns = useMemo<CompactColumns<DeliveryRow>>(
		() => [
			{ title: "模型", dataIndex: "name" },
			{
				title: "目录登记",
				key: "catalog",
				render: (_, row) => (
					<ModelDeliveryStatusCell model={row} status={row.status} kind="catalog" loading={loading} failed={failed} />
				),
			},
			{
				title: "分析准备",
				key: "analysis",
				render: (_, row) => (
					<ModelDeliveryStatusCell model={row} status={row.status} kind="analysis" loading={loading} failed={failed} />
				),
			},
			{
				title: "操作",
				key: "action",
				render: (_, row) => {
					const assetId = resolveModelDeliveryCell(row, row.status, "catalog").assetId;
					return assetId ? (
						<Button onClick={() => router.push(`/catalog/datasets/${encodeURIComponent(assetId)}`)} type="text">
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
				<Button disabled={loading} onClick={() => setReload((value) => value + 1)}>
					{loading ? "读取中…" : "刷新资产状态"}
				</Button>
			</div>
			<p className="dmx-capability-note">目录登记与分析准备分别以当前模型版本的交付结果展示。</p>
			<div className="dmx-table-scroll">
				<CompactTable<DeliveryRow> columns={columns} dataSource={rows} pagination={false} rowKey="id" />
			</div>
		</section>
	);
}
