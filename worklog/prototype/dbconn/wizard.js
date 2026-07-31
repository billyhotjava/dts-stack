/**
 * 新建接入向导 —— 三步：连接 → 选表 → 策略。
 *
 * 对照现网：用户要先去「连接器目录」看有没有连接器，再去「驱动管理」传驱动，
 * 再去「数据源管理」建源，再去「数据源结构采集」采结构，最后到「数据入湖配置」
 * 建任务，中途还要手写 Reader JSON。这里合并为一条流。
 */

(function () {

const { el, renderSchemaForm, defaultsOf } = window.SchemaForm;

const WIZ = {
	step: 0,
	connectorKey: null,
	values: {},
	tested: null, // null | 'testing' | {ok, ms, msg}
	tables: null, // 深拷贝的发现结果
	discovering: false,
	selectMode: "manual",
	pattern: "hr_*",
	policy: {},
	activeTable: null,
	tableAdvOpen: null,
	showJob: false,
};

const STEPS = [
	{ key: "connect", title: "连接", desc: "选连接器、填参数、测通" },
	{ key: "tables", title: "选表", desc: "自动发现、推导 ODS" },
	{ key: "policy", title: "策略", desc: "同步方式、调度、密级" },
];

function resetWizard() {
	WIZ.step = 0;
	WIZ.connectorKey = null;
	WIZ.values = {};
	WIZ.tested = null;
	WIZ.tables = null;
	WIZ.discovering = false;
	WIZ.selectMode = "manual";
	WIZ.policy = {};
	WIZ.activeTable = null;
	WIZ.tableAdvOpen = null;
	WIZ.showJob = false;
}

/* ---------------------------------------------------------------- */
/* 步骤条                                                            */
/* ---------------------------------------------------------------- */

function renderStepper() {
	return el("div", { class: "stepper" }, STEPS.map((s, i) => el("div", {
		class: `step${i === WIZ.step ? " active" : ""}${i < WIZ.step ? " done" : ""}`,
	}, [
		el("span", { class: "step-no" }, i < WIZ.step ? "✓" : String(i + 1)),
		el("span", { class: "step-text" }, [
			el("b", {}, s.title),
			el("i", {}, s.desc),
		]),
	])));
}

/* ---------------------------------------------------------------- */
/* 步骤一：连接                                                       */
/* ---------------------------------------------------------------- */

function renderConnectorPicker(rerender) {
	const grid = el("div", { class: "connector-grid" });

	window.PROTO.CONNECTORS.forEach((c) => {
		const selected = WIZ.connectorKey === c.key;
		const driverBad = c.driver.status !== "READY";

		grid.appendChild(el("button", {
			type: "button",
			class: `connector-card${selected ? " selected" : ""}${c.disabled ? " disabled" : ""}`,
			disabled: c.disabled,
			onclick: () => {
				if (c.disabled) return;
				WIZ.connectorKey = c.key;
				WIZ.values = defaultsOf(c.schema);
				WIZ.tested = null;
				WIZ.tables = null;
				rerender();
			},
		}, [
			el("span", { class: "connector-icon" }, c.icon),
			el("span", { class: "connector-name" }, c.name),
			el("span", { class: `driver-chip ${c.driver.status.toLowerCase()}` },
				c.driver.status === "READY" ? "驱动就绪"
					: c.driver.status === "MISSING" ? "缺驱动"
					: "规划中"),
			driverBad && !c.disabled
				? el("span", { class: "connector-fix" }, "可在本步骤内上传")
				: null,
		]));
	});

	return el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "选择连接器"),
			el("span", { class: "panel-note" }, "连接器与驱动是选择器，不再是两个独立菜单"),
		]),
		grid,
	]);
}

