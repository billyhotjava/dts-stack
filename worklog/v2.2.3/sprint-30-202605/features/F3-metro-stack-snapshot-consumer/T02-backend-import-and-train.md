# T02: 后端导入 snapshot package 并触发训练

**优先级**: P0  
**状态**: READY  
**依赖**: T01, F2

## 目标

metro-stack 后端新增从 DTS snapshot package 创建训练任务的正式 API。

## 技术设计

建议 API：

```http
POST /api/batches/from-training-snapshot
Content-Type: application/json

{
  "snapshot_dir": "/opt/dts/training-snapshots/metro/metro-lstm-20260514-001"
}
```

行为：

- 校验 snapshot contract。
- 记录 `source_type=training_snapshot`。
- 保存 `snapshot_dir`、`snapshot_id`、`data_csv`。
- 复用现有 batch recompute 流程，训练脚本使用 `data.csv`。

## 影响范围

- `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/main.py`
- `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_pack_service/batch_runner.py`
- `/opt/prod/metro-app/sources/metro-stack/backend/src/metro_mlops/datasets/build_lstm_ae_dataset.py`

## 验证

- [ ] 后端单测创建 training snapshot batch。
- [ ] 本地样例 snapshot 可进入 recompute。

## 完成标准

- [ ] metro-stack 不要求用户上传原始 CSV。
- [ ] 训练输入来自 DTS 治理后的 `data.csv`。
