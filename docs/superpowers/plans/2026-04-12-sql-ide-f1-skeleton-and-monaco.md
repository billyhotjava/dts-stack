# SQL IDE F1: 架构骨架与 Monaco 编辑器 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 搭建 SQL IDE 的整体架构骨架，将原 textarea 替换为 Monaco，实现编辑器基础能力（语法高亮、catalog 驱动补全、9 个快捷键、SQL 格式化），并通过 feature flag 与旧 `QueryWorkbenchPage` 并存切换。

**Architecture:** 在 `source/dts-platform-webapp/src/components/sql-ide/` 新建组件树，旧 `SqlWorkbenchExperimental.tsx` 不动；通过 `GLOBAL_CONFIG.enableSqlIdeV2` 在路由层切换渲染。后端在 `/api/sql/v2/*` 新增骨架 Controller，旧 `/api/sql/*` 不动，配置项 `dts.sql-ide.v2.enabled` 通过全局配置端点暴露给前端。

**Tech Stack:** React 18 + TypeScript + Vite + @monaco-editor/react 4.6 + monaco-editor 0.52 + Zustand 4 + @tanstack/react-query 5 + Ant Design 5 + sql-formatter 15（新增）；Spring Boot 3.4.5 + Java 21 + Liquibase + JUnit 5。

---

## Spec Reference

- Sprint README: `worklog/v2.2.3/sprint-11-202604/README.md`
- Feature README: `worklog/v2.2.3/sprint-11-202604/features/F1-架构骨架与Monaco编辑器/README.md`
- Tasks: T01–T06 in same folder

## File Structure

**Frontend (all new files unless noted):**

| Path | Responsibility |
|---|---|
| `src/pages/explore/SqlIdePage.tsx` | 路由入口，挂载 `<SqlIde/>` |
| `src/components/sql-ide/SqlIde.tsx` | 根组件，骨架布局 |
| `src/components/sql-ide/layout/ActivityBar.tsx` | 最左侧 44px icon 栏（本期仅占位） |
| `src/components/sql-ide/layout/SidePanel.tsx` | 左侧可折叠面板容器（本期仅占位） |
| `src/components/sql-ide/layout/BottomPanel.tsx` | 底部面板容器（本期仅占位） |
| `src/components/sql-ide/editor/SqlEditor.tsx` | Monaco 封装 |
| `src/components/sql-ide/editor/themes.ts` | 注册 `sqlide-dark` / `sqlide-light` 主题 |
| `src/components/sql-ide/editor/statementSplitter.ts` | 分号切分 SQL（含字符串内分号） |
| `src/components/sql-ide/editor/statementSplitter.test.ts` | 切分单测 |
| `src/components/sql-ide/editor/formatter.ts` | sql-formatter 动态加载封装 |
| `src/components/sql-ide/editor/completion/contextParser.ts` | 轻量 SQL 上下文识别（alias / from / dot） |
| `src/components/sql-ide/editor/completion/contextParser.test.ts` | 解析器单测 |
| `src/components/sql-ide/editor/completion/catalogProvider.ts` | Monaco catalog 补全源 |
| `src/components/sql-ide/editor/completion/keywordProvider.ts` | Monaco 关键字补全源 |
| `src/components/sql-ide/api/sqlIdeBackend.ts` | `GET /api/sql/v2/ping` 等 |
| `src/global-config.ts` | **MODIFY** 增加 `enableSqlIdeV2: boolean` |
| `src/routes/sections/dashboard/dynamic-resolver.tsx` | **MODIFY** flag 控制 `/explore/workbench` 目标页 |

**Backend (all new files unless noted):**

| Path | Responsibility |
|---|---|
| `config/SqlIdeFeatureProperties.java` | `@ConfigurationProperties("dts.sql-ide.v2")` |
| `web/rest/sql/SqlIdeResource.java` | `/api/sql/v2/*` 骨架（本期仅 `ping`） |
| `service/sql/SqlIdeTabService.java` | 空骨架（F2 填充） |
| `service/sql/SqlResultStreamService.java` | 空骨架（F4/F5 填充） |
| `service/sql/SqlPlanService.java` | 空骨架（F5 填充） |
| `domain/sql/SqlIdeTab.java` | 空骨架（F2 填充） |
| `web/rest/ClientConfigResource.java` 或等价 | **MODIFY** 返回 `enableSqlIdeV2` |
| `src/main/resources/config/application.yml` | **MODIFY** 新增 `dts.sql-ide.v2.enabled: false` |

---

## Task 1 — T01: 新建骨架目录与 SqlIdePage 路由入口

**Files:**
- Create: `source/dts-platform-webapp/src/pages/explore/SqlIdePage.tsx`
- Create: `source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx`
- Create: `source/dts-platform-webapp/src/components/sql-ide/layout/ActivityBar.tsx`
- Create: `source/dts-platform-webapp/src/components/sql-ide/layout/SidePanel.tsx`
- Create: `source/dts-platform-webapp/src/components/sql-ide/layout/BottomPanel.tsx`
- Create: `source/dts-platform-webapp/src/components/sql-ide/api/sqlIdeBackend.ts`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResource.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlIdeTabService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlResultStreamService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlPlanService.java`
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/sql/SqlIdeTab.java`

- [ ] **Step 1.1: 创建前端目录骨架**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp/src
mkdir -p components/sql-ide/{layout,editor/completion,tabs,schema,result,history,copilot,hooks,api,store}
```

- [ ] **Step 1.2: 创建 ActivityBar 占位组件**

`src/components/sql-ide/layout/ActivityBar.tsx`：

```tsx
import type { FC } from "react";

export const ActivityBar: FC = () => (
  <div
    role="navigation"
    aria-label="SQL IDE Activity Bar"
    style={{
      width: 44,
      flexShrink: 0,
      height: "100%",
      borderRight: "1px solid var(--ant-color-border)",
      background: "var(--ant-color-bg-container)",
    }}
    data-testid="sqlide-activity-bar"
  >
    {/* Icons placeholder — filled in F3/T12 */}
  </div>
);
```

- [ ] **Step 1.3: 创建 SidePanel 占位组件**

`src/components/sql-ide/layout/SidePanel.tsx`：

```tsx
import type { FC, PropsWithChildren } from "react";

