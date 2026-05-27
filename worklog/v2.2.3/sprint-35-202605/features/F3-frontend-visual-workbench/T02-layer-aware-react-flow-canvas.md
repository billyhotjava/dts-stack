# T02: React Flow 分层画布节点模型

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

让 React Flow 画布的节点、边和属性面板都携带数据层语义，避免 DWD、DWS、ADS 在 UI 中混成普通表。

## 技术设计

- 节点类型：`sourceAsset`、`businessObject`、`join`、`dimension`、`metric`、`filter`、`model`、`validation`、`publish`。
- `sourceAsset` 节点显示 `warehouseLayer`、grain、governance、permission、lineage。
- `model` 节点区分 `DWS_CANDIDATE` 和 `ADS_CANDIDATE`。
- 边类型包含 `uses_asset`、`joins_to`、`selects_field`、`depends_on_metric`、`materializes_to`、`publishes_to`。
- 画布保存时写入 `sourceAssetKey` 和 `warehouseLayer`。

## 影响范围

- `source/dts-metrics-webapp/src/features/semantic/SemanticModelCanvas.tsx`
- `source/dts-metrics-webapp/src/features/semantic/semanticCanvas.helpers.ts`
- `source/dts-metrics-webapp/src/styles.css`

## 验证

- [ ] React Flow smoke 能看到 layer badge。
- [ ] 保存/加载后节点层级不丢失。
- [ ] DWD 节点无法直接连接 publish 节点。

## 完成标准

- [ ] 画布自身表达 ELT 分层，不需要靠说明文字解释。
