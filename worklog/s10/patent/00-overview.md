# 专利数据仓库建模方案 - 总览

## 1. 业务背景

基于 `ods_patent_info` 原始专利数据，构建从 ODS 到 ADS 的完整数仓链路，
最终为"专利分析仪表盘"提供可直接查询的宽表。

## 2. 数据源

来源表：`public.ods_patent_info`（PostgreSQL），由数据入湖任务从外部系统导入。

| 字段 | 类型 | 含义 |
|---|---|---|
| id | bigserial | 自增主键 |
| seq_no | varchar(500) | 序号 |
| patent_title_cn | varchar(500) | 专利中文名称 |
| patent_type | varchar(500) | 专利类型（发明/实用新型/外观设计） |
| patent_no | varchar(500) | 专利号 |
| application_date | varchar(500) | 申请日期（字符串，格式不统一；默认可由受理日映射） |
| accept_date | varchar(500) | 受理日期（字符串，可作为申请日期兜底） |
| grant_date | varchar(500) | 授权日期（字符串） |
| first_publication_date | varchar(500) | 首次公开日期（字符串） |
| assignee_name | varchar(500) | 申请人/权利人 |
| inventor_names | varchar(500) | 发明人（可能多人） |
| dept_name | varchar(500) | 所属部门 |
| dept_code | varchar(500) | 所属部门编码 |
| agent_org_name | varchar(500) | 代理机构 |
| state | varchar(500) | 专利状态（受理/初审/公布/实审/授权/失效/无效等） |

**数据质量问题**：
- 日期字段为字符串，格式混杂（YYYY-MM-DD、YYYY/MM/DD、YYYY.MM.DD、YYYYMMDD）
- 状态字段为自由文本，需要标准化映射（受理/初审/实审/公布/授权…）
- 专利号可能为空（手动录入数据）
- 所有字段均为 varchar(500)，无类型约束

## 3. 分层架构

```
  ODS（原始层）         已有，数据入湖写入
    |
  DIM（维度层）         状态码映射等参考数据
    |
  DWD（明细层）         清洗、标准化、补充衍生字段
    |
  DWS（汇总层）         按主题聚合统计
    |
  ADS（应用层）         面向仪表盘 / API 的最终宽表
```

### 各层职责

| 层级 | 职责 | 更新策略 | 数据粒度 |
|---|---|---|---|
| **ODS** | 原样落地，不做任何加工 | 增量/全量入湖 | 每条专利记录 |
| **DIM** | 维度参考数据（状态映射、类型字典） | 手动维护或低频更新 | 维度枚举值 |
| **DWD** | 数据清洗 + 类型转换 + 标准化 + 衍生字段 | 全量覆盖 | 每条专利记录 |
| **DWS** | 按年度/月度/部门/类型聚合 | 全量覆盖 | 聚合粒度 |
| **ADS** | 仪表盘直查宽表，当年视角 | 全量覆盖 | 面向展示 |

### 为什么要分层？

1. **可维护性** — 清洗逻辑集中在 DWD，上层不重复处理脏数据
2. **可复用性** — DWS 汇总表可被多个 ADS 表复用
3. **可追溯性** — 每层有明确输入输出，问题可逐层排查
4. **性能** — ADS 表预聚合，仪表盘直接 SELECT 即可，无需实时计算

## 4. 模型依赖图

```
ods_patent_info (source)
       |
       v
  dim_patent_status ------+
                          |
                          v
                    dwd_patent
                     /   |   \        \
                    v    v    v        v
    dws_patent_    dws_patent_  dws_patent_   dws_patent_
    year_kpi       year_type    month_trend   year_dept
        |              |            |             |
        v              v            v             v
  ads_patent_    ads_patent_  ads_patent_   ads_patent_
  dashboard_kpi  type_share   month_trend   dept_rank

                    dwd_patent
                     /   |   \
                    v    v    v
          ads_patent_  ads_patent_  ads_patent_
          recent_grant detail_year  overdue_list
```

## 5. 模型清单

| # | 模型名 | 层级 | 物化方式 | 说明 | SQL 文件 |
|---|---|---|---|---|---|
| 1 | dim_patent_status | DIM | table | 专利状态维度映射 | 01-dim/ |
| 2 | dwd_patent | DWD | table | 专利明细宽表 | 02-dwd/ |
| 3 | dws_patent_year_kpi | DWS | table | 年度核心指标（申请年口径） | 03-dws/ |
| 4 | dws_patent_year_grant | DWS | table | 年度授权数量（授权年口径） | 03-dws/ |
| 5 | dws_patent_year_type | DWS | table | 年度-类型分布 | 03-dws/ |
| 6 | dws_patent_month_trend | DWS | table | 月度趋势 | 03-dws/ |
| 7 | dws_patent_year_dept | DWS | table | 年度-部门分布 | 03-dws/ |
| 8 | ads_patent_dashboard_kpi | ADS | table | 仪表盘核心 KPI | 04-ads/ |
| 9 | ads_patent_type_share | ADS | table | 类型占比 | 04-ads/ |
| 10 | ads_patent_month_trend | ADS | table | 当年月度趋势 | 04-ads/ |
| 11 | ads_patent_dept_rank | ADS | table | 部门排名 TOP20 | 04-ads/ |
| 12 | ads_patent_recent_grant | ADS | table | 近 30 天授权列表 | 04-ads/ |
| 13 | ads_patent_detail_year | ADS | table | 当年专利明细 | 04-ads/ |
| 14 | ads_patent_overdue_list | ADS | table | 超期未授权预警 | 04-ads/ |

