-- ============================================================
-- DWH DEMO: ODS -> DIM/FACT (Inceptor/Hive Compatible)


USE default;

-- 动态分区设置（Inceptor/Hive）
SET hive.exec.dynamic.partition=true;
SET hive.exec.dynamic.partition.mode=nonstrict;

-- 为了更好的压缩/查询，统一使用 ORC
-- 也可根据需要切换为 PARQUET

-- ============================================================
-- 1) ODS 层（模拟 RDBMS 落地的拉链/快照/增量）
-- ============================================================

-- 客户表（RDBMS 源）
DROP TABLE IF EXISTS ods_customers;
CREATE TABLE ods_customers (
  customer_id        STRING COMMENT '业务主键：客户ID',
  customer_name      STRING COMMENT '客户名称',
  customer_email     STRING COMMENT '邮箱',
  customer_phone     STRING COMMENT '电话',
  customer_city      STRING COMMENT '城市',
  customer_tier      STRING COMMENT '等级：A/B/C',
  is_active          INT    COMMENT '是否有效：1有效 0无效',
  etl_dt             STRING COMMENT 'ODS装载日期YYYY-MM-DD'
)
STORED AS ORC;

-- 商品表（RDBMS 源）
DROP TABLE IF EXISTS ods_products;
CREATE TABLE ods_products (
  product_id         STRING COMMENT '业务主键：商品ID',
  product_name       STRING COMMENT '商品名称',
  category           STRING COMMENT '品类',
  brand              STRING COMMENT '品牌',
  unit_price         DECIMAL(18,2) COMMENT '标准单价',
  is_active          INT COMMENT '是否有效：1有效 0无效',
  etl_dt             STRING COMMENT 'ODS装载日期YYYY-MM-DD'
)
STORED AS ORC;

-- 订单表（RDBMS 源）
DROP TABLE IF EXISTS ods_orders;
CREATE TABLE ods_orders (
  order_id           STRING COMMENT '业务主键：订单ID',
  customer_id        STRING COMMENT '客户ID',
  order_date         STRING COMMENT '下单日期YYYY-MM-DD',
  order_status       STRING COMMENT '状态：NEW/PAID/CANCELLED',
  payment_method     STRING COMMENT '支付方式：CARD/CASH/TRANSFER',
  etl_dt             STRING COMMENT 'ODS装载日期YYYY-MM-DD'
)
STORED AS ORC;

-- 订单明细表（RDBMS 源）
-- 说明：这里不做分区，事实层会按天分区
DROP TABLE IF EXISTS ods_order_items;
CREATE TABLE ods_order_items (
  order_item_id      STRING COMMENT '业务主键：订单明细ID',
  order_id           STRING COMMENT '订单ID',
  product_id         STRING COMMENT '商品ID',
  quantity           INT    COMMENT '数量',
  unit_price         DECIMAL(18,2) COMMENT '成交单价（可与标准单价不同）',
  discount_amount    DECIMAL(18,2) COMMENT '整行折扣金额',
  etl_dt             STRING COMMENT 'ODS装载日期YYYY-MM-DD'
)
STORED AS ORC;

-- ============================================================
-- 2) 样例数据（演示用） 
-- 采用 SELECT ... UNION ALL ... 方式插入，兼容 Hive/Inceptor
-- ============================================================

-- customers
INSERT INTO ods_customers
SELECT 'C001','张三','zhangsan@example.com','13800000001','上海','A',1,'2025-10-05' UNION ALL
SELECT 'C002','李四','lisi@example.com','13800000002','北京','B',1,'2025-10-05' UNION ALL
SELECT 'C003','王五','wangwu@example.com','13800000003','深圳','A',1,'2025-10-05';

-- products
INSERT INTO ods_products
SELECT 'P100','Apple iPhone 15','手机','Apple', 5999.00,1,'2025-10-05' UNION ALL
SELECT 'P101','Xiaomi 14','手机','Xiaomi', 3999.00,1,'2025-10-05' UNION ALL
SELECT 'P200','Apple Watch 9','穿戴','Apple', 2999.00,1,'2025-10-05' UNION ALL
SELECT 'P300','Nikon Z6 II','相机','Nikon', 12999.00,1,'2025-10-05';

