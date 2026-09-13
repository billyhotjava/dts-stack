# T01: 后端快照导出 API 合约

**优先级**: P0  
**状态**: READY  
**依赖**: F1

## 目标

定义 DTS 平台侧训练快照导出 API，供 UI 和 metro-stack 对接。

## 技术设计

建议新增后端资源：

- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ml/TrainingSnapshotResource.java`
- `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/ml/TrainingSnapshotExportService.java`

API 草案：

```http
POST /api/ml/training-snapshots/export
Content-Type: application/json

{
  "domain": "metro",
  "modelName": "metro_dwd_lstm_training_snapshot",
  "contractModelName": "metro_dwd_lstm_training_contract",
  "snapshotId": "metro-lstm-20260514-001",
  "format": "csv",
  "limit": 100000
}
```

返回：

```json
{
  "snapshotId": "metro-lstm-20260514-001",
  "status": "SUCCEEDED",
  "outputDir": "/opt/dts/training-snapshots/metro/metro-lstm-20260514-001",
  "dataFile": "data.csv",
  "rowCount": 100000,
  "featureCount": 42,
  "contractVersion": "metro-lstm-contract-v1"
}
```

## 影响范围

- `source/dts-platform`
- 服务间权限：默认使用平台登录用户权限，不开放匿名访问。

## 验证

- [ ] 单测覆盖请求参数校验。
- [ ] API 返回路径不能跳出配置的 snapshot root。

## 完成标准

- [ ] API 合约稳定，metro-stack 可基于 `outputDir` 拉取或挂载读取。
