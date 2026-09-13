# 黄金链路验收数据集

**Sprint**: Sprint-31
**Feature**: F1/T04
**状态**: DONE

## 样例 1: JDBC

| 项 | 建议值 |
|---|---|
| 数据源 | `demo_jdbc` |
| 源表 | `public.demo_order` |
| ODS | `ods_demo.demo_order` |
| DWD | `dwd_demo_order_detail` |
| DWS | `dws_demo_order_month_summary` |
| ADS | `ads_demo_order_dashboard` |
| 核心指标 | 订单数、订单金额、客户数、完成率 |

## 样例 2: 文件

| 项 | 建议值 |
|---|---|
| 文件 | `demo_orders.csv` |
| ODS | `ods_file.demo_orders` |
| 字段 | `order_id, customer_id, order_date, amount, status` |
| 质量规则 | `order_id not null`、`amount >= 0`、`status in (...)` |

## 验收原则

- 数据集足够小，适合 smoke。
- 字段能覆盖维度、指标、时间、状态、金额。
- JDBC 样例优先跑通，文件样例用于验证能力边界。
