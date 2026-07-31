/**
 * 新建接入向导第二步 —— 按来源类型定义资源。
 *
 * 连接只保存可复用的地址、认证与公共策略；表、API Endpoint、文件解析版本
 * 都是资源级配置。这里保持静态原型的全局脚本风格，不发真实请求。
 */
(function () {
const { el } = window.SchemaForm;
const P = window.PROTO;
const CLASSIFICATION_RANK = { "公开": 0, "内部": 1, "秘密": 2, "机密": 3 };
const RESOURCE_KIND_BY_CATEGORY = { DATABASE: "DATABASE", API: "API", FILE: "FILE" };
const RESOURCE_STEP_LABELS = {
	DATABASE: { title: "选择资源", desc: "选择表与字段，推导 ODS" },
	API: { title: "定义资源", desc: "请求样例、分页与响应结构" },
	FILE: { title: "解析文件", desc: "封条、解析预览与版本差异" },
};
function clone(value) { return JSON.parse(JSON.stringify(value)); }
function rerenderOrNoop(rerender) { return typeof rerender === "function" ? rerender : function () {}; }
function panel(title, note, children, extraClass) {
	const body = Array.isArray(children) ? children : [children];
	return el("section", { class: `panel${extraClass ? ` ${extraClass}` : ""}` }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, title),
			note ? el("span", { class: "panel-note" }, note) : null,
		]),
		...body,
	]);
}
function formItem(label, control, hint, span) {
	const tagName = control && control.tagName;
	if (["INPUT", "SELECT", "TEXTAREA", "BUTTON"].includes(tagName)) control.setAttribute("aria-label", label);
	return el("div", { class: `form-item${span ? ` span-${span}` : ""}` }, [
		el("label", { class: "form-label" }, label),
		control,
		hint ? el("div", { class: "form-hint" }, hint) : null,
	]);
}
function option(value, label, selected) { return el("option", { value, selected }, label); }
function formatCount(value) { return Number(value || 0).toLocaleString("zh-CN"); }
function highestClassification(levels) {
	return levels.reduce((highest, level) => {
		if (!(level in CLASSIFICATION_RANK)) return highest;
		if (!(highest in CLASSIFICATION_RANK)) return level;
		return CLASSIFICATION_RANK[level] > CLASSIFICATION_RANK[highest] ? level : highest;
	}, "公开");
}
function resourceKind(connectorKey) {
	const connector = P.connectorByKey(connectorKey);
	if (!connector) return "UNKNOWN";
	return RESOURCE_KIND_BY_CATEGORY[connector.category] || "UNSUPPORTED";
}
function resourceStepLabel(connectorKey) {
	const kind = resourceKind(connectorKey);
	return RESOURCE_STEP_LABELS[kind] || { title: "定义资源", desc: "当前连接器尚未提供资源步骤" };
}
function createResourceState(connectorKey) {
	const kind = resourceKind(connectorKey);
	if (kind === "DATABASE") {
		const tables = clone(P.DISCOVERED_TABLES).map((table) => ({ ...table, selected: false }));
		return { kind, tables, activeTable: tables.length ? tables[0].name : null, selectionMode: "manual" };
	}
	if (kind === "API") {
		const state = { kind, ...clone(P.API_RESOURCE_DEMO) };
		Object.assign(state, { sampleVersion: 0, sampleStatus: "idle", sampleResult: null, inferredSchema: [] });
		return state;
	}
	if (kind === "FILE") return { kind, ...clone(P.FILE_RESOURCE_DEMOS.xlsx) };
	return { kind };
}
function renderDatabaseTableList(state, rerender) {
	const render = rerenderOrNoop(rerender);
	const tables = Array.isArray(state.tables) ? state.tables : [];
	const selectedCount = tables.filter((table) => table.selected).length;
	const rows = tables.map((table) => el("tr", {
		class: `${table.selected ? "picked" : ""}${state.activeTable === table.name ? " active" : ""}`,
		onclick: () => {
			state.activeTable = table.name;
			render();
		},
	}, [
		el("td", { class: "cell-check" }, el("input", {
			type: "checkbox",
			checked: table.selected,
			"aria-label": `选择 ${table.name}`,
			onclick: (event) => {
				event.stopPropagation();
				table.selected = event.target.checked;
				render();
			},
		})),
		el("td", {}, [
			el("div", { class: "t-name" }, table.name),
			el("div", { class: "t-comment" }, table.comment),
		]),
		el("td", { class: "num" }, formatCount(table.rows)),
		el("td", { class: "num" }, table.size),
		el("td", {}, table.pk ? el("code", {}, table.pk) : el("span", { class: "muted" }, "无")),
		el("td", {}, table.incrementalCol
			? el("span", { class: "chip chip-ok" }, table.incrementalCol)
			: el("span", { class: "chip" }, "仅全量")),
		el("td", {}, el("span", {
			class: `chip${table.classification === "秘密" || table.classification === "机密" ? " chip-warn" : " chip-quiet"}`,
		}, table.classification || "待识别")),
			el("td", {}, table.warn ? el("span", { class: "chip chip-warn", title: table.warn }, "需确认") : ""),
		]));
	return panel("发现的数据库表", `共 ${tables.length} 张，已选 ${selectedCount} 张`, [
		el("div", { class: "inline-alert info" }, [
			el("b", {}, "默认不预选"),
			el("span", {}, "发现只代表可见，不代表已获准接入；请明确勾选本 Revision 的资源。"),
		]),
		el("div", { class: "select-mode" }, [
			el("button", {
				class: "btn small",
				type: "button",
				onclick: () => {
					tables.forEach((table) => { table.selected = !table.warn; });
					render();
				},
			}, "选择无警告表"),
			el("button", {
				class: "btn small ghost",
				type: "button",
				disabled: selectedCount === 0,
				onclick: () => {
					tables.forEach((table) => { table.selected = false; });
					render();
				},
			}, "清空"),
			el("span", { class: "muted small" }, "带“需确认”的表仍可单独勾选，但会进入发布审批。"),
		]),
		el("div", { class: "table-scroll" }, el("table", { class: "grid" }, [
			el("thead", {}, el("tr", {}, [
				el("th", { class: "cell-check" }, ""),
				el("th", {}, "源表"),
				el("th", { class: "num" }, "行数"),
				el("th", { class: "num" }, "大小"),
				el("th", {}, "主键"),
				el("th", {}, "可用增量列"),
				el("th", {}, "推断密级"),
				el("th", {}, ""),
			])),
			el("tbody", {}, rows),
		])),
	], "flush");
}
function renderDatabasePreview(state) {
	const tables = Array.isArray(state.tables) ? state.tables : [];
	const table = tables.find((item) => item.name === state.activeTable) || tables[0];
	if (!table) return panel("资源预览", "", el("div", { class: "empty" }, "未发现可用表"));
	const columnRows = P.SAMPLE_COLUMNS.map((column) => el("tr", {}, [
		el("td", {}, el("code", {}, column.src)),
		el("td", { class: "muted" }, column.srcType),
		el("td", {}, column.role ? el("span", { class: "chip chip-ok" }, column.role) : "—"),
		el("td", {}, column.classification
			? el("span", { class: `chip${column.classification === "秘密" ? " chip-warn" : " chip-quiet"}` }, column.classification)
			: "—"),
	]));
	return panel("表结构与落地预览", "点击左侧表切换；查看不等于选择", [
		el("div", { class: "kvline" }, [
			el("span", {}, "源表"),
			el("code", {}, table.name),
			el("span", { class: "arrow" }, "→"),
			el("span", {}, "目标"),
			el("code", { class: "strong" }, `ods.ods_hr_${table.name.replace(/^hr_/, "")}`),
		]),
		el("div", { class: "summary" }, [
			["字段", `${table.cols} 列`],
			["主键", table.pk || "无"],
			["增量", table.incrementalCol || "仅全量"],
			["推断密级", table.classification || "待确认"],
		].map(([key, value]) => el("div", { class: "summary-item" }, [
			el("span", { class: "muted" }, key),
			el("b", {}, value),
		]))),
		table.warn ? el("div", { class: "inline-alert warn" }, [
			el("b", {}, "资源级准入提示"),
			el("span", {}, table.warn),
		]) : null,
		el("div", { class: "sub-title" }, "抽样字段（静态演示）"),
		el("div", { class: "table-scroll short" }, el("table", { class: "grid tight" }, [
			el("thead", {}, el("tr", {}, [
				el("th", {}, "源字段"),
				el("th", {}, "源类型"),
				el("th", {}, "角色"),
				el("th", {}, "推断密级"),
			])),
			el("tbody", {}, columnRows),
		])),
	]);
}
function renderDatabaseResources(state, rerender) {
	return el("div", { class: "step-body split" }, [
		renderDatabaseTableList(state, rerender),
		renderDatabasePreview(state),
	]);
}
function validateDatabaseResources(state) {
	if (!state || !Array.isArray(state.tables)) return { ok: false, reason: "尚未读取数据库资源" };
	if (!state.tables.some((table) => table.selected)) return { ok: false, reason: "请至少明确选择一张源表" };
	return { ok: true, reason: "" };
}
function invalidateApiSample(state) {
	state.sampleVersion = Number(state.sampleVersion || 0) + 1;
	state.sampleStatus = "idle";
	state.sampleResult = null;
	state.inferredSchema = [];
}
function renderPairEditor(rows, rerender, keyPlaceholder, valuePlaceholder, invalidate) {
	const render = rerenderOrNoop(rerender);
	const pairs = Array.isArray(rows) ? rows : [];
	const invalidateEvidence = typeof invalidate === "function" ? invalidate : function () {};
	const box = el("div", { class: "kv" });
	pairs.forEach((row, index) => {
		box.appendChild(el("div", { class: "kv-row" }, [
				el("input", {
					class: "ctl",
					"aria-label": keyPlaceholder,
					value: row.k || "",
					placeholder: keyPlaceholder,
					onchange: (event) => { row.k = event.target.value; invalidateEvidence(); render(); },
				}),
			el("input", {
					class: "ctl",
					"aria-label": valuePlaceholder,
					value: row.v || "",
					placeholder: valuePlaceholder,
					onchange: (event) => { row.v = event.target.value; invalidateEvidence(); render(); },
				}),
			el("button", {
				class: "icon-btn",
				type: "button",
				title: "删除",
					onclick: () => {
						pairs.splice(index, 1);
						invalidateEvidence();
						render();
				},
			}, "×"),
		]));
	});
	box.appendChild(el("button", {
		class: "link-btn",
		type: "button",
		onclick: () => {
			pairs.push({ k: "", v: "" });
			invalidateEvidence();
			render();
		},
	}, "+ 添加一项"));
	return box;
}
function runApiSample(state, rerender) {
	const render = rerenderOrNoop(rerender);
	const sampleVersion = Number(state.sampleVersion || 0) + 1;
	state.sampleVersion = sampleVersion;
	state.sampleStatus = "loading";
	state.sampleResult = null;
	render();
	window.setTimeout(() => {
		if (state.sampleVersion !== sampleVersion) return;
		const demo = clone(P.API_RESOURCE_DEMO);
		state.sampleStatus = "succeeded";
		state.sampleResult = demo.sampleResult;
		state.inferredSchema = demo.inferredSchema;
		render();
	}, 550);
}
function renderApiRequest(state, rerender) {
	const render = rerenderOrNoop(rerender);
	const methods = ["GET", "POST", "PUT", "PATCH"];
	return panel("API 资源", "Method、Path 与参数属于 Endpoint，不写入可复用连接", [
		el("div", { class: "form-grid" }, [
			formItem("Method", el("select", {
				class: "ctl",
					onchange: (event) => {
						state.method = event.target.value;
						invalidateApiSample(state);
						render();
				},
			}, methods.map((method) => option(method, method, method === state.method)))),
			formItem("Path", el("input", {
					class: "ctl",
					value: state.path || "",
					placeholder: "/population/changes/search",
					onchange: (event) => { state.path = event.target.value; invalidateApiSample(state); render(); },
				}), "相对 Base URL；不在连接层固定", 2),
				formItem("Query 参数", renderPairEditor(state.query, render, "参数名", "样例值", () => invalidateApiSample(state)), "样例值只用于测试，运行时变量可在发布计划中绑定", 3),
			formItem("Body 模板", el("textarea", {
				class: "ctl mono",
					rows: 6,
					value: state.bodyTemplate || "",
					placeholder: "{}",
					onchange: (event) => { state.bodyTemplate = event.target.value; invalidateApiSample(state); render(); },
				}), "允许使用 ${window_start} / ${window_end} 等受控变量；不允许嵌入 Secret 明文", 3),
		]),
	]);
	}
