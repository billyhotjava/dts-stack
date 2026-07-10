# Todo Sprint Queue

用于沉淀尚未正式进入版本执行队列的候选 sprint。进入正式开发前，需要再确认分支、当前未提交差异、真实页面/API 状态和验收命令。

## Sprint-60: 标准管理控制面与建模/指标闭环 (202607)

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-标准控制面与事实源收敛 | 3 | READY |
| F2-数据元到逻辑建模强约束 | 4 | IN_PROGRESS |
| F3-业务术语到指标口径绑定 | 4 | READY |
| F4-模板到低代码和发布门禁 | 4 | READY |
| F5-标准到物理模型生成与SQL微调 | 4 | IN_PROGRESS |

**统计**: READY=17, IN_PROGRESS=2, DONE=0, BLOCKED=0

**目录**: `worklog/todo/sprint-60-202607-standards-control-plane/`

## Sprint-61: UI 主导的端到端数据产品体验闭环 (202607)

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-端到端旅程工作台与上下文保持 | 3 | DONE |
| F2-数据集成到数仓规划的首屏引导 | 4 | DONE |
| F3-标准落标到建模与指标的可见传递 | 3 | DONE |
| F4-数据开发到发布门禁与运行证据 | 3 | DONE |
| F5-数据服务消费闭环与客户验收 | 3 | DONE |
| F6-旅程上下文组件化与页面接入 | 3 | DONE |
| F7-阶段状态与缺口计算模型 | 3 | DONE |
| F8-客户验收包与证据聚合 | 3 | DONE |
| F9-可登录浏览器验收与回归基线 | 3 | READY |

**统计**: READY=3, IN_PROGRESS=0, DONE=25, BLOCKED=0

**目录**: `worklog/todo/sprint-61-202607-ui-led-e2e-product-experience/`

## Sprint-62: 旅程可信化与门禁证据结构化 (202607)

| Feature | Task 数 | 状态 |
|---------|---------|------|
| F1-旅程实例持久化与恢复 | 3 | DONE |
| F2-阶段真实性校验 | 3 | DONE |
| F3-dbt式门禁证据结构化 | 3 | DONE |
| F4-菜单直达旅程感知与验收包打印 | 3 | IN_PROGRESS |

**统计**: READY=1, IN_PROGRESS=1, DONE=11, BLOCKED=0

**目录**: `worklog/todo/sprint-62-202607-journey-trust-and-gate-evidence/`

**来源**: 对 sprint-61 旅程重构的三视角 review（DataWorks 成熟产品 / dbt 门禁语义 / 客户无正规开发）；缺口分析见 sprint 目录 `assets/gap-analysis.md`。依赖：browser 证据挂靠 sprint-61 F9。
