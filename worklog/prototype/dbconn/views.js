/**
 * 列表页 / 详情运维页 / 连接器与驱动（系统管理）/ 设计说明。
 */

(function () {

const V = window.SchemaForm.el;
const P = window.PROTO;

const stateChip = (key) => {
	const meta = P.STATE_META[key] || { label: key, tone: "muted" };
	return V("span", { class: `chip chip-${meta.tone}` }, meta.label);
};

const STATUS_META = {
	lifecycle: { ACTIVE: ["已发布", "ok"], PAUSED: ["已暂停", "warn"], DRAFT: ["草稿", "muted"], ARCHIVED: ["已归档", "muted"] },
	health: { HEALTHY: ["健康", "ok"], ATTENTION: ["需关注", "warn"], NOT_EVALUATED: ["未评估", "muted"] },
	connectionHealth: { UP: ["可用", "ok"], RETEST_REQUIRED: ["待复测", "warn"], NOT_TESTED: ["未测试", "muted"], NOT_APPLICABLE: ["不适用（文件）", "muted"] },
	admission: { ADMITTED: ["已准入 ODS", "ok"], CONDITIONAL: ["有条件准入", "warn"], REVIEW_REQUIRED: ["待人工确认", "warn"], NOT_STARTED: ["未发起", "muted"] },
	lastRun: { SUCCESS: ["成功", "ok"], PARTIAL: ["部分成功", "warn"], FAILED: ["失败", "bad"], STAGING: ["等待准入", "info"], RUNNING: ["执行中", "info"], NOT_RUN: ["未运行", "muted"] },
};

const WORKSPACE_META = {
	"ing-1042": { lifecycle: "ACTIVE", health: "ATTENTION", connectionHealth: "UP", admission: "ADMITTED", active: "R12", draft: null },
	"ing-1039": { lifecycle: "ACTIVE", health: "ATTENTION", connectionHealth: "RETEST_REQUIRED", admission: "CONDITIONAL", active: "R08", draft: "R09 · 待审批" },
	"ing-1035": { lifecycle: "ACTIVE", health: "HEALTHY", connectionHealth: "UP", admission: "ADMITTED", active: "R06", draft: null },
	"ing-1028": { lifecycle: "ACTIVE", health: "ATTENTION", connectionHealth: "NOT_APPLICABLE", admission: "REVIEW_REQUIRED", active: "R05", draft: null },
	"ing-1011": { lifecycle: "PAUSED", health: "HEALTHY", connectionHealth: "UP", admission: "ADMITTED", active: "R04", draft: null },
	"ing-1007": { lifecycle: "DRAFT", health: "NOT_EVALUATED", connectionHealth: "NOT_TESTED", admission: "NOT_STARTED", active: null, draft: "R01 · 初始草稿" },
};

const plainChip = (meta) =>
	V("span", { class: `chip chip-${meta.tone}`, title: meta.code || meta.label }, meta.label);

const labeledChip = (label, meta) =>
	V("span", { class: `chip chip-${meta.tone}`, title: meta.code || meta.label }, `${label} · ${meta.label}`);

function statusMetaOf(dimension, code) {
	const value = STATUS_META[dimension]?.[code];
	return value
		? { code, label: value[0], tone: value[1] }
		: { code: code || "UNKNOWN", label: `未知（${code || "缺失"}）`, tone: "bad" };
}

function workspacePresentation(conn) {
	const fixture = WORKSPACE_META[conn.id] || {};
	const lastRunCode = conn.lastRunState === "—" ? "NOT_RUN" : conn.lastRunState;
	return {
		lifecycle: statusMetaOf("lifecycle", fixture.lifecycle),
		health: statusMetaOf("health", fixture.health),
		connectionHealth: statusMetaOf("connectionHealth", fixture.connectionHealth),
		admission: statusMetaOf("admission", fixture.admission),
		lastRun: statusMetaOf("lastRun", lastRunCode),
		revision: { active: fixture.active || null, draft: fixture.draft || null },
	};
}

function revisionChip(label, value, tone = "quiet") {
	return V("span", { class: `chip chip-${tone}` }, `${label} · ${value || "无"}`);
}

function nextRevisionId(activeRevision) {
	const matched = /^R(\d+)$/.exec(activeRevision || "");
	if (!matched) return `${activeRevision || "R00"}.1`;
	const next = String(Number(matched[1]) + 1).padStart(matched[1].length, "0");
	return `R${next}`;
}

function openDraftRevision(conn, rerender) {
	const { revision } = workspacePresentation(conn);
	if (!revision.active) {
		window.alert(`打开 ${revision.draft}。初始草稿发布前不会生成 active Revision。`);
		return;
	}
	if (revision.draft) {
		window.alert(`打开已有草稿 ${revision.draft}；当前生效配置仍为 ${revision.active}。`);
		return;
	}
	const draft = `${nextRevisionId(revision.active)} · 未发布`;
	WORKSPACE_META[conn.id] = { ...WORKSPACE_META[conn.id], active: revision.active, draft };
	window.alert(`已从 active Revision ${revision.active} 复制出 ${draft}；发布前不会影响当前运行计划。`);
	if (typeof rerender === "function") rerender();
}

/* ---------------------------------------------------------------- */
/* 一、数据接入（列表）                                                */
/* ---------------------------------------------------------------- */

function renderList(nav) {
	const cards = P.CONNECTIONS.map((c) => {
		const connector = P.connectorByKey(c.connector);
		const status = workspacePresentation(c);
		const alerts = [];
		if (c.health.drift) alerts.push(`${c.health.drift} 处结构漂移`);
		if (c.health.failed7d) alerts.push(`7 日内 ${c.health.failed7d} 次失败`);
		if (c.health.pendingChange) alerts.push(`${c.health.pendingChange} 项变更待审批`);
		if (c.health.stagingErrors) alerts.push(`${c.health.stagingErrors} 行待人工确认`);

		return V("article", {
			class: `conn-card tone-${status.health.tone}`,
			onclick: () => nav("detail", c.id),
		}, [
			V("div", { class: "conn-top" }, [
				V("span", { class: "conn-icon" }, connector?.icon || "🔌"),
				V("div", { class: "conn-title" }, [
					V("h4", {}, c.name),
					V("span", { class: "muted small" },
						`AccessWorkspace ${c.id} · ${connector?.name || c.connector} · ${c.endpoint}`),
				]),
				labeledChip("生命周期", status.lifecycle),
			]),
			V("div", { class: "conn-metrics" }, [
				["总体健康", status.health],
				["连接健康", status.connectionHealth],
				["ODS 准入", status.admission],
				["上次运行", status.lastRun],
			].map(([k, meta]) => V("div", {}, [
				V("span", { class: "muted small" }, k),
				plainChip(meta),
			]))),
			V("div", { class: "conn-alerts" }, [
				revisionChip("active Revision", status.revision.active, status.revision.active ? "info" : "muted"),
				revisionChip("draft Revision", status.revision.draft, status.revision.draft ? "warn" : "quiet"),
			]),
			alerts.length
				? V("div", { class: "conn-alerts" }, alerts.map((a) => V("span", { class: "chip chip-warn" }, a)))
				: V("div", { class: "conn-alerts" }, V("span", { class: "chip chip-quiet" }, "无待处理事项")),
			V("div", { class: "conn-foot" }, [
				V("span", { class: "muted small" },
					`密级 ${c.classification} · ${c.owner} · ${c.tables} 张表 · ${c.syncMode === "incremental" ? "增量" : "全量"}`),
				V("span", { class: "muted small" }, `调度 ${c.schedule} · 下次 ${c.nextRun}`),
			]),
		]);
	});

	return V("div", { class: "page" }, [
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, "数据接入"),
				V("p", { class: "muted" },
					"AccessWorkspace 聚合连接、执行计划、ODS 准入与运行证据；底层对象独立演进，不是一个物理“连接 + 任务”实体。"),
			]),
			V("button", { class: "btn primary", onclick: () => nav("wizard") }, "+ 新建接入"),
		]),
		V("div", { class: "filters" }, [
			V("input", { class: "ctl", placeholder: "搜索接入名称、主机、库名…" }),
			V("select", { class: "ctl narrow" }, [V("option", {}, "全部生命周期"), V("option", {}, "已发布"), V("option", {}, "草稿"), V("option", {}, "已暂停")]),
			V("select", { class: "ctl narrow" }, [V("option", {}, "全部健康"), V("option", {}, "健康"), V("option", {}, "需关注"), V("option", {}, "未评估")]),
			V("select", { class: "ctl narrow" }, [V("option", {}, "全部类型"), V("option", {}, "数据库"), V("option", {}, "API"), V("option", {}, "文件")]),
			V("select", { class: "ctl narrow" }, [V("option", {}, "全部部门")]),
		]),
		V("div", { class: "conn-grid" }, cards),
	]);
}

