# F1：新建用户初始口令安全治理

**优先级**：P0
**状态**：READY

## 目标

新建 DTS 用户的初始口令由系统一次性随机生成并强制首登修改，任何常量口令从代码与交付物中消失；授权管理员只能在审批执行结果中一次性看到明文。

## 契约定义（Contracts）

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| Service | `KeycloakAdminClient.resetPassword(userId, password, temporary, accessToken)` | 复用既有签名（账本#3）；创建路径改为 `temporary=true` |
| API | 审批 applyCreate 执行结果 detail | `initialPasswordDelivered: boolean`（替代 `defaultPasswordApplied`）；一次性口令仅存在于执行响应内存，不序列化到持久层 |
| 审计 | `USER_CREATE` / 口令交付 | actor、action、resource、result、client metadata、timestamp；**不含口令明文** |
| 运维 | 存量处置工具 | 输入：用户范围（username 列表或过滤条件）；动作：批量设置 `UPDATE_PASSWORD` required action；幂等、逐用户记录结果 |

## UI/UX 规格（面向用户部分）

- **入口**：dts-admin-webapp 用户管理 → 创建用户审批 → 审批执行结果。
- **四态**：空（不适用）；加载（审批执行中，既有样式）；错误（Keycloak 设密失败 → 创建整体失败并展示原因，账本#2 既有行为）；成功（展示用户名 + 一次性初始口令 + "已强制首登改密"提示 + 复制按钮，关闭后不可再次查看）。
- **关键交互**：执行审批 → 后端返回一次性口令（仅内存）→ 前端弹窗展示并要求"已复制"确认 → 审计仅记录 delivered=true。
- **操作走查**：1. authadmin 提交创建用户 → 2. 审批通过并执行 → 3. 结果弹窗显示一次性初始口令 → 4. 复制并确认关闭 → 5. 新用户首登被 Keycloak 强制改密。
- **可访问性/兼容**：口令区块支持复制按钮与手动全选；Chrome95 可用。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 初始口令随机化与强制改密（后端） | P0 | READY | - |
| T02 | 审批结果一次性口令交付与前端适配 | P0 | READY | T01 |
| T03 | 存量弱口令风险账号处置工具 | P0 | READY | T01 |

## Definition of Ready

- [x] 契约已钉死（resetPassword 复用既有签名；detail 字段命名确定）
- [x] 竖切片已画通（审批执行 → Keycloak 设密 → 一次性展示 → 首登强制改密）
- [x] UI 落点已命名（审批执行结果弹窗）
- [x] 依赖已就绪（无外部依赖；SecureRandom 先例见账本#5）
- [x] 验收可验证（IT-01/IT-02）

## 完成标准

- [ ] 仓库无 `DEFAULT_INITIAL_PASSWORD` 残留；新建用户 `temporary=true`（IT-01）
- [ ] 一次性口令不落库、不进日志/审计明细（IT-01/IT-02，含拒绝路径测试）
- [ ] 存量处置工具幂等、默认不执行、动作可审计（IT-02）