function renderApiSample(state, rerender) {
	const render = rerenderOrNoop(rerender);
	const loading = state.sampleStatus === "loading";
	const result = state.sampleResult;
	return panel("样例请求与记录定位", "静态原型只模拟请求，不访问真实接口", [
		el("div", { class: "test-block" }, [
			el("button", {
				class: "btn primary",
				type: "button",
				disabled: loading,
				onclick: () => runApiSample(state, rerender),
			}, loading ? "发送中…" : result ? "重新发送样例请求" : "发送样例请求"),
			loading ? el("span", { class: "muted" }, "正在应用连接鉴权并等待响应…") : null,
			result ? el("div", { class: "test-result ok" }, [
				el("b", {}, `HTTP ${result.status}`),
				el("span", {}, `${result.durationMs}ms · 响应 ${result.bytes} · 已脱敏后保存样例`),
			]) : null,
		]),
		result ? el("div", { class: "kvline" }, [
			el("span", {}, "请求"),
			el("code", {}, `${state.method} ${result.requestUrl}`),
			el("span", { class: "chip chip-lock" }, "Secret 仅引用"),
		]) : null,
		result ? el("pre", { class: "code-block" }, JSON.stringify(result.body, null, 2)) : null,
		el("div", { class: "form-grid" }, [
			formItem("recordPath", el("input", {
					class: "ctl",
					value: state.recordPath || "",
					placeholder: "$.data.records",
					onchange: (event) => { state.recordPath = event.target.value; invalidateApiSample(state); render(); },
				}), "JSONPath 必须指向记录数组；样例中命中 2 条", 2),
			formItem("资源键", el("div", { class: "inherited" }, [
				el("code", {}, state.resourceKey || "api-resource"),
				el("span", { class: "chip chip-inherit" }, "Revision 内唯一"),
			]), "运行、checkpoint 与漂移均按此键隔离"),
		]),
	]);
	}