/* ---------------------------------------------------------------- */
/* 二、接入详情 / 运维                                                 */
/* ---------------------------------------------------------------- */

const DETAIL_TABS = [
	{ key: "overview", label: "概览" },
	{ key: "runs", label: "运行历史" },
	{ key: "drift", label: "结构漂移", badge: (c) => c.health.drift },
	{ key: "staging", label: "落地预检", badge: (c) => c.health.stagingErrors },
	{ key: "changes", label: "变更记录", badge: (c) => c.health.pendingChange },
	{ key: "config", label: "配置" },
];

const DETAIL = { tab: "overview" };

function renderRuns() {
	return V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
		V("thead", {}, V("tr", {}, ["批次", "开始时间", "耗时", "结果", "行数", "表", "触发", "说明", ""].map((h) => V("th", {}, h)))),
		V("tbody", {}, P.RUNS.map((r) => V("tr", {}, [
			V("td", {}, V("code", {}, r.id)),
			V("td", {}, r.started),
			V("td", {}, r.duration),
			V("td", {}, stateChip(r.state)),
			V("td", { class: "num" }, r.rows.toLocaleString("zh-CN")),
			V("td", { class: "num" }, r.tables),
			V("td", {}, r.trigger),
			V("td", { class: "muted small" }, r.note || "—"),
			V("td", {}, V("div", { class: "row-actions" }, [
				V("button", { class: "link-btn" }, "日志"),
				r.state !== "SUCCESS" ? V("button", { class: "link-btn" }, "重跑") : null,
			])),
		]))),
	]));
}

