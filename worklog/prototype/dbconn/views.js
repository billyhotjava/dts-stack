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

/* ---------------------------------------------------------------- */
/* 一、数据接入（列表）                                                */
/* ---------------------------------------------------------------- */

function renderList(nav) {
	const cards = P.CONNECTIONS.map((c) => {
		const connector = P.connectorByKey(c.connector);
		const alerts = [];
		if (c.health.drift) alerts.push(`${c.health.drift} 处结构漂移`);
		if (c.health.failed7d) alerts.push(`7 日内 ${c.health.failed7d} 次失败`);
		if (c.health.pendingChange) alerts.push(`${c.health.pendingChange} 项变更待审批`);
		if (c.health.stagingErrors) alerts.push(`${c.health.stagingErrors} 行待人工确认`);

		return V("article", {
			class: `conn-card tone-${P.STATE_META[c.state]?.tone || "muted"}`,
			onclick: () => nav("detail", c.id),
		}, [
			V("div", { class: "conn-top" }, [
				V("span", { class: "conn-icon" }, connector?.icon || "🔌"),
				V("div", { class: "conn-title" }, [
					V("h4", {}, c.name),
					V("span", { class: "muted small" }, `${connector?.name || c.connector} · ${c.endpoint}`),
				]),
				stateChip(c.state),
			]),
			V("div", { class: "conn-metrics" }, [
				["入湖表", `${c.tables}`],
				["同步", c.syncMode === "incremental" ? "增量" : "全量"],
				["调度", c.schedule],
				["上次", c.lastRun],
			].map(([k, v]) => V("div", {}, [
				V("span", { class: "muted small" }, k),
				V("b", {}, v),
			]))),
			alerts.length
				? V("div", { class: "conn-alerts" }, alerts.map((a) => V("span", { class: "chip chip-warn" }, a)))
				: V("div", { class: "conn-alerts" }, V("span", { class: "chip chip-quiet" }, "无待处理事项")),
			V("div", { class: "conn-foot" }, [
				V("span", { class: "muted small" }, `密级 ${c.classification} · ${c.owner}`),
				V("span", { class: "muted small" }, `下次 ${c.nextRun}`),
			]),
		]);
	});

	return V("div", { class: "page" }, [
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, "数据接入"),
				V("p", { class: "muted" }, "一条接入 = 一个连接 + 它的入湖任务。用户不再分别维护两个对象。"),
			]),
			V("button", { class: "btn primary", onclick: () => nav("wizard") }, "+ 新建接入"),
		]),
		V("div", { class: "filters" }, [
			V("input", { class: "ctl", placeholder: "搜索接入名称、主机、库名…" }),
			V("select", { class: "ctl narrow" }, [V("option", {}, "全部状态"), V("option", {}, "需要关注"), V("option", {}, "已暂停")]),
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
	return V("div", { class: "overview" }, [
		V("div", { class: "stat-row" }, [
			["最近一次", conn.lastRun, conn.lastRunState],
			["写入行数", conn.lastRows.toLocaleString("zh-CN"), null],
			["入湖表", `${conn.tables} 张`, null],
			["下次运行", conn.nextRun, null],
		].map(([k, v, s]) => V("div", { class: "stat" }, [
			V("span", { class: "muted small" }, k),
			V("b", {}, v),
			s ? stateChip(s) : null,
		]))),
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
			V("div", { class: "panel-head" }, [V("h3", {}, "生命周期")]),
			V("div", { class: "lifecycle" }, ["DRAFT", "READY", "RUNNING", "PAUSED", "ARCHIVED"].map((s) => V("span", {
				class: `lc${s === "RUNNING" ? " current" : ""}`,
			}, s))),
			V("div", { class: "foot-note" },
				"状态迁移由显式状态机约束（RUNNING 只能到 PAUSED / ATTENTION）。现网这里是自由字符串，拼错也会静默生效。"),
		]),
	]);
}

