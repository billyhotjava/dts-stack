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

本节的目标是：把 ODS 中“同一业务对象/同一业务过程”在不同表里的字段差异、时间语义差异、金额数量口径差异统一起来，让后续 DWD→DWS→ADS 的计算链条清晰可追溯，并确保指标口径在跨域（库存/采购/项目/预算）聚合时不会“对不上”。

#### 2.1 ODS→DWD 的边界（为什么要统一）
- ODS：保持源系统字段语义（字段名/类型可能不一致，如大量 `char(19)` 时间），只做必要去噪与标准化（如时间格式、空值）。
- DWD：做“字段统一 + 主键外键统一 + 口径字段固化”，让指标可以稳定引用（例如：统一用 `material_id/project_id/supplier_id/warehouse_id`）。
- DWS：在明确粒度前提下做主题汇总（如日快照、月周转、交付KPI）。
- ADS：面向看板/报表输出，字段贴合展示，并保持指标口径一致。

#### 2.2 统一的实体主键与外键（结合 ODS 实际字段）
ODS 的主数据表给了稳定的 `pk_*` 主键；业务单据/流水表在不同模块里对同一对象的字段名不一致（如物料在库存侧为 `cmaterialoid`，采购侧为 `pk_material`）。统一规则如下：

**统一命名规则**
- 统一输出：`*_id`（主键/外键）、`*_code`（业务编码）、`*_name`（名称）。
- DWD 维度表主键：使用源系统 `pk_*` 作为 `*_id`；业务编码字段保留 `code` 作为 `*_code`。

**核心对象映射（ODS→DWD）**
- 物料：`bd_material_v.pk_material` → `material_id`；库存表常见外键：`ic_flow.cmaterialoid`、`ic_openbal_b.cmaterialoid`；采购表常见外键：`po_order_b.pk_material`、`po_praybill_b.pk_material`、`po_purchaseinfi_b.pk_material`。
- 物料分类：`bd_marbasclass.pk_marbasclass` → `material_class_id`；物料表关联字段：`bd_material_v.pk_marbasclass`。
- 供应商：`bd_supplier.pk_supplier` → `supplier_id`；库存/入库类常见外键：`ic_flow.cvendorid`、`ic_openbal_b.cvendorid`；采购订单表头：`po_order.pk_supplier`（行表也可能有 `pk_supplier`）。
- 仓库：`bd_stordoc.pk_stordoc` → `warehouse_id`；库存流水：`ic_flow.cwarehouseid`；期初/单据表体常见：`ic_openbal_b.cbodywarehouseid`、`ic_purchasein_b.cbodywarehouseid`（字段以 ODS 明细为准）。
- 项目：`bd_project.pk_project` → `project_id`；库存/采购侧常见：`ic_flow.cprojectid`、`po_order.pk_project`/`po_order_b.cprojectid`、`po_praybill_b.cprojectid`、`po_purchaseinfi_b.cprojectid`。
- 批次：`pk_batchcode` → `batch_id`（在 `ic_openbal_b`、`ic_flow` 等存在），用于效期风险/龄分析。

> 约束：DWD 明细事实必须保留源单据行主键（例如 `pk_order_b`、`pk_praybill_b`、`pk_arriveorder_b`、`pk_stockps_b`、`pk_flow`），否则无法把指标追溯回源系统明细行进行对账与排错。

#### 2.3 业务过程链路与粒度（保证上下游能衔接）
指标中“准时交付率/采购周期/项目延迟”等都依赖采购全流程链路。结合 ODS 表关系，推荐以“订单行”作为采购过程的核心粒度：

**采购链路（推荐主链路）**
1) 请购（需求产生）：`po_praybill_b`（行粒度 `pk_praybill_b`，关键日期：`dbilldate` 请购日期、`dreqdate` 需求日期）
2) 订单（承诺产生）：`po_order_b`（行粒度 `pk_order_b`，关键日期：`dbilldate` 订单日期、`dplanarrvdate` 计划到货日期）
3) 到货（履约到货）：`po_arriveorder_b`（行粒度 `pk_arriveorder_b`，关键字段包含 `pk_order_b` 可直连订单行；日期在表头 `po_arriveorder.dbilldate`）
4) 入库（最终落账）：`po_purchaseinfi_b`（行粒度 `pk_stockps_b`，关键字段包含 `pk_order_b`；日期：表头 `po_purchaseinfi.dbilldate` 或表体 `dbizdate`）

**为什么以订单行做主粒度**
- 采购金额、计划到货、交付判定通常发生在订单行层面；一张订单可拆多行物料，且到货/入库也可能分批发生，只有行粒度才能计算“按时/延迟/周期”的可解释指标。

**库存链路（快照的来源）**
- 期初：`ic_openbal_b`（余额起点）
- 流水：`ic_flow`（事件增量，`pk_flow` 粒度，`dbizdate` 为业务日期，`ninnum/noutnum/ncostmny` 为核心度量）
- 库存日快照：在 DWS 层由 “期初 + 截止日净流水累加”得到（用于库存金额、龄、效期预警、周转等指标）

#### 2.4 时间语义统一（把“哪一天”说清楚）
ODS 中同类日期字段含义不同，统一时必须明确业务语义，否则会出现“同一指标口径混用”：

**常见日期类型（来自 ODS 字段习惯）**
- `dbilldate`：单据日期（下单/到货/入库等业务发生日期，常在采购/单据表头/表体出现）
- `dbizdate`：业务日期/过账日期（库存流水 `ic_flow.dbizdate`、库存相关表体常见）
- `dreqdate` / `drequiredate`：需求日期（请购与采购入库侧都可能出现）
- 项目计划/实际日期：`bd_project.plan_*`、`bd_project.actu_*`

**统一输出规则**
- DWD 中输出 `*_date`（DATE）用于业务计算；保留 `*_time`（TIMESTAMP/STRING）用于追溯与排序。
- 分区字段 `dt`：
  - 明细事实（流水/单据）：一般取核心业务日期（库存取 `biz_date=dbizdate`；采购单据取 `order_date/arrive_date/in_date`）
  - 日快照：`dt` 表示快照日（不等同于源系统某个单据日期）

**与指标的对应关系（示例）**
- 准时交付：`plan_arrive_date(dplanarrvdate)` vs `arrive_date(po_arriveorder.dbilldate)`（或最终以 `stock_in_date` 判定）
- 延迟天数：`actual_done_date(coalesce(stock_in_date,arrive_date)) - require_date(dreqdate/drequiredate)`
- 项目延期：`dt` vs `plan_finish_date`（若 `actu_finish_date` 为空则用 `dt` 作为“截至今日”）

#### 2.5 数量/金额口径统一（把“算什么钱/什么量”说清楚）

**数量**
- DWD 统一字段：`*_qty`（`DECIMAL(28,8)`）
- 库存流水：入/出数量分别来自 `ic_flow.ninnum`、`ic_flow.noutnum`（并构造 `net_qty=in_qty-out_qty`）
- 采购单据：优先使用“主数量”字段（如 `nnum`），必要时同时保留“辅数量/换算率”用于解释

**金额**
ODS 中同一单据可能同时存在多套金额字段（本币/原币、含税/无税、计划/实际），为避免指标混用，DWD 建议固化两套“可用且可解释”的口径：
- `amt_excl_tax`：无税金额（常见字段：`nmny`、`norigmny`）
- `amt_incl_tax`：含税金额/价税合计（常见字段：`norigtaxmny`、`ntaxmny`）

库存模块另有“成本金额”口径：
- `ic_flow.ncostmny`：库存成本金额（不天然带正负号）
- 统一做法：DWD 显式产出 `in_amt/out_amt/net_amt`，并保留 `flow_amt_raw` 用于对账（详见 `dwd_inv_fact_stock_flow`）

> 约束：DWS/ADS 中所有金额指标必须明确引用哪一套口径（无税/含税/成本），并在指标文档（`metrics.md`）保持一致。

#### 2.6 状态、关闭与删除（哪些记录参与统计）
ODS 明细包含大量状态/标志位字段（如采购订单 `forderstatus`、关闭标志 `bfinalclose/barriveclose`，项目 `deletestate/enablestate` 等）。统一原则：
- DWD 不“武断删除”记录，而是保留关键状态字段并提供统一的 `is_valid`/`is_deleted` 标记（如可从 `deletestate` 推导）。
- DWS/ADS 统计时明确过滤条件（例如：只统计有效订单行、排除作废/关闭行），并把过滤规则写入指标口径。

#### 2.7 可追溯与对账字段（治理要求）
每张 DWD/DWS/ADS 表都应满足“能追溯回源”的最低要求：
- 记录来源：`source_system/source_table/source_pk/source_ts`
- 记录加工：`etl_time` + 分区字段（`dt/stat_month`）
- 关键链路保留：采购链路至少保留 `pray_line_id/order_line_id/arrive_line_id/stock_in_line_id` 的可回溯关联（如果存在）

---

### 3. DWD（明细层）设计

命名遵循平台规范：`dwd_{domain}_{subject}_{entity}`。以下给出最小可支撑指标体系的一组“维度 + 事实 + 宽表”设计。

#### 3.0 DWD 表清单（英文名/中文名）

