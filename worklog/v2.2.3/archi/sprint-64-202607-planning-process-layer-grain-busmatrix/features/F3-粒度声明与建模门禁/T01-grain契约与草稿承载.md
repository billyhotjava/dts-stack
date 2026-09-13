# T01: grain 契约与草稿承载

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

定义粒度声明契约并挂到模型草稿 metadata。

## 技术设计

- GrainDeclaration 契约（design-notes 第 2 节）；校验纯函数 `isGrainDeclared`（statement 非空 + grainKeys≥1，键须属于草稿字段集）。
- 不进 URL，随模型草稿/planning session 存储。

## 影响范围

- 新增 grain helper（建议随 dimensionCandidateGate 同文件或毗邻）+ vitest

## 验证

- [x] 声明/未声明/键不在字段集三类用例。