function paginationField(label, value, oninput, hint, rerender) {
	return formItem(label, el("input", {
		class: "ctl",
		value: value == null ? "" : value,
		onchange: (event) => { oninput(event); rerender(); },
	}), hint);
}
function renderApiPagination(state, rerender) {
	const render = rerenderOrNoop(rerender);
	const pagination = state.pagination || (state.pagination = { type: "none" });
	const invalidate = () => invalidateApiSample(state);
	const fields = [
		formItem("分页方式", el("select", {
			class: "ctl",
				onchange: (event) => {
					pagination.type = event.target.value;
					invalidate();
					render();
			},
		}, [
			option("none", "不分页", pagination.type === "none"),
			option("page", "页码", pagination.type === "page"),
			option("offset", "偏移量", pagination.type === "offset"),
			option("cursor", "游标", pagination.type === "cursor"),
		])),
	];
	if (pagination.type === "page") {
		fields.push(
			paginationField("页码参数", pagination.pageParam, (event) => { pagination.pageParam = event.target.value; invalidate(); }, "", render),
			paginationField("起始页", pagination.startPage, (event) => { pagination.startPage = Number(event.target.value); invalidate(); }, "", render),
			paginationField("每页条数参数", pagination.sizeParam, (event) => { pagination.sizeParam = event.target.value; invalidate(); }, "", render),
		);
	}
	if (pagination.type === "offset") {
		fields.push(
			paginationField("偏移参数", pagination.offsetParam, (event) => { pagination.offsetParam = event.target.value; invalidate(); }, "", render),
			paginationField("条数参数", pagination.limitParam, (event) => { pagination.limitParam = event.target.value; invalidate(); }, "", render),
		);
	}
	if (pagination.type === "cursor") {
		fields.push(
			paginationField("游标参数", pagination.cursorParam, (event) => { pagination.cursorParam = event.target.value; invalidate(); }, "", render),
			paginationField("下一游标路径", pagination.cursorPath, (event) => { pagination.cursorPath = event.target.value; invalidate(); }, "JSONPath，从每页响应提取", render),
		);
	}
	if (pagination.type !== "none") {
		fields.push(
			paginationField("每页条数", pagination.pageSize, (event) => { pagination.pageSize = Number(event.target.value); invalidate(); }, "", render),
			paginationField("单次最多页数", pagination.maxPages, (event) => { pagination.maxPages = Number(event.target.value); invalidate(); }, "硬上限，防止翻页不终止", render),
		);
	}
	return panel("资源级分页与 checkpoint", "同一连接可挂载分页协议不同的多个 Endpoint", [
		el("div", { class: "form-grid" }, fields),
		pagination.type === "cursor" ? el("div", { class: "inline-alert info" }, [
			el("b", {}, "checkpoint 预览"),
			el("span", {}, `checkpoint/${state.resourceKey || "api-resource"} = ${state.checkpointPreview || "nextCursor（首轮为空）"}`),
		]) : null,
		]);
	}
