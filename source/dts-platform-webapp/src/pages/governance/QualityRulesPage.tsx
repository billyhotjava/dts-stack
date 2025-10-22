import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { Icon } from "@/components/icon";
import { Badge } from "@/ui/badge";
import { Button } from "@/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/ui/card";
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/ui/dialog";
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
import { normalizeClassification, type ClassificationLevel } from "@/utils/classification";


type DataLevel = "DATA_PUBLIC" | "DATA_INTERNAL" | "DATA_SECRET" | "DATA_CONFIDENTIAL";
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
  lastRunStatus?: string;
  lastRunFinishedAt?: string;
  lastRunDurationMs?: number;
  lastRunMessage?: string | null;
  runSuccessRate?: number | null;
  totalRuns?: number | null;
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

const toLegacy = (v: DataLevel): ClassificationLevel =>
	v === "DATA_PUBLIC"
		? "PUBLIC"
		: v === "DATA_INTERNAL"
			? "INTERNAL"
			: v === "DATA_SECRET"
				? "SECRET"
				: "CONFIDENTIAL";
const fromLegacy = (v: string): DataLevel => {
	const normalized = normalizeClassification(v);
	if (normalized === "PUBLIC") return "DATA_PUBLIC";
	if (normalized === "INTERNAL") return "DATA_INTERNAL";
	if (normalized === "SECRET") return "DATA_SECRET";
	if (normalized === "CONFIDENTIAL") return "DATA_CONFIDENTIAL";
	return "DATA_CONFIDENTIAL";
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

type SqlTemplateId = "daily_null_ratio" | "duplicate_check" | "negative_amount" | "freshness_check";

const SQL_TEMPLATES: Array<{
  id: SqlTemplateId;
  label: string;
  description: string;
  severity: SeverityLevel;
  preset: RuleForm["frequencyPreset"];
  cron?: string;
  build: (dataset?: DatasetOption) => string;
}> = [
  {
    id: "daily_null_ratio",
    label: "字段空值占比（日批）",
    description: "检测关键字段空值占比是否超过 5%",
    severity: "HIGH",
    preset: "DAILY",
    cron: "0 5 2 * * ?",
    build: (dataset) => {
      const table = dataset?.hiveTable || dataset?.name || "target_table";
      return `WITH base AS (
    SELECT
        COUNT(*) AS total_cnt,
        SUM(CASE WHEN critical_field IS NULL THEN 1 ELSE 0 END) AS null_cnt
    FROM ${table}
    WHERE dt = DATE_FORMAT(DATE_SUB(CURRENT_DATE, 1), 'yyyyMMdd')
)
SELECT
    total_cnt,
    null_cnt,
    CASE WHEN total_cnt = 0 THEN 0 ELSE null_cnt / total_cnt END AS null_ratio
FROM base
WHERE CASE WHEN total_cnt = 0 THEN 0 ELSE null_cnt / total_cnt END > 0.05;`;
    },
  },
  {
    id: "duplicate_check",
    label: "主键重复校验（小时）",
    description: "校验主键组合在最近一小时是否存在重复",
    severity: "CRITICAL",
    preset: "HOURLY",
    cron: "0 0/30 * * * ?",
    build: (dataset) => {
      const table = dataset?.hiveTable || dataset?.name || "target_table";
      return `SELECT business_key, COUNT(*) AS dup_cnt
FROM ${table}
WHERE event_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY business_key
HAVING COUNT(*) > 1;`;
    },
  },
  {
    id: "negative_amount",
    label: "金额为负检查（日批）",
    description: "金额字段出现负值时触发",
    severity: "MEDIUM",
    preset: "DAILY",
    cron: "0 30 2 * * ?",
    build: (dataset) => {
      const table = dataset?.hiveTable || dataset?.name || "target_table";
      return `SELECT order_id, amount, updated_at
FROM ${table}
WHERE dt = DATE_FORMAT(DATE_SUB(CURRENT_DATE, 1), 'yyyyMMdd')
  AND amount < 0;`;
    },
  },
  {
    id: "freshness_check",
    label: "数据新鲜度（自定义 Cron）",
    description: "检测最近分区是否在 2 小时内更新",
    severity: "HIGH",
    preset: "CUSTOM",
    cron: "0 0/20 * * * ?",
    build: (dataset) => {
      const table = dataset?.hiveTable || dataset?.name || "target_table";
      return `WITH latest AS (
    SELECT MAX(updated_at) AS max_time FROM ${table}
)
SELECT max_time
FROM latest
WHERE max_time < DATE_SUB(NOW(), INTERVAL 2 HOUR);`;
    },
  },
];

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
  const [searchKeyword, setSearchKeyword] = useState("");
  const [severityFilter, setSeverityFilter] = useState<SeverityLevel | "ALL">("ALL");
  const [datasetFilter, setDatasetFilter] = useState<string>("ALL");
  const [enabledFilter, setEnabledFilter] = useState<"ALL" | "ENABLED" | "DISABLED">("ALL");

  const inferDataLevel = useCallback(
    (datasetId?: string): DataLevel => {
      if (datasetId && datasetLookup.has(datasetId)) {
        const classification = datasetLookup.get(datasetId)?.classification;
        return fromLegacy(classification || "INTERNAL");
      }
      return "DATA_INTERNAL";
    },
    [datasetLookup],
  );

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
      setForm((prev) => {
        const currentId = prev.datasetId;
        const hasCurrent = Boolean(currentId) && map.has(currentId);
        const fallbackDataset = list[0]?.id || "";
        const nextId = hasCurrent ? (currentId as string) : fallbackDataset;
        const nextLevel = hasCurrent
          ? prev.dataLevel
          : nextId
            ? fromLegacy(map.get(nextId)?.classification || "INTERNAL")
            : prev.dataLevel;
        return {
          ...prev,
          datasetId: nextId,
          dataLevel: nextLevel,
        };
      });
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
    const latestRun =
      raw?.latestRun ||
      raw?.lastRun ||
      (Array.isArray(raw?.recentRuns) && raw.recentRuns.length ? raw.recentRuns[0] : null) ||
      (Array.isArray(raw?.executions) && raw.executions.length ? raw.executions[0] : null);
    const runStats = raw?.runStats || raw?.statistics || raw?.metrics;
    const successRate =
      typeof runStats?.successRate === "number"
        ? runStats.successRate
        : typeof runStats?.successRatePct === "number"
        ? runStats.successRatePct / 100
        : null;
    const totalRuns = Number.isFinite(runStats?.totalRuns)
      ? Number(runStats.totalRuns)
      : Number.isFinite(runStats?.runs)
      ? Number(runStats.runs)
      : latestRun && Array.isArray(raw?.recentRuns)
      ? raw.recentRuns.length
      : null;
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
      lastRunStatus: latestRun?.status ? String(latestRun.status).toUpperCase() : undefined,
      lastRunFinishedAt: latestRun?.finishedAt || latestRun?.endedAt || latestRun?.createdDate,
      lastRunDurationMs: Number.isFinite(latestRun?.durationMs)
        ? Number(latestRun.durationMs)
        : Number.isFinite(latestRun?.elapsedMs)
        ? Number(latestRun.elapsedMs)
        : undefined,
      lastRunMessage: latestRun?.message || latestRun?.resultMessage || latestRun?.detail,
      runSuccessRate:
        successRate !== null && !Number.isNaN(successRate) ? Math.max(0, Math.min(1, successRate)) : null,
      totalRuns,
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
    if (!datasetLookup.size || !rules.length) return;
    setRules((prev) => prev.map((rule) => mapRule(rule.raw)));
  }, [datasetLookup]);

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
      dataLevel: inferDataLevel(firstDataset),
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
      dataLevel: rule.dataLevel ? rule.dataLevel : fromLegacy("INTERNAL"),
      frequencyPreset: preset,
      customCron: preset === "CUSTOM" ? cron : FREQUENCY_PRESETS.find((p) => p.preset === preset)?.cron || cron,
      enabled: rule.enabled,
      sql: rule.sqlPreview || "",
      description: rule.description || "",
    });
    setFormMode("edit");
    setFormOpen(true);
  };

  const handleApplyTemplate = (templateId: SqlTemplateId) => {
    const template = SQL_TEMPLATES.find((item) => item.id === templateId);
    if (!template) {
      return;
    }
    setForm((prev) => {
      const dataset = prev.datasetId ? datasetLookup.get(prev.datasetId) : undefined;
      const cronValue =
        template.preset === "CUSTOM"
          ? template.cron || prev.customCron || "0 0/30 * * * ?"
          : template.cron ||
            FREQUENCY_PRESETS.find((item) => item.preset === template.preset)?.cron ||
            prev.customCron ||
            "0 0 2 * * ?";
      return {
        ...prev,
        severity: template.severity,
        frequencyPreset: template.preset,
        customCron: cronValue,
        sql: template.build(dataset),
      };
    });
    toast.success(`已套用模板：${template.label}`);
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
    const trimmedSql = form.sql.trim();
    if (!trimmedSql) {
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
    const owner = form.owner.trim() || "data_quality";
    const payload = {
      code: form.code?.trim() || undefined,
      name: form.name.trim(),
      type: "CUSTOM_SQL",
      category: "QUALITY",
      description: form.description?.trim(),
      owner,
      severity: form.severity,
      dataLevel: toLegacy(form.dataLevel),
      frequencyCron: cron,
      enabled: form.enabled,
      datasetId: form.datasetId,
      definition: {
        type: "SQL",
        sql: trimmedSql,
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
    setRunLoading(true);
    try {
      const bindingId = rule.bindings[0]?.id;
      const datasetId = rule.datasetIds[0];
      if (!bindingId && !datasetId) {
        toast.error("该规则缺少绑定对象，请先配置数据集");
        return;
      }
      const payload: any = { ruleId: rule.id, triggerType: "MANUAL" };
      if (bindingId) {
        payload.bindingId = bindingId;
      }
      if (datasetId) {
        payload.datasetId = datasetId;
      }
      await triggerQualityRun(payload);
      toast.success("已提交检测任务");
      refreshRunsForRule(rule.id);
    } catch (error) {
      console.error(error);
      toast.error("触发检测失败");
    } finally {
      setRunLoading(false);
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

  const filteredRules = useMemo(() => {
    const keyword = searchKeyword.trim().toLowerCase();
    const filtered = rules.filter((rule) => {
      const matchKeyword =
        !keyword ||
        rule.name.toLowerCase().includes(keyword) ||
        (rule.code ?? "").toLowerCase().includes(keyword) ||
        (rule.owner ?? "").toLowerCase().includes(keyword) ||
        rule.datasetNames.some((name) => name.toLowerCase().includes(keyword));
      const matchSeverity = severityFilter === "ALL" || rule.severity === severityFilter;
      const matchDataset = datasetFilter === "ALL" || rule.datasetIds.includes(datasetFilter);
      const matchEnabled =
        enabledFilter === "ALL" ||
        (enabledFilter === "ENABLED" ? rule.enabled : !rule.enabled);
      return matchKeyword && matchSeverity && matchDataset && matchEnabled;
    });
    return filtered.sort((a, b) => {
      const severityScore = (value?: SeverityLevel) =>
        value === "CRITICAL"
          ? 5
          : value === "HIGH"
          ? 4
          : value === "MEDIUM"
          ? 3
          : value === "LOW"
          ? 2
          : 1;
      const diff = severityScore(b.severity) - severityScore(a.severity);
      if (diff !== 0) return diff;
      return (b.updatedAt || "").localeCompare(a.updatedAt || "");
    });
  }, [rules, searchKeyword, severityFilter, datasetFilter, enabledFilter]);

  const datasetFilterOptions = useMemo(() => {
    return datasets.map((dataset) => ({
      value: dataset.id,
      label: dataset.name,
    }));
  }, [datasets]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-center gap-2 rounded-md border border-dashed border-red-200 bg-red-50 px-4 py-3 text-center text-sm font-medium text-red-700 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200">
        <Icon icon="mdi:star" className="h-5 w-5 text-red-500" />
        <span className="text-center">非密模块禁止处理涉密数据</span>
      </div>
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
          <div className="mb-4 flex flex-wrap items-center gap-3">
            <Input
              placeholder="搜索规则 / 责任人 / 数据集"
              value={searchKeyword}
              onChange={(event) => setSearchKeyword(event.target.value)}
              className="min-w-[220px] flex-1"
            />
            <div className="w-full sm:w-[160px]">
              <Select value={severityFilter} onValueChange={(value) => setSeverityFilter(value as SeverityLevel | "ALL")}>
                <SelectTrigger>
                  <SelectValue placeholder="严重程度" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">全部严重程度</SelectItem>
                  {SEVERITY_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="w-full sm:w-[180px]">
              <Select value={datasetFilter} onValueChange={(value) => setDatasetFilter(value)}>
                <SelectTrigger>
                  <SelectValue placeholder="数据集" />
                </SelectTrigger>
                <SelectContent className="max-h-64">
                  <SelectItem value="ALL">全部数据集</SelectItem>
                  {datasetFilterOptions.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="w-full sm:w-[160px]">
              <Select value={enabledFilter} onValueChange={(value) => setEnabledFilter(value as "ALL" | "ENABLED" | "DISABLED")}>
                <SelectTrigger>
                  <SelectValue placeholder="启停状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">全部状态</SelectItem>
                  <SelectItem value="ENABLED">仅启用</SelectItem>
                  <SelectItem value="DISABLED">仅停用</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="overflow-x-auto">
            <table className="min-w-full table-auto border-collapse text-sm">
              <thead className="bg-muted/40 text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-3 py-3 text-left font-medium">规则信息</th>
                  <th className="px-3 py-3 text-left font-medium">绑定信息</th>
                  <th className="px-3 py-3 text-left font-medium">执行情况</th>
                  <th className="px-3 py-3 text-right font-medium">操作</th>
                </tr>
              </thead>
              <tbody>
                {ruleLoading ? (
                  <tr>
                    <td colSpan={4} className="px-3 py-6 text-center text-sm text-muted-foreground">
                      正在加载质量规则...
                    </td>
                  </tr>
                ) : filteredRules.length ? (
                  filteredRules.map((rule) => (
                    <tr key={rule.id} className="border-b last:border-none hover:bg-muted/60">
                      <td className="px-3 py-3 align-top">
                        <div className="flex flex-col gap-1">
                          <div className="flex items-center gap-2">
                            <span className="font-medium text-foreground">{rule.name}</span>
                            {rule.severity && (
                              <Badge variant={SEVERITY_BADGE[rule.severity]}>{SEVERITY_LABELS[rule.severity]}</Badge>
                            )}
                          </div>
                          <div className="text-xs text-muted-foreground">
                            编码：{rule.code || rule.id} · 责任人：{rule.owner || "-"}
                          </div>
                          {rule.description ? (
                            <div className="text-xs text-muted-foreground line-clamp-2">{rule.description}</div>
                          ) : null}
                        </div>
                      </td>
                      <td className="px-3 py-3 align-top">
						<div className="space-y-1 text-xs text-muted-foreground">
							<div>
								<span className="text-muted-foreground">数据集：</span>
								{rule.datasetNames.length ? rule.datasetNames.join(", ") : "未绑定"}
							</div>
							<div>
								<span className="text-muted-foreground">频率：</span>
								{rule.frequencyLabel || rule.frequencyCron || "-"}
							</div>
                          <div>
                            <span className="text-muted-foreground">状态：</span>
                            <Badge variant={rule.enabled ? "outline" : "secondary"}>
                              {rule.enabled ? "启用" : "停用"}
                            </Badge>
                          </div>
                        </div>
                      </td>
                      <td className="px-3 py-3 align-top">
                        <div className="space-y-1 text-xs text-muted-foreground">
                          <div>
                            最近执行：
                            {rule.lastRunStatus ? (
                              <Badge
                                variant="outline"
                                className={
                                  rule.lastRunStatus === "SUCCESS" || rule.lastRunStatus === "PASSED"
                                    ? "border-emerald-300 text-emerald-600"
                                    : rule.lastRunStatus === "FAILED"
                                    ? "border-red-300 text-red-600"
                                    : "border-muted-foreground text-muted-foreground"
                                }
                              >
                                {rule.lastRunStatus}
                              </Badge>
                            ) : (
                              "暂无记录"
                            )}
                          </div>
                          <div>完成时间：{formatTime(rule.lastRunFinishedAt)}</div>
                          <div>耗时：{rule.lastRunDurationMs ? formatDuration(rule.lastRunDurationMs) : "-"}</div>
                          {rule.lastRunMessage ? (
                            <div className="truncate text-xs text-muted-foreground">
                              结果：{rule.lastRunMessage}
                            </div>
                          ) : null}
                          {rule.runSuccessRate !== null && rule.runSuccessRate !== undefined ? (
                            <div>
                              成功率 {(rule.runSuccessRate * 100).toFixed(0)}%（共 {rule.totalRuns ?? "-"} 次）
                            </div>
                          ) : null}
                        </div>
                      </td>
                      <td className="px-3 py-3 align-top">
                        <div className="flex justify-end gap-2">
                          <Button variant="ghost" size="icon" title="查看详情" onClick={() => openDetail(rule)}>
                            <Icon name="Eye" className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" title={rule.enabled ? "停用规则" : "启用规则"} onClick={() => handleToggle(rule)}>
                            <Icon name={rule.enabled ? "Pause" : "PlayCircle"} className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" title="手动检测" onClick={() => handleManualRun(rule)}>
                            <Icon name="Play" className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" title="编辑" onClick={() => openEditForm(rule)}>
                            <Icon name="Pencil" className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" title="删除" onClick={() => handleDelete(rule)}>
                            <Icon name="Trash" className="h-4 w-4 text-destructive" />
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))
                ) : (
                  <tr>
                    <td colSpan={4} className="px-3 py-12 text-center text-sm text-muted-foreground">
                      未找到匹配的质量规则，请调整筛选条件或点击“新增规则”。
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      <Dialog open={detailDrawerOpen} onOpenChange={(open) => (open ? setDetailDrawerOpen(true) : closeDetail())}>
        <DialogContent className="mx-auto max-h-[85vh] w-full max-w-4xl overflow-y-auto rounded-xl border bg-background shadow-xl">
          <DialogHeader className="flex flex-col items-start gap-2 text-left">
            <DialogTitle>{detailRule?.name}</DialogTitle>
            <DialogDescription className="whitespace-pre-line text-sm">
              规则编码：{detailRule?.code || detailRule?.id}
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-1 gap-6 px-6 pb-6 lg:grid-cols-2">
            <section className="space-y-4">
              <h3 className="text-sm font-semibold text-muted-foreground">基础信息</h3>
              <div className="space-y-2 text-sm">
                <InfoRow label="责任人" value={detailRule?.owner || "-"} />
                <InfoRow
                  label="严重程度"
                  value={detailRule?.severity ? SEVERITY_LABELS[detailRule.severity] : "-"}
                />
                <InfoRow label="调度频率" value={detailRule?.frequencyLabel || "-"} />
                <InfoRow
                  label="绑定数据集"
                  value={detailRule?.datasetNames.length ? detailRule.datasetNames.join(", ") : "未绑定"}
                />
                <InfoRow
                  label="最近执行"
                  value={
                    detailRule?.lastRunStatus
                      ? `${detailRule.lastRunStatus} · ${formatTime(detailRule.lastRunFinishedAt)}`
                      : "暂无执行记录"
                  }
                />
                <InfoRow
                  label="成功率"
                  value={
                    detailRule?.runSuccessRate !== null && detailRule?.runSuccessRate !== undefined
                      ? `${(detailRule.runSuccessRate * 100).toFixed(0)}%（共 ${detailRule?.totalRuns ?? "-"} 次）`
                      : "暂未统计"
                  }
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
          <DialogFooter className="flex flex-row items-center justify-between border-t bg-muted/30 px-6 py-4">
            <div className="text-xs text-muted-foreground">
              创建：{formatTime(detailRule?.createdAt)}
              {" "}· 更新：{formatTime(detailRule?.updatedAt)}
            </div>
            <DialogClose asChild>
              <Button variant="outline">关闭</Button>
            </DialogClose>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
              <Select
                value={form.datasetId}
                onValueChange={(value) =>
                  setForm((prev) => ({
                    ...prev,
                    datasetId: value,
                    dataLevel: inferDataLevel(value),
                  }))
                }
              >
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
              <Label>快速模板</Label>
              <div className="flex flex-wrap gap-2">
                {SQL_TEMPLATES.map((template) => (
                  <Button
                    key={template.id}
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => handleApplyTemplate(template.id)}
                  >
                    {template.label}
                  </Button>
                ))}
              </div>
              <p className="text-xs text-muted-foreground">
                根据数据集快速套用常见检测逻辑，SQL 内容由系统生成，仅供查看。
              </p>
            </div>
            <div className="md:col-span-2 space-y-2">
              <Label>质量检测 SQL</Label>
              <Textarea
                placeholder="质量检测 SQL 由系统自动生成"
                value={form.sql}
                readOnly
                aria-readonly="true"
                rows={8}
                className="cursor-not-allowed resize-none bg-muted/40 text-muted-foreground"
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
