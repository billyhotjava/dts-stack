# T03: 模型摘要与验收包展示 grain

**优先级**: P1
**状态**: DONE
**依赖**: T02

## 目标

粒度成为模型对外的第一句自我介绍。

## 技术设计

- 模型草稿卡显示"粒度：{statement}（键：...）"。
- 验收包 markdown 模型组补 grain 行；gateEvidence compile 项 detail 带粒度摘要。

## 影响范围

- 模型草稿摘要渲染处 + `dataProductAcceptancePackage.ts` + 测试

## 验证

- [x] markdown 含粒度；无 grain 的旧草稿显示"未声明"不报错。
