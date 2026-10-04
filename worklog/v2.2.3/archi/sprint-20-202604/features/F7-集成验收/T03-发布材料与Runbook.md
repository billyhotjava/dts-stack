# T03: 发布材料与 Runbook

**优先级**: P1
**状态**: READY
**依赖**: T01

## 目标

整理上线前所需的全部文档：用户文档、运维 Runbook、回滚步骤、配置说明。

## 交付物

### 1. 用户文档

`docs/user/lineage-visualization.md`：

- LineagePage 入口与基本使用
- 过滤器使用说明（方向、深度、层级、变更时间、项目）
- 列级 toggle 说明与性能注意
- 影响范围高亮使用
- 导出 PNG/SVG/CSV
- 时间旅行 / Diff 模式
- 常见问题 FAQ

### 2. Runbook（运维）

`worklog/v2.2.3/sprint-20-202604/it/runbook.md`：

#### 2.1 上线步骤

```
□ 步骤 1: 备份 catalog_dataset_lineage 表
□ 步骤 2: 滚动升级 dts-platform，liquibase 自动跑迁移
□ 步骤 3: 验证迁移成功（行数对齐、字段非空检查）
□ 步骤 4: 升级 dts-ingestion，开启 dts.lineage.platform-callback.enabled
□ 步骤 5: 升级 Airflow worker，安装 openlineage provider
□ 步骤 6: 上线前端
□ 步骤 7: 跑 F1.T04 backfill runner（dry-run 后实跑）
□ 步骤 8: 端到端冒烟（按 F7.T01 步骤）
```

#### 2.2 配置清单

新增配置项汇总：

| 配置项 | 默认 | 说明 |
|--------|------|------|
| `DTS_LINEAGE_PLATFORM_CALLBACK_ENABLED` | true | 入口总开关 |
| `DTS_PLATFORM_BASE_URL` | http://dts-platform:8080 | 回写目标 |
| `DTS_AIRFLOW_OPENLINEAGE_ENABLED` | true | DAG 注入 inlets/outlets |
| `DTS_OPENLINEAGE_URL` | http://dts-platform:8080/api/internal/lineage/openlineage | OL 接收端 |
| `DTS_OPENLINEAGE_INTERNAL_TOKEN` | (必填) | 内部鉴权 |
| `DTS_COLUMN_LINEAGE_ENABLED` | true | 列级解析开关 |
| `DTS_COLUMN_LINEAGE_MAX_COLS` | 200 | 单 model 列数硬上限 |
| `DTS_LINEAGE_OM_PIPELINE_SYNC_ENABLED` | true | OM Pipeline 同步开关 |

#### 2.3 故障排查

| 现象 | 排查 |
|------|------|
| LineagePage 看不到 Addax 边 | 1. 检查 `dts.lineage.platform-callback.enabled=true`；2. 检查 `IngestionExecution.lineage_synced_at` 是否为空；3. 检查 platform 接口日志 |
| OpenLineage 事件没回到 | 1. Airflow worker 看 OL provider 启动日志；2. 检查 `DTS_OPENLINEAGE_URL` 网络连通；3. 检查 token 是否正确 |
| 列级血缘缺失 | 1. 确认 dbt 已 compile（manifest 含 compiled_sql）；2. 查 `dts-platform` 日志找 SqlColumnLineageExtractor 失败记录 |
| 前端图节点重叠 | 1. 确认 dagre 加载成功；2. 浏览器 console 看 layout 报错 |
| 时间旅行查询慢 | 1. 检查 `idx_lineage_validity` 索引存在；2. 检查归档任务跑过 |

#### 2.4 回滚步骤

```
□ 关闭 DTS_LINEAGE_PLATFORM_CALLBACK_ENABLED=false
□ 关闭 DTS_AIRFLOW_OPENLINEAGE_ENABLED=false
□ 前端回滚到上版本
□ DB 不回滚（SCD2 字段已加，但不影响旧逻辑读取）
□ MANUAL/DBT/AUTO_VIEW 边继续保留
```

### 3. 发布说明

`worklog/v2.2.3/sprint-20-202604/RELEASE_NOTES.md`：

- 用户视角变化
- 数据工程师视角变化
- DBA 视角变化（新表、新字段、新归档）
- 不兼容变化（如有）
- 已知问题与下个 Sprint 跟进

### 4. 培训材料

短视频脚本（5 分钟）：

- 0:00 介绍：从"看不到"到"看得清"
- 1:00 用例 1：列级影响分析
- 2:30 用例 2：变更对比
- 4:00 总结

## 影响范围

- 4 个文档文件
- 不涉及代码

## 验证

- [ ] 文档评审：至少 1 名运维 + 1 名数据工程师 review 通过
- [ ] Runbook 上线步骤被独立工程师按文档跑通（dev 环境演练）
- [ ] 回滚步骤在 dev 环境实际验证一次

## 完成标准

- [ ] 4 个交付物完整
- [ ] 评审通过
- [ ] 上线 Runbook 实跑一次