export const SidePanel: FC<PropsWithChildren<{ width?: number }>> = ({ width = 240, children }) => (
  <aside
    style={{
      width,
      flexShrink: 0,
      height: "100%",
      borderRight: "1px solid var(--ant-color-border)",
      overflow: "auto",
      background: "var(--ant-color-bg-container)",
    }}
    data-testid="sqlide-side-panel"
  >
    {children}
  </aside>
);
```

- [ ] **Step 1.4: 创建 BottomPanel 占位组件**

`src/components/sql-ide/layout/BottomPanel.tsx`：

```tsx
import type { FC, PropsWithChildren } from "react";

export const BottomPanel: FC<PropsWithChildren<{ height?: number }>> = ({ height = 260, children }) => (
  <div
    style={{
      height,
      flexShrink: 0,
      borderTop: "1px solid var(--ant-color-border)",
      background: "var(--ant-color-bg-container)",
      overflow: "auto",
    }}
    data-testid="sqlide-bottom-panel"
  >
    {children}
  </div>
);
```

- [ ] **Step 1.5: 创建骨架 SqlIde 根组件**

`src/components/sql-ide/SqlIde.tsx`：

```tsx
import type { FC } from "react";
import { ActivityBar } from "./layout/ActivityBar";
import { SidePanel } from "./layout/SidePanel";
import { BottomPanel } from "./layout/BottomPanel";

export const SqlIde: FC = () => (
  <div
    data-testid="sqlide-root"
    style={{
      display: "flex",
      width: "100%",
      height: "100%",
      minHeight: 0,
    }}
  >
    <ActivityBar />
    <SidePanel>
      <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Schema · 骨架</div>
    </SidePanel>
    <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
      <div style={{ flex: 1, padding: 16, color: "var(--ant-color-text-secondary)" }}>
        SQL IDE v2 骨架 · Monaco 将在 T03 挂载此处
      </div>
      <BottomPanel>
        <div style={{ padding: 12 }}>Bottom · 骨架</div>
      </BottomPanel>
    </div>
  </div>
);
```

- [ ] **Step 1.6: 创建 SqlIdePage 路由页面**

`src/pages/explore/SqlIdePage.tsx`：

```tsx
import type { FC } from "react";
import { SqlIde } from "@/components/sql-ide/SqlIde";

const SqlIdePage: FC = () => (
  <div style={{ position: "absolute", inset: 0, display: "flex" }}>
    <SqlIde />
  </div>
);

export default SqlIdePage;
```

- [ ] **Step 1.7: 创建前端 API 客户端**

`src/components/sql-ide/api/sqlIdeBackend.ts`：

```ts
import { apiClient } from "@/api/apiClient";

export type PingResponse = { status: "ok"; version: string };

export async function pingSqlIde(): Promise<PingResponse> {
  return apiClient.get<PingResponse>({ url: "/api/sql/v2/ping" });
}
```

> **Note:** 如本仓库没有 `apiClient.get` 签名一致，按仓库现有 API 层约定调整 (运行 `rg -n "api/apiClient" source/dts-platform-webapp/src/api | head`).

- [ ] **Step 1.8: 创建后端 SqlIdeResource 骨架**

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResource.java`：

```java
package com.yuzhi.dts.platform.web.rest.sql;

import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql/v2")
public class SqlIdeResource {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponses.ok(Map.of("status", "ok", "version", "v2-skeleton"));
    }
}
```

> **Note:** 若 `ApiResponses.ok` 签名不同，按 `SqlWorkbenchResource` 现有 pattern 调整。

- [ ] **Step 1.9: 创建后端空 Service 骨架（F2/F4/F5 填充）**

`service/sql/SqlIdeTabService.java`：

```java
package com.yuzhi.dts.platform.service.sql;

/** Filled in Sprint-11 F2 (T08). */
public interface SqlIdeTabService {
}
```

`service/sql/SqlResultStreamService.java`：

```java
package com.yuzhi.dts.platform.service.sql;

/** Filled in Sprint-11 F4 (T19) and F5 (T24). */
public interface SqlResultStreamService {
}
```

`service/sql/SqlPlanService.java`：

```java
package com.yuzhi.dts.platform.service.sql;

/** Filled in Sprint-11 F5 (T23). */
public interface SqlPlanService {
}
```

`domain/sql/SqlIdeTab.java`：

```java
package com.yuzhi.dts.platform.domain.sql;

/** Filled in Sprint-11 F2 (T07) with JPA annotations. */
public class SqlIdeTab {
}
```

- [ ] **Step 1.10: 验证后端编译通过**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform
./mvnw -q -DskipTests compile
```

**Expected:** BUILD SUCCESS，无编译错误。

- [ ] **Step 1.11: 验证前端类型检查通过**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform-webapp
pnpm tsc --noEmit
```

**Expected:** 无类型错误。

- [ ] **Step 1.12: 提交**

```bash
cd /opt/prod/s10/s10-stack
git add source/dts-platform-webapp/src/components/sql-ide \
        source/dts-platform-webapp/src/pages/explore/SqlIdePage.tsx \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResource.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlIdeTabService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlResultStreamService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/sql/SqlPlanService.java \
        source/dts-platform/src/main/java/com/yuzhi/dts/platform/domain/sql/SqlIdeTab.java
git commit -m "feat(F1/T01): scaffold sql-ide module and /api/sql/v2 resource

Sprint-11 F1 T01: 新建 sql-ide 组件目录、SqlIdePage 路由入口、
后端 SqlIdeResource 骨架（/api/sql/v2/ping）。老路径 /api/sql/* 不动。"
```

---

## Task 2 — T02: Feature Flag 前后端打通

