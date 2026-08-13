# F4: 工程级代码编辑体验

**优先级**: P1
**状态**: IN_PROGRESS（Monaco、诊断适配、冲突写锁与兼容构建完成；等待真实 Chrome 95/IT-05）

## 目标

把当前高级 dbt 工作区的普通文本框升级为可维护的多文件 Monaco 编辑体验，同时保留现有草稿、ETag、容量、安全和校验契约。

## 契约定义

| 类型 | 契约 | 关键字段/签名 |
|---|---|---|
| 组件 | `DbtCodeEditor` | `{path,content,readOnly,diagnostics,onChange,onSave}`；不包含执行/发布 API |
| 文件映射 | path → language | `.sql` 使用 SQL；`.yml/.yaml` 使用 YAML；其他文本使用 plaintext；Jinja 保留原文 |
| 草稿 | 既有 create/save/validate/commit | expected ETag、validatedChecksum、idempotencyKey 不变 |
| 诊断 | `DbtDraftValidation.diagnostics[]` | `{severity,code,path,modelUniqueId,message,line?,column?}`；有位置则写 Monaco marker，无位置则列表展示 |
| 容量 | 服务端既有边界 | 128 文件、单文件 2 MiB、总计 16 MiB；只挂载选中文件 editor model |
| 装载 | 路由级懒加载 | `React.lazy` + 动态 `import()`；建模页首屏包不得含 monaco（账本 #23、ADR-91-06） |
| Worker | 沿用主线程 | 不新增 `MonacoEnvironment`/`getWorker`；`@vitejs/plugin-legacy` 不转译 worker chunk |

## UI/UX 规格

- 左侧文件列表，右侧 Monaco；文件切换保留各文件 undo/view state。
- `Ctrl/Cmd+S` 调用“保存文件”，不得触发浏览器保存；不提供 `Ctrl+Enter` 执行 SQL。
- 底部统一动作：“保存文件 / 校验 / 提交实现”；提交成功后当前草稿只读，可创建新草稿。
- 校验结果点击后定位对应文件与行列；无行列诊断仍在列表完整显示。
- ETag 冲突显示“刷新并创建新草稿”，不自动覆盖远端。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 用共享 Monaco 能力替换 dbt 文本框 | P1 | IN_PROGRESS | F0/T01、F1/T02、F2/T03 |
| T02 | 收敛诊断定位、脏状态与并发冲突反馈 | P1 | IN_PROGRESS | T01 |

## Definition of Ready

- [x] 组件边界、语言映射和快捷键已定义
- [x] 明确不复制 SQL IDE 执行控制面
- [x] 容量、敏感文件和 ETag 契约保持不变
- [x] 装载方式与 worker 策略已定，并已进入 `assets/nfr-budget.md`
- [ ] F0 浏览器/build harness 通过

## 完成标准

- [ ] 128 文件边界、2 MiB 选中文件、文件切换和保存可用。
- [ ] 诊断可定位，冲突不覆盖，提交后状态准确。
- [ ] 建模页首屏包不含 monaco，`view=visual` 不加载编辑器 chunk。
- [ ] Chrome 95、source-contract、Vitest 与 build 通过。
