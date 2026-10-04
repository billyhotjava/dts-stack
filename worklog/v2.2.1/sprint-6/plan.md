# CLI 工具抽象 + 批量导入 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 将 deploy.sh 抽象为通用 CLI 工具集，新增后端批量导入 API 和前端批量导入 UI。

**Architecture:** CLI 层提取共享库 + 3 个独立命令（dts-plan / dts-deploy / dts-manifest-gen），后端新增 ZIP 批量导入 endpoint 复用现有 importFromFiles 逻辑，前端新增双 Tab 批量导入弹窗（ZIP + 多文件编辑）通过 JSZip 统一为 ZIP 提交。

**Tech Stack:** Bash, Spring Boot 3.4.5 / Java 21, React / antd / JSZip, PostgreSQL

---

### Task 1: lib/dts-common.sh 共享函数库

**Files:**
- Create: `bin/lib/dts-common.sh`

**Step 1: Create shared library**

```bash
#!/usr/bin/env bash
# dts-common.sh — shared functions for DTS CLI tools
# Usage: source "${SCRIPT_DIR}/lib/dts-common.sh"

# ---- 颜色输出 ----
red()    { printf '\033[0;31m%s\033[0m\n' "$*"; }
green()  { printf '\033[0;32m%s\033[0m\n' "$*"; }
yellow() { printf '\033[0;33m%s\033[0m\n' "$*"; }

# CALLER_NAME 由调用方设置，默认取脚本文件名
: "${CALLER_NAME:=$(basename "${BASH_SOURCE[1]:-dts}")}"

info()   { echo "[$CALLER_NAME] $*"; }
warn()   { yellow "[$CALLER_NAME] WARNING: $*"; }
die()    { red "[$CALLER_NAME] ERROR: $*" >&2; exit 1; }

# ---- 环境变量校验 ----
require_env() {
  for var in "$@"; do
    [[ -n "${!var:-}" ]] || die "$var is required"
  done
}

# ---- curl 封装 ----
_DTS_INSECURE_FLAG=""
_DTS_DEPT_HEADER=""

dts_init_http() {
  API_URL="${API_BASE%/}/api"
  AUTH_HEADER="Authorization: Bearer $TOKEN"
  if [[ -n "${ACTIVE_DEPT:-}" ]]; then
    _DTS_DEPT_HEADER="X-Active-Dept: $ACTIVE_DEPT"
  fi
}

dts_curl_get() {
  local url="$1"
  local args=(-sS -H "$AUTH_HEADER" $_DTS_INSECURE_FLAG)
  if [[ -n "$_DTS_DEPT_HEADER" ]]; then
    args+=(-H "$_DTS_DEPT_HEADER")
  fi
  curl "${args[@]}" "$url"
}

dts_curl_post_json() {
  local url="$1"
  local data="$2"
  local args=(-sS -X POST -H "$AUTH_HEADER" -H "Content-Type: application/json" $_DTS_INSECURE_FLAG)
  if [[ -n "$_DTS_DEPT_HEADER" ]]; then
    args+=(-H "$_DTS_DEPT_HEADER")
  fi
  curl "${args[@]}" -d "$data" "$url"
}

# ---- JSON 字段提取 ----
json_extract() {
  local file="$1"
  local field="$2"
  if command -v jq >/dev/null 2>&1; then
    jq -r ".$field // empty" "$file" 2>/dev/null || true
  else
    python3 -c "
import sys, json
data = json.load(open('$file'))
val = data.get('$field', '')
if isinstance(val, dict):
    val = val.get('$field', '')
if val: print(val)
" 2>/dev/null || true
  fi
}

# ---- URL 编码 ----
url_encode() {
  python3 -c "import urllib.parse; print(urllib.parse.quote('$1'))"
}
```

**Step 2: Verify library is sourceable**

Run: `bash -c 'source bin/lib/dts-common.sh && info "lib loaded"'`
Expected: `[bash] lib loaded`

**Step 3: Commit**

```bash
git add bin/lib/dts-common.sh
git commit -m "feat(cli): add shared function library bin/lib/dts-common.sh"
```

---

### Task 2: dts-plan CLI

**Files:**
- Create: `bin/dts-plan`

**Step 1: Create dts-plan script**

Extract `find_plan_by_name()`, `resolve_unique_plan_name()`, `create_plan()` from `bin/project-progress/deploy.sh:112-185`. Add `list` action.

Subcommands:
- `create --name <name> [--domain <d>] [--scope <s>] [--tags <t>] [--content <c>]` → outputs `plan_id=<uuid>`
- `find --name <name>` → outputs plan_id or empty
- `list [--keyword <kw>]` → outputs JSON array

Support `--dry-run`, `--insecure`. Read env: `API_BASE`, `TOKEN`, `ACTIVE_DEPT`.

