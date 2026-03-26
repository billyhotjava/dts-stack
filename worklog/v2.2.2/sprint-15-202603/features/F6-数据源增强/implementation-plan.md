# Excel/CSV 文件上传数据源 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为大屏设计器新增 `uploaded` 数据源类型，支持用户上传 Excel/CSV 文件，前端解析 + 字段编辑后提交后端加密存储，运行时解密返回数据供组件渲染。

**Architecture:** 前端用 SheetJS + PapaParse 解析文件，用户在字段编辑器中调整列名/类型/排除列后，将结构化数据 POST 到后端。后端 AES-256-GCM 加密存入 PostgreSQL `bytea` 字段，运行时解密返回标准 `{ cols, rows }` 格式。前端复用现有 DataLayer + FieldMapping 管线渲染数据。

**Tech Stack:** SheetJS (xlsx)、PapaParse、Java AES-256-GCM (JDK内置)、Spring Boot REST、Liquibase、React

---

## 文件结构

### 后端新建

| 文件 | 职责 |
|------|------|
| `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenDataset.java` | JPA 实体 |
| `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenDatasetRepository.java` | JPA Repository |
| `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenDatasetService.java` | 加密/解密 + CRUD 业务逻辑 |
| `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenDatasetResource.java` | REST 端点 |
| `source/dts-analytics/src/main/resources/config/liquibase/changelog/0036_screen_uploaded_dataset.xml` | 建表迁移 |

### 前端新建

| 文件 | 职责 |
|------|------|
| `source/dts-analytics-webapp/modern/src/pages/screens/components/UploadedDataEditor.tsx` | 上传 + 字段编辑 + 预览 UI |
| `source/dts-analytics-webapp/modern/src/pages/screens/components/DatasetPicker.tsx` | 已有数据集选择器 |

### 前端修改

| 文件 | 修改内容 |
|------|----------|
| `types.ts:185,238` | 新增 `'uploaded'` 类型 + `uploadedConfig` 字段 |
| `useCardDataSource.ts:187,605` | 新增 `'uploaded'` fetch 分支 |
| `PropertyPanel.tsx:4629,4708` | 新增下拉选项 + setType case + 编辑器渲染 |
| `analyticsApi.ts:1618` | 新增 dataset CRUD API 方法 |
| `package.json` | 添加 `xlsx`、`papaparse` 依赖 |

---

## Task 1: 后端 — 数据库表 + Entity + Repository

**Files:**
- Create: `source/dts-analytics/src/main/resources/config/liquibase/changelog/0036_screen_uploaded_dataset.xml`
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenDataset.java`
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenDatasetRepository.java`
- Modify: `source/dts-analytics/src/main/resources/config/liquibase/master.xml` (引入新 changelog)

- [ ] **Step 1: 创建 Liquibase changelog**

```xml
<?xml version="1.0" encoding="utf-8"?>
<databaseChangeLog
    xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.22.xsd">

    <changeSet id="0036-create-screen-uploaded-dataset" author="billy">
        <createTable tableName="screen_uploaded_dataset">
            <column name="id" type="bigint" autoIncrement="true">
                <constraints primaryKey="true" nullable="false"/>
            </column>
            <column name="uuid" type="varchar(36)">
                <constraints nullable="false" unique="true"/>
            </column>
            <column name="name" type="varchar(255)">
                <constraints nullable="false"/>
            </column>
            <column name="original_file_name" type="varchar(255)"/>
            <column name="file_type" type="varchar(10)"/>
            <column name="columns_meta" type="text">
                <constraints nullable="false"/>
            </column>
            <column name="encrypted_data" type="bytea">
                <constraints nullable="false"/>
            </column>
            <column name="row_count" type="integer" defaultValueNumeric="0"/>
            <column name="file_size" type="bigint" defaultValueNumeric="0"/>
            <column name="created_by" type="bigint"/>
            <column name="created_at" type="timestamp" defaultValueComputed="now()"/>
            <column name="updated_at" type="timestamp" defaultValueComputed="now()"/>
        </createTable>
        <createIndex tableName="screen_uploaded_dataset" indexName="idx_screen_dataset_uuid">
            <column name="uuid"/>
        </createIndex>
    </changeSet>
</databaseChangeLog>
```

- [ ] **Step 2: 在 master.xml 中引入新 changelog**

