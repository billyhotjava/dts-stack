/**
 * 新建接入向导外壳。
 *
 * 用户在一个工作台里完成接入；底层仍保持 Connection/Artifact、Resource、
 * AccessPlan、PipelineRevision、Admission 与 Run 等对象独立。
 */

(function () {

const { el, renderSchemaForm, defaultsOf } = window.SchemaForm;
const Admission = window.AdmissionPolicy;
const Runtime = window.WizardRuntime;

const WIZ = {
	step: 0,
	sourceCategory: "DATABASE",
	connectorKey: null,
	values: {},
	tested: null,
	testRequestId: 0,
	resourceState: null,
	loadingResources: false,
	policy: {},
	runtimeOpen: false,
	showPlan: false,
	showCompiled: false,
	revisionId: "rev-draft-1",
};
let sourceCheckSequence = 0;

function resourceKind() {
	return WIZ.connectorKey ? window.ResourceSteps.resourceKind(WIZ.connectorKey) : "DATABASE";
}

function resourceSummary() {
	return WIZ.connectorKey
		? window.ResourceSteps.resourceSummary(WIZ.connectorKey, ensureResourceState())
		: {
				resourceKeys: [],
				resourceLabel: "尚未选择资源",
				count: 0,
				targetHint: "ods.<待推导>",
				estimatedVolume: "待评估",
				admissionDecision: { outcome: "BLOCKED", reasons: ["尚未选择资源"] },
				effectiveClassification: "内部",
				classificationEvidence: [],
			};
}

function stepsForConnector() {
	const kind = resourceKind();
	const resourceStep = WIZ.connectorKey
		? window.ResourceSteps.resourceStepLabel(WIZ.connectorKey)
		: { title: "表与字段", desc: "发现、选择与 ODS 推导" };
	if (kind === "API") {
		return [
			{ title: "API 连接", desc: "端点、TLS 与鉴权" },
			resourceStep,
			{ title: "游标与发布", desc: "checkpoint、准入与 Revision" },
		];
	}
	if (kind === "FILE") {
		return [
			{ title: "上传", desc: "制品、哈希与安全预检" },
			resourceStep,
			{ title: "落地与发布", desc: "批次策略、准入与 Revision" },
		];
	}
	return [
		{ title: "数据库连接", desc: "端点、驱动与权限" },
		resourceStep,
		{ title: "策略与发布", desc: "同步、准入与 Revision" },
	];
}

function sourceConnectors() {
	return window.PROTO.CONNECTORS.filter((connector) =>
		connector.category === WIZ.sourceCategory && !connector.disabled);
}

function selectConnector(connector) {
	WIZ.connectorKey = connector.key;
	WIZ.values = defaultsOf(connector.schema);
	WIZ.tested = null;
	WIZ.testRequestId = ++sourceCheckSequence;
	WIZ.resourceState = window.ResourceSteps.createResourceState(connector.key);
	WIZ.policy = {};
}

function resetWizard(sourceCategory = "DATABASE") {
	WIZ.step = 0;
	WIZ.sourceCategory = sourceCategory;
	WIZ.connectorKey = null;
	WIZ.values = {};
	WIZ.tested = null;
	WIZ.testRequestId = ++sourceCheckSequence;
	WIZ.resourceState = null;
	WIZ.loadingResources = false;
	WIZ.policy = {};
	WIZ.runtimeOpen = false;
	WIZ.showPlan = false;
	WIZ.showCompiled = false;
	WIZ.revisionId = "rev-draft-1";
	if (sourceCategory !== "DATABASE" && sourceConnectors()[0]) selectConnector(sourceConnectors()[0]);
}

function ensureResourceState() {
	if (!WIZ.resourceState && WIZ.connectorKey) {
		WIZ.resourceState = window.ResourceSteps.createResourceState(WIZ.connectorKey);
	}
	return WIZ.resourceState;
}

function renderStepper() {
	return el("div", { class: "stepper" }, stepsForConnector().map((step, index) => el("div", {
		class: `step${index === WIZ.step ? " active" : ""}${index < WIZ.step ? " done" : ""}`,
	}, [
		el("span", { class: "step-no" }, index < WIZ.step ? "✓" : String(index + 1)),
		el("span", { class: "step-text" }, [
			el("b", {}, step.title),
			el("i", {}, step.desc),
		]),
	])));
}

function renderConnectorPicker(rerender) {
	const grid = el("div", { class: "connector-grid" });
	sourceConnectors().forEach((connector) => {
		const selected = WIZ.connectorKey === connector.key;
		const driverBad = connector.driver.status !== "READY" && connector.driver.status !== "NOT_REQUIRED";
		grid.appendChild(el("button", {
			type: "button",
			class: `connector-card${selected ? " selected" : ""}${connector.disabled ? " disabled" : ""}`,
			disabled: connector.disabled,
			onclick: () => {
				if (connector.disabled) return;
				selectConnector(connector);
				rerender();
			},
		}, [
			el("span", { class: "connector-icon" }, connector.icon),
			el("span", { class: "connector-name" }, connector.name),
			el("span", { class: `driver-chip ${connector.driver.status.toLowerCase()}` },
				connector.driver.status === "READY" || connector.driver.status === "NOT_REQUIRED"
					? "运行时就绪"
					: connector.driver.status === "MISSING" ? "缺厂商驱动" : "规划中"),
			driverBad && !connector.disabled
				? el("span", { class: "connector-fix" }, "需管理员处理")
				: null,
		]));
	});

	return el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "选择数据库类型"),
			el("span", { class: "panel-note" }, "数据库连接器契约决定字段、能力与可用执行适配器"),
		]),
		grid,
	]);
}

