# F0：架构基线与产品决策冻结

**优先级**：P0  
**状态**：DONE_EVIDENCE（T05 已登记认证 derivative；生产激活待 F6）

## 目标

在不写产品代码的前提下，冻结所有权、可视化范围、输入兼容矩阵、验收路径和精确契约，使 F1～F6 不需要边编码边决定架构。

## 输出契约

- `assets/decision-register.md`：D01～D13 全部有确认结论；D09 的 policy acceptance 与 runtime certification evidence 分开记录。
- `assets/dbt-runtime-hotfix-prerequisite.md`：H83-01 紧急运行时修复入口，产生不可变候选与原始 RT-01 证据；T05 是唯一认证登记 owner，仅阻断 materialization 切片。
- `assets/domain-profile.md`：工程准入、编码后回归和客户兼容声明三类证据边界；T02 的 FX 与 T05 的 RT-01 分开归档。
- `it/baseline.md`：P1～P8B 的分切片真实探针结果；P4B/P8B 不反向阻断 83a。
- Sprint README 的端到端 API/DTO/错误码从提案升级为 FROZEN。

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 |
|---|---|---|---|---|
| T01 | 冻结事实所有权与可视化能力等级 | P0 | DONE | - |
| T02 | 建立分级工程 fixtures 与 parser 兼容画像 | P0 | DONE（83a/83b） | T01 |
| T03 | 验证交付与验收基线 | P0 | DONE（PASS_WITH_GAPS） | T02 |
| T04 | 冻结当前切片契约并完成 DoR 评审 | P0 | DONE（83a） | T01～T03 |
| T05 | 消费 H83-01 并认证 materialization runtime | P0 Gate（仅 S3） | DONE_EVIDENCE | H83-01、T01 |

## Definition of Ready

- [x] 用户确认 D01～D05、D07、D08、D12。
- [x] 用户确认 D09/D10，并接受 D06/D11 延期；D09 的 parser/fixture 画像由 T02 负责；H83-01 产生 runtime 候选/原始证据，T05 唯一登记 materialization 认证，且只阻断 S3。
- [x] 用户确认 D13：inspect → preview 使用 30 分钟 `inspectionProof`，只允许白名单用户输入，不新增 inspect 表或独立 secret。
- [x] 当前 P0 切片使用的工程 fixture 有生成说明、离线副本与 SHA-256；不要求先取得客户脱敏包。
- [x] 当前共享工作树中的数据质量等并行改动已登记为非本 Sprint 所有，不回退、不暂存、不吸收。

## 完成标准

- [x] 83a P0 竖切片的输入、输出、错误路径、UI 落点和验证命令无 TBD；P1/P2 保持具名 DRAFT，不阻断 P0。
- [x] 83a `G1-PARSER/CONTRACT` 为 PASS；客户兼容声明 GAP 有独立证据责任人，不参与编码准入。只有 S3 materialization 还要求 T05 的 `G0-RUNTIME` PASS。
