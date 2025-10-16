-- big_data_platform_scenario_full_reset.sql

-- ============================================================================
-- Hive大数据平台日常使用场景设计：用户行为分析
-- 每次运行此脚本将清空所有相关表并重新创建和加载数据，适用于测试环境。
-- ============================================================================

-- 设置Hive运行参数
SET hive.exec.dynamic.partition=true;
SET hive.exec.dynamic.partition.mode=nonstrict;
 -- 允许非分区表插入分区表
-- 设置为false可以在插入数据时，如果目标分区键的列值是null，
-- 会插入一个名为__HIVE_DEFAULT_PARTITION__的默认分区，如果为true则会报错。
-- 在此处是用于数据分区时，确保即使源数据没有明确的dt，也能进行动态分区。
-- 实际上，我们这里的dt是从event_time计算而来，应该不会是null，但作为一个通用设置可保留。


-- ============================================================================
-- 1. ODS (Operational Data Store) 层设计和数据插入
-- ============================================================================

-- 删除并创建 ods_user_logs 表
DROP TABLE IF EXISTS ods_user_logs;
CREATE TABLE ods_user_logs (
    log_id STRING COMMENT '日志ID',
    user_id STRING COMMENT '用户ID',
    event_time TIMESTAMP COMMENT '事件时间',
    event_type STRING COMMENT '事件类型 (e.g., view, add_to_cart, purchase)',
    product_id STRING COMMENT '商品ID (如果有)',
    category_id STRING COMMENT '商品类别ID (如果有)',
    price DECIMAL(10, 2) COMMENT '商品价格 (如果有)',
    quantity INT COMMENT '数量 (如果有)',
    ip_address STRING COMMENT 'IP地址',
    user_agent STRING COMMENT '用户代理',
    data_secret_level STRING COMMENT '数据密级'
)
COMMENT '用户行为原始日志表'
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE;

-- ODS层样例数据 (插入10条)
INSERT INTO TABLE ods_user_logs
SELECT 'log001', 'userA', '2023-10-26 10:00:00', 'view', 'prod101', 'cat001', 100.00, 1, '192.168.1.1', 'Chrome', 'PUBLIC'
UNION ALL
SELECT 'log002', 'userB', '2023-10-26 10:05:00', 'add_to_cart', 'prod102', 'cat002', 200.00, 2, '192.168.1.2', 'Firefox', 'INTERNAL'
UNION ALL
SELECT 'log003', 'userA', '2023-10-26 10:10:00', 'purchase', 'prod101', 'cat001', 100.00, 1, '192.168.1.1', 'Chrome', 'SECRET'
UNION ALL
SELECT 'log004', 'userC', '2023-10-26 10:15:00', 'view', 'prod103', 'cat003', 50.00, 1, '192.168.1.3', 'Safari', 'PUBLIC'
UNION ALL
SELECT 'log005', 'userB', '2023-10-26 10:20:00', 'view', 'prod101', 'cat001', 100.00, 1, '192.168.1.2', 'Firefox', 'PUBLIC'
UNION ALL
SELECT 'log006', 'userC', '2023-10-26 10:25:00', 'add_to_cart', 'prod103', 'cat003', 50.00, 3, '192.168.1.3', 'Safari', 'INTERNAL'
UNION ALL
SELECT 'log007', 'userA', '2023-10-26 10:30:00', 'view', 'prod104', 'cat004', 300.00, 1, '192.168.1.1', 'Chrome', 'PUBLIC'
UNION ALL
SELECT 'log008', 'userD', '2023-10-26 10:35:00', 'purchase', 'prod105', 'cat005', 150.00, 1, '192.168.1.4', 'Edge', 'TOP-SECRET'
UNION ALL
SELECT 'log009', 'userD', '2023-10-26 10:40:00', 'view', 'prod102', 'cat002', 200.00, 1, '192.168.1.4', 'Edge', 'PUBLIC'
UNION ALL
SELECT 'log010', 'userA', '2023-10-26 10:45:00', 'purchase', 'prod104', 'cat004', 300.00, 1, '192.168.1.1', 'Chrome', 'SECRET';


-- ============================================================================
-- 2. DW (Data Warehouse) 层设计和数据抽取
-- ============================================================================

-- 删除并创建 dim_products (商品维度表)
DROP TABLE IF EXISTS dim_products;
CREATE TABLE dim_products (
    product_id STRING COMMENT '商品ID',
    product_name STRING COMMENT '商品名称',
    category_id STRING COMMENT '商品类别ID',
    category_name STRING COMMENT '商品类别名称',
    brand STRING COMMENT '品牌',
    data_secret_level STRING COMMENT '数据密级'
)
COMMENT '商品维度表'
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE;

