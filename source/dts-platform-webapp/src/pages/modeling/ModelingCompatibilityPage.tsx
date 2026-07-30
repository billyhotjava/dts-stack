import { Alert, Button, Card, Space, Typography } from "antd";
import { useEffect, useMemo, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router";
import { listModelSpecs } from "@/api/modelSpecApi";
import { LineLoading } from "@/components/loading";
import { type LegacyObjectTarget, resolveModelingCompatibilityTarget } from "./modelingCompatibilityRoute";
import type { ModelSpecView } from "./modelSpecV2Contract";

const { Paragraph, Text, Title } = Typography;

type ObjectLookup = {
	requestKey: string;
	status: "idle" | "loading" | "ready" | "error";
	mapped?: LegacyObjectTarget;
};

const legacyObjectTarget = (models: ModelSpecView[], objectId: string): LegacyObjectTarget | undefined => {
	const model = models.find(
		(candidate) =>
			candidate.compatibilityMode === "LEGACY_READONLY" && candidate.legacyRefs.legacyModelRef === objectId,
	);
	if (!model) return undefined;
	return {
		modelSpecId: model.id,
		modelType: model.modelType,
		planId: model.planId,
		domainId: model.domainId,
		revision: String(model.revision),
	};
};

export default function ModelingCompatibilityPage() {
	const location = useLocation();
	const navigate = useNavigate();
	const searchParams = useMemo(() => new URLSearchParams(location.search), [location.search]);
	const objectId = searchParams.get("objectId")?.trim() || "";
	const needsObjectLookup = location.pathname === "/modeling/semantic/objects" && Boolean(objectId);
	const [attempt, setAttempt] = useState(0);
	const requestKey = needsObjectLookup ? `${location.pathname}:${objectId}:${attempt}` : "";
	const [lookup, setLookup] = useState<ObjectLookup>({
		requestKey: "",
		status: "idle",
	});

	useEffect(() => {
		if (!needsObjectLookup) {
			setLookup({ requestKey: "", status: "ready" });
			return;
		}
		let cancelled = false;
		setLookup({ requestKey, status: "loading" });
		void listModelSpecs()
			.then((models) => {
				if (cancelled) return;
				setLookup({
					requestKey,
					status: "ready",
					mapped: legacyObjectTarget(Array.isArray(models) ? models : [], objectId),
				});
			})
			.catch(() => {
				if (!cancelled) setLookup({ requestKey, status: "error" });
			});
		return () => {
			cancelled = true;
		};
	}, [needsObjectLookup, objectId, requestKey]);

	const currentLookup: ObjectLookup =
		lookup.requestKey === requestKey ? lookup : { requestKey, status: needsObjectLookup ? "loading" : "ready" };

	if (needsObjectLookup && (currentLookup.status === "idle" || currentLookup.status === "loading")) {
		return <LineLoading />;
	}

	if (needsObjectLookup && currentLookup.status === "error") {
		return (
			<div className="mx-auto max-w-2xl p-4" data-testid="modeling-compatibility-error">
				<Card>
					<Alert
						type="error"
						showIcon
						message="无法验证旧链接目标"
						description="目标可能已迁移、暂时不可用，或当前账号无权访问。系统不会退回旧写入口。"
						action={<Button onClick={() => setAttempt((value) => value + 1)}>重试</Button>}
					/>
				</Card>
			</div>
		);
	}

	const target = resolveModelingCompatibilityTarget(
		location.pathname,
		searchParams,
		currentLookup.mapped,
		location.hash,
	);
	if (target.kind === "redirect") return <Navigate to={target.to} replace />;

	return (
		<div className="mx-auto max-w-2xl p-4" data-testid="modeling-compatibility-recovery">
			<Card>
				<Title level={3}>旧模型需要确认业务分类</Title>
				<Paragraph type="secondary">
					该旧链接尚未找到可安全定位的新模型。请先确认业务分类，再从维度目录或模型中心继续。
				</Paragraph>
				<Alert
					type="warning"
					showIcon
					message="NEEDS_CLASSIFICATION"
					description={<Text code>{target.legacyObjectId}</Text>}
				/>
				<Space className="mt-4" wrap>
					<Button type="primary" onClick={() => navigate("/governance/subjects")}>
						前往业务分类
					</Button>
					<Button onClick={() => navigate("/modeling/workbench")}>返回建模工作台</Button>
				</Space>
			</Card>
		</div>
	);
}