function renderDrift() {
	return V("div", {}, [
		V("div", { class: "inline-alert info" }, [
			V("b", {}, "为什么在这里"),
			V("span", {}, "现网这块在「数据治理 / 元数据管理」，与运行失败分属两个模块。漂移是接入的运行时事件，应当和运行历史并列。"),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
			V("thead", {}, V("tr", {}, ["表", "变化", "详情", "发现时间", "策略", "状态", ""].map((h) => V("th", {}, h)))),
			V("tbody", {}, P.DRIFTS.map((d) => V("tr", {}, [
				V("td", {}, V("code", {}, d.table)),
				V("td", {}, d.kind),
				V("td", { class: "muted small" }, d.detail),
				V("td", {}, d.detected),
				V("td", {}, d.policy),
				V("td", {}, d.state === "待处置"
					? V("span", { class: "chip chip-warn" }, d.state)
					: V("span", { class: "chip chip-ok" }, d.state)),
				V("td", {}, d.state === "待处置"
					? V("div", { class: "row-actions" }, [
							V("button", { class: "link-btn" }, "接受并改表"),
							V("button", { class: "link-btn" }, "忽略"),
						])
					: ""),
			]))),
		])),
	]);
}

function renderStaging() {
	return V("div", {}, [
		V("div", { class: "inline-alert info" }, [
			V("b", {}, "落地前拦截"),
			V("span", {}, "数据先进 staging，规则不通过的行在这里改，确认后才写入 ODS。现网这套能力已存在，只是入口在治理模块。"),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
			V("thead", {}, V("tr", {}, ["行号", "字段", "值", "问题", ""].map((h) => V("th", {}, h)))),
			V("tbody", {}, P.STAGING_ROWS.map((s) => V("tr", { class: s.error ? "bad-row" : "" }, [
				V("td", { class: "num" }, String(s.row)),
				V("td", {}, s.col),
				V("td", {}, V("input", { class: "ctl tight", value: s.value })),
				V("td", { class: "muted small" }, s.error || "✓"),
				V("td", {}, s.error ? V("button", { class: "link-btn" }, "重检此行") : ""),
			]))),
		])),
		V("div", { class: "wizard-foot" }, [
			V("span", { class: "muted" }, "17 行待确认 · 修正后统一重检"),
			V("div", { class: "spacer" }),
			V("button", { class: "btn ghost" }, "丢弃本批"),
			V("button", { class: "btn primary" }, "全部重检并提交"),
		]),
	]);
}

function renderChanges() {
	return V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
		V("thead", {}, V("tr", {}, ["编号", "类型", "摘要", "风险", "状态", "处理人", "时间"].map((h) => V("th", {}, h)))),
		V("tbody", {}, P.CHANGES.map((c) => V("tr", {}, [
			V("td", {}, V("code", {}, c.id)),
			V("td", {}, c.type),
			V("td", {}, c.summary),
			V("td", {}, V("span", { class: c.risk === "高" ? "chip chip-bad" : "chip chip-warn" }, c.risk)),
			V("td", {}, c.state === "待审批"
				? V("span", { class: "chip chip-warn" }, c.state)
				: V("span", { class: "chip chip-ok" }, c.state)),
			V("td", {}, c.assignee),
			V("td", { class: "muted small" }, c.at),
		]))),
	]));
}

