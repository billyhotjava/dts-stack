# T04: 补充缺失 KPI 卡片

**优先级**: P1
**状态**: READY
**依赖**: F1/T01

## 目标
在现有三屏中补充 project3.xlsx 要求但当前未展示的 KPI 卡片

## 技术设计

### 第 1 屏补充
- 里程碑完成率（从 milestoneKpis 获取或 screen/overview 扩展）

### 第 2 屏补充
- 里程碑正常待完成数（milestoneKpis 扩展）
- 未完成重大节点数（incompleteKpis 扩展）
- 未完成重要节点数（incompleteKpis 扩展）

### 第 3 屏补充
- 超期已完成未变更数（changeKpis 扩展）

### 后端调整
`buildScreenExecutionKpis` 和 `buildScreenIncompleteKpis` 中补充缺失字段，确保返回完整数组。

## 影响范围
- `ProjectCockpitService.java` — 扩展 KPI 数组
- `projectManagementCommandCenterTemplate.ts` — 增加 number-card 引用新 index

## 验证
- [ ] 新增 KPI 卡片数值正确
- [ ] 不影响现有 KPI 卡片的 index 路径

## 完成标准
- [ ] project3.xlsx 中所有指标在大屏至少出现一次