**Step 2: Test create with dry-run**

Run: `API_BASE=https://example.com TOKEN=fake bin/dts-plan create --name "test" --dry-run`
Expected: `[dts-plan] DRY-RUN: would create plan 'test'`

**Step 3: Commit**

```bash
git add bin/dts-plan
git commit -m "feat(cli): add dts-plan CLI for Plan CRUD"
```

---

### Task 3: dts-deploy 编排器

**Files:**
- Create: `bin/dts-deploy`

**Step 1: Create dts-deploy script**

Two modes:
- `--config <deploy.conf>` — source conf file, resolve MANIFEST_PATH relative to conf dir
- `--plan-name <name> --manifest <path> [--domain <d>] [--scope <s>]` — direct params

Flow:
1. `source lib/dts-common.sh`
2. Parse args / load config
3. Call `bin/dts-plan create --name "$PLAN_NAME" ...` and capture plan_id
4. Call `bin/dts-dbt-import --plan-id "$PLAN_ID" --manifest "$MANIFEST_PATH" ...`

Pass through: `--dry-run`, `--insecure`, `--skip-existing`, `--show-response`

**Step 2: Test with dry-run**

Run: `API_BASE=https://example.com TOKEN=fake SOURCE_DATA_SOURCE_ID=00000000-0000-0000-0000-000000000001 bin/dts-deploy --plan-name "test" --manifest services/dts-dbt/deploy/project-progress/manifest/models.tsv --dry-run`
Expected: dry-run output from both dts-plan and dts-dbt-import

**Step 3: Commit**

```bash
git add bin/dts-deploy
git commit -m "feat(cli): add dts-deploy orchestrator"
```

---

### Task 4: dts-manifest-gen TSV 生成器

**Files:**
- Create: `bin/dts-manifest-gen`

**Step 1: Create manifest generator**

Logic:
1. Scan `services/dts-dbt/models/` for `*.sql` files
2. Parse dbt `config()` block via regex: extract `materialized` and `tags` array
3. Filter by `--tag` (match in parsed tags) and/or `--path` (match directory prefix)
4. Infer layer from parent directory name: `dim/` → DWD, `dwd/` → DWD, `dws/` → DWS, `ads/` → ADS
5. Output TSV to `--output` or stdout

TSV header: `name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path`

**Step 2: Test generation**

Run: `bin/dts-manifest-gen --tag project-management | head -5`
Expected: TSV header + rows for project-management tagged models (dim_completion_status, biz_dwd_project_node, etc.)

**Step 3: Compare with existing manifest**

Run: `diff <(bin/dts-manifest-gen --tag project-management | sort -t$'\t' -k1,1) <(tail -n+2 services/dts-dbt/deploy/project-progress/manifest/models.tsv | sort -t$'\t' -k1,1) | head -20`
Expected: should be mostly equivalent (description field may differ)

**Step 4: Commit**

```bash
git add bin/dts-manifest-gen
git commit -m "feat(cli): add dts-manifest-gen for auto TSV generation from dbt models"
```

---

### Task 5: dts-dbt-import 最小改造

**Files:**
- Modify: `bin/dts-dbt-import:11-12,29-34`

**Step 1: Add source of common library**

Replace lines 29-34 (inline color functions) with:

```bash
source "${SCRIPT_DIR}/lib/dts-common.sh"
CALLER_NAME="dts-dbt-import"
```

Keep all other code unchanged.

**Step 2: Verify dry-run still works**

Run: `API_BASE=https://example.com TOKEN=fake PLAN_ID=00000000-0000-0000-0000-000000000001 SOURCE_DATA_SOURCE_ID=00000000-0000-0000-0000-000000000002 bin/dts-dbt-import --manifest services/dts-dbt/deploy/project-progress/manifest/models.tsv --dry-run`
Expected: same output as before refactor

**Step 3: Commit**

```bash
git add bin/dts-dbt-import
git commit -m "refactor(cli): dts-dbt-import sources shared lib"
```

---

### Task 6: 业务文件迁移

**Files:**
- Create: `services/dts-dbt/deploy/project-progress/deploy.conf`
- Move: `bin/project-progress/manifest/models.tsv` → `services/dts-dbt/deploy/project-progress/manifest/models.tsv`
- Move: `bin/project-progress/seed-ods-project-progress.sql` → `services/dts-dbt/deploy/project-progress/`
- Move: `bin/project-progress/validate-indicators.sql` → `services/dts-dbt/deploy/project-progress/`
- Move: `bin/project-progress/screen-template-project-progress.json` → `services/dts-dbt/deploy/project-progress/`
- Delete: `bin/project-progress/deploy.sh`
- Delete: `bin/project-progress/` (empty dir)

**Step 1: Create deploy.conf**

