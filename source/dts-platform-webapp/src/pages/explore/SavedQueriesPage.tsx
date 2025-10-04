import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Textarea } from "@/ui/textarea";
import { toast } from "sonner";
import {
  createSavedQuery,
  deleteSavedQuery,
  listSavedQueries,
  runSavedQuery,
  listResultSets,
  previewResultSet,
  deleteResultSet,
  cleanupExpiredResultSets,
  listDatasets,
  updateSavedQuery,
  type SavedQueryCreatePayload,
  type SavedQueryUpdatePayload,
} from "@/api/platformApi";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from "@/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";

const NONE_DATASET_VALUE = "__NONE__";

type SavedQuery = { id: string; name?: string; title?: string; sqlText?: string; datasetId?: string };

type ResultSet = {
  id: string;
  storageUri?: string;
  columns: string;
  rowCount?: number;
  expiresAt?: string;
  datasetName?: string;
  classification?: string;
  classificationKey?: Classification;
  createdAt?: string;
};

const CLASSIFICATION_META = {
  TOP_SECRET: { label: "机密", tone: "bg-rose-500/10 text-rose-500" },
  SECRET: { label: "秘密", tone: "bg-amber-500/10 text-amber-500" },
  INTERNAL: { label: "内部", tone: "bg-sky-500/10 text-sky-500" },
  PUBLIC: { label: "公开", tone: "bg-emerald-500/10 text-emerald-600" },
} as const;

const CLASSIFICATION_LABEL_MAP: Record<string, Classification> = {
  机密: "TOP_SECRET",
  SECRET: "SECRET",
  SECRET_LEVEL: "SECRET",
  秘密: "SECRET",
  INTERNAL: "INTERNAL",
  内部: "INTERNAL",
  PUBLIC: "PUBLIC",
  公开: "PUBLIC",
  TOP_SECRET: "TOP_SECRET",
};

type Classification = keyof typeof CLASSIFICATION_META;

type Dataset = {
  id: string;
  name: string;
  classification: Classification;
  trinoCatalog?: string;
  hiveDatabase?: string;
  hiveTable?: string;
};

function toUiDataset(input: any): Dataset {
  const classification = String(input?.classification || "INTERNAL").toUpperCase();
  return {
    id: String(input?.id ?? ""),
    name: String(input?.name || input?.hiveTable || input?.id || "未知数据集"),
    classification: (classification in CLASSIFICATION_META ? classification : "INTERNAL") as Classification,
    trinoCatalog: input?.trinoCatalog,
    hiveDatabase: input?.hiveDatabase,
    hiveTable: input?.hiveTable,
  };
}

function classificationBadge(level?: Classification) {
  if (!level) return null;
  const meta = CLASSIFICATION_META[level];
  if (!meta) return null;
  return (
    <span className={`inline-flex items-center rounded px-1.5 py-0.5 text-xs font-semibold ${meta.tone}`}>
      {meta.label}
    </span>
  );
}

function resolveClassification(value?: string): Classification | undefined {
  if (!value) return undefined;
  const trimmed = String(value).trim();
  if (!trimmed) return undefined;
  const upper = trimmed.toUpperCase();
  if (upper in CLASSIFICATION_META) {
    return upper as Classification;
  }
  if (trimmed in CLASSIFICATION_LABEL_MAP) {
    return CLASSIFICATION_LABEL_MAP[trimmed];
  }
  if (upper in CLASSIFICATION_LABEL_MAP) {
    return CLASSIFICATION_LABEL_MAP[upper];
  }
  return undefined;
}

function formatDateTime(value?: string) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

