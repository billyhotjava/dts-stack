## 数据管理平台一期：数仓分层（ODS→DWD→DWS→ADS）设计

本文基于 `docs/implementation/governance/dg.docx`（ODS表明细）与 `docs/implementation/governance/metrics.xlsx`（指标清单）梳理 ODS 业务域、设计 DWD/DWS/ADS 三层结构，并为指标计算提供可追溯的模型承载。

### 1. 输入：ODS 表域梳理（来自 dg.docx）

#### 1.1 基础主数据（Base）
- `bd_material_v`：物料主数据（`pk_material`、`code`、名称、规格/型号、分类 `pk_marbasclass` 等）
- `bd_marbasclass`：物料分类（`pk_marbasclass`、`pk_parent`、`code` 等）
- `bd_supplier`：供应商主数据（`pk_supplier`、`code`、名称、分类等）
- `bd_stordoc`：仓库主数据（`pk_stordoc`、`code`、名称等）
- `bd_project`：项目主数据（`pk_project`、`pk_parentpro`、计划/实际起止日期、责任部门等）

#### 1.2 库存业务数据（Inventory）
- 入库
  - `ic_purchasein_h` / `ic_purchasein_b`：采购入库单（表头/表体，含 `dbizdate`、`cmaterialoid`、`cbodywarehouseid`、数量金额等）
  - `ic_generalin_h` / `ic_generalin_b`：其他入库单（表头/表体）
- 出库
  - `ic_generalout_h` / `ic_generalout_b`：普通出库单（表头/表体）
  - `ic_material_h` / `ic_material_b`：材料出库单（表头/表体）
- 库存余额与流水
  - `ic_openbal_h` / `ic_openbal_b`：期初余额（表头/表体，含数量 `nnum`/`nassistnum`、金额 `ncostmny`）
  - `ic_flow`：库存流水（含 `dbizdate`、入库数量 `ninnum`、出库数量 `noutnum`、金额 `ncostmny`、批次/失效日期等）

#### 1.3 采购业务数据（Purchase）
- `po_praybill` / `po_praybill_b`：请购单（申请/需求日期、项目、物料、数量、含税单价等）
- `po_order` / `po_order_b`：采购订单（订单日期、计划到货日期 `dplanarrvdate`、项目、物料、金额等）
- `po_arriveorder` / `po_arriveorder_b`：到货单（到货日期 `dbilldate`、关联订单 `pk_order`/`pk_order_b` 等）
- `ct_pu` / `ct_pu_b`：采购合同（合同、关联请购/订单信息等）
- `po_purchaseinfi` / `po_purchaseinfi_b`：采购入库（采购侧入库，含 `dbilldate`/`dbizdate`、需求日期 `drequiredate`、金额数量等）

#### 1.4 预算数据（Budget）
- “项目预算执行”视图：文档仅给出字段名称（未给出视图名/字段编码/类型）
  - 父项目、PK_Project、项目编码、项目名称、责任部门、预算金额、调整金额、预占金额、执行金额、历史执行金额

---

### 2. 统一口径与建模约束

#### 2.1 主键与编码
- 维度主键统一使用源系统 `pk_*`（如 `pk_material`、`pk_supplier`、`pk_project`、`pk_stordoc`、`pk_marbasclass`）
- 业务编码保留 `code` / `*_code`（用于对外展示与对账）
- 明细事实保留来源单据主键/行主键（如 `pk_order_b`、`pk_arriveorder_b`、`pk_praybill_b`、`pk_stockps_b`、`pk_flow`），确保可追溯

#### 2.2 时间字段标准化
- ODS 存在 `char(19)`/`varchar(19)` 的时间字段；DWD 统一产出：
  - `*_time`：TIMESTAMP（或 STRING 标准格式 `yyyy-MM-dd HH:mm:ss`）
  - `*_date`：DATE（或 STRING `yyyy-MM-dd`）
- 分区字段建议统一为 `dt`（`yyyy-MM-dd`），用于日增量/快照

#### 2.3 金额/数量选择
- 金额优先选择“本币无税金额”与“价税合计”两套口径：
  - 无税金额：`nmny`/`norigmny`
  - 含税金额：`norigtaxmny`/`ntaxmny`
- 库存流水 `ic_flow` 只有 `ncostmny`（金额）与 `ninnum`/`noutnum`（入/出数量），DWD 需显式构造 `in_qty/out_qty/net_qty` 与 `in_amt/out_amt/net_amt`（见 3.2）

---

### 3. DWD（明细层）设计

