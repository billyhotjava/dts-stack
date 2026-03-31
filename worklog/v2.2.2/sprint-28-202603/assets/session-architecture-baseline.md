# Sprint-28 Session Architecture Baseline

## 目标形态

前端长期只保留两类独立应用：

- `platform` 域：`dts-platform-webapp`
- `admin` 域：`dts-admin-webapp`

`dts-analytics-webapp/modern` 不再作为长期前端建设对象，只在迁移期内作为 `platform` 域的兼容消费者，最终删除。

## Domain 矩阵

| 应用 | Session Domain | Storage Namespace | 登录态关系 | 备注 |
|------|----------------|------------------|------------|------|
| `dts-platform-webapp` | `platform` | `dts.platform.session.*` | 独立 | analytics 最终唯一 UI 入口 |
| `dts-admin-webapp` | `admin` | `dts.admin.session.*` | 独立 | 仅共享技术栈，不共享浏览器 session key |
| `dts-analytics-webapp/modern` | `platform` | `dts.platform.session.*` | 迁移期共享 | 目标是退场，不新增长期逻辑 |

## Core 原则

1. 一个 domain 只允许一个 proactive refresh scheduler。
2. 所有 401 refresh 必须走同一个 single-flight 锁。
3. logout、idle timeout、session conflict、refresh failed 必须共享同一套 reason 枚举与广播协议。
4. redirect 必须由统一 builder 生成，保留完整当前 route，不允许调用方自行拼接 `pathname`。
5. 新协议只写 domain namespace；旧 `dts.session.*` 仅在兼容期只读迁移。

## 模块分层

- `session-core`
  - refresh coordination
  - token persistence adapter
  - activity / idle timeout
  - cross-tab broadcast
  - redirect intent builder
  - logout reason state machine
- app adapter
  - 当前路由读取
  - 登录页 URL 生成
  - userStore 读写
  - UI toast / 文案
- app callers
  - `apiClient`
  - `SessionManager`
  - `analyticsApi`
  - `platformSession` 仅迁移期保留

## 迁移顺序

1. 先定义 domain 协议和 shared core 边界。
2. 先接 `platform`，因为当前线上问题在这里。
3. 再接 `admin`，切断与 `platform` 的 localStorage 冲突。
4. 最后拆 `modern` 的 runtime/build/test 依赖，并正式删除前端代码。

## modern 删除门禁

只有同时满足以下条件，`modern` 才能直接删除：

- compose/dev-up/dev-stop 不再启动 `dts-analytics-webapp-modern`
- `builds/dts-build.sh` 不再构建 `dts-analytics-webapp-modern`
- `dts-platform-webapp` 不再代理或回跳到 `modern` UI
- e2e / smoke / 发布文档不再依赖 `source/dts-analytics-webapp/modern`
- analytics 全量入口、分享页、drill 页都已由 `platform` 承载