function sourceCheckLabels(kind) {
	if (kind === "FILE") {
		return { button: "加密上传并预检", waiting: "正在校验文件制品…", idle: "完成预检后才能进入解析步骤" };
	}
	if (kind === "API") {
		return { button: "验证连接与鉴权", waiting: "正在验证端点与鉴权…", idle: "验证后再定义具体 API 资源" };
	}
	return { button: "分层测试连接", waiting: "正在检查网络、认证与读取权限…", idle: "测通后才能发现表结构" };
}
function secureApiUrl(value) {
	try {
		const url = new URL(String(value || "").trim());
		return url.protocol === "https:" && Boolean(url.hostname) && !url.username && !url.password;
	} catch {
		return false;
	}
}
function missingRequiredFields(connector) {
	return connector.schema.flatMap((group) => group.fields).filter((field) => {
		const applies = !field.when || Object.entries(field.when).every(([key, expected]) => {
			const actual = WIZ.values[key];
			return Array.isArray(expected) ? expected.includes(actual) : actual === expected;
		});
		const value = WIZ.values[field.name];
		return applies && field.required && (value === undefined || value === null || value === ""
			|| (Array.isArray(value) && value.length === 0));
	});
}
function renderTestBlock(rerender) {
	const tested = WIZ.tested;
	const connector = window.PROTO.connectorByKey(WIZ.connectorKey);
	const kind = resourceKind();
	const labels = sourceCheckLabels(kind);
	const run = () => {
		const missing = missingRequiredFields(connector);
		if (missing.length) {
			WIZ.tested = { ok: false, ms: 0, msg: `请先填写：${missing.map((field) => field.label).join("、")}` };
			rerender();
			return;
		}
		const testedConnectorKey = WIZ.connectorKey;
		const requestId = ++sourceCheckSequence;
		WIZ.testRequestId = requestId;
		WIZ.tested = "testing";
		rerender();
		setTimeout(() => {
			if (WIZ.testRequestId !== requestId || WIZ.connectorKey !== testedConnectorKey) return;
			const driverReady = connector.driver.status === "READY" || connector.driver.status === "NOT_REQUIRED";
			const apiTransportBlocked = kind === "API" && (Boolean(WIZ.values.allowPlainHttp) || !secureApiUrl(WIZ.values.baseUrl));
			const msg = !driverReady
				? "运行时缺少管理员提供的驱动，不能发布"
				: apiTransportBlocked
					? "API Base URL 必须使用有效 HTTPS；当前未绑定外部例外决定"
					: kind === "FILE"
					? "制品已进入加密暂存区；哈希、封条和解析规则将在下一步确认"
					: kind === "API"
						? "Base URL 可达，鉴权配置格式有效；下一步发送真实样例请求"
						: "DNS、TCP、认证、SELECT 与元数据读取权限均通过";
			WIZ.tested = { ok: driverReady && !apiTransportBlocked, ms: kind === "FILE" ? 86 : 412, msg };
			rerender();
		}, 700);
	};
	return el("div", { class: "test-block" }, [
		el("button", {
			class: "btn primary",
			type: "button",
			disabled: tested === "testing",
			onclick: run,
		}, tested === "testing" ? "检查中…" : labels.button),
		tested === "testing" ? el("span", { class: "muted" }, labels.waiting) : null,
		tested && tested !== "testing"
			? el("div", { class: `test-result ${tested.ok ? "ok" : "bad"}` }, [
					el("b", {}, tested.ok ? "✓ 来源预检通过" : "✗ 来源预检失败"),
					el("span", {}, `${tested.msg} · ${tested.ms}ms`),
				])
			: null,
		!tested ? el("span", { class: "muted" }, labels.idle) : null,
	]);
}