命名遵循平台规范：`dwd_{domain}_{subject}_{entity}`。以下给出最小可支撑指标体系的一组“维度 + 事实 + 宽表”设计。

#### 3.1 维度表（DWD DIM）

1) `dwd_inv_master_material`
- 粒度：1 行/物料（`material_id=pk_material`）
- 关键字段：`material_id`、`material_code=code`、`material_name`、`material_spec/material_type`、`material_class_id=pk_marbasclass`、审计字段（`source_ts` 等）
- 来源：`bd_material_v`

2) `dwd_inv_master_material_class`
- 粒度：1 行/物料分类（`material_class_id=pk_marbasclass`）
- 关键字段：`material_class_id`、`material_class_code=code`、`material_class_name`、`parent_class_id=pk_parent`
- 来源：`bd_marbasclass`

3) `dwd_pur_master_supplier`
- 粒度：1 行/供应商（`supplier_id=pk_supplier`）
- 关键字段：`supplier_id`、`supplier_code=code`、`supplier_name`、`supplier_class_id=pk_supplierclass`
- 来源：`bd_supplier`

4) `dwd_inv_master_warehouse`
- 粒度：1 行/仓库（`warehouse_id=pk_stordoc`）
- 关键字段：`warehouse_id`、`warehouse_code=code`、`warehouse_name`
- 来源：`bd_stordoc`

5) `dwd_proj_master_project`
- 粒度：1 行/项目（`project_id=pk_project`）
- 关键字段：`project_id`、`project_code`、`project_name`、`parent_project_id=pk_parentpro`、`plan_start_date/plan_finish_date`、`actu_start_date/actu_finish_date`、`duty_dept_id=pk_duty_dept`
- 来源：`bd_project`

6)（可选但指标需要）`dwd_inv_master_material_policy`
- 粒度：1 行/物料/仓库（或 1 行/物料）
- 关键字段：`material_id`、`warehouse_id`、`safety_stock_qty`、`lead_time_days`、`is_critical`
- 来源：当前 ODS 未提供（建议配置表/人工维护/从业务系统补采）
- 用途：支撑“紧缺物料数量”“关键/采购周期长标记”等指标

#### 3.2 事实表（DWD FACT）

1) `dwd_inv_fact_stock_opening_balance`
- 粒度：1 行/期初余额明细（`ic_openbal_b` 行粒度）
- 关键维度：`material_id=cmaterialoid`、`warehouse_id=cbodywarehouseid`（或对应字段）、`project_id=cprojectid`、`supplier_id=cvendorid`、`batch_id=pk_batchcode`
- 度量：`opening_qty=nnum`（主数量）、`opening_amt=ncostmny`
- 来源：`ic_openbal_h` + `ic_openbal_b`

2) `dwd_inv_fact_stock_flow`
- 粒度：1 行/库存流水（`pk_flow`）
- 关键维度：`material_id=cmaterialoid`、`warehouse_id=cwarehouseid`、`project_id=cprojectid`、`supplier_id=cvendorid`、`batch_id=pk_batchcode`
- 度量口径（建议在ETL中落地）：  
  - `in_qty = coalesce(ninnum,0)`，`out_qty = coalesce(noutnum,0)`，`net_qty = in_qty - out_qty`  
  - `in_amt = case when in_qty>0 then ncostmny else 0 end`，`out_amt = case when out_qty>0 then ncostmny else 0 end`  
  - `net_amt = in_amt - out_amt`（保留 `flow_amt_raw=ncostmny` 便于对账）
- 时间：`biz_date=dbizdate`
- 来源：`ic_flow`

3) `dwd_inv_fact_inbound_line`
- 粒度：1 行/入库单明细行
- 统一来源（并保留 `src_system/src_table`）：  
  - `ic_purchasein_h/b`（库存侧采购入库）  
  - `ic_generalin_h/b`（其他入库）  
  - `po_purchaseinfi/po_purchaseinfi_b`（采购侧入库）
- 关键维度：`material_id`、`warehouse_id`、`project_id`、`supplier_id`、`biz_date`
- 度量：`in_qty`、`in_amt`（无税/含税两套）

4) `dwd_inv_fact_outbound_line`
- 粒度：1 行/出库单明细行
- 统一来源：`ic_generalout_h/b`（普通出库）、`ic_material_h/b`（材料出库）
- 关键维度：`material_id`、`warehouse_id`、`project_id`
- 度量：`out_qty`、`out_amt`