function renderInferredSchema(state) {
	const columns = Array.isArray(state.inferredSchema) ? state.inferredSchema : [];
	const rows = columns.map((column) => el("tr", {}, [
		el("td", {}, el("code", {}, column.name)),
		el("td", { class: "muted" }, column.jsonType),
		el("td", {}, el("code", {}, column.targetType)),
		el("td", {}, column.nullable ? "可空" : "必有"),
		el("td", {}, column.classification
			? el("span", { class: `chip${column.classification === "秘密" ? " chip-warn" : " chip-quiet"}` }, column.classification)
			: "—"),
	]));
	return panel("响应 Schema 推断", `${columns.length} 个字段，基于已脱敏样例冻结到 Revision`, [
		el("div", { class: "table-scroll short" }, el("table", { class: "grid tight" }, [
			el("thead", {}, el("tr", {}, [
				el("th", {}, "字段"),
				el("th", {}, "JSON 类型"),
				el("th", {}, "目标类型"),
				el("th", {}, "空值"),
				el("th", {}, "推断密级"),
			])),
			el("tbody", {}, rows),
		])),
		el("div", { class: "foot-note" }, "后续样例结构变化会生成新 Revision 差异，不会原地改写当前生效 Schema。"),
	]);
}
function renderApiResource(state, rerender) {
	return el("div", { class: "step-body" }, [
		renderApiRequest(state, rerender),
		renderApiSample(state, rerender),
		renderApiPagination(state, rerender),
		renderInferredSchema(state),
	]);
}
function validateApiResource(state) {
	if (!state || !state.method) return { ok: false, reason: "请选择 API Method" };
	if (!state.path || state.path.charAt(0) !== "/") return { ok: false, reason: "API Path 必须以 / 开头" };
	if (!state.sampleResult || state.sampleResult.status < 200 || state.sampleResult.status >= 300) {
		return { ok: false, reason: "请先获得成功的样例请求结果" };
	}
	if (!state.recordPath) return { ok: false, reason: "请填写指向记录数组的 recordPath" };
	if (!Array.isArray(state.inferredSchema) || !state.inferredSchema.length) {
		return { ok: false, reason: "尚未从样例响应推断出 Schema" };
	}
	const pagination = state.pagination || {};
	if (pagination.type === "cursor" && (!pagination.cursorParam || !pagination.cursorPath)) {
		return { ok: false, reason: "游标分页必须填写游标参数与下一游标路径" };
	}
	if (pagination.type !== "none" && (!pagination.pageSize || !pagination.maxPages)) {
		return { ok: false, reason: "分页资源必须设置每页条数与最多页数" };
	}
	return { ok: true, reason: "" };
}
/* 文件 Artifact 资源 */
function switchFileDemo(state, format) {
	const next = clone(P.FILE_RESOURCE_DEMOS[format]);
	Object.keys(state).forEach((key) => { delete state[key]; });
	Object.keys(next).forEach((key) => { state[key] = next[key]; });
	state.kind = "FILE";
}
function renderArtifact(state, rerender) {
	const render = rerenderOrNoop(rerender);
	const artifact = state.artifact || {};
	const seal = artifact.classificationSeal || {};
	const scan = artifact.securityScan || {};
	return panel("不可变 Source Artifact", "上传内容先形成版本化证据，再进入解析", [
		el("div", { class: "form-grid" }, [
			formItem("演示文件格式", el("select", {
				class: "ctl",
				onchange: (event) => {
					switchFileDemo(state, event.target.value);
					render();
				},
			}, [
				option("xlsx", "Excel（.xlsx）", artifact.format === "xlsx"),
				option("csv", "CSV（.csv）", artifact.format === "csv"),
			]), "真实流程由上传文件自动识别；此选择器仅用于评审两种条件表单"),
			formItem("Artifact", el("div", { class: "inherited" }, [
				el("span", {}, `${artifact.fileName || "—"} · ${artifact.size || "—"}`),
				el("span", { class: "chip chip-inherit" }, artifact.version || "v1"),
			]), "同名重传也生成新 Artifact，不覆盖旧版本", 2),
			formItem("SHA-256", el("code", { class: "small" }, artifact.sha256 || "—"), "内容寻址；发布 Revision 固定引用此 Hash", 3),
		]),
		el("div", { class: "summary" }, [
			["密级封条", `${seal.effectiveClassification || "待确认"} · ${seal.sealId || "未封存"}`],
			["封条依据", (seal.evidence || []).join("；") || "—"],
			["安全扫描", scan.status === "PASSED" ? `通过 · ${scan.engine}` : scan.status || "待扫描"],
			["扫描时间", scan.scannedAt || "—"],
		].map(([key, value]) => el("div", { class: "summary-item" }, [
			el("span", { class: "muted" }, key),
			el("b", {}, value),
		]))),
		scan.status === "PASSED" ? el("div", { class: "inline-alert info" }, [
			el("b", {}, "✓ 安全扫描通过"),
			el("span", {}, `${scan.signatures || "病毒库已更新"}；宏、外链和压缩炸弹检查均无命中。`),
		]) : el("div", { class: "inline-alert warn" }, [
			el("b", {}, "解析已阻断"),
			el("span", {}, "安全扫描通过前不能预览或发布。"),
		]),
	]);
}
function numericInput(value, oninput, min) {
	return el("input", {
		class: "ctl",
		type: "number",
		value,
		min: min == null ? 1 : min,
		onchange: oninput,
	});
}
function renderExcelParse(parse, rerender) {
	const render = rerenderOrNoop(rerender);
	return [
		formItem("工作表", el("select", {
			class: "ctl",
			onchange: (event) => {
				parse.sheet = event.target.value;
				render();
			},
		}, (parse.availableSheets || []).map((sheet) => option(sheet, sheet, sheet === parse.sheet))), "工作表属于本 Artifact Revision"),
		formItem("表头行", numericInput(parse.headerRow, (event) => { parse.headerRow = Number(event.target.value); render(); }), "从 1 开始"),
		formItem("数据起始行", numericInput(parse.dataStartRow, (event) => { parse.dataStartRow = Number(event.target.value); render(); }), "必须晚于或等于表头行"),
		formItem("日期格式", el("input", {
			class: "ctl",
			value: parse.dateFormat || "",
			onchange: (event) => { parse.dateFormat = event.target.value; render(); },
		}), "无法从单元格类型判断时使用"),
		formItem("填充合并单元格", el("button", {
			class: `switch${parse.fillMerged ? " on" : ""}`,
			type: "button",
			"aria-pressed": String(Boolean(parse.fillMerged)),
			onclick: () => {
				parse.fillMerged = !parse.fillMerged;
				render();
			},
		}, el("span", { class: "knob" })), "只向下填充，不跨越空白分组"),
	];
}
function renderCsvParse(parse, rerender) {
	const render = rerenderOrNoop(rerender);
	return [
		formItem("字符编码", el("select", {
			class: "ctl",
			onchange: (event) => { parse.encoding = event.target.value; render(); },
		}, ["UTF-8", "GB18030", "GBK"].map((encoding) => option(encoding, encoding, encoding === parse.encoding)))),
		formItem("分隔符", el("input", {
			class: "ctl mono",
			value: parse.delimiter || "",
			onchange: (event) => { parse.delimiter = event.target.value; render(); },
		}), "支持单字符分隔符"),
		formItem("表头行", numericInput(parse.headerRow, (event) => { parse.headerRow = Number(event.target.value); render(); }), "从 1 开始"),
		formItem("引号字符", el("input", {
			class: "ctl mono",
			value: parse.quote || "",
			onchange: (event) => { parse.quote = event.target.value; render(); },
		}), "通常为双引号"),
		formItem("转义字符", el("input", {
			class: "ctl mono",
			value: parse.escape || "",
			onchange: (event) => { parse.escape = event.target.value; render(); },
		}), "与引号字符分开声明"),
		formItem("日期格式", el("input", {
			class: "ctl",
			value: parse.dateFormat || "",
			onchange: (event) => { parse.dateFormat = event.target.value; render(); },
		})),
	];
}
function renderFileParse(state, rerender) {
	const artifact = state.artifact || {};
	const parse = state.parse || (state.parse = {});
	const fields = artifact.format === "csv"
		? renderCsvParse(parse, rerender)
		: renderExcelParse(parse, rerender);
	return panel("条件解析设置", artifact.format === "csv"
		? "CSV 仅显示编码、分隔符、quote 与 escape"
		: "Excel 仅显示工作表、行号与合并单元格规则", [
		el("div", { class: "form-grid" }, fields),
		el("div", { class: "foot-note" }, "这些配置随 Artifact 和 Revision 冻结，不回写连接模板。"),
	]);
}
function renderFilePreview(state) {
	const columns = Array.isArray(state.previewColumns) ? state.previewColumns : [];
	const rows = Array.isArray(state.previewRows) ? state.previewRows : [];
	return panel("解析预览与 Schema 推断", `${formatCount(state.rowCount)} 行；展示前 ${rows.length} 行脱敏样例`, [
		el("div", { class: "table-scroll" }, el("table", { class: "grid tight" }, [
			el("thead", {}, el("tr", {}, columns.map((column) => el("th", {}, [
				el("div", {}, column.label),
				el("div", { class: "muted small" }, `${column.name} · ${column.type}`),
			])))),
			el("tbody", {}, rows.map((row) => el("tr", {}, columns.map((column) =>
				el("td", {}, row[column.name] == null ? "—" : String(row[column.name])),
			)))),
		])),
		el("div", { class: "foot-note" }, "完整文件不会进入浏览器；预览值已按推断密级脱敏。"),
	]);
}
function diffItems(items, emptyText) {
	if (!items || !items.length) return el("span", { class: "muted" }, emptyText);
	return el("div", {}, items.map((item) => el("div", { class: "kvline" }, [
		el("code", {}, item.name),
		el("span", {}, item.detail || item.type || ""),
	])));
}
function renderFileDiff(state) {
	const diff = state.schemaDiff || {};
	return panel("与当前 Revision 的 Schema 差异", `对比 ${diff.againstRevision || "当前生效版本"}`, [
		el("div", { class: "summary" }, [
			el("div", { class: "summary-item" }, [
				el("span", { class: "muted" }, "新增字段"),
				el("b", {}, String((diff.added || []).length)),
			]),
			el("div", { class: "summary-item" }, [
				el("span", { class: "muted" }, "类型变化"),
				el("b", {}, String((diff.changed || []).length)),
			]),
			el("div", { class: "summary-item" }, [
				el("span", { class: "muted" }, "删除字段"),
				el("b", {}, String((diff.removed || []).length)),
			]),
		]),
		el("div", { class: "form-grid" }, [
			formItem("新增", diffItems(diff.added, "无"), "", 1),
			formItem("变化", diffItems(diff.changed, "无"), "", 1),
			formItem("删除", diffItems(diff.removed, "无"), "", 1),
		]),
		(diff.removed || []).length ? el("div", { class: "inline-alert warn" }, [
			el("b", {}, "需要审批"),
			el("span", {}, "字段删除不会自动发布，旧 Revision 继续生效。"),
		]) : el("div", { class: "inline-alert info" }, [
			el("b", {}, "旧版本未被改写"),
			el("span", {}, "确认后生成新 Revision；所有历史运行仍可追溯到原 Schema。"),
		]),
	]);
}