function renderTestBlock(rerender) {
	const t = WIZ.tested;
	const run = () => {
		WIZ.tested = "testing";
		rerender();
		setTimeout(() => {
			WIZ.tested = { ok: true, ms: 412, msg: "握手成功，账号具备 SELECT 与元数据读取权限" };
			rerender();
		}, 700);
	};

	return el("div", { class: "test-block" }, [
		el("button", {
			class: "btn primary", type: "button",
			disabled: t === "testing", onclick: run,
		}, t === "testing" ? "测试中…" : "测试连接"),
		t === "testing" ? el("span", { class: "muted" }, "正在建立连接…") : null,
		t && t !== "testing"
			? el("div", { class: `test-result ${t.ok ? "ok" : "bad"}` }, [
					el("b", {}, t.ok ? "✓ 连接成功" : "✗ 连接失败"),
					el("span", {}, `${t.msg} · ${t.ms}ms`),
				])
			: null,
		!t ? el("span", { class: "muted" }, "测通后才能进入下一步") : null,
	]);
}

function renderStepConnect(rerender) {
	const connector = window.PROTO.connectorByKey(WIZ.connectorKey);
	const box = el("div", { class: "step-body" }, [renderConnectorPicker(rerender)]);

	if (!connector) {
		box.appendChild(el("div", { class: "empty" }, "请先选择一个连接器"));
		return box;
	}

	const head = el("div", { class: "panel-head" }, [
		el("h3", {}, `${connector.name} 连接参数`),
		el("span", { class: "panel-note" },
			`表单由连接器 schema 渲染 · ${connector.schema.reduce((n, g) => n + g.fields.length, 0)} 个字段`),
	]);

	const panel = el("section", { class: "panel" }, [head]);

	if (connector.driver.status === "MISSING") {
		panel.appendChild(el("div", { class: "inline-alert warn" }, [
			el("b", {}, "缺少 JDBC 驱动"),
			el("span", {}, "达梦驱动包需由管理员上传，不必离开当前流程。"),
			el("button", { class: "btn small", type: "button" }, "上传驱动"),
		]));
	}

	if (connector.replaces) {
		panel.appendChild(el("div", { class: "inline-alert info" }, [
			el("b", {}, "对照现网"),
			el("span", {}, `以下分组替代了当前版本的 ${connector.replaces.length} 个裸 JSON 文本框：${connector.replaces.join("、")}`),
		]));
	}

	panel.appendChild(renderSchemaForm(connector.schema, WIZ.values, rerender));
	panel.appendChild(renderTestBlock(rerender));
	box.appendChild(panel);
	return box;
}

/* ---------------------------------------------------------------- */
/* 步骤二：选表                                                       */
/* ---------------------------------------------------------------- */

function ensureTables() {
	if (!WIZ.tables) {
		WIZ.tables = window.PROTO.DISCOVERED_TABLES.map((t) => ({ ...t }));
		WIZ.activeTable = WIZ.tables[0].name;
	}
	return WIZ.tables;
}

let rerenderHook = () => {};

const odsName = (t) => `ods_hr_${t.name.replace(/^hr_/, "")}`;


/** 发现范围 —— 对应现网「数据源」区的 Schema / 表名筛选 / 排除表 */
function renderDiscoveryScope() {
	const connector = window.PROTO.connectorByKey(WIZ.connectorKey);
	const schemaFromConn = WIZ.values.schema || WIZ.values.database || "hr_prod";

	return el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "发现范围"),
			el("span", { class: "panel-note" }, "决定去源端扫哪些表；留空即全部"),
		]),
		el("div", { class: "form-grid" }, [
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "Schema"),
				el("div", { class: "inherited" }, [
					el("span", {}, schemaFromConn),
					el("span", { class: "chip chip-inherit" }, "继承自连接"),
				]),
				el("button", { class: "link-btn", type: "button" }, "改扫其它 Schema"),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "表名筛选"),
				el("input", { class: "ctl", placeholder: "SQL LIKE，如 hr_%（可留空）" }),
				el("div", { class: "form-hint" }, "在源端下推，减少元数据扫描量"),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "排除表"),
				el("input", { class: "ctl", placeholder: "换行或逗号分隔，如 tmp_%, audit_log" }),
				el("div", { class: "form-hint" }, "默认已排除临时表与备份表" ),
			]),
		]),
		el("div", { class: "foot-note" },
			`Reader 类型由「${connector ? connector.name : "所选连接器"}」自动决定，不需要用户选择——现网这里是一个禁用输入框，占了一个格子却不承载任何决策。`),
	]);
}