**Files:**
- Create: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SqlIdeFeatureProperties.java`
- Modify: `source/dts-platform/src/main/resources/config/application.yml` (~line 278 `dts.platform.features` 段)
- Modify: `source/dts-platform-webapp/src/global-config.ts` (~line 25 `GlobalConfig` 类型定义附近)
- Modify: `source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx:37`
- Modify: 后端返回 client config 的端点（搜 `enableSqlWorkbench` 找到现有暴露点）

- [ ] **Step 2.1: 查找后端 client config 暴露位置**

```bash
cd /opt/prod/s10/s10-stack
rg -n "enableSqlWorkbench|platform.features|kc-localization" source/dts-platform/src/main/java | head
```

**Expected:** 找到返回 client-side 配置的 Controller（可能在 `web/rest/PublicConfigResource.java` 或 `platform.features` 配置 bean）。把该文件路径记下，作为 Step 2.5 的 MODIFY 目标。

- [ ] **Step 2.2: 创建 `SqlIdeFeatureProperties`**

`source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SqlIdeFeatureProperties.java`：

```java
package com.yuzhi.dts.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dts.sql-ide.v2")
public class SqlIdeFeatureProperties {
    /** Master switch for the new SQL IDE (Sprint-11). Default false. */
    private boolean enabled = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
```

- [ ] **Step 2.3: 注册 properties 并在 application.yml 新增配置**

在 `application.yml` 的 `dts.platform` 段后追加（缩进对齐 `dts:`）：

```yaml
  sql-ide:
    v2:
      enabled: ${DTS_SQL_IDE_V2_ENABLED:false}
```

查找 `@ConfigurationPropertiesScan` 或 `@EnableConfigurationProperties` 注解位置：

```bash
rg -n "EnableConfigurationProperties|ConfigurationPropertiesScan" source/dts-platform/src/main/java | head
```

若扫描 package 已覆盖 `config`，无需改动；否则在主 `Application` 类加 `@EnableConfigurationProperties(SqlIdeFeatureProperties.class)`。

- [ ] **Step 2.4: 写后端端点测试（TDD）**

`source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResourceIT.java`：

```java
package com.yuzhi.dts.platform.web.rest.sql;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@WithMockUser
class SqlIdeResourceIT {

    @Autowired private MockMvc mockMvc;

