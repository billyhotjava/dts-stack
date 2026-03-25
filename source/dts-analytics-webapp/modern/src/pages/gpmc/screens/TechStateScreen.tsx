import { useGpmc } from "../GpmcApp";
import { techStateChanges, techStateKpis, techByChangeType, techSignatureStatus } from "../mockData";

export default function TechStateScreen() {
	const { layer, drillDown, setLayer } = useGpmc();
	const maxChangeTypeValue = Math.max(...techByChangeType.map((item) => item.value));
	const maxSignatureValue = Math.max(...techSignatureStatus.map((item) => item.value));

	return (
		<div>
			{layer !== "strategic" && (
				<div className="gpmc__breadcrumb">
					<span className="gpmc__breadcrumb-link" onClick={() => setLayer("strategic")}>战略层</span>
					<span className="gpmc__breadcrumb-sep">/</span>
					{layer === "execution" && (
						<>
							<span className="gpmc__breadcrumb-link" onClick={() => setLayer("control")}>管控层</span>
							<span className="gpmc__breadcrumb-sep">/</span>
						</>
					)}
					<span className="gpmc__breadcrumb-current">{layer === "control" ? "管控层" : "执行层"}</span>
				</div>
			)}

			<div className="gpmc__kpi-row">
				{techStateKpis.map((kpi) => (
					<div
						key={kpi.key}
						className="gpmc__kpi-card gpmc__kpi-card--clickable"
						onClick={drillDown}
					>
						<div className="gpmc__kpi-label">{kpi.label}</div>
						<div className="gpmc__kpi-value">
							{kpi.value}
							{kpi.unit === "%" ? <span style={{ fontSize: 18, fontWeight: 600 }}>%</span> : null}
						</div>
						<div className="gpmc__kpi-sub">
							{kpi.unit && kpi.unit !== "%" ? <span>{kpi.unit}</span> : null}
							{kpi.sub ? <span>{kpi.sub}</span> : null}
							{kpi.trend && (
								<span className={`gpmc__kpi-trend gpmc__kpi-trend--${kpi.trendDir}`}>{kpi.trend}</span>
							)}
							{kpi.tone && (
								<span className={`gpmc__kpi-badge gpmc__kpi-badge--${kpi.tone}`}>
									{kpi.tone === "danger" ? "需关注" : "注意"}
								</span>
							)}
						</div>
					</div>
				))}
			</div>

			<div className="gpmc__two-col">
				<div className="gpmc__panel">
					<div className="gpmc__panel-header">
						<div>
							<div className="gpmc__panel-title">技术状态变更清单</div>
							<div className="gpmc__panel-subtitle">技术状态与跟进</div>
						</div>
						<div className="gpmc__panel-meta">共 {techStateChanges.length} 项</div>
					</div>
					<div className="gpmc__panel-body" style={{ padding: 0 }}>
						<table className="gpmc__table">
							<thead>
								<tr>
									<th>名称</th>
									<th>项目</th>
									<th>状态</th>
									<th>类别</th>
								</tr>
							</thead>
							<tbody>
								{techStateChanges.map((item, idx) => (
									<tr key={idx} className="gpmc__table-row--clickable" onClick={drillDown}>
										<td style={{ fontWeight: 600 }}>{item.name}</td>
										<td style={{ fontSize: 12, color: "var(--gpmc-muted)" }}>{item.project}</td>
										<td>
											<span className={`gpmc__status gpmc__status--${item.status === "已签署" ? "ok" : item.status === "评审中" ? "warn" : "danger"}`}>
												{item.status}
											</span>
										</td>
										<td>{item.changeType}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				</div>

				<div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">更改类别分布</div>
						</div>
						<div className="gpmc__panel-body">
							{techByChangeType.map((item) => (
								<div key={item.name} style={{ display: "flex", alignItems: "center", marginBottom: 10 }}>
									<span style={{ fontSize: 12, minWidth: 48, color: "var(--gpmc-muted)" }}>{item.name}</span>
									<div className="gpmc__progress" style={{ flex: 1, marginLeft: 8 }}>
										<div
											className="gpmc__progress-fill gpmc__progress-fill--primary"
											style={{ width: `${(item.value / maxChangeTypeValue) * 100}%`, backgroundColor: item.color }}
										/>
									</div>
									<span style={{ fontSize: 12, fontWeight: 600, minWidth: 24, textAlign: "right", marginLeft: 8 }}>{item.value}</span>
								</div>
							))}
						</div>
					</div>

					<div className="gpmc__panel">
						<div className="gpmc__panel-header">
							<div className="gpmc__panel-title">文件签署状态</div>
						</div>
						<div className="gpmc__panel-body">
							{techSignatureStatus.map((item) => (
								<div key={item.name} style={{ display: "flex", alignItems: "center", marginBottom: 10 }}>
									<span style={{ fontSize: 12, minWidth: 56, color: "var(--gpmc-muted)" }}>{item.name}</span>
									<div className="gpmc__progress" style={{ flex: 1, marginLeft: 8 }}>
										<div
											className="gpmc__progress-fill gpmc__progress-fill--primary"
											style={{ width: `${(item.value / maxSignatureValue) * 100}%`, backgroundColor: item.color }}
										/>
									</div>
									<span style={{ fontSize: 12, fontWeight: 600, minWidth: 24, textAlign: "right", marginLeft: 8 }}>{item.value}</span>
								</div>
							))}
						</div>
					</div>
				</div>
			</div>
		</div>
	);
}