function renderOverview(conn) {
	const trend = [92, 88, 96, 94, 0, 97, 99, 95, 93, 98, 91, 96, 0, 94];
	const status = workspacePresentation(conn);
	const stateRows = [
		["生命周期", plainChip(status.lifecycle), "AccessWorkspace 的发布 / 暂停 / 归档迁移", "不表示某次执行是否成功"],
		["总体健康", plainChip(status.health), "漂移、失败、待审批与准入信号的聚合", "不触发生命周期迁移"],
		["连接健康", plainChip(status.connectionHealth), "连接测试与连接失败证据", "连接可用不代表数据可准入"],
		["ODS 准入", plainChip(status.admission), "staging 校验与人工确认结果", "不复用连接健康状态"],
		["上次运行", plainChip(status.lastRun), "最近一条 ExecutionRun 快照", "不是 AccessWorkspace 生命周期"],
		["active Revision", revisionChip("已发布", status.revision.active, status.revision.active ? "info" : "muted"),
			"当前生效的 ExecutionPlan Revision", "只读；继续运行使用这一版"],
		["draft Revision", revisionChip("草稿", status.revision.draft, status.revision.draft ? "warn" : "quiet"),
			"编辑现有计划产生的候选 Revision", "发布前不影响 active Revision"],
	];
	return V("div", { class: "overview" }, [
		V("div", { class: "stat-row" }, [
			["最近一次", conn.lastRun, status.lastRun],
			["写入行数", conn.lastRows.toLocaleString("zh-CN"), null],
			["入湖表", `${conn.tables} 张`, null],
			["下次运行", conn.nextRun, null],
		].map(([k, v, meta]) => V("div", { class: "stat" }, [
			V("span", { class: "muted small" }, k),
			V("b", {}, v),
			meta ? plainChip(meta) : null,
		]))),
		V("section", { class: "panel" }, [
			V("div", { class: "panel-head" }, [
				V("h3", {}, "AccessWorkspace 状态边界"),
				V("span", { class: "panel-note" }, "七个读数来自不同事实，不再压进一个 status"),
			]),
			V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
				V("thead", {}, V("tr", {}, ["维度", "当前值", "事实来源", "边界"].map((h) => V("th", {}, h)))),
				V("tbody", {}, stateRows.map(([dimension, value, source, boundary]) => V("tr", {}, [
					V("td", {}, V("b", {}, dimension)),
					V("td", {}, value),
					V("td", {}, source),
					V("td", { class: "muted small" }, boundary),
				]))),
			])),
		]),
		V("section", { class: "panel" }, [
			V("div", { class: "panel-head" }, [V("h3", {}, "近 14 次运行")]),
			V("div", { class: "spark" }, trend.map((n, i) => V("span", {
				class: `bar${n === 0 ? " fail" : ""}`,
				style: `height:${n === 0 ? 12 : Math.round(n * 0.6)}px`,
				title: n === 0 ? "失败" : `成功 · ${n} 千行`,
			}))),
			V("div", { class: "foot-note" }, "灰红柱为失败批次，点击可直达该批次日志。"),
		]),
		V("section", { class: "panel" }, [
			V("div", { class: "panel-head" }, [V("h3", {}, "仅生命周期")]),
			V("div", { class: "lifecycle" }, ["DRAFT", "ACTIVE", "PAUSED", "ARCHIVED"].map((s) => V("span", {
				class: `lc${s === status.lifecycle.code ? " current" : ""}`,
			}, s))),
			V("div", { class: "foot-note" },
				"ATTENTION 属于总体健康，STAGING 属于 ODS 准入，SUCCESS / FAILED 属于 ExecutionRun；它们都不是生命周期节点。"),
		]),
	]);
}

function renderDetail(nav, id) {
	const conn = P.CONNECTIONS.find((c) => c.id === id) || P.CONNECTIONS[1];
	const connector = P.connectorByKey(conn.connector);
	const status = workspacePresentation(conn);

	const tabs = V("div", { class: "tabs" }, DETAIL_TABS.map((t) => {
		const badge = t.badge ? t.badge(conn) : 0;
		return V("button", {
			class: `tab${DETAIL.tab === t.key ? " active" : ""}`,
			onclick: () => { DETAIL.tab = t.key; nav("detail", id); },
		}, [t.label, badge ? V("span", { class: "tab-badge" }, String(badge)) : null]);
	}));

	const body = DETAIL.tab === "runs" ? renderRuns()
		: DETAIL.tab === "drift" ? renderDrift()
		: DETAIL.tab === "staging" ? renderStaging()
		: DETAIL.tab === "changes" ? renderChanges()
		: DETAIL.tab === "config" ? renderConfigTab(conn, connector)
		: renderOverview(conn);

	return V("div", { class: "page" }, [
		V("div", { class: "crumbs" }, [
			V("button", { class: "link-btn", onclick: () => nav("list") }, "数据接入"),
			V("span", { class: "muted" }, "/"),
			V("span", {}, conn.name),
		]),
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, [conn.name, " ", labeledChip("生命周期", status.lifecycle)]),
				V("p", { class: "muted" },
					`AccessWorkspace ${conn.id} · ${connector?.name} · ${conn.endpoint} · 密级 ${conn.classification} · ${conn.owner}`),
				V("div", { class: "conn-alerts" }, [
					labeledChip("总体健康", status.health),
					labeledChip("连接健康", status.connectionHealth),
					labeledChip("ODS 准入", status.admission),
					labeledChip("上次运行", status.lastRun),
					revisionChip("active Revision", status.revision.active, status.revision.active ? "info" : "muted"),
					revisionChip("draft Revision", status.revision.draft, status.revision.draft ? "warn" : "quiet"),
				]),
			]),
			V("div", { class: "head-actions" }, [
				V("button", { class: "btn primary", onclick: () => openDraftRevision(conn, () => nav("detail", id)) },
					status.revision.draft ? "编辑草稿 Revision" : "编辑计划（新建草稿）"),
				V("button", { class: "btn ghost" }, "立即运行"),
				V("button", { class: "btn ghost" }, "暂停"),
				V("button", { class: "btn ghost" }, "补数"),
			]),
		]),
		tabs,
		V("div", { class: "tab-body" }, body),
	]);
}

