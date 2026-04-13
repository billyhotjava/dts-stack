# ODS v2 测试数据

## Excel 文件（数据入湖起点）

| 文件 | Sheet | ODS 目标表 | 行数 | 列数 |
|------|-------|-----------|------|------|
| **ods_project_subject_domain_v2.xlsx** | 进度信息汇总表 | ods_project_subject_domain_v2 | 1000 | 31 |
| **ods_progress_measure_v2.xlsx** | 进度跟进措施表 | ods_progress_measure_v2 | 80 | 23 |
| **ods_quality_issue_v2.xlsx** | 质量信息汇总表 | ods_quality_issue_v2 | 80 | 21 |
| **ods_quality_measure_v2.xlsx** | 质量跟进措施表 | ods_quality_measure_v2 | 80 | 33 |
| **ods_tech_state_v2.xlsx** | 技术状态信息汇总表 | ods_tech_state_v2 | 80 | 33 |
| **ods_tech_state_measure_v2.xlsx** | 技术状态跟进措施表 | ods_tech_state_measure_v2 | 80 | 41 |
| **ods_risk_info_v2.xlsx** | 风险信息汇总表 | ods_risk_info_v2 | 80 | 31 |
| **ods_risk_measure_v2.xlsx** | 风险跟进措施表 | ods_risk_measure_v2 | 80 | 42 |
| **ods_material_info_v2.xlsx** | 重要物料信息表 | ods_material_info_v2 | 80 | 29 |

**合计**: 1640 行测试数据

## 数据设计

- **项目**（8 个）: PJ-2025-001 卫星导航系统、002 深空探测器、003 遥感观测卫星、
  004 通信中继星座、005 空间站实验舱、006 月球着陆器、007 火星巡视器、008 低轨互联网卫星
- **分系统**: 结构/电子/软件/热控/推进（主表为 8 项目 × 5 分系统 × 25 月 = 1000 行）
- **时间跨度**: 2025-01 ~ 2027-01（覆盖多月/季/周聚合）
- **列头**: 与 ODS DDL 字段中文注释严格对齐

## 异常数据注入（用于 ETL 健壮性测试）

生成脚本（固定随机种子 `20260412`）会以约 15% 概率对时间字段注入异常格式：

| 异常类型 | 样例 |
|---------|------|
| 斜杠分隔 | `2025/07/08`、`2025/7/8` |
| 5 位年份拼写错 | `20205/07/08`、`20250/07/08` |
| 中文日期 | `2025年7月8日`、`2025年07月08日` |
| 点分隔 | `2025.7.8` |
| 缺零填充 | `2025-7-8`、`2025-9` |
| 非法月/日 | `2025-13-08`、`2025-07-32`、`2025-02-30` |
| 占位符 | `待定`、`TBD`、`未知`、空字符串 |

周数字段也会按半概率注入 `待定` / `N/A` / `-1` / 全角 `０` 等值。

## 枚举覆盖

| 维度 | 覆盖值 |
|------|--------|
| 完成情况 | 全部 7 种（按时完成/正常待完成/超期已完成已变更/超期已完成未变更/不正常待变更/超期未完成未变更/超期未完成已变更） |
| 节点类型 | 一般节点/重要节点/重大节点/里程碑节点 |
| 风险等级 | 高/中/低 |
| 更改类别 | I/II/III |
| 原因分类 | 设计/工艺/管理/元器件/操作/外协外购/软件/其他 |
| 闭环状态 | 已闭环/空（未闭环） |
| 风险状态 | 已释放/跟踪中/待处理 |

## 测试流程

```bash
# 1. 通过 Addax 将 Excel 导入 ODS 表
#    配置 Addax reader=excelreader, writer=postgresqlwriter
#    每个 Excel 文件只有 1 个 sheet，对应 1 张 ODS 表

# 2. 执行 dbt 构建
docker run --rm --privileged --network dts-core \
  -v /opt/prod/s10-stack/services/dts-dbt:/opt/dbt \
  dts-dbt:1.10.0 build --project-dir /opt/dbt --target dev --threads 1

# 3. 验证各层行数
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -c "
  SELECT 'ODS' as layer, count(*) FROM ods_project_subject_domain_v2
  UNION ALL SELECT 'DWD', count(*) FROM biz_dwd_project_node
  UNION ALL SELECT 'DWS', count(*) FROM biz_dws_period_node_summary
  UNION ALL SELECT 'ADS', count(*) FROM biz_ads_project_kpi_overview;"
```

## 其他文件

| 文件 | 用途 |
|------|------|
| gen_excel.py | 生成脚本（纯标准库，无依赖） |
| load_all.sh | SQL INSERT 方式直灌（跳过 ETL，调试用） |
| 0*.sql | 各表 INSERT 语句（与 Excel 数据一致） |
