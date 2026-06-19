/** 各阶段/旁路区将收编的现网页面与交付 sprint —— 占位页据此渲染，体现 IA 映射。 */
export interface AreaContent {
	kicker: string;
	title: string;
	sprint: string;
	intro: string;
	pages: string[];
}

export const AREA_CONTENT: Record<string, AreaContent> = {
	connect: {
		kicker: "阶段 ①",
		title: "连接",
		sprint: "S2",
		intro: "接入数据源并完成连通，是黄金主线的起点。",
		pages: ["数据源管理", "数据源详情", "新建/编辑表单", "连通测试", "连接器注册", "JDBC 驱动", "接入变更", "任务调度"],
	},
	integrate: {
		kicker: "阶段 ②",
		title: "集成 · 可视化 ELT",
		sprint: "S3–S4",
		intro: "在画布上拖拽搭建转换，dbt 隐藏在底层自动生成。",
		pages: ["可视化 ELT 画布", "节点配置抽屉", "运行/调度/日志 dock", "运行历史", "画布⇄列表双视图", "SQL/脚本工作室（高级）"],
	},
	assets: {
		kicker: "阶段 ③",
		title: "资产",
		sprint: "S5",
		intro: "把转换产出沉淀为可发现、可信任的数据资产。",
		pages: ["资产搜索", "数据集与详情", "数据产品", "血缘图", "列级血缘/影响/diff/导入", "质量报告与规则", "资产权属与授权"],
	},
	metrics: {
		kicker: "阶段 ④",
		title: "指标 · 可视化设计",
		sprint: "S6",
		intro: "基于资产设计业务指标；dbt 仅以只读抽屉呈现。",
		pages: ["指标中心/列表", "指标模板/商店", "指标看板", "语义建模中心", "语义发布/运行", "SQL 建模", "查看生成的 dbt（只读）", "主题域/术语/参考码"],
	},
	serve: {
		kicker: "平台 · 旁路",
		title: "数据服务",
		sprint: "S7",
		intro: "把资产与指标对外提供为可消费的服务。",
		pages: ["API 服务", "访问令牌 Token", "BI 链接", "业务消费", "数据产品（服务视角）"],
	},
	govern: {
		kicker: "平台 · 旁路",
		title: "治理",
		sprint: "S7",
		intro: "跨阶段的治理与合规视角。",
		pages: ["治理中心", "权限审计", "质量规则（管理）", "指标商店（治理视角）"],
	},
	security: {
		kicker: "平台 · 旁路",
		title: "安全",
		sprint: "S7",
		intro: "数据安全与访问审批。",
		pages: ["数据安全", "数据集访问审批"],
	},
	ops: {
		kicker: "平台 · 旁路",
		title: "运维",
		sprint: "S7",
		intro: "平台运行的可观测与运维。",
		pages: ["运维总览", "实例", "回填", "告警/日志中心", "发布治理", "事件可观测", "审计证据"],
	},
	settings: {
		kicker: "平台 · 旁路",
		title: "设置",
		sprint: "S7",
		intro: "平台与系统设置。",
		pages: ["平台设置", "系统管理"],
	},
};
