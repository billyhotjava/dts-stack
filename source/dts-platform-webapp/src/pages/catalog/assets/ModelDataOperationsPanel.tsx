import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { getModelDeliveryStatus, type ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import { registerModelData } from "@/api/modelIngestionTargetApi";
import { getModelSpec } from "@/api/modelSpecApi";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import type { UnsavedEditorHandle } from "../CatalogDatasetGovernanceSummaryEditor";
import { currentCatalogOutputs, ModelCatalogDeliveryPanel } from "@/pages/data-modeling/prototype/ModelCatalogDeliveryPanel";
import { ModelAnalysisPreparationAction } from "@/pages/data-modeling/prototype/ModelAnalysisPreparationAction";
import { ModelWorkbenchNavigationGuard } from "@/pages/data-modeling/prototype/ModelWorkbenchNavigationGuard";
import { Button } from "@/pages/data-modeling/prototype/PrototypePrimitives";
import { type ModelDataManagementFocus, resolveModelingReturnTo } from "@/pages/data-modeling/prototype/modelDataManagementLink";

/**
 * F15: "模型产出登记" in the data asset catalog. It handles only what the catalog owns for a model's output —
 * asset registration, catalog governance details and analysis-service sync (the catalog's service status).
 * Quality rules live in 质量管控 and version publication stays in the modeling workbench; this panel links to
 * them instead of embedding their forms.
 */
export function ModelDataOperationsPanel({ modelSpecId, environment, candidateId, focus, returnTo }: {
	modelSpecId: string; environment: string; candidateId?: string; focus?: ModelDataManagementFocus; returnTo?: string;
}) {
	const [value, setValue] = useState<{ model: ModelSpecView; delivery: ModelDeliveryStatus } | null>(null);
	const [failure, setFailure] = useState("");
	const [busy, setBusy] = useState(false);
	const [catalogGuard, setCatalogGuard] = useState<UnsavedEditorHandle | null>(null);
	const saving = useRef(false);
	const sequence = useRef(0);
	const load = useCallback(async () => {
		const request = ++sequence.current;
		setBusy(true); setFailure("");
		try {
			const [model, delivery] = await Promise.all([getModelSpec(modelSpecId), getModelDeliveryStatus(modelSpecId, environment, candidateId)]);
			if (request !== sequence.current) return;
			if (model.id !== modelSpecId || delivery.modelSpecId !== model.id || delivery.modelRevision !== model.revision || delivery.modelChecksum !== model.checksum) throw new Error("模型版本已变化，请刷新后重试");
			setValue({ model, delivery });
		} catch (error) {
			if (request === sequence.current) setFailure(error instanceof Error ? error.message : "模型产出读取失败");
		} finally { if (request === sequence.current) setBusy(false); }
	}, [modelSpecId, environment, candidateId]);
	useEffect(() => { void load(); return () => { sequence.current++; }; }, [load]);
	const backToModel = resolveModelingReturnTo(returnTo);
	const dirty = Boolean(catalogGuard?.dirty);
	const action = value?.delivery.dataPrimaryAction;
	const awaitingRegistration = action?.code === "REGISTER_DATA_ASSETS";
	const registeredDatasetId = value ? currentCatalogOutputs(value.delivery).find((output) => output.resourceId)?.resourceId : undefined;
	const register = async () => {
		const candidate = value?.delivery.candidate;
		if (!value || !candidate || busy || dirty || !awaitingRegistration || !action?.enabled) return;
		setBusy(true);
		try { await registerModelData(modelSpecId, { candidateId: candidate.id, candidateVersion: candidate.version, modelRevision: value.model.revision, modelChecksum: value.model.checksum }); await load(); }
		catch (error) { setFailure(error instanceof Error ? error.message : "资产登记失败"); }
		finally { setBusy(false); }
	};
	const canCommand = !busy && !failure && !dirty;
	return <section className="dmx-editor-panel" aria-label="模型产出登记">
		<h3>模型产出登记{value ? ` · ${value.model.name}` : ""}</h3>
		{failure ? <div role="alert">{failure}<Button disabled={busy || dirty} onClick={() => void load()}>重试</Button></div> : null}
		{busy && !value ? <p>正在读取当前目标和资产状态…</p> : null}
		{value ? <>
			<p>{value.delivery.modelingResult?.state === "SUCCEEDED" ? "模型已完成构建" : "当前模型构建记录待确认"} · {value.delivery.modelingResult?.targetRelation || "尚无目标表"}</p>
			{backToModel ? <Link to={backToModel}>返回模型</Link> : null}
			<Link to={`/data-modeling/dimensions/workbench?modelSpecId=${encodeURIComponent(modelSpecId)}&step=definition&environment=${encodeURIComponent(environment)}`}>编辑模型</Link>
			{awaitingRegistration ? <>
				<p className="dmx-capability-note">资产尚未登记。登记只处理资产身份，不会重新执行构建。</p>
				<Button disabled={!canCommand || !action?.enabled} onClick={() => void register()}>登记数据资产</Button>
			</> : null}
			{registeredDatasetId ? (
				<p>
					质量规则在质量管控中配置和验证：
					<Link to={`/governance/rules/config/tables/${encodeURIComponent(registeredDatasetId)}`}>按表配置质量规则</Link>
				</p>
			) : focus === "quality" ? <p className="dmx-capability-note">资产登记完成后，才能在质量管控中为它配置规则。</p> : null}
			{value.model.modelType === "SOURCE" && value.delivery.modelingResult?.state === "SUCCEEDED" ? <p><Link to="/foundation/data-sources/access/new">配置数据接入</Link>：在目标步骤选择此模型表，也可编辑已有接入任务绑定。</p> : null}
			<ModelAnalysisPreparationAction delivery={value.delivery} canMaintain={canCommand} onChanged={() => void load()} />
			<ModelCatalogDeliveryPanel delivery={value.delivery} modelName={value.model.name} canMaintain={!busy && !failure} onSaved={() => void load()} onNavigationGuardChange={setCatalogGuard} />
		</> : null}
		<ModelWorkbenchNavigationGuard dirty={dirty} savingRef={saving} onSave={async () => {
			saving.current = true;
			try { return !catalogGuard?.dirty || await catalogGuard.save(); }
			finally { saving.current = false; }
		}} onDiscard={() => catalogGuard?.discard()} />
	</section>;
}