## 6. 前置依赖

需要在 PostgreSQL 中手动执行一次（不通过 dbt）：

- `parse_date_safe(text)` 函数 — 兼容多种日期格式的安全解析
- 详见 `00-prerequisite.sql`

## 7. dbt source 注册

需要在 `ods_sources.yml` 中添加 `ods_patent_info` 表的 source 定义，
dbt 才能通过 `{{ source('public', 'ods_patent_info') }}` 引用。

## 8. 在平台上的操作方式

1. 手动在 PG 执行 `00-prerequisite.sql`（一次性）
2. 在"逻辑建模"页面逐个创建 SQL 模型：
   - 名称 = 模型名
   - 层级 = DIM / DWD / DWS / ADS
   - 物化 = table
   - SQL = 只填 SELECT 语句（不含 `{{ config(...) }}`，平台自动生成）
3. 按依赖顺序提交运行（dim -> dwd -> dws -> ads）
4. 或配置调度，一键运行整个链路

> 说明：如需指定统计年份，可在执行前设置：
> `SET dts.report_year='2024';`  
> 未设置时默认生成“多年份缓存”，ADS 会包含最近 N 年。

可选：指定“最近 N 年”范围：
`SET dts.report_years='5';`

可选：指定 ODS 表名（无需手工建 view）：
`SET dts.ods_table='ods_patent_info';`

## 8.1 一键执行命令（推荐）

使用脚本直接执行（本机 psql 或 Docker）：

```bash
# 本机已有 psql
PG_PASSWORD='Devops123@' ./bin/patent/run-build-all.sh

# 强制使用 docker + postgres:17.6
PG_PASSWORD='Devops123@' ./bin/patent/run-build-all.sh --force-docker

# 指定 ODS 表名 + 指定年份
PG_PASSWORD='Devops123@' ODS_TABLE=ods_patent_info_202602 REPORT_YEAR=2024 \
  ./bin/patent/run-build-all.sh --force-docker

# 生成最近 N 年 ADS 缓存
PG_PASSWORD='Devops123@' REPORT_YEARS=5 ./bin/patent/run-build-all.sh
```

如需指定数据库连接：

```bash
PG_HOST=127.0.0.1 PG_PORT=5432 PG_DB=biadmin PG_USER=biadmin PG_PASSWORD='Devops123@' \
  ./bin/patent/run-build-all.sh --force-docker
```

## 9. 自定义 ODS 表名 / 中文表头适配

如果 Excel/CSV 导入后的 ODS 表名不是 `ods_patent_info`，建议用参数指定（你的表名是 `ods_patent_info`）：

```sql
SET dts.ods_table='ods_patent_info';
```

也可以创建一个同名视图做别名：

```sql
-- 将你的实际 ODS 表映射为标准名 ods_patent_info
CREATE OR REPLACE VIEW public.ods_patent_info AS
SELECT * FROM public.<你的实际ods表名>;
```

如果 Excel header 为中文，可创建 `ods_patent_info_std` 视图进行字段映射（示例表头如下，来源表为 `ods_patent_info`）：

```sql
-- 表头示例：
-- 序号, 专利名称（中文）, 专利类型, 专利号, 申请日, 授权日, 首次公开日, 专利权人, 发明人, 部门, 代理公司, 状态

CREATE OR REPLACE VIEW public.ods_patent_info_std AS
SELECT
  "序号"            AS seq_no,
  "专利名称（中文）" AS patent_title_cn,
  "专利类型"        AS patent_type,
  "专利号"          AS patent_no,
  "申请日"          AS application_date,
  "授权日"          AS grant_date,
  "首次公开日"      AS first_publication_date,
  "专利权人"        AS assignee_name,
  "发明人"          AS inventor_names,
  "部门"            AS dept_name,
  "代理公司"        AS agent_org_name,
  "状态"            AS state,
  dept_code         AS dept_code
FROM public.ods_patent_info;
```

> `99-build-all.sql` 会优先使用 `ods_patent_info_std` 作为数据来源。