在 `master.xml` 末尾 `</databaseChangeLog>` 前添加：
```xml
<include file="config/liquibase/changelog/0036_screen_uploaded_dataset.xml" relativeToChangelogFile="false"/>
```

- [ ] **Step 3: 创建 JPA Entity**

```java
package com.yuzhi.dts.analytics.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "screen_uploaded_dataset")
public class AnalyticsScreenDataset implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uuid", nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "file_type", length = 10)
    private String fileType;

    @Column(name = "columns_meta", columnDefinition = "text", nullable = false)
    private String columnsMeta;  // JSON string

    @Column(name = "encrypted_data", columnDefinition = "bytea", nullable = false)
    private byte[] encryptedData;

    @Column(name = "row_count")
    private Integer rowCount;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters ...
}
```

- [ ] **Step 4: 创建 Repository**

```java
package com.yuzhi.dts.analytics.repository;

import com.yuzhi.dts.analytics.domain.AnalyticsScreenDataset;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsScreenDatasetRepository extends JpaRepository<AnalyticsScreenDataset, Long> {
    Optional<AnalyticsScreenDataset> findByUuid(String uuid);
    List<AnalyticsScreenDataset> findAllByOrderByCreatedAtDesc();
    void deleteByUuid(String uuid);
}
```

- [ ] **Step 5: 启动验证表创建成功**

Run: 启动 dts-analytics 服务，检查日志确认 Liquibase 迁移成功
Expected: 表 `screen_uploaded_dataset` 创建成功，无报错

- [ ] **Step 6: Commit**

```bash
git add source/dts-analytics/src/main/resources/config/liquibase/changelog/0036_screen_uploaded_dataset.xml \
      source/dts-analytics/src/main/resources/config/liquibase/master.xml \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/domain/AnalyticsScreenDataset.java \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/repository/AnalyticsScreenDatasetRepository.java
git commit -m "feat(screen): add screen_uploaded_dataset table and entity"
```

---

## Task 2: 后端 — Service 加密/解密 + REST 端点

