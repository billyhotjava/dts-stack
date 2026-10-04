# T03: screens 模板注册与验收策略

**优先级**: P0
**状态**: DONE
**依赖**: T01,T02

## 目标
确定 GPMC 模板如何接入 `screens` 模板库及后续验收方式。

## 模板注册策略

### 注册位置
- 主注册入口：
  - `source/dts-analytics-webapp/modern/src/pages/screens/screenTemplates.ts`
- 推荐新增聚合文件：
  - `source/dts-analytics-webapp/modern/src/pages/screens/gpmcTemplates/index.ts`
- 推荐按模板拆文件：
  - `gpmcStrategicOverviewTemplate.ts`
  - `gpmcExecutionBoardTemplate.ts`
  - `gpmcQualityBoardTemplate.ts`
  - `gpmcTechStateBoardTemplate.ts`
  - `gpmcCostBoardTemplate.ts`
  - `gpmcRiskBoardTemplate.ts`

### 注册要求
- 每套模板必须导出标准 `ScreenTemplate`
- `category` 统一为 `project-management`
- `recommendedVariables` 必须至少包含：
  - `dateFrom`
  - `dateTo`
  - `deptId`
  - `projectNo`
  - `riskLevel`
- `thumbnail` 第一版允许使用 emoji，占位不阻塞模板上线

## 模板配置约束
- `config.name` 与模板展示名一致
- `config.width = 1920`
- `config.height = 1080`
- 必须带 `globalVariables`
- 所有 API 组件必须带数据源定义
- 所有下钻组件必须带 `jump-url` 或等价动作定义

## 设计器验收策略

### A. 模板发现验收
- 模板在模板库中可见
- 模板分类正确出现在“项目管理”
- 模板标签与描述可区分 6 套模板职责

### B. 新建验收
- 从模板库可创建大屏
- 创建后画布尺寸、布局、组件数量正确
- 全局变量自动带入

### C. 编辑验收
- 可修改布局位置和大小
- 可编辑组件标题、样式、筛选默认值
- 不需要改代码即可调整版式

### D. 绑定验收
- API 组件可读取到配置的数据源
- `responsePath` 与 view model 字段对齐
- 变量变更能触发重新取数

### E. 跳转验收
- 模板组件点击可跳到对应管控页或执行层只读页
- `jump-url` 参数完整透传：
  - `dateFrom`
  - `dateTo`
  - `deptId`
  - `projectNo`
  - `riskLevel`

## 回归最小清单
- 模板可见
- 模板可新建
- 模板可保存
- 模板变量可编辑
- 模板刷新后配置不丢失
- 模板跳转动作可用

## 与业务页的一致性要求
- 模板名称、路由目标、变量键、数据源路径必须与业务页设计文档一致。
- 业务页新增一个看板模块时，若模板需要同步展示，必须同时更新模板文件和注册清单。

## 影响范围
- `source/dts-analytics-webapp/modern/src/pages/screens`

## 验证
- [x] 模板注册策略完成
- [x] 验收清单完成

## 完成标准
- [x] 模板接入策略完成
