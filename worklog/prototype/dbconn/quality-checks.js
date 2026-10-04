/**
 * 数据质量规则引用与接入阶段结果展示。
 *
 * 这里只展示数据质量服务返回的绑定快照和静态运行样例；规则定义、SQL、
 * 阈值、绑定与 GovQualityRun 仍以数据质量模块为唯一事实源。
 */

(function () {

const V = window.SchemaForm.el;

/* 静态 fixture 模拟数据质量服务的只读响应，不是接入侧规则目录。 */
const QUALITY_BINDING_SNAPSHOT = {
	snapshotRef: "gov-rule-binding-snapshot://prototype-revision/qbs-204",
	resolvedBy: "DTS_DATA_QUALITY",
	rules: [
		{ code: "NOT_NULL", name: "关键字段非空", versionRef: "gov-rule-version://not-null@7", bindingRef: "gov-rule-binding://qb-401", severity: "阻断" },
		{ code: "UNIQUE_KEY", name: "业务主键唯一", versionRef: "gov-rule-version://unique-key@5", bindingRef: "gov-rule-binding://qb-402", severity: "阻断" },
		{ code: "REFERENCE_CODE", name: "标准字典有效", versionRef: "gov-rule-version://reference-code@12", bindingRef: "gov-rule-binding://qb-403", severity: "失败计数" },
		{ code: "FORMAT_RANGE", name: "格式与取值范围", versionRef: "gov-rule-version://format-range@4", bindingRef: "gov-rule-binding://qb-404", severity: "失败计数" },
	],
};

const TECHNICAL_GATES = {
	DATABASE: ["连接与只读权限", "Schema 快照", "增量边界与 checkpoint"],
	API: ["TLS 与鉴权", "响应解析", "分页与游标连续性"],
	FILE: ["加密制品与哈希", "恶意内容扫描", "格式、Sheet 与表头解析"],
};

const RUN_RESULTS = {
	"ing-1039": {
		runId: "run-88213",
		revisionId: "rev-12",
		processed: 10_000_000,
		accepted: 9_998_750,
		failed: 1_250,
		status: "PARTIAL",
		targetPolicy: "隔离异常行，正常行提交（待扩展）",
		issues: [
			{ resource: "fin_voucher", recordKey: "voucher_id=FV-260730-0183", field: "account_code", value: "66***9", ruleVersionRef: "gov-rule-version://reference-code@12", reason: "科目编码在标准字典中不存在" },
			{ resource: "fin_budget_execution", recordKey: "execution_id=BE-882013", field: "project_no", value: "(null)", ruleVersionRef: "gov-rule-version://not-null@7", reason: "关键字段不能为空" },
			{ resource: "fin_subject", recordKey: "subject_code=660201", field: "subject_code", value: "660201", ruleVersionRef: "gov-rule-version://unique-key@5", reason: "业务主键在本批次内重复" },
		],
	},
	"ing-1035": {
		runId: "run-api-1035-044",
		revisionId: "rev-8",
		processed: 12_806,
		accepted: 12_783,
		failed: 23,
		status: "PARTIAL",
		targetPolicy: "隔离异常记录，成功后提交 cursor（待扩展）",
		issues: [
			{ resource: "POST /population/changes/search", recordKey: "personId=P-100238", field: "changedAt", value: "2026-13-40", ruleVersionRef: "gov-rule-version://format-range@4", reason: "时间字段不是有效 ISO-8601" },
			{ resource: "POST /population/changes/search", recordKey: "personId=P-100241", field: "regionCode", value: "99****", ruleVersionRef: "gov-rule-version://reference-code@12", reason: "行政区划代码不存在" },
		],
	},
	"ing-1028": {
		runId: "file-check-1028-r06",
		revisionId: "rev-6",
		processed: 1_204,
		accepted: 1_187,
		failed: 17,
		status: "BLOCKED",
		targetPolicy: "预检通过后才允许发布文件 Revision",
		issues: [
			{ row: 4, field: "资产编号", value: "ZC2026 0032", ruleVersionRef: "gov-rule-version://format-range@4", reason: "不符合编码规则 ZC-YYYY-NNNN" },
			{ row: 7, field: "购置日期", value: "2026/13/02", ruleVersionRef: "gov-rule-version://format-range@4", reason: "非法日期" },
			{ row: 9, field: "使用部门", value: "信息处", ruleVersionRef: "gov-rule-version://reference-code@12", reason: "部门字典中不存在" },
			{ row: 12, field: "原值(元)", value: "—", ruleVersionRef: "gov-rule-version://not-null@7", reason: "必填项为空" },
		],
	},
};

const FILE_EDITS = new Map();

function ruleByVersionRef(versionRef) {
	return QUALITY_BINDING_SNAPSHOT.rules.find((rule) => rule.versionRef === versionRef)
		|| { name: "未知规则版本", versionRef, severity: "失败计数" };
}

function resultFor(connection, category) {
	if (RUN_RESULTS[connection.id]) return RUN_RESULTS[connection.id];
	return {
		runId: "尚无质量运行",
		revisionId: "unbound",
		processed: 0,
		accepted: 0,
		failed: 0,
		status: "NOT_RUN",
		targetPolicy: category === "FILE" ? "等待文件上传并执行预检" : "等待同步批次触发质量运行",
		issues: [],
	};
}

function executionContract(category) {
	return {
		controlPlane: "DTS_DATA_QUALITY",
		integrationStatus: "REUSE_EXISTING_QUALITY_SERVICE_EXTENSION_REQUIRED",
		triggerStage: category === "FILE" ? "PRE_PUBLISH_STAGING" : "POST_EXTRACT_STAGING",
		bindingSnapshotRef: QUALITY_BINDING_SNAPSHOT.snapshotRef,
		ruleVersionRefs: QUALITY_BINDING_SNAPSHOT.rules.map((rule) => rule.versionRef),
		failureHandling: category === "FILE" ? "BLOCK_ON_QUALITY_FAILURE" : "COUNT_AND_SAMPLE",
		targetDisposition: category === "FILE" ? "CORRECT_AND_RECHECK" : "QUARANTINE_AND_REPLAY_REQUIRES_EXTENSION",
		resultType: "GovQualityRun",
	};
}

function categoryOf(connector) {
	return connector?.category === "FILE" ? "FILE" : connector?.category === "API" ? "API" : "DATABASE";
}

function tabMeta(connection, connector) {
	const category = categoryOf(connector);
	const result = resultFor(connection, category);
	return {
		label: category === "FILE" ? "文件预检" : "异常数据",
		badge: result.failed > 999 ? `${(result.failed / 1000).toFixed(1)}k` : result.failed,
	};
}

function alertAction(message) {
	window.alert(`静态原型：${message}`);
}

function renderOwnership(category) {
	return V("div", {}, [
		V("div", { class: "inline-alert info quality-owner" }, [
			V("b", {}, "质量规则唯一事实源"),
			V("span", {}, "规则、版本、SQL、阈值与运行结果均由数据质量模块管理；接入任务只冻结已发布 GovRuleVersion 引用。"),
			V("button", {
				class: "btn small",
				type: "button",
				onclick: () => alertAction("跳转数据质量模块查看已发布规则版本与数据集绑定"),
			}, "查看数据质量规则"),
		]),
		V("div", { class: "technical-gates" }, [
			V("b", {}, "接入技术门禁"),
			...TECHNICAL_GATES[category].map((gate) => V("span", { class: "technical-gate" }, `✓ ${gate}`)),
			V("span", { class: "muted small" }, "技术门禁不伪装成数据质量规则"),
		]),
	]);
}

function renderSummary(result, category) {
	const stateLabel = result.status === "BLOCKED" ? "阻断发布"
		: result.status === "PARTIAL" ? "部分成功" : result.status === "PASSED" ? "已通过" : "尚未运行";
	return V("div", { class: "quality-summary-bar" }, [
		["检查阶段", category === "FILE" ? "发布前文件预检" : "运行后 staging 校验"],
		["质量运行", result.runId],
		["处理记录", result.processed.toLocaleString("zh-CN")],
		["通过", result.accepted.toLocaleString("zh-CN")],
		[category === "FILE" ? "待修正" : "失败", result.failed.toLocaleString("zh-CN")],
		["结果", stateLabel],
	].map(([label, value]) => V("div", {}, [
		V("span", {}, label),
		V("b", {}, value),
	])));
}

function renderRuleRefs() {
	const rules = QUALITY_BINDING_SNAPSHOT.rules;
	return V("section", { class: "quality-rule-section" }, [
		V("div", { class: "quality-section-head" }, [
			V("b", {}, "数据质量服务返回的绑定快照"),
			V("span", { class: "muted small" }, `${rules.length} 个已发布规则版本 · ${QUALITY_BINDING_SNAPSHOT.snapshotRef}`),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid tight quality-rule-table" }, [
			V("thead", {}, V("tr", {}, ["规则", "版本引用", "绑定引用", "处置等级", "结果归属"].map((item) => V("th", {}, item)))),
			V("tbody", {}, rules.map((rule) => V("tr", {}, [
				V("td", {}, rule.name),
				V("td", {}, V("code", {}, rule.versionRef)),
				V("td", {}, V("code", {}, rule.bindingRef)),
				V("td", {}, V("span", { class: `chip chip-${rule.severity === "阻断" ? "bad" : "warn"}` }, rule.severity)),
				V("td", {}, "GovQualityRun / GovQualityFailingRow"),
			]))),
		])),
	]);
}

function renderFileIssues(connection, result) {
	const editKey = (issue) => `${connection.id}:${result.revisionId}:${result.runId}:${issue.row}:${issue.field}`;
	if (!result.issues.length) {
		return V("div", { class: "quality-empty" }, [
			V("b", {}, "文件 Revision 尚未产生质量预检结果"),
			V("span", {}, "解析进入 staging 后，由数据质量服务按绑定快照执行规则；接入侧不根据文件内容自行判定通过。"),
		]);
	}
	return V("section", { class: "quality-result-section" }, [
		V("div", { class: "quality-section-head" }, [
			V("b", {}, "文件异常修正"),
			V("span", { class: "muted small" }, "静态原型只暂存编辑值；正式重检须调用数据质量服务并生成新的解析快照"),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid quality-issue-table" }, [
			V("thead", {}, V("tr", {}, ["行号", "字段", "当前值", "质量规则", "失败原因", "操作"].map((item) => V("th", {}, item)))),
			V("tbody", {}, result.issues.map((issue) => {
				const rule = ruleByVersionRef(issue.ruleVersionRef);
				return V("tr", { class: "bad-row" }, [
				V("td", { class: "num" }, String(issue.row)),
				V("td", {}, issue.field),
				V("td", {}, V("input", {
					class: "ctl tight",
					value: FILE_EDITS.has(editKey(issue)) ? FILE_EDITS.get(editKey(issue)) : issue.value,
					"aria-label": `${issue.field} 第 ${issue.row} 行`,
					oninput: (event) => FILE_EDITS.set(editKey(issue), event.target.value),
				})),
				V("td", {}, [V("span", { class: "table-primary" }, rule.name), V("code", { class: "table-sub" }, rule.versionRef)]),
				V("td", { class: "muted small" }, issue.reason),
				V("td", {}, V("button", {
					class: "link-btn",
					type: "button",
					onclick: () => alertAction(`模拟使用 ${rule.versionRef} 重检第 ${issue.row} 行；未创建质量运行或解析快照`),
				}, "模拟重检")),
			]);
			})),
		])),
		V("div", { class: "quality-actions" }, [
			V("span", { class: "muted" }, `${result.failed} 行待确认 · 真实质量运行通过后才允许发布文件 Revision`),
			V("span", { class: "spacer" }),
			V("button", { class: "btn ghost", type: "button", onclick: () => alertAction("丢弃当前文件解析批次，保留原始加密制品审计记录") }, "丢弃本批"),
			V("button", { class: "btn primary", type: "button", onclick: () => alertAction("模拟重新调用数据质量服务；静态原型不会修改检查状态") }, "模拟全部重检"),
		]),
	]);
}