**Files:**
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenDatasetService.java`
- Create: `source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenDatasetResource.java`

- [ ] **Step 1: 创建 Service（含 AES 加密/解密）**

```java
package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenDataset;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenDatasetRepository;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreenDatasetService {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final AnalyticsScreenDatasetRepository repository;
    private final ObjectMapper objectMapper;
    private final SecretKey secretKey;

    public ScreenDatasetService(
            AnalyticsScreenDatasetRepository repository,
            ObjectMapper objectMapper,
            @Value("${screen.dataset.encryption-key:default-32-char-key-change-me!!}") String encryptionKey) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        // 确保密钥为 32 字节（AES-256）
        byte[] keyBytes = new byte[32];
        byte[] src = encryptionKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(src, 0, keyBytes, 0, Math.min(src.length, 32));
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    @Transactional
    public AnalyticsScreenDataset create(String name, String originalFileName,
            String fileType, String columnsMetaJson, String rowsDataJson,
            int rowCount, long fileSize, Long userId) {
        AnalyticsScreenDataset ds = new AnalyticsScreenDataset();
        ds.setUuid(UUID.randomUUID().toString());
        ds.setName(name);
        ds.setOriginalFileName(originalFileName);
        ds.setFileType(fileType);
        ds.setColumnsMeta(columnsMetaJson);
        ds.setEncryptedData(encrypt(rowsDataJson));
        ds.setRowCount(rowCount);
        ds.setFileSize(fileSize);
        ds.setCreatedBy(userId);
        return repository.save(ds);
    }

    public List<AnalyticsScreenDataset> listAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public AnalyticsScreenDataset getByUuid(String uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("Dataset not found: " + uuid));
    }

    /** 解密返回行数据 JSON 字符串 */
    public String decryptData(AnalyticsScreenDataset dataset) {
        return decrypt(dataset.getEncryptedData());
    }

    @Transactional
    public void deleteByUuid(String uuid) {
        repository.deleteByUuid(uuid);
    }

    private byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            // IV + ciphertext
            return ByteBuffer.allocate(GCM_IV_LENGTH + encrypted.length)
                    .put(iv).put(encrypted).array();
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private String decrypt(byte[] data) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(data);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buf.get(iv);
            byte[] ciphertext = new byte[buf.remaining()];
            buf.get(ciphertext);
            Cipher cipher = Cipher.getInstance(AES_GCM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
```

- [ ] **Step 2: 创建 REST Resource**

```java
package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenDataset;
import com.yuzhi.dts.analytics.service.ScreenDatasetService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/screen-datasets")
public class ScreenDatasetResource {

    private final ScreenDatasetService service;
    private final ObjectMapper objectMapper;

    public ScreenDatasetResource(ScreenDatasetService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /** 创建数据集 — 接收前端解析后的 JSON 数据 */
    @PostMapping
    @Transactional
    public ResponseEntity<JsonNode> create(@RequestBody JsonNode body,
            MetabaseAuth auth) {
        String name = body.path("name").asText("未命名数据集");
        String originalFileName = body.path("originalFileName").asText("");
        String fileType = body.path("fileType").asText("");
        JsonNode columnsMeta = body.path("columnsMeta");
        JsonNode rowsData = body.path("rows");
        int rowCount = rowsData.isArray() ? rowsData.size() : 0;
        long fileSize = body.path("fileSize").asLong(0);
        Long userId = auth != null ? auth.getUserId() : null;

        AnalyticsScreenDataset ds = service.create(
                name, originalFileName, fileType,
                columnsMeta.toString(), rowsData.toString(),
                rowCount, fileSize, userId);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("uuid", ds.getUuid());
        result.put("name", ds.getName());
        result.put("rowCount", ds.getRowCount());
        return ResponseEntity.ok(result);
    }

    /** 列出所有数据集（仅 meta，不含数据） */
    @GetMapping
    public ResponseEntity<JsonNode> list() {
        List<AnalyticsScreenDataset> datasets = service.listAll();
        var arr = objectMapper.createArrayNode();
        for (var ds : datasets) {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("uuid", ds.getUuid());
            node.put("name", ds.getName());
            node.put("originalFileName", ds.getOriginalFileName());
            node.put("fileType", ds.getFileType());
            node.set("columnsMeta", parseJson(ds.getColumnsMeta()));
            node.put("rowCount", ds.getRowCount());
            node.put("fileSize", ds.getFileSize());
            node.put("createdAt", ds.getCreatedAt().toString());
            arr.add(node);
        }
        return ResponseEntity.ok(arr);
    }

    /** 读取数据集数据（解密） */
    @GetMapping("/{uuid}/data")
    public ResponseEntity<JsonNode> getData(@PathVariable String uuid) {
        AnalyticsScreenDataset ds = service.getByUuid(uuid);
        String rowsJson = service.decryptData(ds);

        ObjectNode result = objectMapper.createObjectNode();
        result.set("cols", parseJson(ds.getColumnsMeta()));
        result.set("rows", parseJson(rowsJson));
        result.put("rowCount", ds.getRowCount());
        return ResponseEntity.ok(result);
    }

    /** 删除数据集 */
    @DeleteMapping("/{uuid}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable String uuid) {
        service.deleteByUuid(uuid);
        return ResponseEntity.noContent().build();
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }
}
```

- [ ] **Step 3: 配置加密密钥**

在 `application.yml` 中添加：
```yaml
screen:
  dataset:
    encryption-key: ${SCREEN_DATASET_KEY:your-32-character-secret-key-here}
```

- [ ] **Step 4: 启动验证 API 端点可达**

Run: `curl -X GET http://localhost:3000/api/screen-datasets` (需带认证)
Expected: 返回空数组 `[]`

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/service/ScreenDatasetService.java \
      source/dts-analytics/src/main/java/com/yuzhi/dts/analytics/web/rest/ScreenDatasetResource.java
git commit -m "feat(screen): add screen dataset REST API with AES encryption"
```

---

## Task 3: 前端 — 安装依赖 + 类型定义扩展

**Files:**
- Modify: `source/dts-analytics-webapp/modern/package.json`
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/types.ts` (line 185, ~238)
- Modify: `source/dts-analytics-webapp/modern/src/api/analyticsApi.ts` (~line 1618)

- [ ] **Step 1: 安装 SheetJS 和 PapaParse**

```bash
cd source/dts-analytics-webapp/modern
npm install xlsx@0.18.5 papaparse@5.4.1
npm install -D @types/papaparse
```

注意: SheetJS (xlsx) 自带类型定义，无需额外 @types 包。

- [ ] **Step 2: 扩展 DataSourceType 和 DataSourceConfig**

在 `types.ts` line 185，`DataSourceType` 联合类型中添加 `'uploaded'`：
```typescript
export type DataSourceType = 'static' | 'api' | 'card' | 'sql' | 'dataset' | 'metric' | 'database' | 'uploaded';
```

在 `DataSourceConfig` 接口（~line 238）中添加：
```typescript
    uploadedConfig?: {
        datasetId: string;    // uuid
        datasetName?: string; // 显示用
    };
```

- [ ] **Step 3: 在 analyticsApi.ts 中添加数据集 API**

在 `analyticsApi` 导出对象中添加：
```typescript
    // Screen uploaded datasets
    createScreenDataset: (body: {
        name: string;
        originalFileName: string;
        fileType: string;
        columnsMeta: Array<{ name: string; displayName: string; type: string }>;
        rows: unknown[][];
        fileSize: number;
    }) => sendJson<{ uuid: string; name: string; rowCount: number }>(
        '/analytics/api/screen-datasets', body),
    listScreenDatasets: () => fetchJson<Array<{
        uuid: string;
        name: string;
        originalFileName: string;
        fileType: string;
        columnsMeta: Array<{ name: string; displayName: string; type: string }>;
        rowCount: number;
        fileSize: number;
        createdAt: string;
    }>>('/analytics/api/screen-datasets'),
    getScreenDatasetData: (uuid: string) => fetchJson<{
        cols: Array<{ name: string; displayName: string; type: string }>;
        rows: unknown[][];
        rowCount: number;
    }>(`/analytics/api/screen-datasets/${uuid}/data`),
    deleteScreenDataset: (uuid: string) =>
        requestJson<void>(`/analytics/api/screen-datasets/${uuid}`, 'DELETE'),
```

- [ ] **Step 4: 验证 TypeScript 编译通过**

Run: `cd source/dts-analytics-webapp/modern && npx tsc --noEmit`
Expected: 无类型错误

- [ ] **Step 5: Commit**

```bash
git add source/dts-analytics-webapp/modern/package.json \
      source/dts-analytics-webapp/modern/package-lock.json \
      source/dts-analytics-webapp/modern/src/pages/screens/types.ts \
      source/dts-analytics-webapp/modern/src/api/analyticsApi.ts
git commit -m "feat(screen): add uploaded data source type and API client"
```

---

## Task 4: 前端 — UploadedDataEditor 组件（上传 + 字段编辑 + 预览）

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/components/UploadedDataEditor.tsx`

- [ ] **Step 1: 创建 UploadedDataEditor 组件**

组件功能：
1. 文件选择（拖拽或点击，支持 .xlsx/.xls/.csv）
2. SheetJS/PapaParse 浏览器端解析
3. Excel 多 Sheet 选择
4. 起始行选择（跳过非数据行）
5. 列编辑器：改显示名、改类型（文本/数值/日期/布尔）、排除列
6. 前 10 行数据预览
7. 数据集命名
8. "上传并绑定"按钮 → 调用 API 存储 → 回写 dataSource config

```tsx
import { useState, useCallback, useRef, useMemo } from 'react';
import * as XLSX from 'xlsx';
import Papa from 'papaparse';
import { analyticsApi } from '../../../../api/analyticsApi';

interface ColumnDef {
    name: string;        // 原始列名
    displayName: string; // 用户可编辑
    type: string;        // text | number | date | boolean
    excluded: boolean;
}

interface ParsedData {
    columns: ColumnDef[];
    rows: unknown[][];
    sheetNames: string[];
    totalRows: number;
    fileSize: number;
}

interface UploadedDataEditorProps {
    onBind: (config: { datasetId: string; datasetName: string }) => void;
    existingDatasetId?: string;
}

export function UploadedDataEditor({ onBind, existingDatasetId }: UploadedDataEditorProps) {
    // --- 状态 ---
    const [file, setFile] = useState<File | null>(null);
    const [parsed, setParsed] = useState<ParsedData | null>(null);
    const [columns, setColumns] = useState<ColumnDef[]>([]);
    const [selectedSheet, setSelectedSheet] = useState(0);
    const [startRow, setStartRow] = useState(1); // 1-based
    const [datasetName, setDatasetName] = useState('');
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const inputRef = useRef<HTMLInputElement>(null);

    // --- 文件解析 ---
    const parseFile = useCallback(async (f: File, sheetIdx = 0, skipRows = 1) => {
        setError(null);
        try {
            const ext = f.name.split('.').pop()?.toLowerCase();
            if (ext === 'csv') {
                const text = await f.text();
                const result = Papa.parse(text, { header: false, skipEmptyLines: true });
                const allRows = result.data as string[][];
                if (allRows.length < skipRows) {
                    setError('文件行数不足'); return;
                }
                const headerRow = allRows[skipRows - 1] as string[];
                const dataRows = allRows.slice(skipRows);
                const cols: ColumnDef[] = headerRow.map((h, i) => ({
                    name: h || `col_${i + 1}`,
                    displayName: h || `列${i + 1}`,
                    type: inferType(dataRows.map(r => r[i])),
                    excluded: false,
                }));
                setParsed({ columns: cols, rows: dataRows, sheetNames: ['CSV'], totalRows: dataRows.length, fileSize: f.size });
                setColumns(cols);
            } else {
                const buf = await f.arrayBuffer();
                const wb = XLSX.read(buf, { type: 'array' });
                const sheetName = wb.SheetNames[sheetIdx] || wb.SheetNames[0];
                const ws = wb.Sheets[sheetName];
                const allRows: unknown[][] = XLSX.utils.sheet_to_json(ws, { header: 1 });
                if (allRows.length < skipRows) {
                    setError('文件行数不足'); return;
                }
                const headerRow = allRows[skipRows - 1] as string[];
                const dataRows = allRows.slice(skipRows);
                const cols: ColumnDef[] = headerRow.map((h, i) => ({
                    name: String(h ?? `col_${i + 1}`),
                    displayName: String(h ?? `列${i + 1}`),
                    type: inferType(dataRows.map(r => (r as unknown[])[i])),
                    excluded: false,
                }));
                setParsed({
                    columns: cols, rows: dataRows as unknown[][],
                    sheetNames: wb.SheetNames, totalRows: dataRows.length, fileSize: f.size,
                });
                setColumns(cols);
            }
            if (!datasetName) setDatasetName(f.name.replace(/\.\w+$/, ''));
        } catch (e) {
            setError(`解析失败: ${e instanceof Error ? e.message : String(e)}`);
        }
    }, [datasetName]);

    // --- 文件选择 handler ---
    const handleFileChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
        const f = e.target.files?.[0];
        if (!f) return;
        setFile(f);
        setSelectedSheet(0);
        setStartRow(1);
        parseFile(f, 0, 1);
    }, [parseFile]);

    // --- Sheet / 起始行变化时重新解析 ---
    const handleSheetChange = useCallback((idx: number) => {
        setSelectedSheet(idx);
        if (file) parseFile(file, idx, startRow);
    }, [file, startRow, parseFile]);

    const handleStartRowChange = useCallback((row: number) => {
        setStartRow(row);
        if (file) parseFile(file, selectedSheet, row);
    }, [file, selectedSheet, parseFile]);

    // --- 列编辑 ---
    const updateColumn = useCallback((index: number, updates: Partial<ColumnDef>) => {
        setColumns(prev => prev.map((c, i) => i === index ? { ...c, ...updates } : c));
    }, []);

    // --- 活跃列（未排除的） ---
    const activeCols = useMemo(() => columns.filter(c => !c.excluded), [columns]);

    // --- 预览行（前 10 行，过滤排除列） ---
    const previewRows = useMemo(() => {
        if (!parsed) return [];
        const activeIndices = columns.map((c, i) => c.excluded ? -1 : i).filter(i => i >= 0);
        return parsed.rows.slice(0, 10).map(row =>
            activeIndices.map(i => (row as unknown[])[i])
        );
    }, [parsed, columns]);

    // --- 上传并绑定 ---
    const handleUpload = useCallback(async () => {
        if (!parsed) return;
        setUploading(true);
        setError(null);
        try {
            const activeIndices = columns.map((c, i) => c.excluded ? -1 : i).filter(i => i >= 0);
            const meta = activeCols.map(c => ({ name: c.name, displayName: c.displayName, type: c.type }));
            const rows = parsed.rows.map(row =>
                activeIndices.map(i => (row as unknown[])[i])
            );
            const result = await analyticsApi.createScreenDataset({
                name: datasetName || '未命名数据集',
                originalFileName: file?.name || '',
                fileType: file?.name.split('.').pop()?.toLowerCase() || '',
                columnsMeta: meta,
                rows,
                fileSize: parsed.fileSize,
            });
            onBind({ datasetId: result.uuid, datasetName: datasetName || result.name });
        } catch (e) {
            setError(`上传失败: ${e instanceof Error ? e.message : String(e)}`);
        } finally {
            setUploading(false);
        }
    }, [parsed, columns, activeCols, datasetName, file, onBind]);

    // --- 渲染 --- (参考 PropertyPanel 的 StaticDataEditor 样式)
    // ... 完整 JSX 渲染代码在实现时编写，包含：
    // 1. 拖拽上传区域
    // 2. Sheet 选择器 + 起始行选择器
    // 3. 列编辑表格（显示名输入框、类型下拉、排除按钮）
    // 4. 数据预览表格（前 10 行）
    // 5. 统计信息（总行数、列数、文件大小）
    // 6. 数据集名称输入
    // 7. "上传并绑定"按钮
}