5) `dwd_pur_fact_pray_line`
- 粒度：1 行/请购单明细（`pk_praybill_b`）
- 关键字段：`pray_bill_id=pk_praybill`、`pray_line_id=pk_praybill_b`、`project_id=cprojectid`、`material_id=pk_material`、`apply_date=dbilldate`、`require_date=dreqdate`
- 度量：`req_qty=nnum`、`req_amt=ntaxmny`（或数量×单价）
- 来源：`po_praybill` + `po_praybill_b`

6) `dwd_pur_fact_order_line`
- 粒度：1 行/采购订单明细（`pk_order_b`）
- 关键字段：`order_id=pk_order`、`order_line_id=pk_order_b`、`project_id=pk_project/cprojectid`、`material_id=pk_material`、`order_date=dbilldate`、`plan_arrive_date=dplanarrvdate`
- 度量：`order_qty=nnum`、`order_amt=nmny`（无税）/`tax_amt`（含税）
- 来源：`po_order` + `po_order_b`

7) `dwd_pur_fact_arrival_line`
- 粒度：1 行/到货单明细（`pk_arriveorder_b`）
- 关键字段：`arrive_id=pk_arriveorder`、`arrive_line_id=pk_arriveorder_b`、`order_id=pk_order`、`order_line_id=pk_order_b`、`arrive_date=dbilldate`
- 度量：`arrive_qty`、`arrive_amt`
- 来源：`po_arriveorder` + `po_arriveorder_b`

8) `dwd_pur_fact_purchase_in_line`
- 粒度：1 行/采购入库明细（`pk_stockps_b`）
- 关键字段：`stock_in_id=pk_stockps`、`stock_in_line_id=pk_stockps_b`、`order_id=pk_order`、`order_line_id=pk_order_b`、`in_date=dbilldate/dbizdate`
- 度量：`in_qty=ninnum`、`in_amt=nmny/ncostmny`
- 来源：`po_purchaseinfi` + `po_purchaseinfi_b`

9) `dwd_pur_fact_contract_line`
- 粒度：1 行/合同明细（`pk_ct_pu_b`）
- 关键字段：`contract_id=pk_ct_pu`、`contract_line_id=pk_ct_pu_b`、（如有）`pray_line_id=pk_praybill_b`
- 来源：`ct_pu` + `ct_pu_b`

10) `dwd_proj_fact_budget_execution`
- 粒度：1 行/项目（或 1 行/项目×快照日；建议按日快照落地）
- 字段：`project_id`、`project_code`、`project_name`、`duty_dept_name`、`budget_amt`、`budget_adjust_amt`、`reserved_amt`、`exec_amt`、`exec_hist_amt`
- 来源：项目预算执行视图（视图名/字段编码在文档中缺失，需补齐元数据后实施）

#### 3.3 采购“全流程”宽表（用于KPI）

`dwd_pur_wide_order_lifecycle_line`（建议）
- 粒度：1 行/采购订单行（`order_line_id`）
- 通过关联键打通：请购（`cpraybillhid/cpraybillbid`）→订单（`pk_order_b`）→到货（`pk_order_b`）→入库（`pk_order_b`）
- 输出关键日期：`apply_date`、`require_date`、`order_date`、`plan_arrive_date`、`arrive_date`、`stock_in_date`
- 输出金额：`order_amt`、`stock_in_amt`
- 输出派生字段：`is_ontime`、`delay_days`、`cycle_days`

---

### 4. DWS（主题汇总层）设计

命名遵循：`dws_{domain}_{topic}_{metric}`。DWS 负责把 DWD 明细加工为指标可直接复用的主题数据集，明确“粒度 + 维度 + 指标”。

#### 4.1 库存主题（INV）

1) `dws_inv_stock_snapshot_di`（日快照）
- 粒度：`dt, material_id, warehouse_id, batch_id, project_id`
- 指标：`stock_qty`、`stock_amt`、`last_out_date`、`age_days`、`expiry_date`
- 计算：`期初余额 + 截止dt的净流水累加`（以 `dwd_inv_fact_stock_opening_balance` + `dwd_inv_fact_stock_flow` 为准）

2) `dws_inv_stock_summary_di`
- 粒度：`dt, material_class_id`
- 指标：`stock_qty`、`stock_amt`、`sku_cnt=count(distinct material_id)`
- 用途：库存结构、趋势、健康度图谱的基础

3) `dws_inv_turnover_mn`
- 粒度：`stat_month, material_class_id`（也可扩展到仓库/项目）
- 指标：`out_amt`、`avg_stock_amt`、`turnover_rate=out_amt/avg_stock_amt`、`turnover_days=30/turnover_rate`