function renderStepConnect(rerender) {
	const connector = window.PROTO.connectorByKey(WIZ.connectorKey);
	const body = el("div", { class: "step-body" }, WIZ.sourceCategory === "DATABASE"
		? [renderConnectorPicker(rerender)]
		: [el("div", { class: "source-locked" }, [
				el("span", { class: `source-kind ${WIZ.sourceCategory.toLowerCase()}` }, WIZ.sourceCategory === "API" ? "API" : "离线文件"),
				el("div", {}, [
					el("b", {}, connector?.name || "内置连接器"),
					el("span", {}, "当前入口已限定接入方式，无需再次选择类型。"),
				]),
			])]);
	if (!connector) {
		body.appendChild(el("div", { class: "empty" }, "请先选择一个来源类型"));
		return body;
	}

	const kind = resourceKind();
	const panel = el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, `${connector.name} ${kind === "FILE" ? "来源" : "连接"}参数`),
			el("span", { class: "panel-note" },
				`由连接器 contract 渲染 · ${connector.schema.reduce((count, group) => count + group.fields.length, 0)} 个连接级字段`),
		]),
	]);

	if (connector.driver.status === "MISSING") {
		panel.appendChild(el("div", { class: "inline-alert warn" }, [
			el("b", {}, "运行时未就绪"),
			el("span", {}, "该厂商驱动因授权不能随镜像分发，需管理员登记并校验；普通创建者不负责上传。"),
			el("button", { class: "btn small", type: "button", disabled: true }, "等待管理员处理"),
			]));
		}
	if (connector.replaces) {
		panel.appendChild(el("div", { class: "inline-alert info" }, [
			el("b", {}, "配置契约"),
			el("span", {}, `结构化字段替代 ${connector.replaces.length} 个裸 JSON：${connector.replaces.join("、")}`),
		]));
		}

	const sourceValuesBeforeRender = JSON.stringify(WIZ.values, (key, value) => key === "__collapsed" ? undefined : value);
	panel.appendChild(renderSchemaForm(connector.schema, WIZ.values, () => {
		const sourceValuesNow = JSON.stringify(WIZ.values, (key, value) => key === "__collapsed" ? undefined : value);
		if (sourceValuesNow !== sourceValuesBeforeRender) {
			WIZ.tested = null;
			WIZ.testRequestId = ++sourceCheckSequence;
		}
		rerender();
	}));
	panel.appendChild(renderTestBlock(rerender));
	body.appendChild(panel);
	return body;
}

function renderStepResources(rerender) {
	if (WIZ.loadingResources) {
		const text = resourceKind() === "FILE" ? "正在解析文件样例…" : resourceKind() === "API" ? "正在准备样例请求…" : "正在读取源端结构…";
		return el("div", { class: "step-body" }, el("div", { class: "empty" }, text));
	}
	return window.ResourceSteps.renderResourceStep(
		WIZ.connectorKey,
		ensureResourceState(),
		rerender,
	);
}

