# T01: governanceMode 模型属性与迁移

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标
为语义模型引入 `governanceMode`（CONTROLLED|PERMISSIVE）属性并落库，作为绞杀者开关的载体。

## 技术设计
- 在语义模型实体/表（`semantic_modeling_center` 相关，见 `changelog/20260501_02_semantic_modeling_center.xml`）新增列 `governance_mode varchar` 默认 `PERMISSIVE`。
- 新建 Liquibase changelog `20260616_01_semantic_governance_mode.xml`，挂入 `master.xml`；存量行默认 `PERMISSIVE`（保证兼容）。
- `ModelDto` / 创建入参补 `governanceMode`；**新建模型默认 CONTROLLED**（在 service 创建逻辑设默认，而非 DB 默认，以区分"存量=PERMISSIVE / 新建=CONTROLLED"）。
- 校验取值枚举（非法值 → 400）。

## 影响范围
- `dts-platform`：语义模型实体 + DTO + 创建/更新 service（`SemanticModelingService` subject/model CRUD 段）。
- Liquibase：新 changelog + master.xml。

## 验证
- [ ] 迁移前滚后，存量模型 `governance_mode=PERMISSIVE`。
- [ ] 新建模型默认 CONTROLLED；可显式指定。
- [ ] 非法 governanceMode → 400。

## 完成标准
- [ ] 列与 changelog 落地、前滚通过；DTO/CRUD 贯通；单测覆盖默认值与枚举校验。