| 表名 | 中文名称 | 粒度（建议） | 备注 |
|---|---|---|---|
| `dwd_inv_master_material` | 物料主数据维度 | 1行/物料 | 来源：`bd_material_v` |
| `dwd_inv_master_material_class` | 物料分类维度 | 1行/分类 | 来源：`bd_marbasclass` |
| `dwd_pur_master_supplier` | 供应商主数据维度 | 1行/供应商 | 来源：`bd_supplier` |
| `dwd_inv_master_warehouse` | 仓库主数据维度 | 1行/仓库 | 来源：`bd_stordoc` |
| `dwd_proj_master_project` | 项目主数据维度 | 1行/项目 | 来源：`bd_project` |
| `dwd_inv_master_material_policy` | 物料策略配置维表 | 1行/物料×仓库×生效期 | 配置/补采（安全库存/关键标记/提前期） |
| `dwd_inv_fact_stock_opening_balance` | 库存期初余额明细事实 | 1行/期初余额行 | 来源：`ic_openbal_h/b` |
| `dwd_inv_fact_stock_flow` | 库存流水明细事实 | 1行/流水 | 来源：`ic_flow` |
| `dwd_inv_fact_inbound_line` | 统一入库明细事实 | 1行/入库单行 | 统一：`ic_purchasein*`/`ic_generalin*`/`po_purchaseinfi*` |
| `dwd_inv_fact_outbound_line` | 统一出库明细事实 | 1行/出库单行 | 统一：`ic_generalout*`/`ic_material*` |
| `dwd_pur_fact_pray_line` | 请购单明细事实 | 1行/请购行 | 来源：`po_praybill/po_praybill_b` |
| `dwd_pur_fact_order_line` | 采购订单明细事实 | 1行/订单行 | 来源：`po_order/po_order_b` |
| `dwd_pur_fact_arrival_line` | 到货单明细事实 | 1行/到货行 | 来源：`po_arriveorder/po_arriveorder_b` |
| `dwd_pur_fact_purchase_in_line` | 采购入库明细事实 | 1行/入库行 | 来源：`po_purchaseinfi/po_purchaseinfi_b` |
| `dwd_pur_fact_contract_line` | 采购合同明细事实 | 1行/合同明细 | 来源：`ct_pu/ct_pu_b` |
| `dwd_proj_fact_budget_execution` | 项目预算执行快照事实 | 1行/项目/日 | 需补齐预算执行视图元数据 |
| `dwd_pur_wide_order_lifecycle_line` | 采购全流程宽表（订单行生命周期） | 1行/订单行 | 请购→订单→到货→入库 |

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

#### 4.0 DWS 表清单（英文名/中文名）

| 表名 | 中文名称 | 粒度（建议） | 备注 |
|---|---|---|---|
| `dws_inv_stock_snapshot_di` | 库存日快照主题表 | dt×物料×仓库×批次×项目 | 期初+流水累加 |
| `dws_inv_stock_summary_di` | 库存按分类日汇总主题表 | dt×物料分类 | 用于结构/趋势/健康度 |
| `dws_inv_turnover_mn` | 库存周转月汇总主题表 | 月×物料分类 | 用于周转率/天数 |
| `dws_inv_idle_stock_di` | 闲置库存日汇总主题表 | dt×闲置阈值桶 | 3/6/9/12月未领用 |
| `dws_inv_expiry_risk_di` | 效期风险日汇总主题表 | dt×临期桶 | 临期SKU数/金额 |
| `dws_pur_delivery_kpi_di` | 采购交付KPI日汇总主题表 | dt×项目×供应商 | 准时率/延迟 |
| `dws_pur_cycle_by_class_mn` | 采购周期按分类月汇总主题表 | 月×物料分类 | 平均周期 |
| `dws_pur_urgent_ratio_mn` | 紧急采购占比月汇总主题表 | 月 | 紧急金额占比 |
| `dws_pur_single_source_ratio_mn` | 单一来源占比月汇总主题表 | 月 | SKU占比/金额占比 |
| `dws_pur_purchase_trend_mn` | 月度采购趋势主题表 | 月 | 月采购金额 |
| `dws_proj_budget_exec_di` | 预算执行日快照主题表 | dt×项目 | 执行率/结余 |
| `dws_proj_budget_exec_mn` | 预算执行月汇总主题表 | 月×项目 | 月末快照/累计 |
| `dws_proj_progress_di` | 项目进度日快照主题表 | dt×项目 | 完工/延期/延迟天数 |

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

#### 5.0 ADS 表清单（英文名/中文名）

| 表名 | 中文名称 | 粒度（建议） | 对应指标/图表（metrics.xlsx） |
|---|---|---|---|
| `ads_inv_board_summary_di` | 库存看板总览 | dt | 库存资金占用率/周转率/闲置率/紧缺/效期风险/盘点差异等 |
| `ads_inv_board_structure_di` | 库存资产结构数据集 | dt×分类层级 | 库存资产结构（旭日图） |
| `ads_inv_board_health_bubble_mn` | 库存健康度诊断数据集 | 月×分类 | 库存健康度诊断（气泡图） |
| `ads_inv_board_age_dist_di` | 库存龄分布数据集 | dt×分类×年龄桶 | 库存龄分布分析（热力/堆叠） |
| `ads_inv_board_trend_mn` | 库存关联分析趋势数据集 | 月 | 库存关联分析（折线+柱） |
| `ads_inv_board_expiry_calendar_di` | 效期风险预警日历数据集 | dt×到期日 | 效期风险预警（日历/时间轴） |
| `ads_inv_shortage_material_di` | 紧缺物料明细清单 | dt×物料×仓库 | 紧缺物料数量（明细支撑，可选） |
| `ads_pur_board_summary_mn` | 采购看板总览 | 月 | 准时交付率/平均延迟/紧急占比/单一来源/月度采购 |
| `ads_pur_board_trend_mn` | 月度采购趋势数据集 | 月 | 月度采购趋势（柱状图） |
| `ads_pur_cycle_by_class_mn` | 分类采购周期数据集 | 月×分类 | 物料类别的平均采购周期（数字指标） |
| `ads_pur_delivery_gantt_di` | 采购到货周期监控数据集 | dt×订单行 | 采购到货周期监控（甘特图） |
| `ads_pur_supplier_risk_bubble_mn` | 供应商风险分析数据集 | 月×供应商 | 供应商风险分析（气泡图，需风险评分） |
| `ads_pur_project_monitor_di` | 项目采购监控清单 | dt×项目×订单行 | 项目采购监控（列表） |
| `ads_proj_fin_board_summary_mn` | 项目财务看板总览 | 月 | 预算执行率/合规率/人均经费/结余率等（部分需补采） |
| `ads_proj_fin_budget_exec_trend_mn` | 预算执行率趋势数据集 | 月 | 预算执行率分析（趋势折线） |
| `ads_proj_fin_expense_compliance_trend_mn` | 支出合规率趋势数据集 | 月 | 支出合规率（趋势折线，需补采） |
| `ads_proj_fin_cost_structure_mn` | 成本结构占比数据集 | 月×科目 | 项目成本结构占比（饼图，需补采） |
| `ads_proj_fin_budget_structure_mn` | 预算结构占比数据集 | 月×科目 | 项目预算结构占比（饼图，需补采） |
| `ads_proj_fin_budget_dept_share_mn` | 预算部门占比数据集 | 月×部门 | 项目预算部门占比（饼图） |
| `ads_proj_fin_fund_per_researcher_cmp_mn` | 人均项目经费对比数据集 | 月×分组 | 人均项目经费对比（条形图，需补采） |
| `ads_proj_fin_budget_exec_cmp_mn` | 预算执行对比数据集 | 月×分组 | 项目预算执行对比（分组柱） |
| `ads_proj_exec_board_summary_di` | 项目执行看板总览 | dt | 项目及时完成率/总数/在执行/完工/延期等 |
| `ads_proj_exec_key_projects_di` | 重点项目进展数据集 | dt×项目 | 重点项目进展（列表/甘特图） |

#### 5.1 库存看板（示例）
 `ads_inv_board_summary_di`：库存资金占用率、库存周转率、闲置率、紧缺物料数量、效期风险指数、盘点差异率等（按 `dt`）
 `ads_inv_board_structure_di`：库存资产结构（旭日图数据集）
 `ads_inv_board_health_bubble_mn`：库存健康度诊断（气泡图数据集：X均值库存金额 / Y周转天数 / Size=SKU数 / Color=分类）
 `ads_inv_board_age_dist_di`：库存龄分布分析（热力图/堆叠柱状图数据集）
 `ads_inv_board_trend_mn`：库存关联分析（折线+柱状：库存金额、周转率等同轴趋势）
 `ads_inv_board_expiry_calendar_di`：效期风险预警（日历/时间轴数据集）
 `ads_inv_shortage_material_di`：紧缺物料明细清单（用于预警处置，可选）

#### 5.2 采购看板（示例）
- `ads_pur_board_summary_mn`：准时交付率、项目平均采购延迟天数、紧急采购占比、单一来源采购占比、月度采购金额（按 `stat_month`）
- `ads_pur_board_trend_mn`：月度采购趋势（柱状图数据集）
- `ads_pur_cycle_by_class_mn`：物料类别的平均采购周期（数字指标：按分类输出，支持TopN）
- `ads_pur_delivery_gantt_di`：采购到货周期监控（甘特图数据集：请购/下单/计划到货/实际到货/入库）
- `ads_pur_supplier_risk_bubble_mn`：供应商风险分析（气泡图数据集：X采购金额/占比、Y风险评分）
- `ads_pur_project_monitor_di`：项目采购监控清单（列表：项目×订单行，输出当前环节、已延迟天数等）

