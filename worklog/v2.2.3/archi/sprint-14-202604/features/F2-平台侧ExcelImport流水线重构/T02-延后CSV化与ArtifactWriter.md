# T02: 延后 CSV 化与 ArtifactWriter

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标

把当前“读 Excel 时顺手写 CSV”的流程改成“先得到结构化 Artifact，再统一写 `data.csv` / `error.csv` / `manifest.json`”。

## 完成标准

- [ ] `data.csv` 仍存在，但来源改为 ArtifactWriter
- [ ] `error.csv` 保持兼容
- [ ] 增加 `manifest.json` 之类的内部辅助产物时，不影响既有前端接口
