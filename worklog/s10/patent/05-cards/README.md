# 专利数据中心 — Card SQL

大屏看板所需的全部 Card 查询语句。
在 Analytics 界面中逐一创建 Card（新建问题 → Native query），保存后记录 Card ID。

## Card 清单

| 编号 | 名称 | 类型 | 用途 |
|------|------|------|------|
| C01 | 申请总量 | number-card | KPI |
| C02 | 受理数量 | number-card | KPI |
| C03 | 授权数量 | number-card | KPI |
| C04 | 授权率 | number-card | KPI |
| C05 | 同比增长率 | number-card | KPI |
| C06 | 上年申请量 | number-card | KPI |
| C07 | 专利类型占比 | pie-chart | 图表 |
| C08 | 月度专利趋势 | line-chart | 图表 |
| C09 | 部门专利排行 | bar-chart | 图表 |
| C10 | 部门专利类型分布 | bar-chart | C09 下钻目标, 参数: dept_name |
| C11 | 类型部门分布 | pie-chart | C07 下钻目标, 参数: patent_type |
| C12 | 近期专利授权 | scroll-board | 表格 |
| C13 | 当年申请详情 | scroll-board | 表格 |
| C14 | 受理超期预警 | scroll-board | 表格 |

## 大屏下钻配置

- **部门专利排行** (C09): 启用下钻 → 层级1: cardId=C10, paramName=`dept_name`, label=`部门`
- **专利类型占比** (C07): 启用下钻 → 层级1: cardId=C11, paramName=`patent_type`, label=`类型`
