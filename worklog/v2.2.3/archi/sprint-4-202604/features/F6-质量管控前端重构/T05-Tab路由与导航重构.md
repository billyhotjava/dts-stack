# T05: Tab 路由与导航重构

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
将现有 4 个 Tab（质量规则/规则模板/清洗函数/数据编辑）重构为 5 个工作流 Tab（概览/质量规则/检查任务/质量报告/数据修复）。

## 技术设计

### 路由变更
```
现有：
  /governance/quality?tab=rules
  /governance/quality?tab=templates
  /governance/quality?tab=cleansing
  /governance/quality?tab=editing

改为：
  /governance/quality?tab=overview
  /governance/quality?tab=rules
  /governance/quality?tab=tasks
  /governance/quality?tab=report
  /governance/quality?tab=repair
```

### Tab 组件映射
```tsx
const tabs = [
  { key: 'overview', label: '概览',      component: QualityDashboard },
  { key: 'rules',    label: '质量规则',   component: QualityRulesPage },
  { key: 'tasks',    label: '检查任务',   component: QualityTasksTab },
  { key: 'report',   label: '质量报告',   component: QualityReportTab },
  { key: 'repair',   label: '数据修复',   component: DataRepairTab },
];
```

### 废弃处理
- "规则模板"Tab 删除，模板功能内嵌到规则创建流程
- "清洗函数"Tab 删除，清洗配置内嵌到规则的"修复策略"中
- "数据编辑"Tab 重命名为"数据修复"，功能大幅扩展

## 影响范围
- `QualityPage.tsx`（或等效的 Tab 容器组件）：Tab 定义重构
- 菜单/面包屑文案更新
- 旧 Tab URL 兼容（301 重定向或 fallback）

## 验证
- [ ] 5 个 Tab 正确渲染
- [ ] Tab 切换 URL 更新
- [ ] 旧 URL 不报 404

## 完成标准
- [ ] Tab 结构按工作流重组完成