const PUBLISHED_CONFIG_VALUES = {
	mysql: { host: "10.20.30.41", port: 3306, database: "hr_prod", username: "dts_reader" },
	postgresql: { host: "10.20.30.88", port: 5432, database: "edu", schema: "public", username: "dts_reader" },
	oracle: { host: "10.20.31.7", port: 1521, serviceName: "FINSVC", username: "dts_reader" },
	http_api: { baseUrl: "https://api.example.gov.cn/v1", authType: "bearer", verifyCert: true },
	file_batch: { file: "资产盘点表-202607.xlsx", sheet: "Sheet1", headerRow: 1, dataStartRow: 2 },
};

function renderConfigTab(conn, connector) {
	const status = workspacePresentation(conn);
	if (!status.revision.active) {
		return V("div", {}, [
			V("div", { class: "inline-alert warn" }, [
				V("b", {}, "尚无已发布 Revision"),
				V("span", {},
					`${status.revision.draft} 是初始草稿；配置页只展示 active Revision，因此当前没有可读配置。`),
			]),
			V("button", { class: "btn primary", onclick: () => openDraftRevision(conn) }, "编辑初始草稿"),
		]);
	}

	const values = window.SchemaForm.defaultsOf(connector.schema);
	Object.assign(values, PUBLISHED_CONFIG_VALUES[connector.key] || {});
	values.__collapsed = Object.fromEntries(
		connector.schema.filter((group) => group.collapsed).map((group) => [group.group, false]),
	);
	const form = window.SchemaForm.renderSchemaForm(connector.schema, values, () => {});
	form.setAttribute("aria-label", `当前已发布 Revision ${status.revision.active} 的只读配置`);
	form.querySelectorAll("input, select, textarea, button").forEach((control) => {
		control.disabled = true;
		control.setAttribute("aria-disabled", "true");
	});

	return V("div", {}, [
		V("div", { class: "inline-alert info" }, [
			V("b", {}, `当前已发布 Revision ${status.revision.active}`),
			V("span", {},
				"配置页只读并继续复用连接器 schema。点击顶部“编辑计划”会复制 active Revision 形成 draft Revision，不会原地覆盖运行中配置。"),
		]),
		status.revision.draft
			? V("div", { class: "inline-alert warn" }, [
					V("b", {}, `另有草稿 ${status.revision.draft}`),
					V("span", {}, "草稿发布前不生效；本页仍忠实展示 active Revision。"),
				])
			: null,
		V("section", { class: "panel" }, [
			V("div", { class: "panel-head" }, [
				V("h3", {}, "已发布连接与读取配置"),
				V("span", { class: "panel-note" }, "只读快照"),
			]),
			form,
		]),
	]);
}

/* ---------------------------------------------------------------- */
/* 三、连接器与驱动（下沉到系统管理）                                   */
/* ---------------------------------------------------------------- */

const ADAPTER_LABELS = {
	ADDAX: "batch.addax",
	API_RUNTIME: "api.runtime",
	FILE: "file.native",
	FUTURE: "cdc.candidate",
};

function renderExecutionBindings(connector) {
	const bindings = connector.executionBindings || [];
	if (!bindings.length) return V("code", {}, ADAPTER_LABELS[connector.engine] || connector.engine);
	return V("div", { class: "binding-list" }, bindings.map((binding) => {
		const tone = binding.status === "READY" ? "ok"
			: binding.status === "BLOCKED_DRIVER" ? "bad" : "warn";
		return V("span", {
			class: `chip chip-${tone}`,
			title: `${binding.mode} · ${binding.status}`,
		}, `${binding.mode} → ${binding.adapter}`);
	}));
}

function renderRuntimeStrategySlot() {
	const slot = V("div", { id: "runtime-strategy-slot" }, V("section", { class: "panel" }, [
		V("div", { class: "panel-head" }, [V("h3", {}, "正在加载运行时与架构策略…")]),
		V("div", { class: "foot-note" }, "该面板内嵌在系统管理页，不注册新的顶级路由。"),
	]));
	const mount = () => {
		if (typeof window.renderRuntimeStrategyPanel !== "function") {
			slot.replaceChildren(V("div", { class: "inline-alert warn" }, [
				V("b", {}, "运行时策略面板未注册"),
				V("span", {}, "runtime-strategy.js 已加载，但没有暴露 window.renderRuntimeStrategyPanel。"),
			]));
			return;
		}
		slot.replaceChildren(window.renderRuntimeStrategyPanel());
	};

	if (typeof window.renderRuntimeStrategyPanel === "function") {
		mount();
		return slot;
	}

	let script = document.querySelector("script[data-runtime-strategy]");
	const showLoadError = () => {
		if (script?.isConnected) script.remove();
		slot.replaceChildren(V("div", { class: "inline-alert warn" }, [
			V("b", {}, "运行时策略面板加载失败"),
			V("span", {}, "请确认 runtime-strategy.js 与 views.js 位于同一目录；再次进入本页会重新加载。"),
		]));
	};
	if (!script) {
		script = document.createElement("script");
		script.src = "./runtime-strategy.js";
		script.dataset.runtimeStrategy = "true";
		script.addEventListener("load", () => {
			script.dataset.loaded = "true";
			mount();
		}, { once: true });
		script.addEventListener("error", showLoadError, { once: true });
		document.head.appendChild(script);
	} else if (script.dataset.loaded === "true") {
		mount();
	} else {
		script.addEventListener("load", mount, { once: true });
		script.addEventListener("error", showLoadError, { once: true });
	}

	return slot;
}