function modeCompatibility(mode) {
	const state = ensureResourceState();
	if (resourceKind() === "DATABASE") {
		const selected = (state.tables || []).filter((table) => table.selected);
		if (mode === "TIMESTAMP_INCREMENTAL") {
			const unsupported = selected.filter((table) => !table.incrementalCol);
			if (unsupported.length) return { ok: false, reason: `${unsupported.map((table) => table.name).join("、")} 无增量时间列` };
		}
		if (mode === "PRIMARY_KEY_INCREMENTAL") {
			const unsupported = selected.filter((table) => !table.pk);
			if (unsupported.length) return { ok: false, reason: `${unsupported.map((table) => table.name).join("、")} 无主键` };
		}
	}
	if (resourceKind() === "API" && mode === "CURSOR_INCREMENTAL"
		&& (!state.pagination || state.pagination.type !== "cursor")) {
		return { ok: false, reason: "当前 API 资源未配置游标分页" };
	}
	return { ok: true, reason: "" };
}

function modesForSource() {
	const connector = window.PROTO.connectorByKey(WIZ.connectorKey);
	const presentations = {
		FULL_REFRESH: ["全量快照", "先写暂存表，校验后原子切换"],
		TIMESTAMP_INCREMENTAL: ["时间戳增量", "逐资源 checkpoint，目标端按主键合并"],
		PRIMARY_KEY_INCREMENTAL: ["主键增量", "按递增主键推进独立 checkpoint"],
		APPEND_BATCH: ["追加新批次", "以批次号保证幂等与可追溯"],
		REPLACE_BATCH: ["替换当前快照", "先写暂存表，校验后原子切换"],
		CURSOR_INCREMENTAL: ["游标增量", "每个 API 资源独立提交 checkpoint"],
		CDC: ["CDC 日志捕获", "独立运行时、状态存储与 schema 演进"],
	};
	const preferred = resourceKind() === "API" ? "CURSOR_INCREMENTAL"
		: resourceKind() === "FILE" ? "REPLACE_BATCH" : "TIMESTAMP_INCREMENTAL";
	const bindings = connector.executionBindings || [];
	const modes = bindings.map((binding) => {
		const presentation = presentations[binding.mode] || [binding.mode, "由连接器 contract 声明"];
		const compatibility = modeCompatibility(binding.mode);
		const enabled = binding.status === "READY" && compatibility.ok;
		const reason = !compatibility.ok ? compatibility.reason
			: binding.status === "POC" ? "受控 PoC，当前不可发布"
				: binding.status === "PLANNED" ? "规划能力，当前不可发布"
					: binding.status === "BLOCKED_DRIVER" ? "缺少管理员提供的厂商驱动" : "";
		return {
			key: binding.mode,
			contract: binding.mode,
			title: presentation[0],
			desc: presentation[1],
			adapter: binding.adapter,
			enabled,
			recommended: false,
			reason,
		};
	});
	const recommended = modes.find((mode) => mode.enabled && mode.key === preferred)
		|| modes.find((mode) => mode.enabled);
	modes.forEach((mode) => { mode.recommended = Boolean(recommended && mode.key === recommended.key); });
	return modes;
}

function selectedMode() {
	const modes = modesForSource();
	const chosen = modes.find((mode) => mode.key === WIZ.policy.syncMode && mode.enabled)
		|| modes.find((mode) => mode.recommended && mode.enabled)
		|| modes.find((mode) => mode.enabled)
		|| modes[0];
	if (chosen && chosen.enabled) WIZ.policy.syncMode = chosen.key;
	return chosen;
}

function renderModeCards(rerender) {
	const chosen = selectedMode();
	return el("section", { class: "panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, resourceKind() === "FILE" ? "批次落地策略" : "同步方式"),
			el("span", { class: "panel-note" }, "能力来自 connector contract 与当前 runtime binding"),
		]),
		el("div", { class: "mode-grid" }, modesForSource().map((mode) => el("button", {
			type: "button",
			class: `mode-card${chosen.key === mode.key ? " selected" : ""}${mode.enabled ? "" : " disabled"}`,
			disabled: !mode.enabled,
			onclick: () => { WIZ.policy.syncMode = mode.key; rerender(); },
		}, [
			el("b", {}, [
				mode.title,
				mode.recommended ? el("span", { class: "chip chip-ok" }, "推荐") : null,
			]),
			el("span", {}, mode.desc),
			!mode.enabled ? el("span", { class: "chip chip-warn" }, mode.reason) : null,
		]))),
	]);
}

