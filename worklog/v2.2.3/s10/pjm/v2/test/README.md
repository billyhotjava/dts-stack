# ODS v2 测试数据

## 数据设计

- **项目**: 2 个主项目 (PJ-2025-001 卫星导航系统, PJ-2025-002 深空探测器)
- **分系统**: 结构/电子/软件/热控
- **时间跨度**: 2025-01 ~ 2025-12 (覆盖多月聚合)
- **数据量**: 每张 ODS 表 8~15 行，合计约 90 行

## 覆盖场景

| 维度 | 覆盖值 |
|------|--------|
| completion_status | 全部 7 种 |
| node_type | 一般/重要/重大/里程碑 |
| risk_level | 高/中/低 |
| change_category | I/II/III |
| issue_category | 设计/工艺/管理/元器件/操作/外协外购/软件/其他 |
| closure_status | 已闭环 / NULL(未闭环) |
| risk_status | 已释放 / 跟踪中 / 待处理 |

## 文件清单

| 文件 | 表 | 行数 |
|------|----|------|
| 00_project_subject_domain.sql | ods_project_subject_domain_v2 | 15 |
| 01_progress_measure.sql | ods_progress_measure_v2 | 10 |
| 02_quality_issue.sql | ods_quality_issue_v2 | 10 |
| 03_quality_measure.sql | ods_quality_measure_v2 | 10 |
| 04_tech_state.sql | ods_tech_state_v2 | 8 |
| 05_tech_state_measure.sql | ods_tech_state_measure_v2 | 8 |
| 06_risk_info.sql | ods_risk_info_v2 | 10 |
| 07_risk_measure.sql | ods_risk_measure_v2 | 10 |
| 08_material_info.sql | ods_material_info_v2 | 10 |

## 使用方式

```bash
# 1. 先执行建表 DDL
docker cp ../ods/ods_create_tables_v2.sql s10-stack_dts-pg_1:/tmp/
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -f /tmp/ods_create_tables_v2.sql

# 2. 灌入测试数据
for f in /opt/prod/s10/s10-stack/worklog/v2.2.3/s10/pjm/v2/test/0*.sql; do
  docker cp "$f" s10-stack_dts-pg_1:/tmp/
  docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -f "/tmp/$(basename $f)"
done

# 3. 验证行数
docker exec s10-stack_dts-pg_1 psql -U biadmin -d biadmin -c \
  "SELECT 'project_subject_domain_v2' as t, count(*) FROM ods_project_subject_domain_v2
   UNION ALL SELECT 'progress_measure_v2', count(*) FROM ods_progress_measure_v2
   UNION ALL SELECT 'quality_issue_v2', count(*) FROM ods_quality_issue_v2
   UNION ALL SELECT 'quality_measure_v2', count(*) FROM ods_quality_measure_v2
   UNION ALL SELECT 'tech_state_v2', count(*) FROM ods_tech_state_v2
   UNION ALL SELECT 'tech_state_measure_v2', count(*) FROM ods_tech_state_measure_v2
   UNION ALL SELECT 'risk_info_v2', count(*) FROM ods_risk_info_v2
   UNION ALL SELECT 'risk_measure_v2', count(*) FROM ods_risk_measure_v2
   UNION ALL SELECT 'material_info_v2', count(*) FROM ods_material_info_v2;"
```