function renderFileResource(state, rerender) {
	return el("div", { class: "step-body" }, [
		renderArtifact(state, rerender),
		renderFileParse(state, rerender),
		renderFilePreview(state),
		renderFileDiff(state),
	]);
}

function validateFileResource(state) {
	const artifact = state && state.artifact;
	if (!artifact) return { ok: false, reason: "尚未形成文件 Artifact" };
	if (!/^[a-f0-9]{64}$/i.test(artifact.sha256 || "")) return { ok: false, reason: "Artifact 缺少有效 SHA-256" };
	if (!artifact.classificationSeal || artifact.classificationSeal.state !== "SEALED") {
		return { ok: false, reason: "文件密级尚未封存" };
	}
	if (!artifact.securityScan || artifact.securityScan.status !== "PASSED") {
		return { ok: false, reason: "文件安全扫描尚未通过" };
	}

	const parse = state.parse || {};
	if (artifact.format === "xlsx") {
		const headerRow = Number(parse.headerRow);
		const dataStartRow = Number(parse.dataStartRow);
		if (!parse.sheet || !Number.isInteger(headerRow) || headerRow < 1 || !Number.isInteger(dataStartRow) || dataStartRow < 1) {
			return { ok: false, reason: "请完整设置 Excel 工作表与正整数行号" };
		}
		if (dataStartRow < headerRow) return { ok: false, reason: "数据起始行不能早于表头行" };
	}
	if (artifact.format === "csv" && (!parse.encoding || !parse.delimiter || !parse.quote || !parse.escape)) {
		return { ok: false, reason: "请完整设置 CSV 编码与分隔规则" };
	}
	if (!Array.isArray(state.previewColumns) || !state.previewColumns.length) {
		return { ok: false, reason: "文件尚未生成解析预览" };
	}
	return { ok: true, reason: "" };
}
/* 统一入口与第三步摘要 */
function renderResourceStep(connectorKey, state, rerender) {
	const kind = resourceKind(connectorKey);
	if (!state || state.kind !== kind) {
		return el("div", { class: "empty" }, "资源状态与所选连接器不匹配，请重新初始化第二步。");
	}
	if (kind === "DATABASE") return renderDatabaseResources(state, rerender);
	if (kind === "API") return renderApiResource(state, rerender);
	if (kind === "FILE") return renderFileResource(state, rerender);
	return el("div", { class: "empty" }, "该连接器尚未实现资源级配置原型。");
}

