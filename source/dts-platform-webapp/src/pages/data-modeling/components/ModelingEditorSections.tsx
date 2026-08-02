import { RefreshCw, TableProperties } from "lucide-react";
import type { ModelSpecStageGate } from "@/api/modelSpecApi";
import type { WarehousePlanCategoryBindingView, WarehousePlanHeader } from "@/api/warehousePlanApi";
import type { DimensionDefinitionView } from "@/features/modeling/contracts/dimensionDefinitionContract";
import type { CanonicalModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { ActionButton, StatusTag } from "./WorkspacePage";

type ModelingEditorHeaderProps = {
	name: string;
	modelLabel: string;
	model: CanonicalModelSpecView | null;
	saving: boolean;
	contextLoading: boolean;
	canMaintain: boolean;
	checking: boolean;
	error: string | null;
	notice: string | null;
	gates: ModelSpecStageGate[];
	onSave: () => void;
	onCheckGates: () => void;
	onRefresh: () => void;
	onRelease: () => void;
	onCancel?: () => void;
};

export function ModelingEditorHeader({
	name,
	modelLabel,
	model,
	saving,
	contextLoading,
	canMaintain,
	checking,
	error,
	notice,
	gates,
	onSave,
	onCheckGates,
	onRefresh,
	onRelease,
	onCancel,
}: ModelingEditorHeaderProps) {
	return (
		<>
			<div className="dm-editor-tabs">
				<div className="dm-editor-tab is-active">
					<TableProperties aria-hidden="true" size={15} />
					<strong>{name || modelLabel}</strong>
					<span>{model ? `草稿 r${model.revision}` : "新建"}</span>
				</div>
			</div>
			<div className="dm-editor-toolbar">
				<ActionButton
					disabled={saving || contextLoading || !canMaintain}
					kind="primary"
					onClick={onSave}
					title={!canMaintain ? "当前账号没有建模维护权限" : undefined}
				>
					{saving ? "保存中…" : "保存"}
				</ActionButton>
				<ActionButton disabled={saving || !model || checking} onClick={onCheckGates}>
					{checking ? "检查中…" : "校验门禁"}
				</ActionButton>
				<ActionButton disabled={saving} onClick={onRefresh}>
					<RefreshCw aria-hidden="true" size={14} />
					刷新
				</ActionButton>
				{model ? (
					<ActionButton disabled={saving} onClick={onRelease}>
						发布与物化
					</ActionButton>
				) : null}
				{onCancel ? (
					<ActionButton disabled={saving} onClick={onCancel}>
						取消
					</ActionButton>
				) : null}
				<span className="dm-editor-toolbar__status">
					<StatusTag tone={model?.status === "PUBLISHED" ? "success" : "warning"}>
						{model?.status || "未保存"}
					</StatusTag>
				</span>
			</div>
			{error ? (
				<div className="dm-pending-callout" role="alert">
					{error}
				</div>
			) : null}
			{notice ? <output className="dm-pending-callout">{notice}</output> : null}
			{gates.length ? (
				<div className="dm-model-context">
					{gates.map((gate) => (
						<StatusTag key={gate.stage} tone={gate.status === "READY" ? "success" : "danger"}>
							{gate.stage} · {gate.status}
						</StatusTag>
					))}
				</div>
			) : null}
		</>
	);
}

type DimensionDefinitionRef = {
	dimensionDefinitionId: string;
	revision: number;
};

type ModelingBasicInfoSectionProps = {
	persisted: boolean;
	plans: WarehousePlanHeader[];
	planId: string;
	onPlanChange: (value: string) => void;
	categories: WarehousePlanCategoryBindingView[];
	domainId: string;
	onDomainChange: (value: string) => void;
	modelLabel: string;
	modelLayer: string;
	name: string;
	onNameChange: (value: string) => void;
	variantCode: string;
	onVariantCodeChange: (value: string) => void;
	placeholder: string;
	description: string;
	onDescriptionChange: (value: string) => void;
	grainStatement: string;
	onGrainStatementChange: (value: string) => void;
	materialization: string;
	onMaterializationChange: (value: string) => void;
	isDimension: boolean;
	isDimensionDefinition: boolean;
	isDimensionTable: boolean;
	dimensionScdType: "NONE" | "TYPE1" | "TYPE2";
	onDimensionScdTypeChange: (value: "NONE" | "TYPE1" | "TYPE2") => void;
	dimensionReuseScope: "PLAN" | "DOMAIN" | "TENANT";
	onDimensionReuseScopeChange: (value: "PLAN" | "DOMAIN" | "TENANT") => void;
	dimensionDefinitions: DimensionDefinitionView[];
	pendingDefinitionRef: DimensionDefinitionRef | null;
	dimensionDefinitionId: string;
	onDimensionDefinitionChange: (value: string) => void;
};

const categoryLabel = (category: WarehousePlanCategoryBindingView) =>
	category.name || category.code || category.domainId;

export function ModelingBasicInfoSection({
	persisted,
	plans,
	planId,
	onPlanChange,
	categories,
	domainId,
	onDomainChange,
	modelLabel,
	modelLayer,
	name,
	onNameChange,
	variantCode,
	onVariantCodeChange,
	placeholder,
	description,
	onDescriptionChange,
	grainStatement,
	onGrainStatementChange,
	materialization,
	onMaterializationChange,
	isDimension,
	isDimensionDefinition,
	isDimensionTable,
	dimensionScdType,
	onDimensionScdTypeChange,
	dimensionReuseScope,
	onDimensionReuseScopeChange,
	dimensionDefinitions,
	pendingDefinitionRef,
	dimensionDefinitionId,
	onDimensionDefinitionChange,
}: ModelingBasicInfoSectionProps) {
	return (
		<section className="dm-editor-section">
			<h2>基本信息</h2>
			<div className="dm-model-form">
				<div className="dm-model-form__field">
					<span>
						<b>*</b>建模计划
					</span>
					<select
						aria-label="建模计划"
						className="dm-select"
						disabled={persisted}
						onChange={(event) => onPlanChange(event.target.value)}
						value={planId}
					>
						<option value="">请选择建模计划</option>
						{plans.map((plan) => (
							<option key={plan.id} value={plan.id}>
								{plan.name}（{plan.code}）
							</option>
						))}
					</select>
				</div>
				<div className="dm-model-form__field">
					<span>
						<b>*</b>数据域
					</span>
					<select
						aria-label="数据域"
						className="dm-select"
						disabled={!planId || persisted}
						onChange={(event) => onDomainChange(event.target.value)}
						value={domainId}
					>
						<option value="">{categories.length ? "请选择数据域" : "请先在数仓规划确认数据域"}</option>
						{categories.map((category) => (
							<option key={category.domainId} value={category.domainId}>
								{categoryLabel(category)}
							</option>
						))}
					</select>
				</div>
				<div className="dm-model-form__field">
					<span>模型类型</span>
					<input aria-label="模型类型" className="dm-input" readOnly value={modelLabel} />
				</div>
				<div className="dm-model-form__field">
					<span>目标分层</span>
					<input aria-label="目标分层" className="dm-input" readOnly value={modelLayer} />
				</div>
				<div className="dm-model-form__field">
					<span>
						<b>*</b>模型名称
					</span>
					<input
						aria-label="模型名称"
						className="dm-input"
						onChange={(event) => onNameChange(event.target.value)}
						value={name}
					/>
				</div>
				<div className="dm-model-form__field">
					<span>模型编码</span>
					<input
						aria-label="模型编码"
						className="dm-input"
						onChange={(event) => onVariantCodeChange(event.target.value.toUpperCase())}
						placeholder={placeholder}
						value={variantCode}
					/>
				</div>
				<div className="dm-model-form__field dm-model-form__field--wide">
					<span>业务定义</span>
					<textarea
						aria-label="业务定义"
						className="dm-textarea"
						onChange={(event) => onDescriptionChange(event.target.value)}
						rows={2}
						value={description}
					/>
				</div>
				<div className="dm-model-form__field">
					<span>
						<b>*</b>粒度声明
					</span>
					<input
						aria-label="粒度声明"
						className="dm-input"
						onChange={(event) => onGrainStatementChange(event.target.value)}
						placeholder="说明一行代表什么"
						value={grainStatement}
					/>
				</div>
				<div className="dm-model-form__field">
					<span>物化方式</span>
					<select
						aria-label="物化方式"
						className="dm-select"
						onChange={(event) => onMaterializationChange(event.target.value)}
						value={materialization}
					>
						<option value="table">table</option>
						<option value="view">view</option>
						<option value="incremental">incremental</option>
					</select>
				</div>
				{isDimension ? (
					<>
						<div className="dm-model-form__field">
							<span>SCD 策略</span>
							<select
								aria-label="SCD 策略"
								className="dm-select"
								onChange={(event) => onDimensionScdTypeChange(event.target.value as typeof dimensionScdType)}
								value={dimensionScdType}
							>
								<option value="NONE">不保留历史</option>
								<option value="TYPE1">覆盖更新（Type 1）</option>
								<option value="TYPE2">保留历史（Type 2）</option>
							</select>
						</div>
						{isDimensionDefinition ? (
							<div className="dm-model-form__field">
								<span>复用范围</span>
								<select
									aria-label="复用范围"
									className="dm-select"
									onChange={(event) => onDimensionReuseScopeChange(event.target.value as typeof dimensionReuseScope)}
									value={dimensionReuseScope}
								>
									<option value="PLAN">当前建模计划</option>
									<option value="DOMAIN">同一数据域</option>
									<option value="TENANT">租户范围</option>
								</select>
							</div>
						) : null}
						{isDimensionTable && !persisted ? (
							<div className="dm-model-form__field dm-model-form__field--wide">
								<span>
									<b>*</b>现行维度定义
								</span>
								<select
									aria-label="现行维度定义"
									className="dm-select"
									onChange={(event) => onDimensionDefinitionChange(event.target.value)}
									value={dimensionDefinitionId}
								>
									<option value="">{dimensionDefinitions.length ? "请选择" : "当前数据域没有现行维度定义"}</option>
									{pendingDefinitionRef &&
									!dimensionDefinitions.some((item) => item.id === pendingDefinitionRef.dimensionDefinitionId) ? (
										<option value={pendingDefinitionRef.dimensionDefinitionId}>
											固定修订 {pendingDefinitionRef.dimensionDefinitionId}@r{pendingDefinitionRef.revision}
										</option>
									) : null}
									{dimensionDefinitions.map((item) => (
										<option key={item.id} value={item.id}>
											{item.name}（{item.systemCode}）
										</option>
									))}
								</select>
							</div>
						) : null}
					</>
				) : null}
			</div>
		</section>
	);
}