#### 5.3 项目财务/执行看板（示例）
`ads_proj_fin_board_summary_mn`：整体预算执行率、预算调整率、经费结余率、人均项目经费、支出合规率等（按 `stat_month`；部分指标需补采财务/人事数据）
- `ads_proj_fin_budget_exec_trend_mn`：预算执行率分析（趋势折线图数据集）
- `ads_proj_fin_expense_compliance_trend_mn`：支出合规率趋势（趋势折线图数据集，需补采）
- `ads_proj_fin_cost_structure_mn`：项目成本结构占比（饼图数据集，需补采成本明细）
- `ads_proj_fin_budget_structure_mn`：项目预算结构占比（饼图数据集，需补采预算科目明细）
- `ads_proj_fin_budget_dept_share_mn`：项目预算部门占比（饼图数据集）
- `ads_proj_fin_fund_per_researcher_cmp_mn`：人均项目经费对比（分组条形图数据集，需补采人员数）
- `ads_proj_fin_budget_exec_cmp_mn`：项目预算执行对比（分组柱状图数据集）
- `ads_proj_exec_board_summary_di`：项目总数、在执行/完工/延期数量、项目及时完成率等（按 `dt`）
- `ads_proj_exec_key_projects_di`：重点项目进展（列表/甘特图数据集）

---

### 6. 缺口与落地建议（基于当前ODS范围）

以下指标在 `metrics.xlsx` 中出现，但当前 `dg.docx` ODS 范围缺少直接可计算的数据源，建议补采/新增配置表后再落地：
- 盘点差异率：需盘点表（账面 vs 实盘）或盘点结果明细
- 整体支出合规率：需支出/报销/凭证及合规规则判定结果
- 人均项目经费：需科研人员总数（HR/组织人员）维表或快照
- 单位成果经费强度：需核心成果（论文/专利/课题成果）事实表
- 项目物料供应及时率（“缺货导致实验延迟”）：需缺货事件/延期原因，或定义可审计的替代口径（如采购延迟触发）
- 供应商风险分析（风险评分）：需质量/交付/独家性等风险因子与评分规则（可先定义 `risk_score` 标准并补采数据）
- 项目成本结构占比/预算结构占比：需预算/成本按科目分解明细（预算科目、成本科目）
- 里程碑按时达成率：`metrics.xlsx` 的口径涉及里程碑，但当前ODS未提供里程碑事实/计划

---

### 7. 表结构明细（建议DDL字段）

说明：
- 本节给出 DWD/DWS/ADS 设计表的字段级结构建议（字段/类型/含义/来源或计算），用于后续建表与ETL开发。
- ODS 的完整字段明细建议以 `dg.docx` 为准（ODS表字段量较大，本文件仅在必要处映射关键字段）。
- SQL 类型以 Hive/SparkSQL 常用类型表达：`STRING/DATE/TIMESTAMP/INT/BIGINT/DECIMAL(28,8)`；如落地到其他引擎（如 PostgreSQL/StarRocks/ClickHouse）可按映射转换。

#### 7.1 通用字段规范（推荐）

| 字段 | 类型 | 说明 |
|---|---|---|
| dt | STRING | 日分区（`yyyy-MM-dd`），用于日快照/日增量表（DWD/DWS/ADS） |
| stat_month | STRING | 月分区（`yyyy-MM`），用于月度汇总表（DWS/ADS） |
| etl_time | TIMESTAMP | 本次加工时间 |
| source_system | STRING | 源系统标识（如 `nc`/`erp`/`budget`） |
| source_table | STRING | 源表或源视图名 |
| source_pk | STRING | 源主键/源组合键（便于追溯与对账） |
| source_ts | STRING | 源系统时间戳/抽取时间（如 `sourcets/sourcebts/modifiedtime` 等） |

> 备注：若平台建模标准已规定审计字段（created/updated/tenant/secret_level 等），可在上述基础上扩展，但建议保持跨表一致。

---

#### 7.2 DWD 维度表（DIM）

##### 7.2.1 `dwd_inv_master_material`（物料维度）

- 粒度：1 行/物料（`material_id`）
- 主键建议：`material_id`
- 更新策略：全量覆盖或按 `material_id` 拉链（视源系统是否保留历史版本而定）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_id | STRING | 物料ID | ODS `bd_material_v.pk_material` |
| material_code | STRING | 物料编码 | ODS `bd_material_v.code` |
| material_name | STRING | 物料名称 | ODS `bd_material_v.name`（若字段名不同，以doc为准） |
| material_spec | STRING | 规格 | ODS `bd_material_v.materialspec` |
| material_type | STRING | 型号 | ODS `bd_material_v.materialtype` |
| material_class_id | STRING | 物料分类ID | ODS `bd_material_v.pk_marbasclass` |
| brand_id | STRING | 品牌 | ODS `bd_material_v.pk_brand`（可选） |
| org_id | STRING | 所属组织 | ODS `bd_material_v.pk_org` |
| group_id | STRING | 所属集团 | ODS `bd_material_v.pk_group` |
| is_eprocurement | INT | 是否电子采购 | ODS `bd_material_v.iselectrans`（`char(1)`→0/1） |
| created_time | STRING | 创建时间 | ODS `bd_material_v.creationtime`（标准化） |
| updated_time | STRING | 最后修改时间 | ODS `bd_material_v.modifiedtime`（若存在） |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | 固定值 `bd_material_v` |
| source_pk | STRING | 源主键 | `pk_material` |
| source_ts | STRING | 源时间戳 | 取 `modifiedtime`/`sourcets` 等 |

##### 7.2.2 `dwd_inv_master_material_class`（物料分类维度）

- 粒度：1 行/物料分类（`material_class_id`）
- 主键建议：`material_class_id`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_class_id | STRING | 分类ID | ODS `bd_marbasclass.pk_marbasclass` |
| material_class_code | STRING | 分类编码 | ODS `bd_marbasclass.code` |
| material_class_name | STRING | 分类名称 | ODS `bd_marbasclass.name`（若字段名不同，以doc为准） |
| parent_class_id | STRING | 上级分类ID | ODS `bd_marbasclass.pk_parent` |
| org_id | STRING | 所属组织 | ODS `bd_marbasclass.pk_org` |
| group_id | STRING | 所属集团 | ODS `bd_marbasclass.pk_group` |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | 固定值 `bd_marbasclass` |
| source_pk | STRING | 源主键 | `pk_marbasclass` |
| source_ts | STRING | 源时间戳 | 取 `modifiedtime`/`sourcets` 等 |

##### 7.2.3 `dwd_pur_master_supplier`（供应商维度）

- 粒度：1 行/供应商（`supplier_id`）
- 主键建议：`supplier_id`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| supplier_id | STRING | 供应商ID | ODS `bd_supplier.pk_supplier` |
| supplier_code | STRING | 供应商编码 | ODS `bd_supplier.code` |
| supplier_name | STRING | 供应商名称 | ODS `bd_supplier.name`（若字段名不同，以doc为准） |
| supplier_class_id | STRING | 供应商分类ID | ODS `bd_supplier.pk_supplierclass` |
| finance_org_id | STRING | 对应业务单元/财务组织 | ODS `bd_supplier.pk_financeorg`（可选） |
| country_id | STRING | 国家/地区 | ODS `bd_supplier.pk_country`（可选） |
| org_id | STRING | 所属组织 | ODS `bd_supplier.pk_org` |
| group_id | STRING | 所属集团 | ODS `bd_supplier.pk_group` |
| created_time | STRING | 创建时间 | ODS `bd_supplier.creationtime`（若存在） |
| updated_time | STRING | 最后修改时间 | ODS `bd_supplier.modifiedtime`（若存在） |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | 固定值 `bd_supplier` |
| source_pk | STRING | 源主键 | `pk_supplier` |
| source_ts | STRING | 源时间戳 | 取 `modifiedtime`/`sourcets` 等 |

##### 7.2.4 `dwd_inv_master_warehouse`（仓库维度）

- 粒度：1 行/仓库（`warehouse_id`）
- 主键建议：`warehouse_id`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| warehouse_id | STRING | 仓库ID | ODS `bd_stordoc.pk_stordoc` |
| warehouse_code | STRING | 仓库编码 | ODS `bd_stordoc.code` |
| warehouse_name | STRING | 仓库名称 | ODS `bd_stordoc.name`（若字段名不同，以doc为准） |
| org_id | STRING | 所属库存组织 | ODS `bd_stordoc.pk_org` |
| group_id | STRING | 所属集团 | ODS `bd_stordoc.pk_group` |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | 固定值 `bd_stordoc` |
| source_pk | STRING | 源主键 | `pk_stordoc` |
| source_ts | STRING | 源时间戳 | 取 `modifiedtime`/`sourcets` 等 |

##### 7.2.5 `dwd_proj_master_project`（项目维度）

- 粒度：1 行/项目（`project_id`）
- 主键建议：`project_id`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | ODS `bd_project.pk_project` |
| project_code | STRING | 项目编码 | ODS `bd_project.code`（若字段名不同，以doc为准） |
| project_name | STRING | 项目名称 | ODS `bd_project.name`（若字段名不同，以doc为准） |
| parent_project_id | STRING | 父项目ID | ODS `bd_project.pk_parentpro` |
| project_state_id | STRING | 项目状态ID | ODS `bd_project.pk_projectstate` |
| duty_dept_id | STRING | 责任部门ID | ODS `bd_project.pk_duty_dept` |
| duty_org_id | STRING | 责任组织ID | ODS `bd_project.pk_duty_org`（可选） |
| plan_start_date | DATE | 计划开始日期 | ODS `bd_project.plan_start_date`（标准化） |
| plan_finish_date | DATE | 计划完成日期 | ODS `bd_project.plan_finish_date`（标准化） |
| actu_start_date | DATE | 实际开始日期 | ODS `bd_project.actu_start_date`（标准化） |
| actu_finish_date | DATE | 实际完成日期 | ODS `bd_project.actu_finish_date`（标准化） |
| plan_duration_days | INT | 计划工期(天) | ODS `bd_project.planduration` |
| actu_duration_days | INT | 实际工期(天) | ODS `bd_project.actuduration` |
| plan_priority | INT | 计划优先级 | ODS `bd_project.planpriority` |
| status_date | DATE | 状态日期 | ODS `bd_project.status_date`（标准化） |
| org_id | STRING | 项目组织 | ODS `bd_project.pk_org` |
| group_id | STRING | 所属集团 | ODS `bd_project.pk_group` |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | 固定值 `bd_project` |
| source_pk | STRING | 源主键 | `pk_project` |
| source_ts | STRING | 源时间戳 | 取 `modifiedtime`/`sourcets` 等 |

