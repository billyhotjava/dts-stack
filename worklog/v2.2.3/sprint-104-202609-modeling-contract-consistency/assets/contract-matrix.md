# 保存、治理与执行契约决策

2026-09-06 修订；这是整改目标，不是测试或现场验收证据。

| 操作 | 规则 | 责任 |
|---|---|---|
| 交互创建 | 保留现有 interactive create 的最小身份检查；不改为完整发布校验 | T02 |
| PUT 模型 / 可编辑视图 | 与 Java validateUpdate/validateEditableView 对齐：允许缺少稳定业务上下文；已填写的非法 UUID/类型仍拒绝，维度定义由原有阶段约束控制 | T02 |
| 创作草稿暂存 | 保留完整 snapshot、CAS 和合法类型，不套用发布治理条件 | T02/T05 |
| 草稿 commit | 创建模型/实现修订，复用既有提交校验和双版本控制；不代表执行成功 | T02/T05 |
| DESIGNED / IMPLEMENTATION_READY / RELEASE_READY | 保留既有阶段完整性、来源、版本与合规边界；稳定上下文在需要它的阶段阻断。对每个阶段按现有 StageGate 契约补正反例，不能把仅 Schema 通过当作阶段通过 | T02 |
| 编译和执行 | grain.keys 与 fields.KEY 的集合必须相同，键不得重复或引用不存在字段；不一致草稿可编辑，执行必须拒绝 | T04 |
| 发布标准覆盖 | R=必须绑定字段集合；B=全部已声明绑定字段。R 检查缺失，B 检查专业证据。NONE 仅令 R 为空；B 为空不依赖证据服务，B 非空失效或未知时发布阻断 | T03 |
| TYPE2 配置 | 可编辑/暂存/恢复/提交元数据；不因此承诺历史执行可用 | T05/T07 |
| SNAPSHOT / 历史维护 | 沿用当前实际能力；执行不支持时在物化前拒绝。不得封锁 TYPE2 元数据编辑作为替代 | T07 |

## 当前值与清空

- 当前实现 settings 的自有字段存在时优先；loadStrategy=FULL 与 partitionFields=[] 都是显式值。仅缺少字段时才从旧 implementationPolicy 兼容读取。
- loadStrategy 的 null/空串/未知枚举不等于缺少字段，不能回退成旧有效策略；由现有实现校验拒绝。partitionFields 显式 [] 表示清空，null/错误类型不得自动恢复旧分区。
- 普通可视化与 DBT_MANAGED 保留快照路径都要验证；不为 DBT_MANAGED 新增写 owner。
- 维度 profile 的缺失、显式 null 与完整对象分别处理；完整对象中的空层级数组不得回填旧层级。TYPE2→NONE 只清除不适用的配置绑定，不删除业务字段或物理列。
- DTO 更新沿用既有完整替换约定和 CAS；不新增 PATCH 或自行发明版本头。

## 验收边界

前端/Java/Schema 的结构检查与阶段完整性分别比较：Schema 不读取专业服务或数据库，不能要求原始 JSON Schema 单独判断引用当前有效性。共享样例应标注 operation、阶段和预期错误码；专业证据与阶段组合由服务测试证明。源码子项可独立交付，当前部署/浏览器/离线验收未执行则保持未验证。
