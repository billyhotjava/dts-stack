import { Alert, Button, Select, Skeleton, Space, Tag, Typography } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { getWarehousePlanDataMarts, listDataMarts, saveWarehousePlanDataMarts } from "@/api/dataMartApi";
import { getWarehousePlanCategories } from "@/api/warehousePlanApi";
import type {
	DataMartView,
	WarehousePlanDataMartBaseline as WarehousePlanDataMartBaselineView,
} from "../dataMartContract";

const { Text } = Typography;

const errorMessage = (error: unknown) => {
	if (error && typeof error === "object") {
		const candidate = error as { message?: string; response?: { data?: { message?: string } } };
		return candidate.response?.data?.message || candidate.message || "数据集市基线暂时不可用";
	}
	return "数据集市基线暂时不可用";
};

export function WarehousePlanDataMartBaseline({ planId, editable }: { planId: string; editable: boolean }) {
	const [baseline, setBaseline] = useState<WarehousePlanDataMartBaselineView | null>(null);
	const [dataMarts, setDataMarts] = useState<DataMartView[]>([]);
	const [confirmedDomainIds, setConfirmedDomainIds] = useState<Set<string>>(new Set());
	const [selectedIds, setSelectedIds] = useState<string[]>([]);
	const [loading, setLoading] = useState(false);
	const [saving, setSaving] = useState(false);
	const [error, setError] = useState("");
	const [dirty, setDirty] = useState(false);

	const load = useCallback(async () => {
		setLoading(true);
		setError("");
		try {
			const [nextBaseline, available, categories] = await Promise.all([
				getWarehousePlanDataMarts(planId),
				listDataMarts({ status: "CURRENT", offset: 0, limit: 100 }),
				getWarehousePlanCategories(planId),
			]);
			setBaseline(nextBaseline);
			setSelectedIds(nextBaseline.dataMartIds);
			setDataMarts(available);
			setConfirmedDomainIds(
				new Set(
					categories.value.domainBindings
						.filter((binding) => binding.confirmationStatus === "CONFIRMED" && binding.resolutionStatus === "AVAILABLE")
						.map((binding) => binding.domainId),
				),
			);
			setDirty(false);
		} catch (loadError) {
			setError(errorMessage(loadError));
		} finally {
			setLoading(false);
		}
	}, [planId]);

	useEffect(() => {
		void load();
	}, [load]);

	const options = useMemo(
		() =>
			dataMarts.map((item) => ({
				value: item.id,
				label: `${item.name}（${item.code}）${
					item.domainIds.every((domainId) => confirmedDomainIds.has(domainId)) ? "" : " · 需先确认业务分类"
				}`,
				disabled: !item.domainIds.every((domainId) => confirmedDomainIds.has(domainId)),
			})),
		[confirmedDomainIds, dataMarts],
	);

	const save = async () => {
		if (!baseline) return;
		setSaving(true);
		setError("");
		try {
			const next = await saveWarehousePlanDataMarts(planId, selectedIds, baseline.version);
			setBaseline(next);
			setSelectedIds(next.dataMartIds);
			setDirty(false);
			toast.success("数据集市基线已保存");
		} catch (saveError) {
			setError(errorMessage(saveError));
		} finally {
			setSaving(false);
		}
	};

	if (loading && !baseline) return <Skeleton active paragraph={{ rows: 4 }} />;

	return (
		<div className="max-w-4xl space-y-4" data-testid="warehouse-plan-data-mart-baseline">
			<div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3">
				<div>
					<div className="font-medium">按建设与消费边界选择数据集市</div>
					<Text type="secondary">数据集市可覆盖多个已确认业务分类，维度和模型创建时只使用这里纳入的集市。</Text>
				</div>
				<Tag color={selectedIds.length ? "green" : "blue"}>
					{selectedIds.length ? `已纳入 ${selectedIds.length} 个` : "可选"}
				</Tag>
			</div>
			{error ? (
				<Alert type="warning" showIcon message={error} action={<Button onClick={() => void load()}>重新加载</Button>} />
			) : null}
			<div className="rounded-xl border border-slate-200 p-4">
				<Space direction="vertical" className="w-full" size={12}>
					<div>
						<div className="mb-2 font-medium">当前规划的数据集市</div>
						<Text type="secondary">
							若暂时只做业务域级概念设计，可以不选择；创建数据集市范围内的维度或模型前必须先纳入。
						</Text>
					</div>
					<Select
						mode="multiple"
						showSearch
						optionFilterProp="label"
						placeholder="选择已确认的数据集市"
						options={options}
						value={selectedIds}
						disabled={!editable || saving}
						onChange={(value) => {
							setSelectedIds(value);
							setDirty(true);
						}}
					/>
					<div className="flex justify-end">
						<Button
							type="primary"
							disabled={!editable || !dirty || !baseline}
							loading={saving}
							onClick={() => void save()}
						>
							保存数据集市
						</Button>
					</div>
				</Space>
			</div>
		</div>
	);
}
