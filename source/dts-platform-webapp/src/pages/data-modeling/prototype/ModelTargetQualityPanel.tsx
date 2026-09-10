import { QualitySqlPreflight } from "@/features/data-quality/QualitySqlPreflight";
import { useCallback, useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router";
import api from "@/api/apiClient";
import type { ModelDeliveryStatus } from "@/api/modelDeliveryStatusApi";
import { createQualityRule } from "@/api/platformApi";
import { buildQualityRulePayload, qualityPath } from "@/features/data-quality/qualityRoutes";
import type { QualityRule } from "@/features/data-quality/qualityTypes";
import type { UnsavedEditorHandle } from "@/pages/catalog/CatalogDatasetGovernanceSummaryEditor";
import { Button, Status } from "./PrototypePrimitives";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

type QualityAsset = {
	modelSpecId: string;
	modelRevision: number;
	datasetId: string;
	assetKey: string;
	qualifiedName: string;
	configurable: boolean;
	configurationBlockerCode?: string;
	rules: QualityRule[];
};
type QualityContext = {
	candidateId: string;
	candidateVersion: number;
	status: string;
	assets: QualityAsset[];
	governanceQualityCode?: string;
	governanceQualityMessage?: string;
};
export function ModelTargetQualityPanel({
	delivery,
	canMaintain,
	openRequest = 0,
	onChanged,
	onNavigationGuardChange,
}: {
	delivery: ModelDeliveryStatus;
	canMaintain: boolean;
	openRequest?: number;
	onChanged: () => void;
	onNavigationGuardChange: (handle: UnsavedEditorHandle | null) => void;
}) {
	const navigate = useNavigate(),
		location = useLocation();
	const [assets, setAssets] = useState<QualityAsset[]>([]),
		[failure, setFailure] = useState("");
	const [loading, setLoading] = useState(false),
		[refresh, setRefresh] = useState(0);
	const identity = `${delivery.modelSpecId}:${delivery.modelRevision}:${delivery.candidate?.id}:${delivery.candidate?.version}`;
	const loadedIdentity = useRef(identity);
	const panelRef = useRef<HTMLElement>(null);
	useEffect(() => {
		if (openRequest > 0) panelRef.current?.scrollIntoView({ block: "start", behavior: "smooth" });
	}, [openRequest]);
	// biome-ignore lint/correctness/useExhaustiveDependencies: refresh explicitly reloads the existing target context after rule edits.
	useEffect(() => {
		let active = true;
		if (loadedIdentity.current !== identity) {
			setAssets([]);
			loadedIdentity.current = identity;
		}
		setFailure("");
		if (!delivery.candidate?.matchesCurrentModel) return;
		setLoading(true);
		void api
			.get<QualityContext>({
				url: `/modeling/plans/${encodeURIComponent(delivery.planId)}/release-candidates/${encodeURIComponent(delivery.candidate.id)}/quality-context`,
				_skipErrorToast: true,
			} as any)
			.then((value) => {
				if (!active) return;
				if (value.candidateId !== delivery.candidate?.id || value.candidateVersion !== delivery.candidate.version)
					throw new Error("候选版本已变化，请刷新后继续");
				if (value.governanceQualityCode === "MODEL_SPEC_GOVERNANCE_QUALITY_CONTEXT_UNAVAILABLE")
					throw new Error(value.governanceQualityMessage || "质量目标读取失败");
				setAssets(
					value.assets.filter(
						(asset) => asset.modelSpecId === delivery.modelSpecId && asset.modelRevision === delivery.modelRevision,
					),
				);
			})
			.catch((error) => {
				if (active) setFailure(normalizeModelingRequestFailure(error, "目标质量规则读取失败").message);
			})
			.finally(() => {
				if (active) setLoading(false);
			});
		return () => {
			active = false;
		};
	}, [delivery.candidate, delivery.planId, delivery.modelSpecId, delivery.modelRevision, refresh]);
	const editorPath = (asset: QualityAsset, rule?: QualityRule) => {
		const params = new URLSearchParams({
			datasetId: asset.datasetId,
			assetKey: asset.assetKey,
			modelSpecId: delivery.modelSpecId,
			candidateId: delivery.candidate!.id,
			environment: delivery.environment || "dev",
			returnTo: location.pathname + location.search,
		});
		return `${rule ? qualityPath("rule-detail", { ruleId: rule.id }) + "/edit" : qualityPath("rule-editor")}?${params}`;
	};
	return (
		<section className="dmx-target-quality" id="model-target-quality" aria-label="目标表质量规则" ref={panelRef}>
			<h3>目标表质量规则</h3>
			<p className="dmx-target-quality__hint">为物化后的目标表配置检查规则，保存并发布后即可执行质量检查。</p>
			{failure ? (
				<div className="dmx-inline-error" role="alert">
					{failure}
					<Button
						onClick={() => {
							setRefresh((value) => value + 1);
							onChanged();
						}}
					>
						重新加载
					</Button>
				</div>
			) : null}
			{loading ? <span>读取中…</span> : null}
			{!canMaintain ? (
				<p className="dmx-target-quality__hint">规则当前只读，请确认维护权限并先保存模型和实现修改。</p>
			) : null}
			{!loading && !failure && !assets.length ? <span>完成物化后配置质量规则</span> : null}
			{assets.map((asset) => (
				<div className="dmx-target-quality__asset" key={asset.datasetId}>
					<strong>{asset.qualifiedName}</strong>
					{!asset.configurable ? (
						<output>
							{asset.configurationBlockerCode === "QUALITY_DATASET_NOT_DEFAULT_LAKE"
								? "当前目标不在默认数仓，暂不支持质量检查"
								: "当前目标暂不支持质量规则配置"}
						</output>
					) : null}
					<ul className="dmx-target-quality__rules">
						{asset.rules.map((rule) => (
							<li key={rule.id}>
								{rule.name} · v{rule.latestVersion?.version || "—"}{" "}
								<Status tone={rule.latestVersion?.status === "PUBLISHED" ? "success" : "neutral"}>
									{rule.latestVersion?.status === "PUBLISHED" ? "已发布" : "草稿"}
								</Status>
								<Button
									disabled={!canMaintain || !asset.configurable}
									onClick={() => navigate(editorPath(asset, rule))}
								>
									编辑规则
								</Button>
							</li>
						))}
					</ul>
					{asset.configurable && canMaintain ? (
						<ModelTargetRuleForm
							key={`${delivery.modelSpecId}:${asset.datasetId}`}
							asset={asset}
							openRequest={openRequest}
							onSaved={() => {
								setRefresh((value) => value + 1);
								onChanged();
							}}
							onAdvanced={() => navigate(editorPath(asset))}
							onNavigationGuardChange={onNavigationGuardChange}
						/>
					) : null}
				</div>
			))}
		</section>
	);
}

function ModelTargetRuleForm({
	asset,
	openRequest,
	onSaved,
	onAdvanced,
	onNavigationGuardChange,
}: {
	asset: QualityAsset;
	openRequest: number;
	onSaved: () => void;
	onAdvanced: () => void;
	onNavigationGuardChange: (handle: UnsavedEditorHandle | null) => void;
}) {
	const [name, setName] = useState(""),
		[sql, setSql] = useState(""),
		[failure, setFailure] = useState(""),
		[saving, setSaving] = useState(false);
	const pending = useRef(false);
	const nameInput = useRef<HTMLInputElement>(null);
	const [expanded, setExpanded] = useState(openRequest > 0);
	useEffect(() => {
		if (openRequest > 0) setExpanded(true);
	}, [openRequest]);
	useEffect(() => {
		if (expanded && openRequest > 0) nameInput.current?.focus({ preventScroll: true });
	}, [expanded, openRequest]);
	const savedCallback = useRef(onSaved);
	savedCallback.current = onSaved;
	const save = useCallback(
		async (publishNow = true): Promise<boolean> => {
			if (pending.current || !name.trim() || !sql.trim()) return false;
			pending.current = true;
			setSaving(true);
			setFailure("");
			try {
				await createQualityRule(
					buildQualityRulePayload({
						name: name.trim(),
						type: "COMPLETENESS",
						severity: "MEDIUM",
						datasetId: asset.datasetId,
						enabled: true,
						publishNow,
						definition: { sql: sql.trim() },
					}),
				);
				setName("");
				setSql("");
				savedCallback.current();
				return true;
			} catch (error) {
				setFailure(normalizeModelingRequestFailure(error, "规则保存失败").message);
				return false;
			} finally {
				pending.current = false;
				setSaving(false);
			}
		},
		[name, sql, asset.datasetId],
	);
	const discard = useCallback(() => {
		setName("");
		setSql("");
	}, []);
	const saveDraft = useCallback(() => save(false), [save]);
	useEffect(() => {
		onNavigationGuardChange({ dirty: Boolean(name || sql), save: saveDraft, discard });
		return () => onNavigationGuardChange(null);
	}, [name, sql, saveDraft, discard, onNavigationGuardChange]);
	return (
		<details
			className="dmx-target-quality__editor"
			open={expanded}
			onToggle={(event) => setExpanded(event.currentTarget.open)}
		>
			<summary>新增完整性规则</summary>
			<fieldset className="dmx-target-quality__form" disabled={saving}>
				<label>
					<span>规则名称</span>
					<input
						ref={nameInput}
						aria-label="规则名称"
						placeholder="输入规则名称"
						value={name}
						onChange={(event) => setName(event.target.value)}
					/>
				</label>
				<label>
					<span>检测 SQL</span>
					<textarea
						aria-label="检测 SQL"
						placeholder="输入用于检查目标表的 SQL"
						rows={5}
						value={sql}
						onChange={(event) => setSql(event.target.value)}
					/>
				</label>
				<QualitySqlPreflight datasetId={asset.datasetId} sql={sql} disabled={saving} />
				{failure ? (
					<div className="dmx-inline-error" role="alert">
						{failure}
					</div>
				) : null}
				<div className="dmx-target-quality__actions">
					<Button disabled={!name.trim() || !sql.trim() || saving} onClick={() => void save()}>
						{saving ? "保存中…" : "保存并发布规则"}
					</Button>
					<Button onClick={onAdvanced}>更多规则配置</Button>
				</div>
			</fieldset>
		</details>
	);
}
