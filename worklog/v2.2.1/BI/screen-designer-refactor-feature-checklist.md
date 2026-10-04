# 大屏重构功能清单（统一内核模式）

> 目的：把架构方案转成可执行 checklist，支持研发阶段快速推进。
>
> 日期：2026-02-13

---

## 1. 内核协议
- [ ] F-001 定义 `QuerySpec v2`（含 sourceType、params、securityContext、cachePolicy）
- [ ] F-002 定义 `VizSpec v2`（fieldMapping、style、behavior）
- [ ] F-003 定义 `ScreenSpec v2`（layout、components、variables、publish）
- [ ] F-004 为 v2 协议增加 JSON Schema 校验
- [ ] F-005 增加协议版本字段 `schemaVersion`

## 2. 设计器（bi-designer）
- [ ] F-101 画布读写改为 `ScreenSpec` 单一来源
- [ ] F-102 属性面板改为插件 schema 驱动
- [ ] F-103 图层面板支持插件组件统一显示
- [ ] F-104 模板导入导出改为 `ScreenSpec` 包
- [ ] F-105 主题包机制（颜色/字体/背景/组件默认样式）

## 3. 运行时（bi-runtime）
- [ ] F-201 查询调度器（并发控制、超时、重试）
- [ ] F-202 缓存策略（key、ttl、失效）
- [ ] F-203 联动执行引擎（变量传播、循环检测）
- [ ] F-204 错误归一（错误码、requestId）
- [ ] F-205 组件级降级渲染（错误不拖垮整屏）

## 4. 适配器（bi-adapters）
- [ ] F-301 `metabase-adapter`：Card -> QuerySpec
- [ ] F-302 `metabase-adapter`：结果元数据 -> DataFrame 标准
- [ ] F-303 `superset-adapter`：Chart 元数据 -> VizSpec
- [ ] F-304 `native-adapter`：DTS SQL/语义查询执行器
- [ ] F-305 adapter 契约测试（同输入同输出结构）

## 5. 插件体系（bi-plugins）
- [ ] F-401 定义 RendererPlugin 接口
- [ ] F-402 定义 DataSourcePlugin 接口
- [ ] F-403 ECharts 基础插件包（line/bar/pie/scatter/radar/funnel）
- [ ] F-404 表格插件（静态表 + 数据绑定 + 列映射）
- [ ] F-405 KPI/进度/时间等基础组件插件化

## 6. 发布与治理
- [ ] F-501 草稿/发布双轨模型
- [ ] F-502 版本历史与回滚
- [ ] F-503 Screen ACL（read/edit/publish/manage）
- [ ] F-504 安全分享（过期/口令/IP 白名单）
- [ ] F-505 操作审计（create/update/publish/rollback/share）

## 7. 数据与分析能力
- [ ] F-601 数据源统一入口（metric/dataset/sql/card/api）
- [ ] F-602 全局变量中心
- [ ] F-603 跨组件联动规则中心
- [ ] F-604 钻取链路标准化（InteractionSpec）
- [ ] F-605 语义层绑定（DatasetSpec/MetricSpec）

## 8. 质量保障
- [ ] F-701 内核协议单测
- [ ] F-702 adapter 契约测试
- [ ] F-703 设计器关键路径 E2E（新建-配置-发布-预览）
- [ ] F-704 兼容性回归（Chrome 95/109/最新）
- [ ] F-705 性能压测（50/100 组件）

## 9. 里程碑映射
- M0（内核打底）：F-001~005, F-101~103, F-201, F-301, F-401
- M1（能力对齐）：F-104~105, F-202~205, F-302~305, F-402~405, F-601~604
- M2（商用与差异化）：F-501~505, F-605, F-701~705, AI 草稿生成（另立任务）

