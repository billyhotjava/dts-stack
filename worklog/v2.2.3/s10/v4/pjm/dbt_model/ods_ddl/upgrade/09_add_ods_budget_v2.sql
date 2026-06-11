-- ============================================================
-- 增量升级 DDL：仅新增「预算执行台账」ODS 表 ods_budget_v2
--
-- 适用场景：现场已部署 dbt 项目与既有 9 张 ODS 表（进度/质量/技术状态/
--           风险/物料等），仅需新增第 6 个业务域「预算」。
--
-- ⚠ 不要在现场执行整个 ods_create_tables_v2.sql！
--   该文件对全部 10 张表都是 `DROP TABLE IF EXISTS ... CASCADE`，
--   会连带删除现场其他 9 个域的 ODS 表及其 CASCADE 依赖（视图等），
--   造成数据丢失。升级只执行本文件即可。
--
-- 安全性：
--   - 仅 CREATE，使用 IF NOT EXISTS，幂等；重复执行不丢数据。
--   - 只涉及 ods_budget_v2 一张表，绝不触碰其他对象。
--
-- 用法：
--   psql -h <host> -U <user> -d <db> -v ON_ERROR_STOP=1 \
--        -f ods_ddl/upgrade/09_add_ods_budget_v2.sql
-- ============================================================

\set ON_ERROR_STOP on

-- 预算执行台账表（来源: budget.xlsx，8个业务字段；科研经费"三本账"快照）
-- 金额单位：万元（DWD/ADS 按源值原样建模，不做单位换算）
-- budget_no：全局唯一自然主键
CREATE TABLE IF NOT EXISTS ods_budget_v2 (
    id                       serial PRIMARY KEY,
    project_no               varchar(500),   -- 项目号
    budget_no                varchar(500),   -- 预算编号（全局唯一）
    subtopic                 varchar(2000),  -- 所属子课题
    research_lab             varchar(500),   -- 研究室
    budget_amount_adjusted   varchar(500),   -- 预算金额（调整后），万元
    prepaid_amount           varchar(500),   -- 预付账款（未验收未报销、无发票），万元
    book_cost_amount         varchar(500),   -- 账面成本（已验收有发票），万元
    payable_amount           varchar(500),   -- 应付账款（有发票暂未付款），万元
    _dts_source_system       varchar(500) DEFAULT 'excel',
    _dts_import_time         timestamp DEFAULT now()
);

-- 校验：确认表已存在
SELECT 'ods_budget_v2' AS tbl,
       count(*)        AS rows
FROM ods_budget_v2;
