# T02: API 与页面能力迁移矩阵

**优先级**: P0  
**状态**: DONE  
**依赖**: T01

## 目标

把旧 `dts-metrics` API 能力映射到平台新页面和平台 API，不从后端模块视角盲目删除。

## 技术设计

- 识别旧 API：`/api/metrics/visual-assets`、`/api/metrics/graphs`、`/api/metrics/models/{modelId}/*`、`/api/internal/metrics/model-validation`。
- 识别平台接管面：`/modeling/metric-workbench`、`/modeling/semantic/*`、`/bi/*` analytics 消费页面、`/golden-chains/*`。
- 标记三类结果：
  - `MIGRATE_NOW`: Sprint-53 必须接住。
  - `KEEP_TEMPORARY`: 当前仍有调用或回滚价值，暂留源码。
  - `DELETE_LATER`: Sprint-55 物理删除候选。
- 输出到 `assets/runtime-api-migration-register.md`。

## 影响范围

- `source/dts-metrics/**`
- `source/dts-metrics-webapp/**`
- `source/dts-platform-webapp/src/pages/modeling/**`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/SemanticModelingResource.java`

## 验证

- [x] 所有旧 metrics API 有目标页面或暂留理由。
- [x] 新增平台 API 缺口必须绑定具体页面验收项。

## 完成标准

- [x] 不因退役破坏模型校验、发布、BI 注册或血缘注册路径。
- [x] 物理删除项明确延后到 Sprint-55。