##### 7.2.6 `dwd_inv_master_material_policy`（物料策略维表，补采/配置）

> 用于“安全库存/关键物料/采购周期长”等指标。当前 ODS 未提供，建议以配置表方式维护并纳入治理审批。

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_id | STRING | 物料ID | 关联 `dwd_inv_master_material.material_id` |
| warehouse_id | STRING | 仓库ID | 关联 `dwd_inv_master_warehouse.warehouse_id` |
| safety_stock_qty | DECIMAL(28,8) | 安全库存数量 | 配置/补采 |
| lead_time_days | INT | 采购提前期(天) | 配置/补采 |
| is_critical | INT | 是否关键物料(0/1) | 配置/补采 |
| effective_date | DATE | 生效日期 | 配置 |
| expire_date | DATE | 失效日期 | 配置 |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | `config` |
| source_table | STRING | 源表 | `material_policy`（示例） |
| source_pk | STRING | 源主键 | `material_id||'|'||warehouse_id||'|'||effective_date` |
| source_ts | STRING | 源时间戳 | 配置变更时间 |

---

#### 7.3 DWD 事实表（FACT）

##### 7.3.1 `dwd_inv_fact_stock_opening_balance`（期初余额明细）

- 粒度：1 行/期初余额明细行（建议以源表体主键做去重）
- 主键建议：`opening_line_id`（或 `source_pk`）
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| opening_bill_id | STRING | 期初余额单据ID | ODS `ic_openbal_h.cgeneralhid`（或等价字段） |
| opening_line_id | STRING | 期初余额行ID | ODS `ic_openbal_b.cgeneralbid`（或等价字段） |
| biz_date | DATE | 业务日期 | ODS `ic_openbal_b.dbizdate`（标准化） |
| material_id | STRING | 物料ID | ODS `ic_openbal_b.cmaterialoid` |
| warehouse_id | STRING | 仓库ID | ODS `ic_openbal_b.cbodywarehouseid`（或等价字段） |
| project_id | STRING | 项目ID | ODS `ic_openbal_b.cprojectid` |
| supplier_id | STRING | 供应商ID | ODS `ic_openbal_b.cvendorid` |
| batch_id | STRING | 批次ID | ODS `ic_openbal_b.pk_batchcode` |
| stock_state_id | STRING | 库存状态 | ODS `ic_openbal_b.cstateid` |
| produced_date | DATE | 生产日期 | ODS `ic_openbal_b.dproducedate`（标准化） |
| expiry_date | DATE | 失效/到期日期 | ODS `ic_openbal_b.dvalidate`（标准化） |
| opening_qty | DECIMAL(28,8) | 期初主数量 | ODS `ic_openbal_b.nnum` |
| opening_amt | DECIMAL(28,8) | 期初金额 | ODS `ic_openbal_b.ncostmny` |
| opening_unit_price | DECIMAL(28,8) | 期初单价 | ODS `ic_openbal_b.ncostprice` |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `ic_openbal_h/ic_openbal_b` |
| source_pk | STRING | 源主键 | `opening_line_id` |
| source_ts | STRING | 源时间戳 | 取 `sourcets/sourcebts/modifiedtime` 等 |

##### 7.3.2 `dwd_inv_fact_stock_flow`（库存流水明细）

