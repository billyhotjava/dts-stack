# F3: 创建对话框强制选择密级

**优先级**: P0
**状态**: READY

## 目标

新建大屏时强制要求选择密级，从源头消除 `classification=null` 裸屏。前后端双重防御：前端必填 + 后端 create endpoint 校验。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 创建大屏对话框加密级 select（必填 + 默认 INTERNAL placeholder） | P0 | READY | F1/T01 |
| T02 | 前端提交校验：classification 为空时按钮 disabled + 红色提示 | P0 | READY | T01 |
| T03 | 后端 `POST /api/screens` 加 classification 校验：null/blank → 400 | P0 | READY | - |
| T04 | 写一组单元测试覆盖 T03 的校验路径 | P1 | READY | T03 |

## 完成标准

- [ ] 创建大屏对话框（Modal / Drawer）有「大屏密级 *」字段，required。
- [ ] 不选密级时「确定」按钮 disabled。
- [ ] 默认选中 INTERNAL，但 placeholder 显示明显「请确认密级」字样，要求用户主动确认或更改（防止盲点确认）。
- [ ] 后端 `POST /api/screens`：当 body.classification 为 `null` / `""` / 不在 PUBLIC/INTERNAL/SECRET/CONFIDENTIAL 集合时，返回 `400 {"error": "classification is required and must be one of PUBLIC/INTERNAL/SECRET/CONFIDENTIAL"}`。
- [ ] 创建成功后大屏 `classification` 字段已落库，列表卡片立即显示对应 Tag。
- [ ] 单元测试覆盖：缺字段 / 空字符串 / 错误值 / 大小写（"public" 应被接受并 normalize 成 "PUBLIC"）。

## 关键文件

- 改：创建大屏对话框组件（前端，预计在大屏列表页或 ScreenWizard）
- 改：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenResource.java:733-820`（create 端点）
- 改：`source/dts-analytics/src/test/java/com/yuzhi/dts/analytics/web/rest/`（新增 ScreenResourceCreateClassificationTest）

## 注意

- **不改老数据**：只对新创建大屏强制；老裸屏靠 F4 盘点 + owner 主动补登。
- 后端 normalize：`classification.trim().toUpperCase(Locale.ROOT)`，与 `updateClassification` 端点（`ScreenResource.java:1283`）已有逻辑保持一致。
- create 时如果用户在 body 里漏传 classification，response 错误信息要明确指引"创建大屏必须选择密级"——便于前端展示给最终用户。
