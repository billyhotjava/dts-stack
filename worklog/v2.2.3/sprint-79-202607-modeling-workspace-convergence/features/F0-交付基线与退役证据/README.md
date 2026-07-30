# F0：交付基线与退役证据

**优先级**：P0  
**状态**：IN_PROGRESS

## 目标

恢复可重复的认证验收链，补齐代表数据和旧入口观测，使后续 UI 合并与删除决策都有真实证据。

## 契约

| 类型 | 契约 | 要点 |
|---|---|---|
| 认证验收 | Keycloak → DTS portal → protected API/page | 证据不保存凭据 |
| 兼容观测 | `POST /api/modeling/compatibility-usage` | body=`route,target,result`；route/target 服务端 allowlist |
| 数据 | `modeling_legacy_api_usage` | 复用 route/http_method/result/occurred_at；tenant/caller 服务端写入 |
| 退役报告 | `assets/architecture-assessment.md` | 代码、页面、路由、表四级删除门禁 |

## UI/UX

兼容页仍然无感重定向；只有无法映射旧对象时显示 recovery。新增观测不得引入弹窗或阻塞跳转。

## Tasks

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | 恢复认证验收与代表 Demo 数据 | IN_PROGRESS | - |
| T02 | 建立客户画像与兼容访问观测 | DRAFT | T01、客户只读环境 |
| T03 | 删除确认无引用的孤儿代码 | DONE | T01 认证子门禁、GitNexus + current HEAD |

## Definition of Ready

- [x] F0 的运行路径、数据表与安全边界已确定。
- [x] T01 不依赖产品功能改造，可先执行。
- [ ] T02 等待客户只读环境/日志窗口。
- [x] T03 使用 GitNexus LOW 影响、current HEAD 全仓引用和完整 webapp build 三重证据完成。

## 完成标准

- [x] protected API 与正式工作台在系统 Chrome 150 有真实证据，且一次性身份无残留。
- [ ] Chrome 95 兼容证据在 F1 壳层实现后补齐。
- [ ] 项目/财务 Demo 覆盖四类表和至少一个 DEV Candidate。
- [ ] 旧入口观测可按 tenant/route/time window 查询。
- [x] Batch A 删除通过 focused test、build 和影响审计。
