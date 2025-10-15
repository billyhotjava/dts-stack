import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Button } from "@/ui/button";
import { Badge } from "@/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Textarea } from "@/ui/textarea";
import { Popover, PopoverContent, PopoverTrigger } from "@/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/ui/command";
import { cn } from "@/utils";
import directoryService, { type DirectoryRole } from "@/api/services/directoryService";
import {
  getAccessPolicy,
  listDatasets,
  previewSecurityViews,
  upsertAccessPolicy,
  getEffectivePolicy,
  applyPolicy,
} from "@/api/platformApi";
import { Check, ChevronsUpDown } from "lucide-react";

const normalizeRoleName = (name: string): string => {
  const trimmed = (name || "").trim();
  if (!trimmed) return "";
  const upper = trimmed.toUpperCase();
  return upper.startsWith("ROLE_") ? upper : `ROLE_${upper}`;
};

const parseAllowRoles = (raw: unknown): string[] => {
  const result: string[] = [];
  const push = (value: string) => {
    const normalized = normalizeRoleName(value);
    if (!normalized) return;
    if (!result.includes(normalized)) result.push(normalized);
  };
  if (Array.isArray(raw)) {
    raw.forEach((item) => push(String(item ?? "")));
  } else if (typeof raw === "string") {
    raw
      .split(/[,，\s]+/)
      .map((token) => token.trim())
      .filter(Boolean)
      .forEach(push);
  }
  return result;
};

type DatasetLite = {
  id: string;
  name: string;
  classification?: string;
  dataLevel?: string;
  owner?: string;
  ownerDept?: string;
};

const CLASSIFICATION_LABELS: Record<string, string> = {
  DATA_PUBLIC: "公开",
  DATA_INTERNAL: "内部",
  DATA_SECRET: "秘密",
  DATA_TOP_SECRET: "机密",
  PUBLIC: "公开",
  INTERNAL: "内部",
  SECRET: "秘密",
  TOP_SECRET: "机密",
};

