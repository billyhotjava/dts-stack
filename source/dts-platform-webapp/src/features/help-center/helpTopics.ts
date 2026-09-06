export type HelpBlocker = {
	problem: string;
	action: string;
};

export type HelpTopic = {
	id: string;
	title: string;
	section: string;
	summary: string;
	keywords: string[];
	routePrefixes: string[];
	prerequisites: string[];
	steps: string[];
	blockers: HelpBlocker[];
	relatedTopicIds: string[];
};

export const HELP_TOPICS: HelpTopic[] = [
	{
		id: "overview",
		title: "DTS 产品总览",
		section: "开始使用",
		summary: "了解 DTS 从数据接入、建设、治理到分析服务的完整工作路径。",
		keywords: ["DTS", "产品", "新手", "入门", "角色", "流程"],
		routePrefixes: [],
		prerequisites: ["已获得 DTS 账号", "账号已分配与岗位相符的菜单和数据权限"],
		steps: [
			"从工作台确认待办、异常和当前建设任务。",
			"接入数据源，并完成连通性与元数据检查。",
			"建立数据建设计划，依次完成建模、治理和发布。",
			"通过指标、报表、API 或数据产品交付业务结果。",
			"在运维与审计页面持续检查运行状态和发布证据。",
		],
		blockers: [
			{ problem: "页面或操作入口不可见", action: "联系管理员核对现有角色的菜单与数据权限。" },
			{ problem: "不确定从哪里开始", action: "先进入工作台，按待办或首要阻塞继续处理。" },
		],
		relatedTopicIds: ["workbench", "data-integration", "data-modeling"],
	},
	{
		id: "workbench",
		title: "工作台与待办",
		section: "工作台",
		summary: "查看个人待办、运行异常和跨模块任务，并从统一入口继续处理。",
		keywords: ["工作台", "待办", "任务", "异常", "建设计划"],
		routePrefixes: ["/workbench"],
		prerequisites: ["账号已登录", "至少拥有一个业务模块的查看权限"],
		steps: [
			"查看待办、异常和最近使用的业务入口。",
			"根据优先级进入对应任务页面。",
			"处理完成后返回工作台，确认状态已刷新。",
		],
		blockers: [
			{ problem: "待办没有更新", action: "刷新页面并确认对应操作已成功提交。" },
			{ problem: "点击后没有操作权限", action: "保留当前任务信息，联系管理员核对岗位角色。" },
		],
		relatedTopicIds: ["overview", "operations"],
	},
	{
		id: "data-integration",
		title: "数据集成",
		section: "数据接入",
		summary: "配置数据源、采集与转换任务，并验证数据是否稳定进入 DTS。",
		keywords: ["数据源", "连接器", "采集", "同步", "ETL", "转换", "任务"],
		routePrefixes: ["/foundation/data-sources", "/foundation/connectors", "/explore/etl"],
		prerequisites: ["准备数据库或接口连接信息", "网络与账号已允许 DTS 访问源系统"],
		steps: [
			"新建或选择数据源，填写连接参数。",
			"执行连接测试，确认网络、驱动和凭据有效。",
			"配置采集范围、增量方式和调度周期。",
			"运行一次任务并核对记录数、日志与目标表。",
		],
		blockers: [
			{ problem: "连接测试失败", action: "依次检查地址、端口、网络、驱动和账号权限。" },
			{ problem: "任务运行但没有数据", action: "检查采集范围、增量游标和源端是否存在新增记录。" },
		],
		relatedTopicIds: ["data-modeling", "operations"],
	},
	{
		id: "data-modeling",
		title: "数据建模",
		section: "数据建设",
		summary: "在独立的数据建模工作区完成标准、模型、指标和关系设计。",
		keywords: ["建模", "标准", "维度", "模型", "指标", "关系图"],
		routePrefixes: ["/data-modeling"],
		prerequisites: ["至少有一个可用数据源", "已明确业务目标或现有数据范围"],
		steps: [
			"新建建设计划并选择业务分类。",
			"绑定来源数据，完成来源盘点与数仓分层。",
			"设计维度、事实和汇总模型。",
			"完成编译、质量检查和构建验证。",
			"发布模型并检查版本、证据和下游影响。",
		],
		blockers: [
			{ problem: "阶段显示有阻塞", action: "展开阻塞详情，先完成当前阶段要求的必需证据。" },
			{ problem: "模型无法构建或发布", action: "检查依赖、编译日志、质量门禁和当前版本状态。" },
		],
		relatedTopicIds: ["construction-planning", "model-center", "sql-modeling"],
	},
	{
		id: "construction-planning",
		title: "数仓规划",
		section: "数据建设",
		summary: "统一规划业务分类、数据域、业务过程、数仓分层、数据集市和主题域。",
		keywords: ["数仓规划", "业务分类", "分层", "数据域", "业务过程", "数据集市", "主题域"],
		routePrefixes: ["/data-architecture"],
		prerequisites: ["已明确本次建设的业务目标或首批来源数据", "账号具备规划查看权限；新建和编辑还需规划维护权限"],
		steps: [
			"进入数据建设工作台，新建规划或选择已有规划。",
			"选择从业务目标开始或从现有数据开始，并填写规划范围。",
			"查看当前阶段、首要阻塞和已有证据。",
			"点击当前主动作进入专业模块完成配置。",
			"返回工作台确认阶段状态和下一步已经刷新。",
		],
		blockers: [
			{ problem: "指定规划不可用", action: "不要切换成其他规划；先重试，并核对规划是否存在及当前账号权限。" },
			{ problem: "阶段状态未知", action: "按页面恢复动作重新读取后台证据，不要把未知状态当作已完成。" },
		],
		relatedTopicIds: ["data-integration", "model-center", "governance"],
	},
	{
		id: "model-center",
		title: "维度建模",
		section: "数据建设",
		summary: "在模型工作台中设计维度、贴源、明细、汇总和应用模型，或从现有表逆向生成草稿。",
		keywords: ["模型中心", "维度表", "明细表", "汇总表", "应用表", "逻辑设计", "模型版本"],
		routePrefixes: ["/data-modeling/dimensions"],
		prerequisites: ["已经选择建设计划和业务分类", "已明确模型表达稳定对象、业务事件、聚合结果还是消费输出"],
		steps: [
			"根据业务目的选择维度表、明细表、汇总表或应用表。",
			"填写模型名称、粒度、业务分类和字段。",
			"保存逻辑设计，并处理必填字段、主键和标准绑定问题。",
			"进入数据实现，选择普通配置或高级 SQL/dbt。",
			"构建与质量检查通过后，数据管理员可在职责范围内直接发布；报表和数据服务仍单独授权。",
		],
		blockers: [
			{ problem: "当前建设计划只读", action: "可以继续查看模型；新建或修改需要规划维护权限。" },
			{ problem: "不能安全修改模型类型", action: "已有实现或发布证据时保留历史版本，按页面预检结果处理。" },
		],
		relatedTopicIds: [
			"construction-planning",
			"model-definition",
			"model-implementation",
			"model-verification",
			"model-delivery",
			"sql-modeling",
			"governance",
		],
	},
	{
		id: "model-definition",
		title: "模型定义",
		section: "数据建设",
		summary: "明确模型类型、业务粒度、主键和字段口径，形成可保存的逻辑设计。",
		keywords: ["模型定义", "逻辑设计", "模型类型", "粒度", "主键", "字段"],
		routePrefixes: [],
		prerequisites: ["已选择建设计划和业务分类", "已明确模型服务的业务对象或业务事件"],
		steps: [
			"根据用途选择维度、贴源、明细、汇总或应用模型。",
			"填写模型名称、业务分类和数据粒度。",
			"维护主键、字段及其业务口径，并绑定适用的数据标准。",
			"保存逻辑设计，处理页面提示的必填项或字段约束。",
		],
		blockers: [
			{ problem: "模型类型或粒度不明确", action: "先确认模型表达的是稳定对象、业务事件、聚合结果还是消费输出。" },
			{ problem: "无法保存逻辑设计", action: "检查模型名称、主键、必填字段和标准绑定提示。" },
		],
		relatedTopicIds: ["model-center", "model-implementation", "governance"],
	},
	{
		id: "model-implementation",
		title: "模型实现",
		section: "数据建设",
		summary: "按模型类型配置稳定的数据来源和必要的实现参数。",
		keywords: ["模型实现", "数据来源", "物理源", "上游模型", "日期生成", "固定维度", "时间字段"],
		routePrefixes: [],
		prerequisites: ["模型逻辑设计已保存", "来源表或上游模型已确认可用"],
		steps: [
			"来源方式以当前模型的可选项为准：维度表可读取输入源表或生成日期维度，明细表可读取输入源表或引用上游模型，汇总和应用表引用上游模型。",
			"选择系统生成标准日期维度后，保存模型会创建相应实现；后续仍需执行物化才能生成目标表。",
			"引用维度模型会固定所选修订；引用失效时重新选择，不自动替换成其他版本。",
			"仅选择具有业务时间含义的字段，例如订单时间。选择后字段作用设为“时间”，不转换字段类型或已有数据；普通明细模型可以不选择时间字段。",
		],
		blockers: [
			{ problem: "没有可选来源", action: "先确认物理表已采集，或上游模型已保存并在当前建设范围内可用。" },
			{
				problem: "日期生成配置不完整",
				action: "检查生成策略是否已配置，按当前页面提示选择日期维度生成方式或处理实现限制。",
			},
		],
		relatedTopicIds: ["model-definition", "model-verification", "model-center"],
	},
	{
		id: "model-verification",
		title: "模型验证",
		section: "数据建设",
		summary: "区分既有物化检查和质量检查，处理缺少规则等验证阻塞。",
		keywords: ["模型验证", "物化", "质量检查", "质量规则", "验证阻塞"],
		routePrefixes: [],
		prerequisites: ["模型实现配置已保存", "已了解当前模型适用的质量规则或检查范围"],
		steps: [
			"先查看既有物化结果，确认数据是否已按当前配置生成。",
			"再执行或查看质量检查，核对数据是否满足已配置的质量规则。",
			"质量检查失败时，按失败规则修复来源数据、模型配置或规则定义。",
			"缺少质量规则时，记录当前缺口并先补充适用规则，再完成验证。",
		],
		blockers: [
			{
				problem: "物化结果与质量结果混淆",
				action: "物化确认数据生成，质量检查确认数据是否符合规则；分别查看对应结果。",
			},
			{ problem: "没有可执行的质量规则", action: "联系规则维护责任人补充适用规则，不把未检查当作已通过。" },
		],
		relatedTopicIds: ["model-implementation", "model-delivery", "quality-security-lineage"],
	},
	{
		id: "model-delivery",
		title: "模型交付",
		section: "数据建设",
		summary: "确认模型发布结果，并分别完成资产登记和分析准备。",
		keywords: ["模型交付", "发布确认", "资产登记", "分析准备", "数据服务"],
		routePrefixes: [],
		prerequisites: ["模型验证结果已确认", "已明确交付对象和后续使用场景"],
		steps: [
			"确认模型发布状态、版本和相关证据已经满足当前职责范围的要求。",
			"核对模型输出资产是否已登记；已登记的资产可维护负责人和业务说明。",
			"按分析或服务使用场景，分别准备指标、报表、数据服务等后续内容。",
			"分别核对发布确认、资产登记和分析准备的完成状态。",
		],
		blockers: [
			{ problem: "发布状态未确认", action: "先核对模型版本、验证结果和发布证据，再处理后续交付。" },
			{
				problem: "资产或分析准备尚未完成",
				action: "查看实际失败环节与原因；分析准备失败不等于目标表构建失败，避免重复物化。",
			},
		],
		relatedTopicIds: ["model-verification", "assets", "metrics-bi", "services-products"],
	},
	{
		id: "sql-modeling",
		title: "数据实现与上线",
		section: "数据建设",
		summary: "数据实现、编译、测试和上线能力将在界面评审通过后的后台重构阶段接入。",
		keywords: ["SQL", "dbt", "编译", "测试", "上线", "Airflow", "DAG", "selector", "full-refresh", "重建"],
		routePrefixes: [],
		prerequisites: ["ODS 或其他来源表已准备完成", "dbt 工作区、Profile 和目标数据源配置可用"],
		steps: [
			"需要快速起步时，从来源表生成 DWD、DWS 或 ADS 模型模板。",
			"在模型树选择模型，在 SQL 编辑器修改逻辑并保存。",
			"点击“编译”检查 SQL 与依赖，再点击“测试”执行数据质量检查。",
			"在数据预览中核对模型输出；异常时先查看编译、测试和运行日志。",
			"提交上线前核对 DAG 就绪、质量门禁和发布门禁。",
			"门禁通过后由系统把受控 selector 和 target 交给 Airflow 执行 dbt build，并把结果同步回操作记录。",
			"需要重建表时使用“数据输出 → 重建”；系统执行受控 full-refresh，失败时保留原有数据。",
		],
		blockers: [
			{
				problem: "DAG 未就绪",
				action: "等待 Airflow 完成注册并刷新状态；不要手工填写 DAG、selector、target 或运行凭据。",
			},
			{ problem: "质量或发布门禁阻断", action: "先修复编译、测试、依赖或版本问题，再重新提交。" },
			{ problem: "执行超时或预览为空", action: "在操作记录中核对 Airflow 日志和最终运行状态，成功后再加载预览。" },
		],
		relatedTopicIds: ["model-center", "operations", "quality-security-lineage"],
	},
	{
		id: "metric-workbench",
		title: "数据指标",
		section: "分析应用",
		summary: "维护复合指标、派生指标、原子指标、修饰词和时间周期的统一口径。",
		keywords: ["数据指标", "复合指标", "派生指标", "原子指标", "修饰词", "时间周期"],
		routePrefixes: ["/data-modeling/metrics"],
		prerequisites: ["用于创建指标的模型已经发布", "指标口径、计量单位和责任人已经明确"],
		steps: [
			"在“定义与发布”维护指标口径、版本并直接发布；所级数据管理员面向全局，部门数据管理员仅维护所属部门指标。",
			"在“从模型创建”选择已发布模型及度量字段生成原子指标草稿。",
			"通过模板复用稳定的指标定义。",
			"在运行看板、指标商店和我的订阅中检查消费状态。",
		],
		blockers: [
			{ problem: "不能从模型创建指标", action: "确认模型已发布、字段可作为度量，并检查计量单位与管理权限。" },
			{ problem: "不能关联指标版本", action: "只能关联带稳定版本的已发布指标；草稿需先完成校验和发布。" },
		],
		relatedTopicIds: ["model-center", "metrics-bi", "services-products"],
	},
	{
		id: "governance",
		title: "数据标准与治理",
		section: "数据治理",
		summary: "维护术语、数据元、码表、主题域和标准绑定，统一数据含义与责任。",
		keywords: ["治理", "标准", "术语", "数据元", "码表", "主题域", "单位"],
		routePrefixes: [
			"/data-modeling/standards",
			"/governance/standards",
			"/governance/templates",
			"/governance/subjects",
			"/governance/asset-ownership",
		],
		prerequisites: ["已明确标准维护责任人", "已准备业务口径或制度依据"],
		steps: [
			"在术语、数据元和码表中维护标准定义。",
			"将标准分配到主题域或责任部门。",
			"把标准绑定到模型字段和数据资产。",
			"检查未绑定、冲突和待审核项。",
		],
		blockers: [
			{ problem: "标准不能发布", action: "补齐责任人、口径、版本和审批信息。" },
			{ problem: "字段无法绑定标准", action: "检查字段权限、标准状态和适用范围。" },
		],
		relatedTopicIds: ["data-modeling", "assets", "quality-security-lineage"],
	},
	{
		id: "assets",
		title: "数据资产",
		section: "资产目录",
		summary: "检索、盘点和维护数据资产，查看负责人、用途、版本与消费关系。",
		keywords: ["资产", "目录", "台账", "数据集", "元数据", "负责人", "检索"],
		routePrefixes: ["/catalog/assets", "/catalog/metadata", "/catalog/datasets"],
		prerequisites: ["数据源已完成元数据采集", "账号拥有对应资产的查看权限"],
		steps: [
			"通过名称、分类、标签或来源检索资产。",
			"进入详情核对字段、负责人、质量和血缘。",
			"补充业务说明、分类、标签和责任信息。",
			"通过授权或服务入口交付给使用方。",
		],
		blockers: [
			{ problem: "搜索不到资产", action: "确认元数据采集成功，并检查资产范围与数据权限。" },
			{ problem: "详情信息不完整", action: "先核对采集状态，再由资产责任人补充业务属性。" },
		],
		relatedTopicIds: ["governance", "quality-security-lineage", "services-products"],
	},
	{
		id: "quality-security-lineage",
		title: "质量、安全与血缘",
		section: "治理控制",
		summary: "执行质量规则、分级分类、权限控制和血缘影响分析，形成发布门禁。",
		keywords: ["质量", "安全", "血缘", "分级分类", "权限", "脱敏", "门禁"],
		routePrefixes: ["/governance/quality", "/governance/rules", "/security", "/catalog/lineage", "/catalog/quality"],
		prerequisites: ["资产或模型已经存在", "已明确质量阈值和数据安全要求"],
		steps: [
			"为资产或模型字段配置质量规则。",
			"执行检查并处理阻断级问题。",
			"维护分级分类、访问授权与脱敏要求。",
			"发布前查看上下游血缘和影响范围。",
		],
		blockers: [
			{ problem: "质量门禁未通过", action: "打开失败规则，修复数据或按流程调整阈值后重新执行。" },
			{ problem: "无法查看敏感字段", action: "确认数据级别、人员级别和有效授权是否匹配。" },
		],
		relatedTopicIds: ["governance", "assets", "operations"],
	},
	{
		id: "metrics-bi",
		title: "指标、BI 与数据大屏",
		section: "分析应用",
		summary: "定义统一指标，通过报表、探索分析和数据大屏交付业务洞察。",
		keywords: ["指标", "BI", "报表", "看板", "大屏", "分析", "语义模型"],
		routePrefixes: ["/metrics", "/bi"],
		prerequisites: ["模型已经构建并可查询", "指标口径与统计周期已经确认"],
		steps: [
			"定义指标口径、维度、过滤条件和负责人。",
			"完成指标校验与发布。",
			"在探索分析或报表中选择指标和维度。",
			"保存并发布看板或数据大屏。",
		],
		blockers: [
			{ problem: "指标结果为空或异常", action: "检查模型版本、时间范围、过滤条件和派生表达式。" },
			{ problem: "看板无法分享", action: "检查内容发布状态以及访问者的数据与菜单权限。" },
		],
		relatedTopicIds: ["data-modeling", "services-products"],
	},
	{
		id: "services-products",
		title: "数据服务与数据产品",
		section: "数据交付",
		summary: "把模型和指标封装为 API、数据产品、推送或共享任务并持续运营。",
		keywords: ["API", "数据服务", "数据产品", "共享", "推送", "订阅", "消费"],
		routePrefixes: ["/services"],
		prerequisites: ["待交付的数据资产或指标已经发布", "已明确消费方、用途和权限范围"],
		steps: [
			"选择已发布资产、模型或指标作为交付内容。",
			"配置服务协议、字段范围、频率和访问策略。",
			"执行预检并发布服务或数据产品。",
			"使用调用记录、订阅和告警持续检查交付状态。",
		],
		blockers: [
			{ problem: "服务预检失败", action: "检查来源版本、字段授权、访问策略和运行连接。" },
			{ problem: "消费方调用失败", action: "核对服务状态、凭据、网络和限流信息。" },
		],
		relatedTopicIds: ["assets", "metrics-bi", "operations"],
	},
	{
		id: "operations",
		title: "运维、审计与补数",
		section: "平台运营",
		summary: "监控运行实例和告警，处理失败、补数、发布门禁与审计证据。",
		keywords: ["运维", "实例", "告警", "日志", "补数", "审计", "发布"],
		routePrefixes: ["/ops"],
		prerequisites: ["拥有相应任务或平台运维查看权限"],
		steps: [
			"从运维总览定位异常任务或服务。",
			"查看实例日志、输入版本和失败原因。",
			"完成重试、补数或责任人转派。",
			"检查发布门禁和审计证据是否闭环。",
		],
		blockers: [
			{ problem: "不能重试或补数", action: "确认任务状态允许操作，并核对当前账号的运维权限。" },
			{ problem: "错误原因不明确", action: "记录实例编号与发生时间，再联查平台事件和上下游任务。" },
		],
		relatedTopicIds: ["workbench", "data-integration", "quality-security-lineage"],
	},
	{
		id: "administration",
		title: "系统与权限管理",
		section: "管理配置",
		summary: "使用现有角色管理用户、菜单、组织、连接器和平台运行配置。",
		keywords: ["管理", "用户", "角色", "菜单", "组织", "连接器", "驱动", "配置"],
		routePrefixes: ["/settings", "/admin", "/foundation/connectors", "/foundation/jdbc-drivers"],
		prerequisites: ["已获得相应的系统管理权限", "变更范围和责任人已经明确"],
		steps: [
			"确认用户所属组织和岗位职责。",
			"从现有角色中分配所需菜单和操作权限。",
			"维护连接器、驱动或运行参数。",
			"使用目标账号复核页面可见性和实际操作权限。",
		],
		blockers: [
			{ problem: "分配后权限未生效", action: "检查角色、菜单和数据范围，并让用户重新登录后复核。" },
			{ problem: "运行配置影响范围不明确", action: "暂停变更，先确认使用该配置的服务与租户。" },
		],
		relatedTopicIds: ["overview", "operations"],
	},
];

