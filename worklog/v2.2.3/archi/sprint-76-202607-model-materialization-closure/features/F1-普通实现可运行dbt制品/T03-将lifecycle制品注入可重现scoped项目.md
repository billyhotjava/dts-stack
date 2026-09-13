# T03：将 lifecycle 制品注入可重现 scoped dbt project

**优先级**：P0
**状态**：DONE
**依赖**：T02

## 目标

让 ReleaseCandidate 的普通模型 artifact 与既有 dbt workspace 的 sources/macros/dependencies 合并成临时可运行项目，不创建平行 `ModelingSqlModel` 记录。

## 技术设计（Contract-first）

- **输入契约**：`CandidateBuildRequest.entries[]` 与每条 current SQL/schema artifact。
- **输出契约**：`ScopedCandidateProject(projectDir,selector,bundleChecksum,entries[])`；projectDir 仍走现有 host/container 映射。
- **数据流**：artifact overlay → 依赖扫描 → 复制既有 root config/macros/packages/sources → scoped dir → checksum。
- **冲突规则**：overlay node 与 workspace node 同名且 checksum 不同，返回 `MATERIALIZATION_NODE_CONFLICT`；同 checksum 幂等复用。
- **资源限制**：单文件 5 MiB、bundle 100 MiB、候选 100 entries；超限=422。
- **清理**：沿用 scoped project 24h/MAX 30 清理；active run 目录不得被清理。
- **复用点**：`DbtScopedProjectService`、dbt project path translation、现有 ref/source dependency parser。

## 影响范围

- `DbtScopedProjectService.java`
- 新的 candidate overlay DTO/测试
- 不修改 `ModelingSqlModel` canonical 语义

## 验证（RED→GREEN）

- [x] 普通 artifact 当前无法被 prepare(selector) 找到的失败测试。
- [x] overlay 与已有 source/ref 依赖一起 `dbt compile` 通过。
- [x] 冲突、缺 artifact、stale checksum、超限稳定失败。
- [x] 重复 prepare 得到相同 bundleChecksum。

## Definition of Done

- [x] scoped project 是唯一执行投影。
- [x] 运行所需文件完整且无工作区污染。
- [x] 清理策略不会删除 active candidate 项目。

## 完成证据

- `prepareCandidate(entries)` 只消费 lifecycle artifact，不创建或更新
  `ModelingSqlModel`。
- scoped project 合并既有 sources、macros、packages 与被引用 workspace node；相同输入
  幂等复用，不同依赖内容改变 bundle checksum。
- 路径穿越、重复 model/selector、节点冲突、stale/missing/oversized artifact 和循环依赖
  均 fail-closed。
- dbt `target-path` 与 `log-path` 已在真实测试中移出 immutable candidate project，并以
  平台 UID 运行，证明运行产物可清理；生产 task-scoped runtime/profile lease 仍由 F2/T04
  落实。
- RED/GREEN 与清理证据见 `../../it/evidence/f1-t03-scoped-dbt-project/README.md`。
