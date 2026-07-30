# DataWorks 维度建模页面盘点

采集日期：2026-07-30  
采集范围：DataWorks 华北 2（北京）/ 默认工作空间 / 维度建模。  
采集方式：登录后只读浏览；未执行保存、提交、发布、逆向建模或物化。

## 页面清单

| 页面 | 关键结构 | 截图 |
|---|---|---|
| 智能数据建模入口 | 顶部产品导航、工作空间、五类模型入口 | [dataworks-kimball-entry.png](./dataworks-kimball-entry.png) |
| 维度建模工作台 | 模块栏、分层、数据域树、编辑画布 | [dataworks-kimball-main.png](./dataworks-kimball-main.png) |
| 新建菜单 | 概念模型与五类逻辑模型 | [dataworks-create-menu.png](./dataworks-create-menu.png) |
| 创建维度 | 分层、业务分类、数据域、缩写、名称、描述 | [dataworks-create-dimension.png](./dataworks-create-dimension.png) |
| 创建贴源表 | 基础信息、来源设置、字段与分区字段 | [dataworks-create-source-table.png](./dataworks-create-source-table.png) |
| 创建维度表 | 基础信息、维度引用、字段管理 | [dataworks-create-dimension-table.png](./dataworks-create-dimension-table.png) |
| 创建明细表 | 基础信息、业务过程、字段管理 | [dataworks-create-fact-table.png](./dataworks-create-fact-table.png) |
| 创建汇总表 | 数据域、粒度、周期、修饰词、字段管理 | [dataworks-create-aggregate-table.png](./dataworks-create-aggregate-table.png) |
| 创建应用表 | 主题域、粒度、周期、修饰词、字段管理 | [dataworks-create-application-table.png](./dataworks-create-application-table.png) |
| 字段编辑行 | 紧凑表格内编辑、主键/非空、冗余字段 | [dataworks-field-row.png](./dataworks-field-row.png) |
| 字段显示设置 | 按列勾选显示项 | [dataworks-field-display-settings.png](./dataworks-field-display-settings.png) |
| 字段关联 | 模型字段与标准/指标的关系画布 | [dataworks-field-association.png](./dataworks-field-association.png) |
| 导入菜单 | 导入对象入口 | [dataworks-import-menu.png](./dataworks-import-menu.png) |
| 导出菜单 | 导出对象入口 | [dataworks-export-menu.png](./dataworks-export-menu.png) |
| 逆向建模入口 | 空状态与快速开始 | [dataworks-reverse-modeling.png](./dataworks-reverse-modeling.png) |
| 逆向建模向导 | 逆向策略、模型确认、生成、完成 | [dataworks-reverse-modeling-wizard.png](./dataworks-reverse-modeling-wizard.png) |

## 本地交互原型

- 入口：[dataworks-kimball/index.html](./dataworks-kimball/index.html)
- 模型编辑器预览：[prototype-model-editor.png](./dataworks-kimball/prototype-model-editor.png)
- 发布与物化预览：[prototype-publish-materialize.png](./dataworks-kimball/prototype-publish-materialize.png)
- 逆向建模预览：[prototype-overview.png](./dataworks-kimball/prototype-overview.png)
- 首页预览：[prototype-home.png](./dataworks-kimball/prototype-home.png)
- 数仓规划业务过程预览：[prototype-planning-business-process.png](./dataworks-kimball/prototype-planning-business-process.png)
- 数据标准预览：[prototype-standards.png](./dataworks-kimball/prototype-standards.png)
- 原子指标工作台预览：[prototype-metrics-atomic.png](./dataworks-kimball/prototype-metrics-atomic.png)
- 关系图预览：[prototype-relationship-graph.png](./dataworks-kimball/prototype-relationship-graph.png)

## 六类新建页面字段差异

| 类型 | 业务上下文关键项 | 编辑工具 |
|---|---|---|
| 维度 | 数据域、英文缩写、中文名称 | 保存 |
| 贴源表 | 业务分类、存储策略、表名规则 | 字段/分区字段、导入、导出 |
| 维度表 | 数据域、维度、存储策略、表名规则 | 字段关联、关联关系 |
| 明细表 | 业务分类、业务过程、存储策略 | 字段关联、关联关系 |
| 汇总表 | 数据域、统计粒度、统计周期、修饰词 | 快速导入、字段关联 |
| 应用表 | 主题域、统计粒度、统计周期、修饰词 | 快速导入、字段关联 |

## 对 DTS 的落地约束

1. 建模主界面保持“对象树 + 单页编辑器”，不再拆成多个解释性阶段页。
2. 维度系统编码由系统在首次保存时生成；用户只填写业务定义和属性映射。
3. 属性类型属于模型字段，不在业务维度目录重复维护。
4. 维度主键只需在字段表中同时选择业务维度属性并勾选“主键”。
5. 发布前校验集中显示，发布成功后可在同一弹窗继续创建物化任务。
6. 高级能力（标准、质量、关系、FML、日志）保留为短工具栏或弹层，不占用主编辑流程。
