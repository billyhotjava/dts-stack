# F5: 密级降级二次确认（可选）

**优先级**: P2
**状态**: READY

## 目标

当用户把密级**从高到低**修改时（如 SECRET → INTERNAL），弹二次确认，要求填写降级原因，原因写入审计。鼠标手抖一秒就降密的风险消除。

P2 优先级，本 sprint 视进度决定是否纳入；如时间紧张可移到下一个 sprint。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 前端在 ClassificationSelect onChange 中检测降级方向 | P2 | READY | F1/T01 |
| T02 | 弹 confirm modal：要求填降级原因（必填，>=10 字符） | P2 | READY | T01 |
| T03 | 后端 `PATCH /api/screens/{id}/classification` 接受 `reason` 字段，写入审计 payload | P2 | READY | - |

## 完成标准

- [ ] 升级（如 INTERNAL → SECRET）：直接生效，无二次确认。
- [ ] 同级修改（如 INTERNAL → INTERNAL）：不生效（已有逻辑：`updateClassification` line 1308 直接 changed=false 返回）。
- [ ] 降级（如 SECRET → INTERNAL）：
  - 前端弹 modal，标题「确认降低密级」。
  - 必填「降级原因」textarea（>=10 字符）。
  - 用户确认后才发请求；取消则恢复原值。
- [ ] 后端 PATCH endpoint 接受可选 `reason` 字段，写入审计 `screen.classification.update` 的 payload `before/after/reason` 三个字段。

## 关键文件

- 改：`source/dts-platform-webapp/src/analytics/pages/screens/components/ClassificationSelect.tsx`
- 改：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java:1283-1320`

## 注意

- 升级路径不需要 reason，避免日常操作变重。
- "降级方向"判定基于密级阶梯 PUBLIC < INTERNAL < SECRET < CONFIDENTIAL（已在 ScreenPermissionService.CLASSIFICATION_LADDER 定义）。
- 如不纳入本 sprint，可作为单独议题；这个 feature 跟 F1-F4 解耦，不阻塞前面四项。