function renderAdmin() {
	const rows = P.CONNECTORS.map((c) => V("tr", {}, [
		V("td", {}, [V("span", { class: "conn-icon small" }, c.icon), " ", c.name]),
		V("td", {}, c.category),
		V("td", {}, renderExecutionBindings(c)),
		V("td", {}, c.driver.status === "READY"
			? V("span", { class: "chip chip-ok" }, c.driver.label)
			: V("span", { class: "chip chip-warn" }, c.driver.label)),
		V("td", {}, V("div", { class: "cap-tags" },
			c.capabilities.map((k) => V("span", { class: "chip chip-quiet" }, P.CAPABILITY_LABELS[k] || k)))),
		V("td", { class: "num" }, String(c.schema.reduce((n, g) => n + g.fields.length, 0))),
		V("td", {}, V("div", { class: "row-actions" }, [
			V("button", { class: "link-btn" }, "查看 schema"),
			c.driver.status !== "READY" ? V("button", { class: "link-btn" }, "上传驱动") : null,
		])),
	]));

	return V("div", { class: "page" }, [
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, "连接器、驱动与运行时策略"),
				V("p", { class: "muted" },
					"这是系统管理内嵌视图：管理共享适配器与执行边界，不创建新的顶级页面，也不替代 AccessWorkspace。"),
			]),
			V("button", { class: "btn ghost" }, "注册连接器"),
		]),
		V("div", { class: "inline-alert info" }, [
			V("b", {}, "状态边界"),
			V("span", {},
				"连接器 / 驱动是共享系统资源；AccessWorkspace 的生命周期、总体健康、连接健康、ODS 准入、上次运行与 Revision 各自保留独立事实源。"),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
			V("thead", {}, V("tr", {}, ["连接器", "类别", "执行适配器", "驱动", "能力", "配置字段数", ""].map((h) => V("th", {}, h)))),
			V("tbody", {}, rows),
		])),
		renderRuntimeStrategySlot(),
	]);
}

/* ---------------------------------------------------------------- */
/* 四、设计说明                                                        */
/* ---------------------------------------------------------------- */

const DECISIONS = [
	{
		n: "01",
		title: "连接器 schema 驱动表单",
		now: "MySQL 等走写死的表单字段；HTTP API 让用户手写 8 个 JSON 文本框（apiQueryJson / apiPaginationJson / apiCursorJson / apiTlsJson …）。后端 8 个 jsonb 列无任何 schema 校验。",
		next: "连接器声明配置契约，前端据此渲染，后端据同一份校验。新增连接器零前端改动。",
		why: "现在的裸 JSON 不是 UI 偷懒，是领域模型缺位——没定义「什么是一份数据库入湖配置」，只好把 Addax 的格式原样递给用户。",
	},
	{
		n: "02",
		title: "任务继承连接，偏离才覆盖",
		now: "readerType、密级、归属部门在数据源表单和任务表单各填一遍。",
		next: "默认显示「继承自连接」并置灰，点「覆盖」才可改，且覆盖会记入变更记录并触发审批。",
		why: "既省录入，也让「这个任务的密级为什么和连接不一样」变成可审计的显式动作——对有密级封条的场景尤其重要。",
	},
	{
		n: "03",
		title: "六个菜单收敛为两个",
		now: "连接器目录 / 数据源管理 / 驱动管理 / 数据源结构采集 / 数据入湖配置 / 接入变更记录，横跨三个顶级模块。",
		next: "「数据接入」用 AccessWorkspace 聚合日常操作与运维证据，连接器、驱动和运行时策略下沉系统管理。",
		why: "AccessWorkspace 是工作入口，不是物理合表：Connection、ExecutionPlan、Revision、ExecutionRun 与 ODSAdmission 仍各自演进。",
	},
	{
		n: "04",
		title: "运行时事件聚合到 AccessWorkspace",
		now: "结构漂移在元数据管理、落地预检在治理、重跑在运维中心。一次失败要跨三个模块处理。",
		next: "运行历史 / 漂移 / 预检 / 变更作为同一 AccessWorkspace 的标签页，但各自保留原领域对象与状态。",
		why: "这些能力后端已经做得不错（SchemaSnapshot、IncrementalState、RetryService、rollback、staging），只是被按团队边界拆散在 UI 上。",
	},
	{
		n: "05",
		title: "状态改为显式状态机",
		now: "status / syncMode / sourceType / lastExecutionStatus 全是自由字符串，无枚举无约束。",
		next: "有限状态机，非法迁移在 service 层拒绝并审计。",
		why: "任何一处 setStatus(「actice」) 拼错都会静默生效，且 DB 里已有的脏值不会被发现。",
	},
	{
		n: "06",
		title: "湖的凭据不进任务表单",
		now: "writerJdbcUrls / writerUsername / writerPassword —— 用户建入湖任务时要手填数据湖的地址、账号和密码，每个任务各存一份。",
		next: "湖是平台级唯一目标，凭据由密钥服务持有，任务侧只读展示。",
		why: "现状下轮换一次湖口令要改 N 个任务，且普通数据工程师必须知道写入账号——与「密钥由密钥服务管理」的设计直接冲突。",
	},
	{
		n: "07",
		title: "扩展 JSON 不得覆盖平台管辖的键",
		now: "mergeConfig 用 {...base, ...extra}，「Reader 扩展配置 JSON」会静默覆盖同屏上方的「Reader 字段」和「Reader 过滤条件」，界面零提示。且 readerConfig 原样成为 Addax reader parameter（AddaxJobService:362），任意键直通插件。",
		next: "Addax 插件参数进 schema「读取性能」分组；仅保留受控逃生口：拒绝平台管辖键、未知键告警、使用后留痕。",
		why: "同屏两个控件写同一个键、下面那个赢，这是 bug 不是取舍。另外 channel 硬编码为 1（AddaxJobService:339），splitPk 全仓只存在于两处 placeholder，后端零引用——用户照提示填了会以为开了并行读。",
	},
	{
		n: "08",
		title: "先分「必须决策」和「有正确默认」，再谈界面",
		now: "数据源表单 22 项 + 任务表单 64 项全部平铺为必填或可填。「运行治理策略」9 项每项都要用户判断，而它们全都有可辩护的默认值。",
		next: "只把无默认可言的项留在主流程（连接、选表、同步方式、调度、名称），其余压成一行摘要 chips，点「查看并调整」才展开。",
		why: "减负不是砍功能，是把决策权从「必填」降级为「可改」。做原型时我自己也踩了这个坑——第③步一度有 18 个表单项，重构后 3 个。",
	},
];