function renderTableList(rerender) {
	const tables = ensureTables();
	const rows = tables.map((t) => el("tr", {
		class: `${t.selected ? "picked" : ""}${WIZ.activeTable === t.name ? " active" : ""}`,
		onclick: () => { WIZ.activeTable = t.name; rerender(); },
	}, [
		el("td", { class: "cell-check" }, el("input", {
			type: "checkbox", checked: t.selected,
			onclick: (e) => { e.stopPropagation(); t.selected = e.target.checked; rerender(); },
		})),
		el("td", {}, [
			el("div", { class: "t-name" }, t.name),
			el("div", { class: "t-comment" }, t.comment),
		]),
		el("td", { class: "num" }, t.rows.toLocaleString("zh-CN")),
		el("td", { class: "num" }, t.size),
		el("td", {}, t.pk ? el("code", {}, t.pk) : el("span", { class: "muted" }, "无")),
		el("td", {}, t.incrementalCol
			? el("span", { class: "chip chip-ok" }, t.incrementalCol)
			: el("span", { class: "chip" }, "仅全量")),
		el("td", {}, t.warn ? el("span", { class: "chip chip-warn", title: t.warn }, "注意") : ""),
	]));

	const picked = tables.filter((t) => t.selected).length;

	return el("section", { class: "panel flush" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "发现的表"),
			el("span", { class: "panel-note" }, `共 ${tables.length} 张，已选 ${picked} 张`),
		]),
		el("div", { class: "select-mode" }, [
			el("label", {}, [
				el("input", {
					type: "radio", name: "smode", checked: WIZ.selectMode === "manual",
					onchange: () => { WIZ.selectMode = "manual"; rerender(); },
				}), " 手动勾选",
			]),
			el("label", {}, [
				el("input", {
					type: "radio", name: "smode", checked: WIZ.selectMode === "pattern",
					onchange: () => { WIZ.selectMode = "pattern"; rerender(); },
				}), " 按规则匹配",
			]),
			WIZ.selectMode === "pattern"
				? el("input", {
						class: "ctl inline", value: WIZ.pattern,
						oninput: (e) => { WIZ.pattern = e.target.value; },
					})
				: null,
			WIZ.selectMode === "pattern"
				? el("span", { class: "muted" }, "规则在每次运行时重新求值，源端新增表会自动纳入")
				: null,
		]),
		el("div", { class: "table-scroll" },
			el("table", { class: "grid" }, [
				el("thead", {}, el("tr", {}, [
					el("th", { class: "cell-check" }, ""),
					el("th", {}, "源表"), el("th", { class: "num" }, "行数"),
					el("th", { class: "num" }, "大小"), el("th", {}, "主键"),
					el("th", {}, "可用增量列"), el("th", {}, ""),
				])),
				el("tbody", {}, rows),
			])),
	]);
}