export default function SavedQueriesPage() {
  const [items, setItems] = useState<SavedQuery[]>([]);
  const [resultSets, setResultSets] = useState<ResultSet[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [isLoading, setLoading] = useState<boolean>(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [sql, setSql] = useState("SELECT 1 AS col_1");
  const [newDatasetId, setNewDatasetId] = useState<string | undefined>();
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewHeaders, setPreviewHeaders] = useState<string[]>([]);
  const [previewRows, setPreviewRows] = useState<any[]>([]);
  const [previewMasking, setPreviewMasking] = useState<any>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editId, setEditId] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [editSql, setEditSql] = useState("SELECT 1 AS col_1");
  const [editDatasetId, setEditDatasetId] = useState<string | undefined>();

  const datasetMap = useMemo(() => {
    return datasets.reduce<Record<string, Dataset>>((acc, item: Dataset) => {
      acc[item.id] = item;
      return acc;
    }, {});
  }, [datasets]);

  const enhancedSavedQueries = useMemo(() => {
    return items.map((item) => {
      const datasetInfo = item.datasetId ? datasetMap[item.datasetId] : undefined;
      const classification = datasetInfo?.classification;
      return {
        ...item,
        datasetName: datasetInfo?.name ?? (item.datasetId ? `ID: ${item.datasetId}` : undefined),
        datasetClassification: classification,
      };
    });
  }, [items, datasetMap]);

  const maskMode = previewMasking?.mode ?? (Array.isArray(previewMasking?.maskedColumns) ? "heuristic" : undefined);
  const maskColumns = Array.isArray(previewMasking?.columns)
    ? previewMasking.columns
    : Array.isArray(previewMasking?.maskedColumns)
      ? previewMasking.maskedColumns
      : [];
  const maskRules = Array.isArray(previewMasking?.rules) ? previewMasking.rules : [];
  const maskDefault = previewMasking?.default;

  const load = async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const [datasetResp, savedResp, resultResp] = await Promise.all([
        listDatasets({ page: 0, size: 200 }),
        listSavedQueries(),
        listResultSets(),
      ]);

      const datasetListRaw = Array.isArray((datasetResp as any)?.content)
        ? (datasetResp as any).content
        : Array.isArray(datasetResp)
          ? datasetResp
          : [];
      const datasetList: Dataset[] = (datasetListRaw as any[]).map(toUiDataset).filter((item: Dataset) => Boolean(item.id));
      const datasetMapLocal = datasetList.reduce<Record<string, Dataset>>((acc, entry) => {
        acc[entry.id] = entry;
        return acc;
      }, {});
      setDatasets(datasetList);
      if (datasetList.length === 0) {
        setNewDatasetId(undefined);
      } else if (newDatasetId && newDatasetId !== NONE_DATASET_VALUE && !datasetMapLocal[newDatasetId]) {
        setNewDatasetId(undefined);
      }

      const savedList = Array.isArray(savedResp) ? savedResp : Array.isArray((savedResp as any)?.data) ? (savedResp as any).data : [];
      setItems(savedList);

      const resultSetListRaw = Array.isArray(resultResp) ? resultResp : Array.isArray((resultResp as any)?.data) ? (resultResp as any).data : resultResp;
      const resultSetList: ResultSet[] = (Array.isArray(resultSetListRaw) ? resultSetListRaw : []).map((item: any) => {
        const datasetInfo = item?.datasetId ? datasetMapLocal[item.datasetId] : undefined;
        const classificationKey = resolveClassification(item?.classification ?? datasetInfo?.classification);
        const columns = Array.isArray(item?.columns)
          ? item.columns.join(", ")
          : typeof item?.columns === "string"
            ? item.columns
            : "";
        return {
          id: String(item?.id ?? ""),
          columns,
          rowCount: item?.rowCount,
          expiresAt: item?.expiresAt,
          storageUri: item?.storageUri,
          datasetName: datasetInfo?.name ?? item?.datasetName,
          classification: classificationKey ? CLASSIFICATION_META[classificationKey].label : item?.classification,
          classificationKey,
          createdAt: item?.createdAt,
        };
      });
      setResultSets(resultSetList);
    } catch (e: any) {
      console.error(e);
      const message = typeof e?.message === "string" ? e.message : "加载保存的查询失败";
      setLoadError(message);
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const handleCreate = async () => {
    if (!title.trim() || !sql.trim()) {
      toast.error("请输入标题和 SQL");
      return;
    }
    try {
      const payload: SavedQueryCreatePayload = {
        name: title.trim(),
        sqlText: sql,
      };
      if (newDatasetId && newDatasetId !== NONE_DATASET_VALUE) {
        payload.datasetId = newDatasetId;
      } else if (newDatasetId === NONE_DATASET_VALUE) {
        payload.datasetId = null;
      }
      await createSavedQuery(payload);
      toast.success("已保存查询");
      setTitle("");
      setSql("SELECT 1 AS col_1");
      setNewDatasetId(undefined);
      await load();
    } catch (e) {
      console.error(e);
      const message = typeof (e as any)?.message === "string" ? (e as any).message : "保存失败";
      toast.error(message);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await deleteSavedQuery(id);
      toast.success("已删除");
      await load();
    } catch (e) {
      console.error(e);
      toast.error("删除失败");
    }
  };

  const openEditDialog = (item: SavedQuery) => {
    setEditId(item.id);
    setEditName(item.name || item.title || "");
    setEditSql(item.sqlText || "SELECT 1 AS col_1");
    setEditDatasetId(item.datasetId || undefined);
    setEditOpen(true);
  };

  const handleUpdate = async () => {
    if (!editId) {
      toast.error("未找到待更新的查询");
      return;
    }
    if (!editName.trim() || !editSql.trim()) {
      toast.error("请输入名称和 SQL");
      return;
    }
    setEditSubmitting(true);
    try {
      const payload: SavedQueryUpdatePayload = {
        name: editName.trim(),
        sqlText: editSql,
      };
      if (editDatasetId && editDatasetId !== NONE_DATASET_VALUE) {
        payload.datasetId = editDatasetId;
      } else if (editDatasetId === NONE_DATASET_VALUE || editDatasetId === undefined) {
        payload.datasetId = null;
      }
      await updateSavedQuery(editId, payload);
      toast.success("已更新保存的查询");
      setEditOpen(false);
      await load();
    } catch (e) {
      console.error(e);
      const message = typeof (e as any)?.message === "string" ? (e as any).message : "更新失败";
      toast.error(message);
    } finally {
      setEditSubmitting(false);
    }
  };

  const handleRun = async (id: string) => {
    try {
      await runSavedQuery(id);
      toast.success("已执行保存的查询（查看工作台结果）");
    } catch (e) {
      console.error(e);
      toast.error("执行失败");
    }
  };

  const handlePreview = async (id: string) => {
    try {
      const resp: any = await previewResultSet(id, 10);
      const headers: string[] = Array.isArray(resp?.headers) ? resp.headers : [];
      const rows: any[] = Array.isArray(resp?.rows) ? resp.rows : [];
      setPreviewHeaders(headers);
      setPreviewRows(rows);
      setPreviewMasking(resp?.masking ?? null);
      setPreviewOpen(true);
    } catch (e) {
      console.error(e);
      toast.error("预览失败");
    }
  };

  const showDatasetHint = !isLoading && !datasets.length && !loadError;

  return (
    <>
    <div className="space-y-4">
      {showDatasetHint ? (
        <div className="flex items-center justify-between rounded-md border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
          <span>当前尚未同步任何数据集，请先在「基础数据维护 &gt; 数据源」中完成配置。</span>
          <Button variant="ghost" size="sm" onClick={load}>
            重试
          </Button>
        </div>
      ) : null}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">新建保存的查询</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="space-y-1">
            <Label>标题</Label>
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="标题" />
          </div>
          <div className="space-y-1">
            <Label>SQL</Label>
            <Textarea rows={4} value={sql} onChange={(e) => setSql(e.target.value)} />
          </div>
          {datasets.length ? (
            <div className="space-y-1">
              <Label>绑定数据集（可选）</Label>
              <Select
                value={newDatasetId ?? NONE_DATASET_VALUE}
                onValueChange={(value) => setNewDatasetId(value === NONE_DATASET_VALUE ? undefined : value)}
              >
                <SelectTrigger>
                  <SelectValue placeholder="选择数据集" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE_DATASET_VALUE}>不关联数据集</SelectItem>
                  {datasets.map((dataset) => (
                    <SelectItem key={dataset.id} value={dataset.id}>
                      <div className="flex items-center justify-between gap-2">
                        <span className="truncate">{dataset.name}</span>
                        {classificationBadge(dataset.classification)}
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}
          <div>
            <Button onClick={handleCreate} disabled={isLoading}>保存</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">已保存的查询</CardTitle>
            <div className="flex items-center gap-2">
              {loadError ? (
                <span className="text-xs text-red-500">{loadError}</span>
              ) : isLoading ? (
                <span className="text-xs text-muted-foreground">加载中...</span>
              ) : null}
              <Button variant="outline" size="sm" onClick={load} disabled={isLoading}>
                刷新
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {enhancedSavedQueries.length ? (
            <div className="overflow-hidden rounded-md border">
              <table className="w-full border-collapse text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="border-b px-3 py-2 text-left font-medium">名称</th>
                    <th className="border-b px-3 py-2 text-left font-medium">数据集</th>
                    <th className="border-b px-3 py-2 text-left font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {enhancedSavedQueries.map((it) => (
                    <tr key={it.id} className="border-b last:border-b-0">
                      <td className="px-3 py-2">{it.title || it.name || it.id}</td>
                      <td className="px-3 py-2">
                        {it.datasetName ? (
                          <div className="flex items-center gap-2">
                            {classificationBadge(it.datasetClassification)}
                            <span>{it.datasetName}</span>
                          </div>
                        ) : (
                          <span className="text-muted-foreground">未关联数据集</span>
                        )}
                      </td>
                      <td className="px-3 py-2 space-x-2">
                        <Button size="sm" variant="outline" onClick={() => openEditDialog(it)} disabled={isLoading}>
                          编辑
                        </Button>
                        <Button size="sm" variant="outline" onClick={() => handleRun(it.id)} disabled={isLoading}>
                          执行
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => handleDelete(it.id)} disabled={isLoading}>
                          删除
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-sm text-muted-foreground">
              暂无保存的查询，可在「数据查询和预览」页面保存常用 SQL。
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">结果集管理</CardTitle>
            <div className="flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={async () => {
                  try {
                    await load();
                    toast.success("已刷新");
                  } catch {}
                }}
                disabled={isLoading}
              >
                刷新
              </Button>
              <Button
                size="sm"
                onClick={async () => {
                  try {
                    const r: any = await cleanupExpiredResultSets();
                    toast.success(`已清理 ${r?.deleted ?? 0} 条过期结果集`);
                    await load();
                  } catch (e) {
                    console.error(e);
                    toast.error("清理失败");
                  }
                }}
                disabled={isLoading}
              >
                清理过期
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {resultSets.length ? (
            <div className="overflow-hidden rounded-md border">
              <table className="w-full border-collapse text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="border-b px-3 py-2 text-left font-medium">查询编号</th>
                    <th className="border-b px-3 py-2 text-left font-medium">所属数据集</th>
                    <th className="border-b px-3 py-2 text-left font-medium">列</th>
                    <th className="border-b px-3 py-2 text-left font-medium">行数</th>
                    <th className="border-b px-3 py-2 text-left font-medium">密级</th>
                    <th className="border-b px-3 py-2 text-left font-medium">过期时间</th>
                    <th className="border-b px-3 py-2 text-left font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {resultSets.map((rs) => (
                    <tr key={rs.id} className="border-b last:border-b-0">
                      <td className="px-3 py-2">{rs.id}</td>
                      <td className="px-3 py-2">{rs.datasetName ?? "-"}</td>
                      <td className="px-3 py-2">{rs.columns || "-"}</td>
                      <td className="px-3 py-2">{rs.rowCount ?? "-"}</td>
                      <td className="px-3 py-2">
                        {rs.classificationKey || rs.classification ? (
                          <div className="flex items-center gap-2">
                            {classificationBadge(rs.classificationKey)}
                            <span>{rs.classification ?? CLASSIFICATION_META[rs.classificationKey!]?.label ?? "-"}</span>
                          </div>
                        ) : (
                          "-"
                        )}
                      </td>
                      <td className="px-3 py-2">{formatDateTime(rs.expiresAt)}</td>
                      <td className="px-3 py-2 space-x-2 text-nowrap">
                        <Button size="sm" variant="outline" onClick={() => handlePreview(rs.id)} disabled={isLoading}>
                          预览
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          onClick={async () => {
                            try {
                              await deleteResultSet(rs.id);
                              toast.success("已删除结果集");
                              await load();
                            } catch (e) {
                              console.error(e);
                              toast.error("删除失败");
                            }
                          }}
                          disabled={isLoading}
                        >
                          删除
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="text-sm text-muted-foreground">暂无结果集</div>
          )}
        </CardContent>
      </Card>
    </div>
    <Dialog open={previewOpen} onOpenChange={setPreviewOpen}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>结果集预览</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          {previewMasking ? (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-700">
              {maskMode === "policy" ? (
                <div>
                  <p className="font-medium">按策略脱敏</p>
                  {maskRules.length ? (
                    <p>规则：{maskRules.map((r: any) => `${r.column}:${r.fn}`).join(", ")}</p>
                  ) : null}
                  {maskDefault ? <p>默认：{maskDefault}</p> : null}
                </div>
              ) : (
                <div>
                  <p className="font-medium">启发式脱敏</p>
                  {maskColumns.length ? (
                    <p>列：{maskColumns.join(", ")}</p>
                  ) : (
                    <p>未识别到敏感列</p>
                  )}
                </div>
              )}
            </div>
          ) : null}
          <div className="overflow-auto rounded-md border">
            <table className="w-full border-collapse text-xs">
              <thead className="bg-muted/50">
                <tr>
                  {previewHeaders.map((h) => (
                    <th key={h} className="border-b px-2 py-1 text-left font-medium">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {previewRows.map((row, i) => (
                  <tr key={`r-${i}`} className="border-b last:border-b-0">
                    {previewHeaders.map((h) => (
                      <td key={h} className="px-2 py-1">{String(row[h] ?? "")}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setPreviewOpen(false)}>关闭</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    <Dialog
      open={editOpen}
      onOpenChange={(open) => {
        setEditOpen(open);
        if (!open) {
          setEditSubmitting(false);
        }
      }}
    >
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>编辑保存的查询</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
          <div className="space-y-1">
            <Label>名称</Label>
            <Input value={editName} onChange={(e) => setEditName(e.target.value)} placeholder="请输入名称" />
          </div>
          <div className="space-y-1">
            <Label>SQL</Label>
            <Textarea rows={4} value={editSql} onChange={(e) => setEditSql(e.target.value)} />
          </div>
          <div className="space-y-1">
            <Label>关联数据集</Label>
            <Select
              value={editDatasetId ?? NONE_DATASET_VALUE}
              onValueChange={(value) => setEditDatasetId(value === NONE_DATASET_VALUE ? undefined : value)}
            >
              <SelectTrigger>
                <SelectValue placeholder="选择数据集" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE_DATASET_VALUE}>不关联数据集</SelectItem>
                {datasets.map((dataset) => (
                  <SelectItem key={dataset.id} value={dataset.id}>
                    {dataset.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="ghost" onClick={() => setEditOpen(false)} disabled={editSubmitting}>
            取消
          </Button>
          <Button onClick={handleUpdate} disabled={editSubmitting}>
            保存修改
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
    </>
  );
}
