# F3: 建模 API 契约与兼容入口

**优先级**: P0
**状态**: IN_PROGRESS（REST + PostgreSQL 集成、权限、发布门禁和旧读取回归已通过，OpenAPI 生成物待部署验收）

## 目标

建立 `/api/modeling/vnext/*` 新版本 API，支持对象、规划、ModelSpec、dbt 导入、编译、漂移和运行，同时保持旧 `/api/modeling/*` 与 `/api/semantic/*` 兼容。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 对象、规划与 ModelSpec CRUD 契约 | P0 | DONE | F1-T01 |
| T02 | dbt 产物、manifest 导入与漂移 API | P0 | DONE | T01 |
| T03 | 编译、运行、血缘和证据 API | P0 | DONE | T01,T02 |
| T04 | OpenAPI/契约测试与旧接口兼容 | P0 | IN_PROGRESS | T01,T02,T03 |

## 完成标准

- [ ] API 覆盖普通建模、dbt 原生登记和运行证据三类场景。
- [x] 请求校验、权限、错误码、幂等键和 revision 规则明确。
- [x] 旧 `/api/semantic/models` 与 `/api/semantic/business-objects` 读取路由回归测试通过。
