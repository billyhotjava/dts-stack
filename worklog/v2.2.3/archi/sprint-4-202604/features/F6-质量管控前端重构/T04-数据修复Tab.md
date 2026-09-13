# T04: 数据修复 Tab（集成 F5 组件）

**优先级**: P1
**状态**: READY
**依赖**: F5

## 目标
数据修复 Tab 作为统一入口，通过 radio 切换"入湖预检"和"质量问题修复"两种模式。

## 技术设计

### 布局
```
◉ 入湖预检    ○ 质量问题修复

入湖预检模式：
  → 上传区 + 目标数据集选择 + 已绑定规则展示
  → [开始检查] → 引用 F5/T01 StagingDataEditor 组件

质量问题修复模式：
  → 待修复任务列表（来自失败的 QualityRun）
  → 点击"去修复" → 修复策略选择：批量清洗 / SQL修复 / 人工编辑
  → 引用 F5/T02 BatchCleansingDialog 或 F5/T03 SqlRepairEditor
```

### URL 参数支持
- `?tab=repair&mode=precheck&taskId=xxx` → 自动进入预检编辑台
- `?tab=repair&mode=fix&runId=xxx` → 自动打开修复面板

## 影响范围
- 新增 `DataRepairTab.tsx` 作为容器组件
- 引用 F5 中的 3 个子组件

## 验证
- [ ] 两种模式切换正常
- [ ] 从"检查任务"跳转参数正确加载
- [ ] 从入湖流程跳转参数正确加载

## 完成标准
- [ ] 数据修复 Tab 统一入口可用
