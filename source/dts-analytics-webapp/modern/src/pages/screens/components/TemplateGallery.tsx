import { useEffect, useMemo, useRef, useState } from 'react';
import { analyticsApi, type ScreenIndustryPackAuditRow, type ScreenTemplateItem } from '../../../api/analyticsApi';
import { StructuredActionDialog, type StructuredActionField } from './StructuredActionDialog';
import { parseRuntimeTargetsInput } from './TemplateGallery.helpers';
import {
    screenTemplates,
    TEMPLATE_CATEGORY_LABELS,
    TEMPLATE_CATEGORY_ORDER,
    type ScreenTemplate,
} from '../screenTemplates';
import { SCREEN_SCHEMA_VERSION } from '../specV2';
import '../ScreenDesigner.css';

export type TemplateSelection =
    | { kind: 'builtin'; template: ScreenTemplate }
    | { kind: 'asset'; template: ScreenTemplateItem };

interface TemplateGalleryProps {
    onSelect: (selection: TemplateSelection) => void;
    onClose: () => void;
}

const CATEGORY_LABELS: Record<string, string> = {
    business: '商务',
    tech: '科技',
    dashboard: '仪表盘',
    monitor: '监控',
    official: '官方',
    industry: '行业',
    ...TEMPLATE_CATEGORY_LABELS,
};

const VISIBILITY_LABELS: Record<string, string> = {
    personal: '个人',
    team: '团队',
    global: '全局',
};

const INDUSTRY_AUDIT_RESULT_LABELS: Record<string, string> = {
    success: '成功',
    partial: '部分成功',
    failed: '失败',
    rejected: '拒绝',
};

function templateText(template: {
    name?: string;
    description?: string | null;
    category?: string;
    tags?: string[];
}) {
    return [template.name || '', template.description || '', template.category || '', ...(template.tags || [])]
        .join(' ')
        .toLowerCase();
}

function normalizeTemplateCategory(value?: string | null): string {
    const text = String(value || '').trim().toLowerCase();
    return text || 'custom';
}

function asTemplatePackage(selection: TemplateSelection): Record<string, unknown> {
    if (selection.kind === 'builtin') {
        return {
            packageType: 'screen-template',
            exportedAt: new Date().toISOString(),
            template: {
                name: selection.template.name,
                description: selection.template.description,
                category: selection.template.category,
                thumbnail: selection.template.thumbnail,
                config: selection.template.config,
            },
        };
    }

    const template = selection.template;
    return {
        packageType: 'screen-template',
        exportedAt: new Date().toISOString(),
        template: {
            name: template.name,
            description: template.description,
            category: template.category,
            thumbnail: template.thumbnail,
            tags: template.tags,
            visibilityScope: template.visibilityScope,
            listed: template.listed !== false,
            themePack: template.themePack,
            config: {
                schemaVersion: template.schemaVersion ?? SCREEN_SCHEMA_VERSION,
                width: template.width,
                height: template.height,
                backgroundColor: template.backgroundColor,
                backgroundImage: template.backgroundImage,
                theme: template.theme,
                themePack: template.themePack,
                components: template.components || [],
                globalVariables: template.globalVariables || [],
            },
        },
    };
}

function formatAuditTime(value?: string): string {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    });
}

function summarizeAuditDetails(details: unknown): string {
    if (details == null) return '-';
    if (typeof details === 'string') return details;
    try {
        const text = JSON.stringify(details);
        if (!text) return '-';
        return text.length > 120 ? `${text.slice(0, 120)}...` : text;
    } catch {
        return '-';
    }
}

type TemplateGalleryNotice = {
    tone: 'success' | 'error';
    title: string;
    message: string;
};

type TemplateGalleryDialogKind =
    | 'industry-export'
    | 'connector-plan'
    | 'ops-health'
    | 'runtime-probe'
    | 'connector-probe'
    | 'restore-version';

type TemplateGalleryDialogState = {
    kind: TemplateGalleryDialogKind;
    title: string;
    description?: string;
    submitText: string;
    fields: StructuredActionField[];
};

function downloadJson(filename: string, payload: unknown): void {
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
}

function fieldValue(dialog: TemplateGalleryDialogState | null, key: string): string {
    return dialog?.fields.find((field) => field.key === key)?.value || '';
}