/** 逐表高级设置 —— 对应现网 readerColumns / readerWhere / readerQuerySql / incrementalColumn / initialWatermark */
function renderTableAdvanced(t) {
	const open = WIZ.tableAdvOpen === t.name;
	const head = el("div", {
		class: "form-group-head", style: "cursor:pointer;margin-top:10px",
		onclick: () => { WIZ.tableAdvOpen = open ? null : t.name; rerenderHook(); },
	}, [
		el("span", { class: "form-group-title" }, "本表高级设置"),
		el("span", { class: "caret" }, open ? "收起 ▴" : "展开 ▾"),
		el("span", { class: "muted", style: "font-size:11.5px" }, "默认全列、无过滤，多数表无需调整"),
	]);

	if (!open) return el("div", {}, head);

	return el("div", {}, [head, el("div", { class: "form-grid" }, [
		el("div", { class: "form-item span-3" }, [
			el("label", { class: "form-label" }, "列裁剪"),
			el("div", { class: "inherited" }, [
				el("span", {}, `全部 ${t.cols} 列`),
				el("span", { class: "chip chip-inherit" }, "默认"),
			]),
			el("button", { class: "link-btn", type: "button" }, "改为按需选择"),
		]),
		el("div", { class: "form-item span-2" }, [
			el("label", { class: "form-label" }, "过滤条件"),
			el("input", { class: "ctl", placeholder: "如 status <> 'DELETED'（可留空）" }),
			el("div", { class: "form-hint" }, "拼进 WHERE，不支持子查询"),
		]),
		el("div", { class: "form-item" }, [
			el("label", { class: "form-label" }, "增量列"),
			el("select", { class: "ctl" }, [
				t.incrementalCol ? el("option", { selected: true }, `${t.incrementalCol}（自动识别）`) : null,
				el("option", {}, "不做增量，每次全量"),
			].filter(Boolean)),
		]),
		el("div", { class: "form-item" }, [
			el("label", { class: "form-label" }, "增量类型"),
			el("select", { class: "ctl" }, [el("option", {}, "时间戳"), el("option", {}, "自增主键")]),
		]),
		el("div", { class: "form-item" }, [
			el("label", { class: "form-label" }, "初始水位"),
			el("input", { class: "ctl", value: "2026-01-01 00:00:00" }),
			el("div", { class: "form-hint" }, "首轮从此刻开始拉"),
		]),
		el("div", { class: "form-item" }, [
			el("label", { class: "form-label" }, "分片键 splitPk"),
			el("div", { class: "inherited" }, [
				el("span", {}, t.pk || "无主键，不可分片"),
				el("span", { class: "chip chip-inherit" }, t.pk ? "取自主键" : "不可用"),
			]),
			el("div", { class: "form-hint" }, "并发通道 > 1 时生效；现网需手写 {\"splitPk\":\"id\"}，且因 channel 恒为 1 从不生效"),
		]),
		el("div", { class: "form-item" }, [
			el("label", { class: "form-label" }, "目标表名"),
			el("input", { class: "ctl", value: odsName(t) }),
			el("div", { class: "form-hint" }, "按规范推导，可覆盖"),
		]),
		el("div", { class: "form-item span-3" }, [
			el("label", { class: "form-label" }, "自定义查询 SQL"),
			el("input", { class: "ctl", placeholder: "留空则由上述条件生成；填写后与列裁剪/过滤互斥" }),
			el("div", { class: "form-hint warn-text" }, "逃生口。使用后无法自动推导字段映射与血缘，需人工登记"),
		]),
	])]);
}

function renderOdsPreview() {
	const tables = ensureTables();
	const t = tables.find((x) => x.name === WIZ.activeTable) || tables[0];

	const colRows = window.PROTO.SAMPLE_COLUMNS.map((c) => el("tr", {}, [
		el("td", {}, el("code", {}, c.src)),
		el("td", { class: "muted" }, c.srcType),
		el("td", {}, el("code", {}, c.target)),
		el("td", { class: "muted" }, c.targetType),
		el("td", {}, [
			c.role ? el("span", { class: "chip chip-ok" }, c.role) : null,
			c.masked ? el("span", { class: "chip chip-warn", title: "命中敏感识别规则，落地自动脱敏" }, "脱敏") : null,
		]),
		el("td", { class: "muted small" }, c.std || "—"),
	]));

	return el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "目标表推导"),
			el("span", { class: "panel-note" }, "系统按命名规范生成，可逐项覆盖"),
		]),
		el("div", { class: "kvline" }, [
			el("span", {}, "源表"), el("code", {}, t.name),
			el("span", { class: "arrow" }, "→"),
			el("span", {}, "ODS 表"), el("code", { class: "strong" }, odsName(t)),
		]),
		el("div", { class: "kvline" }, [
			el("span", {}, "写入策略"),
			el("code", {}, t.incrementalCol ? `按 ${t.incrementalCol} 时间戳增量 + 主键去重` : "全量覆盖"),
		]),
		t.warn ? el("div", { class: "inline-alert warn" }, [el("b", {}, "提示"), el("span", {}, t.warn)]) : null,
		renderTableAdvanced(t),
		el("div", { class: "sub-title" }, "字段映射"),
		el("div", { class: "table-scroll short" },
			el("table", { class: "grid tight" }, [
				el("thead", {}, el("tr", {}, [
					el("th", {}, "源字段"), el("th", {}, "源类型"),
					el("th", {}, "目标字段"), el("th", {}, "目标类型"),
					el("th", {}, "角色"), el("th", {}, "关联数据元"),
				])),
				el("tbody", {}, colRows),
			])),
		el("div", { class: "foot-note" },
			"技术列 dts_load_time / dts_batch_id / dts_src_hash 由平台自动追加，不在此处维护。"),
	]);
}

