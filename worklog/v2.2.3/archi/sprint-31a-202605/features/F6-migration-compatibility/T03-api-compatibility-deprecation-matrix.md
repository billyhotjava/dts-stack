# T03: API 兼容和弃用矩阵

**优先级**: P0
**状态**: DONE
**依赖**: F2-F4

## 目标

定义旧 Catalog、semantic、analytics 权限 API 的兼容、代理和弃用策略。

## 技术设计

- 标注继续支持、兼容代理、只读 fallback、弃用。
- 明确 `/api/semantic/**` 在 Sprint-32 中的代理行为。
- 给前端和服务调用方提供迁移路径。

## 影响范围

- dts-platform API
- dts-platform-webapp
- dts-metrics
- dts-analytics

## 验证

- [x] 不存在无归属的旧 API。
- [x] 兼容代理失败时错误明确。

## 完成标准

- [x] 后续迁移不会靠猜测。

## 证据

- `worklog/v2.2.3/sprint-31a-202605/assets/api-compatibility-deprecation-matrix.md`