-- 插入 dim_products 数据
INSERT INTO TABLE dim_products
SELECT 'prod101', '智能手机X', 'cat001', '电子产品', '品牌A', 'PUBLIC'
UNION ALL
SELECT 'prod102', '蓝牙耳机', 'cat002', '数码配件', '品牌B', 'PUBLIC'
UNION ALL
SELECT 'prod103', '运动鞋', 'cat003', '服装鞋帽', '品牌C', 'PUBLIC'
UNION ALL
SELECT 'prod104', '智能手表', 'cat004', '电子产品', '品牌D', 'INTERNAL'
UNION ALL
SELECT 'prod105', '高端笔记本', 'cat005', '电脑办公', '品牌E', 'SECRET';

-- 删除并创建 dim_users (用户维度表)
DROP TABLE IF EXISTS dim_users;
CREATE TABLE dim_users (
    user_id STRING COMMENT '用户ID',
    gender STRING COMMENT '性别',
    age_group STRING COMMENT '年龄段',
    registration_date DATE COMMENT '注册日期',
    data_secret_level STRING COMMENT '数据密级'
)
COMMENT '用户维度表'
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE;

-- 插入 dim_users 数据
INSERT INTO TABLE dim_users
SELECT 'userA', 'Male', '25-34', '2022-01-01', 'INTERNAL'
UNION ALL
SELECT 'userB', 'Female', '18-24', '2022-03-15', 'PUBLIC'
UNION ALL
SELECT 'userC', 'Male', '35-44', '2021-11-20', 'PUBLIC'
UNION ALL
SELECT 'userD', 'Female', '25-34', '2023-05-10', 'TOP-SECRET';


-- 删除并创建 fact_user_events (用户事件事实表)
DROP TABLE IF EXISTS fact_user_events;
CREATE TABLE fact_user_events (
    user_id STRING COMMENT '用户ID',
    product_id STRING COMMENT '商品ID',
    event_date DATE COMMENT '事件日期',
    view_count BIGINT COMMENT '浏览次数',
    add_to_cart_count BIGINT COMMENT '加入购物车次数',
    purchase_count BIGINT COMMENT '购买次数',
    total_purchase_amount DECIMAL(18, 2) COMMENT '总购买金额',
    data_secret_level STRING COMMENT '数据密级'
)
COMMENT '用户事件事实表'
PARTITIONED BY (dt STRING COMMENT '日期分区')
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE;

-- 从ODS抽取数据到DW (以当日数据 '2023-10-26' 为例)
INSERT INTO TABLE fact_user_events PARTITION(dt)
SELECT
    t.user_id,
    t.product_id,
    t.event_date,
    SUM(CASE WHEN t.event_type = 'view' THEN 1 ELSE 0 END) AS view_count,
    SUM(CASE WHEN t.event_type = 'add_to_cart' THEN 1 ELSE 0 END) AS add_to_cart_count,
    SUM(CASE WHEN t.event_type = 'purchase' THEN 1 ELSE 0 END) AS purchase_count,
    SUM(CASE WHEN t.event_type = 'purchase' THEN t.price * t.quantity ELSE 0 END) AS total_purchase_amount,
    -- data_secret_level 聚合：对于同一用户同一商品同一天的所有行为，取最高密级
    -- 使用 CASE WHEN 来定义密级等级以便比较
    CASE
        WHEN MAX(CASE WHEN t.data_secret_level = 'TOP-SECRET' THEN 4
                      WHEN t.data_secret_level = 'SECRET' THEN 3
                      WHEN t.data_secret_level = 'INTERNAL' THEN 2
                      WHEN t.data_secret_level = 'PUBLIC' THEN 1 ELSE 0 END) = 4 THEN 'TOP-SECRET'
        WHEN MAX(CASE WHEN t.data_secret_level = 'TOP-SECRET' THEN 4
                      WHEN t.data_secret_level = 'SECRET' THEN 3
                      WHEN t.data_secret_level = 'INTERNAL' THEN 2
                      WHEN t.data_secret_level = 'PUBLIC' THEN 1 ELSE 0 END) = 3 THEN 'SECRET'
        WHEN MAX(CASE WHEN t.data_secret_level = 'TOP-SECRET' THEN 4
                      WHEN t.data_secret_level = 'SECRET' THEN 3
                      WHEN t.data_secret_level = 'INTERNAL' THEN 2
                      WHEN t.data_secret_level = 'PUBLIC' THEN 1 ELSE 0 END) = 2 THEN 'INTERNAL'
        WHEN MAX(CASE WHEN t.data_secret_level = 'TOP-SECRET' THEN 4
                      WHEN t.data_secret_level = 'SECRET' THEN 3
                      WHEN t.data_secret_level = 'INTERNAL' THEN 2
                      WHEN t.data_secret_level = 'PUBLIC' THEN 1 ELSE 0 END) = 1 THEN 'PUBLIC'
        ELSE 'PUBLIC' -- 默认或处理未知情况
    END AS data_secret_level,
    t.dt