- 粒度：1 行/库存流水（`flow_id`）
- 主键建议：`flow_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| flow_id | STRING | 流水主键 | ODS `ic_flow.pk_flow` |
| biz_date | DATE | 业务日期 | ODS `ic_flow.dbizdate`（标准化） |
| bill_code | STRING | 单据号 | ODS `ic_flow.vbillcode` |
| bill_type_code | STRING | 单据类型编码 | ODS `ic_flow.cbilltypecode` |
| tran_type_code | STRING | 出入库类型编码 | ODS `ic_flow.vtrantypecode` |
| material_id | STRING | 物料ID | ODS `ic_flow.cmaterialoid` |
| warehouse_id | STRING | 仓库ID | ODS `ic_flow.cwarehouseid` |
| project_id | STRING | 项目ID | ODS `ic_flow.cprojectid` |
| supplier_id | STRING | 供应商ID | ODS `ic_flow.cvendorid` |
| batch_id | STRING | 批次ID | ODS `ic_flow.pk_batchcode` |
| stock_state_id | STRING | 库存状态 | ODS `ic_flow.cstateid` |
| produced_date | DATE | 生产日期 | ODS `ic_flow.dproducedate`（标准化） |
| expiry_date | DATE | 失效/到期日期 | ODS `ic_flow.dvalidate`（标准化） |
| in_qty | DECIMAL(28,8) | 入库主数量 | `coalesce(ic_flow.ninnum,0)` |
| out_qty | DECIMAL(28,8) | 出库主数量 | `coalesce(ic_flow.noutnum,0)` |
| net_qty | DECIMAL(28,8) | 净数量 | `in_qty - out_qty` |
| in_amt | DECIMAL(28,8) | 入库金额 | `case when in_qty>0 then ic_flow.ncostmny else 0 end` |
| out_amt | DECIMAL(28,8) | 出库金额 | `case when out_qty>0 then ic_flow.ncostmny else 0 end` |
| net_amt | DECIMAL(28,8) | 净金额 | `in_amt - out_amt` |
| flow_amt_raw | DECIMAL(28,8) | 源金额（对账用） | ODS `ic_flow.ncostmny` |
| cost_price | DECIMAL(28,8) | 单价 | ODS `ic_flow.ncostprice` |
| dt | STRING | 日分区 | 装载分区（通常等于 `biz_date`） |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `ic_flow` |
| source_pk | STRING | 源主键 | `pk_flow` |
| source_ts | STRING | 源时间戳 | 取 `sourcets/sourcebts` 等 |

##### 7.3.3 `dwd_inv_fact_inbound_line`（统一入库明细）

- 粒度：1 行/入库单明细行（跨来源统一）
- 主键建议：`inbound_line_id`（或 `source_pk`）
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| inbound_type | STRING | 入库类型 | `purchase_in/general_in/purchase_infi` |
| inbound_bill_id | STRING | 入库单ID | 源表头主键（如 `cgeneralhid/pk_stockps` 等） |
| inbound_line_id | STRING | 入库单行ID | 源表体主键（如 `cgeneralbid/pk_stockps_b` 等） |
| biz_date | DATE | 入库日期/业务日期 | 优先取源表体 `dbizdate`，否则取表头 `dbilldate` |
| material_id | STRING | 物料ID | `cmaterialoid/pk_material` 等映射 |
| warehouse_id | STRING | 仓库ID | `cbodywarehouseid/pk_stordoc` 等映射 |
| project_id | STRING | 项目ID | `cprojectid/pk_project` 等映射 |
| supplier_id | STRING | 供应商ID | `cvendorid/pk_supplier` 等映射 |
| batch_id | STRING | 批次ID | `pk_batchcode`（若有） |
| in_qty | DECIMAL(28,8) | 入库数量 | 统一数量口径（如 `ninnum/nnum/nassistnum`） |
| in_amt_excl_tax | DECIMAL(28,8) | 入库无税金额 | `nmny/norigmny/nestmny` 等 |
| in_amt_incl_tax | DECIMAL(28,8) | 入库含税金额 | `norigtaxmny/ntaxmny` 等 |
| in_unit_price_excl_tax | DECIMAL(28,8) | 无税单价 | `nprice/norigprice` 等 |
| in_unit_price_incl_tax | DECIMAL(28,8) | 含税单价 | `ntaxprice/norigtaxprice` 等 |
| source_bill_code | STRING | 源单据号 | `vbillcode/vsourcecode` 等 |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `ic_purchasein_*/ic_generalin_*/po_purchaseinfi*` |
| source_pk | STRING | 源主键 | 源表体主键 |
| source_ts | STRING | 源时间戳 | 取 `sourcebts/sourcets/modifiedtime` 等 |

##### 7.3.4 `dwd_inv_fact_outbound_line`（统一出库明细）

- 粒度：1 行/出库单明细行（跨来源统一）
- 主键建议：`outbound_line_id`（或 `source_pk`）
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| outbound_type | STRING | 出库类型 | `general_out/material_out` |
| outbound_bill_id | STRING | 出库单ID | 源表头主键（如 `cgeneralhid` 等） |
| outbound_line_id | STRING | 出库单行ID | 源表体主键（如 `cgeneralbid` 等） |
| biz_date | DATE | 出库日期/业务日期 | 优先取源表体 `dbizdate`，否则取表头日期 |
| material_id | STRING | 物料ID | `cmaterialoid` 等 |
| warehouse_id | STRING | 仓库ID | `cbodywarehouseid/cwarehouseid` 等 |
| project_id | STRING | 项目ID | `cprojectid` 等 |
| batch_id | STRING | 批次ID | `pk_batchcode`（若有） |
| out_qty | DECIMAL(28,8) | 出库数量 | `noutnum/nnum` 等映射 |
| out_amt | DECIMAL(28,8) | 出库金额 | 优先取成本/金额字段（如 `ncostmny`） |
| out_unit_price | DECIMAL(28,8) | 出库单价 | `ncostprice` 等 |
| source_bill_code | STRING | 源单据号 | `vbillcode/vsourcecode` 等 |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `ic_generalout_*/ic_material_*` |
| source_pk | STRING | 源主键 | 源表体主键 |
| source_ts | STRING | 源时间戳 | 取 `sourcebts/sourcets/modifiedtime` 等 |

##### 7.3.5 `dwd_pur_fact_pray_line`（请购单明细）

- 粒度：1 行/请购单明细（`pray_line_id`）
- 主键建议：`pray_line_id`
- 分区：`dt`（建议等于 `apply_date`）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| pray_bill_id | STRING | 请购单ID | ODS `po_praybill.pk_praybill` |
| pray_line_id | STRING | 请购单行ID | ODS `po_praybill_b.pk_praybill_b` |
| project_id | STRING | 项目ID | ODS `po_praybill_b.cprojectid` |
| material_id | STRING | 物料ID | ODS `po_praybill_b.pk_material` |
| suggest_supplier_id | STRING | 建议供应商 | ODS `po_praybill_b.pk_suggestsupplier` |
| req_dept_id | STRING | 需求部门 | ODS `po_praybill_b.pk_reqdept` |
| req_warehouse_id | STRING | 需求仓库 | ODS `po_praybill_b.pk_reqstor` |
| apply_date | DATE | 请购日期 | ODS `po_praybill_b.dbilldate`（标准化） |
| require_date | DATE | 需求日期 | ODS `po_praybill_b.dreqdate`（标准化） |
| suggest_order_date | DATE | 建议订货日期 | ODS `po_praybill_b.dsuggestdate`（标准化） |
| req_qty | DECIMAL(28,8) | 请购数量 | ODS `po_praybill_b.nnum`（或 `nastnum`） |
| req_amt_incl_tax | DECIMAL(28,8) | 请购含税金额 | ODS `po_praybill_b.ntaxmny`（若存在） |
| req_unit_price_incl_tax | DECIMAL(28,8) | 请购含税单价 | ODS `po_praybill_b.ntaxprice` |
| dt | STRING | 日分区 | `date_format(apply_date,'yyyy-MM-dd')` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `po_praybill/po_praybill_b` |
| source_pk | STRING | 源主键 | `pk_praybill_b` |
| source_ts | STRING | 源时间戳 | ODS `po_praybill_b.sourcebts/sourcets` 等 |

##### 7.3.6 `dwd_pur_fact_order_line`（采购订单明细）

- 粒度：1 行/采购订单明细（`order_line_id`）
- 主键建议：`order_line_id`
- 分区：`dt`（建议等于 `order_date`）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| order_id | STRING | 采购订单ID | ODS `po_order.pk_order` |
| order_line_id | STRING | 采购订单行ID | ODS `po_order_b.pk_order_b` |
| pray_bill_id | STRING | 来源请购单ID | ODS `po_order_b.cpraybillhid`（若有） |
| pray_line_id | STRING | 来源请购单行ID | ODS `po_order_b.cpraybillbid`（若有） |
| project_id | STRING | 项目ID | ODS `po_order_b.cprojectid` 或 `po_order.pk_project` |
| supplier_id | STRING | 供应商ID | ODS `po_order.pk_supplier`（或行级 `pk_supplier`） |
| material_id | STRING | 物料ID | ODS `po_order_b.pk_material` |
| order_date | DATE | 订单日期 | ODS `po_order_b.dbilldate`（标准化） |
| plan_arrive_date | DATE | 计划到货日期 | ODS `po_order_b.dplanarrvdate`（标准化） |
| order_qty | DECIMAL(28,8) | 订单数量 | ODS `po_order_b.nnum`（或 `nastnum`） |
| order_amt_excl_tax | DECIMAL(28,8) | 订单无税金额 | ODS `po_order_b.nmny` |
| order_unit_price_excl_tax | DECIMAL(28,8) | 无税单价 | ODS `po_order_b.nprice` |
| order_amt_incl_tax | DECIMAL(28,8) | 订单含税金额 | ODS `po_order_b.norigtaxmny`（若存在） |
| order_unit_price_incl_tax | DECIMAL(28,8) | 含税单价 | ODS `po_order_b.norigtaxprice/ntaxprice`（若存在） |
| order_status | INT | 单据状态 | ODS `po_order.forderstatus`（或行级状态） |
| is_final_close | INT | 最终关闭 | ODS `po_order.bfinalclose`（`char(1)`→0/1） |
| dt | STRING | 日分区 | `date_format(order_date,'yyyy-MM-dd')` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `po_order/po_order_b` |
| source_pk | STRING | 源主键 | `pk_order_b` |
| source_ts | STRING | 源时间戳 | ODS `po_order_b.sourcebts` 等 |

##### 7.3.7 `dwd_pur_fact_arrival_line`（到货单明细）

- 粒度：1 行/到货单明细（`arrive_line_id`）
- 主键建议：`arrive_line_id`
- 分区：`dt`（建议等于 `arrive_date`）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| arrive_id | STRING | 到货单ID | ODS `po_arriveorder.pk_arriveorder` |
| arrive_line_id | STRING | 到货单行ID | ODS `po_arriveorder_b.pk_arriveorder_b` |
| order_id | STRING | 采购订单ID | ODS `po_arriveorder_b.pk_order` |
| order_line_id | STRING | 采购订单行ID | ODS `po_arriveorder_b.pk_order_b` |
| supplier_id | STRING | 供应商ID | ODS `po_arriveorder.pk_supplier` |
| material_id | STRING | 物料ID | ODS `po_arriveorder_b.pk_material` |
| arrive_date | DATE | 实际到货日期 | ODS `po_arriveorder.dbilldate`（标准化） |
| arrive_qty | DECIMAL(28,8) | 到货数量 | ODS `po_arriveorder_b.nnum/nastnum`（按doc映射） |
| dt | STRING | 日分区 | `date_format(arrive_date,'yyyy-MM-dd')` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `po_arriveorder/po_arriveorder_b` |
| source_pk | STRING | 源主键 | `pk_arriveorder_b` |
| source_ts | STRING | 源时间戳 | ODS `po_arriveorder_b.sourcebts` 等 |

##### 7.3.8 `dwd_pur_fact_purchase_in_line`（采购入库明细）

- 粒度：1 行/采购入库明细（`stock_in_line_id`）
- 主键建议：`stock_in_line_id`
- 分区：`dt`（建议等于 `biz_date`/`in_date`）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| stock_in_id | STRING | 入库单ID | ODS `po_purchaseinfi.pk_stockps` |
| stock_in_line_id | STRING | 入库单行ID | ODS `po_purchaseinfi_b.pk_stockps_b` |
| order_id | STRING | 采购订单ID | ODS `po_purchaseinfi_b.pk_order` |
| order_line_id | STRING | 采购订单行ID | ODS `po_purchaseinfi_b.pk_order_b` |
| project_id | STRING | 项目ID | ODS `po_purchaseinfi_b.cprojectid` |
| supplier_id | STRING | 供应商ID | ODS `po_purchaseinfi_b.pk_supplier` |
| material_id | STRING | 物料ID | ODS `po_purchaseinfi_b.pk_material` |
| in_date | DATE | 入库日期 | ODS `po_purchaseinfi.dbilldate`（标准化） |
| biz_date | DATE | 业务日期 | ODS `po_purchaseinfi_b.dbizdate`（标准化） |
| require_date | DATE | 需求日期 | ODS `po_purchaseinfi_b.drequiredate`（标准化） |
| in_qty | DECIMAL(28,8) | 实入主数量 | ODS `po_purchaseinfi_b.ninnum` |
| in_amt_excl_tax | DECIMAL(28,8) | 本币无税金额 | ODS `po_purchaseinfi_b.nmny`（或 `nestmny`） |
| in_amt_incl_tax | DECIMAL(28,8) | 价税合计 | ODS `po_purchaseinfi_b.norigtaxmny`（若存在） |
| cost_amt | DECIMAL(28,8) | 成本金额 | ODS `po_purchaseinfi_b.ncostmny`（若存在） |
| stock_state_id | STRING | 库存状态 | ODS `po_purchaseinfi_b.cstateid` |
| dt | STRING | 日分区 | 优先取 `biz_date`，否则取 `in_date` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `po_purchaseinfi/po_purchaseinfi_b` |
| source_pk | STRING | 源主键 | `pk_stockps_b` |
| source_ts | STRING | 源时间戳 | ODS `po_purchaseinfi_b.sourcebts` 等 |

##### 7.3.9 `dwd_pur_fact_contract_line`（采购合同明细）

> 合同明细字段在 `dg.docx` 中较多，且与“价格信息/关联合同”存在多主键字段。建议先确定业务主键（通常为 `pk_ct_pu_b`）并统一抽取最关键字段，其他字段按需扩展。

- 粒度：1 行/合同明细（`contract_line_id`）
- 主键建议：`contract_line_id`
- 分区：`dt`（建议等于合同生效日或抽取日）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| contract_id | STRING | 合同ID | ODS `ct_pu.pk_ct_pu` |
| contract_line_id | STRING | 合同明细ID | ODS `ct_pu_b.pk_ct_pu_b` |
| related_pray_line_id | STRING | 关联请购行ID | ODS `ct_pu_b.pk_praybill_b`（若有） |
| contract_effective_date | DATE | 实际生效日期 | ODS `ct_pu.actualvalidate`（标准化） |
| contract_invalid_date | DATE | 实际终止日期 | ODS `ct_pu.actualinvalidate`（标准化） |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | 固定值/配置 |
| source_table | STRING | 源表 | `ct_pu/ct_pu_b` |
| source_pk | STRING | 源主键 | `pk_ct_pu_b` |
| source_ts | STRING | 源时间戳 | 取 `sourcets/modifiedtime` 等 |

##### 7.3.10 `dwd_proj_fact_budget_execution`（项目预算执行快照，视图补齐后落地）

- 粒度：1 行/项目/日（`project_id, dt`）
- 主键建议：`project_id, dt`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 预算执行视图 `PK_Project` |
| project_code | STRING | 项目编码 | 预算执行视图“项目编码” |
| project_name | STRING | 项目名称 | 预算执行视图“项目名称” |
| parent_project_name | STRING | 父项目 | 预算执行视图“父项目” |
| duty_dept_name | STRING | 责任部门 | 预算执行视图“责任部门” |
| budget_amt | DECIMAL(28,8) | 预算金额 | 预算执行视图“预算金额” |
| budget_adjust_amt | DECIMAL(28,8) | 调整金额 | 预算执行视图“调整金额” |
| reserved_amt | DECIMAL(28,8) | 预占金额 | 预算执行视图“预占金额” |
| exec_amt | DECIMAL(28,8) | 执行金额 | 预算执行视图“执行金额” |
| exec_hist_amt | DECIMAL(28,8) | 历史执行金额 | 预算执行视图“历史执行金额” |
| approved_budget_amt | DECIMAL(28,8) | 批复预算（含调整） | `budget_amt + budget_adjust_amt` |
| balance_amt | DECIMAL(28,8) | 预算结余 | `approved_budget_amt - exec_amt` |
| dt | STRING | 日分区 | 快照日期 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | `budget` |
| source_table | STRING | 源视图 | 待补齐视图名 |
| source_pk | STRING | 源主键 | `project_id||'|'||dt` |
| source_ts | STRING | 源时间戳 | 视图抽取时间 |

##### 7.3.11 `dwd_pur_wide_order_lifecycle_line`（采购全流程宽表）

- 粒度：1 行/采购订单行（`order_line_id`）
- 主键建议：`order_line_id`
- 分区：`dt`（建议等于 `order_date` 或“宽表更新日”）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| order_id | STRING | 采购订单ID | `dwd_pur_fact_order_line.order_id` |
| order_line_id | STRING | 采购订单行ID | `dwd_pur_fact_order_line.order_line_id` |
| pray_bill_id | STRING | 请购单ID | 由 `po_order_b.cpraybillhid` 关联 `dwd_pur_fact_pray_line` |
| pray_line_id | STRING | 请购单行ID | 由 `po_order_b.cpraybillbid` 关联 |
| project_id | STRING | 项目ID | 订单/请购/入库综合择优 |
| supplier_id | STRING | 供应商ID | 订单/到货/入库综合择优 |
| material_id | STRING | 物料ID | 订单行物料 |
| apply_date | DATE | 请购日期 | `dwd_pur_fact_pray_line.apply_date` |
| require_date | DATE | 需求日期 | `dwd_pur_fact_pray_line.require_date` 或入库 `drequiredate` |
| order_date | DATE | 订单日期 | `dwd_pur_fact_order_line.order_date` |
| plan_arrive_date | DATE | 计划到货日期 | `dwd_pur_fact_order_line.plan_arrive_date` |
| arrive_date | DATE | 实际到货日期 | `dwd_pur_fact_arrival_line.arrive_date`（可取最早到货） |
| stock_in_date | DATE | 实际入库日期 | `dwd_pur_fact_purchase_in_line.biz_date/in_date`（可取最早入库） |
| order_qty | DECIMAL(28,8) | 订单数量 | `dwd_pur_fact_order_line.order_qty` |
| order_amt | DECIMAL(28,8) | 订单金额 | `dwd_pur_fact_order_line.order_amt_excl_tax`（口径可选） |
| arrive_qty | DECIMAL(28,8) | 到货数量 | 聚合 `dwd_pur_fact_arrival_line.arrive_qty` |
| stock_in_qty | DECIMAL(28,8) | 入库数量 | 聚合 `dwd_pur_fact_purchase_in_line.in_qty` |
| delay_days | INT | 延迟天数 | `datediff(coalesce(stock_in_date,arrive_date), require_date)` |
| cycle_days | INT | 全流程天数 | `datediff(coalesce(stock_in_date,arrive_date), apply_date)` |
| is_ontime | INT | 是否准时(0/1) | `coalesce(stock_in_date,arrive_date) <= plan_arrive_date` |
| current_stage | STRING | 当前环节 | 按日期字段判断：已请购/已下单/已到货/已入库 |
| dt | STRING | 日分区 | 装载分区 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
| source_system | STRING | 源系统 | `nc`（示例） |
| source_table | STRING | 源表集合 | `po_praybill* / po_order* / po_arriveorder* / po_purchaseinfi*` |
| source_pk | STRING | 源主键 | `order_line_id` |
| source_ts | STRING | 源时间戳 | 取各来源最大时间戳 |

---

#### 7.4 DWS 主题汇总表（按指标复用）

##### 7.4.1 `dws_inv_stock_snapshot_di`（库存日快照）

- 粒度：`dt, material_id, warehouse_id, batch_id, project_id`
- 分区：`dt`
- 生成逻辑：`期初余额 + 截止dt净流水累加`，并补充“最近一次出库日期/库龄/效期”等派生字段

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_id | STRING | 物料ID | 来自明细（opening/flow） |
| warehouse_id | STRING | 仓库ID | 来自明细（opening/flow） |
| batch_id | STRING | 批次ID | 来自明细（opening/flow） |
| project_id | STRING | 项目ID | 来自明细（opening/flow） |
| stock_qty | DECIMAL(28,8) | 结存数量 | `opening_qty + sum(net_qty)` |
| stock_amt | DECIMAL(28,8) | 结存金额 | `opening_amt + sum(net_amt)` |
| first_in_date | DATE | 首次入库日期 | `min(in_date)`（如可取 opening.biz_date 或 flow.in 事件） |
| last_out_date | DATE | 最近出库日期 | `max(case when out_qty>0 then biz_date end)` |
| age_days | INT | 库龄(天) | `datediff(to_date(dt), coalesce(last_out_date, first_in_date))`（口径可调） |
| produced_date | DATE | 生产日期 | 批次维度字段（择优取明细） |
| expiry_date | DATE | 到期/失效日期 | 批次维度字段（择优取明细） |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.2 `dws_inv_stock_summary_di`（库存日汇总：按分类）

- 粒度：`dt, material_class_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_class_id | STRING | 物料分类 | 由 `dwd_inv_master_material` 关联 |
| stock_qty | DECIMAL(28,8) | 库存数量 | sum |
| stock_amt | DECIMAL(28,8) | 库存金额 | sum |
| sku_cnt | BIGINT | SKU数 | `count(distinct material_id)` |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.3 `dws_inv_turnover_mn`（库存月周转）

