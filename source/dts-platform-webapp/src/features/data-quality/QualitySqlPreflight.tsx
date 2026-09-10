import { Alert, Button, Space } from "antd";
import { useEffect, useRef, useState } from "react";
import { previewQualityDraft, validateQualityDraft, qualityDiagnosticText, type QualityPreview, type QualityValidation } from "./qualityExecutionContract";

export function QualitySqlPreflight({ datasetId, sql, disabled }: { datasetId?: string; sql?: string; disabled?: boolean }) {
 const identity = JSON.stringify([datasetId, sql]);
 const current = useRef(identity); current.current = identity;
 const sequence = useRef(0);
 const [busy, setBusy] = useState<"validate" | "preview" | "">("");
 const [validation, setValidation] = useState<QualityValidation>();
 const [preview, setPreview] = useState<QualityPreview>();
 const [failure, setFailure] = useState("");
 useEffect(() => { sequence.current++; setValidation(undefined); setPreview(undefined); setFailure(""); setBusy(""); }, [identity]);
 useEffect(() => () => { sequence.current++; }, []);
 const run = async (mode: "validate" | "preview") => {
  if (!datasetId || !sql?.trim() || disabled || busy) return;
  const key = identity; const request = ++sequence.current;
  setBusy(mode); setFailure(""); setPreview(undefined);
  try {
   const checked = await validateQualityDraft(datasetId, sql);
   if (current.current !== key || sequence.current !== request) return;
   setValidation(checked);
   if (checked.valid && mode === "preview") {
    const result = await previewQualityDraft(datasetId, sql);
    if (current.current === key && sequence.current === request) {
     if (result.checksum !== checked.checksum) throw new Error("试跑内容与当前 SQL 不一致，请重试");
     setPreview(result);
    }
   }
  } catch (error) {
   if (current.current === key && sequence.current === request) setFailure(error instanceof Error ? error.message : "校验或试跑失败");
  } finally { if (current.current === key && sequence.current === request) setBusy(""); }
 };
 return <Space direction="vertical" style={{ width: "100%" }}>
  <p>SQL 返回违规记录；仅允许只读查询和绑定资产的完整表名，不要求 id 列。试跑执行当前输入，不保存规则、不作为正式发布证据。</p>
  <Space wrap>
   <Button disabled={disabled || !datasetId || !sql?.trim() || Boolean(busy)} loading={busy === "validate"} onClick={() => void run("validate")}>校验 SQL</Button>
   <Button disabled={disabled || !datasetId || !sql?.trim() || Boolean(busy)} loading={busy === "preview"} onClick={() => void run("preview")}>试跑当前 SQL</Button>
  </Space>
  {disabled ? <p>当前无维护权限或编辑器尚未就绪。</p> : null}
  {validation ? <Alert showIcon type={validation.valid ? "success" : "error"} message={validation.valid ? "SQL 静态校验通过" : "SQL 校验未通过"}
   description={<div>{validation.diagnostics.map((item, index) => <p key={index}>{qualityDiagnosticText(item)}</p>)}<p>允许函数：{validation.allowedFunctions}</p><p>静态校验不检查目标表的实时列结构，实际执行以试跑结果为准。</p></div>} /> : null}
  {preview ? <Alert showIcon type={preview.outcome.qualityOutcome === "PASSED" && preview.outcome.executionOutcome === "OK" ? "success" : "warning"}
   message={`试跑：${preview.outcome.qualityOutcome === "PASSED" ? "检查通过" : preview.outcome.qualityOutcome === "VIOLATION" ? "发现违规" : "暂无业务结论"}；${preview.outcome.executionOutcome === "OK" ? "执行完成" : "执行未完成"}`}
   description={<div>{preview.outcome.violationOccurrences != null ? <p>违规记录次数：{preview.outcome.violationOccurrences}（多语句可能重复）</p> : null}{preview.outcome.diagnostics.map((item, index) => <p key={index}>{qualityDiagnosticText(item)}</p>)}</div>} /> : null}
  {failure ? <Alert type="error" showIcon message={failure} /> : null}
 </Space>;
}
