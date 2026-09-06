# T10 统一交付状态只读契约

**状态：FROZEN（服务端 DTO/API）；不代表构建、部署或页面验收完成。**

## API

`GET /api/modeling/model-specs/{id}/delivery-status?environment=<环境>&candidateId=<UUID>`

Definition-only persistence for an existing draft is `PUT /api/modeling/model-specs/{id}/definition`. It accepts the existing `ModelSpecUpdateRequestDecoder.decodeDefinition` body and requires canonical `If-Match: "model-spec:{id}:{revision}:{checksum}"`. It returns `ApiResponse<ModelSpecView>` with the replacement ETag. It calls existing `ModelSpecApplicationService.updateDefinition`; only a readable, writable `DRAFT` model can pass and no implementation is created or updated.

- `id` 必须是调用者在当前 tenant 中可读取的 ModelSpec；不能通过候选或输出资产反向发现模型。
- `environment` 可选。给定时只接受 candidate.environment 精确相等的候选；不以版本、目标名称或显示名替代环境匹配。
- `candidateId` 可选。给定时必须属于该模型的 `planId`、包含该 `modelSpecId` 的 entry，且 tenant 相同；不满足统一返回既有范围内 not-found，不泄露别的计划、模型或 tenant 的候选。
- 未给 `candidateId` 时只读取该模型 plan 当前 workbench candidate；没有候选时返回模型身份和五个 `NOT_STARTED`/`UNKNOWN` 步骤，不创建候选或写任何记录。
- 成功响应沿用 `ApiResponse<DeliveryStatusView>`；GET 不修复关联、不触发质量、物化、发布、目录登记或分析同步。

## Response DTO

```ts
type DeliveryStepKey = 'materialization' | 'quality' | 'publication' | 'catalog' | 'analysis';
type DeliveryStepState = 'NOT_STARTED' | 'WAITING_INPUT' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'NOT_APPLICABLE' | 'UNKNOWN';
type DeliveryActionCode =
  | 'EDIT_MODEL' | 'EDIT_IMPLEMENTATION' | 'SAVE' | 'VALIDATE' | 'COMMIT' | 'FORK_DRAFT'
  | 'CREATE_CANDIDATE' | 'UPDATE_SCOPE' | 'REFRESH_CANDIDATE' | 'REMATERIALIZE'
  | 'START_BUILD' | 'RETRY_BUILD' | 'RUN_QUALITY' | 'SUBMIT_REVIEW' | 'CANCEL_CANDIDATE'
  | 'APPROVE' | 'REJECT' | 'CREATE_REPLACEMENT_CANDIDATE' | 'PUBLISH'
  | 'RETRY_PUBLICATION' | 'ROLLBACK'
  | 'CONFIGURE_QUALITY_RULES' | 'RERUN_GOVERNANCE_QUALITY';

interface DeliveryStatusView {
  modelSpecId: string; modelRevision: number; modelChecksum: string;
  planId: string; environment: string | null;
  candidate: { id: string; version: number; status: string; entryRevision: number;
    entryChecksum: string; matchesCurrentModel: boolean; updatedAt: string } | null;
  observedAt: string;
  workspace: ReleaseCandidateWorkbench | null; // exact authorized candidate projection; null when no model candidate
  recommendedStep: 'definition' | 'implementation' | 'verification' | 'delivery';
  wizard: Array<{ key: 'definition' | 'implementation' | 'verification' | 'delivery';
    canView: boolean; canEdit: boolean; reasonCode: string | null;
    primaryAction: DeliveryAction | null }>;
  steps: Array<{ key: DeliveryStepKey; state: DeliveryStepState; reasonCode: string | null;
    message: string; evidenceRevision: number | null; matchesCurrentTarget: boolean;
    resourceId: string | null; updatedAt: string | null; outputs: DeliveryOutput[] }>;
  actions: DeliveryAction[];
}
interface DeliveryAction { code: DeliveryActionCode; enabled: boolean; reasonCode: string | null;
  targetId: string | null; expectedVersion: number | null; }
interface DeliveryOutput { resourceId: string | null; state: DeliveryStepState; reasonCode: string | null;
  message: string; matchesCurrentTarget: boolean; updatedAt: string | null; }
```

`wizard[].primaryAction` is either `null` or one action from `actions`; each page has at most one. Definition and implementation `canEdit` and the editor action summary derive from current `authoring-context.allowedActions` (`EDIT_MODEL`, `EDIT_IMPLEMENTATION`, `SAVE`, `VALIDATE`, `COMMIT`, `FORK_DRAFT`), not delivery state. W1 selects definition save when editable; W2 selects validate/commit from those existing actions and whether an implementation exists. `canView` is a navigation/read decision. Candidate commands are exposed only from the exact workbench's role-filtered `allowedActions`, including `CREATE_CANDIDATE`, build/retry/quality, review/approval/rejection and publication recovery. W3 selects `CREATE_CANDIDATE` when no candidate exists, then the first authorized refresh/build/quality action. W4 selects the current authorized review, approval, rejection, publish, retry, replacement or rollback action. `CONFIGURE_QUALITY_RULES` and `RERUN_GOVERNANCE_QUALITY` are derived only from T11's exact-candidate `CandidateQualityRuleContextService` decision: the former requires BUILT outputs with configurable assets lacking a published rule; the latter requires QUALITY_RUNNING with rerunnable current evidence. `RUN_QUALITY` remains present only when the existing workbench authorizes it and T11 reports published bindings. Analysis retry is absent until its owner exposes a server-authorized action.

## Evidence mapping and isolation

- The candidate entry is valid only when `(modelSpecId, planId, revision, checksum)` equals the loaded model. After materialization pins an `implementationId`, the projection also requires that immutable implementation identity and the persisted entry-evidence implementation revision to equal the current authoring implementation. An old entry remains visible as `matchesCurrentModel=false` but never proves a current step.
- `workspace` is the existing server-owned candidate/evidence/entryEvidence/governanceQuality/primaryBlocker/allowedActions/etag projection for the exact selected candidate. An omitted candidateId may use the plan workbench only when it contains this model; a workbench candidate for another model is returned as `null`, never substituted.
- Candidate `BUILDING`/`QUALITY_RUNNING`/`PUBLISHING` map only their respective step to `RUNNING`. `BUILT`, `QUALITY_PASSED`, `PUBLISHED`, failures and partial publication map through existing candidate evidence/status; no new success state is persisted.
- `catalog` and `analysis` are separate steps. Their arrays preserve one item per owner observation. An absent owner observation is `UNKNOWN`, while a failing owner observation is `FAILED`; a read failure is `UNKNOWN` with its stable reason code. One output's success cannot turn another output successful.
- `environment`, entry revision/checksum and output identity must all match before `matchesCurrentTarget=true`. Cross-model candidate entries and cross-environment evidence are excluded, never summarized as model evidence.
- Every list is immutable and present (possibly empty). Partial owner failures are represented in the relevant output item and do not erase confirmed results from other owners.

## Recommended wizard step

`definition`/`implementation` reflect normal ModelSpec read state. A current candidate that needs build or quality recommends `verification`; a current candidate ready for publication, publication recovery, catalog or analysis follow-up recommends `delivery`. Running work recommends the page that owns it. A stale or mismatched candidate never grants editability and recommends the first corrective page with its reason code.

## Initial behavior tests

- rejects a candidate from another model/plan/tenant without revealing it;
- rejects environment mismatch and marks an old revision/checksum as non-current;
- maps existing allowedActions only, with no enum-derived write authorization;
- retains per-output catalog/analysis partial failure and no evidence as distinct states;
- has no repository writes during GET.
