# T01: 页面能力审计 skill

**优先级**: P0
**状态**: DONE
**依赖**: 无

## 目标

创建 `dts-page-capability-audit`，用于从现有前端页面、菜单、路由、组件和接口反推产品能力矩阵。

## 技术设计

Skill 固化以下规则：

- 以 `portal-menu-seed.json`、静态路由、动态路由和页面组件为事实源
- 不从后台模块直接倒推页面
- 不默认新增菜单或页面
- 按页面提取用户、能力、按钮、组件、接口和风险
- 标记 `REAL`、`PARTIAL`、`FAKE`、`DUPLICATE`、`ONSITE`

## 影响范围

- `/home/billy/.codex/skills/dts-page-capability-audit/SKILL.md`
- `/home/billy/.codex/skills/dts-page-capability-audit/agents/openai.yaml`

## 验证

- [x] `quick_validate.py /home/billy/.codex/skills/dts-page-capability-audit`

## 完成标准

- [x] skill 可被后续 DTS 页面审计任务复用