- 粒度：`stat_month, material_class_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_class_id | STRING | 物料分类 | 由维度关联 |
| out_amt | DECIMAL(28,8) | 月出库金额 | `sum(dwd_inv_fact_stock_flow.out_amt)` |
| avg_stock_amt | DECIMAL(28,8) | 月平均库存金额 | `avg(每日总库存金额)` |
| turnover_rate | DECIMAL(28,8) | 周转率 | `out_amt / avg_stock_amt` |
| turnover_days | DECIMAL(28,8) | 周转天数 | `30 / turnover_rate`（按月天数可调） |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.4 `dws_inv_idle_stock_di`（闲置库存：按阈值桶）

- 粒度：`dt, idle_bucket`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| idle_bucket | STRING | 闲置阈值桶 | `3m/6m/9m/12m` |
| idle_stock_amt | DECIMAL(28,8) | 闲置库存金额 | sum |
| total_stock_amt | DECIMAL(28,8) | 总库存金额 | sum |
| idle_rate | DECIMAL(28,8) | 闲置率 | `idle_stock_amt/total_stock_amt` |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.5 `dws_inv_expiry_risk_di`（效期风险：按临期桶）

- 粒度：`dt, months_to_expiry_bucket`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| months_to_expiry_bucket | STRING | 临期桶 | `<=1m/<=3m/<=6m/<=12m` 等 |
| risk_sku_cnt | BIGINT | 临期SKU数 | distinct |
| risk_stock_amt | DECIMAL(28,8) | 临期金额 | sum |
| total_sku_cnt | BIGINT | 总SKU数 | distinct |
| risk_rate | DECIMAL(28,8) | 临期SKU占比 | `risk_sku_cnt/total_sku_cnt` |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.6 `dws_pur_delivery_kpi_di`（采购交付KPI）

- 粒度：`dt, project_id, supplier_id`（可扩展到 `material_class_id`）
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 来自宽表 |
| supplier_id | STRING | 供应商ID | 来自宽表 |
| order_line_cnt | BIGINT | 订单行数 | count |
| ontime_cnt | BIGINT | 准时行数 | sum(is_ontime) |
| ontime_rate | DECIMAL(28,8) | 准时率 | `ontime_cnt/order_line_cnt` |
| avg_delay_days | DECIMAL(28,8) | 平均延迟天数 | avg(delay_days) |
| purchase_amt | DECIMAL(28,8) | 采购金额 | sum(order_amt) |
| dt | STRING | 日分区 | 统计日（可取宽表更新日或业务日） |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.7 `dws_pur_cycle_by_class_mn`（采购周期：按物料分类）

- 粒度：`stat_month, material_class_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_class_id | STRING | 物料分类 | 由维度关联 |
| order_line_cnt | BIGINT | 订单行数 | count |
| avg_cycle_days | DECIMAL(28,8) | 平均全流程天数 | avg(cycle_days) |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.8 `dws_pur_urgent_ratio_mn`（紧急采购占比）

- 粒度：`stat_month`（可扩展 `project_id`）
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| urgent_amt | DECIMAL(28,8) | 紧急采购金额 | sum(case when is_urgent=1 then order_amt end) |
| total_amt | DECIMAL(28,8) | 总采购金额 | sum(order_amt) |
| urgent_rate | DECIMAL(28,8) | 紧急占比 | `urgent_amt/total_amt` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.9 `dws_pur_single_source_ratio_mn`（单一来源占比）

