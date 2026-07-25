# F1：模型包契约与转换器

**优先级**: P0  
**状态**: IN_PROGRESS

## 目标

定义版本化 DTS 模型包并从现有 dbt 产物稳定生成候选，不重复建设 dbt parser，不猜测缺失业务语义。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|----|------|--------|------|------|
| T01 | 定义模型包 JSON Schema 与校验和 | P0 | DONE | - |
| T02 | 实现 dbt 产物到模型包转换器 | P0 | DONE | T01 |
| T03 | 实现转换能力分类与技术节点保留 | P0 | DONE | T01/T02 |
| T04 | 建立 PJM 黄金模型包夹具 | P0 | DONE | T02/T03 |
| T05 | 接收 dbt ZIP 并转换为内部模型包 | P0 | IN_PROGRESS | T01/T02 |

## 完成标准

- [ ] 包契约可版本化、可校验、可幂等比较。
- [ ] manifest/catalog/schema/meta 的真值边界明确。
- [ ] STG/ephemeral 不创建 ModelSpec，但依赖不丢失。
- [ ] PJM 黄金包可重复生成且 checksum 稳定。
- [ ] 页面上传 artifact/legacy dbt ZIP 后由服务端无副作用转换，JSON 不再是用户输入。