function renderNotes() {
	return V("div", { class: "page narrow" }, [
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, "设计说明"),
				V("p", { class: "muted" }, "本原型要论证的五个决策，以及它们各自对应现网的哪一处具体问题。"),
			]),
		]),
		V("div", { class: "flow-compare" }, [
			V("div", { class: "flow-col now" }, [
				V("h4", {}, "现在"),
				V("ol", {}, [
					"连接器目录 —— 确认有没有这个连接器",
					"驱动管理 —— 上传/确认 JDBC 驱动",
					"数据源管理 —— 新建数据源，填连接参数",
					"数据源结构采集 —— 触发一次元数据采集",
					"数据入湖配置 —— 新建任务，重填 readerType / 密级 / 部门，手写 Reader JSON",
					"接入变更记录 —— 提交审批",
				].map((s) => V("li", {}, s))),
				V("div", { class: "flow-tally" }, "3 个顶级模块 · 6 个页面"),
			]),
			V("div", { class: "flow-arrow" }, "→"),
			V("div", { class: "flow-col next" }, [
				V("h4", {}, "目标"),
				V("ol", {}, [
					"数据接入 → 新建接入",
					"① 连接：选连接器（含驱动状态）→ schema 表单 → 测通",
					"② 选表：自动发现 → 勾选 → 自动推导 ODS 与字段映射",
					"③ 策略：同步方式 / 调度 / 密级（继承）→ 确认启用",
				].map((s) => V("li", {}, s))),
				V("div", { class: "flow-tally" }, "1 个模块 · 1 个向导"),
			]),
		]),
		V("div", { class: "decisions" }, DECISIONS.map((d) => V("section", { class: "decision" }, [
			V("div", { class: "decision-n" }, d.n),
			V("div", { class: "decision-body" }, [
				V("h3", {}, d.title),
				V("div", { class: "dd" }, [V("span", { class: "dd-k now" }, "现在"), V("span", {}, d.now)]),
				V("div", { class: "dd" }, [V("span", { class: "dd-k next" }, "目标"), V("span", {}, d.next)]),
				V("div", { class: "dd" }, [V("span", { class: "dd-k why" }, "理由"), V("span", {}, d.why)]),
			]),
		]))),
		V("section", { class: "panel" }, [
			V("div", { class: "panel-head" }, [V("h3", {}, "本原型未处理的问题")]),
			V("ul", { class: "todo-list" }, [
				"服务边界：dts-platform 与 dts-ingestion 互相调用（40 个透传方法 + 反向取数据源），是循环依赖。这是后端拓扑问题，改不改都不影响本原型的交互，但必须单独立项。",
				"文件体积：AddaxJobService 3284 行、IngestionTaskResource 2903 行、IngestionTaskService 2865 行。落地 schema 驱动会大量触碰这几个文件，建议先拆再改。",
				"存量迁移：现有任务的 8 个 jsonb 列如何映射到 schema 字段，需要一份逐连接器的迁移表和回退方案。",
				"权限模型：本原型未体现按部门/密级的接入可见性，实际需要接 ABAC。",
			].map((t) => V("li", {}, t))),
		]),
	]);
}


