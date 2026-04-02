-- ============================================================
-- 财务四张 ODS 源表 DDL
-- 数据库: PostgreSQL
-- 说明:   根据客户提供的 CSV 表头定义，创建对应的 ODS 层表
-- ============================================================

-- 1. 自有资金表
-- CSV 表头: 年度,事业基金,事业基金备注,折旧基金,折旧基金备注,职工福利基金,安全生产基金,合计
DROP TABLE IF EXISTS own_fund;
CREATE TABLE own_fund (
    year_period    VARCHAR(50)    NOT NULL,   -- 年度（如 "2026年初"、"2026年预计增加"、"2026年预计使用"、"2026年余额"）
    career_fund    NUMERIC(15,2),             -- 事业基金（万元）
    career_note    VARCHAR(200),              -- 事业基金备注
    deprec_fund    NUMERIC(15,2),             -- 折旧基金（万元）
    deprec_note    VARCHAR(200),              -- 折旧基金备注
    welfare_fund   NUMERIC(15,2),             -- 职工福利基金（万元）
    safety_fund    NUMERIC(15,2),             -- 安全生产基金（万元）
    total          NUMERIC(15,2)              -- 合计（万元）
);

COMMENT ON TABLE  own_fund              IS '自有资金表';
COMMENT ON COLUMN own_fund.year_period  IS '年度期间，如 2026年初、2026年预计增加、2026年预计使用、2026年余额';
COMMENT ON COLUMN own_fund.career_fund  IS '事业基金（万元）';
COMMENT ON COLUMN own_fund.career_note  IS '事业基金备注';
COMMENT ON COLUMN own_fund.deprec_fund  IS '折旧基金（万元）';
COMMENT ON COLUMN own_fund.deprec_note  IS '折旧基金备注';
COMMENT ON COLUMN own_fund.welfare_fund IS '职工福利基金（万元）';
COMMENT ON COLUMN own_fund.safety_fund  IS '安全生产基金（万元）';
COMMENT ON COLUMN own_fund.total        IS '合计 = 事业基金 + 折旧基金 + 职工福利基金 + 安全生产基金';


-- 2. 项目经费表
-- CSV 表头: 项目编号,研制周期,总经费,直接成本控制数,预留简间接费用和受益,直接成本执行率,间接费用支出和收益总额
DROP TABLE IF EXISTS project_fund;
CREATE TABLE project_fund (
    project_id       VARCHAR(50)    NOT NULL,   -- 项目编号（如 PRJ-001）
    cycle            VARCHAR(50),               -- 研制周期（如 "2026.01-2027.06"）
    total_fund       NUMERIC(15,2),             -- 总经费（万元）
    direct_ctrl      NUMERIC(15,2),             -- 直接成本控制数（万元）
    reserve_indirect NUMERIC(15,2),             -- 预留间接费用和收益（万元）
    direct_rate      NUMERIC(8,2),              -- 直接成本执行率（%）
    indirect_spent   NUMERIC(15,2)              -- 间接费用支出和收益总额（万元）
);

COMMENT ON TABLE  project_fund                  IS '项目经费表';
COMMENT ON COLUMN project_fund.project_id       IS '项目编号';
COMMENT ON COLUMN project_fund.cycle            IS '研制周期，如 2026.01-2027.06';
COMMENT ON COLUMN project_fund.total_fund       IS '总经费（万元）';
COMMENT ON COLUMN project_fund.direct_ctrl      IS '直接成本控制数（万元）';
COMMENT ON COLUMN project_fund.reserve_indirect IS '预留间接费用和收益（万元）';
COMMENT ON COLUMN project_fund.direct_rate      IS '直接成本执行率（百分比数值，如 85.3 表示 85.3%）';
COMMENT ON COLUMN project_fund.indirect_spent   IS '间接费用支出和收益总额（万元）';


-- 3. 辅助余额表（项目维度）
-- CSV 表头: 科目编号,科目名称,部门名称,合同名称,余额
DROP TABLE IF EXISTS aux_balance;
CREATE TABLE aux_balance (
    subject_code   VARCHAR(20)    NOT NULL,   -- 科目编号（如 5001.01）
    subject_name   VARCHAR(100),              -- 科目名称（如 "原材料-钢材"）
    dept_name      VARCHAR(100),              -- 部门名称
    contract_name  VARCHAR(200),              -- 合同名称（无合同时为 "—"）
    balance        NUMERIC(15,2)              -- 余额（元）
);

COMMENT ON TABLE  aux_balance                IS '辅助余额表（项目维度）';
COMMENT ON COLUMN aux_balance.subject_code   IS '科目编号，如 5001.01';
COMMENT ON COLUMN aux_balance.subject_name   IS '科目名称';
COMMENT ON COLUMN aux_balance.dept_name      IS '部门名称';
COMMENT ON COLUMN aux_balance.contract_name  IS '合同名称，无合同时为 —';
COMMENT ON COLUMN aux_balance.balance        IS '余额（元）';


-- 4. 辅助余额表（个人维度）
-- CSV 表头: 科目编号,科目名称,职工部门,职工名称,余额
DROP TABLE IF EXISTS aux_balance_personal;
CREATE TABLE aux_balance_personal (
    subject_code   VARCHAR(20)    NOT NULL,   -- 科目编号（如 1122.01、2211.01）
    subject_name   VARCHAR(100),              -- 科目名称（如 "备用金"、"工资应付"）
    employee_dept  VARCHAR(100),              -- 职工部门
    employee_name  VARCHAR(100),              -- 职工名称
    balance        NUMERIC(15,2)              -- 余额（元）：正数=借方(应收)，负数=贷方(应付)
);

COMMENT ON TABLE  aux_balance_personal                IS '辅助余额表（个人维度）';
COMMENT ON COLUMN aux_balance_personal.subject_code   IS '科目编号，1122.xx=其他应收款，2211.xx=应付职工薪酬';
COMMENT ON COLUMN aux_balance_personal.subject_name   IS '科目名称';
COMMENT ON COLUMN aux_balance_personal.employee_dept  IS '职工部门';
COMMENT ON COLUMN aux_balance_personal.employee_name  IS '职工名称';
COMMENT ON COLUMN aux_balance_personal.balance        IS '余额（元），正数=借方余额(应收/借款)，负数=贷方余额(应付/代扣)';