function renderTargetLine() {
	const summary = resourceSummary();
	return el("div", { class: "target-line" }, [
		el("span", { class: "muted small" }, "写入目标"),
		el("code", {}, "DestinationProfile: ods-default@3"),
		el("span", { class: "arrow" }, "→"),
		el("code", { class: "strong" }, summary.targetHint),
		el("span", { class: "chip chip-inherit" }, "平台托管"),
		el("span", { class: "muted small" }, "任务只保存目标策略与 secret 引用"),
	]);
}

function renderAdmission(rerender) {
	const summary = resourceSummary();
	const state = Admission.presentation(summary);
	const confirmation = Admission.confirmationFor(WIZ.revisionId, summary, WIZ.policy.confirmation);
	if (WIZ.policy.confirmation && !confirmation) WIZ.policy.confirmation = null;
	const evidence = [...summary.classificationEvidence, ...(state.decision.reasons || [])];
	const visibleEvidence = evidence.length
		? evidence
		: ["连接默认下限：内部", "资源样例识别：未发现更高等级字段"];
	return el("section", { class: "panel admission-panel" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "密级准入依据"),
			el("span", { class: `chip chip-${state.tone}` }, state.label),
		]),
		el("div", { class: "evidence-list" }, visibleEvidence.map((item) =>
			el("div", { class: "evidence-item" }, [el("span", { class: "check-dot" }, "✓"), el("span", {}, item)]))),
		el("div", { class: "admission-result" }, [
			el("span", { class: "muted" }, "计算后的有效密级"),
			el("b", {}, summary.effectiveClassification),
			el("span", { class: "chip chip-inherit" }, "冻结为 AdmissionDecision"),
		]),
		state.decision.outcome === "CONFIRMATION_REQUIRED" ? el("div", { class: "form-item" }, [
			el("label", { class: "form-label" }, "风险确认理由"),
			el("textarea", {
				class: "ctl", rows: 2, placeholder: "说明影响范围、接受原因和回退方式（至少 8 个字符）",
				oninput: (event) => { WIZ.policy.confirmation = Admission.recordConfirmation(WIZ.revisionId, summary, event.target.value); const button = document.getElementById("publish-revision"); if (button) button.disabled = !canAdvance(); },
				onchange: () => setTimeout(rerender, 0),
			}, confirmation?.reason || ""),
		]) : null,
		el("div", { class: "foot-note" },
			"默认不依赖组织审批：规则通过可直接发布，风险项记录确认理由，硬阻断不可绕过；仅客户已绑定 OA/BPM 时进入外部审批。密级降低默认阻断。"),
	]);
}

function executionAdapter(mode) {
	return mode.adapter || "UNBOUND_ADAPTER";
}

