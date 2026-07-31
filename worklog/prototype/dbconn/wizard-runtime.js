/**
 * 平台运行默认与任务 Revision 稀疏覆盖。
 */

(function () {

const V = window.SchemaForm.el;
const CONTROLS = [
	["retryPolicy", "失败重试", [["EXPONENTIAL_3", "最多 3 次，指数退避"], ["NONE", "不重试"]]],
	["dirtyDataPolicy", "脏数据策略", [["STAGING_REVIEW", "进入 staging 待确认"], ["SKIP_AND_LOG", "跳过并记录"], ["FAIL_FAST", "立即中止"]]],
	["conflictPolicy", "并发冲突", [["QUEUE_10M", "排队，最长 10 分钟"], ["REJECT", "直接拒绝"]]],
];
const PROFILE = {
	ref: "platform-default@3",
	runtime: {
		retryPolicy: "EXPONENTIAL_3",
		dirtyDataPolicy: "STAGING_REVIEW",
		conflictPolicy: "QUEUE_10M",
		taskConcurrency: 1,
		timeZone: "Asia/Shanghai",
		preflight: true,
	},
	parameters: [
		{ scope: "通用", key: "retryPolicy", label: "失败重试", value: "最多 3 次，指数退避", owner: "任务 Revision", override: "允许", version: "platform-default@3" },
		{ scope: "通用", key: "dirtyDataPolicy", label: "脏数据策略", value: "进入 staging 待确认", owner: "任务 Revision", override: "允许", version: "platform-default@3" },
		{ scope: "通用", key: "conflictPolicy", label: "并发冲突", value: "排队，最长 10 分钟", owner: "任务 Revision", override: "允许", version: "platform-default@3" },
		{ scope: "通用", key: "timeZone", label: "业务时区", value: "Asia/Shanghai", owner: "平台", override: "禁止", version: "platform-default@3" },
		{ scope: "数据库", key: "connectTimeoutMs", label: "连接超时", value: "10,000 ms", owner: "连接", override: "允许", version: "jdbc-default@2" },
		{ scope: "数据库", key: "queryTimeoutSec", label: "查询超时", value: "300 秒", owner: "连接 / 任务", override: "受限", version: "jdbc-default@2" },
		{ scope: "API", key: "rateLimitQps", label: "默认限流", value: "5 req/s", owner: "连接", override: "受限", version: "api-default@2" },
		{ scope: "API", key: "timeoutMs", label: "读取超时", value: "30,000 ms", owner: "连接 / 任务", override: "受限", version: "api-default@2" },
		{ scope: "离线文件", key: "batchMode", label: "批次落地", value: "替换当前快照", owner: "任务 Revision", override: "允许", version: "file-default@2" },
		{ scope: "离线文件", key: "maxFileSize", label: "单文件上限", value: "200 MB", owner: "平台", override: "禁止", version: "file-default@2" },
	],
};

function overrides(state) {
	return state.policy.runtimeOverrides || {};
}

function policy(state) {
	return { ...PROFILE.runtime, ...overrides(state) };
}

function setOverride(state, key, value) {
	const next = { ...overrides(state) };
	if (value === PROFILE.runtime[key]) delete next[key];
	else next[key] = value;
	state.policy.runtimeOverrides = next;
}

function summaryItems(state) {
	const current = policy(state);
	const items = CONTROLS.map(([key, label, options]) => {
		const selected = options.find(([value]) => value === current[key]) || options[0];
		return [label, selected[1]];
	});
	return items.concat([
		["本任务并发", String(current.taskConcurrency)],
		["时区", current.timeZone],
		["落地前预检", current.preflight ? "开启" : "关闭"],
	]);
}

function renderControls(state, rerender) {
	const current = policy(state);
	return V("div", { class: "runtime-detail" }, [
		V("div", { class: "inline-alert info" }, [
			V("b", {}, "仅保存偏离平台默认的参数"),
			V("span", {}, "发布时冻结有效配置快照，再由编译器转换为 Addax、API 或 File Adapter 的运行参数。"),
		]),
		V("div", { class: "form-grid" }, [
			...CONTROLS.map(([key, label, options]) => V("div", { class: "form-item" }, [
				V("label", { class: "form-label" }, label),
				V("select", {
					class: "ctl",
					"aria-label": label,
					onchange: (event) => { setOverride(state, key, event.target.value); rerender(); },
				}, options.map(([value, text]) => V("option", {
					value,
					selected: current[key] === value,
				}, text))),
			])),
			V("div", { class: "form-item" }, [
				V("label", { class: "form-label" }, "本任务并发"),
				V("div", { class: "ctl-wrap" }, [
					V("input", {
						class: "ctl",
						type: "number",
						value: current.taskConcurrency,
						"aria-label": "本任务并发",
						min: 1,
						max: 6,
						onchange: (event) => {
							setOverride(state, "taskConcurrency",
								Math.max(1, Math.min(6, Number(event.target.value) || 1)));
							rerender();
						},
					}),
					V("span", { class: "suffix" }, "/ 连接配额 6"),
				]),
			]),
		]),
	]);
}

function renderSummary(state, rerender) {
	const summary = summaryItems(state);
	const overrideCount = Object.keys(overrides(state)).length;
	const panel = V("section", { class: "panel runtime-summary" }, [
		V("div", { class: "runtime-head" }, [
			V("div", {}, [
				V("b", {}, "运行默认策略"),
				V("span", { class: "chip chip-inherit" }, `继承 ${PROFILE.ref} · ${overrideCount} 项覆盖`),
			]),
			V("button", {
				class: "btn small",
				type: "button",
				onclick: () => { state.runtimeOpen = !state.runtimeOpen; rerender(); },
			}, state.runtimeOpen ? "收起" : "高级覆盖"),
		]),
		V("div", { class: "runtime-chips" }, summary.map(([key, value]) =>
			V("span", { class: "runtime-chip" }, [V("i", {}, key), V("b", {}, value)]))),
	]);
	if (state.runtimeOpen) panel.appendChild(renderControls(state, rerender));
	return panel;
}

window.WizardRuntime = { PROFILE, overrides, policy, renderSummary };

})();