function validateResourceStep(connectorKey, state) {
	const kind = resourceKind(connectorKey);
	if (!state || state.kind !== kind) return { ok: false, reason: "资源状态与连接器不匹配" };
	if (kind === "DATABASE") return validateDatabaseResources(state);
	if (kind === "API") return validateApiResource(state);
	if (kind === "FILE") return validateFileResource(state);
	return { ok: false, reason: "该连接器尚未实现资源步骤" };
}

function databaseSummary(state) {
	const selected = (state.tables || []).filter((table) => table.selected);
	const rows = selected.reduce((sum, table) => sum + Number(table.rows || 0), 0);
	const levels = selected.map((table) => table.classification || "内部");
	const effectiveClassification = selected.length ? highestClassification(levels) : "待确认";
	const warned = selected.filter((table) => table.warn);
	const evidence = selected.length
		? [
				`元数据发现与字段抽样覆盖 ${selected.length} 张已选表`,
				`资源推断最高密级为${effectiveClassification}`,
			]
		: ["尚未选择资源，密级证据待生成"];
	if (warned.length) evidence.push(`${warned.map((table) => table.name).join("、")} 命中资源级准入提示`);

	return {
		kind: "DATABASE",
		resourceLabel: selected.length > 1 ? `${selected[0].name} 等 ${selected.length} 张表` : selected.length ? selected[0].name : "未选择源表",
		count: selected.length,
		resourceKeys: selected.map((table) => table.name),
		targetHint: selected.length ? `ods.ods_hr_*（${selected.length} 张）` : "待选择后推导",
		effectiveClassification,
		classificationEvidence: evidence,
		estimatedVolume: selected.length ? `首轮约 ${formatCount(rows)} 行` : "待选择资源",
		requiresApproval: warned.length > 0 || effectiveClassification === "秘密" || effectiveClassification === "机密",
	};
}

