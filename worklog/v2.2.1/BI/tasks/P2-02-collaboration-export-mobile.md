# P2-02 企业协作、导出与多端适配

`status`: `in-progress`  
`priority`: `P2`  
`inspiration`: `企业 BI 协作实践 + DataEase 交付便利性`

## 目标

从“单人设计工具”升级为“团队协作交付平台”。

## 子任务

1. 导出能力
- 导出 PNG/PDF/JSON；支持导入 JSON 复用。

2. 协作能力
- 评论/批注、变更记录、冲突提示（先轻协作，后实时协作）。

3. 多端适配
- PC/平板/手机布局策略（断点 + 缩放 + 组件可见性规则）。

4. 版本对比
- 支持版本差异查看（布局/组件/数据源变更）。

## 验收标准

- 大屏可导出 PNG/PDF 并保持视觉一致。
- 移动端可读，关键 KPI 与图表无遮挡。
- 两个版本可展示差异摘要。

## 风险与回滚

- 风险：多端布局规则过于复杂。  
- 回滚：先支持独立移动模板，不强制自动响应式。

## 实现记录（2026-02-14）

- 已完成首批交付能力：
  - 导出/导入：
    - `ScreenHeader` 新增 `JSON` 导出与 `JSON` 导入；
    - 新增 `PNG` 导出（实验路径：画布 DOM 转 PNG）；
    - 新增 `PDF` 导出（基于导出图像打开打印窗口）。
  - 多端适配：
    - 组件新增 `config.visibleOn`（`pc/tablet/mobile`）可见规则；
    - 属性面板新增多端可见配置；
    - 预览页与公开页按断点自动识别设备并过滤组件显示（`<=768: mobile`, `<=1200: tablet`, 其余 `pc`）。
  - 版本对比：
    - 后端新增 `GET /api/screens/{id}/versions/compare?fromVersionId=&toVersionId=`；
    - 返回组件与变量差异摘要（新增/移除数量、类型变化）；
    - 前端 `ScreenHeader` 增加“版本对比”入口与摘要弹窗。
- 已验证：
  - `mvn -f source/dts-analytics/pom.xml -DskipTests clean compile` 通过。
  - `pnpm -C source/dts-analytics-webapp/modern typecheck` 与 `build` 通过。
- 轻协作评论/批注（2026-02-15）：
  - 后端新增评论接口（基于现有审计流，不新增表）：  
    - `GET /api/screens/{id}/comments`  
    - `POST /api/screens/{id}/comments`  
    - `POST /api/screens/{id}/comments/{commentId}/resolve`  
    - `POST /api/screens/{id}/comments/{commentId}/reopen`
  - 前端新增“协作批注中心”面板：
    - `ScreenHeader` 增加“协作”入口；
    - 支持按组件添加评论、查看状态、标记已解决/重新打开；
    - 新增轻量冲突提示（草稿更新时间漂移检测）。
- 待继续：
  - 冲突提示当前仍是轻模式（后续可补多人实时锁提示）。
  - PNG/PDF 仍是浏览器侧轻实现，后续可升级为服务端一致性渲染导出。