/** 从样本数据推断列类型 */
function inferType(sample: unknown[]): string {
    const nonNull = sample.filter(v => v != null && v !== '').slice(0, 20);
    if (nonNull.length === 0) return 'text';
    const allNum = nonNull.every(v => !isNaN(Number(v)));
    if (allNum) return 'number';
    const datePattern = /^\d{4}[-/]\d{1,2}[-/]\d{1,2}/;
    const allDate = nonNull.every(v => datePattern.test(String(v)));
    if (allDate) return 'date';
    const allBool = nonNull.every(v => ['true','false','是','否','1','0'].includes(String(v).toLowerCase()));
    if (allBool) return 'boolean';
    return 'text';
}
```

- [ ] **Step 2: 验证组件 TypeScript 编译**

Run: `cd source/dts-analytics-webapp/modern && npx tsc --noEmit`
Expected: 无类型错误

- [ ] **Step 3: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/UploadedDataEditor.tsx
git commit -m "feat(screen): add UploadedDataEditor component with Excel/CSV parsing"
```

---

## Task 5: 前端 — DatasetPicker 已有数据集选择器

**Files:**
- Create: `source/dts-analytics-webapp/modern/src/pages/screens/components/DatasetPicker.tsx`

- [ ] **Step 1: 创建 DatasetPicker 组件**