function renderStepTables(rerender) {
	if (WIZ.discovering) {
		return el("div", { class: "step-body" }, el("div", { class: "empty" }, "正在读取源端结构…"));
	}
	rerenderHook = rerender;
	return el("div", {}, [
		renderDiscoveryScope(),
		el("div", { class: "step-body split" }, [
			renderTableList(rerender),
			renderOdsPreview(),
		]),
	]);
}

/* ---------------------------------------------------------------- */
/* 步骤三：策略                                                       */
/* ---------------------------------------------------------------- */

/** 继承自数据源、可显式覆盖的字段 —— 解决"同一字段填两遍" */
function inheritedField(label, inheritedValue, key, rerender, options) {
	const overridden = WIZ.policy[key] !== undefined;
	const control = overridden
		? (options
				? el("select", {
						class: "ctl", onchange: (e) => { WIZ.policy[key] = e.target.value; rerender(); },
					}, options.map((o) => el("option", { value: o, selected: o === WIZ.policy[key] }, o)))
				: el("input", {
						class: "ctl", value: WIZ.policy[key],
						oninput: (e) => { WIZ.policy[key] = e.target.value; },
					}))
		: el("div", { class: "inherited" }, [
				el("span", {}, inheritedValue),
				el("span", { class: "chip chip-inherit" }, "继承自连接"),
			]);

	return el("div", { class: "form-item" }, [
		el("label", { class: "form-label" }, label),
		control,
		el("button", {
			class: "link-btn", type: "button",
			onclick: () => {
				if (overridden) delete WIZ.policy[key];
				else WIZ.policy[key] = inheritedValue;
				rerender();
			},
		}, overridden ? "恢复继承" : "覆盖"),
		overridden
			? el("div", { class: "form-hint warn-text" }, "偏离连接默认值，将记入接入变更记录并触发审批")
			: null,
	]);
}

function renderGeneratedJob() {
	const job = {
		job: {
			setting: { speed: { channel: 3 }, errorLimit: { record: 0 } },
			content: [{
				reader: {
					name: "mysqlreader",
					parameter: {
						column: ["*"],
						connection: [{ table: ["hr_employee"], jdbcUrl: ["jdbc:mysql://10.20.30.41:3306/hr_prod"] }],
						where: "gmt_modified > '${last_watermark}'",
						username: "${secret:ing-1042/username}",
						password: "${secret:ing-1042/password}",
					},
				},
				writer: {
					name: "inceptorwriter",
					parameter: { table: ["ods_hr_employee"], writeMode: "upsert", primaryKey: ["emp_id"] },
				},
			}],
		},
	};
	return el("pre", { class: "code-block" }, JSON.stringify(job, null, 2));
}


/** 目标端：现网让用户手填湖的 JDBC URL / 账号 / 密码，这里改为平台托管只读 */
function renderTargetPanel() {
	return el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "目标端"),
			el("span", { class: "panel-note" }, "平台托管，任务不持有湖的凭据"),
		]),
		el("div", { class: "inline-alert warn" }, [
			el("b", {}, "现网风险"),
			el("span", {}, "当前 writerJdbcUrls / writerUsername / writerPassword 由用户在任务表单里手填，每个任务各存一份湖的账号密码。轮换一次要改 N 个任务，且普通数据工程师需要知道写入口令。"),
		]),
		el("div", { class: "form-grid" }, [
			["数据湖", "Inceptor 集群 dts-lake-prod"],
			["写入库", "ods"],
			["写入账号", "svc_dts_writer（密钥服务持有）"],
			["表名规范", "ods_<源系统>_<源表名>"],
		].map(([k, v]) => el("div", { class: "form-item" }, [
			el("label", { class: "form-label" }, k),
			el("div", { class: "inherited" }, [
				el("span", {}, v),
				el("span", { class: "chip chip-inherit" }, "平台配置"),
			]),
		]))),
		el("div", { class: "foot-note" },
			"写入模式（覆盖 / 追加 / upsert）由同步方式推导；写入前后 SQL 属高级项，需单独审批后才出现。"),
	]);
}

