import { useEffect, useMemo, useState } from "react";
import { Button } from "@/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/ui/card";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Textarea } from "@/ui/textarea";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/ui/tabs";
import { toast } from "sonner";
import {
  getAccessPolicy,
  listDatasets,
  previewMasking,
  previewSecurityViews,
  upsertAccessPolicy,
  getEffectivePolicy,
  applyPolicy,
} from "@/api/platformApi";

const MASKING = ["NONE", "PARTIAL", "HASH", "TOKENIZE", "CUSTOM"] as const;
type MaskingType = (typeof MASKING)[number];

type DatasetLite = { id: string; name: string };

export default function AccessPolicyPage() {
  const [datasets, setDatasets] = useState<DatasetLite[]>([]);
  const [datasetId, setDatasetId] = useState<string>("");
  const [allowRoles, setAllowRoles] = useState<string>("ROLE_PUBLIC,ROLE_INTERNAL");
  const [rowFilter, setRowFilter] = useState<string>("");
  const [defaultMasking, setDefaultMasking] = useState<MaskingType>("NONE");
  const [loading, setLoading] = useState(false);

  // Preview inputs
  const [previewValue, setPreviewValue] = useState("13812345678");
  const [previewStrategy, setPreviewStrategy] = useState<MaskingType>("PARTIAL");
  const [previewMasked, setPreviewMasked] = useState<string>("");
  const [viewsPreview, setViewsPreview] = useState<Record<string, string> | null>(null);
  const [effective, setEffective] = useState<Record<string, any> | null>(null);
  const [applying, setApplying] = useState(false);

  const canSave = useMemo(() => Boolean(datasetId), [datasetId]);

  const fetchDatasets = async () => {
    try {
      const resp = (await listDatasets({ page: 0, size: 100 })) as any;
      const content = (resp?.content ?? []).map((it: any) => ({ id: String(it.id), name: String(it.name) }));
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
    try {
      const p = (await getAccessPolicy(id)) as any;
      if (p) {
        setAllowRoles(String(p.allowRoles ?? ""));
        setRowFilter(String(p.rowFilter ?? ""));
        setDefaultMasking((p.defaultMasking ?? "NONE") as MaskingType);
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

  const onSave = async () => {
    if (!datasetId) return;
    try {
      await upsertAccessPolicy(datasetId, { allowRoles, rowFilter, defaultMasking });
      toast.success("已保存策略");
    } catch (e) {
      console.error(e);
      toast.error("保存失败");
    }
  };

  const onPreviewMask = async () => {
    try {
      // Map UI strategy to backend preview function names
      const resolveFn = (strategy: MaskingType, val: string): string | null => {
        const v = String(val || "");
        const lower = strategy.toUpperCase() as MaskingType;
        if (lower === "NONE") return null; // no-op
        if (lower === "HASH") return "hash";
        if (lower === "PARTIAL") {
          if (v.includes("@")) return "mask_email";
          // treat number-like strings as phone
          const digits = v.replace(/\D/g, "");
          if (digits.length >= 10) return "mask_phone";
          return "mask_phone";
        }
        if (lower === "TOKENIZE" || lower === "CUSTOM") {
          // Use hash as a simple preview stand-in
          return "hash";
        }
        return null;
      };

      const fn = resolveFn(previewStrategy, previewValue);
      if (!fn) {
        setPreviewMasked(previewValue);
        return;
      }
      const r = (await previewMasking({ value: previewValue, function: fn })) as any;
      setPreviewMasked(String(r?.output ?? r?.masked ?? previewValue));
    } catch (e) {
      console.error(e);
      toast.error("预览失败");
    }
  };

  const onPreviewViews = async () => {
    if (!datasetId) return;
    try {
      const vv = (await previewSecurityViews(datasetId)) as any;
      setViewsPreview(vv || {});
      toast.success("已生成视图预览");
    } catch (e) {
      console.error(e);
      toast.error("视图预览失败");
    }
  };

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader>
          <div className="space-y-1">
            <CardTitle className="text-lg">分级保护与访问策略</CardTitle>
            <CardDescription>围绕数据密级配置访问角色、行域过滤及脱敏规则，并验证策略效果。</CardDescription>
          </div>
          <CardAction>
            <div className="flex flex-wrap items-center justify-end gap-2">
              <div className="flex items-center gap-2 text-sm">
                <span className="text-muted-foreground">数据集</span>
                <Select value={datasetId} onValueChange={setDatasetId}>
                  <SelectTrigger className="min-w-[200px] md:w-[260px]">
                    <SelectValue placeholder="选择数据集" />
                  </SelectTrigger>
                  <SelectContent>
                    {datasets.map((d) => (
                      <SelectItem key={d.id} value={d.id}>
                        {d.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Button variant="outline" onClick={() => void fetchDatasets()}>
                刷新
              </Button>
              <Button onClick={onSave} disabled={!canSave || loading}>
                保存
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
          </CardAction>
        </CardHeader>
        <CardContent className="space-y-8">
          <div className="grid gap-6 xl:grid-cols-[minmax(0,1.15fr)_minmax(0,1fr)]">
            <div className="space-y-6 rounded-xl border bg-muted/10 p-6">
              <div className="space-y-1">
                <div className="text-sm font-semibold text-muted-foreground">策略定义</div>
                <p className="text-sm text-muted-foreground">
                  管理分级授权需要的角色、行域表达式与兜底脱敏策略。
                </p>
              </div>
              <div className="grid gap-4">
                <div className="grid gap-2">
                  <Label>允许访问的角色（逗号分隔）</Label>
                  <Input value={allowRoles} onChange={(e) => setAllowRoles(e.target.value)} placeholder="ROLE_PUBLIC,ROLE_INTERNAL" />
                </div>
                <div className="grid gap-2">
                  <Label>行级过滤表达式（RLS）</Label>
                  <Textarea value={rowFilter} onChange={(e) => setRowFilter(e.target.value)} rows={5} placeholder="org_id = :orgId AND data_level &lt;= :level" />
                </div>
                <div className="grid gap-2">
                  <Label>默认兜底策略</Label>
                  <Select value={defaultMasking} onValueChange={(v) => setDefaultMasking(v as MaskingType)}>
                    <SelectTrigger className="w-[220px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {MASKING.map((m) => (
                        <SelectItem key={m} value={m}>
                          {m}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
            </div>

            <div className="space-y-6 rounded-xl border bg-background p-6 shadow-sm">
              <div className="space-y-1">
                <div className="text-sm font-semibold text-muted-foreground">策略校验</div>
                <p className="text-sm text-muted-foreground">实时查看接口脱敏效果与四档安全视图的生成结果。</p>
              </div>
              <Tabs defaultValue="api" className="space-y-4">
                <TabsList className="w-full justify-start gap-2 bg-muted/60 p-1">
                  <TabsTrigger value="api" className="flex-1">接口层校验</TabsTrigger>
                  <TabsTrigger value="view" className="flex-1">视图层校验</TabsTrigger>
                </TabsList>
                <TabsContent value="api" className="space-y-4">
                  <div className="grid gap-2">
                    <Label>策略</Label>
                    <Select value={previewStrategy} onValueChange={(v) => setPreviewStrategy(v as MaskingType)}>
                      <SelectTrigger className="w-[220px]">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {MASKING.map((m) => (
                          <SelectItem key={m} value={m}>
                            {m}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="grid gap-2">
                    <Label>样例值</Label>
                    <Input value={previewValue} onChange={(e) => setPreviewValue(e.target.value)} placeholder="13812345678" />
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button variant="outline" onClick={() => setPreviewMasked("")}>清空</Button>
                    <Button onClick={() => void onPreviewMask()}>预览脱敏</Button>
                  </div>
                  {previewMasked && (
                    <div className="rounded-lg border bg-muted/20 p-3 text-xs font-mono">
                      结果：{previewMasked}
                    </div>
                  )}
                </TabsContent>

                <TabsContent value="view" className="space-y-4">
                  <div className="flex flex-wrap gap-2">
                    <Button onClick={() => void onPreviewViews()} disabled={!datasetId}>
                      生成四档视图 SQL
                    </Button>
                  </div>
                  {viewsPreview && (
                    <div className="grid gap-3 md:grid-cols-2">
                      {Object.entries(viewsPreview).map(([k, v]) => (
                        <div key={k} className="space-y-2 rounded-lg border bg-muted/10 p-3">
                          <div className="text-xs font-semibold uppercase text-muted-foreground">{k}</div>
                          <pre className="whitespace-pre-wrap text-xs font-mono leading-relaxed">{String(v)}</pre>
                        </div>
                      ))}
                    </div>
                  )}
                </TabsContent>
              </Tabs>
            </div>
          </div>
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
  );
}
