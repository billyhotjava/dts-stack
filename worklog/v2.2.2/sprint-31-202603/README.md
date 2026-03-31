# Sprint-31: v2.2.2 Code Review 修复

**时间**: 2026-03
**状态**: READY
**目标**: 修复 v2.2.2 分支全模块 code review 发现的 Critical 和 Important 问题

## 背景

v2.2.2 分支经过多轮重构（analytics 嵌入、权限体系、会话管理、CSS 统一、数据管理重构），累计 2500+ 文件变更。
四模块并行 code review 发现 9 个 Critical、11 个 Important 问题，需在合并前修复。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | Platform 前端 Critical 修复 | 3 | READY |
| F2 | Analytics 后端 Critical 修复 | 3 | READY |
| F3 | Platform 后端 Critical 修复 | 3 | READY |
| F4 | Important 问题修复 | 6 | READY |

## 完成标准

- [ ] App.tsx QueryClient 不再每次渲染重建
- [ ] userStore 登出时正确清理所有 session 数据
- [ ] ScreenResource.java 全量 Optional.get() → orElseThrow()
- [ ] ScreenResourceIT.java 编译通过，测试用例更新
- [ ] AssetPermissionAudit.detail columnDefinition 兼容 PostgreSQL
- [ ] Liquibase 冗余建表/删表清理
- [ ] ServiceDependencyAuthenticationFilter 安全加固
- [ ] vite build 无错误
- [ ] mvn compile 无错误
