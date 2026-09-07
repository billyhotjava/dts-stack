import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { getModelDeliveryStatus, type ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import { getModelSpec, rerunReleaseCandidateGovernanceQuality } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { UnsavedEditorHandle } from "../CatalogDatasetGovernanceSummaryEditor";
import { ModelTargetQualityPanel } from "@/pages/data-modeling/prototype/ModelTargetQualityPanel";
import { ModelCatalogDeliveryPanel } from "@/pages/data-modeling/prototype/ModelCatalogDeliveryPanel";
import { ModelAnalysisPreparationAction } from "@/pages/data-modeling/prototype/ModelAnalysisPreparationAction";
import { ModelPublishDialog } from "@/pages/data-modeling/prototype/ModelPublishDialog";
import { ModelWorkbenchNavigationGuard } from "@/pages/data-modeling/prototype/ModelWorkbenchNavigationGuard";
import { Button } from "@/pages/data-modeling/prototype/PrototypePrimitives";

/** Uses existing governance owners with the same model, candidate and asset identities. */
export function ModelDataOperationsPanel({ modelSpecId, environment }: { modelSpecId: string; environment: string }) {
	const [value, setValue] = useState<{ model: ModelSpecView; delivery: ModelDeliveryStatus } | null>(null);
	const [failure, setFailure] = useState("");
	const [busy, setBusy] = useState(false);
	const [qualityOpen, setQualityOpen] = useState(0);
	const [qualityGuard, setQualityGuard] = useState<UnsavedEditorHandle | null>(null);
	const [catalogGuard, setCatalogGuard] = useState<UnsavedEditorHandle | null>(null);
	const saving = useRef(false);
	const sequence = useRef(0);
	const load = useCallback(async () => {
		const request = ++sequence.current;
		setBusy(true); setFailure("");
		try {
			const [model, delivery] = await Promise.all([getModelSpec(modelSpecId), getModelDeliveryStatus(modelSpecId, environment)]);
			if (request !== sequence.current) return;
			if (model.id !== modelSpecId || delivery.modelSpecId !== model.id || delivery.modelRevision !== model.revision || delivery.modelChecksum !== model.checksum) throw new Error("模型版本已变化，请刷新后重试");
			setValue({ model, delivery });
		} catch (error) {
			if (request === sequence.current) setFailure(error instanceof Error ? error.message : "模型产出读取失败");
		} finally { if (request === sequence.current) setBusy(false); }
	}, [modelSpecId, environment]);
	useEffect(() => { void load(); return () => { sequence.current++; }; }, [load]);
	const guards = [qualityGuard, catalogGuard].filter((guard): guard is UnsavedEditorHandle => Boolean(guard));
	const dirty = guards.some(guard => guard.dirty);
	const action = value?.delivery.dataPrimaryAction;
	const rerunQuality = async () => {
		const candidate = value?.delivery.workspace?.candidate;
		if (!value || !candidate || busy || dirty || action?.code !== "RERUN_GOVERNANCE_QUALITY" || !action.enabled) return;
		setBusy(true);
		try { await rerunReleaseCandidateGovernanceQuality(value.delivery.planId, candidate, crypto.randomUUID()); await load(); }
		catch (error) { setFailure(error instanceof Error ? error.message : "质量检查未能启动"); }
		finally { setBusy(false); }
	};
	const canCommand = !busy && !failure && !dirty;
	return <section className="dmx-editor-panel" aria-label="模型产出数据管理">
		<h3>{value?.model.name || "模型产出"}</h3>
		{failure ? <div role="alert">{failure}<Button disabled={busy || dirty} onClick={() => void load()}>重试</Button></div> : null}
		{busy && !value ? <p>正在读取当前目标和资产状态…</p> : null}
		{value ? <>
			<p>{value.delivery.modelingResult?.state === "SUCCEEDED" ? "模型已完成物化" : "当前模型物化证据待确认"} · {value.delivery.modelingResult?.targetRelation || "尚无目标表"}</p>
			<Link to={`/data-modeling/dimensions/workbench?modelSpecId=${encodeURIComponent(modelSpecId)}&step=definition&environment=${encodeURIComponent(environment)}`}>编辑模型</Link>
			<ModelTargetQualityPanel delivery={value.delivery} openRequest={qualityOpen} canMaintain={canCommand || Boolean(qualityGuard?.dirty)} onChanged={() => void load()} onNavigationGuardChange={setQualityGuard} />
			{action?.code === "CONFIGURE_QUALITY_RULES" ? <Button disabled={!canCommand || !action.enabled} onClick={() => setQualityOpen(n => n + 1)}>配置质量规则</Button> : null}
			{action?.code === "RERUN_GOVERNANCE_QUALITY" ? <Button disabled={!canCommand || !action.enabled} onClick={() => void rerunQuality()}>执行质量检查</Button> : null}
			<ModelPublishDialog models={[value.model]} step="delivery" initialEnvironment={environment}
				deliveryStatus={{ ...value.delivery, wizard: value.delivery.wizard.map(page => page.key === "delivery" ? { ...page, canEdit: Boolean(action?.enabled), primaryAction: action || null } : page) }}
				canMaintain={canCommand} onChanged={() => void load()} onClose={() => void load()} />
			<ModelAnalysisPreparationAction delivery={value.delivery} canMaintain={canCommand} onChanged={() => void load()} />
			<ModelCatalogDeliveryPanel delivery={value.delivery} modelName={value.model.name} canMaintain={!busy && !failure} onSaved={() => void load()} onNavigationGuardChange={setCatalogGuard} />
		</> : null}
		<ModelWorkbenchNavigationGuard dirty={dirty} savingRef={saving} onSave={async () => {
			saving.current = true;
			try { for (const guard of guards) if (guard.dirty && !await guard.save()) return false; return true; }
			finally { saving.current = false; }
		}} onDiscard={() => guards.forEach(guard => guard.discard())} />
	</section>;
}
