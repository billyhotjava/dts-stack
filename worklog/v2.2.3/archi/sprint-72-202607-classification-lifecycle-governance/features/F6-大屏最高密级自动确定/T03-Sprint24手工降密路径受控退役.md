# T03: Sprint-24 手工降密路径受控退役

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T02

## 目标

移除“填写原因即可降低大屏密级”的旧规则，并把手工密级改为只能升高的下限。

## 技术设计

- `PATCH /screens/{id}/classification` 收敛为 raise-floor 命令或兼容适配。
- 删除/禁用 downgrade reason 流程，低值请求返回不可降级错误。
- 保留 Sprint-24 Tag、盘点、审计和访问控制。
- 存量大屏以 `max(oldClassification,resolvedSources)` 建立初始事实。

## 影响范围

`ScreenResource.isDowngrade/updateClassification`、ClassificationSelect、SharePanel、旧测试和审计说明。

## 验证

- [ ] 旧客户端升密可用、降密拒绝。
- [ ] 存量大屏不会因迁移降低。

## 完成标准

- [ ] 全仓不存在大屏合法降密入口。
- [ ] 审计明确区分 manual floor 与 effective level。

## 编码进展

已将旧密级编辑收敛为人工下限命令，删除前端降级原因流程，低于当前有效密级的请求返回
冲突；审计同时记录 manual floor 与 effective level。统一测试延后。
