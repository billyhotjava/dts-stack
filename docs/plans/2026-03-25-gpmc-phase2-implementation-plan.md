# GPMC Phase 2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build the formal GPMC Phase 2 information architecture and implementation baseline for `1` strategic screen, `5` control boards, read-only execution drill pages, and `6` editable screen templates.

**Architecture:** First normalize the 9 source tables into 5 theme domains and a shared indicator matrix. Then define screen-oriented backend view models and align the frontend business pages with editable `screens` templates that use the same semantic layer.

**Tech Stack:** TypeScript/React, Java/Spring Boot, screen template spec v2, Excel field dictionaries

---

### Task 1: Freeze source-table semantics and indicator ownership

**Files:**
- Create: `worklog/v2.2.2/sprint-14-202603/features/F1-源表语义与指标口径建模/T01-九张源表字段归类与关联主键.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F1-源表语义与指标口径建模/T02-指标口径矩阵与口径来源映射.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F1-源表语义与指标口径建模/T03-五个主题域视图模型定义.md`

**Step 1: Build source-table mapping**

Write a matrix that maps each of the 9 source tables to:
- owning theme domain
- primary key
- secondary key
- screen usage

**Step 2: Build indicator matrix**

For each strategic KPI and control-board KPI, define:
- source table(s)
- grouping grain
- filter dimensions
- calculation logic reference

**Step 3: Define theme-domain view models**

Define the backend response shapes for:
- strategic overview
- execution board
- quality board
- tech state board
- cost board
- risk board
- read-only drill detail

### Task 2: Freeze screen information architecture

**Files:**
- Create: `worklog/v2.2.2/sprint-14-202603/features/F2-大屏看板与下钻信息架构/README.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F2-大屏看板与下钻信息架构/T01-战略层大屏模块设计.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F2-大屏看板与下钻信息架构/T02-五个管控层看板模块设计.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F2-大屏看板与下钻信息架构/T03-执行层只读下钻页模型设计.md`

**Step 1: Define strategic screen**

List exact modules for:
- top KPIs
- trends/distributions
- anomaly summaries
- board jump targets

**Step 2: Define five control boards**

For each board, define:
- KPI row
- main visual modules
- ranking/table modules
- drill targets

**Step 3: Define execution drill pages**

Specify the read-only page layout for:
- project execution detail
- quality detail
- tech state detail
- cost detail
- risk detail

### Task 3: Define template-layer delivery

**Files:**
- Create: `worklog/v2.2.2/sprint-14-202603/features/F3-GPMC模板化交付/README.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F3-GPMC模板化交付/T01-六套模板规格与变量槽位定义.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F3-GPMC模板化交付/T02-模板与业务页共用语义层策略.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F3-GPMC模板化交付/T03-screens模板注册与验收策略.md`

**Step 1: Define six templates**

Specify six `ScreenTemplate` deliverables:
- 1 strategic screen
- 5 control boards

**Step 2: Define shared semantics**

Specify how business pages and templates share:
- variables
- metric names
- response paths
- drill actions

**Step 3: Define screens integration**

Specify:
- template IDs
- category
- marketplace visibility
- minimal template test coverage

### Task 4: Prepare implementation handoff

**Files:**
- Create: `worklog/v2.2.2/sprint-14-202603/features/F4-实施准备与验收基线/README.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F4-实施准备与验收基线/T01-后端API与视图模型拆分计划.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F4-实施准备与验收基线/T02-前端gpmc与screens落地计划.md`
- Create: `worklog/v2.2.2/sprint-14-202603/features/F4-实施准备与验收基线/T03-模板与业务页验收清单.md`

**Step 1: Split backend work**

List exact future backend work:
- new GPMC facade/service files
- API grouping by screen
- integration with existing analytics project-cockpit patterns

**Step 2: Split frontend work**

List exact future frontend work:
- remove `resource` board
- split quality and tech state
- replace mock data with screen view models
- add read-only drill pages

**Step 3: Define verification**

List required verification for implementation:
- backend tests
- frontend build/type checks
- screen template tests
- manual acceptance
