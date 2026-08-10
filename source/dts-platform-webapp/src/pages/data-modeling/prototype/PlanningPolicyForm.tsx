import { RefreshCw, Save } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import {
	saveWarehousePlanPolicy,
	type WarehousePlanBusinessCategoryMode,
	type WarehousePlanBusinessProcessMode,
	type WarehousePlanPolicyInput,
} from "@/api/warehousePlanApi";
import { Button, RequestState } from "./PrototypePrimitives";
import { loadPlanningContextPolicy, type PlanningContextPolicy } from "./services/planningContextPolicyService";
import { normalizeModelingRequestFailure } from "./services/planningProjectionService";

export function PlanningPolicyForm({ canMaintain }: { canMaintain: boolean }) {
	const [context, setContext] = useState<PlanningContextPolicy | null>(null);
	const [loading, setLoading] = useState(true);
	const [saving, setSaving] = useState(false);
	const [failure, setFailure] = useState("");
	const [message, setMessage] = useState("");

	const load = useCallback(async () => {
		setLoading(true);
		setFailure("");
		setMessage("");
		try {
			setContext(await loadPlanningContextPolicy());
		} catch (error) {
			setContext(null);
			setFailure(normalizeModelingRequestFailure(error, "建模策略读取失败，请稍后重试。").message);
		} finally {
			setLoading(false);
		}
	}, []);

	useEffect(() => {
		void load();
	}, [load]);

	const patch = (
		value: Partial<
			Pick<
				PlanningContextPolicy["policy"],
				"businessCategoryMode" | "defaultBusinessCategoryId" | "businessProcessMode"
			>
		>,
	) => setContext((current) => (current ? { ...current, policy: { ...current.policy, ...value } } : current));

	const save = async () => {
		if (!context || !canMaintain) return;
		if (context.policy.businessCategoryMode === "SINGLE_DEFAULT" && !context.policy.defaultBusinessCategoryId) {
			setFailure("单默认分类模式需要选择一个当前规划内已确认的业务分类。");
			return;
		}
		setSaving(true);
		setFailure("");
		setMessage("");
		try {
			const input: WarehousePlanPolicyInput = {
				layerScheme: context.policy.layerScheme,
				namingPolicy: context.policy.namingPolicy,
				historyPolicy: context.policy.historyPolicy,
				defaultTimeZone: context.policy.defaultTimeZone,
				conceptualDesignAllowed: context.policy.conceptualDesignAllowed,
				standardCoverage: context.policy.standardCoverage,
				qualityGate: context.policy.qualityGate,
				businessCategoryMode: context.policy.businessCategoryMode,
				defaultBusinessCategoryId: context.policy.defaultBusinessCategoryId,
				businessProcessMode: context.policy.businessProcessMode,
			};
			const saved = await saveWarehousePlanPolicy(context.planId, context.version, input);
			setContext((current) =>
				current
					? {
							...current,
							version: saved.version,
							policy: {
								...current.policy,
								...saved.value,
								businessCategoryMode: saved.value.businessCategoryMode || current.policy.businessCategoryMode,
								businessProcessMode: saved.value.businessProcessMode || current.policy.businessProcessMode,
								defaultBusinessCategoryId:
									saved.value.defaultBusinessCategoryId === undefined
										? current.policy.defaultBusinessCategoryId
										: saved.value.defaultBusinessCategoryId,
							},
						}
					: current,
			);
			setMessage("建模策略已保存；新的默认分类只用于后续创建，不会搬迁历史对象。");
		} catch (error) {
			setFailure(normalizeModelingRequestFailure(error, "建模策略保存失败，请刷新后重试。").message);
		} finally {
			setSaving(false);
		}
	};

	if (loading) return <RequestState description="正在读取当前建模策略与分类范围。" kind="loading" title="正在加载" />;
	if (!context) {
		return (
			<RequestState
				description={failure || "当前没有可用规划。"}
				kind="error"
				onRetry={() => void load()}
				title="建模策略读取失败"
			/>
		);
	}

	return (
		<section className="dmx-catalog-panel">
			<div className="dmx-planning-editor">
				<div className="dmx-form-grid">
					<label>
						<span className="required">业务分类模式</span>
						<select
							aria-label="业务分类模式"
							disabled={!canMaintain || saving}
							onChange={(event) =>
								patch({ businessCategoryMode: event.target.value as WarehousePlanBusinessCategoryMode })
							}
							value={context.policy.businessCategoryMode}
						>
							<option value="SINGLE_DEFAULT">单一默认分类（日常建模隐藏）</option>
							<option value="MULTI_SELECT">多分类（按需显式选择）</option>
						</select>
					</label>
					<label>
						<span className={context.policy.businessCategoryMode === "SINGLE_DEFAULT" ? "required" : ""}>
							新对象默认业务分类
						</span>
						<select
							aria-label="默认业务分类"
							disabled={!canMaintain || saving}
							onChange={(event) => patch({ defaultBusinessCategoryId: event.target.value || null })}
							value={context.policy.defaultBusinessCategoryId || ""}
						>
							<option value="">请选择当前规划内的分类</option>
							{context.categories.map((category) => (
								<option key={category.domainId} value={category.domainId}>
									{category.name || category.domainId}
									{category.code ? ` · ${category.code}` : ""}
								</option>
							))}
						</select>
						{!context.categories.length ? <small>请先在当前规划中确认一个有效业务分类。</small> : null}
					</label>
					<label>
						<span className="required">业务过程模式</span>
						<select
							aria-label="业务过程模式"
							disabled={!canMaintain || saving}
							onChange={(event) =>
								patch({ businessProcessMode: event.target.value as WarehousePlanBusinessProcessMode })
							}
							value={context.policy.businessProcessMode}
						>
							<option value="AUTO_SELECT_SINGLE">唯一过程自动选择</option>
							<option value="MANAGED">每次人工确认</option>
						</select>
					</label>
					<label>
						<span>既有数仓策略</span>
						<input
							disabled
							value={`${context.policy.layerScheme || "未配置"} / ${context.policy.namingPolicy || "未配置"}`}
						/>
					</label>
				</div>
				<p className="dmx-capability-note">
					默认分类仅影响后续新建对象；FACT 与原子指标仍绑定真实业务过程，维度、汇总和应用模型不要求业务过程。
				</p>
				{failure ? (
					<div className="dmx-inline-error" role="alert">
						{failure}
					</div>
				) : null}
				{message ? (
					<div className="dmx-capability-note" role="status">
						{message}
					</div>
				) : null}
				<div className="dmx-catalog-actions">
					<Button disabled={!canMaintain || saving} primary onClick={() => void save()}>
						<Save size={15} /> {saving ? "保存中…" : "保存建模策略"}
					</Button>
					<Button disabled={loading || saving} onClick={() => void load()}>
						<RefreshCw size={15} /> 重新加载
					</Button>
				</div>
			</div>
		</section>
	);
}
