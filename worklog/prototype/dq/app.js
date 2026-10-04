/**
 * DTS 数据质量原型外壳与无依赖路由。
 * 该目录仅用于产品原型，不连接 DTS API，也不持久化任何操作。
 */

(function () {
	"use strict";

	var DQ = (window.DQPrototype = window.DQPrototype || {});
	DQ.views = DQ.views || {};

	var NAV_GROUPS = [
		{ label: "质量概览", items: [{ key: "overview", label: "质量大盘", icon: "总" }] },
		{
			label: "质量资产",
			items: [
				{ key: "rule-list", label: "规则列表", icon: "规" },
				{ key: "rule-template", label: "规则模板库", icon: "模" },
			],
		},
		{
			label: "规则配置",
			items: [
				{ key: "rule-by-table", label: "按表配置", icon: "表" },
				{ key: "rule-by-template", label: "按模板配置", icon: "配" },
			],
		},
		{
			label: "质量运维",
			items: [
				{ key: "monitor", label: "质量监控", icon: "监" },
				{ key: "run-records", label: "运行记录", icon: "运" },
			],
		},
		{ label: "质量分析", items: [{ key: "report", label: "质量报告", icon: "报" }] },
	];

	var SECONDARY_PARENT = {
		"rule-detail": "rule-list",
		"rule-editor": "rule-list",
		"template-detail": "rule-template",
		"table-detail": "rule-by-table",
		"batch-wizard": "rule-by-template",
		"monitor-detail": "monitor",
		"monitor-editor": "monitor",
		"run-detail": "run-records",
		noise: "monitor",
		"report-editor": "report",
		"report-preview": "report",
	};

	var ROUTE_META = {};
	NAV_GROUPS.forEach(function (group) {
		group.items.forEach(function (item) {
			ROUTE_META[item.key] = { label: item.label, group: group.label };
		});
	});
	Object.assign(ROUTE_META, {
		"rule-detail": { label: "规则详情", group: "质量资产" },
		"rule-editor": { label: "新建质量规则", group: "质量资产" },
		"template-detail": { label: "模板详情", group: "质量资产" },
		"table-detail": { label: "表质量详情", group: "规则配置" },
		"batch-wizard": { label: "批量配置规则", group: "规则配置" },
		"monitor-detail": { label: "质量监控详情", group: "质量运维" },
		"monitor-editor": { label: "配置质量监控", group: "质量运维" },
		"run-detail": { label: "运行详情", group: "质量运维" },
		noise: { label: "去噪管理", group: "质量运维" },
		"report-editor": { label: "报告模板编辑", group: "质量分析" },
		"report-preview": { label: "质量报告预览", group: "质量分析" },
	});

	var STATE = {
		route: routeFromHash(),
		period: "7d",
		view: "rule",
		productionOnly: true,
		openGroups: {},
	};
	NAV_GROUPS.forEach(function (group) {
		STATE.openGroups[group.label] = true;
	});

	function routeFromHash() {
		var value = window.location.hash.replace(/^#\/?/, "").split("?")[0];
		return value && (ROUTE_META[value] || (DQ.views && DQ.views[value])) ? value : "overview";
	}

	function parentRoute(route) {
		return SECONDARY_PARENT[route] || route;
	}

	function navigate(route) {
		if (!DQ.views[route]) {
			showToast("该原型页面正在补充中", "warning");
			return;
		}
		STATE.route = route;
		var nextHash = "#/" + route;
		if (window.location.hash !== nextHash) window.history.pushState(null, "", nextHash);
		render();
		window.scrollTo(0, 0);
	}

	function renderNavigation() {
		var active = parentRoute(STATE.route);
		return NAV_GROUPS.map(function (group) {
			var open = STATE.openGroups[group.label];
			var items = group.items
				.map(function (item) {
					return (
						'<button type="button" class="nav-item' +
						(active === item.key ? " active" : "") +
						'" data-route="' +
						item.key +
						'"><span class="nav-icon">' +
						item.icon +
						'</span><span>' +
						item.label +
						"</span></button>"
					);
				})
				.join("");
			return (
				'<section class="nav-group' +
				(open ? " open" : "") +
				'"><button type="button" class="nav-group-title" data-group="' +
				group.label +
				'" aria-expanded="' +
				String(open) +
				'"><span>' +
				group.label +
				'</span><span class="nav-caret">⌄</span></button><div class="nav-group-items">' +
				items +
				"</div></section>"
			);
		}).join("");
	}

	function renderShell(content) {
		var meta = ROUTE_META[STATE.route] || ROUTE_META.overview;
		return (
			'<div class="app-shell">' +
			'<aside class="sidebar">' +
			'<div class="brand"><span class="brand-mark">D</span><span><b>DTS 数据平台</b><small>数据治理 · 数据质量</small></span></div>' +
			'<nav class="side-navigation" aria-label="数据质量导航">' +
			renderNavigation() +
			'</nav><div class="sidebar-foot"><span class="health-dot"></span>原型环境 · 合成数据</div></aside>' +
			'<section class="workspace"><header class="topbar"><div class="breadcrumbs"><span>数据治理</span><i>/</i><span>' +
			meta.group +
			'</span><i>/</i><b>' +
			meta.label +
			'</b></div><div class="topbar-actions"><button type="button" class="search-pill" data-action="search">搜索规则、资产或运行记录 <kbd>Ctrl K</kbd></button><span class="workspace-pill">默认数据湖</span><span class="avatar">质</span></div></header>' +
			'<div class="prototype-banner"><span><b>交互原型</b> · 页面结构参考 DataWorks DQC，业务对象和样例数据已转换为 DTS 语义</span><span>不连接后端 · 操作不会写入数据</span></div>' +
			'<main class="main-content">' +
			content +
			"</main></section></div>"
		);
	}

	function render() {
		var app = document.getElementById("app");
		var view = DQ.views[STATE.route] || DQ.views.overview;
		var content = view ? view(STATE) : missingView();
		app.innerHTML = renderShell(content);
		document.title = (ROUTE_META[STATE.route] || ROUTE_META.overview).label + " · DTS 数据质量原型";
	}

	function missingView() {
		return '<section class="page"><div class="empty-state"><b>页面模块未加载</b><p>请检查原型脚本加载顺序。</p></div></section>';
	}

	function modalBody(kind) {
		var definitions = {
			subscribe: {
				title: "告警订阅",
				body:
					'<div class="form-grid"><label class="form-field"><span>通知对象</span><input class="control" value="表负责人、质量管理员" /></label><label class="form-field"><span>通知渠道</span><select class="control"><option>站内信 + 邮件</option><option>钉钉</option><option>企业微信</option><option>自定义 Webhook</option></select></label><label class="form-field span-2"><span>通知条件</span><div class="choice-row"><label><input type="checkbox" checked /> 红色异常</label><label><input type="checkbox" checked /> 橙色异常</label><label><input type="checkbox" /> 执行失败</label></div></label></div>',
			},
			"test-rule": {
				title: "规则试跑",
				body:
					'<div class="run-feedback"><span class="run-spinner"></span><div><b>已提交只读试跑</b><p>将使用所选数据资产的数据源连接，不会修改业务数据。</p></div></div><div class="code-block">SELECT id FROM public.ods_budget_v2\nWHERE project_no IS NULL\nLIMIT 1000;</div>',
			},
			"dispose-problem": {
				title: "问题数据处理",
				body:
					'<div class="form-grid"><label class="form-field"><span>处理方式</span><select class="control"><option>已确认并修复</option><option>转交处理</option><option>误报忽略</option></select></label><label class="form-field"><span>处理责任人</span><input class="control" value="王敏" /></label><label class="form-field span-2"><span>处理意见</span><textarea class="control" rows="4">已补齐缺失项目编号，等待下一周期复核。</textarea></label></div>',
			},
			"report-subscribe": {
				title: "报告订阅管理",
				body:
					'<div class="form-grid"><label class="form-field"><span>发送周期</span><select class="control"><option>每周一 09:00</option><option>每日 09:00</option><option>每月 1 日 09:00</option></select></label><label class="form-field"><span>统计范围</span><select class="control"><option>最近 7 天</option><option>最近 30 天</option></select></label><label class="form-field span-2"><span>接收人</span><input class="control" value="数据治理组、财务数据负责人" /></label></div>',
			},
			export: {
				title: "导出确认",
				body: '<div class="inline-alert info">导出内容仅包含当前筛选范围内的合成原型数据。</div><p class="muted">文件格式：Excel（.xlsx） · 预计 18 条记录</p>',
			},
			search: {
				title: "原型全局搜索",
				body: '<label class="form-field"><span>搜索数据质量对象</span><input class="control" autofocus placeholder="输入规则、资产、监控或运行 ID" /></label><div class="search-suggestions"><button data-route="rule-detail" data-id="DQR-000127">规则：项目编号标准格式</button><button data-route="table-detail" data-id="dwd_project_master">资产：dwd_project_master</button><button data-route="run-detail" data-id="DQRUN-73102">运行：DQRUN-73102</button></div>',
			},
			schedule: {
				title: "关联调度",
				body:
					'<div class="form-grid"><label class="form-field"><span>触发方式</span><select class="control"><option>跟随产出节点</option><option>定时触发</option><option>手工触发</option></select></label><label class="form-field"><span>关联节点</span><select class="control"><option>sync_budget_detail_d · 02:00</option></select></label><label class="form-field"><span>失败重试</span><select class="control"><option>间隔 5 分钟，最多 2 次</option><option>不重试</option></select></label><label class="form-field"><span>资源队列</span><select class="control"><option>quality-default</option></select></label></div>',
			},
			"asset-picker": {
				title: "选择数据资产",
				body:
					'<div class="filters"><input class="control wide" placeholder="搜索资产名称或路径" /><select class="control"><option>全部业务域</option><option>未归属业务域</option><option>财务域</option><option>交易域</option></select></div><div class="selection-list"><label><input type="radio" name="asset" checked /> <b>ods_budget_v2</b> · 默认数据湖 / PostgreSQL</label><label><input type="radio" name="asset" /> <b>dwd_order_detail</b> · 交易域 / PostgreSQL</label><label><input type="radio" name="asset" /> <b>dim_customer</b> · 未归属业务域 / MySQL</label></div>',
			},
			"new-template": {
				title: "新建规则模板",
				body:
					'<div class="form-grid"><label class="form-field"><span>模板名称</span><input class="control" value="业务编码格式校验" /></label><label class="form-field"><span>质量维度</span><select class="control"><option>有效性</option><option>完整性</option><option>准确性</option></select></label><label class="form-field"><span>关联范围</span><select class="control"><option>字段级</option><option>表级</option></select></label><label class="form-field"><span>模板分组</span><select class="control"><option>DTS 自定义模板</option></select></label><label class="form-field span-2"><span>模板描述</span><textarea class="control" rows="3">检查业务编码是否符合统一格式。</textarea></label></div>',
			},
			"template-group": {
				title: "模板分组管理",
				body: '<div class="selection-list"><div><b>DTS 自定义模板</b><span class="chip info">4 个模板</span></div><div><b>财务数据校验</b><span class="chip info">2 个模板</span></div><div><b>交易链路校验</b><span class="chip info">3 个模板</span></div></div>',
			},
			"noise-editor": {
				title: "创建去噪规则",
				body:
					'<div class="inline-alert warning"><b>去噪不会跳过检测。</b>命中规则的异常仍会保留运行记录。</div><div class="form-grid"><label class="form-field"><span>业务日期</span><input class="control" value="每月最后一个工作日" /></label><label class="form-field"><span>规则类型</span><select class="control"><option>表行数波动</option><option>空分区</option><option>唯一值波动</option></select></label><label class="form-field"><span>作为样本值去噪</span><select class="control"><option>是</option><option>否</option></select></label><label class="form-field"><span>作为基准值去噪</span><select class="control"><option>否</option><option>是</option></select></label><label class="form-field"><span>是否启用</span><select class="control"><option>是</option><option>否</option></select></label></div>',
			},
		};
		return definitions[kind] || {
			title: "原型交互",
			body: '<p>此操作在原型中仅展示交互反馈，不会写入后端。</p>',
		};
	}

	function openModal(kind) {
		var definition = modalBody(kind);
		var root = document.getElementById("overlay-root");
		root.innerHTML =
			'<div class="modal-backdrop" data-action="close-modal"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title"><header><h2 id="modal-title">' +
			definition.title +
			'</h2><button type="button" class="modal-close" aria-label="关闭" data-action="close-modal">×</button></header><div class="modal-content">' +
			definition.body +
			'</div><footer><button type="button" class="btn" data-action="close-modal">取消</button><button type="button" class="btn primary" data-action="confirm-modal">确认</button></footer></section></div>';
		var focusTarget = root.querySelector("[autofocus], .modal button, .modal input");
		if (focusTarget) focusTarget.focus();
	}

	function closeModal() {
		document.getElementById("overlay-root").innerHTML = "";
	}

	function showToast(message, tone) {
		var root = document.getElementById("overlay-root");
		var existing = root.querySelector(".toast");
		if (existing) existing.remove();
		var toast = document.createElement("div");
		toast.className = "toast " + (tone || "success");
		toast.setAttribute("role", "status");
		toast.textContent = message;
		root.appendChild(toast);
		window.setTimeout(function () {
			if (toast.parentNode) toast.parentNode.removeChild(toast);
		}, 2400);
	}

	function handleAction(action, element, clickEvent) {
		if (action === "close-modal") {
			if (element.classList.contains("modal-backdrop") && element !== clickEvent.target) return;
			closeModal();
			return;
		}
		if (action === "confirm-modal") {
			closeModal();
			showToast("原型操作已确认，不会写入后端");
			return;
		}
		if (action === "toggle-production") {
			STATE.productionOnly = !STATE.productionOnly;
			render();
			return;
		}
		if (action === "switch-view") {
			STATE.view = element.getAttribute("data-value") || "rule";
			render();
			return;
		}
		if (action === "set-period") {
			STATE.period = element.getAttribute("data-value") || "7d";
			render();
			return;
		}
		var modalActions = ["subscribe", "test-rule", "dispose-problem", "report-subscribe", "export", "search"];
		if (modalActions.indexOf(action) >= 0) {
			openModal(action);
			return;
		}
		if (action === "modal") {
			openModal(element.getAttribute("data-modal") || "default");
			return;
		}
		if (action === "toast") {
			showToast(element.getAttribute("data-message") || "该交互已在原型中模拟");
			return;
		}
		if (action === "refresh") {
			showToast("页面数据已刷新");
			return;
		}
		if (action === "save" || action === "publish" || action === "run-now" || action === "enable" || action === "disable") {
			showToast("原型已模拟“" + element.textContent.trim() + "”操作");
			return;
		}
		showToast("该操作仅用于演示交互", "info");
	}

	document.addEventListener("click", function (event) {
		var routeTarget = event.target.closest("[data-route]");
		if (routeTarget) {
			event.preventDefault();
			closeModal();
			navigate(routeTarget.getAttribute("data-route"));
			return;
		}
		var groupTarget = event.target.closest("[data-group]");
		if (groupTarget) {
			var group = groupTarget.getAttribute("data-group");
			STATE.openGroups[group] = !STATE.openGroups[group];
			render();
			return;
		}
		var actionTarget = event.target.closest("[data-action]");
		if (actionTarget) handleAction(actionTarget.getAttribute("data-action"), actionTarget, event);
	});

	document.addEventListener("keydown", function (event) {
		if (event.key === "Escape") closeModal();
		if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
			event.preventDefault();
			openModal("search");
		}
	});

	window.addEventListener("hashchange", function () {
		var next = routeFromHash();
		if (next !== STATE.route) {
			STATE.route = next;
			render();
		}
	});

	DQ.navigate = navigate;
	DQ.showToast = showToast;
	render();
})();
