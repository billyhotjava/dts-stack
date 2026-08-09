# F1：数据架构元数据与控制面

**优先级**：P0
**状态**：DONE（Architecture）

## 目标

冻结平台全局的统一语言、canonical owner、关键实体关系和生命周期，回答哪些能力属于数据架构字典、哪些属于建模项目范围，并与后续业务主数据管理划清边界。

## 契约定义

| 类型 | 已批准契约 | 冻结内容 |
|---|---|---|
| 数据 | `catalog_domain`、业务过程、分层、集市、主题域、计划表 | 全局作用域、稳定 ID、类型/层级、状态与版本；计划与架构字典分离 |
| API | `/api/catalog/domains*` 与规划资源 | 唯一维护入口、consumer read port、兼容路由 |
| 权限/审计 | 产品/前端粗粒度授权 + 后端宽角色/对象 guard + 公共审计 | 唯一 command boundary、平台全局字典 actor allowlist、每个写动作 |

## Task

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结统一语言与权威归属 | P0 | DONE | 领域画像 |
| T02 | 冻结关键数据模型关系与端到端契约 | P0 | DONE | T01、关系事实基线 |

## Definition of Ready

- [x] `domain-profile.md`、Context Ledger 和 `data-model-relationships.md` 已作为共享输入，不要求 Task 重新扫描
- [x] IT-01/02/03 已登记真实决策参与者、目标日期和法定评审角色（xiezm 兼任）
- [x] ADR-86-01/02/03/08/11/12/17 的候选项、反例和已知约束已进入决策登记簿与 `assets/f1-t01-decision-pack.md`
- [x] 当前 owner、重复 CRUD、遗留 tenant 字段和 MDM 边界事实均有证据行号
- [x] T01/T02 的输出位置、失败条件和对应 IT 记录已明确
- [x] RF-86-09 等未决项有 Task owner 和截止日期；未把未决结论写成前置完成项

## Feature Definition of Done

- [x] T01、T02 均达到 DONE，产物写回 `decision-register.md` 与关系契约
- [x] 架构字典、建模计划、MDM 的边界和 canonical owner 均达到 `ACCEPTED`
- [x] ADR-86-17 区分预防控制、侦测控制和具名剩余风险
- [x] IT-01、IT-02、IT-03、IT-07 有真实参与者、结论、异议和证据链接
- [x] F2～F5 能直接消费本 Feature 的稳定 ID、owner、状态传播和失败规则
