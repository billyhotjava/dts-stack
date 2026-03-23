# T01: 看板 URL 参数驱动 Drill

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
看板页面支持通过 URL 参数 `drillTarget` 自动打开 DrillDownDrawer

## 技术设计

### 1. URL 参数扩展
在 `projectCockpitQueryState.ts` 的 `parseProjectCockpitQueryState` 中：
- 新增解析 `drillTarget` 参数（可选）
- 新增解析 `drillDept` / `drillReason` 参数

### 2. ProjectCockpitPage 自动触发 Drill
在 `ProjectCockpitPageContent` 的 useEffect 中：
- 检查 URL 中是否有 `drillTarget`
- 有则调用 `openDrill(target, { dept, reason })`
- 触发后从 URL 中移除 `drillTarget` 参数（避免刷新重复打开）

### 3. URL 格式示例
```
/analytics/project-cockpit?theme=risk&drillTarget=high-risk
/analytics/project-cockpit?theme=risk&drillTarget=delay-reason&drillDept=质量科&drillReason=technical
```

## 影响范围
- `projectCockpitQueryState.ts` — 解析 drillTarget
- `ProjectCockpitPage.tsx` — useEffect 自动触发
- `ProjectCockpitContext.tsx` — openDrill 调用

## 验证
- [ ] 直接访问带 `drillTarget=high-risk` 的 URL → Drawer 自动打开
- [ ] Drawer 关闭后 URL 参数被清理
- [ ] 不带 drillTarget 时行为不变

## 完成标准
- [ ] 看板可被外部 URL 驱动打开指定 Drill