功能：搜索/选择已上传的数据集列表，显示名称、行数、上传时间

```tsx
import { useState, useEffect, useCallback } from 'react';
import { analyticsApi } from '../../../../api/analyticsApi';

interface DatasetMeta {
    uuid: string;
    name: string;
    originalFileName: string;
    fileType: string;
    columnsMeta: Array<{ name: string; displayName: string; type: string }>;
    rowCount: number;
    fileSize: number;
    createdAt: string;
}

interface DatasetPickerProps {
    onSelect: (ds: { datasetId: string; datasetName: string }) => void;
    selectedId?: string;
}

export function DatasetPicker({ onSelect, selectedId }: DatasetPickerProps) {
    const [datasets, setDatasets] = useState<DatasetMeta[]>([]);
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        setLoading(true);
        analyticsApi.listScreenDatasets()
            .then(setDatasets)
            .catch(() => setDatasets([]))
            .finally(() => setLoading(false));
    }, []);

    const filtered = search
        ? datasets.filter(d => d.name.includes(search) || d.originalFileName.includes(search))
        : datasets;

    const formatSize = (bytes: number) => {
        if (bytes < 1024) return `${bytes} B`;
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    };

    const formatDate = (iso: string) => {
        try { return new Date(iso).toLocaleDateString('zh-CN'); } catch { return iso; }
    };

    // 渲染：搜索框 + 数据集列表（名称、行数、大小、日期）
    // ... 完整 JSX 在实现时编写
}
```

