import { ShieldCheck, TestTube2 } from "lucide-react";
import type { ModelSpecStageGate } from "@/api/modelSpecApi";
import { ModelStageGatePanel } from "./ModelStageGatePanel";
import { Status } from "./PrototypePrimitives";

const modelTestState = (gates: ModelSpecStageGate[]) => {
	const release = gates.find((gate) => gate.stage === "RELEASE_READY");
	if (!release) return { label: "待读取", tone: "neutral" } as const;
	if (release.blockers.some((blocker) => blocker.code === "MODEL_SPEC_GATE_EVIDENCE_STALE")) {
		return { label: "需刷新证据", tone: "warning" } as const;
	}
	if (release.blockers.some((blocker) => blocker.code.startsWith("MODEL_SPEC_TEST_EVIDENCE"))) {
		return { label: "待完成", tone: "warning" } as const;
	}
	return { label: "已通过", tone: "success" } as const;
};

export function ModelQualityConstraintPanel({ gates, modelName }: { gates: ModelSpecStageGate[]; modelName: string }) {
	const testState = modelTestState(gates);
	return (
		<section className="dmx-quality-constraint">
			<p className="dmx-quality-constraint__intro">
				当前模型只维护与修订绑定的工程测试；治理质量规则唯一在数据治理中维护，物化后绑定目录中的物理数据资产，
				候选发布时再核验已发布规则版本及最新运行证据。
			</p>
			<div className="dmx-quality-constraint__lanes">
				<article className="dmx-quality-constraint__lane">
					<header className="dmx-quality-constraint__header">
						<span className="dmx-quality-constraint__icon">
							<TestTube2 aria-hidden="true" size={18} />
						</span>
						<div className="dmx-quality-constraint__heading">
							<strong>模型测试</strong>
							<small className="dmx-quality-constraint__scope">{modelName} · 当前模型修订</small>
						</div>
						<Status tone={testState.tone}>{testState.label}</Status>
					</header>
					<p className="dmx-quality-constraint__description">
						用于校验字段契约、依赖关系和 dbt tests，随构建执行并形成当前版本的 TEST 证据。
					</p>
				</article>
				<article className="dmx-quality-constraint__lane">
					<header className="dmx-quality-constraint__header">
						<span className="dmx-quality-constraint__icon">
							<ShieldCheck aria-hidden="true" size={18} />
						</span>
						<div className="dmx-quality-constraint__heading">
							<strong>治理质量规则</strong>
							<small className="dmx-quality-constraint__scope">数据治理统一事实源</small>
						</div>
						<Status tone="info">物化后核验</Status>
					</header>
					<p className="dmx-quality-constraint__description">
						用于完整性、唯一性、及时性和业务阈值检查；规则版本、资产绑定、运行记录与问题处置集中管理。
					</p>
					<div className="dmx-quality-constraint__actions">
						<a className="dmx-quality-constraint__action" href="#/governance/rules">
							进入数据质量中心
						</a>
						<a className="dmx-quality-constraint__action" href="#/governance/rules/catalog/new">
							新建治理质量规则
						</a>
					</div>
				</article>
			</div>
			<div className="dmx-quality-constraint__gate">
				<h3 className="dmx-quality-constraint__gate-title">当前模型交付检查</h3>
				<p className="dmx-quality-constraint__gate-description">
					这里只展示模型版本的工程交付证据；物理数据质量是否允许发布，以候选发布流程中的治理质量证据为准。
				</p>
				<ModelStageGatePanel gates={gates} targetStage="RELEASE_READY" />
			</div>
		</section>
	);
}
