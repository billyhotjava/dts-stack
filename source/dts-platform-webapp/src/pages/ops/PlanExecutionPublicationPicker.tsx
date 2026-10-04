import { Alert, Button, Modal, Select, Typography } from "antd";
import { useEffect, useState } from "react";
import { deployPlanExecution, listPlanExecutionPublications, type PlanExecutionPublication } from "@/api/modelSpecApi";
import type { WarehousePlanHeader } from "@/api/warehousePlanApi";
import { normalizeModelingRequestFailure } from "@/pages/data-modeling/prototype/services/planningProjectionService";

type Choice = { plan: WarehousePlanHeader; publication: PlanExecutionPublication };
const ENV: Record<string, string> = { dev: "开发环境", test: "测试环境", prod: "生产环境" };

export function PlanExecutionPublicationPicker({ plans, onDeployed, refreshKey }: {
    plans: WarehousePlanHeader[]; onDeployed: () => Promise<void>; refreshKey: unknown;
}) {
    const [choices, setChoices] = useState<Choice[]>([]);
    const [selected, setSelected] = useState<string>();
    const [failure, setFailure] = useState("");
    const [busy, setBusy] = useState(false);
    const [confirming, setConfirming] = useState(false);
    useEffect(() => {
        let current = true;
        setSelected(undefined); setChoices([]); setConfirming(false);
        void Promise.all(plans.map(async (plan) => {
            try { return { choices: (await listPlanExecutionPublications(plan.id)).map((publication) => ({ plan, publication })), failed: false }; }
            catch { return { choices: [], failed: true }; }
        })).then((results) => {
            if (!current) return;
            setChoices(results.flatMap((result) => result.choices));
            setFailure(results.some((result) => result.failed) ? "部分规划的已发布版本读取失败，请刷新后重试。" : "");
        });
        return () => { current = false; };
    }, [plans, refreshKey]);
    const choice = choices.find((value) => value.publication.candidateId === selected);
    const deploy = async () => {
        if (!choice) return;
        setBusy(true); setFailure("");
        try { await deployPlanExecution(choice.plan.id, choice.publication); setConfirming(false); await onDeployed(); }
        catch (error) { setFailure(normalizeModelingRequestFailure(error, "部署提交失败，请刷新后重试。").message); }
        finally { setBusy(false); }
    };
    return <section aria-label="部署已发布版本" style={{ marginBottom: 16 }}>
        <div style={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 8 }}>
            <Typography.Text strong>部署已发布版本</Typography.Text>
            <Select aria-label="选择部署版本" value={selected} onChange={setSelected} style={{ width: 320, maxWidth: "100%", minWidth: 0 }} placeholder="选择规划与环境"
                options={choices.map(({ plan, publication }) => ({ value: publication.candidateId, label: `${plan.name} · ${ENV[publication.environment] || publication.environment}`, disabled: !publication.canDeploy }))} />
            <Button disabled={!choice || busy} onClick={() => setConfirming(true)}>部署版本</Button>
            <Button disabled={busy} onClick={() => void onDeployed()}>刷新</Button>
        </div>
        {choice ? <p>{choice.publication.models.join("、")}</p> : null}
        {failure ? <Alert type="error" showIcon message={failure} /> : null}
        <Modal open={confirming && !!choice} title="确认部署范围" okText="确认部署" cancelText="取消" confirmLoading={busy} onOk={() => void deploy()} onCancel={() => !busy && setConfirming(false)}>
            <p>{choice?.plan.name} · {ENV[choice?.publication.environment || ""] || choice?.publication.environment}</p>
            <p>{choice?.publication.models.join("、")}</p>
            <p>部署将替换该环境的运行版本，并设为仅手动运行。部署完成后需单独启用。</p>
        </Modal>
    </section>;
}
