# T02: 大屏模板配置 jump-url action

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标
为大屏模板的表格和 KPI 卡片配置 `jump-url` action，点击跳转到看板对应页面

## 技术设计

### 1. 表格行跳转
为以下表格配置 action：

| 表格 | 跳转 URL |
|------|---------|
| 第1屏 重点预警清单 | `/analytics/project-cockpit?theme=overview&majorProjectId={{majorProjectName}}` |
| 第2屏 到期与延期任务 | `/analytics/project-cockpit?theme=execution&drillTarget=overdue` |
| 第3屏 拖期项目清单 | `/analytics/project-cockpit?theme=risk&drillTarget=delay-reason&drillDept={{dept}}` |

配置方式（在模板 card 定义中）：
```typescript
actions: [{
  type: 'jump-url',
  urlTemplate: '/analytics/project-cockpit?theme=risk&drillTarget=overdue',
  openMode: 'new-tab',
}]
```

### 2. KPI 卡片跳转
- 第1屏"已到时间节点"→ 看板总览 + drillTarget=completion
- 第2屏"未完成高风险"→ 看板风险 + drillTarget=high-risk
- 第3屏"不正常待变更"→ 看板风险 + drillTarget=overdue

### 3. 排名柱状图跳转
- 柱状图点击某项目 → `set-variable` + `jump-url`
- 提取 `majorProjectName` → 跳转看板 tree 页

## 影响范围
- `projectManagementCommandCenterTemplate.ts` — 各组件增加 `actions` 配置

## 验证
- [ ] 预警表行点击 → 新标签打开看板，majorProjectId 预填
- [ ] KPI 点击 → 新标签打开看板 + Drawer 展开
- [ ] 排名柱状图点击 → 打开看板项目树

## 完成标准
- [ ] 所有可交互的表格和 KPI 卡片都有跳转配置
