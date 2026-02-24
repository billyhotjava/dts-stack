# P1-03 标准资产联动（术语/数据元/模板/码表）

`status`: `done`
`priority`: `P1`

## 目标

标准管理对象之间建立双向引用，提升复用与一致性。

## 后端实施点

1. 增加术语、数据元、模板、码表的引用关系查询。
2. 删除前做引用检查并返回冲突列表。
3. 审计记录补充“影响对象数量”。

## 前端实施点

1. 详情页新增“被引用/引用了谁”面板。
2. 删除弹窗展示影响对象。
3. 支持从引用对象一键跳转。

## 验收标准

- 任一标准对象都能查到引用链。
- 高风险删除操作有明确拦截与提示。

## 完成记录

1. 后端新增统一引用服务 `ModelingAssetReferenceService`，并接入：
   - 术语：`GET /api/modeling/glossary/terms/{id}/references`
   - 数据元：`GET /api/modeling/metadata-standards/{id}/references`
   - 模板：`GET /api/modeling/templates/{id}/references`
   - 码表：`GET /api/governance/reference-codes/{codeTypeId}/references`
2. 删除前统一做引用检查并阻断（HTTP 409），返回影响对象数量与摘要：
   - 术语删除、模板删除、数据元删除、码表删除
3. 审计补充 `impactCount`：
   - 引用关系查看
   - 删除动作
4. 前端完成：
   - 术语/数据元详情抽屉新增引用面板并支持跳转
   - 模板详情新增引用面板并支持跳转
   - 码表新增“引用关系”弹窗
   - 删除前预检并展示影响对象清单

## 验证

- `cd source/dts-platform && ./mvnw -DskipTests compile`
- `cd source/dts-platform-webapp && pnpm build`