/** 运行时：调度方式、窗口、容错、并发配额、优先级 */
function renderRuntimePanel() {
	const p = WIZ.policy;
	const st = p.scheduleType || "cron";

	const scheduleFields = st === "cron"
		? [el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "Cron 表达式"),
				el("input", { class: "ctl", value: "0 0 2 * * ?" }),
				el("div", { class: "form-hint" }, "每日 02:00"),
			])]
		: st === "interval"
			? [el("div", { class: "form-item" }, [
					el("label", { class: "form-label" }, "间隔"),
					el("div", { class: "ctl-wrap" }, [
						el("input", { class: "ctl", type: "number", value: 240 }),
						el("span", { class: "suffix" }, "分钟"),
					]),
				])]
			: [];

	return el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "运行时"),
			el("span", { class: "panel-note" }, "调度、窗口、容错与并发"),
		]),
		el("div", { class: "form-grid" }, [
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "调度方式"),
				el("select", {
					class: "ctl",
					onchange: (e) => {
						p.scheduleType = ["manual", "interval", "cron"][e.target.selectedIndex];
						rerenderHook();
					},
				}, [
					el("option", { selected: st === "manual" }, "仅手动"),
					el("option", { selected: st === "interval" }, "固定间隔"),
					el("option", { selected: st === "cron" }, "Cron 表达式"),
				]),
			]),
			...scheduleFields,
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "允许运行窗口"),
				el("div", { class: "ctl-wrap" }, [
					el("input", { class: "ctl", value: "01:00" }),
					el("span", { class: "suffix" }, "至"),
					el("input", { class: "ctl", value: "06:00" }),
				]),
				el("div", { class: "form-hint warn-text" }, "窗口外触发会被直接拒绝并记为失败，不会自动顺延"),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "时区"),
				el("div", { class: "inherited" }, [
					el("span", {}, "Asia/Shanghai"),
					el("span", { class: "chip chip-inherit" }, "平台配置"),
				]),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "脏数据策略"),
				el("select", { class: "ctl" }, [
					el("option", {}, "进 staging 待人工确认"),
					el("option", {}, "跳过并记录，继续本批"),
					el("option", {}, "立即中止本批"),
				]),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "失败重试"),
				el("select", { class: "ctl" }, [
					el("option", {}, "最多 3 次，指数退避"), el("option", {}, "不重试"),
				]),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "落地前预检"),
				el("select", { class: "ctl" }, [
					el("option", {}, "开启（失败进 staging 待人工确认）"),
					el("option", {}, "关闭（直接写入 ODS）"),
				]),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "撞上并发上限时"),
				el("select", {
					class: "ctl",
					onchange: (e) => { WIZ.policy.rejectPolicy = e.target.selectedIndex === 1 ? "QUEUE" : "REJECT"; rerenderHook(); },
				}, [
					el("option", { selected: (WIZ.policy.rejectPolicy || "REJECT") === "REJECT" }, "直接失败"),
					el("option", { selected: WIZ.policy.rejectPolicy === "QUEUE" }, "排队等待"),
				]),
				(WIZ.policy.rejectPolicy === "QUEUE")
					? el("div", { class: "form-hint warn-text" }, "现网最多只等 30 秒（硬编码），超时仍失败")
					: el("div", { class: "form-hint" }, "对应 rejectPolicy=REJECT"),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "队列优先级"),
				el("select", { class: "ctl", disabled: WIZ.policy.rejectPolicy !== "QUEUE" }, [
					el("option", { selected: true }, "普通（MEDIUM）"), el("option", {}, "高（HIGH）"), el("option", {}, "低（LOW）"),
				]),
				el("div", { class: "form-hint" },
					WIZ.policy.rejectPolicy === "QUEUE"
						? "决定多个任务抢同一槽位时谁先走"
						: "仅在「排队等待」模式下生效，当前不起作用"),
			]),
			el("div", { class: "form-item" }, [
				el("label", { class: "form-label" }, "本任务并发"),
				el("div", { class: "ctl-wrap" }, [
					el("input", { class: "ctl", type: "number", value: 3, min: 1, max: 6 }),
					el("span", { class: "suffix" }, "/ 上限 6"),
				]),
				el("div", { class: "form-hint" }, "上限来自连接与项目配额，不在此处修改"),
			]),
		]),
		el("div", { class: "quota-bar" }, [
			el("div", { class: "quota" }, [
				el("span", { class: "muted small" }, "源端并发配额（设在连接上）"),
				el("div", { class: "quota-track" }, el("div", { class: "quota-fill", style: "width:50%" })),
				el("span", { class: "small" }, "已占用 3 / 6"),
			]),
			el("div", { class: "quota" }, [
				el("span", { class: "muted small" }, "项目并发配额（管理员设定）"),
				el("div", { class: "quota-track" }, el("div", { class: "quota-fill", style: "width:35%" })),
				el("span", { class: "small" }, "已占用 7 / 20"),
			]),
		]),
		el("div", { class: "inline-alert warn" }, [
			el("b", {}, "现网默认是敞开的"),
			el("span", {}, "sourceConcurrencyLimit 与 projectConcurrencyLimit 默认值都是 0，而 0 表示不限——也就是护源库、护集群这两道闸门默认根本没生效，要每个任务的创建者自己去填才有用。改为配额后由管理员统一设定，任务只能在额度内申请。"),
		]),
	]);
}

