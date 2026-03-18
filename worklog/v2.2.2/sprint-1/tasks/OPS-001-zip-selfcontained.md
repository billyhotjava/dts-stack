# OPS-001: dbt 模型包自包含验证

- **优先级**: P1
- **状态**: TODO
- **负责人**: TBD

## 验证流程

1. `bin/dts-reset --force` 清空 dbt/airflow
2. 通过 UI 导入 `project-management-ui-import.zip`
3. 上传测试 Excel
4. 点"上线"
5. 验证 21 张表 + 项目看板数据

## 检查点

- [ ] zip 内无 seed 文件
- [ ] pm_ods_sources.yml 不与平台自动生成的 source 冲突
- [ ] 4 个 dim 模型均从 ODS 自动推导
- [ ] schema.yml 所有测试 severity: warn
