/**
 * 外壳与路由。
 */

(function () {

const A = window.SchemaForm.el;

const NAV_GROUPS = [
	{
		key: "access",
		label: "接入管理",
		items: [
			{ key: "list", label: "接入概览", icon: "▦" },
			{ key: "database", label: "数据库接入", icon: "DB", sourceCategory: "DATABASE" },
			{ key: "api", label: "API 接入", icon: "API", sourceCategory: "API" },
			{ key: "file", label: "离线文件接入", icon: "F", sourceCategory: "FILE" },
		],
	},
	{
		key: "configuration",
		label: "配置管理",
		items: [
			{ key: "defaults", label: "默认策略", icon: "⚙" },
			{ key: "admin", label: "连接器与运行时", icon: "◇" },
		],
	},
	{
		key: "prototype",
		label: "原型辅助",
		items: [
			{ key: "notes", label: "设计说明", icon: "i" },
		],
	},
];

const SOURCE_ROUTE = { DATABASE: "database", API: "api", FILE: "file" };
const ROUTE_META = Object.fromEntries(
	NAV_GROUPS.flatMap((group) => group.items.map((item) => [item.key, { ...item, group: group.label }])),
);
const STATE = {
	route: "list",
	detailId: "ing-1039",
	sourceCategory: "DATABASE",
	openGroups: { access: true, configuration: true, prototype: false },
};

function categoryForDetail() {
	const connection = window.PROTO.CONNECTIONS.find((item) => item.id === STATE.detailId);
	const connector = connection ? window.PROTO.connectorByKey(connection.connector) : null;
	return connector?.category || null;
}

function activeMenuRoute() {
	if (STATE.route === "wizard") return SOURCE_ROUTE[STATE.sourceCategory] || "database";
	if (STATE.route === "detail") return SOURCE_ROUTE[categoryForDetail()] || "list";
	return STATE.route;
}

function nav(route, id, options) {
	const next = options || {};
	if (route === "wizard" && STATE.route !== "wizard") {
		STATE.sourceCategory = next.sourceCategory || STATE.sourceCategory || "DATABASE";
		window.Wizard.resetWizard(STATE.sourceCategory);
	}
	const routeMeta = ROUTE_META[route];
	if (routeMeta?.sourceCategory) STATE.sourceCategory = routeMeta.sourceCategory;
	STATE.route = route;
	if (id) STATE.detailId = id;
	render();
}

function renderNavGroup(group, activeRoute) {
	const open = STATE.openGroups[group.key];
	return A("section", { class: `nav-tree-group${open ? " open" : ""}` }, [
		A("button", {
			class: "nav-tree-trigger",
			type: "button",
			"aria-expanded": String(open),
			onclick: () => {
				STATE.openGroups[group.key] = !open;
				render();
			},
		}, [
			A("span", { class: "nav-tree-caret" }, open ? "⌄" : "›"),
			A("span", {}, group.label),
		]),
		open ? A("div", { class: "nav-tree-items" }, group.items.map((item) => A("button", {
			class: `nav-tree-item${activeRoute === item.key ? " active" : ""}`,
			type: "button",
			onclick: () => nav(item.key),
		}, [
			A("span", { class: `nav-item-icon icon-${item.key}` }, item.icon),
			A("span", {}, item.label),
		]))) : null,
	]);
}

function renderShell(body) {
	const activeRoute = activeMenuRoute();
	const activeMeta = ROUTE_META[activeRoute] || ROUTE_META.list;
	const pageTitle = STATE.route === "wizard"
		? `新建${activeMeta.label}`
		: STATE.route === "detail" ? "接入详情" : activeMeta.label;
	return A("div", { class: "shell" }, [
		A("aside", { class: "side-nav" }, [
			A("div", { class: "side-brand" }, [
				A("span", { class: "logo" }, "D"),
				A("div", {}, [
					A("b", {}, "DTS 数据平台"),
					A("span", {}, "数据集成"),
				]),
			]),
			A("nav", { class: "nav-tree", "aria-label": "数据接入导航" },
				NAV_GROUPS.map((group) => renderNavGroup(group, activeRoute))),
			A("div", { class: "side-foot" }, [
				A("span", { class: "env-dot" }),
				A("span", {}, "原型环境 · 只读"),
			]),
		]),
		A("section", { class: "workspace" }, [
			A("header", { class: "topbar" }, [
				A("div", { class: "top-crumbs" }, [
					A("span", {}, "数据集成"),
					A("span", { class: "crumb-sep" }, "/"),
					A("b", {}, pageTitle),
				]),
				A("div", { class: "spacer" }),
				A("button", {
					class: "global-search",
					type: "button",
					disabled: true,
					title: "静态原型未接入全局搜索",
				}, [
					A("span", {}, "全局搜索（原型）"),
					A("kbd", {}, "Ctrl K"),
				]),
				A("span", { class: "who" }, "李工 · 信息中心"),
			]),
			A("div", { class: "notice" }, [
				A("span", {}, "本地只读原型 · 不连接任何后端 · 数据为示意"),
				A("span", { class: "muted-light" }, "worklog/prototype/dbconn"),
			]),
			A("main", { class: "main" }, body),
		]),
	]);
}

function render() {
	const app = document.getElementById("app");
	let body;
	switch (STATE.route) {
		case "wizard":
			body = window.Wizard.renderWizard(nav, render);
			break;
		case "detail":
			body = window.Views.renderDetail(nav, STATE.detailId);
			break;
		case "database":
			body = window.AccessPages.renderList(nav, "DATABASE");
			break;
		case "api":
			body = window.AccessPages.renderList(nav, "API");
			break;
		case "file":
			body = window.AccessPages.renderList(nav, "FILE");
			break;
		case "defaults":
			body = window.AccessPages.renderDefaults(nav);
			break;
		case "params":
			body = window.Views.renderParamMap(nav);
			break;
		case "admin":
			body = window.Views.renderAdmin();
			break;
		case "notes":
			body = window.Views.renderNotes();
			break;
		default:
			body = window.AccessPages.renderList(nav);
	}
	app.replaceChildren(renderShell(body));
}

render();

})();
