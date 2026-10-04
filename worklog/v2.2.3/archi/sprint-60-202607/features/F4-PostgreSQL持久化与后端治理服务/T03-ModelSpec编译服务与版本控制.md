# T03: ModelSpec 编译服务与版本控制

**优先级**: P0
**状态**: READY
**依赖**: T01,T02

## 目标

实现确定性 ModelSpec 编译入口，生成 dbt 产物并保存 revision、checksum 和编译日志。

## 技术设计

- 同一 ModelSpec revision 必须生成相同 SQL 内容和 checksum。
- 编译失败只保存 `FAILED` artifact，不改变发布状态。
- 编译成功生成 SQL、schema、tests、docs 四类 artifact。
- revision 冲突不覆盖已提交版本，必须创建新 revision。

## 影响范围

- `SemanticModelingService` 或拆分后的 `ModelSpecCompilerService`。
- 模板资源、artifact repository、编译测试。

## 验证

- [ ] 相同 fixture 两次编译 checksum 一致。
- [ ] 无粒度、无标准绑定、非法 Join 编译失败。
- [ ] 生成物可被 dbt parse。

## 完成标准

- [ ] 编译结果可在模型详情和 API 中查询。