- [ ] **Step 2: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/DatasetPicker.tsx
git commit -m "feat(screen): add DatasetPicker component for reusing uploaded datasets"
```

---

## Task 6: 前端 — PropertyPanel 集成 + useCardDataSource 扩展

**Files:**
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx` (lines 4629-4719)
- Modify: `source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts` (lines 187, 605)

- [ ] **Step 1: PropertyPanel — 添加下拉选项**

在 `PropertyPanel.tsx` ~line 4718（`<option value="metric">` 之后）添加：
```tsx
<option value="uploaded">文件上传</option>
```

- [ ] **Step 2: PropertyPanel — 添加 setType case**

在 `setType()` 函数（~line 4689 `'metric'` case 之后）添加：
```typescript
case 'uploaded': {
    setDataSource({
        type: 'uploaded',
        uploadedConfig: { datasetId: '', datasetName: '' },
    });
    break;
}
```

- [ ] **Step 3: PropertyPanel — 渲染 UploadedDataEditor / DatasetPicker**

在数据源配置区域（~line 4722 `'static'` 判断之后）添加：
```tsx
{dsType === 'uploaded' && (
    <div className="property-section">
        {/* 模式切换：上传新文件 / 选择已有 */}
        {/* uploadedConfig.datasetId 为空时显示上传界面，否则显示已绑定信息 */}
        <UploadedDataEditor
            existingDatasetId={ds?.uploadedConfig?.datasetId}
            onBind={(cfg) => {
                setDataSource({
                    type: 'uploaded',
                    uploadedConfig: cfg,
                });
            }}
        />
        {/* 或选择已有数据集 */}
        <DatasetPicker
            selectedId={ds?.uploadedConfig?.datasetId}
            onSelect={(cfg) => {
                setDataSource({
                    type: 'uploaded',
                    uploadedConfig: cfg,
                });
            }}
        />
    </div>
)}
```