/** 源端摘要按连接器形态取值，不同连接器的"地址"不是同一组字段 */
function describeEndpoint() {
	const v = WIZ.values;
	if (v.baseUrl) return v.baseUrl;
	if (v.jdbcUrl) return v.jdbcUrl;
	if (v.file) return `上传文件 ${v.file}`;
	if (v.host) return `${v.host}:${v.port ?? "—"}${v.database ? ` / ${v.database}` : v.schema ? ` / ${v.schema}` : ""}`;
	return "（未填写）";
}

function renderStepPolicy(rerender) {
	const tables = ensureTables().filter((t) => t.selected);
	const p = WIZ.policy;

	const modeCards = [
		{ key: "incremental", title: "时间戳增量", desc: "按 gmt_modified 拉取变更，主键去重", ok: true },
		{ key: "full_refresh", title: "全量覆盖", desc: "每次重建目标表，适合小表与字典", ok: true },
		{ key: "cdc", title: "CDC 日志捕获", desc: "需源端开启 binlog 且授予 REPLICATION 权限", ok: false, reason: "当前账号无 REPLICATION 权限" },
	];

	const modes = el("div", { class: "mode-grid" }, modeCards.map((m) => el("button", {
		type: "button",
		class: `mode-card${(p.syncMode || "incremental") === m.key ? " selected" : ""}${m.ok ? "" : " disabled"}`,
		disabled: !m.ok,
		title: m.reason || "",
		onclick: () => { p.syncMode = m.key; rerender(); },
	}, [
		el("b", {}, m.title),
		el("span", {}, m.desc),
		!m.ok ? el("span", { class: "chip chip-warn" }, m.reason) : null,
	])));

	return el("div", { class: "step-body" }, [
		el("section", { class: "panel" }, [
			el("div", { class: "panel-head" }, [el("h3", {}, "同步方式")]),
			modes,
		]),
		el("section", { class: "panel" }, [
			el("div", { class: "panel-head" }, [
				el("h3", {}, "基本信息"),
				el("span", { class: "panel-note" }, "密级与部门默认继承连接，偏离需显式覆盖"),
			]),
			el("div", { class: "form-grid" }, [
				el("div", { class: "form-item" }, [
					el("label", { class: "form-label" }, "接入名称"),
					el("input", { class: "ctl", value: p.name ?? "人事主数据库", oninput: (e) => { p.name = e.target.value; } }),
				]),
				el("div", { class: "form-item" }, [
					el("label", { class: "form-label" }, "所属项目"),
					el("select", { class: "ctl" }, [
						el("option", {}, "人事数据域"), el("option", {}, "综合治理"),
					]),
					el("div", { class: "form-hint" }, "决定配额与调度队列"),
				]),
				inheritedField("数据密级", "内部", "classification", rerender, ["公开", "内部", "秘密", "机密"]),
				inheritedField("归属部门", "人事处", "ownerDept", rerender),
			]),
		]),
		renderTargetPanel(),
		renderRuntimePanel(),
		el("section", { class: "panel" }, [
			el("div", { class: "panel-head" }, [
				el("h3", {}, "确认"),
				el("span", { class: "panel-note" }, "启用后进入「接入运维」跟踪"),
			]),
			el("div", { class: "summary" }, [
				["连接器", window.PROTO.connectorByKey(WIZ.connectorKey)?.name || "—"],
				["源端", describeEndpoint()],
				["入湖表", `${tables.length} 张 → ods_hr_*`],
				["同步方式", modeCards.find((m) => m.key === (p.syncMode || "incremental")).title],
				["密级", p.classification ?? "内部（继承）"],
				["预计首轮", "约 42 万行 / 6 分钟"],
			].map(([k, v]) => el("div", { class: "summary-item" }, [
				el("span", { class: "muted" }, k), el("b", {}, v),
			]))),
			el("button", {
				class: "link-btn", type: "button",
				onclick: () => { WIZ.showJob = !WIZ.showJob; rerender(); },
			}, WIZ.showJob ? "收起生成的执行作业" : "查看生成的执行作业（只读）"),
			WIZ.showJob ? renderGeneratedJob() : null,
			WIZ.showJob
				? el("div", { class: "foot-note" },
						"Addax 作业由平台生成，用户不再手写。密钥以引用形式出现，不落明文。")
				: null,
		]),
	]);
}

