import { ArrowLeft, Check, Database, Search, WandSparkles } from "lucide-react";
import { useState } from "react";
import { ActionButton, BackendPendingButton, StatusTag } from "./WorkspacePage";

const STEPS = ["逆向策略", "确认模型信息", "生成模型", "完成"];

const DISCOVERED_MODELS = [
	{
		source: "ods_budget_v2",
		target: "ods_budget_execution",
		name: "预算执行导入表",
		layer: "贴源层",
		fields: 8,
	},
	{
		source: "ods_project_subject_domain_v2",
		target: "ods_project_progress",
		name: "项目进度导入表",
		layer: "贴源层",
		fields: 33,
	},
	{
		source: "ods_risk_info_v2",
		target: "ods_project_risk",
		name: "项目风险导入表",
		layer: "贴源层",
		fields: 31,
	},
];

export function ReverseModelingWizard() {
	const [started, setStarted] = useState(false);
	const [step, setStep] = useState(0);
	const [keyword, setKeyword] = useState("ods_%_v2");
	const [matchMode, setMatchMode] = useState<"fuzzy" | "exact">("fuzzy");
	const [selectedModels, setSelectedModels] = useState(() => new Set(DISCOVERED_MODELS.map((item) => item.source)));

	if (!started) {
		return (
			<section className="dm-reverse-intro">
				<div className="dm-reverse-intro__visual">
					<Database aria-hidden="true" size={31} />
					<WandSparkles aria-hidden="true" size={24} />
				</div>
				<div>
					<StatusTag tone="info">结构识别</StatusTag>
					<h2>从已有表快速建立逻辑模型</h2>
					<p>选择数据源和表范围，预览字段、命名与目标分层。确认后再进入真实模型生成阶段。</p>
					<ActionButton kind="primary" onClick={() => setStarted(true)}>
						快速开始
					</ActionButton>
				</div>
				<aside>
					<strong>逆向建模流程</strong>
					<ol>
						{STEPS.map((item, index) => (
							<li key={item}>
								<span>{index + 1}</span>
								{item}
							</li>
						))}
					</ol>
				</aside>
			</section>
		);
	}

	const toggleModel = (source: string, checked: boolean) => {
		setSelectedModels((current) => {
			const next = new Set(current);
			if (checked) next.add(source);
			else next.delete(source);
			return next;
		});
	};

	return (
		<section className="dm-reverse-wizard">
			<header className="dm-reverse-wizard__header">
				<ActionButton
					kind="quiet"
					onClick={() => {
						if (step === 0) setStarted(false);
						else setStep((current) => Math.max(0, current - 1));
					}}
				>
					<ArrowLeft aria-hidden="true" size={15} />
					返回
				</ActionButton>
				<div>
					<h2>逆向建模</h2>
					<p>仅进行结构预览；生成动作将在后台重构完成后开放。</p>
				</div>
			</header>
			<ol className="dm-wizard-steps" aria-label="逆向建模步骤">
				{STEPS.map((item, index) => (
					<li className={`${index === step ? "is-active" : ""} ${index < step ? "is-done" : ""}`} key={item}>
						<span>{index < step ? <Check aria-hidden="true" size={13} /> : index + 1}</span>
						<strong>{item}</strong>
					</li>
				))}
			</ol>

			<div className="dm-wizard-content">
				{step === 0 ? (
					<div className="dm-reverse-form">
						<label>
							<span>
								项目空间 <b>*</b>
							</span>
							<select className="dm-select" defaultValue="默认工作空间">
								<option>默认工作空间</option>
							</select>
						</label>
						<label>
							<span>
								数据源类型 <b>*</b>
							</span>
							<select className="dm-select" defaultValue="PostgreSQL">
								<option>PostgreSQL</option>
							</select>
						</label>
						<label>
							<span>
								数据源名称 <b>*</b>
							</span>
							<select className="dm-select" defaultValue="dts_demo">
								<option>dts_demo</option>
							</select>
						</label>
						<fieldset>
							<legend>表名匹配规则</legend>
							<label>
								<input
									checked={matchMode === "fuzzy"}
									name="match-mode"
									onChange={() => setMatchMode("fuzzy")}
									type="radio"
								/>
								模糊匹配
							</label>
							<label>
								<input
									checked={matchMode === "exact"}
									name="match-mode"
									onChange={() => setMatchMode("exact")}
									type="radio"
								/>
								精准匹配
							</label>
						</fieldset>
						<label className="dm-reverse-form__wide">
							<span>表名关键字</span>
							<div className="dm-input-with-icon">
								<Search aria-hidden="true" size={14} />
								<input className="dm-input" onChange={(event) => setKeyword(event.target.value)} value={keyword} />
							</div>
						</label>
						<fieldset>
							<legend>模型所在分层</legend>
							<label>
								<input defaultChecked name="layer" type="radio" /> 贴源层
							</label>
							<label>
								<input name="layer" type="radio" /> 公共层
							</label>
							<label>
								<input name="layer" type="radio" /> 应用层
							</label>
						</fieldset>
						<label>
							<span>表命名规范</span>
							<select className="dm-select" defaultValue="表名检查器">
								<option>表名检查器</option>
								<option>自定义规则</option>
							</select>
						</label>
						<fieldset>
							<legend>执行方式</legend>
							<label>
								<input name="run-mode" type="radio" /> 全量覆盖
							</label>
							<label>
								<input defaultChecked name="run-mode" type="radio" /> 增量更新
							</label>
						</fieldset>
					</div>
				) : null}

				{step === 1 ? (
					<div className="dm-reverse-confirm">
						<div className="dm-reverse-summary">
							<div>
								<span>数据源</span>
								<strong>dts_demo / PostgreSQL</strong>
							</div>
							<div>
								<span>匹配范围</span>
								<strong>{keyword || "未填写"}</strong>
							</div>
							<div>
								<span>目标分层</span>
								<strong>贴源层</strong>
							</div>
							<div>
								<span>已选模型</span>
								<strong>{selectedModels.size} 个</strong>
							</div>
						</div>
						<div className="dm-field-table-wrap">
							<table className="dm-field-table dm-reverse-table">
								<thead>
									<tr>
										<th>选择</th>
										<th>来源表</th>
										<th>目标模型编码</th>
										<th>模型中文名</th>
										<th>分层</th>
										<th>字段数</th>
									</tr>
								</thead>
								<tbody>
									{DISCOVERED_MODELS.map((model) => (
										<tr key={model.source}>
											<td className="dm-field-table__check">
												<input
													aria-label={`选择 ${model.source}`}
													checked={selectedModels.has(model.source)}
													onChange={(event) => toggleModel(model.source, event.target.checked)}
													type="checkbox"
												/>
											</td>
											<td>
												<code>{model.source}</code>
											</td>
											<td>
												<input aria-label={`${model.source} 目标编码`} defaultValue={model.target} />
											</td>
											<td>
												<input aria-label={`${model.source} 中文名`} defaultValue={model.name} />
											</td>
											<td>{model.layer}</td>
											<td>{model.fields}</td>
										</tr>
									))}
								</tbody>
							</table>
						</div>
					</div>
				) : null}

				{step === 2 ? (
					<div className="dm-generation-preview">
						<WandSparkles aria-hidden="true" size={28} />
						<h3>模型生成预览已就绪</h3>
						<p>将为 {selectedModels.size} 张来源表生成贴源层模型草稿，并保留来源字段映射。</p>
						<ul>
							<li>应用表名检查器并展示命名差异</li>
							<li>识别字段类型、非空约束和主键候选</li>
							<li>记录来源数据源、Schema、表和字段</li>
						</ul>
						<BackendPendingButton>开始创建模型</BackendPendingButton>
						<small>后台能力尚未接入，因此当前不会生成模型或进入“完成”状态。</small>
					</div>
				) : null}

				{step === 3 ? (
					<div className="dm-generation-preview">
						<Check aria-hidden="true" size={30} />
						<h3>模型生成完成</h3>
						<p>后台接入后，此处将展示成功、跳过和失败的对象以及模型目录入口。</p>
					</div>
				) : null}
			</div>

			<footer className="dm-wizard-footer">
				<span>界面阶段不读取真实数据库，也不创建模型。</span>
				<div>
					{step > 0 ? <ActionButton onClick={() => setStep((current) => current - 1)}>上一步</ActionButton> : null}
					{step < 2 ? (
						<ActionButton
							disabled={step === 1 && selectedModels.size === 0}
							kind="primary"
							onClick={() => setStep((current) => current + 1)}
						>
							下一步
						</ActionButton>
					) : null}
				</div>
			</footer>
		</section>
	);
}
