import { Alert, Button, Modal, Skeleton } from "antd";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
	getWarehousePlan,
	getWarehousePlanPolicy,
	type VersionedWarehousePlanValue,
	type WarehousePlanHeader,
	type WarehousePlanPolicyView,
	type WarehousePlanSourceInventoryView,
} from "@/api/warehousePlanApi";
import { type WarehousePlanSourcesSaveResult, WarehousePlanSourcesTab } from "../WarehousePlanSourcesTab";
import { createLatestRequestGuard } from "../warehousePlanCreateFlow";
import { sourceInventoryRequiresFurtherConfirmation } from "../warehousePlanSourceRegistration";

type Props = {
	open: boolean;
	planId: string;
	roleAllowsPlanMaintenance: boolean;
	onClose: () => void;
	onSaved: () => Promise<WarehousePlanSourceInventoryView | null>;
};

export function ModelSpecSourceInventoryModal({ open, planId, roleAllowsPlanMaintenance, onClose, onSaved }: Props) {
	const [plan, setPlan] = useState<WarehousePlanHeader | null>(null);
	const [policy, setPolicy] = useState<VersionedWarehousePlanValue<WarehousePlanPolicyView> | null>(null);
	const [loading, setLoading] = useState(false);
	const [loadError, setLoadError] = useState("");
	const [refreshError, setRefreshError] = useState("");
	const [sourceSaving, setSourceSaving] = useState(false);
	const [refreshingSources, setRefreshingSources] = useState(false);
	const loadGuard = useMemo(() => createLatestRequestGuard(), []);
	const refreshGuard = useMemo(() => createLatestRequestGuard(), []);

	const loadPlanContext = useCallback(async () => {
		if (!planId) {
			setPlan(null);
			setPolicy(null);
			setLoadError("");
			setRefreshError("");
			setLoading(false);
			setSourceSaving(false);
			return;
		}
		const isCurrent = loadGuard.begin();
		setLoading(true);
		setLoadError("");
		try {
			const [currentPlan, currentPolicy] = await Promise.all([
				getWarehousePlan(planId),
				getWarehousePlanPolicy(planId),
			]);
			if (!isCurrent()) return;
			setPlan(currentPlan);
			setPolicy(currentPolicy);
		} catch {
			if (!isCurrent()) return;
			setPlan(null);
			setPolicy(null);
			setLoadError("计划状态或建模策略加载失败，为避免越权修改已保持只读，请重试");
		} finally {
			if (isCurrent()) setLoading(false);
		}
	}, [loadGuard, planId]);

	useEffect(() => {
		if (!open) {
			loadGuard.invalidate();
			refreshGuard.invalidate();
			setPlan(null);
			setPolicy(null);
			setLoadError("");
			setRefreshError("");
			setLoading(false);
			setSourceSaving(false);
			setRefreshingSources(false);
			return;
		}
		void loadPlanContext();
		return () => {
			loadGuard.invalidate();
			refreshGuard.invalidate();
		};
	}, [loadGuard, loadPlanContext, open, refreshGuard]);

	const editable =
		roleAllowsPlanMaintenance &&
		Boolean(plan) &&
		plan !== null &&
		plan.lifecycleStatus !== "PUBLISHED" &&
		plan.lifecycleStatus !== "ARCHIVED";
	const refreshParentSources = async (writeRequiresFurtherConfirmation = false) => {
		const isCurrent = refreshGuard.begin();
		setRefreshingSources(true);
		try {
			const refreshedInventory = await onSaved();
			if (!isCurrent()) return;
			if (!refreshedInventory) {
				setRefreshError("来源已保存，但模型表单刷新失败。请保持当前弹窗并重试刷新。");
				return;
			}
			setRefreshError("");
			if (
				!writeRequiresFurtherConfirmation &&
				!sourceInventoryRequiresFurtherConfirmation(refreshedInventory.bindings)
			) {
				onClose();
			}
		} catch {
			if (isCurrent()) setRefreshError("来源已保存，但模型表单刷新失败。请保持当前弹窗并重试刷新。");
		} finally {
			if (isCurrent()) setRefreshingSources(false);
		}
	};
	const handleSaved = async (result: WarehousePlanSourcesSaveResult) => {
		await refreshParentSources(result.requiresFurtherConfirmation);
	};
	const sourceBusy = sourceSaving || refreshingSources;

	return (
		<Modal
			title="登记当前计划来源"
			open={open}
			onCancel={() => {
				if (!sourceBusy) onClose();
			}}
			footer={null}
			width="min(1180px, calc(100vw - 32px))"
			closable={!sourceBusy}
			maskClosable={!sourceBusy}
			keyboard={!sourceBusy}
			destroyOnClose
			styles={{ body: { maxHeight: "76vh", overflowY: "auto" } }}
		>
			{loading ? (
				<Skeleton active paragraph={{ rows: 6 }} />
			) : loadError ? (
				<Alert
					type="error"
					showIcon
					message={loadError}
					action={
						<Button size="small" onClick={() => void loadPlanContext()}>
							重试
						</Button>
					}
				/>
			) : plan ? (
				<>
					{refreshError ? (
						<Alert
							className="mb-3"
							type="warning"
							showIcon
							message={refreshError}
							action={
								<Button size="small" loading={refreshingSources} onClick={() => void refreshParentSources()}>
									重试刷新
								</Button>
							}
						/>
					) : null}
					<WarehousePlanSourcesTab
						planId={plan.id}
						onboardingMode={plan.onboardingMode}
						conceptualDesignAllowed={policy?.value.conceptualDesignAllowed === true}
						editable={editable}
						onOpenCatalog={() => window.open("/catalog/metadata-management", "_blank", "noopener,noreferrer")}
						onSaved={handleSaved}
						onSavingChange={setSourceSaving}
						registrationOpenByDefault
					/>
				</>
			) : (
				<Alert type="info" showIcon message="请先选择建设计划，再登记具体来源" />
			)}
		</Modal>
	);
}