function apiSummary(state) {
	const classification = state.classification || {};
	const effectiveClassification = classification.effectiveClassification || "待确认";
	const path = state.path || "未定义 Path";
	return {
		kind: "API",
		resourceLabel: `${state.method || "—"} ${path}`,
		count: state.path ? 1 : 0,
		resourceKeys: state.path ? [state.resourceKey || path] : [],
		targetHint: state.targetHint || "ods.ods_api_resource",
		effectiveClassification,
		classificationEvidence: clone(classification.evidence || ["样例响应尚未生成密级证据"]),
		estimatedVolume: state.estimatedVolume || "待样例请求后估算",
		requiresApproval: Boolean(state.requiresApproval)
			|| effectiveClassification === "秘密"
			|| effectiveClassification === "机密",
	};
}

function fileSummary(state) {
	const artifact = state.artifact || {};
	const seal = artifact.classificationSeal || {};
	const diff = state.schemaDiff || {};
	const key = artifact.id
		? `${artifact.id}${state.parse && state.parse.sheet ? `#${state.parse.sheet}` : ""}`
		: null;
	return {
		kind: "FILE",
		resourceLabel: artifact.fileName || "未上传文件",
		count: artifact.id ? 1 : 0,
		resourceKeys: key ? [key] : [],
		targetHint: state.targetHint || "ods.ods_file_resource",
		effectiveClassification: seal.effectiveClassification || "待确认",
		classificationEvidence: clone(seal.evidence || ["Artifact 密级尚未封存"]),
		estimatedVolume: artifact.id ? `${formatCount(state.rowCount)} 行 · ${artifact.size}` : "待解析",
		requiresApproval: Boolean(state.requiresApproval) || (diff.removed || []).length > 0,
	};
}

function resourceSummary(connectorKey, state) {
	const kind = resourceKind(connectorKey);
	if (!state || state.kind !== kind) {
		return {
			kind,
			resourceLabel: "资源状态不匹配",
			count: 0,
			resourceKeys: [],
			targetHint: "—",
			effectiveClassification: "待确认",
			classificationEvidence: ["资源状态与连接器不匹配"],
			estimatedVolume: "—",
			requiresApproval: false,
		};
	}
	if (kind === "DATABASE") return databaseSummary(state);
	if (kind === "API") return apiSummary(state);
	if (kind === "FILE") return fileSummary(state);
	return {
		kind,
		resourceLabel: "未实现资源",
		count: 0,
		resourceKeys: [],
		targetHint: "—",
		effectiveClassification: "待确认",
		classificationEvidence: ["该连接器尚未实现资源步骤"],
		estimatedVolume: "—",
		requiresApproval: false,
	};
}

window.ResourceSteps = {
	createResourceState,
	renderResourceStep,
	renderDatabaseResources,
	renderApiResource,
	renderFileResource,
	validateResourceStep,
	validateDatabaseResources,
	validateApiResource,
	validateFileResource,
	resourceStepLabel,
	resourceKind,
	resourceSummary,
};

})();