export function TemplateGallery({ onSelect, onClose }: TemplateGalleryProps) {
    const [selectedKey, setSelectedKey] = useState<string>('builtin:blank');
    const [keyword, setKeyword] = useState('');
    const [category, setCategory] = useState('all');
    const [visibility, setVisibility] = useState('all');
    const [listing, setListing] = useState<'all' | 'listed' | 'unlisted'>('all');
    const [assetTemplates, setAssetTemplates] = useState<ScreenTemplateItem[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isUpdatingAsset, setIsUpdatingAsset] = useState(false);
    const [importMode, setImportMode] = useState<'template' | 'industry'>('template');
    const fileInputRef = useRef<HTMLInputElement | null>(null);
    const [showIndustryAudit, setShowIndustryAudit] = useState(false);
    const [industryAuditRows, setIndustryAuditRows] = useState<ScreenIndustryPackAuditRow[]>([]);
    const [industryAuditLoading, setIndustryAuditLoading] = useState(false);
    const [industryAuditError, setIndustryAuditError] = useState<string | null>(null);
    const [industryAuditAction, setIndustryAuditAction] = useState<'all' | 'pack.export' | 'pack.import'>('all');
    const [industryAuditResult, setIndustryAuditResult] = useState<'all' | 'success' | 'partial' | 'failed' | 'rejected'>('all');
    const [actionNotice, setActionNotice] = useState<TemplateGalleryNotice | null>(null);
    const [dialogState, setDialogState] = useState<TemplateGalleryDialogState | null>(null);
    const [dialogLoading, setDialogLoading] = useState(false);
    const [dialogError, setDialogError] = useState<string | null>(null);

    const loadAssetTemplates = () => {
        setLoading(true);
        setError(null);
        analyticsApi.listScreenTemplates()
            .then((data) => {
                setAssetTemplates(Array.isArray(data) ? data : []);
            })
            .catch((err) => {
                console.error('Failed to load screen templates:', err);
                setError('模板资产加载失败');
            })
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        loadAssetTemplates();
    }, []);

    const loadIndustryAudit = async () => {
        setIndustryAuditLoading(true);
        setIndustryAuditError(null);
        try {
            const rows = await analyticsApi.listScreenIndustryPackAudit(120);
            setIndustryAuditRows(Array.isArray(rows) ? rows : []);
        } catch (err) {
            console.error('Failed to load industry pack audit:', err);
            const message = err instanceof Error ? err.message : '行业包审计加载失败';
            if (message.toLowerCase().includes('403') || message.toLowerCase().includes('forbidden')) {
                setIndustryAuditError('仅超级管理员可查看行业包审计');
            } else {
                setIndustryAuditError('行业包审计加载失败');
            }
        } finally {
            setIndustryAuditLoading(false);
        }
    };

    const normalizedKeyword = keyword.trim().toLowerCase();

    const filteredBuiltins = useMemo(() => {
        return screenTemplates.filter((template) => {
            if (category !== 'all' && template.category !== category) {
                return false;
            }
            if (!normalizedKeyword) {
                return true;
            }
            return templateText({
                name: template.name,
                description: template.description,
                category: template.category,
                tags: template.tags,
            }).includes(normalizedKeyword);
        });
    }, [category, normalizedKeyword]);

    const filteredAssets = useMemo(() => {
        return assetTemplates.filter((template) => {
            const templateCategory = normalizeTemplateCategory(template.category);
            if (category !== 'all' && templateCategory !== category) {
                return false;
            }
            const templateVisibility = String(template.visibilityScope || 'team').toLowerCase();
            if (visibility !== 'all' && templateVisibility !== visibility) {
                return false;
            }
            const isListed = template.listed !== false;
            if (listing === 'listed' && !isListed) {
                return false;
            }
            if (listing === 'unlisted' && isListed) {
                return false;
            }
            if (!normalizedKeyword) {
                return true;
            }
            return templateText({
                name: template.name,
                description: template.description,
                category: templateCategory,
                tags: template.tags,
            }).includes(normalizedKeyword);
        });
    }, [assetTemplates, category, listing, normalizedKeyword, visibility]);

    const filteredIndustryAuditRows = useMemo(() => {
        return industryAuditRows.filter((item) => {
            if (industryAuditAction !== 'all' && item.action !== industryAuditAction) {
                return false;
            }
            if (industryAuditResult !== 'all' && item.result !== industryAuditResult) {
                return false;
            }
            return true;
        });
    }, [industryAuditAction, industryAuditResult, industryAuditRows]);

    const allCategories = useMemo(() => {
        const values = new Set<string>();
        for (const t of screenTemplates) {
            values.add(t.category);
        }
        for (const t of assetTemplates) {
            values.add(normalizeTemplateCategory(t.category));
        }
        const ordered = TEMPLATE_CATEGORY_ORDER.filter((value) => values.has(value));
        const extras = Array.from(values).filter((value) => !TEMPLATE_CATEGORY_ORDER.includes(value as typeof TEMPLATE_CATEGORY_ORDER[number]));
        return ['all', ...ordered, ...extras];
    }, [assetTemplates]);

    const selectedBuiltin = selectedKey.startsWith('builtin:')
        ? screenTemplates.find((template) => template.id === selectedKey.slice('builtin:'.length))
        : undefined;
    const selectedAsset = selectedKey.startsWith('asset:')
        ? assetTemplates.find((template) => String(template.id) === selectedKey.slice('asset:'.length))
        : undefined;

    const selectedSelection: TemplateSelection | null = selectedBuiltin
        ? { kind: 'builtin', template: selectedBuiltin }
        : selectedAsset
            ? { kind: 'asset', template: selectedAsset }
            : null;

    const handleConfirm = () => {
        if (!selectedSelection) {
            return;
        }
        onSelect(selectedSelection);
    };

    const handleImportClick = (mode: 'template' | 'industry') => {
        setImportMode(mode);
        fileInputRef.current?.click();
    };

    const handleImport = async (event: React.ChangeEvent<HTMLInputElement>) => {
        const file = event.target.files?.[0];
        event.target.value = '';
        if (!file) return;

        try {
            const text = await file.text();
            const payload = JSON.parse(text) as Record<string, unknown>;

            if (importMode === 'industry') {
                const validation = await analyticsApi.validateScreenIndustryPack(payload);
                if (Array.isArray(validation.errors) && validation.errors.length > 0) {
                    setActionNotice({
                        tone: 'error',
                        title: '行业包校验失败',
                        message: validation.errors.join('；'),
                    });
                    return;
                }
                if (Array.isArray(validation.warnings) && validation.warnings.length > 0) {
                    setActionNotice({
                        tone: 'error',
                        title: '行业包存在告警',
                        message: `${validation.warnings.join('\n')}\n\n请确认内容后重新导入。`,
                    });
                    return;
                }
                const result = await analyticsApi.importScreenIndustryPack(payload);
                await loadAssetTemplates();
                setActionNotice({
                    tone: 'success',
                    title: '行业包导入完成',
                    message: `成功 ${result.imported || 0}，失败 ${result.failed || 0}`,
                });
                return;
            }

            const rawTemplate = (payload.template || payload) as Record<string, unknown>;
            const rawConfig = (rawTemplate.config || payload.config || payload) as Record<string, unknown>;

            const body = {
                name: typeof rawTemplate.name === 'string' ? rawTemplate.name : file.name.replace(/\.[^.]+$/, ''),
                description: typeof rawTemplate.description === 'string' ? rawTemplate.description : '',
                category: typeof rawTemplate.category === 'string' ? rawTemplate.category : 'custom',
                thumbnail: typeof rawTemplate.thumbnail === 'string' ? rawTemplate.thumbnail : '🧩',
                tags: Array.isArray(rawTemplate.tags) ? rawTemplate.tags : [],
                visibilityScope: typeof rawTemplate.visibilityScope === 'string' ? rawTemplate.visibilityScope : 'team',
                listed: typeof rawTemplate.listed === 'boolean' ? rawTemplate.listed : true,
                themePack: rawTemplate.themePack && typeof rawTemplate.themePack === 'object' ? rawTemplate.themePack : undefined,
                config: {
                    schemaVersion: typeof rawConfig.schemaVersion === 'number' ? rawConfig.schemaVersion : SCREEN_SCHEMA_VERSION,
                    width: typeof rawConfig.width === 'number' ? rawConfig.width : 1920,
                    height: typeof rawConfig.height === 'number' ? rawConfig.height : 1080,
                    backgroundColor: typeof rawConfig.backgroundColor === 'string' ? rawConfig.backgroundColor : '#0d1b2a',
                    backgroundImage: typeof rawConfig.backgroundImage === 'string' ? rawConfig.backgroundImage : null,
                    theme: typeof rawConfig.theme === 'string' ? rawConfig.theme : null,
                    themePack: rawConfig.themePack && typeof rawConfig.themePack === 'object' ? rawConfig.themePack : undefined,
                    components: Array.isArray(rawConfig.components) ? rawConfig.components : [],
                    globalVariables: Array.isArray(rawConfig.globalVariables) ? rawConfig.globalVariables : [],
                },
            };

            const created = await analyticsApi.createScreenTemplate(body);
            await loadAssetTemplates();
            setSelectedKey(`asset:${String(created.id)}`);
            setActionNotice({
                tone: 'success',
                title: '模板包导入成功',
                message: `模板已导入到资产中心，资产 ID=${String(created.id)}`,
            });
        } catch (err) {
            console.error('Failed to import package:', err);
            setActionNotice({
                tone: 'error',
                title: importMode === 'industry' ? '行业包导入失败' : '模板包导入失败',
                message: importMode === 'industry' ? '请检查 JSON 结构和行业包内容' : '请检查 JSON 结构和模板内容',
            });
        }
    };

    const handleExportTemplate = () => {
        if (!selectedSelection) {
            setActionNotice({
                tone: 'error',
                title: '请先选择模板',
                message: '需要先选中一个内置模板或资产模板，才能执行导出。',
            });
            return;
        }
        const pack = asTemplatePackage(selectedSelection);
        const name = selectedSelection.kind === 'builtin'
            ? selectedSelection.template.name
            : (selectedSelection.template.name || `template-${String(selectedSelection.template.id)}`);
        downloadJson(`${name || 'screen-template'}.json`, pack);
        setActionNotice({
            tone: 'success',
            title: '模板包已导出',
            message: `${name || 'screen-template'}.json 已开始下载。`,
        });
    };

    const updateDialogField = (key: string, value: string) => {
        setDialogState((current) => current ? {
            ...current,
            fields: current.fields.map((field) => field.key === key ? { ...field, value } : field),
        } : current);
    };

    const closeDialog = () => {
        if (dialogLoading) return;
        setDialogState(null);
        setDialogError(null);
    };

    const openIndustryExportDialog = async () => {
        const presets = await analyticsApi.getScreenIndustryPackPresets().catch(() => null);
        const defaultIndustry = String((presets?.industries?.[0] as Record<string, unknown> | undefined)?.id || 'discrete-manufacturing');
        const defaultHardware = String((presets?.hardwareProfiles?.[0] as Record<string, unknown> | undefined)?.id || 'edge-box-standard');
        const deploymentOptions = Array.isArray(presets?.deploymentModes) && presets.deploymentModes.length > 0
            ? presets.deploymentModes.map((item) => ({ label: String(item), value: String(item) }))
            : [
                { label: 'online', value: 'online' },
                { label: 'offline', value: 'offline' },
                { label: 'isolated', value: 'isolated' },
            ];
        setDialogError(null);
        setDialogState({
            kind: 'industry-export',
            title: '导出行业包',
            description: '为当前模板生成可交付的行业包，包含部署模式与连接器预置。',
            submitText: '导出 JSON',
            fields: [
                { key: 'industry', label: '行业标识', value: defaultIndustry, placeholder: '例如 discrete-manufacturing' },
                { key: 'hardwareProfile', label: '硬件预置', value: defaultHardware, placeholder: '例如 edge-box-standard' },
                { key: 'deploymentMode', label: '部署模式', kind: 'select', value: 'online', options: deploymentOptions },
                { key: 'connectors', label: '连接器类型', value: 'plc,mqtt,opcua', placeholder: '逗号分隔，如 plc,mqtt,opcua' },
            ],
        });
    };

    const handleOpenIndustryAudit = async () => {
        setShowIndustryAudit(true);
        if (industryAuditRows.length > 0 || industryAuditLoading) {
            return;
        }
        await loadIndustryAudit();
    };

    const openConnectorPlanDialog = async () => {
        const presets = await analyticsApi.getScreenIndustryPackPresets().catch(() => null);
        const allTemplates = Array.isArray(presets?.connectorTemplates)
            ? (presets?.connectorTemplates as Array<Record<string, unknown>>)
            : [];
        const defaultIds = allTemplates
            .map((item) => String(item.id || '').trim())
            .filter((id) => id.length > 0)
            .join(',');
        setDialogError(null);
        setDialogState({
            kind: 'connector-plan',
            title: '生成采集任务草案',
            description: '按连接器模板生成可导出的采集任务计划。',
            submitText: '生成并导出',
            fields: [
                { key: 'connectorIds', label: '连接器 ID', value: defaultIds || 'plc,mqtt,opcua,postgresql', placeholder: '逗号分隔' },
            ],
        });
    };

    const openOpsHealthDialog = () => {
        setDialogError(null);
        setDialogState({
            kind: 'ops-health',
            title: '运行运维巡检',
            description: '按部署模式生成模板资产的运维健康报告。',
            submitText: '开始巡检',
            fields: [
                {
                    key: 'deploymentMode',
                    label: '部署模式',
                    kind: 'select',
                    value: 'online',
                    options: [
                        { label: 'online', value: 'online' },
                        { label: 'offline', value: 'offline' },
                        { label: 'isolated', value: 'isolated' },
                    ],
                },
            ],
        });
    };

    const openRuntimeProbeDialog = () => {
        setDialogError(null);
        setDialogState({
            kind: 'runtime-probe',
            title: '运行时探测',
            description: '检测模板交付依赖的运行时端点，支持按行输入自定义探测目标。',
            submitText: '开始探测',
            fields: [
                { key: 'timeoutMs', label: '超时毫秒', value: '1500', placeholder: '300-5000' },
                {
                    key: 'customTargets',
                    label: '自定义目标',
                    kind: 'textarea',
                    value: '',
                    helpText: '每行一个，格式：id,protocol,host,port,path,required,expectedStatus,expectedBodyContains',
                    placeholder: 'analytics,http,127.0.0.1,3000,/analytics,true,200-499,metabase',
                },
            ],
        });
    };

    const openConnectorProbeDialog = async () => {
        const presets = await analyticsApi.getScreenIndustryPackPresets().catch(() => null);
        const allTemplates = Array.isArray(presets?.connectorTemplates)
            ? (presets?.connectorTemplates as Array<Record<string, unknown>>)
            : [];
        const defaultIds = allTemplates
            .map((item) => String(item.id || '').trim())
            .filter((id) => id.length > 0)
            .join(',');
        setDialogError(null);
        setDialogState({
            kind: 'connector-probe',
            title: '连接器探测',
            description: '按连接器模板执行连接性探测，并输出结构化诊断结果。',
            submitText: '开始探测',
            fields: [
                { key: 'connectorIds', label: '连接器 ID', value: defaultIds || 'plc,mqtt,opcua,postgresql', placeholder: '逗号分隔' },
                { key: 'timeoutMs', label: '超时毫秒', value: '1500', placeholder: '300-5000' },
            ],
        });
    };

    const handleToggleListing = async () => {
        if (!selectedAsset || isUpdatingAsset) {
            return;
        }
        const nextListed = selectedAsset.listed === false;
        setIsUpdatingAsset(true);
        try {
            const updated = await analyticsApi.updateScreenTemplateListing(selectedAsset.id, nextListed);
            setAssetTemplates((prev) => prev.map((item) => String(item.id) === String(updated.id) ? updated : item));
            setSelectedKey(`asset:${String(updated.id)}`);
            setActionNotice({
                tone: 'success',
                title: nextListed ? '模板已上架' : '模板已下架',
                message: `${updated.name || `模板 #${updated.id}`} 的市场状态已更新。`,
            });
        } catch (err) {
            console.error('Failed to update template listing:', err);
            setActionNotice({
                tone: 'error',
                title: '模板上下架失败',
                message: err instanceof Error ? err.message : '更新模板状态失败',
            });
        } finally {
            setIsUpdatingAsset(false);
        }
    };

    const openRestoreTemplateVersionDialog = async () => {
        if (!selectedAsset || isUpdatingAsset) {
            return;
        }
        setIsUpdatingAsset(true);
        try {
            const versions = await analyticsApi.listScreenTemplateVersions(selectedAsset.id, 30);
            if (!Array.isArray(versions) || versions.length === 0) {
                setActionNotice({
                    tone: 'error',
                    title: '暂无可恢复版本',
                    message: '当前模板还没有可恢复的历史版本。',
                });
                return;
            }
            setDialogError(null);
            setDialogState({
                kind: 'restore-version',
                title: '恢复模板版本',
                description: '选择一个历史版本，恢复到当前模板草稿。',
                submitText: '恢复版本',
                fields: [
                    {
                        key: 'versionNo',
                        label: '历史版本',
                        kind: 'select',
                        value: String(versions[0]?.versionNo || ''),
                        options: versions.map((version) => ({
                            label: `v${version.versionNo ?? '-'} · ${version.action || '-'} · ${version.createdAt || '-'}`,
                            value: String(version.versionNo ?? ''),
                        })),
                    },
                ],
            });
        } catch (err) {
            console.error('Failed to restore template version:', err);
            setActionNotice({
                tone: 'error',
                title: '模板版本读取失败',
                message: err instanceof Error ? err.message : '读取模板版本失败',
            });
        } finally {
            setIsUpdatingAsset(false);
        }
    };

    const handleSubmitDialog = async () => {
        if (!dialogState) {
            return;
        }
        setDialogLoading(true);
        setDialogError(null);
        try {
            if (dialogState.kind === 'industry-export') {
                const connectors = fieldValue(dialogState, 'connectors')
                    .split(',')
                    .map((item) => item.trim())
                    .filter(Boolean);
                const body = selectedSelection && selectedSelection.kind === 'asset'
                    ? {
                        templateIds: [selectedSelection.template.id],
                        industry: fieldValue(dialogState, 'industry'),
                        hardwareProfile: fieldValue(dialogState, 'hardwareProfile'),
                        deploymentMode: fieldValue(dialogState, 'deploymentMode'),
                        connectorTypes: connectors.length > 0 ? connectors : undefined,
                    }
                    : {
                        industry: fieldValue(dialogState, 'industry'),
                        hardwareProfile: fieldValue(dialogState, 'hardwareProfile'),
                        deploymentMode: fieldValue(dialogState, 'deploymentMode'),
                        connectorTypes: connectors.length > 0 ? connectors : undefined,
                    };
                const pack = await analyticsApi.exportScreenIndustryPack(body);
                downloadJson('dts-industry-pack.json', pack);
                setActionNotice({
                    tone: 'success',
                    title: '行业包已导出',
                    message: 'dts-industry-pack.json 已开始下载。',
                });
                setDialogState(null);
                return;
            }

            if (dialogState.kind === 'connector-plan') {
                const presets = await analyticsApi.getScreenIndustryPackPresets().catch(() => null);
                const allTemplates = Array.isArray(presets?.connectorTemplates)
                    ? (presets?.connectorTemplates as Array<Record<string, unknown>>)
                    : [];
                const requestedIds = fieldValue(dialogState, 'connectorIds')
                    .split(',')
                    .map((item) => item.trim().toLowerCase())
                    .filter(Boolean);
                const selectedTemplates = requestedIds.length > 0
                    ? allTemplates.filter((item) => requestedIds.includes(String(item.id || '').trim().toLowerCase()))
                    : allTemplates;
                const plan = await analyticsApi.generateScreenIndustryConnectorPlan({
                    source: 'template-gallery',
                    connectorTemplates: selectedTemplates,
                });
                downloadJson('dts-connector-plan.json', plan);
                setActionNotice({
                    tone: 'success',
                    title: '采集任务草案已生成',
                    message: `任务数：${plan.jobCount ?? 0}`,
                });
                setDialogState(null);
                return;
            }

            if (dialogState.kind === 'ops-health') {
                const deploymentMode = fieldValue(dialogState, 'deploymentMode') || 'online';
                const report = await analyticsApi.getScreenIndustryOpsHealth(deploymentMode, true);
                const summary = report.summary || {};
                const checks = Array.isArray(report.checks) ? report.checks : [];
                const lines = checks.map((item) => {
                    const status = String(item.status || '-');
                    const name = String(item.name || '-');
                    const message = String(item.message || '');
                    return `[${status}] ${name}${message ? `: ${message}` : ''}`;
                });
                setActionNotice({
                    tone: 'success',
                    title: `运维巡检完成 (${deploymentMode})`,
                    message: `评分: ${String(summary.score ?? '-')}\n模板数: ${String(summary.templateCount ?? '-')}\n失败审计: ${String(summary.failedAudits ?? '-')}\n\n${lines.join('\n')}`,
                });
                setDialogState(null);
                return;
            }

            if (dialogState.kind === 'runtime-probe') {
                const timeoutMs = Number(fieldValue(dialogState, 'timeoutMs'));
                const customTargets = parseRuntimeTargetsInput(fieldValue(dialogState, 'customTargets'));
                const report = await analyticsApi.probeScreenIndustryRuntime({
                    source: 'template-gallery',
                    timeoutMs: Number.isFinite(timeoutMs) ? timeoutMs : 1500,
                    targets: customTargets.length > 0 ? customTargets : undefined,
                });
                const summary = report.summary || {};
                const rows = Array.isArray(report.rows) ? report.rows : [];
                const lines = rows.map((item) => {
                    const status = String(item.status || '-');
                    const name = String(item.name || item.id || '-');
                    const protocol = String(item.protocol || 'tcp');
                    const host = String(item.host || '-');
                    const port = String(item.port || '-');
                    const message = String(item.message || '');
                    return `[${status}] ${name} [${protocol}] (${host}:${port})${message ? `: ${message}` : ''}`;
                });
                setActionNotice({
                    tone: 'success',
                    title: '运行时探测完成',
                    message: `总计 ${String(summary.total ?? '-')}, 通过 ${String(summary.pass ?? '-')}, 告警 ${String(summary.warn ?? '-')}, 失败 ${String(summary.fail ?? '-')}\n超时: ${String(summary.timeoutMs ?? '-')} ms\n\n${lines.join('\n')}`,
                });
                setDialogState(null);
                return;
            }

            if (dialogState.kind === 'connector-probe') {
                const presets = await analyticsApi.getScreenIndustryPackPresets().catch(() => null);
                const allTemplates = Array.isArray(presets?.connectorTemplates)
                    ? (presets?.connectorTemplates as Array<Record<string, unknown>>)
                    : [];
                const requestedIds = fieldValue(dialogState, 'connectorIds')
                    .split(',')
                    .map((item) => item.trim().toLowerCase())
                    .filter(Boolean);
                const selectedTemplates = requestedIds.length > 0
                    ? allTemplates.filter((item) => requestedIds.includes(String(item.id || '').trim().toLowerCase()))
                    : allTemplates;
                const timeoutMs = Number(fieldValue(dialogState, 'timeoutMs'));
                const report = await analyticsApi.probeScreenIndustryConnectors({
                    source: 'template-gallery',
                    timeoutMs: Number.isFinite(timeoutMs) ? timeoutMs : 1500,
                    connectorTemplates: selectedTemplates,
                });
                const summary = report.summary || {};
                const rows = Array.isArray(report.rows) ? report.rows : [];
                const lines = rows.map((item) => {
                    const connectorId = String(item.connectorId || '-');
                    const status = String(item.status || '-');
                    const message = String(item.message || '');
                    return `[${status}] ${connectorId}${message ? `: ${message}` : ''}`;
                });
                setActionNotice({
                    tone: 'success',
                    title: '连接器探测完成',
                    message: `总计 ${String(summary.total ?? '-')}, 通过 ${String(summary.pass ?? '-')}, 告警 ${String(summary.warn ?? '-')}, 失败 ${String(summary.fail ?? '-')}\n超时: ${String(summary.timeoutMs ?? '-')} ms\n\n${lines.join('\n')}`,
                });
                setDialogState(null);
                return;
            }

            if (dialogState.kind === 'restore-version') {
                if (!selectedAsset) {
                    throw new Error('当前没有选中的资产模板');
                }
                const versionNo = Number(fieldValue(dialogState, 'versionNo'));
                if (!Number.isFinite(versionNo) || versionNo <= 0) {
                    setDialogError('版本号格式不正确');
                    return;
                }
                const updated = await analyticsApi.restoreScreenTemplateVersion(selectedAsset.id, versionNo);
                setAssetTemplates((prev) => prev.map((item) => String(item.id) === String(updated.id) ? updated : item));
                setSelectedKey(`asset:${String(updated.id)}`);
                setActionNotice({
                    tone: 'success',
                    title: '模板恢复成功',
                    message: `${updated.name || `模板 #${updated.id}`} 已恢复到 v${versionNo}`,
                });
                setDialogState(null);
            }
        } catch (err) {
            console.error('Template gallery action failed:', err);
            setDialogError(err instanceof Error ? err.message : '执行失败，请稍后重试');
        } finally {
            setDialogLoading(false);
        }
    };

    return (
        <div className="template-gallery-overlay" data-testid="analytics-screen-template-gallery" onClick={onClose}>
            <div className="template-gallery-modal" onClick={(e) => e.stopPropagation()}>
                <div className="template-gallery-header">
                    <h2>📋 模板市场</h2>
                    <button className="template-gallery-close" onClick={onClose}>✕</button>
                </div>

                {actionNotice ? (
                    <div style={{
                        margin: '14px 24px 0',
                        padding: '12px 14px',
                        borderRadius: 12,
                        border: actionNotice.tone === 'success'
                            ? '1px solid rgba(16,185,129,0.22)'
                            : '1px solid rgba(239,68,68,0.22)',
                        background: actionNotice.tone === 'success'
                            ? 'rgba(16,185,129,0.12)'
                            : 'rgba(239,68,68,0.10)',
                        color: actionNotice.tone === 'success' ? '#bbf7d0' : '#fecaca',
                    }}>
                        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
                            <strong>{actionNotice.title}</strong>
                            <button className="template-btn secondary" onClick={() => setActionNotice(null)}>关闭</button>
                        </div>
                        <div style={{ marginTop: 6, fontSize: 12, whiteSpace: 'pre-wrap', lineHeight: 1.6 }}>
                            {actionNotice.message}
                        </div>
                    </div>
                ) : null}

                <div className="template-category-tabs">
                    {allCategories.map((value) => (
                        <button
                            key={value}
                            data-testid={`analytics-screen-template-category-${value}`}
                            className={`template-category-tab ${category === value ? 'active' : ''}`}
                            onClick={() => setCategory(value)}
                        >
                            {value === 'all' ? '全部' : (CATEGORY_LABELS[value] || value)}
                        </button>
                    ))}
                </div>

                <div className="template-gallery-filters">
                    <input
                        type="text"
                        className="template-search-input"
                        value={keyword}
                        onChange={(e) => setKeyword(e.target.value)}
                        placeholder="搜索模板名称、说明、分类或标签"
                    />
                    <select
                        className="template-category-select"
                        value={visibility}
                        onChange={(e) => setVisibility(e.target.value)}
                    >
                        <option value="all">全部可见范围</option>
                        <option value="personal">个人</option>
                        <option value="team">团队</option>
                        <option value="global">全局</option>
                    </select>
                    <select
                        className="template-category-select"
                        value={listing}
                        onChange={(e) => setListing(e.target.value as 'all' | 'listed' | 'unlisted')}
                    >
                        <option value="all">全部状态</option>
                        <option value="listed">仅上架</option>
                        <option value="unlisted">仅下架</option>
                    </select>
                    <button className="template-btn secondary" onClick={loadAssetTemplates}>刷新资产</button>
                    <button className="template-btn secondary" onClick={() => handleImportClick('template')}>导入模板包</button>
                    <button className="template-btn secondary" onClick={handleExportTemplate}>导出模板包</button>
                    <button className="template-btn secondary" onClick={() => handleImportClick('industry')}>导入行业包</button>
                    <button className="template-btn secondary" onClick={() => void openIndustryExportDialog()}>导出行业包</button>
                    <button className="template-btn secondary" onClick={handleOpenIndustryAudit}>行业包审计</button>
                    <button className="template-btn secondary" onClick={() => void openConnectorPlanDialog()}>采集任务草案</button>
                    <button className="template-btn secondary" onClick={() => void openConnectorProbeDialog()}>连接器探测</button>
                    <button className="template-btn secondary" onClick={openOpsHealthDialog}>运维巡检</button>
                    <button className="template-btn secondary" onClick={openRuntimeProbeDialog}>运行时探测</button>
                    <input
                        ref={fileInputRef}
                        type="file"
                        accept="application/json"
                        style={{ display: 'none' }}
                        onChange={handleImport}
                    />
                </div>

                <div className="template-gallery-content">
                    <div className="template-section-title">内置模板</div>
                    <div className="template-gallery-grid">
                        {filteredBuiltins.map((template) => {
                            const key = `builtin:${template.id}`;
                            return (
                                <div
                                    key={key}
                                    data-testid={`analytics-screen-template-builtin-${template.id}`}
                                    className={`template-card ${selectedKey === key ? 'selected' : ''}`}
                                    onClick={() => setSelectedKey(key)}
                                >
                                    <div
                                        className="template-card-preview"
                                        style={{
                                            background: template.config.backgroundColor
                                                ? `linear-gradient(135deg, ${template.config.backgroundColor} 0%, ${template.config.backgroundColor} 100%)`
                                                : undefined,
                                        }}
                                    >
                                        <span className="template-card-icon">{template.thumbnail}</span>
                                        <div className="template-card-badge">内置</div>
                                    </div>
                                    <div className="template-card-info">
                                        <h3>{template.name}</h3>
                                        <p>{template.description}</p>
                                        {Array.isArray(template.tags) && template.tags.length > 0 && (
                                            <div className="template-card-tags">
                                                {template.tags.map((tag) => (
                                                    <span key={tag} className="template-tag">{tag}</span>
                                                ))}
                                            </div>
                                        )}
                                        <div className="template-card-meta">
                                            {(CATEGORY_LABELS[template.category] || template.category)}
                                            {` · ${template.config.width}×${template.config.height}`}
                                            {` · ${template.config.components.length} 个组件`}
                                            {Array.isArray(template.recommendedVariables) && template.recommendedVariables.length > 0
                                                ? ` · ${template.recommendedVariables.length} 个预置变量`
                                                : ''}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div className="template-section-title" style={{ marginTop: 20 }}>资产模板</div>
                    {loading ? (
                        <div className="template-empty-hint">加载中...</div>
                    ) : error ? (
                        <div className="template-empty-hint">{error}</div>
                    ) : filteredAssets.length === 0 ? (
                        <div className="template-empty-hint">暂无资产模板</div>
                    ) : (
                        <div className="template-gallery-grid">
                            {filteredAssets.map((template) => {
                                const key = `asset:${template.id}`;
                                return (
                                    <div
                                        key={key}
                                        data-testid={`analytics-screen-template-asset-${template.id}`}
                                        className={`template-card ${selectedKey === key ? 'selected' : ''}`}
                                        onClick={() => setSelectedKey(key)}
                                    >
                                        <div
                                            className="template-card-preview"
                                            style={{
                                                background: template.backgroundColor
                                                    ? `linear-gradient(135deg, ${template.backgroundColor} 0%, ${template.backgroundColor} 100%)`
                                                    : undefined,
                                            }}
                                        >
                                            <span className="template-card-icon">{template.thumbnail || '🧩'}</span>
                                            <div className="template-card-badge">资产</div>
                                        </div>
                                        <div className="template-card-info">
                                            <h3>{template.name || `模板 #${template.id}`}</h3>
                                            <p>{template.description || '无描述'}</p>
                                            <div className="template-card-meta">
                                                {(CATEGORY_LABELS[normalizeTemplateCategory(template.category)] || normalizeTemplateCategory(template.category))}
                                                {` · ${template.listed === false ? '下架' : '上架'}`}
                                                {` · ${VISIBILITY_LABELS[String(template.visibilityScope || 'team').toLowerCase()] || String(template.visibilityScope || 'team')}`}
                                                {template.templateVersion ? ` · v${template.templateVersion}` : ''}
                                                {template.width && template.height ? ` · ${template.width}×${template.height}` : ''}
                                                {Array.isArray(template.tags) && template.tags.length > 0 ? ` · ${template.tags.join(' / ')}` : ''}
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                <div className="template-gallery-footer">
                    {selectedAsset ? (
                        <>
                            <button
                                className="template-btn secondary"
                                onClick={handleToggleListing}
                                disabled={isUpdatingAsset}
                            >
                                {selectedAsset.listed === false ? '上架模板' : '下架模板'}
                            </button>
                            <button
                                className="template-btn secondary"
                                onClick={() => void openRestoreTemplateVersionDialog()}
                                disabled={isUpdatingAsset}
                            >
                                版本恢复
                            </button>
                        </>
                    ) : null}
                    <button className="template-btn secondary" onClick={onClose}>取消</button>
                    <button className="template-btn primary" data-testid="analytics-screen-template-confirm" onClick={handleConfirm}>使用此模板</button>
                </div>
            </div>

            {showIndustryAudit && (
                <div className="industry-audit-overlay" onClick={(e) => e.stopPropagation()}>
                    <div className="industry-audit-modal">
                        <div className="industry-audit-header">
                            <h3 style={{ margin: 0 }}>行业包审计</h3>
                            <div style={{ display: 'flex', gap: 8 }}>
                                <button
                                    className="template-btn secondary"
                                    onClick={() => {
                                        void loadIndustryAudit();
                                    }}
                                    disabled={industryAuditLoading}
                                >
                                    {industryAuditLoading ? '刷新中...' : '刷新'}
                                </button>
                                <button className="template-btn secondary" onClick={() => setShowIndustryAudit(false)}>关闭</button>
                            </div>
                        </div>
                        <div className="industry-audit-filters">
                            <select
                                className="template-category-select"
                                value={industryAuditAction}
                                onChange={(e) => setIndustryAuditAction(e.target.value as typeof industryAuditAction)}
                            >
                                <option value="all">全部动作</option>
                                <option value="pack.export">导出</option>
                                <option value="pack.import">导入</option>
                            </select>
                            <select
                                className="template-category-select"
                                value={industryAuditResult}
                                onChange={(e) => setIndustryAuditResult(e.target.value as typeof industryAuditResult)}
                            >
                                <option value="all">全部结果</option>
                                <option value="success">成功</option>
                                <option value="partial">部分成功</option>
                                <option value="failed">失败</option>
                                <option value="rejected">拒绝</option>
                            </select>
                            <div style={{ fontSize: 12, color: '#94a3b8' }}>
                                共 {filteredIndustryAuditRows.length} 条
                            </div>
                        </div>
                        <div className="industry-audit-body">
                            {industryAuditError ? (
                                <div className="template-empty-hint">{industryAuditError}</div>
                            ) : industryAuditLoading && industryAuditRows.length === 0 ? (
                                <div className="template-empty-hint">加载中...</div>
                            ) : filteredIndustryAuditRows.length === 0 ? (
                                <div className="template-empty-hint">暂无审计记录</div>
                            ) : (
                                <table className="industry-audit-table">
                                    <thead>
                                        <tr>
                                            <th>时间</th>
                                            <th>动作</th>
                                            <th>结果</th>
                                            <th>操作者</th>
                                            <th>来源</th>
                                            <th>requestId</th>
                                            <th>详情</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        {filteredIndustryAuditRows.map((row) => (
                                            <tr key={String(row.id ?? `${row.requestId}-${row.createdAt}`)}>
                                                <td>{formatAuditTime(row.createdAt)}</td>
                                                <td>{row.action || '-'}</td>
                                                <td>{INDUSTRY_AUDIT_RESULT_LABELS[String(row.result || '')] || row.result || '-'}</td>
                                                <td>{row.actorId == null ? '-' : String(row.actorId)}</td>
                                                <td>{row.source || '-'}</td>
                                                <td>{row.requestId || '-'}</td>
                                                <td>{summarizeAuditDetails(row.details)}</td>
                                            </tr>
                                        ))}
                                    </tbody>
                                </table>
                            )}
                        </div>
                    </div>
                </div>
            )}

            <StructuredActionDialog
                open={dialogState !== null}
                title={dialogState?.title || ''}
                description={dialogState?.description}
                submitText={dialogState?.submitText || '确认'}
                loading={dialogLoading}
                error={dialogError}
                fields={dialogState?.fields || []}
                onClose={closeDialog}
                onChange={updateDialogField}
                onSubmit={handleSubmitDialog}
            />

            <style>{`
                .template-gallery-overlay {
                    position: fixed;
                    top: 0;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: rgba(0, 0, 0, 0.7);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 1000;
                    backdrop-filter: blur(4px);
                }

                .template-gallery-modal {
                    background: #1a1f36;
                    border-radius: 12px;
                    width: 92%;
                    max-width: 1080px;
                    max-height: 84vh;
                    display: flex;
                    flex-direction: column;
                    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
                    border: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-gallery-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    padding: 20px 24px;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-gallery-header h2 {
                    margin: 0;
                    font-size: 20px;
                    color: #fff;
                }

                .template-gallery-close {
                    background: none;
                    border: none;
                    color: #888;
                    font-size: 24px;
                    cursor: pointer;
                    padding: 4px 8px;
                    border-radius: 4px;
                    transition: all 0.2s;
                }

                .template-gallery-close:hover {
                    background: rgba(255, 255, 255, 0.1);
                    color: #fff;
                }

                .template-category-tabs {
                    display: flex;
                    gap: 2px;
                    padding: 10px 24px 0;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                    overflow-x: auto;
                }

                .template-category-tab {
                    background: none;
                    border: none;
                    border-bottom: 2px solid transparent;
                    padding: 8px 14px;
                    color: #94a3b8;
                    font-size: 13px;
                    cursor: pointer;
                    white-space: nowrap;
                    transition: all 0.2s;
                }

                .template-category-tab:hover {
                    color: #e2e8f0;
                }

                .template-category-tab.active {
                    color: #00d4ff;
                    border-bottom-color: #00d4ff;
                }

                .template-gallery-filters {
                    display: grid;
                    grid-template-columns: 1fr repeat(10, auto);
                    gap: 8px;
                    padding: 12px 24px;
                    border-bottom: 1px solid rgba(255, 255, 255, 0.08);
                }

                .template-search-input {
                    background: rgba(15, 23, 42, 0.8);
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    border-radius: 6px;
                    padding: 8px 10px;
                    color: #fff;
                    font-size: 13px;
                }

                .template-category-select {
                    background: rgba(15, 23, 42, 0.8);
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    border-radius: 6px;
                    padding: 8px 10px;
                    color: #fff;
                    font-size: 13px;
                }

                .template-gallery-content {
                    flex: 1;
                    overflow-y: auto;
                    padding: 24px;
                }

                .template-section-title {
                    font-size: 14px;
                    font-weight: 700;
                    color: #e2e8f0;
                    margin-bottom: 12px;
                }

                .template-gallery-grid {
                    display: grid;
                    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
                    gap: 20px;
                }

                .template-card {
                    position: relative;
                    background: #0d1226;
                    border: 2px solid transparent;
                    border-radius: 10px;
                    overflow: hidden;
                    cursor: pointer;
                    transition: all 0.2s ease;
                }

                .template-card:hover {
                    border-color: rgba(0, 212, 255, 0.3);
                    transform: translateY(-2px);
                }

                .template-card.selected {
                    border-color: #00d4ff;
                    box-shadow: 0 0 20px rgba(0, 212, 255, 0.3);
                }

                .template-card-preview {
                    height: 140px;
                    background: linear-gradient(135deg, #0a0e27 0%, #1a1f46 100%);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    position: relative;
                }

                .template-card-icon {
                    font-size: 48px;
                }

                .template-card-badge {
                    position: absolute;
                    top: 10px;
                    right: 10px;
                    padding: 4px 10px;
                    background: rgba(0, 212, 255, 0.2);
                    color: #00d4ff;
                    border-radius: 20px;
                    font-size: 11px;
                    font-weight: 500;
                }

                .template-card-info {
                    padding: 16px;
                }

                .template-card-info h3 {
                    margin: 0 0 8px 0;
                    font-size: 16px;
                    font-weight: 600;
                    color: #fff;
                }

                .template-card-info p {
                    margin: 0 0 10px 0;
                    font-size: 13px;
                    color: #888;
                    line-height: 1.4;
                }

                .template-card-tags {
                    display: flex;
                    flex-wrap: wrap;
                    gap: 4px;
                    margin-bottom: 8px;
                }

                .template-tag {
                    display: inline-block;
                    padding: 2px 8px;
                    background: rgba(0, 212, 255, 0.1);
                    color: #00d4ff;
                    border-radius: 10px;
                    font-size: 11px;
                }

                .template-card-meta {
                    font-size: 12px;
                    color: #666;
                }

                .template-empty-hint {
                    color: #94a3b8;
                    font-size: 13px;
                    padding: 14px 4px;
                }

                .template-gallery-footer {
                    display: flex;
                    gap: 12px;
                    justify-content: flex-end;
                    padding: 16px 24px;
                    border-top: 1px solid rgba(255, 255, 255, 0.1);
                }

                .template-btn {
                    padding: 10px 14px;
                    border-radius: 6px;
                    font-size: 13px;
                    font-weight: 500;
                    cursor: pointer;
                    transition: all 0.2s;
                }

                .template-btn.secondary {
                    background: transparent;
                    border: 1px solid rgba(255, 255, 255, 0.2);
                    color: #888;
                }

                .template-btn.secondary:hover {
                    border-color: rgba(255, 255, 255, 0.4);
                    color: #fff;
                }

                .template-btn.primary {
                    background: linear-gradient(135deg, #00d4ff 0%, #0066ff 100%);
                    border: none;
                    color: #fff;
                }

                .template-btn.primary:hover {
                    transform: translateY(-1px);
                    box-shadow: 0 4px 12px rgba(0, 212, 255, 0.4);
                }

                .industry-audit-overlay {
                    position: fixed;
                    inset: 0;
                    z-index: 1600;
                    background: rgba(2, 6, 23, 0.65);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    padding: 20px;
                }

                .industry-audit-modal {
                    width: min(1100px, 94vw);
                    max-height: 82vh;
                    display: flex;
                    flex-direction: column;
                    background: #0f172a;
                    border: 1px solid rgba(148, 163, 184, 0.28);
                    border-radius: 10px;
                    box-shadow: 0 18px 48px rgba(2, 6, 23, 0.5);
                }

                .industry-audit-header {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: 14px 16px;
                    border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                    color: #e2e8f0;
                }

                .industry-audit-filters {
                    display: flex;
                    gap: 8px;
                    align-items: center;
                    padding: 10px 16px;
                    border-bottom: 1px solid rgba(148, 163, 184, 0.2);
                }

                .industry-audit-body {
                    flex: 1;
                    overflow: auto;
                    padding: 8px 12px 12px;
                }

                .industry-audit-table {
                    width: 100%;
                    border-collapse: collapse;
                    font-size: 12px;
                    color: #cbd5e1;
                }

                .industry-audit-table th {
                    position: sticky;
                    top: 0;
                    z-index: 1;
                    text-align: left;
                    padding: 8px;
                    border-bottom: 1px solid rgba(148, 163, 184, 0.3);
                    background: rgba(15, 23, 42, 0.95);
                }

                .industry-audit-table td {
                    padding: 8px;
                    border-bottom: 1px solid rgba(148, 163, 184, 0.16);
                    vertical-align: top;
                    word-break: break-all;
                }
            `}</style>
        </div>
    );
}
