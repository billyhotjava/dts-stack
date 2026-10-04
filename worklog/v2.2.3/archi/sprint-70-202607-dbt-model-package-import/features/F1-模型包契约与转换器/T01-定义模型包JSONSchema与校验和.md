# T01：定义模型包 JSON Schema 与校验和

**优先级**: P0  
**状态**: DONE  
**依赖**: 无

## 目标

冻结 `dts.model-package/v1` 的结构、版本兼容、大小限制和内容校验和边界。

## 技术设计

- 定义 package、source、technicalNode、model、semantic override 和 conversion result。
- UUID 仅作为目标环境解析结果，不作为跨环境包真值。
- checksum 覆盖字段、配置、依赖、SQL checksum 和业务语义；规范化键顺序与空值。
- 未知 major version、重复 uniqueId、非法路径和超限包 fail closed。

## 影响范围

- `source/dts-platform` 模型导入契约与 JSON Schema 资源。
- 模型包生成工具和测试夹具。

## 验证

- [ ] 合法最小包和完整包通过 schema 校验。
- [ ] 未知字段、重复节点、错版本、错 checksum 和超限包被拒绝。
- [ ] 相同语义不同 JSON 键顺序产生相同 checksum。

## 完成标准

- [ ] Schema、示例和稳定错误码进入版本控制。
- [ ] 前后端使用同一 schemaVersion 与校验和定义。