function buildExecutionPlan() {
	const summary = resourceSummary();
	const mode = selectedMode();
	const sourceType = resourceKind() === "FILE" ? "ARTIFACT" : "CONNECTION";
	const checkpointed = mode.contract.includes("INCREMENTAL") || mode.contract === "APPEND_BATCH";
	return {
		contractVersion: "dts.execution-plan/v1",
		revisionId: WIZ.revisionId,
		connectorContractRef: `${WIZ.connectorKey}@3`,
		sourceRef: `${sourceType.toLowerCase()}://draft-source`,
		resources: summary.resourceKeys,
		schemaSnapshotRef: `schema://${WIZ.revisionId}`,
		mode: mode.contract,
		checkpointPolicy: checkpointed
			? { scope: "PER_RESOURCE", commit: "AFTER_TARGET_COMMIT" }
			: { scope: "PER_RUN", marker: "BATCH_ID" },
		targetPolicyRef: "destination://ods-default@3",
			targetWritePolicy: mode.contract === "FULL_REFRESH" || mode.contract === "REPLACE_BATCH"
				? "STAGING_VALIDATE_ATOMIC_SWAP"
				: "IDEMPOTENT_MERGE",
			schedule: WIZ.policy.schedule || scheduleOptions()[0],
			defaultProfileRef: Runtime.PROFILE.ref,
			runtimeOverrides: { ...Runtime.overrides(WIZ) },
			effectiveRuntimePolicy: { ...Runtime.policy(WIZ) },
			qualityGate: window.QualityChecks.executionContract(resourceKind()),
			admissionSealRef: `admission://${WIZ.revisionId}`,
		secretRefs: sourceType === "CONNECTION" ? ["secret://connection/draft"] : [],
		executionAdapter: executionAdapter(mode),
		orchestrator: "AIRFLOW_REVISION_RUNNER",
		postLandingEvent: "DBT_SOURCE_READY",
	};
}

function compiledArtifact() {
	const plan = buildExecutionPlan();
	if (plan.executionAdapter === "ADDAX_BATCH") {
		return {
			adapter: "ADDAX_BATCH",
			generatedFrom: plan.revisionId,
			job: {
				reader: { name: `${WIZ.connectorKey}reader`, resources: plan.resources, credential: plan.secretRefs[0] },
				writer: { name: "inceptorwriter", targetPolicyRef: plan.targetPolicyRef, writePolicy: plan.targetWritePolicy },
			},
		};
	}
	if (plan.executionAdapter === "DTS_API_HTTP") {
		return {
			adapter: "DTS_API_HTTP",
			generatedFrom: plan.revisionId,
			requestContractRef: plan.resources[0],
			checkpoint: plan.checkpointPolicy,
			credential: plan.secretRefs[0],
		};
	}
	return {
		adapter: "DTS_NATIVE_FILE",
		generatedFrom: plan.revisionId,
		artifactRef: plan.resources[0],
		parseSchemaRef: plan.schemaSnapshotRef,
		writerPolicy: plan.targetWritePolicy,
	};
}

function renderExecutionPlan(rerender) {
	return el("section", { class: "panel execution-plan" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "版本化执行计划"),
			el("span", { class: "panel-note" }, "标准契约是事实来源，引擎配置只是可重建编译产物"),
		]),
		el("button", {
			class: "link-btn",
			type: "button",
			onclick: () => { WIZ.showPlan = !WIZ.showPlan; rerender(); },
		}, WIZ.showPlan ? "收起 ExecutionPlan" : "查看 ExecutionPlan（只读）"),
		WIZ.showPlan ? el("pre", { class: "code-block" }, JSON.stringify(buildExecutionPlan(), null, 2)) : null,
		WIZ.showPlan ? el("button", {
			class: "link-btn compiled-toggle",
			type: "button",
			onclick: () => { WIZ.showCompiled = !WIZ.showCompiled; rerender(); },
		}, WIZ.showCompiled ? "收起适配器编译产物" : `查看 ${executionAdapter(selectedMode())} 编译产物`) : null,
		WIZ.showPlan && WIZ.showCompiled
			? el("pre", { class: "code-block secondary" }, JSON.stringify(compiledArtifact(), null, 2))
			: null,
	]);
}

function renderPublishChecks() {
	const connector = window.PROTO.connectorByKey(WIZ.connectorKey);
	const summary = resourceSummary();
	const gate = Admission.publishGate(WIZ.revisionId, summary, WIZ.policy.confirmation);
	const checks = [
		["来源预检", "连接/制品检查在有效期内", true],
		["运行时", connector.driver.status === "READY" || connector.driver.status === "NOT_REQUIRED" ? "驱动与适配器就绪" : "缺少管理员驱动", connector.driver.status === "READY" || connector.driver.status === "NOT_REQUIRED"],
		["资源契约", `${summary.count} 个资源的 Schema 已冻结`, summary.count > 0],
		["目标影响", "覆盖类写入采用 staging 校验后原子切换", true],
		["密级准入", gate.detail, gate.ok],
		["Secret", resourceKind() === "FILE" ? "无连接密码" : "仅保存 secretRef，不进入计划正文", true],
		["重跑语义", "checkpoint 在目标提交成功后原子推进", true],
	];
	return el("section", { class: "panel publish-checks" }, [
		el("div", { class: "panel-head" }, [
			el("h3", {}, "发布前检查"),
			el("span", { class: "panel-note" }, `${WIZ.revisionId} 不会覆盖当前生效版本`),
		]),
		el("div", { class: "check-grid" }, checks.map(([name, detail, ok]) => el("div", {
			class: `check-card ${ok ? "ok" : "warn"}`,
		}, [
			el("b", {}, `${ok ? "✓" : "!"} ${name}`),
			el("span", {}, detail),
		]))),
	]);
}

