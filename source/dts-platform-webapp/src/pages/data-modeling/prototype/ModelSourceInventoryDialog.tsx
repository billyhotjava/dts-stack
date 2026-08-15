import { useCallback, useEffect, useMemo, useState } from "react";
import { listCatalogAssetsV2, listTablesByDataset } from "@/api/platformApi";
import {
	getWarehousePlanSources,
	saveWarehousePlanSources,
	type WarehousePlanSourceBindingInput,
	type WarehousePlanSourceBindingView,
	type WarehousePlanSourceInventoryView,
} from "@/api/warehousePlanApi";
import { Button, Modal, RequestState, Status } from "./PrototypePrimitives";

type Option = { value: string; label: string };

type Props = {
	planId: string;
	onClose: () => void;
	onSourcesChanged: (sources: WarehousePlanSourceBindingView[]) => void;
};

const retainedBinding = (binding: WarehousePlanSourceBindingView): WarehousePlanSourceBindingInput => ({
	bindingId: binding.bindingId,
	confirmationStatus: binding.confirmationStatus,
	exclusionReason: binding.exclusionReason || null,
});

const currentConfirmedSources = (bindings: WarehousePlanSourceBindingView[]) =>
	bindings.filter(
		(binding) =>
			binding.confirmationStatus === "CONFIRMED" &&
			binding.resolutionStatus === "AVAILABLE" &&
			binding.freshness === "CURRENT",
	);

const failureMessage = (error: unknown, fallback: string) => {
	if (!error || typeof error !== "object") return fallback;
	const record = error as Record<string, any>;
	return String(record?.response?.data?.message || record?.response?.data?.error || record.message || fallback);
};