/* ---------------------------------------------------------------- */

function canAdvance() {
	if (WIZ.step === 0) return Boolean(WIZ.tested && WIZ.tested !== "testing" && WIZ.tested.ok);
	if (WIZ.step === 1) return ensureTables().some((t) => t.selected);
	return true;
}

function renderWizard(nav, rerender) {
	const body = WIZ.step === 0 ? renderStepConnect(rerender)
		: WIZ.step === 1 ? renderStepTables(rerender)
		: renderStepPolicy(rerender);

	const blockReason = !canAdvance()
		? (WIZ.step === 0 ? "请先测通连接" : "请至少选择一张表")
		: null;

	return el("div", { class: "wizard" }, [
		el("div", { class: "page-head" }, [
			el("div", {}, [
				el("h2", {}, "新建接入"),
				el("p", { class: "muted" }, "连接与任务在同一条流里完成，不再分散到六个页面"),
			]),
			el("button", { class: "btn ghost", onclick: () => nav("list") }, "取消"),
		]),
		renderStepper(),
		body,
		el("div", { class: "wizard-foot" }, [
			blockReason ? el("span", { class: "muted" }, blockReason) : null,
			el("div", { class: "spacer" }),
			WIZ.step > 0
				? el("button", { class: "btn ghost", onclick: () => { WIZ.step -= 1; rerender(); } }, "上一步")
				: null,
			WIZ.step < 2
				? el("button", {
						class: "btn primary", disabled: !canAdvance(),
						onclick: () => {
							if (!canAdvance()) return;
							if (WIZ.step === 0) {
								WIZ.discovering = true;
								WIZ.step = 1;
								rerender();
								setTimeout(() => { WIZ.discovering = false; ensureTables(); rerender(); }, 600);
								return;
							}
							WIZ.step += 1;
							rerender();
						},
					}, WIZ.step === 0 ? "下一步：发现表结构" : "下一步：设置策略")
				: el("button", { class: "btn primary", onclick: () => nav("detail") }, "创建并启用"),
		]),
	]);
}

window.Wizard = { renderWizard, resetWizard, WIZ };

})();