function defaultName() {
	if (resourceKind() === "API") return "订单 API 入湖";
	if (resourceKind() === "FILE") return "离线文件批次入湖";
	return "人事主数据库入湖";
}

function scheduleOptions() {
	if (resourceKind() === "FILE") return ["上传后手动发布", "上传并准入后自动执行"];
	if (resourceKind() === "API") return ["每 15 分钟", "每小时", "每日 02:00", "仅手动"];
	return ["每日 02:00", "每 4 小时", "每周一 03:00", "仅手动"];
}

function renderStepPolicy(rerender) {
	const summary = resourceSummary();
	const mode = selectedMode();
	const schedules = scheduleOptions();
	const selectedSchedule = WIZ.policy.schedule || schedules[0];
	return el("div", { class: "step-body" }, [
		renderModeCards(rerender),
		el("section", { class: "panel" }, [
			el("div", { class: "panel-head" }, [
				el("h3", {}, "接入计划"),
				el("span", { class: "panel-note" }, "保存为新 Revision，不原地改写已发布配置"),
			]),
			el("div", { class: "revision-banner" }, [
				el("div", {}, [el("span", { class: "muted small" }, "当前编辑"), el("b", {}, "Revision 1 · DRAFT")]),
				el("span", {}, "→"),
				el("div", {}, [el("span", { class: "muted small" }, "发布后"), el("b", {}, "Revision 1 · PUBLISHED")]),
			]),
			el("div", { class: "form-grid" }, [
				el("div", { class: "form-item" }, [
					el("label", { class: "form-label" }, "接入名称"),
						el("input", {
							class: "ctl",
							"aria-label": "接入名称",
							value: WIZ.policy.name || defaultName(),
						oninput: (event) => { WIZ.policy.name = event.target.value; },
					}),
				]),
				el("div", { class: "form-item" }, [
					el("label", { class: "form-label" }, "调度"),
						el("select", {
							class: "ctl",
							"aria-label": "调度",
							onchange: (event) => { WIZ.policy.schedule = event.target.value; },
					}, schedules.map((schedule) => el("option", {
						value: schedule,
						selected: schedule === selectedSchedule,
					}, schedule))),
				]),
			]),
			renderTargetLine(),
		]),
		Runtime.renderSummary(WIZ, rerender),
		renderAdmission(rerender),
		el("section", { class: "panel" }, [
			el("div", { class: "panel-head" }, [
				el("h3", {}, "确认范围"),
				el("span", { class: "panel-note" }, "工作台聚合展示，连接与计划仍可独立复用和版本化"),
			]),
			el("div", { class: "summary" }, [
				["连接器", window.PROTO.connectorByKey(WIZ.connectorKey).name],
				["资源", summary.resourceLabel],
				["同步方式", mode.title],
				["目标", summary.targetHint],
				["有效密级", summary.effectiveClassification],
				["预计首轮", summary.estimatedVolume],
			].map(([key, value]) => el("div", { class: "summary-item" }, [
				el("span", { class: "muted" }, key),
				el("b", {}, String(value)),
			]))),
			el("div", { class: "decision-count" }, [
				el("b", {}, "业务决策与平台默认已分层："),
				el("span", {}, "创建者确认来源、资源、同步方式、调度和名称；凭据、目标、密级计算与运行参数由引用或准入证据承载。"),
			]),
		]),
		renderExecutionPlan(rerender),
		renderPublishChecks(),
	]);
}

