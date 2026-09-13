# T01: API 缺口实测与登记

**优先级**: P0  
**状态**: DONE  
**依赖**: F0

## 目标

逐项确认现有 API 是否足够，只有真实缺口才补后端。

## 技术设计

- 从 `platformApi.ts` 和后端 Resource 找候选接口。
- 用真实响应或测试 fixture 确认字段。
- 更新 `assets/api-gap-register.md` 状态：TO_VERIFY / COVERED / NEED_BACKEND。

## 影响范围

- API 缺口登记表
- 后续 backend task 拆分

## 验证

- [ ] 每个 NEED_BACKEND 项都有页面、字段和失败态。
- [ ] COVERED 项有现有接口路径。

## 完成标准

- [ ] 后端补充从页面倒推，而不是从抽象域模型倒推。
