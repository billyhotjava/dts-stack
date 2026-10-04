import { Alert, Button, Input, Space, Spin } from "antd";
import { useCallback, useEffect, useRef, useState } from "react";
import { getDataset, updateDatasetGovernanceSummary, type DatasetGovernanceSummary } from "@/api/platformApi";
export type UnsavedEditorHandle = { dirty: boolean; save: () => Promise<boolean>; discard: () => void };

export function CatalogDatasetGovernanceSummaryEditor({ datasetId, canMaintain, onSaved, onDirtyChange, onNavigationGuardChange }: {
    datasetId: string; canMaintain: boolean; onSaved?: (dataset: DatasetGovernanceSummary) => void;
    onDirtyChange?: (dirty: boolean) => void; onNavigationGuardChange?: (handle: UnsavedEditorHandle | null) => void;
}) {
    const [dataset, setDataset] = useState<DatasetGovernanceSummary | null>(null);
    const [owner, setOwner] = useState(""), [description, setDescription] = useState("");
    const [loading, setLoading] = useState(true), [saving, setSaving] = useState(false), [error, setError] = useState("");
    const requestSequence = useRef(0), pending = useRef(false), currentId = useRef(datasetId);
    const savedCallback = useRef(onSaved); savedCallback.current = onSaved; currentId.current = datasetId;
    const dirty = !!dataset && (owner !== (dataset.owner || "") || description !== (dataset.description || ""));
    const discard = useCallback(() => { if (dataset) {setOwner(dataset.owner || ""); setDescription(dataset.description || "");} }, [dataset]);
    const load = async (force = false) => {
        if (!force && dirty && !window.confirm("未保存的修改将丢失，是否重新加载？")) return;
        const sequence = ++requestSequence.current; setLoading(true); setError(""); setDataset(null);
        try {
            const value: any = await getDataset(datasetId);
            if (sequence !== requestSequence.current) return;
            const data = value?.data || value;
            if (data.id !== datasetId || !Number.isInteger(data.version)) throw new Error("资产版本不可用");
            setDataset(data); setOwner(data.owner || ""); setDescription(data.description || "");
        } catch (e: any) {
            if (sequence !== requestSequence.current) return;
            setOwner(""); setDescription(""); setError(e?.response?.status === 403 ? "没有维护该资产的权限" : "资产信息加载失败");
        } finally { if (sequence === requestSequence.current) setLoading(false); }
    };
    // Load identity is datasetId; edits must never trigger a reload.
    // biome-ignore lint/correctness/useExhaustiveDependencies: only a target change reloads this editable snapshot.
    useEffect(() => { void load(true); return () => {requestSequence.current += 1;}; }, [datasetId]);
    useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);
    const save = useCallback(async () => {
        if (!dataset || dataset.id !== datasetId || !canMaintain || pending.current) return false;
        if (!dirty) return true;
        pending.current = true; setSaving(true); setError("");
        try {
            const value: any = await updateDatasetGovernanceSummary(datasetId, { owner: owner.trim() || null, description: description.trim() || null }, dataset.version);
            if (currentId.current !== datasetId) return false;
            const saved = value?.data || value;
            setDataset(saved); setOwner(saved.owner || ""); setDescription(saved.description || ""); savedCallback.current?.(saved); return true;
        } catch (e: any) {
            if (currentId.current !== datasetId) return false;
            const status = e?.response?.status;
            setError(status === 409 ? "资产已被其他操作修改，请重新加载后保存" : status === 401 || status === 403 ? "没有维护该资产的权限" : "保存失败，请稍后重试"); return false;
        } finally { pending.current = false; setSaving(false); }
    }, [dataset, datasetId, canMaintain, dirty, owner, description]);
    useEffect(() => { onNavigationGuardChange?.({dirty,save,discard}); return () => onNavigationGuardChange?.(null); }, [dirty,save,discard,onNavigationGuardChange]);
    if (loading) return <Spin size="small" />;
    return <section aria-label="基本治理信息"><Space direction="vertical" style={{ width: "100%" }}><strong>基本治理信息</strong>
        {error ? <Alert type="error" message={error} action={<Button size="small" disabled={saving} onClick={() => void load()}>重新加载</Button>} /> : null}
        <Input aria-label="资产负责人" value={owner} disabled={!dataset || !canMaintain || saving} placeholder="负责人" onChange={e => setOwner(e.target.value)} />
        <Input.TextArea aria-label="资产说明" value={description} disabled={!dataset || !canMaintain || saving} placeholder="资产说明" rows={3} onChange={e => setDescription(e.target.value)} />
        <Space><Button type="primary" disabled={!dataset || !canMaintain || !dirty} loading={saving} onClick={() => void save()}>保存</Button><Button disabled={!dataset || !dirty || saving} onClick={discard}>取消</Button></Space>
    </Space></section>;
}
