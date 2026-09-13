# F2: 会话安全 P0 整改

**优先级**: P0
**状态**: READY

## 目标

闭合协议 2.3.2.10（安全保密）会话控制项中 sprint-22 评审遗留的 4 项前端 P0。后端 inactivity filter + 30 分钟超时 + session registry 已落地，但前端仍存在裸 token 持久化、生产 console 打印 Authorization、测试旁路随生产发布、无操作不锁屏等泄露面与认证绕过风险，机密级测评不可接受。本 feature 只闭合前端泄露面与旁路，不改既有后端会话契约。

## 协议依据与缺口

- 协议条款：2.3.2.10-4（衍生整改项·会话控制），对照 BMB17.1/17.2-2024 机密级最低控制；证据底稿 `assets/gap-evidence/M10-安全保密.md`（会话控制行：🟡 部分）。
- 当前缺口（带证据，源自 `worklog/v2.2.3/sprint-22-202604/review/session-management-audit.md`）：
  - admin/portal 双 token 裸存 localStorage：`dts-platform-webapp/src/store/userStore.ts:64,91`（zustand persist → `createJSONStorage(() => localStorage)`），admin token 在前端只写不读；`dts-admin-webapp/src/store/userStore.ts` 同款。
  - 生产 console 打印 Authorization：`dts-platform-webapp/src/api/apiClient.ts:320,331`（请求/响应整体落 console，含 `config.headers.Authorization` 与 `res.data`）。
  - `TEST_SESSION_ENABLED` 旁路随生产构建：`apiClient.ts:43,503`（`VITE_TEST_LONG_SESSION/VITE_TEST_SESSION` 令 session 失效被忽略）。
  - `handleDevFallback` 前端伪造账号：`userStore.ts:280`（`VITE_DEV_LOGIN_FALLBACK` 自签 `dev-access-*` 假 token 并赋 `ROLE_OP_ADMIN`，调用于 `userStore.ts:250`）。
  - 后端基线：`source/dts-platform/.../security/session/PortalSessionInactivityFilter.java` + `PortalSessionRegistry.java`，超时 `application.yml:340 timeout-minutes=30`。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 前端裸 token 整改：停止持久化裸 token，迁移 httpOnly cookie / 受控内存（双端） | P0 | READY | — |
| T02 | 生产构建 strip 所有 `console.log(Authorization)` 及敏感日志 | P0 | READY | — |
| T03 | 生产 profile 硬关闭 `TEST_SESSION_ENABLED` 与 `handleDevFallback` 旁路 + 启动断言 | P0 | READY | — |
| T04 | 前端 idle 软锁：与后端 30min inactivity 对齐，无操作锁屏需重认证 | P0 | READY | T01 |
| T05 | 集成/E2E 测试：token 不落盘、无 Authorization 日志、prod 拒旁路、idle 软锁 | P0 | READY | T01-T04 |

## 完成标准

- [ ] 前端不再以明文持久化 portal/admin 裸 token；admin token 完全不进前端，portal token 迁 httpOnly cookie 或仅留短寿命内存标识。
- [ ] 生产 bundle 内无任何打印 Authorization/X-Portal-Access-Token/响应体的 console 语句。
- [ ] 生产 profile 下 `TEST_SESSION_ENABLED` 与 `handleDevFallback` 编译期 strip 或运行期 fail-fast，存在即拒绝启动/构建。
- [ ] 无操作 30 分钟触发前端软锁，与后端 inactivity 对齐，解锁需重新认证。
- [ ] 全部改动 TDD，覆盖率 ≥80%，会话/口令/旁路路径分支覆盖。

## TDD 约定

- 每个 task RED→GREEN→REFACTOR，测试先行；覆盖率 ≥80%，鉴权/口令/会话路径要求分支覆盖。
- 改既有 symbol 前先 `gitnexus_impact`，提交前 `gitnexus_detect_changes()`；Java 侧禁用 `Optional.get()`，统一 `orElseThrow()`。