function renderDetail(nav, id) {
	const conn = P.CONNECTIONS.find((c) => c.id === id) || P.CONNECTIONS[1];
	const connector = P.connectorByKey(conn.connector);

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
		: DETAIL.tab === "config" ? renderConfigTab(connector)
		: renderOverview(conn);

	return V("div", { class: "page" }, [
		V("div", { class: "crumbs" }, [
			V("button", { class: "link-btn", onclick: () => nav("list") }, "数据接入"),
			V("span", { class: "muted" }, "/"),
			V("span", {}, conn.name),
		]),
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, [conn.name, " ", stateChip(conn.state)]),
				V("p", { class: "muted" }, `${connector?.name} · ${conn.endpoint} · 密级 ${conn.classification} · ${conn.owner}`),
			]),
			V("div", { class: "head-actions" }, [
				V("button", { class: "btn ghost" }, "立即运行"),
				V("button", { class: "btn ghost" }, "暂停"),
				V("button", { class: "btn ghost" }, "补数"),
			]),
		]),
		tabs,
		V("div", { class: "tab-body" }, body),
	]);
}

function renderConfigTab(connector) {
	const values = window.SchemaForm.defaultsOf(connector.schema);
	Object.assign(values, { host: "10.20.31.7", port: 1521, serviceName: "FINSVC", username: "dts_reader" });
	return V("div", {}, [
		V("div", { class: "inline-alert info" }, [
			V("b", {}, "同一份 schema"),
			V("span", {}, "编辑态复用向导第一步的渲染器，不存在「新建表单」和「编辑表单」两套代码。"),
		]),
		window.SchemaForm.renderSchemaForm(connector.schema, values, () => {}),
	]);
}

/* ---------------------------------------------------------------- */
/* 三、连接器与驱动（下沉到系统管理）                                   */
/* ---------------------------------------------------------------- */

function renderAdmin() {
	const rows = P.CONNECTORS.map((c) => V("tr", {}, [
		V("td", {}, [V("span", { class: "conn-icon small" }, c.icon), " ", c.name]),
		V("td", {}, c.category),
		V("td", {}, V("code", {}, c.engine)),
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
				V("h2", {}, "连接器与驱动"),
				V("p", { class: "muted" }, "管理员一年动几次的东西，应该在系统管理里，不该和日常接入平级。"),
			]),
			V("button", { class: "btn ghost" }, "注册连接器"),
		]),
		V("div", { class: "inline-alert info" }, [
			V("b", {}, "关键差异"),
			V("span", {}, "「配置字段数」这一列来自连接器自己声明的 schema。前端没有为任何一个连接器写过专门的表单代码。"),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
			V("thead", {}, V("tr", {}, ["连接器", "类别", "执行引擎", "驱动", "能力", "配置字段数", ""].map((h) => V("th", {}, h)))),
			V("tbody", {}, rows),
		])),
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
		next: "「数据接入」（日常）+「接入运维」（生命周期），连接器与驱动下沉系统管理。",
		why: "菜单现在反映的是我们的模块划分，不是用户的任务流。用户心智里只有一个对象：「一条接入」。",
	},
	{
		n: "04",
		title: "运行时事件聚合到接入对象",
		now: "结构漂移在元数据管理、落地预检在治理、重跑在运维中心。一次失败要跨三个模块处理。",
		next: "运行历史 / 漂移 / 预检 / 变更，作为同一个接入的四个标签页。",
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

const PM = { filter: "" };

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
		legend,
		V("div", { class: "inline-alert warn" }, [
			V("b", {}, "最需要先处理的一组"),
			V("span", {}, "writerJdbcUrls / writerUsername / writerPassword —— 用户建入湖任务时要手填数据湖的地址、账号和密码，且每个任务各存一份。湖是唯一目标，这三项根本不该出现在任务表单里。"),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid" }, [
			V("thead", {}, V("tr", {}, ["字段", "含义", "现状", "归属", "说明"].map((h) => V("th", {}, h)))),
			V("tbody", {}, rows),
		])),
	]);
}

window.Views = { renderList, renderDetail, renderAdmin, renderNotes, renderParamMap, DETAIL };

})();
