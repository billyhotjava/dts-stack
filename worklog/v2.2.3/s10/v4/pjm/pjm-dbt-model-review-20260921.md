# PJM 进度统计修复与导入包验证

- 源码提交：`46932747137932eb3bcd8aea714278bc2d0ce01f`
- 正式构建目录：`/data/dts-stack`；push 后通过 `git pull --ff-only` 同步并核对 SHA。
- 修复：进度月度汇总改用全外连接，保留仅有周期外完成记录的项目月份；计划类计数补 0，补齐年、季度、月份及项目编号。字段结构不变。
- 影响链：进度月度汇总 → 进度 KPI → 进度派生及综合派生指标。
- 包同步包含当前源码已有的 `project_total_cnt`、`project_active_cnt`、`project_delay_cnt`，字段总数由 1326 更新为 1329。

## 验证证据

- 旧 SQL 在定向样例中复现漏计，新 SQL 通过：仅完成月份、跨年、同月有计划记录、未完成节点、同月完成；验证无重复行、计数归零及完成数量守恒。
- 工具版本：dbt-core 1.10.22、dbt-postgres 1.9.1。
- 隔离验证库中创建 10 张空 ODS 源表，`dbt build` 成功：53 张表模型、10 个视图模型、135 个数据测试及 1 个初始化 hook；PASS=199、ERROR=0。
- `dbt docs generate` 成功，刷新 manifest 和 catalog。
- 使用 `dbt_model/scripts/build_import_zip.sh` 生成交付包；ZIP CRC、63 个模型 SQL 与 manifest 一致性、1329 个字段的目录名称及类型检查通过，未打入 profiles、日志、运行结果或密码。
- 构建日志保留在 `/data/dts-stack/.artifacts/pjm-repack-20260921/`；隔离验证库及临时连接配置已清理。
- ZIP SHA256：`65409a638337d84d79ba2fef6667b0551d058995b9d7970320978f5a748b305f`。

## 验收边界

本次验证使用隔离空源表及定向样例，不代表现场真实数据正确性。catalog 中的数据库/Schema 是隔离验证环境的记录，不是目标环境绑定。未部署容器、未执行页面逆向导入。导入仍需完成目标规划、维度定义和源资产映射；目标物化运行环境仍需具备 `public.dts_try_to_date(text)`。
