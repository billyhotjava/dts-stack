# F0：交付基线与契约冻结

**优先级**：P0
**状态**：`DONE`（环境限制见 `it/baseline.md`）

## 目标

用真实任务与客户流程证明产品范围，建立可重复验收环境，并冻结“任务配置为事实源、拓扑为投影、目标资产为唯一身份、质量为提交后正式验证”的 UI/API/数据契约。此 Feature 不修改业务功能。

## 契约定义

| 类型 | 契约 | 产出 |
|---|---|---|
| 需求证据 | `assets/workflow-complexity-evidence.md` | 全量活跃任务 + 客户流程分类，决定只读拓扑或另立 DAG 设计 |
| 交付基线 | `it/baseline.md` | 三角色会话、金丝雀、Chrome 95、运行依赖、DAG 对账 |
| 产品 ADR | `assets/product-scope-decision.md` | 数据集成流程定位及非目标 |
| 质量/资产 ADR | `assets/quality-asset-integration-contract.md` | 两段式质量、目标资产身份、当前证据与可信派生 |
| API 契约 | task design/topology/admit/execution | DTO、状态码、并发、权限、审计、兼容 |

## Tasks

| Task | 状态 | 产出 |
|---|---|---|
| [T01-建立可重复交付基线](T01-建立可重复交付基线.md) | DONE | 复杂度盘点、真实登录、本地 Chrome、DAG/失败归类及环境说明 |
| [T02-冻结产品形态与接口契约](T02-冻结产品形态与接口契约.md) | DONE | 页面线框、design/topology/quality evidence DTO、错误码、plan checksum 与兼容契约 |

## Definition of Ready

- [x] 当前页面、API、版本、执行、数据库和 Airflow 链路已形成勘察账本。
- [x] T01 是只读盘点和受控夹具任务，目标与停止条件明确。
- [x] T02 已使用真实流程分类、线上 schema 和运行数据冻结实现契约。

## 完成标准

- [x] 可编辑 DAG 是否必要由样本证据裁决，而非由已有画布代码决定。
- [x] 页面、API、service、data、migration 每层契约均无未命名 owner。
- [ ] 金丝雀的 task/revision/execution/dataset/workflow/run 证据链可重复，并证明旧 PASS 不冒充当前证据。
- [ ] F1～F4 不再自行发明 DSL、节点语义、权限或通用 Airflow 控制入口。
