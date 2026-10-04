-- ============================================================================
-- 数据管理平台一期：DWD/DWS 建表与加工SQL（模板）
-- 说明：
-- 1) 本文件为 Hive/SparkSQL 风格示例，默认分层库：ods / dwd / dws
-- 2) ODS 字段名以 `dg.docx` 为准；若与此处示例不一致，请按实际ODS字段替换
-- 3) 分区约定：日分区 dt='yyyy-MM-dd'，月分区 stat_month='yyyy-MM'
-- 4) 变量约定：
--    - ${dt}：运行日（yyyy-MM-dd）
--    - ${dt_prev}：前一日（yyyy-MM-dd）
--    - ${stat_month}：运行月（yyyy-MM）
-- ============================================================================

-- 建议先选择执行引擎的方言设置（如SparkSQL支持）：
-- SET hive.exec.dynamic.partition=true;
-- SET hive.exec.dynamic.partition.mode=nonstrict;

-- ============================================================================
-- 1. DWD 层：DDL
-- ============================================================================

-- 1.1 维度表（DIM）

CREATE TABLE IF NOT EXISTS dwd.dwd_inv_master_material (
  material_id               STRING  COMMENT '物料ID(pk_material)',
  material_code             STRING  COMMENT '物料编码(code)',
  material_name             STRING  COMMENT '物料名称(以ODS为准)',
  material_spec             STRING  COMMENT '规格(materialspec)',
  material_type             STRING  COMMENT '型号(materialtype)',
  material_class_id         STRING  COMMENT '物料分类ID(pk_marbasclass)',
  brand_id                  STRING  COMMENT '品牌(pk_brand)',
  org_id                    STRING  COMMENT '所属组织(pk_org)',
  group_id                  STRING  COMMENT '所属集团(pk_group)',
  is_eprocurement           INT     COMMENT '是否电子采购(iselectrans -> 0/1)',
  created_time              STRING  COMMENT '创建时间(标准化前)',
  updated_time              STRING  COMMENT '最后修改时间(标准化前)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '物料主数据维度'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_inv_master_material_class (
  material_class_id         STRING  COMMENT '分类ID(pk_marbasclass)',
  material_class_code       STRING  COMMENT '分类编码(code)',
  material_class_name       STRING  COMMENT '分类名称(以ODS为准)',
  parent_class_id           STRING  COMMENT '上级分类ID(pk_parent)',
  org_id                    STRING  COMMENT '所属组织(pk_org)',
  group_id                  STRING  COMMENT '所属集团(pk_group)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '物料分类维度'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_pur_master_supplier (
  supplier_id               STRING  COMMENT '供应商ID(pk_supplier)',
  supplier_code             STRING  COMMENT '供应商编码(code)',
  supplier_name             STRING  COMMENT '供应商名称(以ODS为准)',
  supplier_class_id         STRING  COMMENT '供应商分类ID(pk_supplierclass)',
  finance_org_id            STRING  COMMENT '财务组织(pk_financeorg)',
  country_id                STRING  COMMENT '国家/地区(pk_country)',
  org_id                    STRING  COMMENT '所属组织(pk_org)',
  group_id                  STRING  COMMENT '所属集团(pk_group)',
  created_time              STRING  COMMENT '创建时间(标准化前)',
  updated_time              STRING  COMMENT '最后修改时间(标准化前)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '供应商主数据维度'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_inv_master_warehouse (
  warehouse_id              STRING  COMMENT '仓库ID(pk_stordoc)',
  warehouse_code            STRING  COMMENT '仓库编码(code)',
  warehouse_name            STRING  COMMENT '仓库名称(以ODS为准)',
  org_id                    STRING  COMMENT '所属库存组织(pk_org)',
  group_id                  STRING  COMMENT '所属集团(pk_group)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '仓库主数据维度'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_proj_master_project (
  project_id                STRING  COMMENT '项目ID(pk_project)',
  project_code              STRING  COMMENT '项目编码(以ODS为准)',
  project_name              STRING  COMMENT '项目名称(以ODS为准)',
  parent_project_id         STRING  COMMENT '父项目ID(pk_parentpro)',
  project_state_id          STRING  COMMENT '项目状态ID(pk_projectstate)',
  duty_dept_id              STRING  COMMENT '责任部门ID(pk_duty_dept)',
  duty_org_id               STRING  COMMENT '责任组织ID(pk_duty_org)',
  plan_start_date           DATE    COMMENT '计划开始日期',
  plan_finish_date          DATE    COMMENT '计划完成日期',
  actu_start_date           DATE    COMMENT '实际开始日期',
  actu_finish_date          DATE    COMMENT '实际完成日期',
  plan_duration_days        INT     COMMENT '计划工期(天)',
  actu_duration_days        INT     COMMENT '实际工期(天)',
  plan_priority             INT     COMMENT '计划优先级',
  status_date               DATE    COMMENT '状态日期',
  org_id                    STRING  COMMENT '项目组织(pk_org)',
  group_id                  STRING  COMMENT '所属集团(pk_group)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '项目主数据维度'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- 配置/补采：物料策略（安全库存/关键/提前期）
CREATE TABLE IF NOT EXISTS dwd.dwd_inv_master_material_policy (
  material_id               STRING  COMMENT '物料ID',
  warehouse_id              STRING  COMMENT '仓库ID',
  safety_stock_qty          DECIMAL(28,8) COMMENT '安全库存数量',
  lead_time_days            INT     COMMENT '采购提前期(天)',
  is_critical               INT     COMMENT '是否关键物料(0/1)',
  effective_date            DATE    COMMENT '生效日期',
  expire_date               DATE    COMMENT '失效日期',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统(config)',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '物料策略配置维表(补采/配置)'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- 1.2 事实表（FACT）

CREATE TABLE IF NOT EXISTS dwd.dwd_inv_fact_stock_opening_balance (
  opening_bill_id           STRING  COMMENT '期初余额单据ID(以ODS为准)',
  opening_line_id           STRING  COMMENT '期初余额行ID(以ODS为准)',
  biz_date                  DATE    COMMENT '业务日期',
  material_id               STRING  COMMENT '物料ID',
  warehouse_id              STRING  COMMENT '仓库ID',
  project_id                STRING  COMMENT '项目ID',
  supplier_id               STRING  COMMENT '供应商ID',
  batch_id                  STRING  COMMENT '批次ID',
  stock_state_id            STRING  COMMENT '库存状态',
  produced_date             DATE    COMMENT '生产日期',
  expiry_date               DATE    COMMENT '到期/失效日期',
  opening_qty               DECIMAL(28,8) COMMENT '期初数量(主数量)',
  opening_amt               DECIMAL(28,8) COMMENT '期初金额',
  opening_unit_price        DECIMAL(28,8) COMMENT '期初单价',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '库存期初余额明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_inv_fact_stock_flow (
  flow_id                   STRING  COMMENT '流水主键(pk_flow)',
  biz_date                  DATE    COMMENT '业务日期(dbizdate)',
  bill_code                 STRING  COMMENT '单据号(vbillcode)',
  bill_type_code            STRING  COMMENT '单据类型编码(cbilltypecode)',
  tran_type_code            STRING  COMMENT '出入库类型编码(vtrantypecode)',
  material_id               STRING  COMMENT '物料ID(cmaterialoid)',
  warehouse_id              STRING  COMMENT '仓库ID(cwarehouseid)',
  project_id                STRING  COMMENT '项目ID(cprojectid)',
  supplier_id               STRING  COMMENT '供应商ID(cvendorid)',
  batch_id                  STRING  COMMENT '批次ID(pk_batchcode)',
  stock_state_id            STRING  COMMENT '库存状态(cstateid)',
  produced_date             DATE    COMMENT '生产日期(dproducedate)',
  expiry_date               DATE    COMMENT '到期/失效日期(dvalidate)',
  in_qty                    DECIMAL(28,8) COMMENT '入库数量(ninnum)',
  out_qty                   DECIMAL(28,8) COMMENT '出库数量(noutnum)',
  net_qty                   DECIMAL(28,8) COMMENT '净数量(in-out)',
  in_amt                    DECIMAL(28,8) COMMENT '入库金额(由ncostmny派生)',
  out_amt                   DECIMAL(28,8) COMMENT '出库金额(由ncostmny派生)',
  net_amt                   DECIMAL(28,8) COMMENT '净金额(in-out)',
  flow_amt_raw              DECIMAL(28,8) COMMENT '源金额(ncostmny)',
  cost_price                DECIMAL(28,8) COMMENT '单价(ncostprice)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '库存流水明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- 统一入库明细（跨来源统一，指标/对账用）
CREATE TABLE IF NOT EXISTS dwd.dwd_inv_fact_inbound_line (
  inbound_type              STRING  COMMENT '入库类型(purchase_in/general_in/purchase_infi/flow)',
  inbound_bill_id           STRING  COMMENT '入库单ID',
  inbound_line_id           STRING  COMMENT '入库单行ID',
  biz_date                  DATE    COMMENT '入库/业务日期',
  material_id               STRING  COMMENT '物料ID',
  warehouse_id              STRING  COMMENT '仓库ID',
  project_id                STRING  COMMENT '项目ID',
  supplier_id               STRING  COMMENT '供应商ID',
  batch_id                  STRING  COMMENT '批次ID',
  in_qty                    DECIMAL(28,8) COMMENT '入库数量',
  in_amt_excl_tax           DECIMAL(28,8) COMMENT '入库无税金额',
  in_amt_incl_tax           DECIMAL(28,8) COMMENT '入库含税金额',
  in_unit_price_excl_tax    DECIMAL(28,8) COMMENT '无税单价',
  in_unit_price_incl_tax    DECIMAL(28,8) COMMENT '含税单价',
  source_bill_code          STRING  COMMENT '源单据号',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '统一入库明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- 统一出库明细（跨来源统一，指标/对账用）
CREATE TABLE IF NOT EXISTS dwd.dwd_inv_fact_outbound_line (
  outbound_type             STRING  COMMENT '出库类型(general_out/material_out/flow)',
  outbound_bill_id          STRING  COMMENT '出库单ID',
  outbound_line_id          STRING  COMMENT '出库单行ID',
  biz_date                  DATE    COMMENT '出库/业务日期',
  material_id               STRING  COMMENT '物料ID',
  warehouse_id              STRING  COMMENT '仓库ID',
  project_id                STRING  COMMENT '项目ID',
  batch_id                  STRING  COMMENT '批次ID',
  out_qty                   DECIMAL(28,8) COMMENT '出库数量',
  out_amt                   DECIMAL(28,8) COMMENT '出库金额(成本口径)',
  out_unit_price            DECIMAL(28,8) COMMENT '出库单价(成本)',
  source_bill_code          STRING  COMMENT '源单据号',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '统一出库明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_pur_fact_pray_line (
  pray_bill_id              STRING  COMMENT '请购单ID(pk_praybill)',
  pray_line_id              STRING  COMMENT '请购单行ID(pk_praybill_b)',
  project_id                STRING  COMMENT '项目ID(cprojectid)',
  material_id               STRING  COMMENT '物料ID(pk_material)',
  suggest_supplier_id       STRING  COMMENT '建议供应商(pk_suggestsupplier)',
  req_dept_id               STRING  COMMENT '需求部门(pk_reqdept)',
  req_warehouse_id          STRING  COMMENT '需求仓库(pk_reqstor)',
  apply_date                DATE    COMMENT '请购日期(dbilldate)',
  require_date              DATE    COMMENT '需求日期(dreqdate)',
  suggest_order_date        DATE    COMMENT '建议订货日期(dsuggestdate)',
  req_qty                   DECIMAL(28,8) COMMENT '请购数量(nnum/nastnum)',
  req_amt_incl_tax          DECIMAL(28,8) COMMENT '请购含税金额(ntaxmny)',
  req_unit_price_incl_tax   DECIMAL(28,8) COMMENT '请购含税单价(ntaxprice)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '请购单明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_pur_fact_order_line (
  order_id                  STRING  COMMENT '采购订单ID(pk_order)',
  order_line_id             STRING  COMMENT '采购订单行ID(pk_order_b)',
  pray_bill_id              STRING  COMMENT '来源请购单ID(cpraybillhid)',
  pray_line_id              STRING  COMMENT '来源请购单行ID(cpraybillbid)',
  project_id                STRING  COMMENT '项目ID',
  supplier_id               STRING  COMMENT '供应商ID(pk_supplier)',
  material_id               STRING  COMMENT '物料ID(pk_material)',
  order_date                DATE    COMMENT '订单日期(dbilldate)',
  plan_arrive_date          DATE    COMMENT '计划到货日期(dplanarrvdate)',
  order_qty                 DECIMAL(28,8) COMMENT '订单数量(nnum/nastnum)',
  order_amt_excl_tax        DECIMAL(28,8) COMMENT '订单无税金额(nmny)',
  order_unit_price_excl_tax DECIMAL(28,8) COMMENT '无税单价(nprice)',
  order_amt_incl_tax        DECIMAL(28,8) COMMENT '订单含税金额(norigtaxmny)',
  order_status              INT     COMMENT '单据状态(forderstatus)',
  is_final_close            INT     COMMENT '最终关闭(bfinalclose -> 0/1)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '采购订单明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_pur_fact_arrival_line (
  arrive_id                 STRING  COMMENT '到货单ID(pk_arriveorder)',
  arrive_line_id            STRING  COMMENT '到货单行ID(pk_arriveorder_b)',
  order_id                  STRING  COMMENT '采购订单ID(pk_order)',
  order_line_id             STRING  COMMENT '采购订单行ID(pk_order_b)',
  supplier_id               STRING  COMMENT '供应商ID(pk_supplier)',
  material_id               STRING  COMMENT '物料ID(pk_material)',
  arrive_date               DATE    COMMENT '实际到货日期(po_arriveorder.dbilldate)',
  arrive_qty                DECIMAL(28,8) COMMENT '到货数量(以ODS为准)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '到货单明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_pur_fact_purchase_in_line (
  stock_in_id               STRING  COMMENT '采购入库单ID(pk_stockps)',
  stock_in_line_id          STRING  COMMENT '采购入库行ID(pk_stockps_b)',
  order_id                  STRING  COMMENT '采购订单ID(pk_order)',
  order_line_id             STRING  COMMENT '采购订单行ID(pk_order_b)',
  project_id                STRING  COMMENT '项目ID(cprojectid)',
  supplier_id               STRING  COMMENT '供应商ID(pk_supplier)',
  material_id               STRING  COMMENT '物料ID(pk_material)',
  in_date                   DATE    COMMENT '入库日期(po_purchaseinfi.dbilldate)',
  biz_date                  DATE    COMMENT '业务日期(po_purchaseinfi_b.dbizdate)',
  require_date              DATE    COMMENT '需求日期(drequiredate)',
  in_qty                    DECIMAL(28,8) COMMENT '入库数量(ninnum)',
  in_amt_excl_tax           DECIMAL(28,8) COMMENT '无税金额(nmny/nestmny)',
  in_amt_incl_tax           DECIMAL(28,8) COMMENT '含税金额(norigtaxmny)',
  cost_amt                  DECIMAL(28,8) COMMENT '成本金额(ncostmny)',
  stock_state_id            STRING  COMMENT '库存状态(cstateid)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '采购入库明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dwd.dwd_pur_fact_contract_line (
  contract_id               STRING  COMMENT '合同ID(pk_ct_pu)',
  contract_line_id          STRING  COMMENT '合同明细ID(pk_ct_pu_b)',
  related_pray_line_id      STRING  COMMENT '关联请购行ID(pk_praybill_b)',
  contract_effective_date   DATE    COMMENT '实际生效日期(actualvalidate)',
  contract_invalid_date     DATE    COMMENT '实际终止日期(actualinvalidate)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '采购合同明细事实'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- 预算执行（视图补齐后落地）
CREATE TABLE IF NOT EXISTS dwd.dwd_proj_fact_budget_execution (
  project_id                STRING  COMMENT '项目ID(PK_Project)',
  project_code              STRING  COMMENT '项目编码',
  project_name              STRING  COMMENT '项目名称',
  parent_project_name       STRING  COMMENT '父项目',
  duty_dept_name            STRING  COMMENT '责任部门',
  budget_amt                DECIMAL(28,8) COMMENT '预算金额',
  budget_adjust_amt         DECIMAL(28,8) COMMENT '调整金额',
  reserved_amt              DECIMAL(28,8) COMMENT '预占金额',
  exec_amt                  DECIMAL(28,8) COMMENT '执行金额',
  exec_hist_amt             DECIMAL(28,8) COMMENT '历史执行金额',
  approved_budget_amt       DECIMAL(28,8) COMMENT '批复预算(含调整)',
  balance_amt               DECIMAL(28,8) COMMENT '预算结余',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统(budget)',
  source_table              STRING  COMMENT '源视图',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '项目预算执行快照事实(补齐视图元数据后落地)'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- 采购全流程宽表：以订单行(order_line_id)为主键
CREATE TABLE IF NOT EXISTS dwd.dwd_pur_wide_order_lifecycle_line (
  order_id                  STRING  COMMENT '采购订单ID',
  order_line_id             STRING  COMMENT '采购订单行ID',
  pray_bill_id              STRING  COMMENT '请购单ID',
  pray_line_id              STRING  COMMENT '请购单行ID',
  project_id                STRING  COMMENT '项目ID',
  supplier_id               STRING  COMMENT '供应商ID',
  material_id               STRING  COMMENT '物料ID',
  apply_date                DATE    COMMENT '请购日期',
  require_date              DATE    COMMENT '需求日期',
  order_date                DATE    COMMENT '订单日期',
  plan_arrive_date          DATE    COMMENT '计划到货日期',
  arrive_date               DATE    COMMENT '实际到货日期(取最早)',
  stock_in_date             DATE    COMMENT '实际入库日期(取最早)',
  order_qty                 DECIMAL(28,8) COMMENT '订单数量',
  order_amt                 DECIMAL(28,8) COMMENT '订单金额(口径可选)',
  arrive_qty                DECIMAL(28,8) COMMENT '累计到货数量',
  stock_in_qty              DECIMAL(28,8) COMMENT '累计入库数量',
  delay_days                INT     COMMENT '延迟天数(实际-需求)',
  cycle_days                INT     COMMENT '全流程天数(实际-请购)',
  is_ontime                 INT     COMMENT '是否准时(0/1)',
  current_stage             STRING  COMMENT '当前环节(已请购/已下单/已到货/已入库)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间',
  source_system             STRING  COMMENT '源系统',
  source_table              STRING  COMMENT '源表集合',
  source_pk                 STRING  COMMENT '源主键',
  source_ts                 STRING  COMMENT '源时间戳'
)
COMMENT '采购全流程宽表(订单行生命周期)'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- ============================================================================
-- 2. DWD 层：ETL（模板）
-- ============================================================================

-- 2.1 维度：物料
INSERT OVERWRITE TABLE dwd.dwd_inv_master_material PARTITION (dt='${dt}')
SELECT
  pk_material                                           AS material_id,
  code                                                  AS material_code,
  name                                                  AS material_name,
  materialspec                                          AS material_spec,
  materialtype                                          AS material_type,
  pk_marbasclass                                        AS material_class_id,
  pk_brand                                              AS brand_id,
  pk_org                                                AS org_id,
  pk_group                                              AS group_id,
  CASE WHEN iselectrans IN ('Y','1','T','true','TRUE') THEN 1 ELSE 0 END AS is_eprocurement,
  creationtime                                          AS created_time,
  modifiedtime                                          AS updated_time,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'bd_material_v'                                       AS source_table,
  pk_material                                           AS source_pk,
  COALESCE(modifiedtime, creationtime)                  AS source_ts
FROM ods.bd_material_v;

-- 2.2 维度：物料分类
INSERT OVERWRITE TABLE dwd.dwd_inv_master_material_class PARTITION (dt='${dt}')
SELECT
  pk_marbasclass                                        AS material_class_id,
  code                                                  AS material_class_code,
  name                                                  AS material_class_name,
  pk_parent                                             AS parent_class_id,
  pk_org                                                AS org_id,
  pk_group                                              AS group_id,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'bd_marbasclass'                                      AS source_table,
  pk_marbasclass                                        AS source_pk,
  COALESCE(modifiedtime, creationtime)                  AS source_ts
FROM ods.bd_marbasclass;

-- 2.3 维度：供应商
INSERT OVERWRITE TABLE dwd.dwd_pur_master_supplier PARTITION (dt='${dt}')
SELECT
  pk_supplier                                           AS supplier_id,
  code                                                  AS supplier_code,
  name                                                  AS supplier_name,
  pk_supplierclass                                      AS supplier_class_id,
  pk_financeorg                                         AS finance_org_id,
  pk_country                                            AS country_id,
  pk_org                                                AS org_id,
  pk_group                                              AS group_id,
  creationtime                                          AS created_time,
  modifiedtime                                          AS updated_time,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'bd_supplier'                                         AS source_table,
  pk_supplier                                           AS source_pk,
  COALESCE(modifiedtime, creationtime)                  AS source_ts
FROM ods.bd_supplier;

-- 2.4 维度：仓库
INSERT OVERWRITE TABLE dwd.dwd_inv_master_warehouse PARTITION (dt='${dt}')
SELECT
  pk_stordoc                                            AS warehouse_id,
  code                                                  AS warehouse_code,
  name                                                  AS warehouse_name,
  pk_org                                                AS org_id,
  pk_group                                              AS group_id,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'bd_stordoc'                                          AS source_table,
  pk_stordoc                                            AS source_pk,
  COALESCE(modifiedtime, creationtime)                  AS source_ts
FROM ods.bd_stordoc;

-- 2.5 维度：项目
INSERT OVERWRITE TABLE dwd.dwd_proj_master_project PARTITION (dt='${dt}')
SELECT
  pk_project                                            AS project_id,
  code                                                  AS project_code,
  name                                                  AS project_name,
  pk_parentpro                                          AS parent_project_id,
  pk_projectstate                                       AS project_state_id,
  pk_duty_dept                                          AS duty_dept_id,
  pk_duty_org                                           AS duty_org_id,
  TO_DATE(SUBSTR(plan_start_date, 1, 10))               AS plan_start_date,
  TO_DATE(SUBSTR(plan_finish_date, 1, 10))              AS plan_finish_date,
  TO_DATE(SUBSTR(actu_start_date, 1, 10))               AS actu_start_date,
  TO_DATE(SUBSTR(actu_finish_date, 1, 10))              AS actu_finish_date,
  planduration                                          AS plan_duration_days,
  actuduration                                          AS actu_duration_days,
  planpriority                                          AS plan_priority,
  TO_DATE(SUBSTR(status_date, 1, 10))                   AS status_date,
  pk_org                                                AS org_id,
  pk_group                                              AS group_id,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'bd_project'                                          AS source_table,
  pk_project                                            AS source_pk,
  COALESCE(modifiedtime, creationtime)                  AS source_ts
FROM ods.bd_project;

-- 2.6 事实：库存流水（按业务日抽取）
INSERT OVERWRITE TABLE dwd.dwd_inv_fact_stock_flow PARTITION (dt='${dt}')
SELECT
  pk_flow                                               AS flow_id,
  TO_DATE(SUBSTR(dbizdate, 1, 10))                      AS biz_date,
  vbillcode                                             AS bill_code,
  cbilltypecode                                         AS bill_type_code,
  vtrantypecode                                         AS tran_type_code,
  cmaterialoid                                          AS material_id,
  cwarehouseid                                          AS warehouse_id,
  cprojectid                                            AS project_id,
  cvendorid                                             AS supplier_id,
  pk_batchcode                                          AS batch_id,
  cstateid                                              AS stock_state_id,
  TO_DATE(SUBSTR(dproducedate, 1, 10))                  AS produced_date,
  TO_DATE(SUBSTR(dvalidate, 1, 10))                     AS expiry_date,
  CAST(COALESCE(ninnum, 0) AS DECIMAL(28,8))            AS in_qty,
  CAST(COALESCE(noutnum, 0) AS DECIMAL(28,8))           AS out_qty,
  CAST(COALESCE(ninnum, 0) - COALESCE(noutnum, 0) AS DECIMAL(28,8)) AS net_qty,
  CAST(CASE WHEN COALESCE(ninnum, 0) > 0 THEN ncostmny ELSE 0 END AS DECIMAL(28,8)) AS in_amt,
  CAST(CASE WHEN COALESCE(noutnum, 0) > 0 THEN ncostmny ELSE 0 END AS DECIMAL(28,8)) AS out_amt,
  CAST(
    (CASE WHEN COALESCE(ninnum, 0) > 0 THEN ncostmny ELSE 0 END)
    - (CASE WHEN COALESCE(noutnum, 0) > 0 THEN ncostmny ELSE 0 END)
    AS DECIMAL(28,8)
  )                                                     AS net_amt,
  CAST(ncostmny AS DECIMAL(28,8))                       AS flow_amt_raw,
  CAST(ncostprice AS DECIMAL(28,8))                     AS cost_price,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'ic_flow'                                             AS source_table,
  pk_flow                                               AS source_pk,
  COALESCE(sourcets, sourcebts, dbizdate)               AS source_ts
FROM ods.ic_flow
WHERE SUBSTR(dbizdate, 1, 10) = '${dt}';

-- 2.6.1 事实：期初余额（通常为一次性/按源系统快照抽取；此处按${dt}落分区示例）
-- 注意：opening_bill_id/opening_line_id 等字段名以 `dg.docx` 为准，请按实际ODS替换
INSERT OVERWRITE TABLE dwd.dwd_inv_fact_stock_opening_balance PARTITION (dt='${dt}')
SELECT
  h.cgeneralhid                                         AS opening_bill_id,
  b.cgeneralbid                                         AS opening_line_id,
  TO_DATE(SUBSTR(b.dbizdate, 1, 10))                    AS biz_date,
  b.cmaterialoid                                        AS material_id,
  b.cbodywarehouseid                                    AS warehouse_id,
  b.cprojectid                                          AS project_id,
  b.cvendorid                                           AS supplier_id,
  b.pk_batchcode                                        AS batch_id,
  b.cstateid                                            AS stock_state_id,
  TO_DATE(SUBSTR(b.dproducedate, 1, 10))                AS produced_date,
  TO_DATE(SUBSTR(b.dvalidate, 1, 10))                   AS expiry_date,
  CAST(COALESCE(b.nnum, 0) AS DECIMAL(28,8))            AS opening_qty,
  CAST(b.ncostmny AS DECIMAL(28,8))                     AS opening_amt,
  CAST(b.ncostprice AS DECIMAL(28,8))                   AS opening_unit_price,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'ic_openbal_h/ic_openbal_b'                           AS source_table,
  b.cgeneralbid                                         AS source_pk,
  COALESCE(b.sourcets, b.sourcebts, b.dbizdate)         AS source_ts
FROM ods.ic_openbal_h h
JOIN ods.ic_openbal_b b
  ON h.cgeneralhid = b.cgeneralhid;

-- 2.6.2 事实：统一入库明细（模板）
-- 说明：此处示例聚合三类来源：
--   A) 库存侧采购入库/其他入库：建议从 ods.ic_purchasein_b / ods.ic_generalin_b 映射（字段名按dg.docx调整）
--   B) 采购侧入库：直接复用 dwd_pur_fact_purchase_in_line
--   C) 若缺少单据明细，也可从库存流水(dwd_inv_fact_stock_flow)派生作为兜底
INSERT OVERWRITE TABLE dwd.dwd_inv_fact_inbound_line PARTITION (dt='${dt}')
SELECT
  'purchase_infi'                                       AS inbound_type,
  stock_in_id                                           AS inbound_bill_id,
  stock_in_line_id                                      AS inbound_line_id,
  COALESCE(biz_date, in_date)                           AS biz_date,
  material_id,
  NULL                                                  AS warehouse_id,
  project_id,
  supplier_id,
  NULL                                                  AS batch_id,
  in_qty                                                AS in_qty,
  in_amt_excl_tax                                       AS in_amt_excl_tax,
  in_amt_incl_tax                                       AS in_amt_incl_tax,
  NULL                                                  AS in_unit_price_excl_tax,
  NULL                                                  AS in_unit_price_incl_tax,
  NULL                                                  AS source_bill_code,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'dwd_pur_fact_purchase_in_line'                       AS source_table,
  stock_in_line_id                                      AS source_pk,
  CAST(current_timestamp() AS STRING)                   AS source_ts
FROM dwd.dwd_pur_fact_purchase_in_line
WHERE dt='${dt}'
UNION ALL
SELECT
  'flow'                                                AS inbound_type,
  NULL                                                  AS inbound_bill_id,
  flow_id                                               AS inbound_line_id,
  biz_date                                              AS biz_date,
  material_id,
  warehouse_id,
  project_id,
  supplier_id,
  batch_id,
  in_qty                                                AS in_qty,
  CAST(NULL AS DECIMAL(28,8))                           AS in_amt_excl_tax,
  CAST(NULL AS DECIMAL(28,8))                           AS in_amt_incl_tax,
  CAST(NULL AS DECIMAL(28,8))                           AS in_unit_price_excl_tax,
  CAST(NULL AS DECIMAL(28,8))                           AS in_unit_price_incl_tax,
  bill_code                                             AS source_bill_code,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'dwd_inv_fact_stock_flow'                             AS source_table,
  flow_id                                               AS source_pk,
  CAST(current_timestamp() AS STRING)                   AS source_ts
FROM dwd.dwd_inv_fact_stock_flow
WHERE dt='${dt}' AND in_qty > 0;

-- 2.6.3 事实：统一出库明细（模板，优先用库存流水派生；若要单据行请映射 ic_generalout_b/ic_material_b）
INSERT OVERWRITE TABLE dwd.dwd_inv_fact_outbound_line PARTITION (dt='${dt}')
SELECT
  'flow'                                                AS outbound_type,
  NULL                                                  AS outbound_bill_id,
  flow_id                                               AS outbound_line_id,
  biz_date                                              AS biz_date,
  material_id,
  warehouse_id,
  project_id,
  batch_id,
  out_qty                                               AS out_qty,
  out_amt                                               AS out_amt,
  cost_price                                            AS out_unit_price,
  bill_code                                             AS source_bill_code,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'dwd_inv_fact_stock_flow'                             AS source_table,
  flow_id                                               AS source_pk,
  CAST(current_timestamp() AS STRING)                   AS source_ts
FROM dwd.dwd_inv_fact_stock_flow
WHERE dt='${dt}' AND out_qty > 0;

-- 2.7 事实：请购单行（按请购日抽取）
INSERT OVERWRITE TABLE dwd.dwd_pur_fact_pray_line PARTITION (dt='${dt}')
SELECT
  h.pk_praybill                                         AS pray_bill_id,
  b.pk_praybill_b                                       AS pray_line_id,
  b.cprojectid                                          AS project_id,
  b.pk_material                                         AS material_id,
  b.pk_suggestsupplier                                  AS suggest_supplier_id,
  b.pk_reqdept                                          AS req_dept_id,
  b.pk_reqstor                                          AS req_warehouse_id,
  TO_DATE(SUBSTR(b.dbilldate, 1, 10))                   AS apply_date,
  TO_DATE(SUBSTR(b.dreqdate, 1, 10))                    AS require_date,
  TO_DATE(SUBSTR(b.dsuggestdate, 1, 10))                AS suggest_order_date,
  CAST(COALESCE(b.nnum, b.nastnum, 0) AS DECIMAL(28,8))  AS req_qty,
  CAST(b.ntaxmny AS DECIMAL(28,8))                      AS req_amt_incl_tax,
  CAST(b.ntaxprice AS DECIMAL(28,8))                    AS req_unit_price_incl_tax,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'po_praybill/po_praybill_b'                           AS source_table,
  b.pk_praybill_b                                       AS source_pk,
  COALESCE(b.sourcebts, b.sourcets, b.dbilldate)        AS source_ts
FROM ods.po_praybill h
JOIN ods.po_praybill_b b
  ON h.pk_praybill = b.pk_praybill
WHERE SUBSTR(b.dbilldate, 1, 10) = '${dt}';

-- 2.8 事实：采购订单行（按订单日抽取）
INSERT OVERWRITE TABLE dwd.dwd_pur_fact_order_line PARTITION (dt='${dt}')
SELECT
  h.pk_order                                            AS order_id,
  b.pk_order_b                                          AS order_line_id,
  b.cpraybillhid                                        AS pray_bill_id,
  b.cpraybillbid                                        AS pray_line_id,
  COALESCE(b.cprojectid, h.pk_project)                  AS project_id,
  COALESCE(b.pk_supplier, h.pk_supplier)                AS supplier_id,
  b.pk_material                                         AS material_id,
  TO_DATE(SUBSTR(b.dbilldate, 1, 10))                   AS order_date,
  TO_DATE(SUBSTR(b.dplanarrvdate, 1, 10))               AS plan_arrive_date,
  CAST(COALESCE(b.nnum, b.nastnum, 0) AS DECIMAL(28,8))  AS order_qty,
  CAST(b.nmny AS DECIMAL(28,8))                         AS order_amt_excl_tax,
  CAST(b.nprice AS DECIMAL(28,8))                       AS order_unit_price_excl_tax,
  CAST(b.norigtaxmny AS DECIMAL(28,8))                  AS order_amt_incl_tax,
  h.forderstatus                                        AS order_status,
  CASE WHEN h.bfinalclose IN ('Y','1','T','true','TRUE') THEN 1 ELSE 0 END AS is_final_close,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'po_order/po_order_b'                                 AS source_table,
  b.pk_order_b                                          AS source_pk,
  COALESCE(b.sourcebts, b.sourcets, b.dbilldate)        AS source_ts
FROM ods.po_order h
JOIN ods.po_order_b b
  ON h.pk_order = b.pk_order
WHERE SUBSTR(b.dbilldate, 1, 10) = '${dt}';

-- 2.9 事实：到货单行（按到货日抽取）
INSERT OVERWRITE TABLE dwd.dwd_pur_fact_arrival_line PARTITION (dt='${dt}')
SELECT
  h.pk_arriveorder                                       AS arrive_id,
  b.pk_arriveorder_b                                     AS arrive_line_id,
  b.pk_order                                             AS order_id,
  b.pk_order_b                                           AS order_line_id,
  h.pk_supplier                                          AS supplier_id,
  b.pk_material                                          AS material_id,
  TO_DATE(SUBSTR(h.dbilldate, 1, 10))                    AS arrive_date,
  CAST(COALESCE(b.nnum, b.nastnum, 0) AS DECIMAL(28,8))   AS arrive_qty,
  current_timestamp()                                    AS etl_time,
  'nc'                                                   AS source_system,
  'po_arriveorder/po_arriveorder_b'                      AS source_table,
  b.pk_arriveorder_b                                     AS source_pk,
  COALESCE(b.sourcebts, b.sourcets, h.dbilldate)         AS source_ts
FROM ods.po_arriveorder h
JOIN ods.po_arriveorder_b b
  ON h.pk_arriveorder = b.pk_arriveorder
WHERE SUBSTR(h.dbilldate, 1, 10) = '${dt}';

-- 2.10 事实：采购入库行（按入库日/业务日抽取）
INSERT OVERWRITE TABLE dwd.dwd_pur_fact_purchase_in_line PARTITION (dt='${dt}')
SELECT
  h.pk_stockps                                           AS stock_in_id,
  b.pk_stockps_b                                         AS stock_in_line_id,
  b.pk_order                                             AS order_id,
  b.pk_order_b                                           AS order_line_id,
  b.cprojectid                                           AS project_id,
  b.pk_supplier                                          AS supplier_id,
  b.pk_material                                          AS material_id,
  TO_DATE(SUBSTR(h.dbilldate, 1, 10))                    AS in_date,
  TO_DATE(SUBSTR(b.dbizdate, 1, 10))                     AS biz_date,
  TO_DATE(SUBSTR(b.drequiredate, 1, 10))                 AS require_date,
  CAST(COALESCE(b.ninnum, 0) AS DECIMAL(28,8))            AS in_qty,
  CAST(COALESCE(b.nmny, b.nestmny, 0) AS DECIMAL(28,8))   AS in_amt_excl_tax,
  CAST(b.norigtaxmny AS DECIMAL(28,8))                   AS in_amt_incl_tax,
  CAST(b.ncostmny AS DECIMAL(28,8))                      AS cost_amt,
  b.cstateid                                             AS stock_state_id,
  current_timestamp()                                    AS etl_time,
  'nc'                                                   AS source_system,
  'po_purchaseinfi/po_purchaseinfi_b'                    AS source_table,
  b.pk_stockps_b                                         AS source_pk,
  COALESCE(b.sourcebts, b.sourcets, h.dbilldate)         AS source_ts
FROM ods.po_purchaseinfi h
JOIN ods.po_purchaseinfi_b b
  ON h.pk_stockps = b.pk_stockps
WHERE COALESCE(SUBSTR(b.dbizdate, 1, 10), SUBSTR(h.dbilldate, 1, 10)) = '${dt}';

-- 2.11 事实：采购合同明细（模板，字段名以dg.docx为准）
INSERT OVERWRITE TABLE dwd.dwd_pur_fact_contract_line PARTITION (dt='${dt}')
SELECT
  h.pk_ct_pu                                            AS contract_id,
  b.pk_ct_pu_b                                          AS contract_line_id,
  b.pk_praybill_b                                       AS related_pray_line_id,
  TO_DATE(SUBSTR(h.actualvalidate, 1, 10))              AS contract_effective_date,
  TO_DATE(SUBSTR(h.actualinvalidate, 1, 10))            AS contract_invalid_date,
  current_timestamp()                                   AS etl_time,
  'nc'                                                  AS source_system,
  'ct_pu/ct_pu_b'                                       AS source_table,
  b.pk_ct_pu_b                                          AS source_pk,
  COALESCE(b.sourcets, h.modifiedtime, h.actualvalidate) AS source_ts
FROM ods.ct_pu h
JOIN ods.ct_pu_b b
  ON h.pk_ct_pu = b.pk_ct_pu;

-- 2.11 宽表：采购全流程（以订单行为基表）
INSERT OVERWRITE TABLE dwd.dwd_pur_wide_order_lifecycle_line PARTITION (dt='${dt}')
WITH order_base AS (
  SELECT
    order_id,
    order_line_id,
    pray_bill_id,
    pray_line_id,
    project_id,
    supplier_id,
    material_id,
    order_date,
    plan_arrive_date,
    order_qty,
    order_amt_excl_tax AS order_amt
  FROM dwd.dwd_pur_fact_order_line
  WHERE dt='${dt}'
),
pray AS (
  SELECT
    pray_bill_id,
    pray_line_id,
    apply_date,
    require_date
  FROM dwd.dwd_pur_fact_pray_line
  WHERE dt BETWEEN '${dt_prev}' AND '${dt}'
),
arrive AS (
  SELECT
    order_line_id,
    MIN(arrive_date) AS arrive_date,
    SUM(arrive_qty)  AS arrive_qty
  FROM dwd.dwd_pur_fact_arrival_line
  WHERE dt BETWEEN '${dt_prev}' AND '${dt}'
  GROUP BY order_line_id
),
stockin AS (
  SELECT
    order_line_id,
    MIN(COALESCE(biz_date, in_date)) AS stock_in_date,
    SUM(in_qty)                      AS stock_in_qty
  FROM dwd.dwd_pur_fact_purchase_in_line
  WHERE dt BETWEEN '${dt_prev}' AND '${dt}'
  GROUP BY order_line_id
)
SELECT
  o.order_id,
  o.order_line_id,
  o.pray_bill_id,
  o.pray_line_id,
  o.project_id,
  o.supplier_id,
  o.material_id,
  p.apply_date,
  COALESCE(p.require_date, NULL) AS require_date,
  o.order_date,
  o.plan_arrive_date,
  a.arrive_date,
  s.stock_in_date,
  o.order_qty,
  o.order_amt,
  CAST(COALESCE(a.arrive_qty, 0) AS DECIMAL(28,8))    AS arrive_qty,
  CAST(COALESCE(s.stock_in_qty, 0) AS DECIMAL(28,8))  AS stock_in_qty,
  CASE
    WHEN COALESCE(s.stock_in_date, a.arrive_date) IS NULL OR COALESCE(p.require_date, NULL) IS NULL THEN NULL
    ELSE DATEDIFF(COALESCE(s.stock_in_date, a.arrive_date), p.require_date)
  END AS delay_days,
  CASE
    WHEN COALESCE(s.stock_in_date, a.arrive_date) IS NULL OR p.apply_date IS NULL THEN NULL
    ELSE DATEDIFF(COALESCE(s.stock_in_date, a.arrive_date), p.apply_date)
  END AS cycle_days,
  CASE
    WHEN COALESCE(s.stock_in_date, a.arrive_date) IS NULL OR o.plan_arrive_date IS NULL THEN NULL
    WHEN COALESCE(s.stock_in_date, a.arrive_date) <= o.plan_arrive_date THEN 1 ELSE 0
  END AS is_ontime,
  CASE
    WHEN s.stock_in_date IS NOT NULL THEN '已入库'
    WHEN a.arrive_date IS NOT NULL THEN '已到货'
    WHEN o.order_date IS NOT NULL THEN '已下单'
    ELSE '已请购'
  END AS current_stage,
  current_timestamp() AS etl_time,
  'nc'                AS source_system,
  'po_praybill*/po_order*/po_arriveorder*/po_purchaseinfi*' AS source_table,
  o.order_line_id     AS source_pk,
  CAST(current_timestamp() AS STRING) AS source_ts
FROM order_base o
LEFT JOIN pray p   ON o.pray_line_id = p.pray_line_id
LEFT JOIN arrive a ON o.order_line_id = a.order_line_id
LEFT JOIN stockin s ON o.order_line_id = s.order_line_id;

-- ============================================================================
-- 3. DWS 层：DDL
-- ============================================================================

CREATE TABLE IF NOT EXISTS dws.dws_inv_stock_snapshot_di (
  material_id               STRING  COMMENT '物料ID',
  warehouse_id              STRING  COMMENT '仓库ID',
  batch_id                  STRING  COMMENT '批次ID',
  project_id                STRING  COMMENT '项目ID',
  stock_qty                 DECIMAL(28,8) COMMENT '结存数量',
  stock_amt                 DECIMAL(28,8) COMMENT '结存金额',
  first_in_date             DATE    COMMENT '首次入库日期',
  last_out_date             DATE    COMMENT '最近出库日期',
  age_days                  INT     COMMENT '库龄(天)',
  produced_date             DATE    COMMENT '生产日期',
  expiry_date               DATE    COMMENT '到期/失效日期',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '库存日快照主题表'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_inv_stock_summary_di (
  material_class_id         STRING  COMMENT '物料分类ID',
  stock_qty                 DECIMAL(28,8) COMMENT '库存数量',
  stock_amt                 DECIMAL(28,8) COMMENT '库存金额',
  sku_cnt                   BIGINT  COMMENT 'SKU数',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '库存按分类日汇总主题表'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_inv_turnover_mn (
  material_class_id         STRING  COMMENT '物料分类ID',
  out_amt                   DECIMAL(28,8) COMMENT '月出库金额',
  avg_stock_amt             DECIMAL(28,8) COMMENT '月平均库存金额',
  turnover_rate             DECIMAL(28,8) COMMENT '周转率',
  turnover_days             DECIMAL(28,8) COMMENT '周转天数',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '库存周转月汇总主题表'
PARTITIONED BY (stat_month STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_inv_idle_stock_di (
  idle_bucket               STRING  COMMENT '闲置阈值桶(3m/6m/9m/12m)',
  idle_stock_amt            DECIMAL(28,8) COMMENT '闲置库存金额',
  total_stock_amt           DECIMAL(28,8) COMMENT '总库存金额',
  idle_rate                 DECIMAL(28,8) COMMENT '闲置率',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '闲置库存日汇总主题表'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_inv_expiry_risk_di (
  months_to_expiry_bucket   STRING  COMMENT '临期桶(<=1m/<=3m/<=6m/<=12m)',
  risk_sku_cnt              BIGINT  COMMENT '临期SKU数',
  risk_stock_amt            DECIMAL(28,8) COMMENT '临期金额',
  total_sku_cnt             BIGINT  COMMENT '总SKU数',
  risk_rate                 DECIMAL(28,8) COMMENT '临期SKU占比',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '效期风险日汇总主题表'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_pur_delivery_kpi_di (
  project_id                STRING  COMMENT '项目ID',
  supplier_id               STRING  COMMENT '供应商ID',
  order_line_cnt            BIGINT  COMMENT '订单行数',
  ontime_cnt                BIGINT  COMMENT '准时行数',
  ontime_rate               DECIMAL(28,8) COMMENT '准时率',
  avg_delay_days            DECIMAL(28,8) COMMENT '平均延迟天数',
  purchase_amt              DECIMAL(28,8) COMMENT '采购金额',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '采购交付KPI日汇总主题表'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_pur_cycle_by_class_mn (
  material_class_id         STRING  COMMENT '物料分类ID',
  order_line_cnt            BIGINT  COMMENT '订单行数',
  avg_cycle_days            DECIMAL(28,8) COMMENT '平均采购周期(天)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '采购周期按分类月汇总主题表'
PARTITIONED BY (stat_month STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_pur_urgent_ratio_mn (
  urgent_amt                DECIMAL(28,8) COMMENT '紧急采购金额',
  total_amt                 DECIMAL(28,8) COMMENT '总采购金额',
  urgent_rate               DECIMAL(28,8) COMMENT '紧急采购占比',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '紧急采购占比月汇总主题表'
PARTITIONED BY (stat_month STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_pur_single_source_ratio_mn (
  single_source_sku_cnt     BIGINT  COMMENT '单一来源SKU数',
  total_sku_cnt             BIGINT  COMMENT '总SKU数',
  single_source_rate        DECIMAL(28,8) COMMENT 'SKU占比',
  single_source_amt_rate    DECIMAL(28,8) COMMENT '金额占比(如落地)',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '单一来源占比月汇总主题表'
PARTITIONED BY (stat_month STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_pur_purchase_trend_mn (
  purchase_amt              DECIMAL(28,8) COMMENT '月采购金额',
  order_line_cnt            BIGINT  COMMENT '订单行数',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '月度采购趋势主题表'
PARTITIONED BY (stat_month STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_proj_budget_exec_di (
  project_id                STRING  COMMENT '项目ID',
  approved_budget_amt       DECIMAL(28,8) COMMENT '批复预算(含调整)',
  reserved_amt              DECIMAL(28,8) COMMENT '预占金额',
  exec_amt                  DECIMAL(28,8) COMMENT '执行金额',
  balance_amt               DECIMAL(28,8) COMMENT '结余金额',
  exec_rate                 DECIMAL(28,8) COMMENT '执行率',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '预算执行日快照主题表'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_proj_budget_exec_mn (
  project_id                STRING  COMMENT '项目ID',
  approved_budget_amt       DECIMAL(28,8) COMMENT '批复预算',
  exec_amt                  DECIMAL(28,8) COMMENT '执行金额',
  exec_rate                 DECIMAL(28,8) COMMENT '执行率',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '预算执行月汇总主题表'
PARTITIONED BY (stat_month STRING)
STORED AS PARQUET;

CREATE TABLE IF NOT EXISTS dws.dws_proj_progress_di (
  project_id                STRING  COMMENT '项目ID',
  plan_finish_date          DATE    COMMENT '计划完成日期',
  actu_finish_date          DATE    COMMENT '实际完成日期',
  is_finished               INT     COMMENT '是否完工(0/1)',
  is_delayed                INT     COMMENT '是否延期(0/1)',
  delay_days                INT     COMMENT '延期天数',
  etl_time                  TIMESTAMP COMMENT 'ETL时间'
)
COMMENT '项目进度日快照主题表'
PARTITIONED BY (dt STRING)
STORED AS PARQUET;

-- ============================================================================
-- 4. DWS 层：ETL（模板）
-- ============================================================================

-- 4.1 库存日快照（增量法：snapshot(dt)=snapshot(dt_prev)+net_flow(dt)）
INSERT OVERWRITE TABLE dws.dws_inv_stock_snapshot_di PARTITION (dt='${dt}')
WITH prev AS (
  SELECT
    material_id, warehouse_id, batch_id, project_id,
    stock_qty, stock_amt,
    first_in_date, last_out_date,
    produced_date, expiry_date
  FROM dws.dws_inv_stock_snapshot_di
  WHERE dt='${dt_prev}'
),
flow AS (
  SELECT
    material_id, warehouse_id, batch_id, project_id,
    SUM(net_qty) AS net_qty,
    SUM(net_amt) AS net_amt,
    MIN(CASE WHEN in_qty > 0 THEN biz_date END) AS min_in_date,
    MAX(CASE WHEN out_qty > 0 THEN biz_date END) AS max_out_date,
    MAX(produced_date) AS produced_date,
    MAX(expiry_date) AS expiry_date
  FROM dwd.dwd_inv_fact_stock_flow
  WHERE dt='${dt}'
  GROUP BY material_id, warehouse_id, batch_id, project_id
),
keys AS (
  SELECT material_id, warehouse_id, batch_id, project_id FROM prev
  UNION
  SELECT material_id, warehouse_id, batch_id, project_id FROM flow
)
SELECT
  k.material_id,
  k.warehouse_id,
  k.batch_id,
  k.project_id,
  CAST(COALESCE(p.stock_qty, 0) + COALESCE(f.net_qty, 0) AS DECIMAL(28,8)) AS stock_qty,
  CAST(COALESCE(p.stock_amt, 0) + COALESCE(f.net_amt, 0) AS DECIMAL(28,8)) AS stock_amt,
  LEAST(p.first_in_date, f.min_in_date) AS first_in_date,
  GREATEST(p.last_out_date, f.max_out_date) AS last_out_date,
  CASE
    WHEN TO_DATE('${dt}') IS NULL THEN NULL
    ELSE DATEDIFF(TO_DATE('${dt}'), COALESCE(GREATEST(p.last_out_date, f.max_out_date), LEAST(p.first_in_date, f.min_in_date)))
  END AS age_days,
  COALESCE(f.produced_date, p.produced_date) AS produced_date,
  COALESCE(f.expiry_date, p.expiry_date) AS expiry_date,
  current_timestamp() AS etl_time
FROM keys k
LEFT JOIN prev p ON k.material_id=p.material_id AND k.warehouse_id=p.warehouse_id AND k.batch_id=p.batch_id AND k.project_id=p.project_id
LEFT JOIN flow f ON k.material_id=f.material_id AND k.warehouse_id=f.warehouse_id AND k.batch_id=f.batch_id AND k.project_id=f.project_id;

-- 4.2 库存分类日汇总
INSERT OVERWRITE TABLE dws.dws_inv_stock_summary_di PARTITION (dt='${dt}')
SELECT
  m.material_class_id,
  CAST(SUM(s.stock_qty) AS DECIMAL(28,8)) AS stock_qty,
  CAST(SUM(s.stock_amt) AS DECIMAL(28,8)) AS stock_amt,
  COUNT(DISTINCT s.material_id)           AS sku_cnt,
  current_timestamp()                     AS etl_time
FROM dws.dws_inv_stock_snapshot_di s
JOIN dwd.dwd_inv_master_material m
  ON s.material_id = m.material_id AND m.dt='${dt}'
WHERE s.dt='${dt}'
GROUP BY m.material_class_id;

-- 4.3 闲置库存日汇总（以 last_out_date 判定；分别输出 3m/6m/9m/12m）
INSERT OVERWRITE TABLE dws.dws_inv_idle_stock_di PARTITION (dt='${dt}')
WITH base AS (
  SELECT
    stock_amt,
    last_out_date
  FROM dws.dws_inv_stock_snapshot_di
  WHERE dt='${dt}' AND stock_amt > 0
),
tot AS (
  SELECT CAST(SUM(stock_amt) AS DECIMAL(28,8)) AS total_stock_amt FROM base
),
buckets AS (
  SELECT '3m' AS idle_bucket, 3 AS months
  UNION ALL SELECT '6m', 6
  UNION ALL SELECT '9m', 9
  UNION ALL SELECT '12m', 12
)
SELECT
  b.idle_bucket,
  CAST(SUM(CASE WHEN base.last_out_date IS NULL OR base.last_out_date <= ADD_MONTHS(TO_DATE('${dt}'), -b.months) THEN base.stock_amt ELSE 0 END) AS DECIMAL(28,8)) AS idle_stock_amt,
  t.total_stock_amt,
  CAST(
    SUM(CASE WHEN base.last_out_date IS NULL OR base.last_out_date <= ADD_MONTHS(TO_DATE('${dt}'), -b.months) THEN base.stock_amt ELSE 0 END)
    / NULLIF(t.total_stock_amt, 0) AS DECIMAL(28,8)
  ) AS idle_rate,
  current_timestamp() AS etl_time
FROM buckets b
CROSS JOIN tot t
CROSS JOIN base
GROUP BY b.idle_bucket, t.total_stock_amt;

-- 4.4 效期风险日汇总（示例临期桶：<=1m/<=3m/<=6m/<=12m）
INSERT OVERWRITE TABLE dws.dws_inv_expiry_risk_di PARTITION (dt='${dt}')
WITH base AS (
  SELECT material_id, stock_amt, expiry_date
  FROM dws.dws_inv_stock_snapshot_di
  WHERE dt='${dt}' AND stock_amt > 0 AND expiry_date IS NOT NULL
),
all_sku AS (
  SELECT COUNT(DISTINCT material_id) AS total_sku_cnt
  FROM dws.dws_inv_stock_snapshot_di
  WHERE dt='${dt}' AND stock_amt > 0
),
buckets AS (
  SELECT '<=1m' AS bucket, 1 AS months
  UNION ALL SELECT '<=3m', 3
  UNION ALL SELECT '<=6m', 6
  UNION ALL SELECT '<=12m', 12
)
SELECT
  b.bucket AS months_to_expiry_bucket,
  COUNT(DISTINCT CASE WHEN base.expiry_date <= ADD_MONTHS(TO_DATE('${dt}'), b.months) THEN base.material_id END) AS risk_sku_cnt,
  CAST(SUM(CASE WHEN base.expiry_date <= ADD_MONTHS(TO_DATE('${dt}'), b.months) THEN base.stock_amt ELSE 0 END) AS DECIMAL(28,8)) AS risk_stock_amt,
  a.total_sku_cnt,
  CAST(
    COUNT(DISTINCT CASE WHEN base.expiry_date <= ADD_MONTHS(TO_DATE('${dt}'), b.months) THEN base.material_id END)
    / NULLIF(a.total_sku_cnt, 0) AS DECIMAL(28,8)
  ) AS risk_rate,
  current_timestamp() AS etl_time
FROM buckets b
CROSS JOIN all_sku a
LEFT JOIN base ON 1=1
GROUP BY b.bucket, a.total_sku_cnt;

-- 4.5 采购交付KPI日汇总（以宽表为准）
INSERT OVERWRITE TABLE dws.dws_pur_delivery_kpi_di PARTITION (dt='${dt}')
SELECT
  project_id,
  supplier_id,
  COUNT(1)                                       AS order_line_cnt,
  SUM(CASE WHEN is_ontime=1 THEN 1 ELSE 0 END)   AS ontime_cnt,
  CAST(SUM(CASE WHEN is_ontime=1 THEN 1 ELSE 0 END) / NULLIF(COUNT(1), 0) AS DECIMAL(28,8)) AS ontime_rate,
  CAST(AVG(CAST(delay_days AS DOUBLE)) AS DECIMAL(28,8)) AS avg_delay_days,
  CAST(SUM(order_amt) AS DECIMAL(28,8))          AS purchase_amt,
  current_timestamp()                            AS etl_time
FROM dwd.dwd_pur_wide_order_lifecycle_line
WHERE dt='${dt}'
GROUP BY project_id, supplier_id;

-- 4.6 月度采购趋势（按订单金额口径；若改为入库金额请替换来源表）
INSERT OVERWRITE TABLE dws.dws_pur_purchase_trend_mn PARTITION (stat_month='${stat_month}')
SELECT
  CAST(SUM(order_amt) AS DECIMAL(28,8)) AS purchase_amt,
  COUNT(1)                              AS order_line_cnt,
  current_timestamp()                   AS etl_time
FROM dwd.dwd_pur_wide_order_lifecycle_line
WHERE SUBSTR(CAST(order_date AS STRING), 1, 7) = '${stat_month}';

-- 4.6.1 库存月周转（按分类）
INSERT OVERWRITE TABLE dws.dws_inv_turnover_mn PARTITION (stat_month='${stat_month}')
WITH out_m AS (
  SELECT
    m.material_class_id,
    CAST(SUM(f.out_amt) AS DECIMAL(28,8)) AS out_amt
  FROM dwd.dwd_inv_fact_stock_flow f
  JOIN dwd.dwd_inv_master_material m
    ON f.material_id = m.material_id AND m.dt='${dt}'
  WHERE SUBSTR(f.dt, 1, 7) = '${stat_month}'
  GROUP BY m.material_class_id
),
avg_stock_m AS (
  SELECT
    material_class_id,
    CAST(AVG(day_stock_amt) AS DECIMAL(28,8)) AS avg_stock_amt
  FROM (
    SELECT
      s.dt,
      m.material_class_id,
      SUM(s.stock_amt) AS day_stock_amt
    FROM dws.dws_inv_stock_snapshot_di s
    JOIN dwd.dwd_inv_master_material m
      ON s.material_id = m.material_id AND m.dt='${dt}'
    WHERE SUBSTR(s.dt, 1, 7) = '${stat_month}'
    GROUP BY s.dt, m.material_class_id
  ) d
  GROUP BY material_class_id
)
SELECT
  COALESCE(o.material_class_id, a.material_class_id) AS material_class_id,
  COALESCE(o.out_amt, 0)                             AS out_amt,
  COALESCE(a.avg_stock_amt, 0)                       AS avg_stock_amt,
  CAST(COALESCE(o.out_amt, 0) / NULLIF(a.avg_stock_amt, 0) AS DECIMAL(28,8)) AS turnover_rate,
  CAST(30 / NULLIF(COALESCE(o.out_amt, 0) / NULLIF(a.avg_stock_amt, 0), 0) AS DECIMAL(28,8)) AS turnover_days,
  current_timestamp() AS etl_time
FROM out_m o
FULL OUTER JOIN avg_stock_m a
  ON o.material_class_id = a.material_class_id;

-- 4.7 采购周期（按分类月汇总）
INSERT OVERWRITE TABLE dws.dws_pur_cycle_by_class_mn PARTITION (stat_month='${stat_month}')
SELECT
  m.material_class_id,
  COUNT(1) AS order_line_cnt,
  CAST(AVG(CAST(w.cycle_days AS DOUBLE)) AS DECIMAL(28,8)) AS avg_cycle_days,
  current_timestamp() AS etl_time
FROM dwd.dwd_pur_wide_order_lifecycle_line w
JOIN dwd.dwd_inv_master_material m
  ON w.material_id = m.material_id AND m.dt='${dt}'
WHERE SUBSTR(CAST(w.order_date AS STRING), 1, 7) = '${stat_month}'
  AND w.cycle_days IS NOT NULL
GROUP BY m.material_class_id;

-- 4.7.1 紧急采购占比（模板：以“无请购来源 pray_line_id 为空”为紧急口径）
INSERT OVERWRITE TABLE dws.dws_pur_urgent_ratio_mn PARTITION (stat_month='${stat_month}')
SELECT
  CAST(SUM(CASE WHEN pray_line_id IS NULL OR pray_line_id='' THEN order_amt ELSE 0 END) AS DECIMAL(28,8)) AS urgent_amt,
  CAST(SUM(order_amt) AS DECIMAL(28,8)) AS total_amt,
  CAST(SUM(CASE WHEN pray_line_id IS NULL OR pray_line_id='' THEN order_amt ELSE 0 END) / NULLIF(SUM(order_amt), 0) AS DECIMAL(28,8)) AS urgent_rate,
  current_timestamp() AS etl_time
FROM dwd.dwd_pur_wide_order_lifecycle_line
WHERE SUBSTR(CAST(order_date AS STRING), 1, 7) = '${stat_month}';

-- 4.7.2 单一来源采购占比（模板：月内同一物料只有1个供应商视为单一来源）
INSERT OVERWRITE TABLE dws.dws_pur_single_source_ratio_mn PARTITION (stat_month='${stat_month}')
WITH purchases AS (
  SELECT
    material_id,
    supplier_id,
    SUM(order_amt) AS amt
  FROM dwd.dwd_pur_wide_order_lifecycle_line
  WHERE SUBSTR(CAST(order_date AS STRING), 1, 7) = '${stat_month}'
  GROUP BY material_id, supplier_id
),
mat_sup_cnt AS (
  SELECT material_id, COUNT(DISTINCT supplier_id) AS sup_cnt
  FROM purchases
  GROUP BY material_id
),
tag AS (
  SELECT p.material_id, p.supplier_id, p.amt, c.sup_cnt
  FROM purchases p
  JOIN mat_sup_cnt c ON p.material_id = c.material_id
),
tot AS (
  SELECT
    COUNT(DISTINCT material_id) AS total_sku_cnt,
    SUM(amt) AS total_amt
  FROM tag
),
single AS (
  SELECT
    COUNT(DISTINCT CASE WHEN sup_cnt=1 THEN material_id END) AS single_source_sku_cnt,
    SUM(CASE WHEN sup_cnt=1 THEN amt ELSE 0 END) AS single_source_amt
  FROM tag
)
SELECT
  s.single_source_sku_cnt,
  t.total_sku_cnt,
  CAST(s.single_source_sku_cnt / NULLIF(t.total_sku_cnt, 0) AS DECIMAL(28,8)) AS single_source_rate,
  CAST(s.single_source_amt / NULLIF(t.total_amt, 0) AS DECIMAL(28,8)) AS single_source_amt_rate,
  current_timestamp() AS etl_time
FROM single s
CROSS JOIN tot t;

-- 4.8 项目进度日快照
INSERT OVERWRITE TABLE dws.dws_proj_progress_di PARTITION (dt='${dt}')
SELECT
  p.project_id,
  p.plan_finish_date,
  p.actu_finish_date,
  CASE WHEN p.actu_finish_date IS NOT NULL THEN 1 ELSE 0 END AS is_finished,
  CASE WHEN p.actu_finish_date IS NULL AND p.plan_finish_date IS NOT NULL AND TO_DATE('${dt}') > p.plan_finish_date THEN 1 ELSE 0 END AS is_delayed,
  CASE
    WHEN p.plan_finish_date IS NULL THEN NULL
    WHEN p.actu_finish_date IS NOT NULL THEN DATEDIFF(p.actu_finish_date, p.plan_finish_date)
    ELSE DATEDIFF(TO_DATE('${dt}'), p.plan_finish_date)
  END AS delay_days,
  current_timestamp() AS etl_time
FROM dwd.dwd_proj_master_project p
WHERE p.dt='${dt}';

-- 预算执行 DWS（依赖预算执行 DWD 数据源已就绪）
INSERT OVERWRITE TABLE dws.dws_proj_budget_exec_di PARTITION (dt='${dt}')
SELECT
  project_id,
  approved_budget_amt,
  reserved_amt,
  exec_amt,
  balance_amt,
  CAST(exec_amt / NULLIF(approved_budget_amt, 0) AS DECIMAL(28,8)) AS exec_rate,
  current_timestamp() AS etl_time
FROM dwd.dwd_proj_fact_budget_execution
WHERE dt='${dt}';

-- 预算执行月汇总（默认取月末快照；若需月内累计请调整口径）
-- 说明：需要在月末运行时 `${dt}` 为月末日，或改用窗口取当月最大dt。
INSERT OVERWRITE TABLE dws.dws_proj_budget_exec_mn PARTITION (stat_month='${stat_month}')
SELECT
  project_id,
  approved_budget_amt,
  exec_amt,
  exec_rate,
  current_timestamp() AS etl_time
FROM dws.dws_proj_budget_exec_di
WHERE dt='${dt}' AND SUBSTR(dt, 1, 7) = '${stat_month}';
