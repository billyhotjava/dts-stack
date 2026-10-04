/**
 * 数据接入运行时与架构策略面板。
 *
 * 全局入口：
 *   window.renderRuntimeStrategyPanel()
 *   window.RuntimeStrategy.renderPanel()
 *
 * 返回一个 DOM 节点，由 views.js 内嵌到既有系统管理页。
 */

(function () {
"use strict";

const EXECUTION_FLOW = [
	{ key: "execution-plan", name: "ExecutionPlan", boundary: "active Revision 固化可执行计划；draft Revision 不直接运行" },
	{ key: "adapter", name: "Adapter", boundary: "按来源选择 Addax batch / API runtime / native file / CDC" },
	{ key: "airflow", name: "Airflow orchestration", boundary: "只负责编排、依赖、调度、重试与运行证据" },
	{ key: "ods-admission", name: "ODS admission", boundary: "staging 校验、拒绝或准入 ODS" },
	{ key: "dbt", name: "dbt transform", boundary: "只处理落地后的 ODS → DWD / DWS / ADS 转换" },
];

const ADAPTERS = [
		{
			name: "Addax batch",
			scope: "数据库批量",
			note: "短期保留，藏在统一 adapter contract 后",
		tone: "ok",
	},
	{
		name: "API runtime",
		scope: "HTTP / REST API",
		note: "沿用现有运行时，不迁入 Addax",
		tone: "info",
	},
	{
		name: "native file",
		scope: "Excel / CSV 原生解析",
		note: "保留文件语义与 staging 预检",
		tone: "info",
	},
	{
		name: "CDC",
		scope: "持续变更流",
		note: "Debezium / Flink 为受控候选",
		tone: "warn",
	},
];

const DECISION_MATRIX = [
	{
			subject: "Addax",
			decision: "短期保留",
			use: "数据库批量；文件场景最多按需复用 Writer，统一藏在 batch adapter 后",
		limit: "不让 ExecutionPlan、界面或 ODS 准入直接依赖 Addax 私有参数",
		status: { label: "保留", tone: "ok" },
	},
	{
		subject: "Airflow",
		decision: "只做编排",
		use: "DAG、依赖、调度、重试、补数与运行证据",
		limit: "不承载连接器领域模型，不做 ODS 准入或业务转换",
		status: { label: "边界固定", tone: "info" },
	},
	{
		subject: "dbt",
		decision: "只做落地后转换",
		use: "ODS 已准入后的 DWD / DWS / ADS 建模",
		limit: "不负责抽取、传输、staging 修复或入湖准入",
		status: { label: "边界固定", tone: "info" },
	},
	{
		subject: "现有 API runtime",
		decision: "继续沿用",
		use: "鉴权、分页、游标、限流、重试与 TLS 策略",
		limit: "通过 api adapter 接入 ExecutionPlan，不另造第二套 API 引擎",
		status: { label: "沿用", tone: "ok" },
	},
	{
		subject: "SeaTunnel",
		decision: "受控 PoC 候选",
		use: "选择少量批量连接器对比吞吐、可运维性与迁移成本",
		limit: "PoC 通过也先按 adapter 逐项替换，不作全平台切换",
		status: { label: "PoC", tone: "warn" },
	},
	{
		subject: "Debezium / Flink",
		decision: "CDC 候选",
		use: "需要持续捕获变更的明确场景，单独验证顺序、重放与 schema 演进",
		limit: "不把 CDC 候选扩成批量同步或全平台运行时替代",
		status: { label: "候选", tone: "warn" },
	},
	{
		subject: "Airbyte",
		decision: "Airbyte concepts only",
		use: "只借鉴 connector catalog、配置契约、能力声明与可观测性概念",
		limit: "不引入 Airbyte runtime，不据此承诺兼容其连接器生态",
		status: { label: "仅参考", tone: "muted" },
	},
	{
		subject: "整体替换",
		decision: "No wholesale replacement",
		use: "以稳定 adapter seam 渐进演进，每种来源单独决策",
		limit: "不启动 Addax / Airflow / dbt / API runtime 的一次性大替换",
		status: { label: "明确否决", tone: "bad" },
	},
];

function strategyChip(V, meta) {
	return V("span", { class: `chip chip-${meta.tone}` }, meta.label);
}

function renderStage(V, stage, tone = "quiet") {
	return V("div", {
		class: "panel",
		style: "margin:0;padding:12px 14px;min-height:138px;",
	}, [
		V("span", { class: `chip chip-${tone}` }, stage.key),
		V("h3", { style: "margin:10px 0 7px;font-size:14px;" }, stage.name),
		V("p", { class: "muted small", style: "margin:0;line-height:1.65;" }, stage.boundary),
	]);
}

function renderAdapterStage(V) {
	return V("div", {
		class: "panel",
		style: "margin:0;padding:12px 14px;min-height:138px;",
	}, [
		V("div", { class: "panel-head", style: "margin-bottom:8px;" }, [
			V("h3", {}, "Adapter layer"),
			V("span", { class: "panel-note" }, "同一 ExecutionPlan 契约，多种执行实现"),
		]),
		V("div", {
			style: "display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:7px;",
		}, ADAPTERS.map((adapter) => V("div", {
			style: "border:1px solid var(--border-soft);border-radius:7px;padding:8px;",
		}, [
			V("div", { style: "display:flex;align-items:center;gap:6px;margin-bottom:3px;" }, [
				V("b", { class: "small" }, adapter.name),
				V("span", { class: `chip chip-${adapter.tone}` }, adapter.scope),
			]),
			V("div", { class: "muted small" }, adapter.note),
		]))),
	]);
}

function flowArrow(V) {
	return V("div", {
		"aria-hidden": "true",
		style: "display:grid;place-items:center;color:var(--blue);font-size:22px;",
	}, "→");
}

function renderRuntimeStrategyPanel() {
	const V = window.SchemaForm && window.SchemaForm.el;
	if (typeof V !== "function") {
		throw new Error("RuntimeStrategy requires window.SchemaForm.el");
	}

	return V("div", { class: "runtime-strategy" }, [
		V("section", { class: "panel", style: "margin-top:16px;" }, [
			V("div", { class: "panel-head" }, [
				V("h3", {}, "运行时架构边界"),
				V("span", { class: "panel-note" },
					"ExecutionPlan → adapters → Airflow orchestration → ODS admission → dbt transform"),
			]),
			V("div", { class: "inline-alert info" }, [
				V("b", {}, "唯一执行入口"),
				V("span", {},
					"AccessWorkspace 只聚合视图；active Revision 生成 ExecutionPlan，执行层不读取未发布的 draft Revision。"),
			]),
			V("div", { class: "table-scroll" }, V("div", {
				style: "display:grid;grid-template-columns:180px 34px 350px 34px 190px 34px 190px 34px 190px;gap:8px;align-items:stretch;min-width:1260px;padding-bottom:4px;",
			}, [
				renderStage(V, EXECUTION_FLOW[0], "info"),
				flowArrow(V),
				renderAdapterStage(V),
				flowArrow(V),
				renderStage(V, EXECUTION_FLOW[2], "quiet"),
				flowArrow(V),
				renderStage(V, EXECUTION_FLOW[3], "warn"),
				flowArrow(V),
				renderStage(V, EXECUTION_FLOW[4], "ok"),
			])),
			V("div", { class: "foot-note" },
				"Adapter 是替换边界：运行时可逐来源演进，但 ExecutionPlan、Airflow DAG、ODS 准入和下游 dbt 契约保持稳定。"),
		]),
		V("section", { class: "panel" }, [
			V("div", { class: "panel-head" }, [
				V("h3", {}, "运行时决策矩阵"),
				V("span", { class: "panel-note" }, "短期收敛边界与受控候选"),
			]),
			V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
				V("thead", {}, V("tr", {}, ["对象", "决策", "允许范围", "禁止越界", "阶段"].map((heading) => V("th", {}, heading)))),
				V("tbody", {}, DECISION_MATRIX.map((row) => V("tr", {}, [
					V("td", {}, V("b", {}, row.subject)),
					V("td", {}, row.decision),
					V("td", {}, row.use),
					V("td", { class: "muted small" }, row.limit),
					V("td", {}, strategyChip(V, row.status)),
				]))),
			])),
		]),
	]);
}

window.RuntimeStrategy = Object.freeze({
	executionFlow: EXECUTION_FLOW,
	adapters: ADAPTERS,
	decisions: DECISION_MATRIX,
	renderPanel: renderRuntimeStrategyPanel,
});
window.renderRuntimeStrategyPanel = renderRuntimeStrategyPanel;

})();
