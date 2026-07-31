/**
 * 接入概览、分类入口与统一默认策略。
 */

(function () {

const V = window.SchemaForm.el;
const P = window.PROTO;

const SOURCE_META = {
	DATABASE: {
		route: "database",
		title: "数据库接入",
		desc: "管理 JDBC 数据源、表发现、增量策略与 ODS 落地。",
		action: "新建数据库接入",
		short: "数据库",
		tone: "database",
	},
	API: {
		route: "api",
		title: "API 接入",
		desc: "管理 HTTP 端点、鉴权、分页游标与响应结构。",
		action: "新建 API 接入",
		short: "API",
		tone: "api",
	},
	FILE: {
		route: "file",
		title: "离线文件接入",
		desc: "管理 Excel / CSV 制品、解析规则、批次与落地结果。",
		action: "上传离线文件",
		short: "离线文件",
		tone: "file",
	},
};

const DEFAULT_ROWS = window.WizardRuntime.PROFILE.parameters;
const LIST_FILTERS = { query: "", lifecycle: "ALL", health: "ALL", owner: "ALL" };

function sourceMetaOf(connection) {
	const connector = P.connectorByKey(connection.connector);
	return { connector, meta: SOURCE_META[connector?.category] || SOURCE_META.DATABASE };
}

function chip(meta, prefix) {
	return V("span", {
		class: `chip chip-${meta.tone}`,
		title: meta.code || meta.label,
	}, prefix ? `${prefix} · ${meta.label}` : meta.label);
}

function createButton(nav, category, secondary) {
	const meta = SOURCE_META[category];
	return V("button", {
		class: `btn ${secondary ? "" : "primary"}`,
		type: "button",
		onclick: () => nav("wizard", null, { sourceCategory: category }),
	}, `+ ${meta.action}`);
}

function renderHead(nav, category) {
	const meta = category ? SOURCE_META[category] : null;
	return V("div", { class: "page-head" }, [
		V("div", {}, [
			V("h2", {}, meta?.title || "接入概览"),
			V("p", { class: "muted" }, meta?.desc
				|| "按数据库、API、离线文件三个入口管理，概览只汇总当前状态与待处理事项。"),
		]),
		V("div", { class: "head-actions" }, meta
			? [createButton(nav, category)]
			: [
					createButton(nav, "DATABASE"),
					createButton(nav, "API", true),
					createButton(nav, "FILE", true),
				]),
	]);
}

function renderFilters(nav, category) {
	const route = category ? SOURCE_META[category].route : "list";
	const refresh = () => nav(route);
	const controls = [
		V("input", {
			class: "ctl",
			placeholder: "搜索名称、地址、负责人…",
			value: LIST_FILTERS.query,
			onchange: (event) => { LIST_FILTERS.query = event.target.value.trim(); refresh(); },
		}),
	];
	if (!category) {
		controls.push(V("select", {
			class: "ctl narrow",
			"aria-label": "接入方式",
			onchange: (event) => nav(event.target.value ? SOURCE_META[event.target.value].route : "list"),
		}, [
			V("option", { value: "" }, "全部接入方式"),
			...Object.entries(SOURCE_META).map(([key, item]) => V("option", { value: key }, item.short)),
		]));
	}
	controls.push(
		V("select", {
			class: "ctl narrow",
			"aria-label": "生命周期",
			onchange: (event) => { LIST_FILTERS.lifecycle = event.target.value; refresh(); },
		}, [
			V("option", { value: "ALL", selected: LIST_FILTERS.lifecycle === "ALL" }, "全部生命周期"),
			V("option", { value: "ACTIVE", selected: LIST_FILTERS.lifecycle === "ACTIVE" }, "已发布"),
			V("option", { value: "DRAFT", selected: LIST_FILTERS.lifecycle === "DRAFT" }, "草稿"),
			V("option", { value: "PAUSED", selected: LIST_FILTERS.lifecycle === "PAUSED" }, "已暂停"),
		]),
		V("select", {
			class: "ctl narrow",
			"aria-label": "健康状态",
			onchange: (event) => { LIST_FILTERS.health = event.target.value; refresh(); },
		}, [
			V("option", { value: "ALL", selected: LIST_FILTERS.health === "ALL" }, "全部健康状态"),
			V("option", { value: "HEALTHY", selected: LIST_FILTERS.health === "HEALTHY" }, "健康"),
			V("option", { value: "ATTENTION", selected: LIST_FILTERS.health === "ATTENTION" }, "需关注"),
			V("option", { value: "NOT_EVALUATED", selected: LIST_FILTERS.health === "NOT_EVALUATED" }, "未评估"),
		]),
		V("select", {
			class: "ctl narrow",
			"aria-label": "负责人",
			onchange: (event) => { LIST_FILTERS.owner = event.target.value; refresh(); },
		}, [
			V("option", { value: "ALL", selected: LIST_FILTERS.owner === "ALL" }, "全部负责人"),
			...[...new Set(P.CONNECTIONS.map((item) => item.owner))].map((owner) =>
				V("option", { value: owner, selected: LIST_FILTERS.owner === owner }, owner)),
		]),
		V("button", {
			class: "btn",
			type: "button",
			onclick: () => {
				Object.assign(LIST_FILTERS, { query: "", lifecycle: "ALL", health: "ALL", owner: "ALL" });
				refresh();
			},
		}, "重置"),
	);
	return V("div", { class: "table-toolbar" }, controls);
}

function renderConnectionRow(nav, connection) {
	const { connector, meta } = sourceMetaOf(connection);
	const status = window.Views.workspacePresentation(connection);
	const runTime = connection.lastRun === "—" ? "未运行" : connection.lastRun;
	const revision = status.revision.active || "未发布";
	const attention = connection.health.drift
		? `${connection.health.drift} 处漂移`
		: connection.health.stagingErrors ? `${connection.health.stagingErrors} 行待确认` : null;
	return V("tr", {}, [
		V("td", {}, [
			V("button", { class: "table-name", type: "button", onclick: () => nav("detail", connection.id) }, connection.name),
			V("span", { class: "table-sub" }, `${connection.id} · ${connection.tables} 个资源`),
		]),
		V("td", {}, V("span", { class: `source-kind ${meta.tone}` }, [
			V("span", { class: "source-kind-icon" }, connector?.icon || "•"),
			V("span", {}, meta.short),
		])),
		V("td", {}, [
			V("b", { class: "table-primary" }, connector?.name || connection.connector),
			V("span", { class: "table-sub endpoint" }, connection.endpoint),
		]),
		V("td", {}, [
			V("span", { class: "table-primary" }, revision),
			status.revision.draft
				? V("span", { class: "table-sub draft-revision" }, `待发布：${status.revision.draft}`)
				: null,
			V("span", { class: "table-sub" }, connection.syncMode === "incremental" ? "增量同步" : "全量 / 批次"),
		]),
		V("td", {}, V("div", { class: "status-stack" }, [
			chip(status.lifecycle),
			chip(status.health),
			attention ? V("span", { class: "table-warning" }, attention) : null,
		])),
		V("td", {}, chip(status.admission)),
		V("td", {}, [
			chip(status.lastRun),
			V("span", { class: "table-sub" }, runTime),
		]),
		V("td", {}, [
			V("span", { class: "table-primary" }, connection.owner),
			V("span", { class: `classification-tag level-${connection.classification}` }, `密级：${connection.classification}`),
		]),
		V("td", {}, V("div", { class: "row-actions" }, [
			V("button", { class: "link-btn", type: "button", onclick: () => nav("detail", connection.id) }, "详情"),
		])),
	]);
}

function renderList(nav, category) {
	const query = LIST_FILTERS.query.toLowerCase();
	const rows = P.CONNECTIONS.filter((connection) => {
		const connector = P.connectorByKey(connection.connector);
		const status = window.Views.workspacePresentation(connection);
		const matchesCategory = !category || connector?.category === category;
		const matchesQuery = !query || [connection.name, connection.endpoint, connection.owner, connector?.name]
			.some((value) => String(value || "").toLowerCase().includes(query));
		return matchesCategory && matchesQuery
			&& (LIST_FILTERS.lifecycle === "ALL" || status.lifecycle.code === LIST_FILTERS.lifecycle)
			&& (LIST_FILTERS.health === "ALL" || status.health.code === LIST_FILTERS.health)
			&& (LIST_FILTERS.owner === "ALL" || connection.owner === LIST_FILTERS.owner);
	});
	const emptyText = category ? `暂无${SOURCE_META[category].short}接入` : "暂无接入记录";
	return V("div", { class: "page access-page" }, [
		renderHead(nav, category),
		V("section", { class: "table-shell" }, [
			renderFilters(nav, category),
			V("div", { class: "table-scroll access-table-scroll" }, V("table", { class: "grid access-table" }, [
				V("thead", {}, V("tr", {}, [
					"接入名称", "接入方式", "来源 / 资源", "有效配置", "状态", "ODS 准入", "最近运行", "负责人 / 密级", "操作",
				].map((title) => V("th", {}, title)))),
				V("tbody", {}, rows.length
					? rows.map((connection) => renderConnectionRow(nav, connection))
					: V("tr", {}, V("td", { colspan: 9, class: "table-empty" }, emptyText))),
			])),
			V("div", { class: "table-footer" }, [
				V("span", {}, `共 ${rows.length} 条`),
				V("span", { class: "spacer" }),
				V("span", {}, "10 条 / 页"),
				V("button", { class: "pager active", type: "button" }, "1"),
			]),
		]),
	]);
}

function overrideChip(value) {
	const tone = value === "允许" ? "ok" : value === "受限" ? "warn" : "muted";
	return V("span", { class: `chip chip-${tone}` }, value);
}

function renderDefaults() {
	const layers = [
		["1", "连接器契约", "字段边界与硬约束"],
		["2", "平台默认", "版本化基线"],
		["3", "连接 / 任务覆盖", "只保存偏离项"],
		["4", "发布快照", "冻结有效配置"],
	];
	return V("div", { class: "page defaults-page" }, [
		V("div", { class: "page-head" }, [
			V("div", {}, [
				V("h2", {}, "默认策略"),
				V("p", { class: "muted" }, "统一维护平台基线；连接和任务只覆盖确有差异的参数。"),
			]),
			V("div", { class: "head-actions" }, [
				V("span", { class: "version-badge" }, `当前版本 ${window.WizardRuntime.PROFILE.ref}`),
				V("button", {
					class: "btn",
					type: "button",
					disabled: true,
					title: "本轮只验证参数归属，版本发布流程下一轮设计",
				}, "新建版本（下一轮）"),
			]),
		]),
		V("div", { class: "inline-alert info defaults-guidance" }, [
			V("b", {}, "推荐：统一默认 + 分层覆盖"),
			V("span", {}, "不是“全局统一”和“任务完全定制”二选一。已发布 Revision 固化有效配置，平台默认升级不会静默改变既有任务。"),
		]),
		V("div", { class: "policy-resolution", "aria-label": "参数解析顺序" },
			layers.flatMap(([no, title, desc], index) => [
				V("div", { class: "policy-layer" }, [
					V("span", { class: "policy-no" }, no),
					V("b", {}, title),
					V("span", {}, desc),
				]),
				index < layers.length - 1 ? V("span", { class: "policy-arrow" }, "→") : null,
			])),
		V("section", { class: "table-shell" }, [
			V("div", { class: "table-toolbar" }, [
				V("b", {}, "统一策略清单"),
				V("span", { class: "muted small" }, "参数来源与覆盖权限在同一版本中维护"),
			]),
			V("div", { class: "table-scroll" }, V("table", { class: "grid defaults-table" }, [
				V("thead", {}, V("tr", {}, [
					"适用范围", "参数编码", "参数名称", "当前默认值", "建议覆盖层级", "任务可覆盖", "来源版本",
				].map((title) => V("th", {}, title)))),
				V("tbody", {}, DEFAULT_ROWS.map((row) => V("tr", {}, [
					V("td", {}, V("span", { class: "source-scope" }, row.scope)),
					V("td", {}, V("code", {}, row.key)),
					V("td", {}, row.label),
					V("td", {}, V("b", { class: "table-primary" }, row.value)),
					V("td", {}, row.owner),
					V("td", {}, overrideChip(row.override)),
					V("td", {}, V("span", { class: "version-link" }, row.version)),
				]))),
			])),
			V("div", { class: "table-footer" }, [
				V("span", {}, `共 ${DEFAULT_ROWS.length} 个默认参数`),
				V("span", { class: "spacer" }),
				V("span", {}, "变更需生成新版本后再应用"),
			]),
		]),
	]);
}

window.AccessPages = { renderList, renderDefaults, SOURCE_META };

})();
