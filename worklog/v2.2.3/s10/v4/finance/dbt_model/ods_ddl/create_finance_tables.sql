-- ============================================================
-- 财务四张 ODS 源表 DDL（v2.2.3 更新版）
-- 数据库: PostgreSQL
-- 命名规范: ods_finance_ 前缀
-- 说明:  根据客户 5.1-5.4 字段定义更新：
--        - 5.1 年度基金表改为"年度 × 基金来源 × 基金类别"长表
--        - 5.2 项目经费表新增 行号/是否重大项目/研究室/项目状态/
--              直接成本账面支出总额/已收款/待收经费
--        - 5.3 / 5.4 字段保持不变
-- ============================================================


-- 5.1 年度基金表
-- 字段: 年度 | 基金来源 | 基金类别 | 金额 | 备注
--   基金来源枚举: 年初 / 预计使用 / 预计增加
--   基金类别枚举: 事业基金 / 职工福利基金 / 安全生产基金
DROP TABLE IF EXISTS ods_finance_own_fund;
CREATE TABLE ods_finance_own_fund (
    year_num       INT            NOT NULL,   -- 年度（如 2026、2027）
    fund_source    VARCHAR(50)    NOT NULL,   -- 基金来源（枚举）
    fund_category  VARCHAR(50)    NOT NULL,   -- 基金类别（枚举）
    amount         NUMERIC(15,2),             -- 金额（万元，两位小数）
    note           VARCHAR(500)               -- 备注
);

COMMENT ON TABLE  ods_finance_own_fund                IS '年度基金表';
COMMENT ON COLUMN ods_finance_own_fund.year_num       IS '年度，如 2026、2027';
COMMENT ON COLUMN ods_finance_own_fund.fund_source    IS '基金来源（枚举：年初、预计使用、预计增加）';
COMMENT ON COLUMN ods_finance_own_fund.fund_category  IS '基金类别（枚举：事业基金、职工福利基金、安全生产基金）';
COMMENT ON COLUMN ods_finance_own_fund.amount         IS '金额（万元，两位小数）';
COMMENT ON COLUMN ods_finance_own_fund.note           IS '备注';


-- 5.2 项目经费表
-- 字段: 行号 | 项目编号 | 研制周期 | 总经费 | 是否重大项目 | 研究室 |
--       项目状态 | 直接成本控制数 | 预留间接费用和收益 |
--       直接成本账面支出总额 | 直接成本执行率 |
--       间接费用支出和收益总额 | 已收款 | 待收经费
--   项目状态枚举: 已完成待收款 / 已完成审计 / 在研 / 支出待处理
DROP TABLE IF EXISTS ods_finance_project_fund;
CREATE TABLE ods_finance_project_fund (
    row_no              INT,                       -- 行号
    project_id          VARCHAR(50)    NOT NULL,   -- 项目编号
    cycle               VARCHAR(50),               -- 研制周期（如 2026.01-2027.06）
    total_fund          NUMERIC(15,2),             -- 总经费（万元，两位小数）
    is_major_project    BOOLEAN,                   -- 是否重大项目
    research_dept       VARCHAR(100),              -- 研究室
    project_status      VARCHAR(50),               -- 项目状态（枚举）
    direct_ctrl         NUMERIC(15,2),             -- 直接成本控制数（万元）
    reserve_indirect    NUMERIC(15,2),             -- 预留间接费用和收益（万元）
    direct_spent        NUMERIC(15,2),             -- 直接成本账面支出总额（万元）
    direct_rate         NUMERIC(8,2),              -- 直接成本执行率（%）
    indirect_spent      NUMERIC(15,2),             -- 间接费用支出和收益总额（万元）
    received_fund       NUMERIC(15,2),             -- 已收款（万元）
    receivable_fund     NUMERIC(15,2)              -- 待收经费（万元）
);