- 粒度：`stat_month`（可扩展 `project_id`）
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| single_source_sku_cnt | BIGINT | 单一来源SKU数 | 基于“SKU-供应商数=1” |
| total_sku_cnt | BIGINT | 总SKU数 | distinct |
| single_source_rate | DECIMAL(28,8) | SKU占比 | `single_source_sku_cnt/total_sku_cnt` |
| single_source_amt_rate | DECIMAL(28,8) | 金额占比 | `single_source_amt/total_amt`（如落地） |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.10 `dws_pur_purchase_trend_mn`（月度采购趋势）

- 粒度：`stat_month`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| purchase_amt | DECIMAL(28,8) | 月采购金额 | sum(order_amt)（或入库金额，需统一口径） |
| order_line_cnt | BIGINT | 订单行数 | count |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.11 `dws_proj_budget_exec_di`（预算执行日快照）

- 粒度：`dt, project_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 来自 `dwd_proj_fact_budget_execution` |
| budget_amt | DECIMAL(28,8) | 原预算金额 | 同上 |
| budget_adjust_amt | DECIMAL(28,8) | 调整金额 | 同上 |
| approved_budget_amt | DECIMAL(28,8) | 批复预算(含调整) | `budget_amt+budget_adjust_amt` |
| reserved_amt | DECIMAL(28,8) | 预占金额 | 同上 |
| exec_amt | DECIMAL(28,8) | 执行金额 | 同上 |
| balance_amt | DECIMAL(28,8) | 结余金额 | `approved_budget_amt-exec_amt` |
| exec_rate | DECIMAL(28,8) | 执行率 | `exec_amt/approved_budget_amt` |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.12 `dws_proj_budget_exec_mn`（预算执行月汇总）

- 粒度：`stat_month, project_id`
- 分区：`stat_month`
- 建议口径：取月末快照（推荐）或月内累计（需业务确认）

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 来自日快照 |
| approved_budget_amt | DECIMAL(28,8) | 批复预算 | 月末快照 |
| exec_amt | DECIMAL(28,8) | 执行金额 | 月末快照 |
| exec_rate | DECIMAL(28,8) | 执行率 | `exec_amt/approved_budget_amt` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.4.13 `dws_proj_progress_di`（项目进度日快照）

- 粒度：`dt, project_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 来自项目维度 |
| plan_finish_date | DATE | 计划完成日期 | `dwd_proj_master_project.plan_finish_date` |
| actu_finish_date | DATE | 实际完成日期 | `dwd_proj_master_project.actu_finish_date` |
| is_finished | INT | 是否完工(0/1) | `actu_finish_date is not null` |
| is_delayed | INT | 是否延期(0/1) | `not finished and dt>plan_finish_date` |
| delay_days | INT | 延期天数 | `datediff(coalesce(actu_finish_date,dt), plan_finish_date)` |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

---

#### 7.5 ADS 应用表（面向看板/报表输出）

##### 7.5.1 `ads_inv_board_summary_di`（库存看板总览）

- 粒度：`dt`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| inventory_amt | DECIMAL(28,8) | 库存总金额 | `sum(dws_inv_stock_snapshot_di.stock_amt)` |
| research_fund_amt | DECIMAL(28,8) | 科研经费总额 | `sum(dws_proj_budget_exec_di.approved_budget_amt)` |
| inventory_fund_occupancy_rate | DECIMAL(28,8) | 资金占用率 | `inventory_amt/research_fund_amt` |
| turnover_rate_mn | DECIMAL(28,8) | 当月周转率 | 来自 `dws_inv_turnover_mn`（可选） |
| turnover_days_mn | DECIMAL(28,8) | 当月周转天数 | 来自 `dws_inv_turnover_mn`（可选） |
| idle_rate_3m | DECIMAL(28,8) | 闲置率(3月) | 来自 `dws_inv_idle_stock_di`（bucket=3m） |
| expiry_risk_index_3m | DECIMAL(28,8) | 临期风险指数(3月) | 来自 `dws_inv_expiry_risk_di`（bucket=<=3m） |
| shortage_material_cnt | BIGINT | 紧缺物料数 | 依赖策略表（可选） |
| project_material_supply_ontime_rate | DECIMAL(28,8) | 项目物料供应及时率 | 当前ODS缺“缺货导致实验延迟”，可用采购延迟替代口径（见 `metrics.md`） |
| stocktake_diff_rate | DECIMAL(28,8) | 盘点差异率 | 需补采盘点单/盘点结果明细（当前占位） |
| dt | STRING | 日分区 | 看板日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.2 `ads_inv_board_structure_di`（库存结构：旭日图数据）

- 粒度：`dt, lvl1_class_id, lvl2_class_id`（可扩展到三级）
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| lvl1_class_id | STRING | 一级分类ID | 维度展开 |
| lvl1_class_name | STRING | 一级分类名称 | 维度展开 |
| lvl2_class_id | STRING | 二级分类ID | 维度展开 |
| lvl2_class_name | STRING | 二级分类名称 | 维度展开 |
| stock_amt | DECIMAL(28,8) | 库存金额 | sum |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.3 `ads_inv_board_age_dist_di`（库存龄分布）

- 粒度：`dt, material_class_id, age_bucket`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_class_id | STRING | 物料分类 | 维度关联 |
| age_bucket | STRING | 库龄桶 | `0-30/31-90/91-180/180+` |
| stock_amt | DECIMAL(28,8) | 库存金额 | sum |
| stock_qty | DECIMAL(28,8) | 库存数量 | sum |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.4 `ads_inv_board_expiry_calendar_di`（效期预警日历）

- 粒度：`dt, expiry_dt`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| expiry_dt | DATE | 到期日期 | 来自快照 |
| batch_cnt | BIGINT | 批次数 | distinct(batch_id) |
| stock_amt | DECIMAL(28,8) | 到期金额 | sum(stock_amt) |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.5 `ads_pur_board_summary_mn`（采购看板总览）

- 粒度：`stat_month`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| ontime_delivery_rate | DECIMAL(28,8) | 准时交付率 | 来自 `dws_pur_delivery_kpi_di` 汇总 |
| avg_delay_days | DECIMAL(28,8) | 平均延迟天数 | 来自 `dws_pur_delivery_kpi_di` 汇总 |
| urgent_rate | DECIMAL(28,8) | 紧急采购占比 | `dws_pur_urgent_ratio_mn.urgent_rate` |
| single_source_rate | DECIMAL(28,8) | 单一来源SKU占比 | `dws_pur_single_source_ratio_mn.single_source_rate` |
| single_source_amt_rate | DECIMAL(28,8) | 单一来源金额占比 | `dws_pur_single_source_ratio_mn.single_source_amt_rate`（如落地） |
| purchase_amt | DECIMAL(28,8) | 月采购金额 | `dws_pur_purchase_trend_mn.purchase_amt` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.6 `ads_pur_project_monitor_di`（项目采购监控清单）

- 粒度：`dt, project_id, order_line_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 宽表 |
| project_name | STRING | 项目名称 | 关联项目维度 |
| order_line_id | STRING | 订单行ID | 宽表 |
| material_id | STRING | 物料ID | 宽表 |
| material_name | STRING | 物料名称 | 关联物料维度 |
| apply_date | DATE | 请购日期 | 宽表 |
| require_date | DATE | 需求日期 | 宽表 |
| plan_arrive_date | DATE | 计划到货日期 | 宽表 |
| arrive_date | DATE | 实际到货日期 | 宽表 |
| stock_in_date | DATE | 实际入库日期 | 宽表 |
| current_stage | STRING | 当前环节 | 宽表 |
| delayed_days | INT | 已延迟天数 | 基于宽表计算 |
| order_amt | DECIMAL(28,8) | 订单金额 | 宽表 |
| dt | STRING | 日分区 | 看板日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.7 `ads_proj_fin_board_summary_mn`（项目财务看板总览）

- 粒度：`stat_month`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| overall_budget_exec_rate | DECIMAL(28,8) | 整体预算执行率 | 汇总 `dws_proj_budget_exec_mn` |
| overall_expense_compliance_rate | DECIMAL(28,8) | 整体支出合规率 | 需补采财务合规明细（占位） |
| fund_per_researcher | DECIMAL(28,8) | 人均项目经费 | 需补采科研人员数（占位） |
| fund_intensity_per_outcome | DECIMAL(28,8) | 单位成果经费强度 | 需补采核心成果数量（占位） |
| budget_adjust_rate | DECIMAL(28,8) | 预算调整率 | 依赖预算明细（可由日快照聚合） |
| budget_balance_rate | DECIMAL(28,8) | 经费结余率 | 汇总 `balance/approved` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.8 `ads_proj_exec_board_summary_di`（项目执行看板总览）

- 粒度：`dt`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| total_project_cnt | BIGINT | 项目总数 | count(project) |
| in_progress_project_cnt | BIGINT | 在执行项目数 | sum(not finished) |
| finished_project_cnt | BIGINT | 完工项目数 | sum(finished) |
| delayed_project_cnt | BIGINT | 延期项目数 | sum(is_delayed) |
| project_ontime_finish_rate | DECIMAL(28,8) | 项目及时完成率 | 按口径计算 |
| milestone_ontime_rate | DECIMAL(28,8) | 里程碑按时达成率 | `metrics.xlsx` 有该口径，但当前ODS缺里程碑明细（占位） |
| dt | STRING | 日分区 | 看板日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.9 `ads_inv_board_health_bubble_mn`（库存健康度诊断：气泡图数据集）

- 粒度：`stat_month, material_class_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_class_id | STRING | 物料分类ID | 维度关联 |
| material_class_name | STRING | 物料分类名称 | 维度关联 |
| x_avg_stock_amt | DECIMAL(28,8) | X轴：平均库存金额 | `avg(每日分类库存金额)` |
| y_turnover_days | DECIMAL(28,8) | Y轴：周转天数 | `30 / (out_amt/avg_stock_amt)` |
| bubble_sku_cnt | BIGINT | 气泡大小：SKU数 | `avg(每日SKU数)` 或 月内 distinct |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.10 `ads_inv_board_trend_mn`（库存关联分析：趋势数据集）

