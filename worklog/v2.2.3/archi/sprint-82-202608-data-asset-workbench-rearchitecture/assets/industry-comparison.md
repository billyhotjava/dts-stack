# 数据资产产品交互对比

## 1. 对比结论

工业界主流产品普遍采用“发现入口 + 统一资产档案 + 上下文治理动作”，而不是在资产列表每行平铺所有模块按钮。

| 产品 | 发现/地图 | 资产详情 | 标签与治理 | 对本 Sprint 的启示 |
|---|---|---|---|---|
| Alibaba DataWorks | Data Map 用于元数据检索、分类浏览和发现 | 详情承载表、字段、血缘、使用等信息 | 标签管理支持查看关联资产和批量关联 | 地图做发现；标签治理进入台账 |
| Databricks Unity Catalog | Catalog Explorer 统一搜索和浏览对象 | 对象页包含详情、样例、依赖、血缘、权限 | 目录治理动作围绕当前对象展开 | 一条资产对应一个 profile/workbench |
| Microsoft Purview | Unified Catalog 搜索资产并按治理概念筛选 | 资产详情含属性、Schema、血缘、分类 | Governance 聚合产品、术语、关键元素和质量 | 治理上下文应聚合，不应散落在表格按钮 |
| Collibra | Catalog 提供发现和导航 | Asset Page 通过概览、页签、侧栏组织事实和任务 | Actions 菜单、Tags、Owner、Certification 围绕资产页 | 用任务/动作区替代表格多按钮 |
| Atlan | Discovery 提供搜索、过滤和浏览 | Asset Profile 汇总 owner、认证、标签、血缘、关联资产 | 治理动作在资产 profile 内完成 | 资产工作台应先展示 at-a-glance 和推荐动作 |

## 2. 设计取舍

### 2.1 采用

- 地图只负责概要分布、搜索方向和下钻。
- 台账是资产查询和治理的唯一主入口。
- 单资产治理采用工作台/档案，先显示上下文和任务。
- 标签管理同时提供“查看资产”和“关联资产”。
- 行操作只有一个明确主按钮。

### 2.2 不直接照搬

- 不引入新的 Catalog Explorer 一级菜单。
- 不复制其他产品的权限、认证或业务术语模型。
- 不在本 Sprint 新建数据产品、报表、API 发布控制面。
- 不用大而全的抽屉替代完整资产档案；工作台负责导引，档案负责详情。

## 3. 可量化目标

| 场景 | 当前点击路径 | 目标 |
|---|---:|---:|
| 从台账发现阻断并开始治理 | 2～4 次且需要理解多个按钮 | 1 次点击“治理资产”进入工作台 |
| 给一条资产绑定标签 | 先进入详情再寻找标签区 | 工作台内直接绑定，2 个关键动作内完成 |
| 从标签查看关联资产 | 当前无闭环 | 标签行 1 次点击进入已过滤台账 |
| 从地图查看资产详情 | 地图承担部分详情/标签管理 | 地图下钻到台账，再进入统一档案 |

## 4. 参考资料

- Alibaba Cloud DataWorks：Data Map、Metadata Search、Tag Management 官方文档。
- Databricks：Catalog Explorer、Explore database objects、Table insights、Unity Catalog lineage 官方文档。
- Microsoft Purview：Search and view data assets in Unified Catalog 官方文档。
- Collibra：Catalog asset pages、Tags 官方文档。
- Atlan：Asset profiles 官方文档。
