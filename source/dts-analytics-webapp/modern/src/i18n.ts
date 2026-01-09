export type Locale = "zh-CN" | "en";

export const LOCALES: Locale[] = ["zh-CN", "en"];

export function normalizeLocale(input: string | undefined | null): Locale {
	const lang = String(input ?? "").toLowerCase();
	if (lang.startsWith("en")) return "en";
	return "zh-CN";
}

const messages: Record<Locale, Record<string, string>> = {
	"zh-CN": {
		title: "DTS 可视化分析",
		subtitle: "Modern UI（逐步替换 Metabase UI）",
		openLegacy: "打开旧版（Metabase UI）",
		user: "当前用户",
		health: "服务状态",
		loading: "加载中…",
		error: "请求失败",
		"home.note": "当前 modern 版本优先实现“可用 + 可迁移”。复杂的编辑/可视化能力会逐步替换 legacy。",
		"nav.home": "概览",
		"nav.collections": "集合",
		"nav.dashboards": "仪表盘",
		"nav.questions": "问题（卡片）",
		"nav.search": "搜索",
		"common.name": "名称",
		"common.id": "ID",
		"common.type": "类型",
		"collections.title": "集合",
		"collections.subtitle": "先实现集合/内容浏览，逐步补齐管理能力。",
		"collections.itemsTitle": "集合内容",
		"collections.itemsSubtitle": "集合 ID:",
		"dashboards.title": "仪表盘",
		"dashboards.subtitle": "先实现浏览与详情展示，后续补齐编辑与可视化。",
		"dashboards.detailNote": "当前为开发态：先展示原始 JSON（后续替换为可视化渲染）。",
		"questions.title": "问题（卡片）",
		"questions.subtitle": "先实现浏览与查询结果预览。",
		"questions.detailNote": "当前为开发态：先展示原始 JSON。",
		"questions.queryResult": "查询结果",
		"questions.querySql": "SQL（native_form.query）",
		"questions.queryRaw": "原始响应（JSON）",
		"search.title": "搜索",
		"search.subtitle": "按名称/描述搜索卡片、仪表盘与集合。",
		"search.placeholder": "输入关键词…",
		"search.total": "总数",
	},
	en: {
		title: "DTS Analytics",
		subtitle: "Modern UI (gradual replacement of Metabase UI)",
		openLegacy: "Open legacy (Metabase UI)",
		user: "Current user",
		health: "Service health",
		loading: "Loading…",
		error: "Request failed",
		"home.note": "Modern UI focuses on incremental replacement. Advanced editing & visualization will be migrated step by step.",
		"nav.home": "Overview",
		"nav.collections": "Collections",
		"nav.dashboards": "Dashboards",
		"nav.questions": "Questions (Cards)",
		"nav.search": "Search",
		"common.name": "Name",
		"common.id": "ID",
		"common.type": "Type",
		"collections.title": "Collections",
		"collections.subtitle": "Browsing first; management features will follow.",
		"collections.itemsTitle": "Collection Items",
		"collections.itemsSubtitle": "Collection ID:",
		"dashboards.title": "Dashboards",
		"dashboards.subtitle": "Browsing and details first; editing & visualization will follow.",
		"dashboards.detailNote": "Dev mode: showing raw JSON for now (will be replaced by visualization rendering).",
		"questions.title": "Questions (Cards)",
		"questions.subtitle": "Browsing and query preview first.",
		"questions.detailNote": "Dev mode: showing raw JSON for now.",
		"questions.queryResult": "Query result",
		"questions.querySql": "SQL (native_form.query)",
		"questions.queryRaw": "Raw response (JSON)",
		"search.title": "Search",
		"search.subtitle": "Search cards, dashboards and collections by name/description.",
		"search.placeholder": "Type keywords…",
		"search.total": "Total",
	},
};

export function t(locale: Locale, key: string): string {
	return messages[locale]?.[key] ?? key;
}
