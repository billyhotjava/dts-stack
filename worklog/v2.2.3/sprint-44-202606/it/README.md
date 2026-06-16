# Sprint-44 集成测试计划（F1 受控建模治理前端呈现）

## 目标
证明 SP-1 后端治理在平台原生语义建模页可见、可用、引导式，且 permissive 行为不变（绞杀者）。

## 证据
| 证据 | 落点 | 状态 |
|------|------|------|
| governanceMode 透传 + 列表标识 | T01 契约/组件测试 | READY |
| 422 unsafe_expression 引导式渲染（非裸码） | T02 错误码渲染测试 | READY |
| 400 分层码 → 可读诊断 + 指引 | T03 错误码渲染测试 | READY |
| 受控/permissive 视觉区分 | T04 组件/契约测试 | READY |

## 验收命令
```bash
cd source/dts-platform-webapp
pnpm test   # 或项目既有前端测试入口（vitest/node:test）
pnpm build  # 类型检查 + 生产构建
```

## 阻断条件（任一触发即不可 DONE）
- governanceMode 未透传到创建/更新请求（前端未贯通）。
- 422/400 仍裸码/裸 toast，无引导文案（呈现未达"可理解"目标）。
- permissive 模型前端行为被改变（绞杀者破坏）。
- 受控模式无视觉区分（用户无法辨识治理态）。

## 手测要点（现场）
- 新建受控模型 → 写原始 SQL 公式 → 应内联提示白名单引导。
- 受控模型设 ODS 输出 / 无 grain DWD → 提交评审 → 应可读分层诊断。
- permissive 模型走原路径不受限。