export function ModelSourceInventoryDialog({ planId, onClose, onSourcesChanged }: Props) {
	const [inventory, setInventory] = useState<WarehousePlanSourceInventoryView | null>(null);
	const [datasets, setDatasets] = useState<Option[]>([]);
	const [tables, setTables] = useState<Option[]>([]);
	const [datasetId, setDatasetId] = useState("");
	const [assetId, setAssetId] = useState("");
	const [loading, setLoading] = useState(true);
	const [tableLoading, setTableLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [failure, setFailure] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		setFailure("");
		try {
			const [nextInventory, assets] = await Promise.all([
				getWarehousePlanSources(planId, 0, 200),
				listCatalogAssetsV2({ page: 0, size: 200 }),
			]);
			setInventory(nextInventory);
			const uniqueDatasets = new Map<string, Option>();
			for (const item of assets.content || []) {
				const value = String(item.legacyDatasetId || "").trim();
				if (!value || uniqueDatasets.has(value)) continue;
				const label = String(item.datasetName || item.displayName || item.fqn || value);
				uniqueDatasets.set(value, { value, label });
			}
			setDatasets([...uniqueDatasets.values()]);
		} catch (error) {
			setFailure(failureMessage(error, "来源清单读取失败，请重试。"));
		} finally {
			setLoading(false);
		}
	}, [planId]);

	useEffect(() => {
		void load();
	}, [load]);

	const registeredAssetIds = useMemo(
		() =>
			new Set(
				(inventory?.bindings || [])
					.filter((binding) => binding.sourceType === "CATALOG_TABLE")
					.map((binding) => binding.locator?.assetId)
					.filter((value): value is string => Boolean(value)),
			),
		[inventory],
	);

	const chooseDataset = async (nextDatasetId: string) => {
		setDatasetId(nextDatasetId);
		setAssetId("");
		setTables([]);
		if (!nextDatasetId) return;
		setTableLoading(true);
		setFailure("");
		try {
			const result: any = await listTablesByDataset(nextDatasetId);
			const rows = Array.isArray(result?.content)
				? result.content
				: Array.isArray(result?.data?.content)
					? result.data.content
					: [];
			setTables(
				rows
					.map((item: any) => ({
						value: String(item?.id || ""),
						label: String(item?.name || item?.displayName || item?.id || ""),
					}))
					.filter((option: Option) => option.value && !registeredAssetIds.has(option.value)),
			);
		} catch (error) {
			setFailure(failureMessage(error, "数据表目录读取失败，请重试。"));
		} finally {
			setTableLoading(false);
		}
	};

	const save = async (bindings: WarehousePlanSourceBindingInput[]) => {
		if (!inventory) return;
		setSaving(true);
		setFailure("");
		try {
			const saved = await saveWarehousePlanSources(planId, inventory.version, bindings);
			setInventory(saved);
			onSourcesChanged(currentConfirmedSources(saved.bindings));
			setAssetId("");
			if (datasetId) await chooseDataset(datasetId);
		} catch (error) {
			setFailure(failureMessage(error, "来源保存失败，请重新加载后再试。"));
		} finally {
			setSaving(false);
		}
	};

	const register = () => {
		if (!inventory || !assetId) return;
		void save([
			...inventory.bindings.map(retainedBinding),
			{
				sourceType: "CATALOG_TABLE",
				locator: { assetId },
				confirmationStatus: "CONFIRMED",
				exclusionReason: null,
			},
		]);
	};

	const confirm = (bindingId: string) => {
		if (!inventory) return;
		void save(
			inventory.bindings.map((binding) =>
				binding.bindingId === bindingId
					? {
							bindingId,
							confirmationStatus: "CONFIRMED",
							exclusionReason: null,
							action: "CONFIRM",
						}
					: retainedBinding(binding),
			),
		);
	};

	const reconfirm = (binding: WarehousePlanSourceBindingView) => {
		if (!inventory) return;
		if (!binding.confirmedVersion || !binding.currentVersion) {
			setFailure("来源版本证据不完整，请重新加载后再试。");
			return;
		}
		void save(
			inventory.bindings.map((item) =>
				item.bindingId === binding.bindingId
					? {
							bindingId: item.bindingId,
							confirmationStatus: "CONFIRMED",
							exclusionReason: null,
							action: "RECONFIRM",
							expectedConfirmedVersion: item.confirmedVersion,
							expectedCurrentVersion: item.currentVersion,
						}
					: retainedBinding(item),
			),
		);
	};

	return (
		<Modal
			footer={
				<div className="dmx-dialog-actions">
					<Button disabled={saving} onClick={onClose}>
						关闭
					</Button>
				</div>
			}
			onClose={onClose}
			title="维护物理来源"
			wide
		>
			{loading ? (
				<RequestState description="正在读取来源清单与资产目录。" kind="loading" title="正在加载" />
			) : failure && !inventory ? (
				<RequestState description={failure} kind="error" onRetry={() => void load()} title="来源读取失败" />
			) : (
				<div className="dmx-source-inventory">
					<p className="dmx-capability-note">
						从资产目录登记真实物理表；登记结果写入平台唯一来源清单，模型只保存稳定绑定与解析版本。
					</p>
					{failure ? (
						<div className="dmx-inline-error" role="alert">
							{failure}
						</div>
					) : null}
					<div className="dmx-workbench-editor__basic-grid">
						<label>
							<span>来源数据集</span>
							<select
								aria-label="来源数据集"
								onChange={(event) => void chooseDataset(event.target.value)}
								value={datasetId}
							>
								<option value="">请选择数据集</option>
								{datasets.map((option) => (
									<option key={option.value} value={option.value}>
										{option.label}
									</option>
								))}
							</select>
						</label>
						<label>
							<span>目录资产</span>
							<select
								aria-label="目录资产"
								disabled={!datasetId || tableLoading}
								onChange={(event) => setAssetId(event.target.value)}
								value={assetId}
							>
								<option value="">{tableLoading ? "正在读取数据表…" : "请选择数据表"}</option>
								{tables.map((option) => (
									<option key={option.value} value={option.value}>
										{option.label}
									</option>
								))}
							</select>
						</label>
					</div>
					<div className="dmx-dialog-actions">
						<Button disabled={!assetId || saving} onClick={register} primary>
							{saving ? "保存中…" : "登记并确认"}
						</Button>
					</div>
					<div className="dmx-implementation-binding-list">
						<strong>已登记来源</strong>
						{inventory?.bindings.length ? (
							inventory.bindings.map((binding) => (
								<div className="dmx-source-inventory__row" key={binding.bindingId}>
									<div>
										<span>{binding.displayName || binding.sourceId || binding.bindingId}</span>
										{binding.statusSummary ? <small>{binding.statusSummary}</small> : null}
										{binding.diffSummary &&
										binding.diffSummary.added + binding.diffSummary.removed + binding.diffSummary.changed > 0 ? (
											<small>
												+{binding.diffSummary.added} / -{binding.diffSummary.removed} / ~{binding.diffSummary.changed}
											</small>
										) : null}
									</div>
									<Status
										tone={
											binding.confirmationStatus === "CONFIRMED" && binding.freshness === "CURRENT"
												? "success"
												: "warning"
										}
									>
										{binding.confirmationStatus} · {binding.freshness}
									</Status>
									{binding.confirmationStatus === "CANDIDATE" ? (
										<Button disabled={saving} onClick={() => confirm(binding.bindingId)}>
											确认纳入
										</Button>
									) : null}
									{binding.allowedActions?.includes("RECONFIRM") ? (
										<Button disabled={saving} onClick={() => reconfirm(binding)}>
											采用新结构
										</Button>
									) : null}
								</div>
							))
						) : (
							<small>当前尚未登记来源。</small>
						)}
					</div>
				</div>
			)}
		</Modal>
	);
}
