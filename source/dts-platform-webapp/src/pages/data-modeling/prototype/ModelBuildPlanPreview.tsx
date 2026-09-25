import type { MaterializationPlanEntry, MaterializationPlanPreview, MaterializationPlanStrategy } from "@/api/modelSpecApi";
import { CompactTable } from "@/components/table";
import { Button } from "./PrototypePrimitives";
import { useModelMaterializationColumns } from "./useModelMaterializationColumns";

export function ModelBuildPlanPreview({ strategy, setStrategy, currentOnlyAvailable, planState, busy, refreshMaterializationPlan, planFailure, materializationPlan }: {
    strategy: MaterializationPlanStrategy; setStrategy: (value: MaterializationPlanStrategy) => void;
    currentOnlyAvailable: boolean; planState: string; busy: boolean; refreshMaterializationPlan: () => Promise<unknown>;
    planFailure: string; materializationPlan: MaterializationPlanPreview | null;
}) {
    const { materializationPlanColumns } = useModelMaterializationColumns();
    return <>
			<label>
				<span>依赖策略</span>
				<select
					onChange={(event) => setStrategy(event.target.value as MaterializationPlanStrategy)}
					value={strategy}
				>
					<option value="WITH_MISSING_UPSTREAMS">缺失上游一并构建（推荐）</option>
					<option disabled={!currentOnlyAvailable} value="CURRENT_ONLY">
						仅使用当前已验证上游
					</option>
				</select>
			</label>
			<div className="dmx-materialization-plan-toolbar">
				<strong>依赖构建计划</strong>
				<Button
					disabled={planState === "loading" || Boolean(busy)}
					onClick={() => void refreshMaterializationPlan()}
				>
					{planState === "loading" ? "预览中…" : "刷新计划"}
				</Button>
			</div>
			{planState === "error" ? (
				<div className="dmx-inline-error" role="alert">
					{planFailure}
				</div>
			) : null}
			{materializationPlan ? (
				<>
					<div className="dmx-table-scroll dmx-materialization-plan-table">
						<CompactTable<MaterializationPlanEntry>
							columns={materializationPlanColumns}
							dataSource={materializationPlan.orderedEntries}
							pagination={false}
							rowKey="modelSpecId"
						/>
					</div>
					{materializationPlan.blockers.length ? (
						<ul className="dmx-materialization-plan-blockers">
							{materializationPlan.blockers.map((blocker) => (
								<li key={`${blocker.code}:${blocker.modelSpecId || "plan"}`}>
									<strong>{blocker.code}</strong>：{blocker.message}
								</li>
							))}
						</ul>
					) : null}
					<p className="dmx-materialization-plan-checksum">
						计划校验码：{materializationPlan.planChecksum.slice(0, 12)}…
					</p>
				</>
			) : planState === "loading" ? (
				<p className="dmx-capability-note">正在计算 BUILD、REUSE 与阻断项…</p>
			) : null}
    </>;
}
