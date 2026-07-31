/**
 * 外壳与路由。
 */

(function () {

const A = window.SchemaForm.el;

const ROUTES = [
	{ key: "list", label: "数据接入" },
	{ key: "params", label: "参数归属" },
	{ key: "admin", label: "连接器与运行时" },
	{ key: "notes", label: "设计说明" },
];

const STATE = { route: "list", detailId: "ing-1039" };

function nav(route, id) {
	if (route === "wizard" && STATE.route !== "wizard") window.Wizard.resetWizard();
	STATE.route = route;
	if (id) STATE.detailId = id;
	render();
}

function renderShell(body) {
	const isDetailish = STATE.route === "detail" || STATE.route === "wizard";
	return A("div", { class: "shell" }, [
		A("div", { class: "notice" }, [
			A("span", {}, "本地只读原型 · 不连接任何后端 · 数据为示意"),
			A("span", { class: "muted-light" }, "worklog/prototype/dbconn"),
		]),
		A("header", { class: "topbar" }, [
			A("div", { class: "brand" }, [A("span", { class: "logo" }, "D"), "DTS 数据平台"]),
			A("nav", { class: "topnav" }, ROUTES.map((r) => A("button", {
				class: `topnav-item${STATE.route === r.key || (isDetailish && r.key === "list") ? " active" : ""}`,
				onclick: () => nav(r.key),
			}, r.label))),
			A("div", { class: "spacer" }),
			A("span", { class: "who" }, "李工 · 信息中心"),
		]),
		A("main", { class: "main" }, body),
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
			body = window.Views.renderList(nav);
	}
	app.replaceChildren(renderShell(body));
}

render();

})();
