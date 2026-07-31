# DataWorks DQC 页面采集与 DTS 原型清单

## 采集说明

- 采集日期：2026-07-31
- 数据区域：华北 2（北京）
- 入口：`https://dataworks.data.aliyun.com/cn-beijing/dqc?defaultProjectId=403731#/overview`
- 方式：用户扫码授权后的隔离 Chrome 会话，只进行页面导航、打开非提交型弹窗和截图
- 安全边界：未读取、输出或保存账号密码；未提交规则、任务、订阅或报告；截图不包含登录二维码
- 当前项目状态：DQC 工作空间没有可用数据源，列表以空态或系统模板为主

因此本原型采用两类来源：

1. **实采**：当前账号实际可见页面、字段、按钮、空态和视觉结构。
2. **文档补齐**：因无数据源或版本权限无法进入的详情/编辑状态，按阿里云官方文档字段补齐，并使用 DTS 合成数据验证完整旅程。

## 主导航页面

| # | DataWorks 页面 | DataWorks 路由 | 原型路由 | 采集状态 | 参考截图 |
|---|---|---|---|---|---|
| 1 | 质量大盘 | `#/overview` | `#/overview` | 实采 | `dataworks-overview.png` |
| 2 | 规则列表 | `#/rule/list` | `#/rule-list` | 实采 | `dataworks-rule-list.png` |
| 3 | 规则模板库 | `#/rule-template` | `#/rule-template` | 实采，含系统/自定义模板态 | `dataworks-rule-template.png`、`dataworks-rule-template-custom.png` |
| 4 | 按表配置 | `#/rule` | `#/rule-by-table` | 实采空态 | `dataworks-rule-by-table.png` |
| 5 | 按模板配置 | `#/rule-configuration/template` | `#/rule-by-template` | 实采 | `dataworks-rule-by-template.png` |
| 6 | 质量监控 | `#/my` | `#/monitor` | 实采空态 | `dataworks-monitor.png` |
| 7 | 运行记录 | `#/job` | `#/run-records` | 实采空态 | `dataworks-run-records.png` |
| 8 | 质量报告 | `#/report` | `#/report` | 实采空态，创建按钮受版本能力限制 | `dataworks-report.png` |

## 实采二级页面与状态

| 页面/状态 | DataWorks 路由或入口 | 原型路由 | 采集结果 |
|---|---|---|---|
| 创建规则入口 | 规则列表“创建规则” | `#/rule-editor` | 实际入口跳转按表配置；原型补齐规则编辑器 |
| 自定义模板空态 | 规则模板库“自定义模板” | `#/rule-template` | 实采 |
| 按模板批量配置：规则设定 | `#/rule-configuration/template/45/create` | `#/batch-wizard` | 实采，见 `dataworks-template-config-wizard.png` |
| 按模板批量配置：生成规则 | 同上“下一步” | `#/batch-wizard` | 实采，见 `dataworks-template-config-targets.png` |
| 去噪管理 | `#/my/noise` | `#/noise` | 实采，见 `dataworks-noise-management.png` |
| 创建去噪规则 | 去噪管理“创建去噪规则” | `#/noise` | 实采，见 `dataworks-noise-create.png`、`dataworks-noise-create-row.png` |

## 文档补齐页面

以下状态在当前空工作空间无法从真实数据进入，但属于完整数据质量旅程，已在原型中实现：

| 页面 | 原型路由 | 原型覆盖内容 |
|---|---|---|
| 规则详情 | `#/rule-detail` | 基本信息、SQL/阈值、关联资产、监控与版本 |
| 新建/编辑规则 | `#/rule-editor` | 系统模板、自定义模板、自定义 SQL/脚本、阈值、试跑、订阅 |
| 模板详情 | `#/template-detail` | 模板配置、应用列表、变更日志 |
| 表质量详情 | `#/table-detail` | 规则管理/质量监控、资产摘要、质量趋势 |
| 质量监控详情 | `#/monitor-detail` | 数据范围、规则、触发、负责人和订阅 |
| 新建/编辑监控 | `#/monitor-editor` | 手动/调度触发、运行资源、告警与阻塞策略 |
| 运行详情 | `#/run-detail` | 本次运行、历史运行、问题数据处理、原始日志 |
| 报告模板编辑 | `#/report-editor` | 名称、周期、统计范围、指标和接收人 |
| 报告预览 | `#/report-preview` | 评分、趋势、问题表、维度分布和订阅 |

## 页面信息架构

```text
质量大盘
├── 配置流程引导
├── 六维质量评分
├── 重点关注（规则 / 表）
├── 实例趋势与运行状态
└── TOP 问题资产 / 负责人

质量资产
├── 规则列表 → 规则详情 / 新建规则
└── 规则模板库 → 模板详情

规则配置
├── 按表配置 → 表质量详情（规则管理 / 质量监控）
└── 按模板配置 → 批量配置向导

质量运维
├── 质量监控 → 详情 / 编辑 / 去噪管理
└── 运行记录 → 运行详情 → 问题数据处置

质量分析
└── 质量报告 → 模板编辑 / 报告预览 / 订阅
```

## DTS 化取舍

- 保留：8 页业务链路、六大质量维度、按表/模板配置、监控与运行分离、问题处置、报告订阅。
- 收敛：移除 DataWorks 全产品入口、Data Agent、商业化横幅和多层空置数据源侧栏。
- 转换：将“项目/MaxCompute 表”统一表达为 DTS 数据源与数据资产；示例使用 PostgreSQL 默认数据湖。
- 增强：详情页显式展示绑定资产、实际数据源、只读执行和失败样例，避免规则与执行来源割裂。
- 边界：原型只描述交互，不声明后台已经具备动态阈值、去噪、Webhook 或定时报告能力。

## 官方核对资料

- 数据质量总览：<https://help.aliyun.com/zh/dataworks/user-guide/data-quality/>
- 质量大盘：<https://help.aliyun.com/zh/dataworks/user-guide/go-to-the-overview-page>
- 规则列表：<https://help.aliyun.com/zh/dataworks/user-guide/monitoring-rules>
- 规则模板库：<https://help.aliyun.com/zh/dataworks/user-guide/create-manage-and-use-rule-templates>
- 按表配置：<https://help.aliyun.com/zh/dataworks/user-guide/configure-monitoring-rules-by-table>
- 按模板配置：<https://help.aliyun.com/zh/dataworks/user-guide/configure-monitoring-rules-based-on-a-monitoring-rule-template>
- 质量监控：<https://help.aliyun.com/zh/dataworks/user-guide/view-my-subscriptions>
- 运行记录：<https://help.aliyun.com/zh/dataworks/user-guide/view-monitoring-results>
- 质量报告：<https://help.aliyun.com/zh/dataworks/user-guide/create-and-manage-report-templates>
- 去噪管理：<https://help.aliyun.com/zh/dataworks/user-guide/mange-noise-reduction-rules>

