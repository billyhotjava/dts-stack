# T01: 资产层级导航与 DWS/ADS 默认入口

**优先级**: P0
**状态**: IN_PROGRESS
**依赖**: F2

## 目标

在 `dts-metrics-webapp` 中建立可视化资产导航，默认展示 DWS/ADS，隐藏 ODS/STG，并把 DWD 放入高级建模入口。

## 技术设计

- 资产列表已默认请求 `/api/metrics/visual-assets?layers=DWS,ADS`。
- 顶部提供层级筛选：DWS、ADS、高级 DWD；不提供 ODS/STG 拖拽入口。
- 每个资产展示 grain、time columns、dimension/metric counts、permission、governance、lineage。
- DWD 高级入口必须显示“生成 DWS 候选模型”而非“直接建看板”。

## 影响范围

- `source/dts-metrics-webapp/src/pages/semantic/**`
- `source/dts-metrics-webapp/src/features/semantic/**`
- `source/dts-metrics-webapp/src/api.ts`

## 验证

- [x] DWS/ADS 默认 tab 有真实 API 请求。
- [ ] DWD tab 必须显式高级模式。
- [x] ODS/STG 不出现在可拖拽资产列表。

## 完成标准

- [ ] 用户打开页面就能判断应从哪一层开始建模。