function renderRuntimeIssues(result, category) {
	if (!result.issues.length) {
		return V("div", { class: "quality-empty" }, [
			V("b", {}, result.status === "NOT_RUN" ? "任务尚未产生运行数据" : "最近批次未发现异常样例"),
			V("span", {}, "任务创建与发布不执行全量扫描；数据质量规则在同步批次写入 staging 后运行。"),
		]);
	}
	return V("section", { class: "quality-result-section" }, [
		V("div", { class: "quality-section-head" }, [
			V("b", {}, category === "API" ? "API 异常记录" : "数据库异常记录"),
			V("span", { class: "muted small" }, "这里只展示 GovQualityFailingRow 脱敏样例；完整异常隔离与重放仍需扩展既有质量服务"),
		]),
		V("div", { class: "table-scroll" }, V("table", { class: "grid quality-issue-table runtime" }, [
			V("thead", {}, V("tr", {}, ["资源", "记录键", "字段", "脱敏值", "质量规则", "失败原因", "处理"].map((item) => V("th", {}, item)))),
			V("tbody", {}, result.issues.map((issue) => {
				const rule = ruleByVersionRef(issue.ruleVersionRef);
				return V("tr", { class: "bad-row" }, [
					V("td", {}, V("code", {}, issue.resource)),
					V("td", {}, V("code", {}, issue.recordKey)),
					V("td", {}, issue.field),
					V("td", {}, issue.value),
					V("td", {}, [V("span", { class: "table-primary" }, rule.name), V("code", { class: "table-sub" }, rule.versionRef)]),
					V("td", { class: "muted small" }, issue.reason),
					V("td", {}, V("span", { class: "chip chip-warn" }, "失败样例")),
				]);
			})),
		])),
		V("div", { class: "quality-actions" }, [
			V("span", { class: "muted" }, `${result.failed.toLocaleString("zh-CN")} 条失败 · 目标处置：${result.targetPolicy}`),
			V("span", { class: "spacer" }),
			V("button", { class: "btn ghost", type: "button", onclick: () => alertAction("跳转数据质量模块调整绑定；已发布任务继续引用原规则版本") }, "调整规则绑定"),
			V("button", {
				class: "btn primary",
				type: "button",
				disabled: true,
				title: "现有质量执行器仅保存失败样例，完整隔离与重放待后端扩展",
			}, "重放（待接入）"),
		]),
	]);
}

function render(connection, connector) {
	const category = categoryOf(connector);
	const result = resultFor(connection, category);
	return V("div", { class: "quality-checks" }, [
		renderOwnership(category),
		renderSummary(result, category),
		renderRuleRefs(),
		category === "FILE" ? renderFileIssues(connection, result) : renderRuntimeIssues(result, category),
	]);
}

window.QualityChecks = { executionContract, render, tabMeta };

})();
