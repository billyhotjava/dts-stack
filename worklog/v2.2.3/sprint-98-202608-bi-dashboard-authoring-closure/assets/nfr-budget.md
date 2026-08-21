# 非功能预算（Gate G1）

**依据**：`assets/domain-profile.md` 及现有 Dashboard publication 上限。

| 维度 | 预算 | 可执行适应度函数 | 归属 | 状态 |
|---|---|---|---|---|
| 组件规模 | 每看板 ≤50 组件、≤20 参数 | 既有 publication service test + UI 上限测试 | F1/F2 | PASS |
| 目录调用 | 发布抽屉每次会话最多各加载 org/role 1 次 | mock E2E 统计请求次数 | F1/T01 | PASS |
| 保存一致性 | validate 前最近一次 save 必须成功；失败时 validate=0 次 | mock E2E 请求序列断言 | F1/T01 | PASS |
| 查询效率 | 保存预检按去重 card ID 批量查询，禁止逐组件 N+1 | Mockito 断言 `findAllById` 单次 | F1/T02 | PASS |
| 批量上限 | 拖入/按钮添加均不得超过 50 | model/source contract | F2/T01 | PASS |
| 权限/密级 | 空范围、密级降级、非 published analysis 均 fail-closed | publication/resource tests | F1 | PASS |
| 兼容性 | Chrome 95 禁用 API 静态检查；现代 Chrome 无 console/page error | dts-chrome95 + Playwright | F3/T01 | PASS_WITH_ENV_NOTE |
| 延迟 | N/A：无新增查询执行路径，目录和保存沿用既有 API | — | — | N/A |
| schema/index | N/A：复用现有表/主键和 dashboard_id 查询 | — | — | N/A |

## 未达标项处置

所有可在当前环境执行的预算项已关闭；真实 Chrome 95 环境仍缺失，因此兼容性只记 `PASS_WITH_ENV_NOTE`，不冒充实机通过。
