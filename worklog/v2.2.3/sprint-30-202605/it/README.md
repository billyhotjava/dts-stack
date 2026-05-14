# Sprint-30 集成测试

## 目标

验证正式版 CSV 训练快照主链路：

```text
运营商 CSV -> DTS ODS -> dbt 地铁模型 -> DTS CSV snapshot package -> metro-stack 训练
```

## 计划证据

| 证据 | 路径 | 状态 |
|---|---|---|
| CSV 上传/预检结果 | `it/evidence/csv-precheck/` | READY |
| dbt parse/run 输出 | `it/evidence/dbt-run/` | PARTIAL |
| snapshot package 文件清单 | `it/evidence/snapshot-package/` | READY |
| metro-stack 契约校验结果 | `it/evidence/metro-contract/` | PARTIAL |
| 训练任务与产物摘要 | `it/evidence/metro-training/` | READY |

## 验收命令草案

```bash
dbt parse --project-dir worklog/v2.2.3/thales/v1/dbt_model --profiles-dir services/dts-dbt/profiles
```

```bash
curl -sS -X POST http://127.0.0.1:18082/api/ml/training-snapshots/export \
  -H 'Content-Type: application/json' \
  -d '{"domain":"metro","format":"csv","modelName":"metro_dwd_lstm_training_snapshot"}'
```

```bash
curl -sS -X POST http://127.0.0.1:50080/api/ml/data-contract/validate \
  -H 'Content-Type: application/json' \
  -d '{"data_dir":"/opt/dts/training-snapshots/metro/metro-lstm-20260514-001"}'
```

## 当前状态

已完成单模块校验，尚未执行 DTS 导出服务和完整端到端。

- 2026-05-14：`dbt parse --project-dir worklog/v2.2.3/thales/v1/dbt_model --profiles-dir services/dts-dbt/profiles` 通过。
- 2026-05-14：`unzip -t worklog/v2.2.3/thales/v1/thales-metro-dbt-model.zip` 通过。
- 2026-05-14：metro-stack 契约测试 `PYTHONPATH=backend/src .venv/bin/python -m unittest discover -s backend/tests -p 'test_contract_*.py'` 通过，7 tests OK。