4) `dws_inv_idle_stock_di`
- 粒度：`dt, idle_bucket`（3/6/9/12月）
- 指标：`idle_stock_amt`、`total_stock_amt`、`idle_rate`

5) `dws_inv_expiry_risk_di`
- 粒度：`dt, months_to_expiry_bucket`
- 指标：`risk_sku_cnt`、`risk_stock_amt`

#### 4.2 采购主题（PUR）

1) `dws_pur_delivery_kpi_di`
- 粒度：`dt, project_id, supplier_id`（或 `stat_month`）
- 指标：`order_line_cnt`、`ontime_cnt`、`ontime_rate`、`avg_delay_days`、`purchase_amt`
- 来源：`dwd_pur_wide_order_lifecycle_line`

2) `dws_pur_cycle_by_class_mn`
- 粒度：`stat_month, material_class_id`
- 指标：`avg_cycle_days`（请购→入库）

3) `dws_pur_urgent_ratio_mn`
- 粒度：`stat_month`（可扩展 project）
- 指标：`urgent_amt`、`total_amt`、`urgent_rate`
- 说明：紧急口径建议优先用“紧急放行/非标准来源单据”等可审计字段定义

4) `dws_pur_single_source_ratio_mn`
- 粒度：`stat_month`（可扩展 project）
- 指标：`single_source_sku_cnt`、`total_sku_cnt`、`single_source_rate`、`single_source_amt_rate`

5) `dws_pur_purchase_trend_mn`
- 粒度：`stat_month`
- 指标：`purchase_amt`（订单金额或入库金额，需选定口径并统一）

#### 4.3 项目财务主题（PROJ-FIN）

1) `dws_proj_budget_exec_di`（预算执行日快照）
- 粒度：`dt, project_id`
- 指标：`approved_budget_amt=budget_amt+budget_adjust_amt`、`reserved_amt`、`exec_amt`、`balance_amt=approved_budget_amt-exec_amt`

2) `dws_proj_budget_exec_mn`（月度）
- 粒度：`stat_month, project_id`
- 指标：同上（取月末快照或月内累计，需按业务口径确定）

#### 4.4 项目执行主题（PROJ-EXEC）

`dws_proj_progress_di`
- 粒度：`dt, project_id`
- 指标：`is_finished`、`is_delayed`、`delay_days`（基于 `plan_finish_date` 与 `actu_finish_date/current_date`）

---

### 5. ADS（应用层）设计

命名遵循：`ads_{domain}_{app}_{scene}`。ADS 面向看板/大屏/接口输出，字段尽量贴合展示需求并保持指标口径一致。

#### 5.1 库存看板（示例）
- `ads_inv_board_summary_di`：库存资金占用率、库存周转率、闲置率、紧缺SKU数、效期风险指数等（按 `dt`）
- `ads_inv_board_structure_di`：库存资产结构（`dt, class_path, stock_amt`）
- `ads_inv_board_age_dist_di`：库存龄分布（`dt, age_bucket, class_id, stock_amt/qty`）
- `ads_inv_board_expiry_calendar_di`：效期风险预警（`dt, expiry_date, batch_cnt, stock_amt`）

#### 5.2 采购看板（示例）
- `ads_pur_board_summary_mn`：准时交付率、平均延迟天数、紧急采购占比、单一来源占比、月度采购金额
- `ads_pur_project_monitor_di`：项目采购监控清单（项目×订单行，输出当前环节、已延迟天数、预算节约额等）

#### 5.3 项目财务/执行看板（示例）
- `ads_proj_fin_board_summary_mn`：整体预算执行率、预算调整率、结余率等
- `ads_proj_exec_board_summary_di`：项目总数、在执行/完工/延期数量、项目及时完成率、重点项目进展清单

---

### 6. 缺口与落地建议（基于当前ODS范围）

以下指标在 `metrics.xlsx` 中出现，但当前 `dg.docx` ODS 范围缺少直接可计算的数据源，建议补采/新增配置表后再落地：
- 盘点差异率：需盘点表（账面 vs 实盘）或盘点结果明细
- 整体支出合规率：需支出/报销/凭证及合规规则判定结果
- 人均项目经费：需科研人员总数（HR/组织人员）维表或快照
- 单位成果经费强度：需核心成果（论文/专利/课题成果）事实表
- 项目物料供应及时率（“缺货导致实验延迟”）：需缺货事件/延期原因，或定义可审计的替代口径（如采购延迟触发）