FROM (
    SELECT
        user_id,
        product_id,
        event_time,
        event_type,
        price,
        quantity,
        data_secret_level,
        CAST(event_time AS DATE) AS event_date,
        DATE_FORMAT(event_time, 'yyyy-MM-dd') AS dt
    FROM
        ods_user_logs
    WHERE
        DATE_FORMAT(event_time, 'yyyy-MM-dd') = '2023-10-26'
) t
GROUP BY
    t.user_id,
    t.product_id,
    t.event_date,
    t.dt;






-- ============================================================================
-- 3. ADS (Application Data Service) 层设计和数据抽取
-- ============================================================================

-- 删除并创建 ads_daily_product_summary (每日商品汇总报表)
DROP TABLE IF EXISTS ads_daily_product_summary;
CREATE TABLE ads_daily_product_summary (
    event_date DATE COMMENT '事件日期',
    product_id STRING COMMENT '商品ID',
    product_name STRING COMMENT '商品名称',
    category_name STRING COMMENT '商品类别名称',
    daily_view_count BIGINT COMMENT '当日浏览次数',
    daily_add_to_cart_count BIGINT COMMENT '当日加入购物车次数',
    daily_purchase_count BIGINT COMMENT '当日购买次数',
    daily_total_sales DECIMAL(18, 2) COMMENT '当日销售额',
    data_secret_level STRING COMMENT '数据密级'
)
COMMENT '每日商品汇总报表'
PARTITIONED BY (dt STRING COMMENT '日期分区')
ROW FORMAT DELIMITED FIELDS TERMINATED BY '\t'
STORED AS TEXTFILE;

-- 从DW层抽取数据到ADS (以当日数据 '2023-10-26' 为例)
INSERT INTO TABLE ads_daily_product_summary PARTITION(dt)
SELECT
    fue.event_date,
    fue.product_id,
    dp.product_name,
    dp.category_name,
    SUM(fue.view_count) AS daily_view_count,
    SUM(fue.add_to_cart_count) AS daily_add_to_cart_count,
    SUM(fue.purchase_count) AS daily_purchase_count,
    SUM(fue.total_purchase_amount) AS daily_total_sales,
    -- 聚合时取事实表和维度表中所有参与字段的最高密级
    MAX(GREATEST(fue.data_secret_level, dp.data_secret_level)) AS data_secret_level,
    DATE_FORMAT(fue.event_date, 'yyyy-MM-dd') AS dt
FROM fact_user_events fue
JOIN dim_products dp ON fue.product_id = dp.product_id
WHERE
    DATE_FORMAT(fue.event_date, 'yyyy-MM-dd') = '2023-10-26'
GROUP BY
    fue.event_date,
    fue.product_id,
    dp.product_name,
    dp.category_name;


-- ============================================================================
-- 4. ADS层报表查询语句
-- ============================================================================

-- 报表1: 查看每日销售额最高的商品 (TOP-5)
SELECT
    event_date,
    product_name,
    daily_total_sales,
    data_secret_level
FROM
    ads_daily_product_summary
WHERE
    dt = '2023-10-26'
ORDER BY
    daily_total_sales DESC
LIMIT 5;

-- 报表2: 查看特定类别的商品销售情况
SELECT
    event_date,
    category_name,
    SUM(daily_purchase_count) AS total_purchase_count,
    SUM(daily_total_sales) AS total_sales_amount,
    MAX(data_secret_level) AS aggregated_secret_level
FROM
    ads_daily_product_summary
WHERE
    dt = '2023-10-26' AND category_name = '电子产品'
GROUP BY
    event_date,
    category_name;

-- 报表3: 查看每日用户行为汇总 (例如，活跃用户数、总浏览量、总购买量)
SELECT
    event_date,
    COUNT(DISTINCT user_id) AS distinct_users,
    SUM(view_count) AS total_views,
    SUM(purchase_count) AS total_purchases,
    SUM(total_purchase_amount) AS total_sales,
    MAX(data_secret_level) AS aggregated_secret_level
FROM
    fact_user_events
WHERE
    dt = '2023-10-26'
GROUP BY
    event_date;