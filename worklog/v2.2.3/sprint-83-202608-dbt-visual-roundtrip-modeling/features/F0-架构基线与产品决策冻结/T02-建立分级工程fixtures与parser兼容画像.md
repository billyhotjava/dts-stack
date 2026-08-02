# T02：建立分级工程 fixtures 与 parser 兼容画像

**优先级**：P0  
**状态**：DONE（83a 工程准入）  
**依赖**：T01

## 目标

用重新构造或许可固定的工程 fixtures 冻结 inspect 与 import projection 的通用契约，并为后续编码提供离线、可重复的 RED 基线。客户脱敏包单独用于客户兼容声明；materialization runtime 认证由独立 T05 消费 H83-01，不再与 parser fixture 混为同一门禁。

## Contract-first

- **输入**：账本 CL-21～CL-24、`assets/dbt-compatibility-and-source-only-contract.md` 与 FX-01～05。
- **输出**：工程 fixture inventory：版本、manifest schema、adapter、生成或许可来源、文件/模型/source/test/macro 数、节点/边/深度、动态表达式比例、缺失字段率、解析耗时，以及 inspection/importProjection 结果；materialization 只记录 `NOT_CERTIFIED` 或引用 T05 证据。
- **失败路径**：含凭据/敏感数据的样本拒收；公开 fixture 未固定 commit/许可证/离线副本/SHA-256 时不得作为工程证据；客户包无法脱敏时由客户在受控环境运行画像脚本，只归档统计与 checksum。
- **数据边界**：客户样本不进入 Git；工程测试夹具必须是重新构造的最小无敏感版本，或许可与归档信息完整的离线固定副本；测试不联网下载依赖。
- **认证边界**：本 Task 不产生 runtime `CERTIFIED`；当前 `dts-dbt:1.10.0` 实际 Core 为 `2.0.0-alpha.5`，materialization 一律保持 `NOT_CERTIFIED`，直到 T05 完成。

## 验证

- [x] 当前 P0 切片所需 FX-01、FX-03 基础分支及 FX-05 安全样本可离线重复；FX-02 source-only、FX-04 drift 保持 P1 DRAFT，不阻断首个 P0 切片。
- [x] 兼容版本、测试耗时、阻断规则与 hash 归档在 `assets/dbt-fixture-inventory.md`；NFR 继续引用该证据。

## Definition of Done

- [x] D09 三轴兼容政策已于 2026-08-02 确认。
- [x] inspection/importProjection 的工程矩阵可重复；materialization 明确为 `NOT_CERTIFIED` 并引用 T05。
- [x] 后续 Task 只消费已归档工程 fixture，不重复扫描客户包决定通用范围；客户画像结果只更新客户兼容声明。