function canAdvance() {
	if (WIZ.step === 0) {
		return Boolean(WIZ.tested && WIZ.tested !== "testing" && WIZ.tested.ok);
	}
	if (WIZ.step === 1) {
		return window.ResourceSteps.validateResourceStep(WIZ.connectorKey, ensureResourceState()).ok;
	}
	const mode = selectedMode();
	const summary = resourceSummary();
	return Boolean(mode && mode.enabled
		&& Admission.publishGate(WIZ.revisionId, summary, WIZ.policy.confirmation).ok);
}

function blockReason() {
	if (canAdvance()) return null;
	if (WIZ.step === 0) return resourceKind() === "FILE" ? "请先完成文件预检" : "请先完成来源连接预检";
	if (WIZ.step === 1) {
		return window.ResourceSteps.validateResourceStep(WIZ.connectorKey, ensureResourceState()).reason;
	}
	if (WIZ.step === 2) {
		const summary = resourceSummary();
		const gate = Admission.publishGate(WIZ.revisionId, summary, WIZ.policy.confirmation);
		return gate.reason || "当前资源没有可发布的执行模式";
	}
	return null;
}

function nextButtonLabel() {
	if (WIZ.step === 0) return `下一步：${stepsForConnector()[1].title}`;
	return "下一步：准入与发布";
}

function publishButtonLabel(summary) {
	const outcome = Admission.resolve(summary).outcome;
	if (outcome === "CONFIRMATION_REQUIRED") return "记录确认并发布 Revision 1";
	if (outcome === "PENDING_EXTERNAL_APPROVAL") return "等待外部流程";
	if (outcome === "ADMITTED") return "已准入（不可重复发布）";
	if (outcome === "BLOCKED") return "准入阻断";
	return "确认准入并发布 Revision 1";
}

function renderWizard(nav, rerender) {
	const sourceMeta = window.AccessPages.SOURCE_META[WIZ.sourceCategory];
	const body = WIZ.step === 0
		? renderStepConnect(rerender)
		: WIZ.step === 1
			? renderStepResources(rerender)
			: renderStepPolicy(rerender);
	const reason = blockReason();
	const summary = WIZ.step === 2 ? resourceSummary() : null;

	return el("div", { class: "wizard" }, [
		el("div", { class: "page-head" }, [
			el("div", {}, [
				el("h2", {}, `新建${sourceMeta.title}`),
				el("p", { class: "muted" }, `${sourceMeta.desc} 发布时生成不可变 Revision 与有效配置快照。`),
			]),
			el("button", { class: "btn ghost", onclick: () => nav(sourceMeta.route) }, "取消"),
		]),
		renderStepper(),
		body,
		el("div", { class: "wizard-foot" }, [
			reason ? el("span", { class: "muted" }, reason) : null,
			el("div", { class: "spacer" }),
			WIZ.step > 0
				? el("button", { class: "btn ghost", onclick: () => { WIZ.step -= 1; rerender(); } }, "上一步")
				: null,
			...(WIZ.step < 2
				? [el("button", {
						class: "btn primary",
						disabled: !canAdvance(),
						onclick: () => {
							if (!canAdvance()) return;
							if (WIZ.step === 0) {
								WIZ.loadingResources = true;
								WIZ.step = 1;
								rerender();
								setTimeout(() => {
									WIZ.loadingResources = false;
									ensureResourceState();
									rerender();
								}, 500);
								return;
							}
							WIZ.step += 1;
							rerender();
						},
					}, nextButtonLabel())]
					: [
							el("button", { class: "btn ghost", onclick: () => nav(sourceMeta.route) }, "保存草稿"),
							el("button", {
								class: "btn primary", id: "publish-revision",
								disabled: !canAdvance(),
								title: "静态原型不创建真实记录，完成后返回工作台",
								onclick: () => nav(sourceMeta.route),
							},
								publishButtonLabel(summary)),
						]),
		]),
	]);
}

window.Wizard = { renderWizard, resetWizard, WIZ };

})();
