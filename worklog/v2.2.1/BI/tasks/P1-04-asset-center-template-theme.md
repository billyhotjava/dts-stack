# P1-04 模板/主题/资产中心

`status`: `done`  
`priority`: `P1`  
`inspiration`: `DataEase(模板资产化) + Metabase(分析资产复用)`

## 目标

把组件与模板从“项目内配置”升级为“可复用资产”。

## 子任务

1. 模板中心
- 支持模板分类、标签、版本、上架/下架。

2. 主题中心
- 主题包包含颜色、字体、背景、组件默认样式。

3. 资产复用
- 支持从已有大屏一键“存为模板”并二次编辑。

4. 权限治理
- 模板可见范围：个人/团队/全局。

## 验收标准

- 新建页面可直接从模板中心创建。
- 应用主题后，组件样式批量切换且可回退。
- 模板版本可追溯，支持按版本恢复。

## 风险与回滚

- 风险：主题字段扩展影响历史模板。  
- 回滚：模板入库时自动补齐默认主题字段。

## 实现记录（2026-02-14）

- 已完成后端资产中心主链路首批实现：
  - 模板新增字段：`visibilityScope`（个人/团队/全局）、`listed`（上架/下架）、`ownerDept`、`themePackJson`、`sourceTemplateId`。
  - 新增模板版本表：`analytics_screen_template_version`，支持快照追踪与版本恢复。
  - `ScreenTemplateResource` 新增接口：
    - `GET /api/screen-templates/{id}/versions`
    - `POST /api/screen-templates/{id}/restore/{versionNo}`
    - `PUT /api/screen-templates/{id}/listing`
  - 列表/详情读取增加可见范围与上下架控制；支持 `tag/visibility/listed` 查询。
- 已完成前端模板市场首批能力：
  - 模板筛选新增“可见范围/上架状态”。
  - 资产模板卡片展示：上架状态、可见范围、模板版本。
  - 资产模板新增操作：上架/下架、版本恢复。
  - 导入/导出模板包透传 `visibilityScope/listed/themePack`。
- 已验证：
  - `mvn -f source/dts-analytics/pom.xml -DskipTests clean compile` 通过。
  - `pnpm -C source/dts-analytics-webapp/modern typecheck` 与 `build` 通过。
- 主题中心补强（2026-02-15）：
  - 新增主题批量应用引擎 `applyThemeToComponents(mode=safe|force)`，覆盖图表、数值卡、表格、筛选器、容器、DataV 组件常用样式字段。
  - 工具栏新增“组件样式策略（强制覆盖/仅补缺省）”与“一键刷组件样式”入口。
  - 主题包导入新增“是否应用到组件”确认链路；主题包导出附带 `componentStyleMode` 元数据。
  - 筛选器组件渲染支持主题色字段（`labelColor/inputTextColor/inputBorderColor/inputBackground`），修复浅色主题可读性问题。
