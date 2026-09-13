# F5：信息架构与路由收敛

**优先级**：P1

**状态**：CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E

**证据**：`../../assets/implementation-evidence-20260810.md`

## 目标

在不新增平行能力 owner 的前提下，用统一“数据架构”入口重组现有架构字典、模型、资产、指标与质量导航，并把模型目录改为可搜索、多选的 Table 工作区。

## Task

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 收敛数据架构导航、模型 Table 与兼容路由 | CODE_COMPLETE / LOCAL_VERIFIED_NON_E2E | 真实菜单 E2E 仍阻塞 |

## Feature DoD

- [ ] ADR-86-09 的菜单例外、页面 owner 和旧路由映射完整落地。
- [ ] 真实菜单点击、旧深链、窄屏、四态和 Chrome 95 验收通过。
- [ ] 页面只消费真实 API/权限/审计契约，不新增第二套写入口。