- 粒度：`stat_month`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| inventory_amt_end | DECIMAL(28,8) | 月末库存金额 | 月末快照汇总 |
| avg_stock_amt | DECIMAL(28,8) | 月均库存金额 | `avg(每日库存金额)` |
| out_amt | DECIMAL(28,8) | 月出库金额 | `sum(dwd_inv_fact_stock_flow.out_amt)` |
| turnover_rate | DECIMAL(28,8) | 周转率 | `out_amt/avg_stock_amt` |
| turnover_days | DECIMAL(28,8) | 周转天数 | `30/turnover_rate` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.11 `ads_inv_shortage_material_di`（紧缺物料明细清单，可选）

- 粒度：`dt, material_id, warehouse_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_id | STRING | 物料ID | 快照/策略 |
| material_code | STRING | 物料编码 | 维度关联 |
| material_name | STRING | 物料名称 | 维度关联 |
| warehouse_id | STRING | 仓库ID | 快照/策略 |
| warehouse_name | STRING | 仓库名称 | 维度关联 |
| stock_qty | DECIMAL(28,8) | 当前库存数量 | `dws_inv_stock_snapshot_di.stock_qty` |
| safety_stock_qty | DECIMAL(28,8) | 安全库存阈值 | `dwd_inv_master_material_policy.safety_stock_qty`（需配置/补采） |
| gap_qty | DECIMAL(28,8) | 缺口数量 | `safety_stock_qty-stock_qty` |
| lead_time_days | INT | 采购提前期(天) | 策略表（需配置/补采） |
| is_critical | INT | 是否关键物料 | 策略表（需配置/补采） |
| dt | STRING | 日分区 | 快照日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.12 `ads_pur_board_trend_mn`（月度采购趋势：柱状图数据集）

- 粒度：`stat_month`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| purchase_amt | DECIMAL(28,8) | 月采购金额 | `dws_pur_purchase_trend_mn.purchase_amt`（口径需统一：订单额/入库额） |
| order_line_cnt | BIGINT | 订单行数 | `dws_pur_purchase_trend_mn.order_line_cnt` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.13 `ads_pur_cycle_by_class_mn`（物料类别平均采购周期：数字指标数据集）

- 粒度：`stat_month, material_class_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| material_class_id | STRING | 物料分类ID | 维度关联 |
| material_class_name | STRING | 物料分类名称 | 维度关联 |
| order_line_cnt | BIGINT | 订单行数 | count |
| avg_cycle_days | DECIMAL(28,8) | 平均采购周期(天) | 来自 `dws_pur_cycle_by_class_mn.avg_cycle_days` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.14 `ads_pur_delivery_gantt_di`（采购到货周期监控：甘特图数据集）

- 粒度：`dt, order_line_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 宽表 |
| project_name | STRING | 项目名称 | 维度关联 |
| supplier_id | STRING | 供应商ID | 宽表 |
| supplier_name | STRING | 供应商名称 | 维度关联 |
| material_id | STRING | 物料ID | 宽表 |
| material_name | STRING | 物料名称 | 维度关联 |
| apply_date | DATE | 采购申请/请购日期 | 宽表 |
| order_date | DATE | 订单发出日期 | 宽表 |
| plan_arrive_date | DATE | 计划到货日期 | 宽表 |
| arrive_date | DATE | 实际到货日期 | 宽表 |
| stock_in_date | DATE | 实际入库日期 | 宽表 |
| delay_days | INT | 延误天数 | 宽表派生 |
| dt | STRING | 日分区 | 看板日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.15 `ads_pur_supplier_risk_bubble_mn`（供应商风险分析：气泡图数据集）

> `metrics.xlsx` 需要“风险评分（交付/质量/独家性等综合）”，当前ODS未覆盖风险评分明细，建议补采后计算 `risk_score`。

- 粒度：`stat_month, supplier_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| supplier_id | STRING | 供应商ID | 采购汇总 |
| supplier_name | STRING | 供应商名称 | 维度关联 |
| purchase_amt | DECIMAL(28,8) | 采购金额 | 汇总订单/入库金额 |
| purchase_amt_share | DECIMAL(28,8) | 金额占比 | `purchase_amt/sum(purchase_amt)` |
| risk_score | DECIMAL(28,8) | 风险评分 | 需补采/计算（占位） |
| bubble_sku_cnt | BIGINT | 气泡大小：SKU数 | `count(distinct material_id)` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.16 `ads_proj_fin_budget_exec_trend_mn`（预算执行率分析：趋势数据集）

- 粒度：`stat_month`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| budget_exec_rate | DECIMAL(28,8) | 月度预算执行率 | `sum(exec_amt)/sum(approved_budget_amt)` |
| approved_budget_amt | DECIMAL(28,8) | 批复预算(汇总) | 汇总 |
| exec_amt | DECIMAL(28,8) | 执行金额(汇总) | 汇总 |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.17 `ads_proj_fin_expense_compliance_trend_mn`（支出合规率：趋势数据集，占位）

> 需补采支出/凭证明细与合规判定结果（例如 `dwd_fin_fact_expense_line.is_compliant`）。

- 粒度：`stat_month`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| expense_compliance_rate | DECIMAL(28,8) | 支出合规率 | `合规笔数/总笔数`（占位） |
| compliant_cnt | BIGINT | 合规笔数 | 占位 |
| total_cnt | BIGINT | 总支出笔数 | 占位 |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.18 `ads_proj_fin_cost_structure_mn`（项目成本结构占比：饼图数据集，占位）

> 需补采成本科目明细（设备费/劳务费/材料费等）。

- 粒度：`stat_month, cost_subject`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| cost_subject | STRING | 成本科目 | 需补采 |
| cost_amt | DECIMAL(28,8) | 成本金额 | 需补采 |
| cost_share | DECIMAL(28,8) | 占比 | `cost_amt/sum(cost_amt)` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.19 `ads_proj_fin_budget_structure_mn`（项目预算结构占比：饼图数据集，占位）

> 需补采预算科目明细（预算科目维度 + 预算分解金额）。

- 粒度：`stat_month, budget_subject`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| budget_subject | STRING | 预算科目 | 需补采 |
| budget_amt | DECIMAL(28,8) | 预算金额 | 需补采 |
| budget_share | DECIMAL(28,8) | 占比 | `budget_amt/sum(budget_amt)` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.20 `ads_proj_fin_budget_dept_share_mn`（项目预算部门占比：饼图数据集）

- 粒度：`stat_month, dept_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| dept_id | STRING | 部门ID | 项目责任部门（或财务部门） |
| dept_name | STRING | 部门名称 | 维度关联（需补采部门维度则占位） |
| budget_amt | DECIMAL(28,8) | 预算金额 | 汇总项目预算 |
| budget_share | DECIMAL(28,8) | 占比 | `budget_amt/sum(budget_amt)` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.21 `ads_proj_fin_fund_per_researcher_cmp_mn`（人均项目经费对比：分组条形图，占位）

> 需补采科研人员数（HR headcount）。

- 粒度：`stat_month, group_dim_type, group_dim_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| group_dim_type | STRING | 分组维度类型 | `dept/project_type`（示例） |
| group_dim_id | STRING | 分组维度ID | 部门/项目类型等 |
| group_dim_name | STRING | 分组维度名称 | 维度关联 |
| fund_amt | DECIMAL(28,8) | 经费金额 | 汇总预算 |
| headcount | BIGINT | 人员数 | 需补采 |
| fund_per_researcher | DECIMAL(28,8) | 人均经费 | `fund_amt/headcount` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.22 `ads_proj_fin_budget_exec_cmp_mn`（项目预算执行对比：分组柱状图数据集）

- 粒度：`stat_month, group_dim_type, group_dim_id`
- 分区：`stat_month`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| group_dim_type | STRING | 分组维度类型 | `dept/project_type`（示例） |
| group_dim_id | STRING | 分组维度ID | 部门/项目类型等 |
| group_dim_name | STRING | 分组维度名称 | 维度关联 |
| approved_budget_amt | DECIMAL(28,8) | 批复预算 | 汇总 |
| exec_amt | DECIMAL(28,8) | 执行金额 | 汇总 |
| exec_rate | DECIMAL(28,8) | 执行率 | `exec_amt/approved_budget_amt` |
| stat_month | STRING | 月分区 | `yyyy-MM` |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |

##### 7.5.23 `ads_proj_exec_key_projects_di`（重点项目进展：列表/甘特图数据集）

- 粒度：`dt, project_id`
- 分区：`dt`

| 字段 | 类型 | 含义 | 来源/计算 |
|---|---|---|---|
| project_id | STRING | 项目ID | 项目维度 |
| project_code | STRING | 项目编码 | 项目维度 |
| project_name | STRING | 项目名称 | 项目维度 |
| duty_dept_id | STRING | 责任部门ID | 项目维度 |
| plan_start_date | DATE | 计划开始 | 项目维度 |
| plan_finish_date | DATE | 计划完成 | 项目维度 |
| actu_start_date | DATE | 实际开始 | 项目维度 |
| actu_finish_date | DATE | 实际完成 | 项目维度 |
| is_finished | INT | 是否完工 | 派生 |
| is_delayed | INT | 是否延期 | 派生 |
| delay_days | INT | 延期天数 | 派生 |
| dt | STRING | 日分区 | 看板日 |
| etl_time | TIMESTAMP | ETL时间 | ETL生成 |
