# T03: 语义主题域动作补齐

**优先级**: P0
**状态**: DONE
**依赖**: F2

## 目标

补齐语义主题域 actionCode，避免 `SEMANTIC_SUBJECT_DOMAIN_*` 落入 General。

## 技术设计

- 在 DB catalog seed 中注册 `SEMANTIC_SUBJECT_DOMAIN_LIST/CREATE/UPDATE`。
- 对支撑 list 做降噪评估：普通列表可不记录，创建/修改必须记录。

## 影响范围

- `SemanticModelingResource.java`
- dts-admin DB action seed

## 验证

- [x] 新建语义主题域显示“新增语义主题域”。
- [x] 修改语义主题域显示“修改语义主题域”。

## 完成标准

- [x] 语义主题域动作不再显示 General。
