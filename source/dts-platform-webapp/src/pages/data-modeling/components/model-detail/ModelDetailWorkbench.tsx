import { Code2, GitBranch, Layers3, RefreshCw, TableProperties } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { getModelRepresentation } from "@/api/modelRepresentationApi";
import { getModelLifecycle } from "@/api/modelSpecApi";
import type { ModelRepresentationView } from "@/features/modeling/contracts/modelRepresentationContract";
import type { ModelSpecView } from "@/features/modeling/contracts/modelSpecV2Contract";
import { useCatalogMaintainerAccess } from "@/hooks/useModuleManageAccess";
import { ActionButton, StatusTag } from "../WorkspacePage";
import { AdvancedDbtImplementationView } from "./AdvancedDbtImplementationView";
import { BusinessModelVisualization } from "./BusinessModelVisualization";
import { ModelRepresentationState } from "./ModelRepresentationState";
import { PhysicalModelPreview } from "./PhysicalModelPreview";

type DetailView = "business" | "advanced" | "physical";

const safeErrorMessage = (error: unknown) => {
	if (!error || typeof error !== "object") return undefined;
	const candidate = error as { response?: { data?: { code?: string; message?: string } } };
	return candidate.response?.data?.code || candidate.response?.data?.message;
};

export function ModelDetailWorkbench({ model }: { model: ModelSpecView }) {
	const canMaintain = useCatalogMaintainerAccess();
	const [view, setView] = useState<DetailView>("business");
	const [business, setBusiness] = useState<ModelRepresentationView | null>(null);
	const [technical, setTechnical] = useState<ModelRepresentationView | null>(null);
	const [loading, setLoading] = useState(false);
	const [error, setError] = useState<string | null>(null);

	const loadBusiness = useCallback(async () => {
		setLoading(true);
		setError(null);
		try {
			const lifecycle = await getModelLifecycle(model.id);
			const implementationRevision = lifecycle.implementation?.implementationRevision;
			const representation = await getModelRepresentation(model.id, {
				modelRevision: model.revision,
				...(implementationRevision ? { implementationRevision } : {}),
				representationScope: "BUSINESS",
			});
			setBusiness(representation);
			setTechnical(null);
		} catch (cause) {
			setBusiness(null);
			setError(safeErrorMessage(cause) || "MODEL_REPRESENTATION_READ_FAILED");
		} finally {
			setLoading(false);
		}
	}, [model.id, model.revision]);

	useEffect(() => {
		setView("business");
		void loadBusiness();
	}, [loadBusiness]);

	const openTechnical = async () => {
		setView("advanced");
		if (technical || !business?.implementationRevision) return;
		setLoading(true);
		setError(null);
		try {
			setTechnical(
				await getModelRepresentation(model.id, {
					modelRevision: business.modelRevision,
					implementationRevision: business.implementationRevision,
					representationScope: "TECHNICAL",
				}),
			);
		} catch (cause) {
			setError(safeErrorMessage(cause) || "MODEL_REPRESENTATION_TECHNICAL_ACCESS_DENIED");
		} finally {
			setLoading(false);
		}
	};

	const openBusiness = () => {
		setError(null);
		setView("business");
	};

	const openPhysical = () => {
		setError(null);
		setView("physical");
	};

	return (
		<section className="dm-model-editor">
			<div className="dm-editor-tabs">
				<div className="dm-editor-tab is-active">
					<TableProperties aria-hidden="true" size={15} />
					<strong>{model.name}</strong>
					<span>模型 r{model.revision}</span>
				</div>
			</div>
			<div className="dm-editor-toolbar">
				<ActionButton onClick={openBusiness}>
					<Layers3 aria-hidden="true" size={14} />
					业务可视化
				</ActionButton>
				{business?.ownershipMode === "DBT_MANAGED" && canMaintain ? (
					<ActionButton onClick={() => void openTechnical()}>
						<Code2 aria-hidden="true" size={14} />
						高级 dbt 实现
					</ActionButton>
				) : null}
				<ActionButton onClick={openPhysical}>
					<GitBranch aria-hidden="true" size={14} />
					物理资产
				</ActionButton>
				<ActionButton onClick={() => void loadBusiness()}>
					<RefreshCw aria-hidden="true" size={14} />
					刷新
				</ActionButton>
				<span className="dm-editor-toolbar__status">
					<StatusTag tone={model.status === "PUBLISHED" ? "success" : "warning"}>{model.status}</StatusTag>
				</span>
			</div>
			{business ? (
				<div className="dm-model-context">
					<span>Model r{business.modelRevision}</span>
					<code>{business.modelChecksum.slice(0, 12)}</code>
					<span>Implementation r{business.implementationRevision ?? "-"}</span>
					<code>{business.implementationChecksum?.slice(0, 12) || "-"}</code>
					<StatusTag tone="info">{business.ownershipMode}</StatusTag>
					<StatusTag tone={business.visualizationCapability === "BLOCKED" ? "danger" : "success"}>
						{business.visualizationCapability}
					</StatusTag>
				</div>
			) : null}

			{loading ? <ModelRepresentationState state="loading" /> : null}
			{!loading && error ? <ModelRepresentationState message={error} state="error" /> : null}
			{!loading && !error && business?.visualizationCapability === "BLOCKED" ? (
				<ModelRepresentationState message={business.capabilityReasons.join("；")} state="blocked" />
			) : null}
			{!loading && !error && business && business.visualizationCapability !== "BLOCKED" && view === "business" ? (
				<BusinessModelVisualization
					dependencyProjection={business.dependencyProjection}
					logicalModel={business.logicalModel}
				/>
			) : null}
			{!loading && !error && business && view === "advanced" ? (
				<AdvancedDbtImplementationView
					baseImplementationChecksum={technical?.implementationChecksum}
					baseImplementationRevision={technical?.implementationRevision}
					baseModelChecksum={technical?.modelChecksum || model.checksum}
					baseModelRevision={technical?.modelRevision || model.revision}
					modelSpecId={model.id}
					onCommitted={() => {
						setTechnical(null);
						setView("business");
						void loadBusiness();
					}}
					planId={business.logicalModel.planId}
					technicalImplementation={technical?.technicalImplementation}
				/>
			) : null}
			{!loading && !error && business && view === "physical" ? (
				<PhysicalModelPreview
					canPreviewCandidate={canMaintain}
					key={`${business.etag}-physical`}
					representation={business}
				/>
			) : null}
		</section>
	);
}