COMMENT ON TABLE  ods_finance_project_fund                    IS '项目经费表';
COMMENT ON COLUMN ods_finance_project_fund.row_no             IS '行号';
COMMENT ON COLUMN ods_finance_project_fund.project_id         IS '项目编号';
COMMENT ON COLUMN ods_finance_project_fund.cycle              IS '研制周期，如 2026.01-2027.06';
COMMENT ON COLUMN ods_finance_project_fund.total_fund         IS '总经费（万元）';
COMMENT ON COLUMN ods_finance_project_fund.is_major_project   IS '是否重大项目（true=是，false=否）';
COMMENT ON COLUMN ods_finance_project_fund.research_dept      IS '研究室';
COMMENT ON COLUMN ods_finance_project_fund.project_status     IS '项目状态（枚举：已完成待收款、已完成审计、在研、支出待处理）';
COMMENT ON COLUMN ods_finance_project_fund.direct_ctrl        IS '直接成本控制数（万元）';
COMMENT ON COLUMN ods_finance_project_fund.reserve_indirect   IS '预留间接费用和收益（万元）';
COMMENT ON COLUMN ods_finance_project_fund.direct_spent       IS '直接成本账面支出总额（万元）';
COMMENT ON COLUMN ods_finance_project_fund.direct_rate        IS '直接成本执行率（百分比数值，如 85.3 表示 85.3%）';
COMMENT ON COLUMN ods_finance_project_fund.indirect_spent     IS '间接费用支出和收益总额（万元）';
COMMENT ON COLUMN ods_finance_project_fund.received_fund      IS '已收款（万元）';
COMMENT ON COLUMN ods_finance_project_fund.receivable_fund    IS '待收经费（万元）';


-- 5.3 辅助余额表-合同
-- 字段: 科目编号 | 科目名称 | 部门名称 | 合同名称 | 余额
DROP TABLE IF EXISTS ods_finance_aux_balance;
CREATE TABLE ods_finance_aux_balance (
    subject_code   VARCHAR(20)    NOT NULL,   -- 科目编号（如 5001.01）
    subject_name   VARCHAR(100),              -- 科目名称（如 "原材料-钢材"）
    dept_name      VARCHAR(100),              -- 部门名称
    contract_name  VARCHAR(200),              -- 合同名称（无合同时为 "—"）
    balance        NUMERIC(15,2)              -- 余额（元）
);

COMMENT ON TABLE  ods_finance_aux_balance                IS '辅助余额表-合同';
COMMENT ON COLUMN ods_finance_aux_balance.subject_code   IS '科目编号，如 5001.01';
COMMENT ON COLUMN ods_finance_aux_balance.subject_name   IS '科目名称';
COMMENT ON COLUMN ods_finance_aux_balance.dept_name      IS '部门名称';
COMMENT ON COLUMN ods_finance_aux_balance.contract_name  IS '合同名称，无合同时为 —';
COMMENT ON COLUMN ods_finance_aux_balance.balance        IS '余额（元）';


-- 5.4 辅助余额表-个人维度
-- 字段: 科目编号 | 科目名称 | 职工部门 | 职工名称 | 余额（元）
DROP TABLE IF EXISTS ods_finance_aux_balance_personal;
CREATE TABLE ods_finance_aux_balance_personal (
    subject_code   VARCHAR(20)    NOT NULL,   -- 科目编号（如 1122.01、2211.01）
    subject_name   VARCHAR(100),              -- 科目名称（如 "备用金"、"工资应付"）
    employee_dept  VARCHAR(100),              -- 职工部门
    employee_name  VARCHAR(100),              -- 职工名称
    balance        NUMERIC(15,2)              -- 余额（元）：正数=借方(应收)，负数=贷方(应付)
);

COMMENT ON TABLE  ods_finance_aux_balance_personal                IS '辅助余额表-个人维度';
COMMENT ON COLUMN ods_finance_aux_balance_personal.subject_code   IS '科目编号，1122.xx=其他应收款，2211.xx=应付职工薪酬';
COMMENT ON COLUMN ods_finance_aux_balance_personal.subject_name   IS '科目名称';
COMMENT ON COLUMN ods_finance_aux_balance_personal.employee_dept  IS '职工部门';
COMMENT ON COLUMN ods_finance_aux_balance_personal.employee_name  IS '职工名称';
COMMENT ON COLUMN ods_finance_aux_balance_personal.balance        IS '余额（元），正数=借方余额(应收/借款)，负数=贷方余额(应付/代扣)';