- [ ] **Step 4: useCardDataSource — 添加 uploaded 分支**

在 `resolveSourceType()` 函数（~line 195）添加 `'uploaded'` 识别。

在主 fetch 逻辑（~line 605 `'dataset'` case 之后）添加：
```typescript
case 'uploaded': {
    const datasetId = dataSource?.uploadedConfig?.datasetId;
    if (!datasetId) {
        setCardData(null);
        setCardLoading(false);
        return;
    }
    setCardLoading(true);
    try {
        const result = await analyticsApi.getScreenDatasetData(datasetId);
        const cols = (result.cols || []).map((c: { name: string; displayName?: string; type?: string }) => ({
            name: c.name,
            display_name: c.displayName || c.name,
            base_type: c.type === 'number' ? 'type/Integer' : c.type === 'date' ? 'type/DateTime' : 'type/Text',
        }));
        setCardData({ rows: result.rows || [], cols });
        setCardError(null);
    } catch (e) {
        setCardError(e instanceof Error ? e.message : '加载数据集失败');
        setCardData(null);
    } finally {
        setCardLoading(false);
    }
    break;
}
```

- [ ] **Step 5: 验证编译**

Run: `cd source/dts-analytics-webapp/modern && npx tsc --noEmit`
Expected: 无类型错误

- [ ] **Step 6: Commit**

```bash
git add source/dts-analytics-webapp/modern/src/pages/screens/components/PropertyPanel.tsx \
      source/dts-analytics-webapp/modern/src/pages/screens/hooks/useCardDataSource.ts
git commit -m "feat(screen): integrate uploaded data source into PropertyPanel and runtime"
```

---

## Task 7: 端到端验证

**Files:** 无新文件

- [ ] **Step 1: 启动后端**

确认 dts-analytics 服务启动，Liquibase 迁移成功。

- [ ] **Step 2: 启动前端**

```bash
cd source/dts-analytics-webapp/modern && npm run dev
```

- [ ] **Step 3: 验证完整流程**

1. 打开大屏编辑器，添加一个 Table 组件
2. 在属性面板 → 数据源 → 选择"文件上传"
3. 上传一个 Excel 文件，确认解析预览正确
4. 编辑列名、修改类型、排除不需要的列
5. 点击"上传并绑定"
6. 确认 Table 组件正确显示数据
7. 验证数据库中 `encrypted_data` 字段不可直读
8. 新增另一个组件，选择"已有数据集"，确认可复用

- [ ] **Step 4: 验证 Chrome 95 兼容性**

确认无 ES2022+ 语法（如 structuredClone、Array.at 等），SheetJS 和 PapaParse 均兼容 Chrome 95。

- [ ] **Step 5: Commit**

如有修复，提交修复 commit。

---

## 注意事项

### Chrome 95 兼容
- SheetJS 0.18.5 支持 Chrome 40+
- PapaParse 5.4.1 支持 IE10+
- 避免使用 `Array.at()`、`structuredClone()`、`Object.hasOwn()` 等新 API

### 离线打包
- SheetJS 和 PapaParse 均为纯 JS 库，无 CDN 依赖
- npm install 后已打包在 bundle 中

### 文件大小限制
- 前端解析受浏览器内存限制，建议在 UI 中限制 50MB
- 后端可在 `ScreenDatasetResource` 中检查 `rowCount` 和 `fileSize`

### 加密密钥管理
- 生产环境通过环境变量 `SCREEN_DATASET_KEY` 注入
- Docker Compose `.env` 中配置（注意不加引号）
