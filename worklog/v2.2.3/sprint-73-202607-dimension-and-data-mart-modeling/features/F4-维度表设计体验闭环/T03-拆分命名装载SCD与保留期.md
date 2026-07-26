# T03: 拆分物理命名、装载、SCD 与保留期

**优先级**: P0  
**状态**: DRAFT  
**依赖**: T01

## 目标

把当前笼统“命名规则/历史保留”拆成四个可理解、可校验、可定位的实现策略。

## 技术设计 (Contract-first)

- **输入契约**: `physicalName/loadStrategy/retentionDays/partitionFields/scdPolicy`。
- **输出契约**: ModelSpec revision snapshot；命名 validate 响应；具体 blocker codes。
- **数据流**: UI 实现策略 → naming validate → ModelSpec update → stage gate。
- **错误路径**: 表名违规、SCD 字段缺失、分区字段不存在、retention 越界分别返回 422。
- **复用点**: plan `namingPolicyRef`、现有 `ModelSpecScdPolicy`、stage gate。
- **实现方案**:
  1. `historyPolicy` 仅兼容读取并映射默认 SCD，不物理删除；
  2. 物理名和逻辑 `name` 分离；
  3. retention 为 null 或 1..36000；
  4. 分区字段必须存在于 fields。

## UI 交互规格

- 四个独立表单区，每区有一句业务解释。
- 模型 revision 历史显示为系统信息，不提供“保留策略”输入。
- 每个 blocker 的“去修复”定位具体字段。

## 验证

- [ ] 0/1/36000/36001 边界
- [ ] TYPE2 三字段完整性
- [ ] 表名规则只检查 physicalName
- [ ] 旧 historyPolicy 兼容

## Definition of Done

- [ ] 用户可辨认四类策略
- [ ] 错误码和 UI 定位一一对应
- [ ] 证据入 `IT-05-policy-semantics.md`
