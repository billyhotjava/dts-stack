# T01: fixtures 样本库

**优先级**: P0
**状态**: READY

## 目标

建立一套最小但高价值的 Excel 兼容性样本库，建议至少包含：

- `negative-curly.xlsx`
- `negative-parentheses.xlsx`
- `fullwidth-minus.xlsx`
- `date-cn.xlsx`
- `formula-cached.xlsx`
- `merged-cells.xlsx`

## 完成标准

- [ ] 样本放入 `src/test/resources` 或 `worklog/.../it/fixtures`
- [ ] 每个样本有期望输出说明
- [ ] 新 bug 优先先补样本，再补逻辑
