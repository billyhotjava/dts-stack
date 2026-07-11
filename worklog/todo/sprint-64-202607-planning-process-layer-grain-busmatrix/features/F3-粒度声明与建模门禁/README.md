# F3: 粒度声明与建模门禁

**优先级**: P0
**状态**: READY

## 目标
模型候选必须声明"一行代表什么"，防止汇总/明细粒度混建。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | grain契约与草稿承载 | P0 | READY | - |
| T02 | 建模页grain填写与门禁 | P0 | READY | T01 |
| T03 | 模型摘要与验收包展示grain | P1 | READY | T02 |

## 完成标准
- [ ] GrainDeclaration（statement + grainKeys≥1）随模型草稿保存。
- [ ] 未声明 grain 不能生成维度候选，门禁给填写入口。
- [ ] 模型草稿卡与验收包 markdown 展示粒度。
