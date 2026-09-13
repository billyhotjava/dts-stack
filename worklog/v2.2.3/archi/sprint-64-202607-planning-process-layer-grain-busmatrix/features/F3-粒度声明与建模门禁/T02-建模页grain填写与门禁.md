# T02: 建模页 grain 填写与门禁

**优先级**: P0
**状态**: DONE
**依赖**: T01

## 目标

低代码/SQL 建模页提供粒度填写，门禁未声明即阻断。

## 技术设计

- 生成候选前弹出/内嵌 grain 表单：粒度语句输入 + 从标准草稿字段多选 grainKeys。
- dimensionCandidateGate 新增 grain 检查项，missing 时修复动作=聚焦填写区。

## 影响范围

- `LowCodeDevelopmentPage.tsx` / `SqlModelingPage.tsx` + 门禁扩展 + 契约测试

## 验证

- [x] 契约覆盖表单存在、门禁阻断、填写后放行。
