# F3：数据指标真实化

**优先级**：P0  
**状态**：CODE_COMPLETE（最终 Review、构建、部署与 E2E 待 F5）

## 目标

指标管理员在现有页面完成真实指标查询、新建、校验、保存、引用、发布、版本查看和归档。

## 契约

复用 `/governance/indicators/**` 的 definition/version/reference/run/template/publish-preview；不恢复旧 semantic metric 或第二套指标中心。

| Task | 状态 |
|---|---|
| T01 接入真实指标目录 | CODE_COMPLETE |
| T02 接入编辑校验保存发布 | CODE_COMPLETE |
| T03 接入引用版本七态与测试 | CODE_COMPLETE |

## 当前编码证据（2026-08-03）

- 当前实现以原型的复合、派生、原子指标、修饰词和时间周期页面结构为基线，目录事实来自既有 Governance Indicator owner。
- 指标创建、保存、校验、发布和归档走真实 API；无 owner 的动作不显示为可用功能。
- 加载、空态、错误、权限和提交中状态均显式呈现，不回退客户示例。
- 代码和契约测试已完成；当前 bundle 尚未完成最终 Review、构建、部署及真实浏览器 E2E。