    @Test
    void pingReturnsOk() throws Exception {
        mockMvc.perform(get("/api/sql/v2/ping"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value(is("ok")));
    }
}
```

> **Note:** 核实 `@IntegrationTest` 注解路径（搜 `rg -n "@interface IntegrationTest"` 或仿 `SqlWorkbenchResource` 的测试模式）；`jsonPath` 路径按 `ApiResponse` 实际包装调整（可能是 `$.status` / `$.data.status`）。

运行：

```bash
cd source/dts-platform
./mvnw -q test -Dtest=SqlIdeResourceIT
```

**Expected:** PASS。

- [ ] **Step 2.5: 修改 client config 端点返回 `enableSqlIdeV2`**

按 Step 2.1 找到的文件，比照现有 `enableSqlWorkbench` 字段追加 `enableSqlIdeV2`，注入 `SqlIdeFeatureProperties` 并返回 `props.isEnabled()`。

- [ ] **Step 2.6: 修改前端 `GlobalConfig` 类型与加载逻辑**

`source/dts-platform-webapp/src/global-config.ts`，在 `enableSqlWorkbench: boolean;` 下追加：

```ts
    /** Enable Sprint-11 new SQL IDE (replaces QueryWorkbenchPage when true) */
    enableSqlIdeV2: boolean;
```

然后在同文件把后端 client config 加载到 `GLOBAL_CONFIG` 的地方加上字段读取（搜 `enableSqlWorkbench` 会显示具体位置），缺省 `false`。

- [ ] **Step 2.7: 修改路由把 `/explore/workbench` 映射到 flag 选择的页面**

`src/routes/sections/dashboard/dynamic-resolver.tsx:37`，把：

```ts
"/explore/workbench": "/pages/explore/QueryWorkbenchPage",
```

改为常量表 + 运行时分派。定位到引用 `PATH_COMPONENT_OVERRIDES["/explore/workbench"]` 的位置（`Component` 工厂处），改为：

```ts
const workbenchPath = GLOBAL_CONFIG.enableSqlIdeV2
  ? "/pages/explore/SqlIdePage"
  : "/pages/explore/QueryWorkbenchPage";
```

并把表里的该项替换为 `workbenchPath`。保持老 path `/explore/workbench` 不变（路由对用户不变，只换页面实现）。

- [ ] **Step 2.8: 运行前端类型与构建**

```bash
cd source/dts-platform-webapp
pnpm tsc --noEmit && pnpm vite build --mode development
```

**Expected:** 无类型/构建错误。

- [ ] **Step 2.9: 手动验证切换**

```bash
cd /opt/prod/s10/s10-stack/source/dts-platform
DTS_SQL_IDE_V2_ENABLED=false ./mvnw spring-boot:run &
# 访问前端 /explore/workbench，应显示 QueryWorkbenchPage
kill %1
DTS_SQL_IDE_V2_ENABLED=true ./mvnw spring-boot:run &
# 刷新页面，应显示 SqlIde 骨架页（看到 "SQL IDE v2 骨架"）
kill %1
```

**Expected:** 两种模式都正常，切换无需改代码。

- [ ] **Step 2.10: 提交**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/config/SqlIdeFeatureProperties.java \
        source/dts-platform/src/main/resources/config/application.yml \
        source/dts-platform/src/test/java/com/yuzhi/dts/platform/web/rest/sql/SqlIdeResourceIT.java \
        source/dts-platform-webapp/src/global-config.ts \
        source/dts-platform-webapp/src/routes/sections/dashboard/dynamic-resolver.tsx \
        $(上面 Step 2.5 的文件)
git commit -m "feat(F1/T02): wire sql-ide feature flag front-to-back

Sprint-11 F1 T02: 新增 dts.sql-ide.v2.enabled 配置，暴露到前端
GLOBAL_CONFIG.enableSqlIdeV2，路由层条件挂载 SqlIdePage 或
旧 QueryWorkbenchPage。默认关闭。"
```

---

## Task 3 — T03: Monaco 编辑器封装

**Files:**
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/themes.ts`
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/SqlEditor.tsx`
- Modify: `source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx` (挂载编辑器)

- [ ] **Step 3.1: 创建自定义主题配置**

`src/components/sql-ide/editor/themes.ts`：

```ts
import type * as MonacoNs from "monaco-editor";

export const SQLIDE_DARK = "sqlide-dark";
export const SQLIDE_LIGHT = "sqlide-light";

export function registerSqlIdeThemes(monaco: typeof MonacoNs): void {
  monaco.editor.defineTheme(SQLIDE_DARK, {
    base: "vs-dark",
    inherit: true,
    rules: [
      { token: "keyword.sql", foreground: "c678dd", fontStyle: "bold" },
      { token: "string.sql", foreground: "98c379" },
      { token: "number.sql", foreground: "d19a66" },
      { token: "comment.sql", foreground: "7f848e", fontStyle: "italic" },
    ],
    colors: {
      "editor.background": "#1e1e2e",
      "editor.foreground": "#d4d4d8",
      "editorLineNumber.foreground": "#5c6370",
      "editor.selectionBackground": "#3a3a5e",
    },
  });
  monaco.editor.defineTheme(SQLIDE_LIGHT, {
    base: "vs",
    inherit: true,
    rules: [
      { token: "keyword.sql", foreground: "7c3aed", fontStyle: "bold" },
      { token: "string.sql", foreground: "059669" },
      { token: "number.sql", foreground: "b45309" },
      { token: "comment.sql", foreground: "6b7280", fontStyle: "italic" },
    ],
    colors: {
      "editor.background": "#ffffff",
      "editor.foreground": "#18181b",
    },
  });
}
```

- [ ] **Step 3.2: 创建 `SqlEditor` 组件**

`src/components/sql-ide/editor/SqlEditor.tsx`：

```tsx
import Editor, { type OnMount, useMonaco } from "@monaco-editor/react";
import { type FC, useCallback, useEffect } from "react";
import { SQLIDE_DARK, SQLIDE_LIGHT, registerSqlIdeThemes } from "./themes";

export type Engine = "trino" | "hive" | "postgresql" | "generic";
export type EditorMode = "simple" | "advanced";

export interface SqlEditorProps {
  value: string;
  onChange: (sql: string) => void;
  engine?: Engine;
  mode?: EditorMode;
  readOnly?: boolean;
  isDark?: boolean;
  onExecute?: (sql: string) => void;
  onCursorPositionChange?: (pos: { line: number; column: number }) => void;
}

export const SqlEditor: FC<SqlEditorProps> = ({
  value,
  onChange,
  engine = "generic",
  mode = "simple",
  readOnly = false,
  isDark = true,
  onExecute,
  onCursorPositionChange,
}) => {
  const monaco = useMonaco();

  useEffect(() => {
    if (monaco) registerSqlIdeThemes(monaco);
  }, [monaco]);

  const handleMount: OnMount = useCallback(
    (editor, mo) => {
      registerSqlIdeThemes(mo);
      editor.onDidChangeCursorPosition((e) => {
        onCursorPositionChange?.({ line: e.position.lineNumber, column: e.position.column });
      });
      // Ctrl+Enter placeholder — full keymap in T05
      editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.Enter, () => {
        onExecute?.(editor.getValue());
      });
    },
    [onCursorPositionChange, onExecute],
  );

  return (
    <div
      data-testid="sqlide-editor"
      data-engine={engine}
      data-mode={mode}
      style={{ height: "100%", width: "100%", minHeight: 0 }}
    >
      <Editor
        height="100%"
        language="sql"
        value={value}
        onChange={(v) => onChange(v ?? "")}
        theme={isDark ? SQLIDE_DARK : SQLIDE_LIGHT}
        onMount={handleMount}
        options={{
          automaticLayout: true,
          minimap: { enabled: mode === "advanced" },
          fontSize: 14,
          lineNumbers: "on",
          wordWrap: "on",
          suggestOnTriggerCharacters: true,
          quickSuggestions: { other: true, comments: false, strings: true },
          tabSize: 2,
          bracketPairColorization: { enabled: true },
          scrollBeyondLastLine: false,
          renderLineHighlight: "line",
          readOnly,
        }}
      />
    </div>
  );
};
```

- [ ] **Step 3.3: 在 `SqlIde.tsx` 挂载编辑器（替换占位）**

修改 `SqlIde.tsx` 的中央区域：

```tsx
import { type FC, useState } from "react";
import { ActivityBar } from "./layout/ActivityBar";
import { SidePanel } from "./layout/SidePanel";
import { BottomPanel } from "./layout/BottomPanel";
import { SqlEditor } from "./editor/SqlEditor";

export const SqlIde: FC = () => {
  const [sql, setSql] = useState<string>("-- SQL IDE v2\nSELECT 1;");
  return (
    <div
      data-testid="sqlide-root"
      style={{ display: "flex", width: "100%", height: "100%", minHeight: 0 }}
    >
      <ActivityBar />
      <SidePanel>
        <div style={{ padding: 12, color: "var(--ant-color-text-secondary)" }}>Schema · 骨架</div>
      </SidePanel>
      <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
        <div style={{ flex: 1, minHeight: 0 }}>
          <SqlEditor
            value={sql}
            onChange={setSql}
            engine="generic"
            mode="simple"
            isDark={true}
            onExecute={(s) => console.info("[SqlIde] execute placeholder:", s)}
          />
        </div>
        <BottomPanel>
          <div style={{ padding: 12 }}>Bottom · 骨架</div>
        </BottomPanel>
      </div>
    </div>
  );
};
```

- [ ] **Step 3.4: 运行类型检查**

```bash
cd source/dts-platform-webapp && pnpm tsc --noEmit
```

**Expected:** 无错误。

- [ ] **Step 3.5: 手动验证 Monaco 渲染**

- 启动后端（flag 开）、前端，访问 `/explore/workbench`
- 编辑器内应能看到 `SELECT 1;` 语法高亮、行号、括号匹配
- 简洁模式下 minimap 不显示；切换到 advanced 模式应显示（临时把 `mode="advanced"` 测）
- 全局暗黑/亮色切换时，编辑器背景同步切换（`isDark` prop 由 T06 后续对接全局 theme store 完成；本步允许手动硬编码）

- [ ] **Step 3.6: 检查 Monaco bundle 拆分**

```bash
cd source/dts-platform-webapp
pnpm vite build
ls -lh dist/assets/ | grep -i monaco
```

**Expected:** Monaco 相关 chunk 为独立文件（Vite 会自动拆），单块 gzip 后 ≤500KB（用 `du -h` 看压缩后大小，或在 Network 面板看生产 gzip）。

- [ ] **Step 3.7: 提交**

```bash
git add source/dts-platform-webapp/src/components/sql-ide/editor/themes.ts \
        source/dts-platform-webapp/src/components/sql-ide/editor/SqlEditor.tsx \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "feat(F1/T03): integrate Monaco editor with SQL theme

Sprint-11 F1 T03: 引入 @monaco-editor/react，实现 SqlEditor 封装，
注册 sqlide-dark/light 双主题，简洁模式默认关 minimap。"
```

---

## Task 4 — T04: SQL 补全引擎

**Files:**
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/completion/contextParser.ts`
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/completion/contextParser.test.ts`
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/completion/keywordProvider.ts`
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/completion/catalogProvider.ts`
- Modify: `source/dts-platform-webapp/src/components/sql-ide/editor/SqlEditor.tsx` (注册 providers)

- [ ] **Step 4.1: 写 contextParser 失败测试**

`src/components/sql-ide/editor/completion/contextParser.test.ts`：

```ts
import { describe, expect, it } from "vitest";
import { parseContext } from "./contextParser";

describe("parseContext", () => {
  it("detects dot-access with alias", () => {
    expect(parseContext("SELECT u. FROM users u")).toEqual({ kind: "afterDot", alias: "u" });
  });

  it("detects dot-access with full table name", () => {
    expect(parseContext("SELECT * FROM public.")).toEqual({ kind: "afterDot", alias: "public" });
  });

  it("detects FROM context", () => {
    expect(parseContext("SELECT * FROM ")).toEqual({ kind: "afterFrom" });
  });

  it("detects JOIN context", () => {
    expect(parseContext("SELECT * FROM a JOIN ")).toEqual({ kind: "afterFrom" });
  });

  it("extracts aliases from the statement", () => {
    const ctx = parseContext("SELECT u. FROM users u JOIN orders o ON u.id = o.user_id");
    expect(ctx).toMatchObject({ kind: "afterDot", alias: "u" });
  });

  it("falls through to default context", () => {
    expect(parseContext("SELECT 1")).toEqual({ kind: "default" });
  });

  it("ignores dots inside string literals", () => {
    expect(parseContext("SELECT 'foo.bar' FROM ")).toEqual({ kind: "afterFrom" });
  });
});
```

- [ ] **Step 4.2: 运行测试确认失败**

```bash
cd source/dts-platform-webapp
pnpm vitest run src/components/sql-ide/editor/completion/contextParser.test.ts
```

**Expected:** FAIL（模块不存在）。

- [ ] **Step 4.3: 实现 contextParser**

`src/components/sql-ide/editor/completion/contextParser.ts`：

```ts
export type SqlContext =
  | { kind: "afterDot"; alias: string }
  | { kind: "afterFrom" }
  | { kind: "default" };

/** Strip content inside single-quoted strings (naive). */
function stripStrings(text: string): string {
  return text.replace(/'([^'\\]|\\.)*'/g, "''");
}

/**
 * Inspect the text before the cursor and classify the completion context.
 * Implementation is intentionally regex-based — a full SQL parser is overkill.
 */
export function parseContext(textBeforeCursor: string): SqlContext {
  const cleaned = stripStrings(textBeforeCursor);
  const trimmed = cleaned.replace(/\s+$/u, "");

  // afterDot: identifier followed by "."
  const dotMatch = /(\b[\p{L}_][\p{L}\p{N}_]*)\.\s*$/u.exec(cleaned);
  if (dotMatch) return { kind: "afterDot", alias: dotMatch[1] };

  // afterFrom: FROM or JOIN followed by whitespace (cursor is where table name goes)
  if (/\b(?:FROM|JOIN)\s+$/iu.test(trimmed + " ")) return { kind: "afterFrom" };

  return { kind: "default" };
}
```

- [ ] **Step 4.4: 运行测试确认全绿**

```bash
pnpm vitest run src/components/sql-ide/editor/completion/contextParser.test.ts
```

**Expected:** 7 tests PASS。

- [ ] **Step 4.5: 实现关键字 provider**

`src/components/sql-ide/editor/completion/keywordProvider.ts`：

```ts
import type * as MonacoNs from "monaco-editor";

const ANSI_KEYWORDS = [
  "SELECT", "FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT",
  "OFFSET", "JOIN", "LEFT JOIN", "RIGHT JOIN", "INNER JOIN", "FULL OUTER JOIN",
  "ON", "AS", "AND", "OR", "NOT", "IN", "BETWEEN", "LIKE", "IS NULL",
  "IS NOT NULL", "CASE", "WHEN", "THEN", "ELSE", "END", "UNION", "UNION ALL",
  "WITH", "DISTINCT", "COUNT", "SUM", "AVG", "MIN", "MAX",
];

export function buildKeywordSuggestions(
  monaco: typeof MonacoNs,
  range: MonacoNs.IRange,
): MonacoNs.languages.CompletionItem[] {
  return ANSI_KEYWORDS.map((kw) => ({
    label: kw,
    kind: monaco.languages.CompletionItemKind.Keyword,
    insertText: kw,
    range,
    sortText: `z_${kw}`, // keywords sort lower than catalog items
  }));
}
```

- [ ] **Step 4.6: 实现 catalog provider 脚手架（真实 fetch 在 F3/T11 接入）**

`src/components/sql-ide/editor/completion/catalogProvider.ts`：

```ts
import type * as MonacoNs from "monaco-editor";
import { parseContext } from "./contextParser";
import { buildKeywordSuggestions } from "./keywordProvider";

export interface CatalogSource {
  listTables(): Promise<Array<{ schema: string; name: string; comment?: string }>>;
  listColumns(
    tableOrAlias: string,
  ): Promise<Array<{ name: string; dataType: string; nullable: boolean; comment?: string }>>;
}

/** In-memory stub; replaced by React Query-backed impl in F3/T11. */
export const NOOP_CATALOG: CatalogSource = {
  async listTables() {
    return [];
  },
  async listColumns() {
    return [];
  },
};

export function registerSqlCatalogCompletion(
  monaco: typeof MonacoNs,
  source: CatalogSource,
): MonacoNs.IDisposable {
  return monaco.languages.registerCompletionItemProvider("sql", {
    triggerCharacters: [".", " "],
    async provideCompletionItems(model, position) {
      const textBefore = model.getValueInRange({
        startLineNumber: 1,
        startColumn: 1,
        endLineNumber: position.lineNumber,
        endColumn: position.column,
      });
      const word = model.getWordUntilPosition(position);
      const range: MonacoNs.IRange = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      };

      const ctx = parseContext(textBefore);
      if (ctx.kind === "afterDot") {
        const cols = await source.listColumns(ctx.alias);
        return {
          suggestions: cols.map((c) => ({
            label: c.name,
            kind: monaco.languages.CompletionItemKind.Field,
            detail: c.dataType,
            documentation: c.comment ?? undefined,
            insertText: c.name,
            range,
          })),
        };
      }
      if (ctx.kind === "afterFrom") {
        const tables = await source.listTables();
        return {
          suggestions: tables.map((t) => ({
            label: `${t.schema}.${t.name}`,
            kind: monaco.languages.CompletionItemKind.Struct,
            detail: t.comment ?? "table",
            insertText: `${t.schema}.${t.name}`,
            range,
          })),
        };
      }
      return { suggestions: buildKeywordSuggestions(monaco, range) };
    },
  });
}
```

- [ ] **Step 4.7: 在 `SqlEditor.tsx` 注册 catalog completion**

在 `SqlEditor.tsx` 的 effect 中追加：

```tsx
import { NOOP_CATALOG, registerSqlCatalogCompletion, type CatalogSource } from "./completion/catalogProvider";

// 加到 SqlEditorProps：
//   catalog?: CatalogSource;

// 组件内部：
useEffect(() => {
  if (!monaco) return;
  const disposable = registerSqlCatalogCompletion(monaco, catalog ?? NOOP_CATALOG);
  return () => disposable.dispose();
}, [monaco, catalog]);
```

并把 `catalog?: CatalogSource;` 加入 `SqlEditorProps`。

- [ ] **Step 4.8: 手动验证补全触发**

- 打开 SqlIde 页面，输入 `SELECT * FROM `（末尾空格）→ 应弹补全（NOOP 状态下只会有 keyword）
- 输入 `x.` → 弹补全（NOOP 下空列表，但触发不应报错）
- 输入任意字符触发通用补全 → 应看到 `SELECT` 等关键字

- [ ] **Step 4.9: 提交**

```bash
git add source/dts-platform-webapp/src/components/sql-ide/editor/completion \
        source/dts-platform-webapp/src/components/sql-ide/editor/SqlEditor.tsx
git commit -m "feat(F1/T04): add SQL completion engine with context parser

Sprint-11 F1 T04: 实现 parseContext (dot/from/default)、keyword 补全、
catalog 补全脚手架 (CatalogSource 接口)，Monaco 注册 SQL completion
provider。真实 catalog fetch 在 F3/T11 接入。"
```

---

## Task 5 — T05: 快捷键体系

**Files:**
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/statementSplitter.ts`
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/statementSplitter.test.ts`
- Create: `source/dts-platform-webapp/src/components/sql-ide/ShortcutsHelp.tsx`
- Modify: `source/dts-platform-webapp/src/components/sql-ide/editor/SqlEditor.tsx`

- [ ] **Step 5.1: 写 statementSplitter 测试**

`src/components/sql-ide/editor/statementSplitter.test.ts`：

```ts
import { describe, expect, it } from "vitest";
import { findStatementAt, splitStatements } from "./statementSplitter";

describe("splitStatements", () => {
  it("splits simple statements by semicolon", () => {
    expect(splitStatements("SELECT 1; SELECT 2;")).toEqual([
      { start: 0, end: 9, text: "SELECT 1;" },
      { start: 10, end: 19, text: "SELECT 2;" },
    ]);
  });

  it("keeps semicolons inside strings", () => {
    const sql = "SELECT ';' FROM t; SELECT 2;";
    const parts = splitStatements(sql);
    expect(parts).toHaveLength(2);
    expect(parts[0].text).toBe("SELECT ';' FROM t;");
  });

  it("handles trailing statement without semicolon", () => {
    const parts = splitStatements("SELECT 1; SELECT 2");
    expect(parts).toHaveLength(2);
    expect(parts[1].text).toBe("SELECT 2");
  });
});

describe("findStatementAt", () => {
  it("returns statement containing offset", () => {
    const sql = "SELECT 1; SELECT 2; SELECT 3;";
    const stmt = findStatementAt(sql, 12); // inside "SELECT 2"
    expect(stmt?.text).toBe("SELECT 2;");
  });

  it("returns undefined for empty input", () => {
    expect(findStatementAt("", 0)).toBeUndefined();
  });
});
```

- [ ] **Step 5.2: 运行测试确认失败**

```bash
pnpm vitest run src/components/sql-ide/editor/statementSplitter.test.ts
```

**Expected:** FAIL（模块不存在）。

- [ ] **Step 5.3: 实现 statementSplitter**

`src/components/sql-ide/editor/statementSplitter.ts`：

```ts
export interface Statement {
  start: number;
  end: number;
  text: string;
}

/**
 * Split SQL by `;` while ignoring semicolons inside single-quoted strings.
 * A full SQL parser is out of scope; this covers the common cases.
 */
export function splitStatements(sql: string): Statement[] {
  const out: Statement[] = [];
  let inString = false;
  let stmtStart = 0;
  let i = 0;
  while (i < sql.length) {
    const ch = sql[i];
    if (ch === "'" && sql[i - 1] !== "\\") inString = !inString;
    if (ch === ";" && !inString) {
      const end = i + 1;
      out.push({ start: stmtStart, end, text: sql.slice(stmtStart, end) });
      stmtStart = end;
    }
    i++;
  }
  const tail = sql.slice(stmtStart).trim();
  if (tail.length > 0) {
    out.push({ start: stmtStart, end: sql.length, text: sql.slice(stmtStart) });
  }
  // Trim leading whitespace but keep ranges accurate for callers.
  return out
    .map((s) => ({ ...s, text: s.text.replace(/^\s+/, "") }))
    .filter((s) => s.text.length > 0);
}

export function findStatementAt(sql: string, offset: number): Statement | undefined {
  for (const s of splitStatements(sql)) {
    if (offset >= s.start && offset <= s.end) return s;
  }
  return undefined;
}
```

- [ ] **Step 5.4: 测试全绿**

```bash
pnpm vitest run src/components/sql-ide/editor/statementSplitter.test.ts
```

**Expected:** 5 tests PASS。

- [ ] **Step 5.5: 创建 `ShortcutsHelp` 组件**

`src/components/sql-ide/ShortcutsHelp.tsx`：

```tsx
import { Modal, Typography } from "antd";
import type { FC } from "react";

const SHORTCUTS: Array<{ key: string; action: string }> = [
  { key: "Ctrl+Enter", action: "执行当前 SQL (或选中块)" },
  { key: "Ctrl+Shift+Enter", action: "执行并在新 Tab 展示结果" },
  { key: "Ctrl+Alt+F", action: "格式化整段 SQL" },
  { key: "Ctrl+/", action: "行注释切换" },
  { key: "Ctrl+D", action: "选中下一个相同词" },
  { key: "Alt+↑ / Alt+↓", action: "行上移 / 下移" },
  { key: "Ctrl+S", action: "保存为 Saved Query" },
  { key: "F8", action: "跳到下一个错误" },
  { key: "Ctrl+`", action: "切换底部面板" },
];

export const ShortcutsHelp: FC<{ open: boolean; onClose: () => void }> = ({ open, onClose }) => (
  <Modal open={open} onCancel={onClose} footer={null} title="快捷键" width={520}>
    <table style={{ width: "100%" }}>
      <tbody>
        {SHORTCUTS.map((s) => (
          <tr key={s.key}>
            <td style={{ padding: "6px 8px" }}>
              <Typography.Text code>{s.key}</Typography.Text>
            </td>
            <td style={{ padding: "6px 8px" }}>{s.action}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </Modal>
);
```

- [ ] **Step 5.6: 注册 9 个快捷键到 Monaco**

修改 `SqlEditor.tsx` 的 `handleMount`：

```tsx
import { findStatementAt } from "./statementSplitter";

// 在 SqlEditorProps 加：
//   onExecuteInNewTab?: (sql: string) => void;
//   onFormat?: () => void;
//   onSaveAsQuery?: (sql: string) => void;
//   onToggleBottomPanel?: () => void;

const handleMount: OnMount = useCallback((editor, mo) => {
  registerSqlIdeThemes(mo);

  editor.onDidChangeCursorPosition((e) => {
    onCursorPositionChange?.({ line: e.position.lineNumber, column: e.position.column });
  });

  const getCurrentSql = (): string => {
    const sel = editor.getSelection();
    const model = editor.getModel();
    if (!model) return "";
    if (sel && !sel.isEmpty()) return model.getValueInRange(sel);
    const offset = model.getOffsetAt(editor.getPosition() ?? { lineNumber: 1, column: 1 });
    return findStatementAt(model.getValue(), offset)?.text ?? model.getValue();
  };

  editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyCode.Enter, () => onExecute?.(getCurrentSql()));
  editor.addCommand(
    mo.KeyMod.CtrlCmd | mo.KeyMod.Shift | mo.KeyCode.Enter,
    () => onExecuteInNewTab?.(getCurrentSql()),
  );
  editor.addCommand(mo.KeyMod.CtrlCmd | mo.KeyMod.Alt | mo.KeyCode.KeyF, () => onFormat?.());
  editor.addCommand(
    mo.KeyMod.CtrlCmd | mo.KeyCode.KeyS,
    () => onSaveAsQuery?.(editor.getValue()),
  );
  editor.addCommand(
    mo.KeyMod.CtrlCmd | mo.KeyCode.Backquote,
    () => onToggleBottomPanel?.(),
  );
  // Ctrl+/, Ctrl+D, Alt+↑↓, F8 use built-in actions
  editor.addAction({
    id: "sqlide.toggleLineComment",
    label: "Toggle Line Comment",
    keybindings: [mo.KeyMod.CtrlCmd | mo.KeyCode.Slash],
    run: (ed) => ed.trigger("sqlide", "editor.action.commentLine", {}),
  });
  editor.addAction({
    id: "sqlide.addNextMatch",
    label: "Add Selection To Next Match",
    keybindings: [mo.KeyMod.CtrlCmd | mo.KeyCode.KeyD],
    run: (ed) => ed.trigger("sqlide", "editor.action.addSelectionToNextFindMatch", {}),
  });
  editor.addAction({
    id: "sqlide.moveLineUp",
    label: "Move Line Up",
    keybindings: [mo.KeyMod.Alt | mo.KeyCode.UpArrow],
    run: (ed) => ed.trigger("sqlide", "editor.action.moveLinesUpAction", {}),
  });
  editor.addAction({
    id: "sqlide.moveLineDown",
    label: "Move Line Down",
    keybindings: [mo.KeyMod.Alt | mo.KeyCode.DownArrow],
    run: (ed) => ed.trigger("sqlide", "editor.action.moveLinesDownAction", {}),
  });
  editor.addAction({
    id: "sqlide.gotoNextMarker",
    label: "Go to Next Marker",
    keybindings: [mo.KeyCode.F8],
    run: (ed) => ed.trigger("sqlide", "editor.action.marker.next", {}),
  });
}, [
  onCursorPositionChange, onExecute, onExecuteInNewTab,
  onFormat, onSaveAsQuery, onToggleBottomPanel,
]);
```

- [ ] **Step 5.7: 在高级模式下暴露 `ShortcutsHelp` 入口**

`SqlIde.tsx` 中：

```tsx
// 当 mode === "advanced" 时在右下角渲染按钮
// 简单起见可放在 BottomPanel header slot，本 Sprint 仅要求能打开
```

实现：在 `SqlIde.tsx` 里加 `const [helpOpen, setHelpOpen] = useState(false);`，传一个按钮绑定。这个按钮可以放在 `BottomPanel` 之上的工具栏占位区——本 Task 允许最小实现（一个小按钮 "⌨ Shortcuts"），美化留给 F6/T26。

- [ ] **Step 5.8: 手动验证快捷键**

启动前端，依次测试：
- `Ctrl+Enter` → console 出现 execute 日志
- `Ctrl+/` → 选中行注释
- `Alt+↓` → 行下移
- `Ctrl+S` → 不触发浏览器保存，走回调
- 其他 5 个按上表测试

- [ ] **Step 5.9: 提交**

```bash
git add source/dts-platform-webapp/src/components/sql-ide/editor/statementSplitter.ts \
        source/dts-platform-webapp/src/components/sql-ide/editor/statementSplitter.test.ts \
        source/dts-platform-webapp/src/components/sql-ide/ShortcutsHelp.tsx \
        source/dts-platform-webapp/src/components/sql-ide/editor/SqlEditor.tsx \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "feat(F1/T05): wire 9 IDE shortcuts into Monaco

Sprint-11 F1 T05: Ctrl+Enter 执行当前语句、Ctrl+Alt+F 格式化、
Ctrl+/ 注释、Ctrl+D 多选、Alt+↑↓ 行移动、Ctrl+S 保存、F8 跳错、
Ctrl+\` 面板切换、Ctrl+Shift+Enter 新 Tab 执行。ShortcutsHelp
弹层展示全部快捷键。"
```

---

## Task 6 — T06: SQL 格式化

**Files:**
- Modify: `source/dts-platform-webapp/package.json` (add `sql-formatter`)
- Create: `source/dts-platform-webapp/src/components/sql-ide/editor/formatter.ts`
- Modify: `source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx` (对接 `onFormat`)

- [ ] **Step 6.1: 安装 sql-formatter**

```bash
cd source/dts-platform-webapp
pnpm add sql-formatter@^15
```

**Expected:** package.json 新增依赖，lock 更新。

- [ ] **Step 6.2: 创建动态加载的 formatter 封装**

`src/components/sql-ide/editor/formatter.ts`：

```ts
import type { Engine } from "./SqlEditor";

const DIALECT_MAP: Record<Engine, string> = {
  trino: "trino",
  hive: "hive",
  postgresql: "postgresql",
  generic: "sql",
};

/** Dynamic import to keep sql-formatter out of the first paint bundle. */
export async function formatSql(sql: string, engine: Engine = "generic"): Promise<string> {
  const { format } = await import("sql-formatter");
  return format(sql, {
    language: (DIALECT_MAP[engine] ?? "sql") as never,
    keywordCase: "upper",
    indentStyle: "standard",
    tabWidth: 2,
  });
}
```

- [ ] **Step 6.3: 在 SqlIde 中接通 `onFormat` 回调**

`SqlIde.tsx`：

```tsx
import { formatSql } from "./editor/formatter";

// 在组件内：
const handleFormat = useCallback(async () => {
  try {
    const next = await formatSql(sql, "generic");
    setSql(next);
  } catch (err) {
    console.error("[SqlIde] format failed", err);
  }
}, [sql]);

// <SqlEditor ... onFormat={handleFormat} />
```

- [ ] **Step 6.4: 写快速冒烟测试**

`src/components/sql-ide/editor/formatter.test.ts`：

```ts
import { describe, expect, it } from "vitest";
import { formatSql } from "./formatter";

describe("formatSql", () => {
  it("upper-cases keywords", async () => {
    const out = await formatSql("select 1 from t", "generic");
    expect(out).toMatch(/^SELECT/);
  });

  it("supports trino dialect", async () => {
    const out = await formatSql("select 1", "trino");
    expect(out).toBeTypeOf("string");
  });
});
```

运行：

```bash
pnpm vitest run src/components/sql-ide/editor/formatter.test.ts
```

**Expected:** PASS。

- [ ] **Step 6.5: 手动验证 Ctrl+Alt+F**

- 在编辑器输入 `select 1 from t where a=1 and b=2`
- 按 Ctrl+Alt+F → 应变成关键字大写、换行缩进的规范 SQL
- 再次按 Ctrl+Alt+F（已格式化的 SQL）不应出错

- [ ] **Step 6.6: 检查构建产物，确认动态 import**

```bash
pnpm vite build
# 检查 sql-formatter 是否在独立 chunk
ls dist/assets/ | xargs -I{} sh -c 'grep -l "sql-formatter" dist/assets/{} 2>/dev/null || true' | head
```

**Expected:** `sql-formatter` 应该在独立 chunk，不出现在主入口 bundle。

- [ ] **Step 6.7: 提交**

```bash
git add source/dts-platform-webapp/package.json \
        source/dts-platform-webapp/pnpm-lock.yaml \
        source/dts-platform-webapp/src/components/sql-ide/editor/formatter.ts \
        source/dts-platform-webapp/src/components/sql-ide/editor/formatter.test.ts \
        source/dts-platform-webapp/src/components/sql-ide/SqlIde.tsx
git commit -m "feat(F1/T06): add dynamic sql-formatter with dialect support

Sprint-11 F1 T06: 引入 sql-formatter@15 (dynamic import)，支持
trino/hive/postgresql/generic dialect，Ctrl+Alt+F 触发格式化。
首屏 bundle 不包含 formatter 依赖。"
```

---

## Final Verification

- [ ] **All F1 tasks complete**

```bash
cd /opt/prod/s10/s10-stack
git log --oneline | grep -E "F1/T0[1-6]"
```

**Expected:** 看到 6 条 commits（T01–T06）。

- [ ] **Full test suite**

```bash
cd source/dts-platform-webapp
pnpm vitest run src/components/sql-ide
cd ../dts-platform
./mvnw -q test -Dtest='*SqlIde*'
```

**Expected:** 前后端测试全绿。

- [ ] **Manual smoke test**

- Flag off (`DTS_SQL_IDE_V2_ENABLED=false`) → `/explore/workbench` 仍是 QueryWorkbenchPage
- Flag on → 看到 SqlIde 骨架 + Monaco + 语法高亮 + 补全 + 快捷键 + 格式化

- [ ] **Update Sprint tracker**

把 `worklog/v2.2.3/sprint-11-202604/features/F1-架构骨架与Monaco编辑器/` 内 T01–T06 的 `**状态**` 从 `READY` 改为 `DONE`，Feature README 状态改为 `DONE`，并更新 `sprint-queue.md` F1 状态。

---

## Notes for the Executing Engineer

1. **项目约束**
   - Java 侧：禁用 `Optional.get()`，始终用 `Optional.orElseThrow()`（见 `CLAUDE.md`）
   - 提交信息首行不超过 72 字符，中文描述允许

2. **前端风格**
   - 本仓库用 **Biome** 做格式化（`pnpm format` / `pnpm lint`），每次提交前跑一遍
   - `@/` 是 `src/` 的别名；已在 `tsconfig.json` 配好
   - 所有新组件单文件 ≤ 300 行，超出就拆

3. **后端风格**
   - `ApiResponses.ok(...)` 的确切签名按 `SqlWorkbenchResource` 现有用法比对
   - 审计本期暂不埋（T01/T02 是骨架，审计在 F4/T20 统一补全）

4. **测试约定**
   - 前端单测：`vitest run <file>`（不是 jest）
   - 后端集成测试：继承 `@IntegrationTest`，遵循现有 `web/rest/**/*IT.java` 命名

5. **不要做的事**
   - 不要动 `SqlWorkbenchExperimental.tsx` 或 `SqlWorkbenchResource.java`（旧实现保持可用）
   - 不要删 `/api/sql/*` 的任何端点
   - 不要把 F2–F6 的内容提前做进来（Tab 持久化、Schema 树、ResultGrid 等均非本 plan 范围）

6. **实现中发现歧义时**
   - 回查 `worklog/v2.2.3/sprint-11-202604/plan.md`（总设计文档）
   - 或参考 Sprint README §技术选型部分