/* ---------------------------------------------------------------- */
/* 五、参数归属                                                        */
/* ---------------------------------------------------------------- */

const PM = { filter: "", view: "fields" };


/** 屏幕对照：现网某一屏 → 原型落点，逐字段 */
function renderScreenMaps() {
	const { SCREEN_MAPS } = window.PARAM_MAP;

	return V("div", { class: "screen-maps" }, SCREEN_MAPS.map((sm) => V("section", { class: "panel" }, [
		V("div", { class: "panel-head" }, [
			V("h3", {}, sm.screen),
			V("span", { class: "panel-note" }, sm.path),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid tight" }, [
			V("thead", {}, V("tr", {}, [
				V("th", { style: "width:150px" }, "现网字段"),
				V("th", { style: "width:190px" }, "界面呈现"),
				V("th", {}, "实际行为（已核实）"),
				V("th", { style: "width:250px" }, "原型落点"),
			])),
			V("tbody", {}, sm.rows.map((r) => V("tr", { class: r.tone === "bad" ? "bad-row" : "" }, [
				V("td", {}, [
					V("b", {}, r.label),
					r.note ? V("div", { class: "muted small" }, r.note) : null,
				]),
				V("td", { class: "muted small" }, r.now),
				V("td", { class: "small" }, r.behaviour),
				V("td", {}, V("span", { class: `chip chip-${r.tone}` }, r.to)),
			]))),
		])),
	])));
}

function renderParamMap(nav) {
	const { PARAMS, WHERE_META } = window.PARAM_MAP;
	const counts = {};
	PARAMS.forEach((p) => { counts[p.where] = (counts[p.where] || 0) + 1; });

	const shown = PM.filter ? PARAMS.filter((p) => p.where === PM.filter) : PARAMS;

	const legend = V("div", { class: "legend" }, [
		V("button", {
			class: `legend-item${PM.filter === "" ? " active" : ""}`,
			onclick: () => { PM.filter = ""; nav("params"); },
		}, [V("b", {}, String(PARAMS.length)), V("span", {}, "全部字段")]),
		...Object.entries(WHERE_META).map(([key, meta]) => V("button", {
			class: `legend-item tone-${meta.tone}${PM.filter === key ? " active" : ""}`,
			onclick: () => { PM.filter = PM.filter === key ? "" : key; nav("params"); },
		}, [
			V("b", {}, String(counts[key] || 0)),
			V("span", {}, meta.label),
			V("i", {}, meta.desc),
		])),
	]);

	const rows = shown.map((p) => {
		const meta = WHERE_META[p.where];
		return V("tr", { class: p.risk ? "bad-row" : "" }, [
			V("td", {}, V("code", {}, p.f)),
			V("td", {}, p.zh),
			V("td", {}, V("div", { class: "src-tags" }, [
				p.ds ? V("span", { class: "chip chip-quiet" }, "数据源表单") : null,
				!p.ds ? V("span", { class: "chip chip-quiet" }, "任务表单") : null,
				p.dup ? V("span", { class: "chip chip-warn" }, "两边重复") : null,
				p.json ? V("span", { class: "chip chip-bad" }, "裸 JSON") : null,
			])),
			V("td", {}, V("span", { class: `chip chip-${meta.tone}` }, meta.label)),
			V("td", { class: "muted small" }, p.note || "—"),
		]);
	});

	return V("div", { class: "page" }, [
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, "参数归属"),
				V("p", { class: "muted" },
					"现网数据源表单 22 个字段 + 入湖任务表单 64 个字段，逐个归位。点击下方分类可筛选。"),
			]),
		]),
		V("div", { class: "seg" }, [
			V("button", {
				class: `seg-item${PM.view !== "screens" ? " active" : ""}`,
				onclick: () => { PM.view = "fields"; nav("params"); },
			}, "按字段"),
			V("button", {
				class: `seg-item${PM.view === "screens" ? " active" : ""}`,
				onclick: () => { PM.view = "screens"; nav("params"); },
			}, "按现网屏幕对照"),
		]),
		PM.view === "screens" ? renderScreenMaps() : null,
		PM.view === "screens" ? null : legend,
		PM.view === "screens" ? null : V("div", { class: "inline-alert warn" }, [
			V("b", {}, "最需要先处理的一组"),
			V("span", {}, "writerJdbcUrls / writerUsername / writerPassword —— 用户建入湖任务时要手填数据湖的地址、账号和密码，且每个任务各存一份。湖是唯一目标，这三项根本不该出现在任务表单里。"),
		]),
		PM.view === "screens" ? null : V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
			V("thead", {}, V("tr", {}, ["字段", "含义", "现状", "归属", "说明"].map((h) => V("th", {}, h)))),
			V("tbody", {}, rows),
		])),
	]);
}

window.Views = { renderList, renderDetail, renderAdmin, renderNotes, renderParamMap, DETAIL };

})();
