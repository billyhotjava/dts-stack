import { useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/ui/drawer";
import { Input } from "@/ui/input";
import { Label } from "@/ui/label";
import { ScrollArea } from "@/ui/scroll-area";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/ui/select";
import { Switch } from "@/ui/switch";
import { Textarea } from "@/ui/textarea";
import {
  createQualityRule,
  deleteQualityRule,
  listDatasets,
  listQualityRules,
  listQualityRuns,
  toggleQualityRule,
  triggerQualityRun,
  updateQualityRule,
} from "@/api/platformApi";


type DataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_TOP_SECRET";
type SeverityLevel = "INFO" | "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

interface DatasetOption {
  id: string;
  name: string;
  hiveDatabase?: string;
  hiveTable?: string;
  classification?: string;
}

interface RuleBindingView {
  id: string;
  datasetId?: string;
  datasetAlias?: string;
  scopeType?: string;
  filterExpression?: string | null;
}

interface RuleRow {
  id: string;
  code?: string;
  name: string;
  type?: string;
  owner?: string;
  severity?: SeverityLevel;
  dataLevel?: DataLevel;
  frequencyCron?: string;
  frequencyLabel?: string;
  enabled: boolean;
  datasetNames: string[];
  datasetIds: string[];
  bindings: RuleBindingView[];
  description?: string | null;
  sqlPreview?: string;
  createdAt?: string;
  updatedAt?: string;
  raw: any;
}

interface QualityRunView {
  id: string;
  status: string;
  severity?: string;
  startedAt?: string;
  finishedAt?: string;
  durationMs?: number;
  message?: string;
}

interface RuleForm {
  id?: string;
  code?: string;
  name: string;
  datasetId: string;
  owner: string;
  severity: SeverityLevel;
  dataLevel: DataLevel;
  frequencyPreset: "HOURLY" | "DAILY" | "CUSTOM";
  customCron: string;
  enabled: boolean;
  sql: string;
  description?: string;
}

const LEVEL_LABELS: Record<DataLevel, string> = {
  DATA_PUBLIC: "公开 (DATA_PUBLIC)",
  DATA_INTERNAL: "内部 (DATA_INTERNAL)",
  DATA_SECRET: "秘密 (DATA_SECRET)",
  DATA_TOP_SECRET: "机密 (DATA_TOP_SECRET)",
};
const toLegacy = (v: DataLevel): "PUBLIC" | "INTERNAL" | "SECRET" | "TOP_SECRET" =>
  v === "DATA_PUBLIC" ? "PUBLIC" : v === "DATA_INTERNAL" ? "INTERNAL" : v === "DATA_SECRET" ? "SECRET" : "TOP_SECRET";
const fromLegacy = (v: string): DataLevel => {
  const u = String(v || "").toUpperCase();
  if (u === "PUBLIC") return "DATA_PUBLIC";
  if (u === "INTERNAL") return "DATA_INTERNAL";
  if (u === "SECRET") return "DATA_SECRET";
  if (u === "TOP_SECRET") return "DATA_TOP_SECRET";
  return "DATA_INTERNAL";
};

const SEVERITY_LABELS: Record<SeverityLevel, string> = {
  INFO: "提示",
  LOW: "低",
  MEDIUM: "中",
  HIGH: "高",
  CRITICAL: "严重",
};

const SEVERITY_BADGE: Record<SeverityLevel, "outline" | "secondary" | "default" | "destructive"> = {
  INFO: "outline",
  LOW: "secondary",
  MEDIUM: "default",
  HIGH: "destructive",
  CRITICAL: "destructive",
};

const FREQUENCY_PRESETS: { preset: RuleForm["frequencyPreset"]; label: string; cron: string }[] = [
  { preset: "HOURLY", label: "小时级", cron: "0 0 * * * ?" },
  { preset: "DAILY", label: "日批", cron: "0 0 2 * * ?" },
  { preset: "CUSTOM", label: "自定义", cron: "" },
];

const DATA_LEVEL_OPTIONS: { value: DataLevel; label: string }[] = [
  { value: "DATA_PUBLIC", label: "公开 (DATA_PUBLIC)" },
  { value: "DATA_INTERNAL", label: "内部 (DATA_INTERNAL)" },
  { value: "DATA_SECRET", label: "秘密 (DATA_SECRET)" },
  { value: "DATA_TOP_SECRET", label: "机密 (DATA_TOP_SECRET)" },
];

const SEVERITY_OPTIONS: { value: SeverityLevel; label: string }[] = [
  { value: "INFO", label: SEVERITY_LABELS.INFO },
  { value: "LOW", label: SEVERITY_LABELS.LOW },
  { value: "MEDIUM", label: SEVERITY_LABELS.MEDIUM },
  { value: "HIGH", label: SEVERITY_LABELS.HIGH },
  { value: "CRITICAL", label: SEVERITY_LABELS.CRITICAL },
];

const INITIAL_FORM: RuleForm = {
  name: "",
  datasetId: "",
  owner: "",
  severity: "MEDIUM",
  dataLevel: "DATA_INTERNAL",
  frequencyPreset: "DAILY",
  customCron: "0 0 2 * * ?",
  enabled: true,
  sql: "",
  description: "",
};

const QualityRulesPage = () => {
  const [rules, setRules] = useState<RuleRow[]>([]);
  const [ruleLoading, setRuleLoading] = useState(false);
  const [datasets, setDatasets] = useState<DatasetOption[]>([]);
  const [datasetLookup, setDatasetLookup] = useState<Map<string, DatasetOption>>(new Map());
  const [detailRule, setDetailRule] = useState<RuleRow | null>(null);
  const [detailDrawerOpen, setDetailDrawerOpen] = useState(false);
  const [detailRuns, setDetailRuns] = useState<QualityRunView[]>([]);
  const [runLoading, setRunLoading] = useState(false);
  const [form, setForm] = useState<RuleForm>(INITIAL_FORM);
  const [formMode, setFormMode] = useState<"create" | "edit">("create");
  const [formOpen, setFormOpen] = useState(false);

  const refreshDatasets = async () => {
    try {
      const resp: any = await listDatasets({ page: 0, size: 200 });
      const content = Array.isArray(resp?.content) ? resp.content : [];
      const list: DatasetOption[] = content.map((item: any) => ({
        id: String(item.id),
        name: item.name || item.hiveTable || item.trinoTable || item.code || String(item.id),
        hiveDatabase: item.hiveDatabase,
        hiveTable: item.hiveTable,
        classification: item.classification,
      }));
      setDatasets(list);
      const map = new Map<string, DatasetOption>();
      list.forEach((d) => map.set(d.id, d));
      setDatasetLookup(map);
      if (!form.datasetId && list.length) {
        setForm((prev) => ({ ...prev, datasetId: list[0].id }));
      }
    } catch (error) {
      console.error(error);
      toast.error("加载数据集失败");
    }
  };

  const mapRule = (raw: any): RuleRow => {
    const bindings: RuleBindingView[] = Array.isArray(raw?.bindings)
      ? raw.bindings.map((binding: any) => ({
          id: String(binding.id),
          datasetId: binding.datasetId ? String(binding.datasetId) : undefined,
          datasetAlias: binding.datasetAlias,
          scopeType: binding.scopeType,
          filterExpression: binding.filterExpression,
        }))
      : [];
    const datasetNames = bindings.map((binding) => {
      if (binding.datasetAlias) return binding.datasetAlias;
      if (binding.datasetId && datasetLookup.has(binding.datasetId)) {
        return datasetLookup.get(binding.datasetId)!.name;
      }
      return binding.datasetId || "-";
    });
    const def = safeParseJson(raw?.latestVersion?.definition);
    const sqlPreview = extractSql(def);
    const cron = raw?.frequencyCron || raw?.frequency || raw?.latestVersion?.frequencyCron;
    return {
      id: String(raw.id),
      code: raw.code,
      name: raw.name || raw.code || String(raw.id),
      type: raw.type || def?.type || "自定义SQL",
      owner: raw.owner || raw.createdBy,
      severity: (raw.severity || "MEDIUM").toUpperCase() as SeverityLevel,
      dataLevel: fromLegacy(raw.dataLevel || "INTERNAL"),
      frequencyCron: cron,
      frequencyLabel: toFrequencyLabel(cron),
      enabled: raw.enabled !== false,
      datasetNames,
      datasetIds: bindings.map((b) => b.datasetId || ""),
      bindings,
      description: raw.description,
      sqlPreview,
      createdAt: raw.createdDate,
      updatedAt: raw.lastModifiedDate,
      raw,
    };
  };

  const refreshRules = async () => {
    setRuleLoading(true);
    try {
      const resp: any = await listQualityRules();
      const list = Array.isArray(resp) ? resp : [];
      const mapped = list.map(mapRule);
      setRules(mapped);
    } catch (error) {
      console.error(error);
      toast.error("加载质量规则失败");
    } finally {
      setRuleLoading(false);
    }
  };

  useEffect(() => {
    refreshDatasets();
    refreshRules();
  }, []);

  useEffect(() => {
    if (!detailRule) {
      setDetailRuns([]);
      return;
    }
    setRunLoading(true);
    (async () => {
      try {
        const list: any = await listQualityRuns({ ruleId: detailRule.id, limit: 5 });
        const mapped: QualityRunView[] = Array.isArray(list)
          ? list.map((run: any) => ({
              id: String(run.id),
              status: run.status || "UNKNOWN",
              severity: run.severity,
              startedAt: run.startedAt,
              finishedAt: run.finishedAt,
              durationMs: run.durationMs,
              message: run.message,
            }))
          : [];
        setDetailRuns(mapped);
      } catch (error) {
        console.error(error);
        setDetailRuns([]);
      } finally {
        setRunLoading(false);
      }
    })();
  }, [detailRule?.id]);

  const openCreateForm = () => {
    const firstDataset = datasets[0]?.id || "";
    setForm({
      ...INITIAL_FORM,
      datasetId: firstDataset,
      customCron: FREQUENCY_PRESETS.find((p) => p.preset === "DAILY")?.cron ?? "0 0 2 * * ?",
    });
    setFormMode("create");
    setFormOpen(true);
  };

  const openEditForm = (rule: RuleRow) => {
    const raw = rule.raw || {};
    const cron = raw.frequencyCron || rule.frequencyCron || "0 0 2 * * ?";
    const preset = detectPreset(cron);
    const primaryDataset = rule.datasetIds[0] || "";
    setForm({
      id: rule.id,
      code: raw.code,
      name: rule.name,
      datasetId: primaryDataset,
      owner: rule.owner || "",
      severity: rule.severity || "MEDIUM",
      dataLevel: rule.dataLevel || "INTERNAL",
      frequencyPreset: preset,
      customCron: preset === "CUSTOM" ? cron : FREQUENCY_PRESETS.find((p) => p.preset === preset)?.cron || cron,
      enabled: rule.enabled,
      sql: rule.sqlPreview || "",
      description: rule.description || "",
    });
    setFormMode("edit");
    setFormOpen(true);
  };

  const handleSubmit = async () => {
    if (!form.name.trim()) {
      toast.error("请输入规则名称");
      return;
    }
    if (!form.datasetId) {
      toast.error("请选择目标数据集");
      return;
    }
    if (!form.sql.trim()) {
      toast.error("请填写质量检测 SQL 或表达式");
      return;
    }
    const cron = form.frequencyPreset === "CUSTOM"
      ? form.customCron?.trim()
      : FREQUENCY_PRESETS.find((item) => item.preset === form.frequencyPreset)?.cron;
    if (!cron) {
      toast.error("请配置执行频率");
      return;
    }
    const dataset = datasetLookup.get(form.datasetId);
    const payload = {
      code: form.code?.trim() || undefined,
      name: form.name.trim(),
      type: "CUSTOM_SQL",
      category: "QUALITY",
      description: form.description?.trim(),
      owner: form.owner.trim(),
      severity: form.severity,
      dataLevel: toLegacy(form.dataLevel),
      frequencyCron: cron,
      enabled: form.enabled,
      datasetId: form.datasetId,
      definition: {
        type: "SQL",
        sql: form.sql,
      },
      bindings: [
        {
          datasetId: form.datasetId,
          datasetAlias: dataset?.name,
          scopeType: "TABLE",
          fieldRefs: [],
          filterExpression: null,
        },
      ],
    };
    try {
      if (formMode === "create") {
        await createQualityRule(payload);
        toast.success("创建质量规则成功");
      } else if (form.id) {
        await updateQualityRule(form.id, payload);
        toast.success("更新质量规则成功");
      }
      setFormOpen(false);
      refreshRules();
    } catch (error: any) {
      console.error(error);
      toast.error(error?.message || "保存质量规则失败");
    }
  };

  const handleDelete = async (rule: RuleRow) => {
    if (!window.confirm(`确定删除质量规则「${rule.name}」吗？`)) {
      return;
    }
    try {
      await deleteQualityRule(rule.id);
      toast.success("删除成功");
      refreshRules();
    } catch (error) {
      console.error(error);
      toast.error("删除失败");
    }
  };

  const handleToggle = async (rule: RuleRow) => {
    try {
      await toggleQualityRule(rule.id, !rule.enabled);
      toast.success(!rule.enabled ? "已启用" : "已停用");
      refreshRules();
    } catch (error) {
      console.error(error);
      toast.error("切换状态失败");
    }
  };

  const handleManualRun = async (rule: RuleRow) => {
    try {
      const bindingId = rule.bindings[0]?.id;
      if (!bindingId) {
        toast.error("该规则尚未配置绑定");
        return;
      }
      await triggerQualityRun({ ruleId: rule.id, bindingId, triggerType: "MANUAL" });
      toast.success("已提交检测任务");
      refreshRunsForRule(rule.id);
    } catch (error) {
      console.error(error);
      toast.error("触发检测失败");
    }
  };

  const refreshRunsForRule = async (ruleId: string) => {
    if (!detailRule || detailRule.id !== ruleId) return;
    try {
      const list: any = await listQualityRuns({ ruleId, limit: 5 });
      const mapped: QualityRunView[] = Array.isArray(list)
        ? list.map((run: any) => ({
            id: String(run.id),
            status: run.status || "UNKNOWN",
            severity: run.severity,
            startedAt: run.startedAt,
            finishedAt: run.finishedAt,
            durationMs: run.durationMs,
            message: run.message,
          }))
        : [];
      setDetailRuns(mapped);
    } catch (error) {
      console.error(error);
    }
  };

  const openDetail = (rule: RuleRow) => {
    setDetailRule(rule);
    setDetailDrawerOpen(true);
  };

  const closeDetail = () => {
    setDetailDrawerOpen(false);
    setDetailRule(null);
    setDetailRuns([]);
  };

  const datasetSummary = useMemo(() => {
    const unique = new Set<string>();
    rules.forEach((rule) => rule.datasetIds.forEach((id) => id && unique.add(id)));
    return unique.size;
  }, [rules]);

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle>质量规则</CardTitle>
            <p className="text-sm text-muted-foreground">管理质量检测策略并跟踪执行状态</p>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="secondary" onClick={refreshRules} disabled={ruleLoading}>
              <Icon name="Sync" className="mr-2 h-4 w-4" />刷新
            </Button>
            <Button onClick={openCreateForm}>
              <Icon name="Plus" className="mr-2 h-4 w-4" />新增规则
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
            <SummaryCard icon="ListChecks" label="规则数量" value={rules.length} />
            <SummaryCard icon="Database" label="覆盖数据集" value={datasetSummary} />
            <SummaryCard
              icon="ShieldCheck"
              label="启用规则"
              value={rules.filter((rule) => rule.enabled).length}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>规则列表</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="hidden w-full flex-col gap-2 md:flex">
            <div className="grid grid-cols-[2fr,1.2fr,1fr,1fr,1fr,160px] items-center gap-2 border-b pb-2 text-xs uppercase text-muted-foreground">
              <span>名称 / 编码</span>
              <span>数据集</span>
              <span>数据密级</span>
              <span>责任人</span>
              <span>频率</span>
              <span className="text-right">操作</span>
            </div>
            {rules.map((rule) => (
              <div
                key={rule.id}
                className="grid grid-cols-[2fr,1.2fr,1fr,1fr,1fr,160px] items-center gap-2 rounded-md border px-3 py-2 text-sm transition hover:bg-muted/60"
              >
                <div className="flex flex-col">
                  <span className="font-medium text-foreground">{rule.name}</span>
                  <span className="text-xs text-muted-foreground">{rule.code || rule.id}</span>
                </div>
                <div className="text-sm text-muted-foreground truncate">
                  {rule.datasetNames.length ? rule.datasetNames.join(", ") : "未绑定"}
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant="outline">{LEVEL_LABELS[rule.dataLevel || "INTERNAL"]}</Badge>
                  {rule.severity && (
                    <Badge variant={SEVERITY_BADGE[rule.severity]}>{SEVERITY_LABELS[rule.severity]}</Badge>
                  )}
                </div>
                <div className="text-sm text-muted-foreground">{rule.owner || "-"}</div>
                <div className="text-sm text-muted-foreground">{rule.frequencyLabel || "-"}</div>
                <div className="flex justify-end gap-2">
                  <Button variant="ghost" size="icon" title="查看详情" onClick={() => openDetail(rule)}>
                    <Icon name="Eye" className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" title="手动执行" onClick={() => handleManualRun(rule)}>
                    <Icon name="Play" className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" title="启用/停用" onClick={() => handleToggle(rule)}>
                    <Icon name={rule.enabled ? "Pause" : "PlayCircle"} className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" title="编辑" onClick={() => openEditForm(rule)}>
                    <Icon name="Pencil" className="h-4 w-4" />
                  </Button>
                  <Button variant="ghost" size="icon" title="删除" onClick={() => handleDelete(rule)}>
                    <Icon name="Trash" className="h-4 w-4 text-destructive" />
                  </Button>
                </div>
              </div>
            ))}
            {!ruleLoading && rules.length === 0 && (
              <div className="rounded-md border border-dashed p-12 text-center text-sm text-muted-foreground">
                暂无质量规则，点击右上角“新增规则”开始配置。
              </div>
            )}
          </div>
          <div className="flex flex-col gap-3 md:hidden">
            {rules.map((rule) => (
              <Card key={rule.id} className="p-4">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="font-medium text-foreground">{rule.name}</div>
                    <div className="text-xs text-muted-foreground">{rule.code || rule.id}</div>
                  </div>
                  <div className="flex gap-1">
                    <Badge variant="outline">{LEVEL_LABELS[rule.dataLevel || "INTERNAL"]}</Badge>
                    {rule.severity && (
                      <Badge variant={SEVERITY_BADGE[rule.severity]}>{SEVERITY_LABELS[rule.severity]}</Badge>
                    )}
                  </div>
                </div>
                <div className="mt-3 space-y-2 text-sm text-muted-foreground">
                  <div>
                    <span className="font-medium text-foreground">数据集：</span>
                    {rule.datasetNames.length ? rule.datasetNames.join(", ") : "未绑定"}
                  </div>
                  <div>
                    <span className="font-medium text-foreground">责任人：</span>
                    {rule.owner || "-"}
                  </div>
                  <div>
                    <span className="font-medium text-foreground">频率：</span>
                    {rule.frequencyLabel || "-"}
                  </div>
                </div>
                <div className="mt-4 flex flex-wrap gap-2">
                  <Button size="sm" variant="outline" onClick={() => openDetail(rule)}>
                    查看
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => handleManualRun(rule)}>
                    执行
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => handleToggle(rule)}>
                    {rule.enabled ? "停用" : "启用"}
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => openEditForm(rule)}>
                    编辑
                  </Button>
                  <Button size="sm" variant="outline" onClick={() => handleDelete(rule)}>
                    删除
                  </Button>
                </div>
              </Card>
            ))}
          </div>
        </CardContent>
      </Card>

      <Drawer open={detailDrawerOpen} onOpenChange={(open) => (open ? setDetailDrawerOpen(true) : closeDetail())}>
        <DrawerContent className="max-h-[90vh]">
          <DrawerHeader className="flex flex-col items-start gap-2 text-left">
            <DrawerTitle>{detailRule?.name}</DrawerTitle>
            <DrawerDescription className="whitespace-pre-line text-sm">
              规则编码：{detailRule?.code || detailRule?.id}
            </DrawerDescription>
          </DrawerHeader>
          <div className="grid grid-cols-1 gap-6 px-6 pb-6 lg:grid-cols-2">
            <section className="space-y-4">
              <h3 className="text-sm font-semibold text-muted-foreground">基础信息</h3>
              <div className="space-y-2 text-sm">
                <InfoRow label="责任人" value={detailRule?.owner || "-"} />
                <InfoRow label="数据密级" value={detailRule ? LEVEL_LABELS[detailRule.dataLevel || "DATA_INTERNAL"] : "-"} />
                <InfoRow
                  label="严重程度"
                  value={detailRule?.severity ? SEVERITY_LABELS[detailRule.severity] : "-"}
                />
                <InfoRow label="调度频率" value={detailRule?.frequencyLabel || "-"} />
                <InfoRow
                  label="绑定数据集"
                  value={detailRule?.datasetNames.length ? detailRule.datasetNames.join(", ") : "未绑定"}
                />
              </div>
              <div className="space-y-2 text-sm">
                <Label>检测 SQL / 表达式</Label>
                <ScrollArea className="h-48 rounded-md border bg-muted/40 p-3 text-xs font-mono">
                  {detailRule?.sqlPreview || "暂无配置"}
                </ScrollArea>
              </div>
            </section>
            <section className="space-y-4">
              <div className="flex items-center justify-between">
                <h3 className="text-sm font-semibold text-muted-foreground">最近检测</h3>
                <Button size="sm" variant="secondary" onClick={() => detailRule && handleManualRun(detailRule)}>
                  <Icon name="Play" className="mr-2 h-4 w-4" />手动执行
                </Button>
              </div>
              <div className="space-y-2 text-sm">
                {runLoading && <div className="text-muted-foreground">正在加载执行记录...</div>}
                {!runLoading && detailRuns.length === 0 && (
                  <div className="text-muted-foreground">暂无执行记录</div>
                )}
                <div className="space-y-3">
                  {detailRuns.map((run) => (
                    <div key={run.id} className="rounded-md border p-3">
                      <div className="flex items-center justify-between text-sm font-medium">
                        <span>{run.status}</span>
                        <Badge variant="outline">{run.severity || detailRule?.severity || "-"}</Badge>
                      </div>
                      <div className="mt-2 text-xs text-muted-foreground">
                        <div>开始：{formatTime(run.startedAt)}</div>
                        <div>完成：{formatTime(run.finishedAt)}</div>
                        <div>耗时：{formatDuration(run.durationMs)}</div>
                        <div className="mt-1">说明：{run.message || "-"}</div>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </section>
          </div>
          <DrawerFooter className="flex flex-row items-center justify-between border-t bg-muted/30 px-6 py-4">
            <div className="text-xs text-muted-foreground">
              创建：{formatTime(detailRule?.createdAt)}
              {" "}· 更新：{formatTime(detailRule?.updatedAt)}
            </div>
            <DrawerClose asChild>
              <Button variant="outline">关闭</Button>
            </DrawerClose>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>

      <Dialog open={formOpen} onOpenChange={setFormOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{formMode === "create" ? "新增质量规则" : "编辑质量规则"}</DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label>规则名称</Label>
              <Input value={form.name} onChange={(event) => setForm((prev) => ({ ...prev, name: event.target.value }))} />
            </div>
            <div className="space-y-2">
              <Label>责任人</Label>
              <Input value={form.owner} onChange={(event) => setForm((prev) => ({ ...prev, owner: event.target.value }))} />
            </div>
            <div className="space-y-2">
              <Label>数据集</Label>
              <Select value={form.datasetId} onValueChange={(value) => setForm((prev) => ({ ...prev, datasetId: value }))}>
                <SelectTrigger>
                  <SelectValue placeholder="选择数据集" />
                </SelectTrigger>
                <SelectContent>
                  {datasets.map((dataset) => (
                    <SelectItem key={dataset.id} value={dataset.id}>
                      {dataset.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>数据密级</Label>
              <Select
                value={form.dataLevel}
                onValueChange={(value: DataLevel) => setForm((prev) => ({ ...prev, dataLevel: value }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {DATA_LEVEL_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>严重程度</Label>
              <Select
                value={form.severity}
                onValueChange={(value: SeverityLevel) => setForm((prev) => ({ ...prev, severity: value }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SEVERITY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>执行频率</Label>
              <Select
                value={form.frequencyPreset}
                onValueChange={(value: RuleForm["frequencyPreset"]) =>
                  setForm((prev) => ({
                    ...prev,
                    frequencyPreset: value,
                    customCron:
                      value === "CUSTOM"
                        ? prev.customCron
                        : FREQUENCY_PRESETS.find((item) => item.preset === value)?.cron || prev.customCron,
                  }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {FREQUENCY_PRESETS.map((option) => (
                    <SelectItem key={option.preset} value={option.preset}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {form.frequencyPreset === "CUSTOM" && (
                <Input
                  placeholder="请输入 Cron 表达式"
                  value={form.customCron}
                  onChange={(event) => setForm((prev) => ({ ...prev, customCron: event.target.value }))}
                />
              )}
            </div>
            <div className="flex items-center gap-2">
              <Switch checked={form.enabled} onCheckedChange={(checked) => setForm((prev) => ({ ...prev, enabled: checked }))} />
              <Label>启用规则</Label>
            </div>
            <div className="md:col-span-2 space-y-2">
              <Label>质量检测 SQL</Label>
              <Textarea
                placeholder="请输入检测 SQL，支持使用 :date 等参数占位符"
                value={form.sql}
                onChange={(event) => setForm((prev) => ({ ...prev, sql: event.target.value }))}
                rows={8}
              />
            </div>
            <div className="md:col-span-2 space-y-2">
              <Label>规则说明 (可选)</Label>
              <Textarea
                placeholder="描述规则用途、告警阈值等信息"
                value={form.description}
                onChange={(event) => setForm((prev) => ({ ...prev, description: event.target.value }))}
                rows={4}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setFormOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSubmit}>{formMode === "create" ? "创建" : "保存"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
};

export default QualityRulesPage;

const SummaryCard = ({ icon, label, value }: { icon: string; label: string; value: number }) => (
  <Card className="border-dashed">
    <CardContent className="flex items-center gap-3 py-4">
      <div className="flex h-10 w-10 items-center justify-center rounded-full bg-muted">
        <Icon name={icon} className="h-5 w-5 text-primary" />
      </div>
      <div>
        <div className="text-sm text-muted-foreground">{label}</div>
        <div className="text-2xl font-semibold text-foreground">{value}</div>
      </div>
    </CardContent>
  </Card>
);

const InfoRow = ({ label, value }: { label: string; value: string }) => (
  <div className="flex items-start gap-3 text-sm">
    <span className="w-20 shrink-0 text-muted-foreground">{label}</span>
    <span className="flex-1 text-foreground">{value}</span>
  </div>
);

const safeParseJson = (value: any): any => {
  if (!value) return null;
  if (typeof value === "object") return value;
  try {
    return JSON.parse(String(value));
  } catch (error) {
    return null;
  }
};

const extractSql = (definition: any): string => {
  if (!definition) return "";
  if (typeof definition === "string") return definition;
  if (definition.sql) return String(definition.sql);
  if (definition.query) return String(definition.query);
  return JSON.stringify(definition, null, 2);
};

const toFrequencyLabel = (cron?: string): string | undefined => {
  if (!cron) return undefined;
  const preset = FREQUENCY_PRESETS.find((item) => item.cron === cron);
  if (preset) return preset.label;
  if (cron === "@partition" || cron === "@event") return "分区触发";
  return cron;
};

const detectPreset = (cron: string): RuleForm["frequencyPreset"] => {
  const preset = FREQUENCY_PRESETS.find((item) => item.cron === cron);
  if (preset) return preset.preset;
  return "CUSTOM";
};

const formatTime = (value?: string) => {
  if (!value) return "-";
  try {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString();
  } catch (error) {
    return value;
  }
};

const formatDuration = (ms?: number) => {
  if (!ms || ms <= 0) return "-";
  const seconds = Math.round(ms / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  const remain = seconds % 60;
  return `${minutes}m${remain ? ` ${remain}s` : ""}`;
};
