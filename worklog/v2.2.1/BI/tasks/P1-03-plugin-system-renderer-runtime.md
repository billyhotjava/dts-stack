# P1-03 插件化渲染体系与运行时解耦

`status`: `done`  
`priority`: `P1`  
`inspiration`: `Superset(可插拔 Viz 插件) + Metabase(稳定核心)`

## 目标

新组件接入不改核心编辑器与运行时主流程，降低后续开发成本。

## 子任务

1. 插件协议
- `RendererPlugin`、`PropertySchema`、`DataContract` 定义。

2. 注册机制
- 通过 registry 注册组件与数据映射器。

3. 沙箱策略
- 插件渲染失败不拖垮整屏，显示组件级降级卡片。

4. 开发模板
- 提供插件脚手架与最小示例（line/table/kpi）。

## 验收标准

- 新增一个插件组件时，不修改核心 `ScreenDesignerPage` 即可渲染。
- 插件异常时，页面其余组件正常。

## 风险与回滚

- 风险：插件边界不清导致核心依赖反向耦合。  
- 回滚：强制插件 API 白名单，CI 校验禁止跨层引用。

## 实现记录（2026-02-14）

- 插件协议落地：
  - 新增 `RendererPlugin` / `PropertySchema` / `DataContract` 类型定义。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/types.ts`
- 注册机制落地：
  - 新增插件注册中心（注册/查询/运行时ID）。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/registry.ts`
- 运行时解耦：
  - `ComponentRenderer` 支持读取组件 `__plugin` 元数据并按 registry 渲染，不改 `ScreenDesignerPage` 主流程。
  - 未加载插件时自动降级到基础组件渲染并提示“插件未加载，已降级”。
- 沙箱策略：
  - 新增 `PluginRenderBoundary`，插件渲染异常仅影响当前组件，页面其余组件不受影响。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/PluginRenderBoundary.tsx`
- 开发模板与示例：
  - 新增插件目录 README 与示例适配器（KPI/趋势/表格）。
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/README.md`
  - 路径：`source/dts-analytics-webapp/modern/src/pages/screens/plugins/builtinPluginAdapters.tsx`
  - 新增插件脚手架命令：
    - `pnpm scaffold:screen-plugin -- --plugin-id <id> --component-id <id> --component-name <name>`
    - 自动生成 `plugins/custom/*.tsx` 适配器模板与 `.manifest.json` 草稿。
  - 路径：`source/dts-analytics-webapp/modern/scripts/scaffold-screen-plugin.mjs`
- 清单协议增强：
  - `screen-plugins` 清单增加 `propertySchema/dataContract`；
  - Demo 插件新增 `table-matrix` 组件。
  - 路径：`source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenPluginResource.java`
- 设计器属性面板接入插件 schema：
  - `PropertyPanel` 新增插件配置区，按 `propertySchema.fields` 动态渲染表单；
  - 支持 `string/number/boolean/color/array/json` 字段编辑；
  - 插件组件新增属性无需改核心属性面板 `switch-case`。
- 插件组件接入弹性增强：
  - 组件库不再对插件 `baseType` 做前端硬编码白名单过滤，支持后续扩展类型平滑接入；
  - 后端插件校验由“固定类型白名单”调整为“合法命名规范校验”（`^[a-z][a-z0-9-]{1,63}$`）；
  - 组件库“常用/最近”键改为插件唯一键（`pluginId:componentId@version`），避免同名组件冲突。
- 插件清单加载统一化：
  - 新增 `manifestLoader` 作为共享加载器（缓存 + 并发复用）；
  - 组件库与运行时插件注册共用同一清单来源，减少重复请求与状态不一致。
- 自定义插件自动发现（2026-02-15）：
  - 新增 `plugins/custom/*.tsx` 自动扫描并按文件名 `{pluginId}__{componentId}.tsx` 注册；
  - 运行时优先按后端清单匹配插件，再自动加载自定义适配器，无需手工改内置映射表；
  - 脚手架生成模板已统一导出 `createPlugin(pluginId, componentId, version)`，可与清单版本自动对齐。
- 清单加载容灾增强（2026-02-16）：
  - `manifestLoader` 在远端 `screen-plugins` 接口失败时，自动降级到本地 `plugins/custom/*.manifest.json`；
  - 支持本地清单单文件多插件格式（`{ plugins: [...] }`）；
  - 本地清单中的同名组件定义可覆盖远端定义，便于快速联调；
  - 现场后端不可达或联调阶段，组件库与运行时仍可使用本地插件清单继续开发与验收。
- 插件清单校验脚本（2026-02-17）：
  - 新增 `pnpm validate:screen-plugins`，校验 `plugins/custom/*.manifest.json`；
  - 覆盖插件/组件 ID 规范、版本 semver、运行时组件 ID 重复、默认宽高缺失告警；
  - 可直接接入 CI 做插件接入门禁，降低“脏清单”导致的运行时故障。
- 构建门禁接入（2026-02-19）：
  - 前端构建脚本新增 `prebuild`，自动执行 `validate:screen-plugins`；
  - 本地构建与 CI 默认具备插件清单校验，减少漏检概率。
- 插件边界门禁补强（2026-02-20）：
  - 新增 `validate:screen-plugin-boundary` 脚本，扫描 `plugins/custom/*.ts(x)` 导入边界；
  - 校验相对导入不得逃逸 `src/pages/screens/plugins`，禁止直接引用 `pages/screens/components/*` 内核实现；
  - `prebuild` 升级为“清单校验 + 边界校验”双门禁，降低插件反向耦合风险。
- 清单与适配器一致性门禁（2026-02-20）：
  - `validate-screen-plugin-manifests` 增加“manifest 组件是否存在本地适配器文件”告警；
  - 约定映射：`pluginId:componentId` ↔ `plugins/custom/{pluginId}__{componentId}.tsx/.ts`；
  - 降低“清单可见但运行时无渲染器”导致的现场空白组件风险。
- 插件属性 schema 能力增强（2026-02-20）：
  - `PropertySchemaField` 新增 `select` 类型，支持 `options` 下拉配置；
  - 新增字段约束扩展：`description/placeholder/min/max/step/options`；
  - 属性面板按 schema 渲染下拉框并保留原始值类型（string/number/boolean）。
  - 插件脚手架模板升级：
    - 默认生成 `mode(select)` 示例字段，开箱即可验证“schema 驱动下拉配置”。
  - 后端 demo 插件清单补齐 `select` 示例字段（`compact-trend.displayMode`），用于联调验证前后端一致性。
- 插件清单门禁补强（2026-02-20）：
  - `validate-screen-plugin-manifests` 增加 `propertySchema.fields` 结构校验；
  - 校验字段类型白名单（含 `select`）与 `select.options` 合法性，提前拦截脏清单。
- 清单加载器运行时校验增强（2026-02-17）：
  - `manifestLoader` 增加远端/本地清单规范化：
    - 非法插件 ID/组件 ID/数据源 ID 直接跳过；
    - 插件组件与数据源重复 ID 自动去重并告警；
    - 插件版本非 semver 给出警告但不阻断加载；
  - 降低“远端返回脏清单”导致组件库与运行时崩溃风险。
- 清单加载器 schema 容灾增强（2026-02-20）：
  - `manifestLoader` 新增 `propertySchema.fields` 运行时规范化：
    - 非法字段类型/缺失 key 的字段自动跳过并告警；
    - `select` 字段无有效 `options` 自动跳过；
    - 字段 key 重复时保留首个，避免属性面板冲突。
  - 远端清单异常时仍可保证属性面板稳定渲染，不拖垮编辑器主流程。
