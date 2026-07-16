# T03: 旧 dbt 与旧语义模型兼容策略

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: T01

## 目标

允许新版本抛弃旧模型实现，但不破坏已有 dbt 项目、旧语义页面和运行数据。

## 技术设计

- 新 API 使用 `/api/modeling/*`，旧 `/api/semantic/*` 继续提供兼容视图。
- 旧模型导入后标记 `LEGACY_READONLY`，保存 `legacyRef` 和原始 dbt unique id。
- 旧模型只允许查看、运行和登记；新设计器不能覆盖旧 SQL。
- 新模型使用独立 ModelSpec revision，必要时提供表名或数据集别名。

## 影响范围

- `source/dts-platform-webapp/src/api/semanticModelingApi.ts`
- `source/dts-platform/src/main/java/.../SemanticModelingResource.java`
- 兼容映射服务、菜单跳转和 source-contract。

## 验证

- [ ] 旧接口返回结果与现有页面契约保持兼容。
- [x] 旧 dbt 模型导入后能在新台账中标识来源和只读状态。
- [x] 新版本删除或重建 ModelSpec 不会删除旧 dbt 文件。

## 完成标准

- [x] 形成兼容矩阵和回滚说明，纳入发布审核。
