type Column = { key: string; label: string };

export function exportCsv(
	columns: Column[],
	rows: Array<Record<string, unknown>>,
	filename: string,
) {
	if (rows.length === 0) return;
	const header = columns.map((c) => c.label).join(",");
	const body = rows.map((row) =>
		columns.map((c) => `"${String(row[c.key] ?? "").replace(/"/g, '""')}"`).join(","),
	);
	const bom = "\uFEFF";
	const csv = bom + [header, ...body].join("\n");
	const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
	const url = URL.createObjectURL(blob);
	const a = document.createElement("a");
	a.href = url;
	a.download = filename.endsWith(".csv") ? filename : `${filename}.csv`;
	a.click();
	URL.revokeObjectURL(url);
}

/**
 * Export an SVG element to PNG via canvas.
 * Chrome 95 compatible (no OffscreenCanvas dependency).
 */
export function exportChartPng(svgElement: SVGSVGElement | null, filename: string) {
	if (!svgElement) return;

	const svgData = new XMLSerializer().serializeToString(svgElement);
	const svgBlob = new Blob([svgData], { type: "image/svg+xml;charset=utf-8" });
	const url = URL.createObjectURL(svgBlob);

	const img = new Image();
	img.onload = () => {
		const canvas = document.createElement("canvas");
		const scale = 2; // retina
		canvas.width = img.width * scale;
		canvas.height = img.height * scale;
		const ctx = canvas.getContext("2d");
		if (!ctx) return;
		ctx.fillStyle = "#ffffff";
		ctx.fillRect(0, 0, canvas.width, canvas.height);
		ctx.scale(scale, scale);
		ctx.drawImage(img, 0, 0);

		canvas.toBlob((blob) => {
			if (!blob) return;
			const pngUrl = URL.createObjectURL(blob);
			const a = document.createElement("a");
			a.href = pngUrl;
			a.download = filename.endsWith(".png") ? filename : `${filename}.png`;
			a.click();
			URL.revokeObjectURL(pngUrl);
		}, "image/png");

		URL.revokeObjectURL(url);
	};
	img.src = url;
}