const HELP_TOPIC_MAP = new Map(HELP_TOPICS.map((topic) => [topic.id, topic]));
const FALLBACK_TOPIC = HELP_TOPICS[0];
const MODEL_WORKBENCH_STEP_TOPIC_IDS: Record<string, string> = {
	definition: "model-definition",
	implementation: "model-implementation",
	verification: "model-verification",
	delivery: "model-delivery",
};

export function getHelpTopicById(topicId?: string | null): HelpTopic | undefined {
	if (!topicId) return undefined;
	return HELP_TOPIC_MAP.get(topicId);
}

export function resolveHelpTopic(
	pathname: string,
	requestedTopicId?: string | null,
	wizardStep?: string | null,
): HelpTopic {
	const requestedTopic = getHelpTopicById(requestedTopicId);
	if (requestedTopic) return requestedTopic;

	const normalizedPath = pathname.length > 1 && pathname.endsWith("/") ? pathname.slice(0, -1) : pathname;
	if (normalizedPath === "/settings/help") return FALLBACK_TOPIC;
	if (normalizedPath === "/data-modeling/dimensions/workbench" && wizardStep != null) {
		const wizardTopic = getHelpTopicById(MODEL_WORKBENCH_STEP_TOPIC_IDS[wizardStep]);
		if (wizardTopic) return wizardTopic;
	}
	let bestMatch: HelpTopic | undefined;
	let bestPrefixLength = -1;
	for (const topic of HELP_TOPICS) {
		for (const prefix of topic.routePrefixes) {
			const matches = normalizedPath === prefix || normalizedPath.startsWith(`${prefix}/`);
			if (matches && prefix.length > bestPrefixLength) {
				bestMatch = topic;
				bestPrefixLength = prefix.length;
			}
		}
	}

	return bestMatch || FALLBACK_TOPIC;
}

export function buildHelpTopicHref(topicId: string): string {
	return `/settings/help?topic=${encodeURIComponent(topicId)}`;
}

export function getRelatedHelpTopics(topic: HelpTopic): HelpTopic[] {
	return topic.relatedTopicIds
		.map((topicId) => getHelpTopicById(topicId))
		.filter((relatedTopic): relatedTopic is HelpTopic => Boolean(relatedTopic));
}
