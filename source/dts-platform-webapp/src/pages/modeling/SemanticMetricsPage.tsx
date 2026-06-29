import { useCallback, useEffect, useState } from "react";
import { Button, Drawer, Form, Input, Select, Tag } from "antd";
import { toast } from "sonner";
import { CompactTable } from "@/components/table";
import { PageHeader } from "@/components/page-header";
import type { ColumnsType } from "antd/es/table";
import {
    listSemanticMetrics,
    createSemanticMetric,
    updateSemanticMetric,
    listSemanticBusinessObjects,
    type SemanticMetric,
    type SemanticBusinessObject,
} from "@/api/semanticModelingApi";

const FORMULA_TYPE_COLOR: Record<string, string> = {
    "aggregation/sum": "blue",
    "aggregation/count_distinct": "green",
    "aggregation/avg": "orange",
};

const FORMULA_OPTIONS = [
    { label: "aggregation/sum", value: "aggregation/sum" },
    { label: "aggregation/count_distinct", value: "aggregation/count_distinct" },
    { label: "aggregation/avg", value: "aggregation/avg" },
    { label: "aggregation/max", value: "aggregation/max" },
    { label: "aggregation/min", value: "aggregation/min" },
];

export default function SemanticMetricsPage() {
    const [metrics, setMetrics] = useState<SemanticMetric[]>([]);
    const [objects, setObjects] = useState<SemanticBusinessObject[]>([]);
    const [loading, setLoading] = useState(false);
    const [editRow, setEditRow] = useState<SemanticMetric | null>(null);
    const [drawerOpen, setDrawerOpen] = useState(false);
    const [createOpen, setCreateOpen] = useState(false);
    const [form] = Form.useForm();
    const [createForm] = Form.useForm();

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const [m, o] = await Promise.allSettled([listSemanticMetrics(), listSemanticBusinessObjects()]);
            if (m.status === "fulfilled") setMetrics(Array.isArray(m.value) ? (m.value as SemanticMetric[]) : []);
            if (o.status === "fulfilled") setObjects(Array.isArray(o.value) ? (o.value as SemanticBusinessObject[]) : []);
        } catch {
            /* global interceptor */
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => { void load(); }, [load]);

    const handleEdit = (row: SemanticMetric) => {
        setEditRow(row);
        form.setFieldsValue({ formulaType: row.formulaType, formulaJson: row.formulaJson, unit: row.unit });
        setDrawerOpen(true);
    };

    const handleSave = async () => {
        try {
            const values = await form.validateFields();
            await updateSemanticMetric(editRow!.id, values);
            toast.success("指标已保存");
            setDrawerOpen(false);
            void load();
        } catch (err: unknown) {
            if (err && typeof err === "object" && "errorFields" in err) return;
        }
    };

    const handleCreate = async () => {
        try {
            const values = await createForm.validateFields();
            await createSemanticMetric(values);
            toast.success("指标已创建");
            setCreateOpen(false);
            void load();
        } catch (err: unknown) {
            if (err && typeof err === "object" && "errorFields" in err) return;
        }
    };

    const columns: ColumnsType<SemanticMetric> = [
        { title: "编码", dataIndex: "code", width: 140 },
        { title: "名称", dataIndex: "name" },
        {
            title: "公式类型", dataIndex: "formulaType", width: 200,
            render: (v?: string) => v
                ? <Tag color={FORMULA_TYPE_COLOR[v] ?? "default"}>{v}</Tag>
                : <span style={{ color: "#aaa" }}>-</span>,
        },
        { title: "单位", dataIndex: "unit", width: 80 },
        {
            title: "状态", dataIndex: "status", width: 100,
            render: (v?: string) => <Tag color={v === "ACTIVE" ? "success" : "default"}>{v ?? "DRAFT"}</Tag>,
        },
        {
            title: "操作", key: "actions", width: 100,
            render: (_: unknown, row: SemanticMetric) => (
                <Button type="link" size="small" onClick={() => handleEdit(row)}>编辑公式</Button>
            ),
        },
    ];

    return (
        <div className="space-y-4" data-testid="semantic-metrics-page">
            <PageHeader
                title="语义建模 · 指标"
                actions={
                    <Button
                        type="primary"
                        data-testid="semantic-metrics-create"
                        onClick={() => { createForm.resetFields(); setCreateOpen(true); }}
                    >
                        + 新建指标
                    </Button>
                }
            />
            <CompactTable<SemanticMetric>
                rowKey="id"
                columns={columns}
                dataSource={metrics}
                loading={loading}
            />
            <Drawer
                title="编辑公式"
                open={drawerOpen}
                onClose={() => setDrawerOpen(false)}
                footer={<Button type="primary" block onClick={handleSave}>保存</Button>}
            >
                <Form form={form} layout="vertical">
                    <Form.Item name="formulaType" label="公式类型">
                        <Select options={FORMULA_OPTIONS} placeholder="选择类型" />
                    </Form.Item>
                    <Form.Item name="formulaJson" label="公式 JSON">
                        <Input.TextArea rows={5} style={{ fontFamily: "monospace", fontSize: 12 }} />
                    </Form.Item>
                    <Form.Item name="unit" label="单位">
                        <Input placeholder="万元 / 次 / %" />
                    </Form.Item>
                </Form>
            </Drawer>
            <Drawer
                title="新建指标"
                open={createOpen}
                onClose={() => setCreateOpen(false)}
                footer={<Button type="primary" block onClick={handleCreate}>创建</Button>}
            >
                <Form form={createForm} layout="vertical">
                    <Form.Item name="code" label="编码" rules={[{ required: true }]}>
                        <Input placeholder="GMV" />
                    </Form.Item>
                    <Form.Item name="name" label="名称" rules={[{ required: true }]}>
                        <Input placeholder="成交总额" />
                    </Form.Item>
                    <Form.Item name="objectId" label="关联业务对象">
                        <Select
                            options={objects.map((o) => ({ label: o.name, value: o.id }))}
                            placeholder="选择业务对象"
                        />
                    </Form.Item>
                    <Form.Item name="formulaType" label="公式类型">
                        <Select options={FORMULA_OPTIONS} />
                    </Form.Item>
                    <Form.Item name="unit" label="单位">
                        <Input placeholder="万元" />
                    </Form.Item>
                </Form>
            </Drawer>
        </div>
    );
}
