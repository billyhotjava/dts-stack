# v2 模型清单与依赖顺序

以下为拓扑顺序；物化选中的 ADS 时应包含其上游。全部输出均为 table。

| 序号 | 层级 | 模型 | 业务含义 | 唯一粒度 | 上游 |
| --- | --- | --- | --- | --- | --- |
| 1 | DWD | `prs_v2_dwd_project` | 项目当前维度 | project_id | p_project, p_contract |
| 2 | DWD | `prs_v2_dwd_flower_order` | 报花业务单头明细 | order_id | prs_v2_dwd_project, t_flower_biz_info |
| 3 | DWD | `prs_v2_dwd_change` | 报花变更明细 | change_id | prs_v2_dwd_flower_order, t_change_info |
| 4 | ADS | `prs_v2_ads_change_detail` | 报花变更记录分析 | change_id | prs_v2_dwd_change, prs_v2_dwd_project |
| 5 | DWD | `prs_v2_dwd_curing` | 养护记录单头明细 | curing_id | t_curing_record |
| 6 | DWS | `prs_v2_dws_curing_workload_monthly` | 养护人员项目月度工作量 | curing_month_key | prs_v2_dwd_curing |
| 7 | ADS | `prs_v2_ads_curing_workload` | 养护工作量分析 | curing_month_key | prs_v2_dws_curing_workload_monthly, prs_v2_dwd_project |
| 8 | DWD | `prs_v2_dwd_placement` | 项目当前摆放快照 | placement_id | prs_v2_dwd_project, p_project_green |
| 9 | DWS | `prs_v2_dws_project_current` | 项目当前摆放汇总 | project_key | prs_v2_dwd_placement |
| 10 | ADS | `prs_v2_ads_current_placement` | 项目当前摆放分析 | project_key | prs_v2_dws_project_current, prs_v2_dwd_project |
| 11 | DWD | `prs_v2_dwd_finance_month_settlement` | 月度结算单明细 | settlement_id | prs_v2_dwd_project, a_month_accounting |
| 12 | DWD | `prs_v2_dwd_finance_collection` | 现金收款单明细 | collection_id | prs_v2_dwd_project, a_collection_record |
| 13 | DWS | `prs_v2_dws_finance_monthly_summary` | 项目月度应收与现金收入并列汇总 | project_month_key | prs_v2_dwd_finance_month_settlement, prs_v2_dwd_finance_collection |
| 14 | DWS | `prs_v2_dws_customer_finance_monthly` | 客户月度应收与现金收入汇总 | customer_month_key | prs_v2_dws_finance_monthly_summary, prs_v2_dwd_project |
| 15 | DWD | `prs_v2_dwd_customer` | 客户当前维度 | customer_id | p_customer |
| 16 | ADS | `prs_v2_ads_customer_finance` | 客户月度经营财务分析 | customer_month_key | prs_v2_dws_customer_finance_monthly, prs_v2_dwd_customer |
| 17 | DWD | `prs_v2_dwd_collection_allocation` | 收款关联分摊明细 | allocation_id | prs_v2_dwd_finance_collection, a_collection_item |
| 18 | ADS | `prs_v2_ads_finance_collection` | 收款与分摊情况明细 | collection_id | prs_v2_dwd_collection_allocation, prs_v2_dwd_finance_collection |
| 19 | ADS | `prs_v2_ads_finance_month_settlement` | 项目月度结算与现金收入分析 | project_month_key | prs_v2_dws_finance_monthly_summary, prs_v2_dwd_project |
| 20 | DWD | `prs_v2_dwd_flower_item` | 报花业务行明细 | order_item_id | prs_v2_dwd_flower_order, t_flower_biz_item |
| 21 | ADS | `prs_v2_ads_lease_detail` | 租赁业务明细分析 | order_item_id | prs_v2_dwd_flower_item, prs_v2_dwd_project |
| 22 | DWS | `prs_v2_dws_project_operations_monthly` | 项目月度租赁业务汇总 | project_month_key | prs_v2_dwd_flower_order |
| 23 | ADS | `prs_v2_ads_operations_overview` | 项目月度租赁经营总览 | project_month_key | prs_v2_dws_project_operations_monthly, prs_v2_dwd_project |
| 24 | DWD | `prs_v2_dwd_recovery` | 回收业务行明细 | recovery_item_id | t_recovery_info_item, t_recovery_info |
| 25 | ADS | `prs_v2_ads_recovery_detail` | 回收明细与执行情况分析 | recovery_item_id | prs_v2_dwd_recovery, prs_v2_dwd_project |
| 26 | DWD | `prs_v2_dwd_contract` | 合同当前维度 | contract_id | p_contract |
| 27 | DWD | `prs_v2_dwd_curing_item` | 养护楼层行明细 | curing_item_id | prs_v2_dwd_curing, t_curing_record_item |
| 28 | DWD | `prs_v2_dwd_extra_cost` | 报花附加费用明细 | extra_cost_id | prs_v2_dwd_flower_order, t_flower_extra_cost |
| 29 | DWD | `prs_v2_dwd_goods` | 商品规格当前维度 | goods_price_id | b_goods_price, b_goods |
| 30 | DWD | `prs_v2_dwd_position` | 摆放位置当前维度 | position_id | p_position, p_project, p_contract |
| 31 | DWD | `prs_v2_dwd_rent_snapshot` | 月度租金快照明细 | rent_snapshot_id | prs_v2_dwd_project, a_month_rent_snapshot |

## ODS 映射

| dbt 来源 | 本地物理表 |
| --- | --- |
| `public.ods_prsa_collection_item` | `biadmin.public.ods_prsa_collection_item` |
| `public.ods_prsa_collection_record` | `biadmin.public.ods_prsa_collection_record` |
| `public.ods_prsa_month_accounting` | `biadmin.public.ods_prsa_month_accounting` |
| `public.ods_prsa_month_rent_snapshot` | `biadmin.public.ods_prsa_month_rent_snapshot` |
| `public.ods_prsb_goods` | `biadmin.public.ods_prsb_goods` |
| `public.ods_prsb_goods_price` | `biadmin.public.ods_prsb_goods_price` |
| `public.ods_prsp_contract` | `biadmin.public.ods_prsp_contract` |
| `public.ods_prsp_customer` | `biadmin.public.ods_prsp_customer` |
| `public.ods_prsp_position` | `biadmin.public.ods_prsp_position` |
| `public.ods_prsp_project` | `biadmin.public.ods_prsp_project` |
| `public.ods_prsp_project_green` | `biadmin.public.ods_prsp_project_green` |
| `public.ods_prst_change_info` | `biadmin.public.ods_prst_change_info` |
| `public.ods_prst_curing_record` | `biadmin.public.ods_prst_curing_record` |
| `public.ods_prst_curing_record_item` | `biadmin.public.ods_prst_curing_record_item` |
| `public.ods_prst_flower_biz_info` | `biadmin.public.ods_prst_flower_biz_info` |
| `public.ods_prst_flower_biz_item` | `biadmin.public.ods_prst_flower_biz_item` |
| `public.ods_prst_flower_extra_cost` | `biadmin.public.ods_prst_flower_extra_cost` |
| `public.ods_prst_recovery_info` | `biadmin.public.ods_prst_recovery_info` |
| `public.ods_prst_recovery_info_item` | `biadmin.public.ods_prst_recovery_info_item` |