```ini
PLAN_NAME="项目进度分析"
PLAN_DOMAIN="project-management"
PLAN_SCOPE="项目主体域数据建模与指标分析"
PLAN_TAGS="project-management,sprint-5"
PLAN_CONTENT="Sprint-5 项目主体域数据建模，包含 11 个 dbt 模型覆盖 35 个指标"
MANIFEST_PATH="manifest/models.tsv"
```

**Step 2: Move files**

```bash
mkdir -p services/dts-dbt/deploy/project-progress/manifest
mv bin/project-progress/manifest/models.tsv services/dts-dbt/deploy/project-progress/manifest/
mv bin/project-progress/seed-ods-project-progress.sql services/dts-dbt/deploy/project-progress/
mv bin/project-progress/validate-indicators.sql services/dts-dbt/deploy/project-progress/
mv bin/project-progress/screen-template-project-progress.json services/dts-dbt/deploy/project-progress/
rm bin/project-progress/deploy.sh
rmdir bin/project-progress/manifest 2>/dev/null; rmdir bin/project-progress 2>/dev/null
```

**Step 3: Verify dts-deploy works with new location**

Run: `API_BASE=https://example.com TOKEN=fake SOURCE_DATA_SOURCE_ID=00000000-0000-0000-0000-000000000001 bin/dts-deploy --config services/dts-dbt/deploy/project-progress/deploy.conf --dry-run`
Expected: successful dry-run

**Step 4: Commit**

```bash
git add services/dts-dbt/deploy/ bin/project-progress/
git commit -m "refactor: migrate business files from bin/ to services/dts-dbt/deploy/"
```

---

### Task 7: 后端 batch-import API — Service 层

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java`

**Step 1: Add BatchImportResult and BatchImportDetail records**

Add inside `ModelingSqlModelService` class:

```java
public record BatchImportResult(
    int total,
    int imported,
    int skipped,
    int failed,
    List<BatchImportDetail> details
) {}

public record BatchImportDetail(
    String name,
    String layer,
    String status,
    String message
) {}
```

**Step 2: Add batchImportFromArchive method**

```java
public BatchImportResult batchImportFromArchive(
    UUID planId,
    UUID defaultSourceDataSourceId,
    boolean skipExisting,
    Path archivePath,
    String activeDept
) {
    // 1. Unzip to temp dir
    // 2. Find first .tsv file
    // 3. Parse TSV line by line (skip header)
    // 4. For each row: read SQL file from zip, call importFromFiles()
    //    - if skipExisting and name exists in plan, mark as skipped
    //    - catch exceptions per row, mark as failed
    // 5. Return BatchImportResult
    // 6. Cleanup temp dir in finally block
}
```

Key implementation details:
- Use `java.util.zip.ZipInputStream` to unzip
- TSV parsing: split by `\t`, same column order as `dts-dbt-import` manifest
- Row-level sourceDataSourceId falls back to defaultSourceDataSourceId
- Check existing: use `sqlModelRepository.findFirstByPlanIdAndNameIgnoreCase(planId, name)`

**Step 3: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/service/modeling/ModelingSqlModelService.java
git commit -m "feat(api): add batchImportFromArchive service method"
```

---

### Task 8: 后端 batch-import API — Controller 层

**Files:**
- Modify: `source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java`

**Step 1: Add batchImport endpoint**

Insert after existing `importModel` method (after line 176):

```java
@PostMapping(value = "/batch-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
public ApiResponse<BatchImportResult> batchImport(
    @RequestParam UUID planId,
    @RequestParam UUID sourceDataSourceId,
    @RequestParam("archive") MultipartFile archive,
    @RequestParam(required = false, defaultValue = "false") boolean skipExisting,
    @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
) throws IOException {
    if (archive.isEmpty()) {
        return ApiResponses.badRequest("archive file is required");
    }
    Path tempFile = Files.createTempFile("batch-import-", ".zip");
    try {
        archive.transferTo(tempFile);
        BatchImportResult result = sqlModelService.batchImportFromArchive(
            planId, sourceDataSourceId, skipExisting, tempFile, activeDept
        );
        auditService.audit("BATCH_IMPORT", "modeling.sql-model",
            "plan=" + planId + " total=" + result.total() + " imported=" + result.imported());
        return ApiResponses.ok(result);
    } finally {
        Files.deleteIfExists(tempFile);
    }
}
```

Add imports at top:
```java
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.BatchImportResult;
```

**Step 2: Compile check**

Run: `cd source/dts-platform && ./mvnw compile -pl . -q`
Expected: BUILD SUCCESS

**Step 3: Commit**

```bash
git add source/dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingSqlModelResource.java
git commit -m "feat(api): add POST /batch-import endpoint"
```

---

### Task 9: 前端 — 安装 JSZip + API 函数

**Files:**
- Modify: `source/dts-platform-webapp/package.json`
- Modify: `source/dts-platform-webapp/src/api/platformApi.ts`