-- orders
INSERT INTO ods_orders
SELECT 'O9001','C001','2025-10-01','PAID','CARD','2025-10-05' UNION ALL
SELECT 'O9002','C002','2025-10-02','PAID','CASH','2025-10-05' UNION ALL
SELECT 'O9003','C001','2025-10-03','NEW','CARD','2025-10-05';

-- order_items
INSERT INTO ods_order_items
SELECT 'OI1','O9001','P100',1,5899.00,100.00,'2025-10-05' UNION ALL
SELECT 'OI2','O9001','P200',1,2899.00, 50.00,'2025-10-05' UNION ALL
SELECT 'OI3','O9002','P101',2,3899.00,100.00,'2025-10-05' UNION ALL
SELECT 'OI4','O9002','P200',1,2999.00,  0.00,'2025-10-05' UNION ALL
SELECT 'OI5','O9003','P300',1,12599.00,400.00,'2025-10-05' UNION ALL
SELECT 'OI6','O9003','P100',1,5999.00,  0.00,'2025-10-05';

-- ============================================================
-- 3) 维度层（DIM）
-- 说明：
-- - 简化处理，维度键使用业务主键（可按需扩展SCD2）
-- - 日期维度覆盖样例数据日期范围
-- ============================================================

DROP TABLE IF EXISTS dim_customer;
CREATE TABLE dim_customer (
  customer_key       STRING COMMENT '维度键=业务主键customer_id',
  customer_id        STRING COMMENT '业务主键',
  customer_name      STRING,
  customer_email     STRING,
  customer_phone     STRING,
  customer_city      STRING,
  customer_tier      STRING,
  is_active          INT,
  src_etl_dt         STRING COMMENT '来源ODS装载日期'
)
STORED AS ORC;

INSERT OVERWRITE TABLE dim_customer
SELECT
  c.customer_id AS customer_key,
  c.customer_id,
  c.customer_name,
  c.customer_email,
  c.customer_phone,
  c.customer_city,
  c.customer_tier,
  c.is_active,
  c.etl_dt AS src_etl_dt
FROM ods_customers c;

DROP TABLE IF EXISTS dim_product;
CREATE TABLE dim_product (
  product_key        STRING COMMENT '维度键=业务主键product_id',
  product_id         STRING,
  product_name       STRING,
  category           STRING,
  brand              STRING,
  unit_price         DECIMAL(18,2),
  is_active          INT,
  src_etl_dt         STRING
)
STORED AS ORC;

INSERT OVERWRITE TABLE dim_product
SELECT
  p.product_id AS product_key,
  p.product_id,
  p.product_name,
  p.category,
  p.brand,
  p.unit_price,
  p.is_active,
  p.etl_dt AS src_etl_dt
FROM ods_products p;

DROP TABLE IF EXISTS dim_date;
CREATE TABLE dim_date (
  date_key           INT    COMMENT 'YYYYMMDD',
  date_value         STRING COMMENT 'YYYY-MM-DD',
  year               INT,
  quarter            INT,
  month              INT,
  day                INT,
  week_of_year       INT,
  is_weekend         INT
)
STORED AS ORC;

INSERT OVERWRITE TABLE dim_date
SELECT
  CAST(date_format(d,'yyyyMMdd') AS INT) AS date_key,
  date_format(d,'yyyy-MM-dd')            AS date_value,
  year(d)                                AS year,
  CAST(CEIL(month(d)/3.0) AS INT)        AS quarter,
  month(d)                               AS month,
  dayofmonth(d)                          AS day,
  weekofyear(d)                          AS week_of_year,
  CASE WHEN p IN ('SAT','SUN') THEN 1 ELSE 0 END AS is_weekend