export default function AccessPolicyPage() {
  const [datasets, setDatasets] = useState<DatasetLite[]>([]);
  const [datasetId, setDatasetId] = useState<string>("");
  const [allowRoles, setAllowRoles] = useState<string[]>([]);
  const [rowFilter, setRowFilter] = useState<string>("");
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [roleOptions, setRoleOptions] = useState<DirectoryRole[]>([]);
  const [roleLoading, setRoleLoading] = useState(false);
  const [rolePickerOpen, setRolePickerOpen] = useState(false);

  // Preview inputs
  const [viewsPreview, setViewsPreview] = useState<Record<string, string> | null>(null);
  const [effective, setEffective] = useState<Record<string, any> | null>(null);
  const [applying, setApplying] = useState(false);
  const [datasetDialogOpen, setDatasetDialogOpen] = useState(false);
  const [datasetKeyword, setDatasetKeyword] = useState("");

  const canSave = useMemo(() => Boolean(datasetId), [datasetId]);
  const selectedDataset = useMemo(() => datasets.find((d) => d.id === datasetId) ?? null, [datasets, datasetId]);
  const filteredDatasets = useMemo(() => {
    if (!datasetKeyword.trim()) return datasets;
    const keyword = datasetKeyword.trim().toLowerCase();
    return datasets.filter((item) => {
      return (
        item.name.toLowerCase().includes(keyword) ||
        (item.dataLevel ?? "").toLowerCase().includes(keyword) ||
        (item.owner ?? "").toLowerCase().includes(keyword) ||
        (item.ownerDept ?? "").toLowerCase().includes(keyword)
      );
    });
  }, [datasets, datasetKeyword]);

  const fetchDatasets = async () => {
    try {
      const resp = (await listDatasets({ page: 0, size: 200 })) as any;
      const content = (resp?.content ?? []).map((it: any) => ({
        id: String(it.id),
        name: String(it.name),
        classification: it.classification,
        dataLevel: it.dataLevel,
        owner: it.owner,
        ownerDept: it.ownerDeptName || it.ownerDept,
      }));
      setDatasets(content);
      if (!datasetId && content.length) setDatasetId(content[0].id);
    } catch (e) {
      console.error(e);
      toast.error("加载数据集失败");
    }
  };

  const fetchPolicy = async (id: string) => {
    if (!id) return;
    setLoading(true);
    setViewsPreview(null);
    setEffective(null);
    try {
      const p = (await getAccessPolicy(id)) as any;
      if (p && typeof p === "object") {
        const parsed = parseAllowRoles(p.allowRoles);
        setAllowRoles(parsed);
        setRowFilter(String(p.rowFilter ?? ""));
      } else {
        setAllowRoles([]);
        setRowFilter("");
      }
    } catch (e) {
      console.error(e);
      toast.error("加载策略失败");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchDatasets();
  }, []);

  useEffect(() => {
    if (datasetId) void fetchPolicy(datasetId);
  }, [datasetId]);

  useEffect(() => {
    let mounted = true;
    setRoleLoading(true);
    directoryService
      .listRoles()
      .then((list) => {
        if (!mounted) return;
        setRoleOptions(Array.isArray(list) ? list : []);
      })
      .catch((error) => {
        console.error(error);
        toast.error("加载角色列表失败");
      })
      .finally(() => {
        if (mounted) setRoleLoading(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  const onSave = async () => {
    if (!datasetId) return;
    const normalizedRoles = allowRoles.map(normalizeRoleName).filter(Boolean);
    if (normalizedRoles.length === 0) {
      toast.error("请至少配置一个允许访问的角色");
      return;
    }
    const payload = {
      allowRoles: normalizedRoles.join(","),
      rowFilter: rowFilter.trim(),
      defaultMasking: "NONE",
    };
    try {
      setSaving(true);
      await upsertAccessPolicy(datasetId, payload);
      setAllowRoles(normalizedRoles);
      toast.success("已保存策略");
    } catch (e) {
      console.error(e);
      toast.error("保存失败");
    } finally {
      setSaving(false);
    }
  };

  const previewViews = useCallback(
    async (successMessage?: string) => {
      if (!datasetId) {
        toast.error("请选择数据集");
        return;
      }
      try {
        const vv = (await previewSecurityViews(datasetId)) as any;
        setViewsPreview(vv || {});
        if (successMessage) {
          toast.success(successMessage);
        }
      } catch (e) {
        console.error(e);
        toast.error("视图预览失败");
      }
    },
    [datasetId],
  );

  const handleValidateRowFilter = useCallback(() => {
    void previewViews("表达式校验完成");
  }, [previewViews]);

  const handleGenerateSql = useCallback(() => {
    void previewViews("已生成安全视图 SQL");
  }, [previewViews]);

  const previewSqlText = useMemo(() => {
    if (!viewsPreview || Object.keys(viewsPreview).length === 0) return "";
    return Object.entries(viewsPreview)
      .map(([key, value]) => {
        const sql = typeof value === "string" ? value : JSON.stringify(value, null, 2);
        return `-- ${key}\n${sql}`;
      })
      .join("\n\n");
  }, [viewsPreview]);

  const handlePreviewResult = useCallback(() => {
    if (!previewSqlText) {
      toast.info("请先生成安全视图 SQL");
      return;
    }
    toast.info("已生成的 SQL 如下，可在工作台执行验证结果。");
  }, [previewSqlText]);

  const handleDownloadSql = useCallback(() => {
    if (!previewSqlText) {
      toast.info("请先生成安全视图 SQL");
      return;
    }
    try {
      const blob = new Blob([previewSqlText], { type: "text/plain;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = datasetId ? `policy-${datasetId}.sql` : "policy.sql";
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      toast.success("已下载安全视图 SQL");
    } catch (error) {
      console.error(error);
      toast.error("下载失败，请稍后重试");
    }
  }, [previewSqlText, datasetId]);

  return (
    <>
    <div className="space-y-5">
      <Card>
        <CardHeader>
          <div className="space-y-1">
            <CardTitle className="text-lg">分级保护与访问策略</CardTitle>
            <CardDescription>围绕数据密级配置访问角色与行域过滤表达式，并验证策略效果。</CardDescription>
          </div>
        </CardHeader>
        <CardContent className="space-y-6">
          <section className="space-y-4 rounded-xl border bg-muted/10 p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="space-y-1">
                <div className="text-sm font-semibold text-muted-foreground">数据集信息</div>
                <p className="text-xs text-muted-foreground">选择策略作用的数据集，并确认基础信息。</p>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <Button variant="outline" onClick={() => setDatasetDialogOpen(true)}>
                  {selectedDataset ? "更换数据集" : "选择数据集"}
                </Button>
                <Button variant="outline" onClick={() => void fetchDatasets()}>
                  刷新
                </Button>
                <Button onClick={onSave} disabled={!canSave || loading || saving}>
                  {saving ? "保存中…" : "保存策略"}
                </Button>
                <Button
                  variant="outline"
                  onClick={async () => {
                    if (!datasetId) return;
                    try {
                      const eff = (await getEffectivePolicy(datasetId)) as any;
                      setEffective(eff || null);
                    } catch (e) {
                      console.error(e);
                      toast.error("获取有效策略失败");
                    }
                  }}
                  disabled={!datasetId}
                >
                  查看有效策略
                </Button>
                <Button
                  onClick={async () => {
                    if (!datasetId) return;
                    setApplying(true);
                    try {
                      await applyPolicy(datasetId);
                      toast.success("策略已生效");
                    } catch (e) {
                      console.error(e);
                      toast.error("生效失败");
                    } finally {
                      setApplying(false);
                    }
                  }}
                  disabled={!datasetId || applying}
                >
                  {applying ? "生效中…" : "策略生效"}
                </Button>
              </div>
            </div>
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
              <div className="space-y-1">
                <Label>当前数据集</Label>
                <Input readOnly value={selectedDataset?.name ?? "未选择"} placeholder="未选择" />
              </div>
              <div className="space-y-1">
                <Label>所属部门</Label>
                <Input readOnly value={selectedDataset?.ownerDept ?? "—"} placeholder="—" />
              </div>
              <div className="space-y-1">
                <Label>责任人</Label>
                <Input readOnly value={selectedDataset?.owner ?? "—"} placeholder="—" />
              </div>
              <div className="space-y-1">
                <Label>数据密级</Label>
                <Input
                  readOnly
                  value={(() => {
                    const level = selectedDataset?.dataLevel || selectedDataset?.classification;
                    if (!level) return "—";
                    const key = level.toUpperCase();
                    return CLASSIFICATION_LABELS[key] || key;
                  })()}
                  placeholder="—"
                />
              </div>
            </div>
          </section>

          <section className="space-y-4 rounded-xl border bg-muted/10 p-6">
            <div className="space-y-1">
              <div className="text-sm font-semibold text-muted-foreground">策略定义</div>
              <p className="text-xs text-muted-foreground">配置允许访问的角色，并填写行级过滤表达式。</p>
            </div>
            <div className="grid gap-4">
                <div className="grid gap-2">
                  <Label>允许访问的角色</Label>
                  <div className="space-y-2">
                    <Popover open={rolePickerOpen} onOpenChange={setRolePickerOpen}>
                      <PopoverTrigger asChild>
                        <Button
                          variant="outline"
                          role="combobox"
                          className="w-full justify-between"
                          disabled={roleLoading && !roleOptions.length}
                        >
                          <span className={cn("truncate text-left", allowRoles.length ? "" : "text-muted-foreground")}>
                            {allowRoles.length ? allowRoles.join(", ") : "选择允许访问的角色"}
                          </span>
                          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
                        </Button>
                      </PopoverTrigger>
                      <PopoverContent className="w-[320px] p-0">
                        <Command>
                          <CommandInput placeholder="搜索角色..." />
                          <CommandList>
                            {roleLoading ? (
                              <div className="px-3 py-4 text-sm text-muted-foreground">加载中…</div>
                            ) : (
                              <>
                                <CommandEmpty>未找到角色</CommandEmpty>
                                <CommandGroup>
                                  {[...roleOptions]
                                    .sort((a, b) =>
                                      normalizeRoleName(a.name).localeCompare(normalizeRoleName(b.name)),
                                    )
                                    .map((role) => {
                                    const normalized = normalizeRoleName(role.name);
                                    const selected = allowRoles.includes(normalized);
                                    return (
                                      <CommandItem
                                        key={normalized}
                                        value={`${role.name} ${role.description ?? ""}`.trim()}
                                        onSelect={() =>
                                          setAllowRoles((prev) =>
                                            prev.includes(normalized)
                                              ? prev.filter((item) => item !== normalized)
                                              : [...prev, normalized],
                                          )
                                        }
                                      >
                                        <div className="flex flex-col">
                                          <span className="font-medium">{normalized}</span>
                                          {role.description ? (
                                            <span className="text-xs text-muted-foreground">{role.description}</span>
                                          ) : null}
                                        </div>
                                        <Check
                                          className={cn("ml-auto h-4 w-4", selected ? "opacity-100" : "opacity-0")}
                                        />
                                      </CommandItem>
                                    );
                                  })}
                                </CommandGroup>
                              </>
                            )}
                          </CommandList>
                        </Command>
                      </PopoverContent>
                    </Popover>
                    {allowRoles.length ? (
                      <div className="flex flex-wrap gap-2">
                        {allowRoles.map((role) => (
                          <Badge key={role} variant="secondary">
                            {role}
                          </Badge>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-muted-foreground">请选择至少一个角色用于访问控制。</p>
                    )}
                  </div>
                </div>
                <div className="space-y-2">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <Label className="mb-0">行级过滤表达式（RLS）</Label>
                    <Button variant="outline" size="sm" onClick={handleValidateRowFilter} disabled={!datasetId}>
                      验证表达式
                    </Button>
                  </div>
                  <Textarea
                    value={rowFilter}
                    onChange={(e) => setRowFilter(e.target.value)}
                    rows={5}
                    placeholder={"org_id = :orgId AND data_level <= :level"}
                  />
                  <p className="text-xs text-muted-foreground">可使用 :orgId、:level 等参数占位符。</p>
                </div>
              </div>
            </section>

          <section className="space-y-4 rounded-xl border bg-muted/10 p-6">
            <div className="space-y-1">
              <div className="text-sm font-semibold text-muted-foreground">策略校验</div>
              <p className="text-xs text-muted-foreground">生成安全视图 SQL，验证授权与行级过滤是否符合预期。</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button onClick={handleGenerateSql} disabled={!datasetId}>
                生成安全视图 SQL
              </Button>
              <Button variant="outline" onClick={handlePreviewResult}>
                预览安全结果
              </Button>
              {/* <Button variant="outline" onClick={handleDownloadSql}>
                下载 SQL
              </Button> */}
            </div>
            <Textarea
              rows={6}
              value={previewSqlText}
              onChange={() => {}}
              readOnly
              placeholder="生成的安全视图 SQL 将展示在此，便于复制或导出。"
            />
          </section>
        </CardContent>
      </Card>

      {effective && (
        <Card>
          <CardHeader>
            <div className="space-y-1">
              <CardTitle className="text-base">策略生效快照</CardTitle>
              <CardDescription>展示最近一次查询到的有效策略编排结果。</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <pre className="whitespace-pre-wrap rounded-lg border bg-muted/10 p-3 text-xs font-mono leading-relaxed">
              {JSON.stringify(effective, null, 2)}
            </pre>
          </CardContent>
        </Card>
      )}
    </div>
      <Dialog open={datasetDialogOpen} onOpenChange={setDatasetDialogOpen}>
        <DialogContent className="max-w-[540px]">
          <DialogHeader>
            <DialogTitle>选择数据集</DialogTitle>
            <DialogDescription>可按名称、密级或责任人快速搜索。</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <Input
              value={datasetKeyword}
              onChange={(event) => setDatasetKeyword(event.target.value)}
              placeholder="输入关键字检索"
            />
            <ScrollArea className="h-[320px]">
              <div className="space-y-2 pr-2">
                {filteredDatasets.length === 0 && (
                  <div className="rounded-md border bg-muted/30 p-4 text-center text-sm text-muted-foreground">
                    未找到匹配的数据集
                  </div>
                )}
                {filteredDatasets.map((item) => {
                  const active = item.id === datasetId;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => {
                        setDatasetId(item.id);
                        setDatasetDialogOpen(false);
                      }}
                      className={cn(
                        "w-full rounded-md border p-3 text-left transition",
                        active ? "border-primary bg-primary/5" : "border-muted bg-background hover:bg-muted/40"
                      )}
                    >
                      <div className="flex flex-wrap items-center gap-2 text-sm font-medium text-foreground">
                        {item.name}
                        {item.dataLevel && (
                          <Badge variant="outline">
                            {(() => {
                              const key = String(item.dataLevel).toUpperCase();
                              return CLASSIFICATION_LABELS[key] || item.dataLevel;
                            })()}
                          </Badge>
                        )}
                      </div>
                      <div className="mt-1 text-xs text-muted-foreground">
                        <div>责任人：{item.owner || "—"}</div>
                        <div>部门：{item.ownerDept || "—"}</div>
                      </div>
                    </button>
                  );
                })}
              </div>
            </ScrollArea>
            <DialogFooter className="justify-end gap-2">
              <Button variant="outline" onClick={() => setDatasetDialogOpen(false)}>
                关闭
              </Button>
            </DialogFooter>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