**Step 1: Install jszip**

Run: `cd source/dts-platform-webapp && npm install jszip`

**Step 2: Add API function**

In `platformApi.ts`, after the existing `importSqlModel` line (line 124), add:

```typescript
export const batchImportSqlModels = (data: FormData) =>
	api.post({ url: "/modeling/sql-models/batch-import", data });
```

**Step 3: Commit**

```bash
git add source/dts-platform-webapp/package.json source/dts-platform-webapp/package-lock.json source/dts-platform-webapp/src/api/platformApi.ts
git commit -m "feat(web): add jszip dependency and batchImport API function"
```

---

### Task 10: 前端 — 批量导入 Modal 组件

**Files:**
- Create: `source/dts-platform-webapp/src/pages/modeling/BatchImportModal.tsx`

**Step 1: Create BatchImportModal component**

Props:
```typescript
interface BatchImportModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  spaces: Array<{ id?: string; name?: string }>;
  dataSources: InfraDataSource[];
  activeSpaceId?: string;
}
```

Component structure:
- Two tabs: "ZIP 上传" / "文件选择"
- Shared form fields at top: planId (Select), sourceDataSourceId (Select), skipExisting (Checkbox)
- Tab 1: Upload.Dragger for .zip, "下载 TSV 模板" link
- Tab 2: Upload for multiple .sql files + editable Table (name, layer, materialized, tags columns)
- Submit handler: Tab 1 sends ZIP directly; Tab 2 uses JSZip to pack TSV + SQL files into ZIP
- Result view: Table showing per-row import status after completion

File name inference logic (Tab 2):
```typescript
const inferLayer = (filename: string): string => {
  const name = filename.replace(/\.sql$/i, "").toLowerCase();
  if (name.startsWith("ads_") || name.startsWith("biz_ads_")) return "ADS";
  if (name.startsWith("dws_") || name.startsWith("biz_dws_")) return "DWS";
  if (name.startsWith("dim_")) return "DWD";
  return "DWD";
};
```

TSV template download:
```typescript
const downloadTemplate = () => {
  const header = "name\tlayer\tsql_path\tsource_data_source_id\talias\tschema_name\tmaterialized\ttags\tstatus\tenabled\towner_dept\tdescription\tcsv_path\n";
  const blob = new Blob([header], { type: "text/tab-separated-values" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url; a.download = "models.tsv"; a.click();
  URL.revokeObjectURL(url);
};
```

**Step 2: Verify component compiles**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: no errors related to BatchImportModal

**Step 3: Commit**

```bash
git add source/dts-platform-webapp/src/pages/modeling/BatchImportModal.tsx
git commit -m "feat(web): add BatchImportModal component with ZIP and multi-file tabs"
```

---

### Task 11: 前端 — 集成到 SqlModelingPage

**Files:**
- Modify: `source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx`

**Step 1: Add import and state**

At imports (top of file), add:
```typescript
import BatchImportModal from "./BatchImportModal";
```

Near existing `importOpen` state (line 422), add:
```typescript
const [batchImportOpen, setBatchImportOpen] = useState(false);
```

**Step 2: Add menu item**

In `modelMenuItems` array (after line 1663), insert:
```typescript
{
  key: "batch-import",
  icon: <ImportOutlined />,
  label: "批量导入",
  disabled: !workspaceOk,
  onClick: () => setBatchImportOpen(true),
},
```

**Step 3: Add Modal to JSX**

Near the existing import Modal (around line 2958), add:
```tsx
<BatchImportModal
  open={batchImportOpen}
  onClose={() => setBatchImportOpen(false)}
  onSuccess={() => { setBatchImportOpen(false); loadModels(); }}
  spaces={spaces}
  dataSources={dataSources}
  activeSpaceId={activeSpace?.id}
/>
```

**Step 4: Verify page compiles**

Run: `cd source/dts-platform-webapp && npx tsc --noEmit --pretty 2>&1 | head -20`
Expected: no errors

**Step 5: Commit**

```bash
git add source/dts-platform-webapp/src/pages/modeling/SqlModelingPage.tsx
git commit -m "feat(web): integrate BatchImportModal into SqlModelingPage toolbar"
```

---

### Task 12: worklog 路径更新

**Files:**
- Modify: `worklog/v2.2.1/sprint-5/README.md`
- Modify: `worklog/v2.2.1/sprint-5/it/README.md`

**Step 1: Update path references**

In both files, replace all occurrences of `bin/project-progress/` with `services/dts-dbt/deploy/project-progress/`.

Update sprint-5 README product table to reference new locations.
Update sprint-5 it/README command examples to use new paths.

**Step 2: Commit**

```bash
git add worklog/v2.2.1/sprint-5/
git commit -m "docs: update sprint-5 path references to new deploy/ location"
```