FROM (
  SELECT to_date('2025-10-01') AS d,'WED' AS p UNION ALL
  SELECT to_date('2025-10-02'),'THU' UNION ALL
  SELECT to_date('2025-10-03'),'FRI' UNION ALL
  SELECT to_date('2025-10-04'),'SAT' UNION ALL
  SELECT to_date('2025-10-05'),'SUN'
) t;

-- ============================================================
-- 4) 事实层（FACT）
-- 说明：
-- - 事实按天分区（dt=订单日期）
-- - 指标：数量、成交金额、折扣金额、净销售额
-- ============================================================

DROP TABLE IF EXISTS fact_sales;
CREATE TABLE fact_sales (
  order_id           STRING,
  order_item_id      STRING,
  customer_key       STRING,
  product_key        STRING,
  date_key           INT,
  quantity           INT,
  unit_price         DECIMAL(18,2),
  gross_amount       DECIMAL(18,2) COMMENT 'quantity * unit_price',
  discount_amount    DECIMAL(18,2),
  net_sales          DECIMAL(18,2) COMMENT 'gross_amount - discount_amount',
  order_status       STRING,
  payment_method     STRING
)
PARTITIONED BY (dt STRING)          -- 分区：YYYY-MM-DD
STORED AS ORC;

-- 装载事实（从 ODS -> DIM 关联）
-- 只加载订单状态为 NEW/PAID（示例保留NEW以便观察差异，业务可改仅PAID）
INSERT OVERWRITE TABLE fact_sales PARTITION (dt)
SELECT
  oi.order_id,
  oi.order_item_id,
  o.customer_id                 AS customer_key,        -- 与dim_customer.customer_key对应
  oi.product_id                 AS product_key,         -- 与dim_product.product_key对应
  CAST(from_unixtime(unix_timestamp(o.order_date),'yyyyMMdd') AS INT) AS date_key,
  oi.quantity,
  oi.unit_price,
  CAST(oi.quantity * oi.unit_price AS DECIMAL(18,2)) AS gross_amount,
  oi.discount_amount,
  CAST(oi.quantity * oi.unit_price - oi.discount_amount AS DECIMAL(18,2)) AS net_sales,
  o.order_status,
  o.payment_method,
  o.order_date AS dt
FROM ods_order_items oi
JOIN ods_orders o
  ON oi.order_id = o.order_id
WHERE o.order_status IN ('NEW','PAID');

-- 可选：仅加载已支付订单
-- INSERT OVERWRITE TABLE fact_sales PARTITION (dt)
-- SELECT ... WHERE o.order_status = 'PAID';

-- ============================================================
-- 5) 校验查询（样例）
-- ============================================================

-- 分区列表
SHOW PARTITIONS fact_sales;

-- 日销售汇总
SELECT dt,
       SUM(quantity)            AS qty,
       SUM(gross_amount)        AS gross_amount,
       SUM(discount_amount)     AS discount_amount,
       SUM(net_sales)           AS net_sales
FROM fact_sales
GROUP BY dt
ORDER BY dt;

-- 加维度做报表（客群/品类）
SELECT f.dt,
       dc.customer_city,
       dp.category,
       SUM(f.quantity)     AS qty,
       SUM(f.net_sales)    AS sales
FROM fact_sales f
LEFT JOIN dim_customer dc ON f.customer_key = dc.customer_key
LEFT JOIN dim_product  dp ON f.product_key  = dp.product_key
GROUP BY f.dt, dc.customer_city, dp.category
ORDER BY f.dt, dc.customer_city, dp.category;

-- 看看某天的明细
SELECT * FROM fact_sales WHERE dt='2025-10-02' ORDER BY net_sales DESC;

-- 日期维度联查（例：周末销售）
SELECT d.date_value, d.is_weekend, SUM(f.net_sales) AS sales
FROM dim_date d
LEFT JOIN fact_sales f
  ON f.date_key = d.date_key
GROUP BY d.date_value, d.is_weekend
ORDER BY d.date_value;
