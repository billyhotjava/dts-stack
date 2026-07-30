/**
 * Schema 驱动的表单渲染器。
 *
 * 这是本原型要论证的核心机制：连接器用一份 schema 声明它的配置契约，
 * 前端据此渲染，后端据同一份校验。新增连接器不需要改前端代码。
 *
 * 对照现网：MySQL 走 DataSourceFormModal 的写死字段，HTTP API 走 8 个
 * Input.TextArea 让用户手写 JSON。两者都无法复用，也都没有服务端校验。
 */

(function () {

const el = (tag, attrs = {}, children = []) => {
	const node = document.createElement(tag);
	Object.entries(attrs).forEach(([k, v]) => {
		if (v === undefined || v === null || v === false) return;
		if (k === "class") node.className = v;
		else if (k === "html") node.innerHTML = v;
		else if (k.startsWith("on")) node.addEventListener(k.slice(2).toLowerCase(), v);
		else node.setAttribute(k, v === true ? "" : String(v));
	});
	(Array.isArray(children) ? children : [children]).forEach((c) => {
		if (c === null || c === undefined || c === false) return;
		node.appendChild(typeof c === "string" ? document.createTextNode(c) : c);
	});
	return node;
};

/** when: { fieldName: value } 或 { fieldName: [v1, v2] } */
const matchesWhen = (field, values) => {
	if (!field.when) return true;
	return Object.entries(field.when).every(([key, expected]) => {
		const actual = values[key];
		return Array.isArray(expected) ? expected.includes(actual) : actual === expected;
	});
};

const defaultsOf = (schema) => {
	const values = {};
	(schema || []).forEach((group) => {
		group.fields.forEach((f) => {
			if (f.default !== undefined) values[f.name] = f.default;
			else if (f.type === "kv") values[f.name] = [];
			else if (f.type === "switch") values[f.name] = false;
		});
	});
	return values;
};

function renderControl(field, values, onChange) {
	const value = values[field.name];
	const commit = (v) => {
		values[field.name] = v;
		onChange();
	};

	switch (field.type) {
		case "select":
			return el("select", {
				class: "ctl",
				onchange: (e) => commit(e.target.value),
			}, (field.options || []).map((o) =>
				el("option", { value: o.value, selected: o.value === value }, o.label),
			));

		case "switch": {
			const on = Boolean(value);
			return el("button", {
				type: "button",
				class: `switch${on ? " on" : ""}${field.danger && on ? " danger" : ""}`,
				onclick: () => commit(!on),
			}, el("span", { class: "knob" }));
		}

		case "number":
			return el("div", { class: "ctl-wrap" }, [
				el("input", {
					class: "ctl", type: "number", value: value ?? "",
					min: field.min, max: field.max,
					oninput: (e) => commit(e.target.value === "" ? undefined : Number(e.target.value)),
				}),
				field.suffix ? el("span", { class: "suffix" }, field.suffix) : null,
			]);

		case "password":
			return el("div", { class: "ctl-wrap" }, [
				el("input", {
					class: "ctl", type: "password", value: value ?? "",
					placeholder: field.placeholder || "",
					oninput: (e) => commit(e.target.value),
				}),
				el("span", { class: "chip chip-lock", title: "写入后由密钥服务加密保管，界面与导出均不回显" }, "🔒 密钥"),
			]);

		case "file":
			return el("div", { class: "filebox" }, [
				el("span", { class: "filebox-btn" }, "选择文件"),
				el("span", { class: "muted" }, value || "未选择"),
			]);

		case "kv":
			return renderKv(field, values, onChange);

		case "raw":
			return renderRaw(field, values, onChange);

		default:
			return el("input", {
				class: "ctl", type: "text", value: value ?? "",
				placeholder: field.placeholder || "",
				oninput: (e) => commit(e.target.value),
			});
	}
}

/** 键值对编辑器 —— 用来替代"扩展配置 JSON"这类兜底文本框 */
function renderKv(field, values, onChange) {
	const rows = Array.isArray(values[field.name]) ? values[field.name] : [];
	const box = el("div", { class: "kv" });

	rows.forEach((row, i) => {
		box.appendChild(el("div", { class: "kv-row" }, [
			el("input", {
				class: "ctl", placeholder: "键", value: row.k || "",
				oninput: (e) => { rows[i].k = e.target.value; },
			}),
			el("input", {
				class: "ctl", placeholder: "值", value: row.v || "",
				oninput: (e) => { rows[i].v = e.target.value; },
			}),
			el("button", {
				class: "icon-btn", type: "button", title: "删除",
				onclick: () => { rows.splice(i, 1); values[field.name] = rows; onChange(); },
			}, "×"),
		]));
	});

	box.appendChild(el("button", {
		class: "link-btn", type: "button",
		onclick: () => { rows.push({ k: "", v: "" }); values[field.name] = rows; onChange(); },
	}, "+ 添加一项"));

	return box;
}


/**
 * 受控逃生口。
 *
 * 对照现网 readerExtraConfig：那是无校验裸 JSON，且 mergeConfig 用 {...base, ...extra}
 * 让它静默覆盖同屏上方的「Reader 字段」「Reader 过滤条件」。这里做三件事：
 *   1. 平台已管的键直接拒绝
 *   2. 插件未知的键给出警告（不阻断，但要用户确认）
 *   3. 任何非空值都提示会记入变更记录
 */
const MANAGED_KEYS = ["table", "tables", "column", "where", "querySql", "connection", "jdbcUrl", "username", "password"];
const KNOWN_PLUGIN_KEYS = ["autoPk", "readMode", "haveKerberos", "kerberosKeytabFilePath", "kerberosPrincipal", "nullFormat", "encoding"];

function renderRaw(field, values, onChange) {
	const text = values[field.name] || "";
	const problems = [];
	let parsed = null;

	if (text.trim()) {
		try {
			parsed = JSON.parse(text);
			if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
				problems.push({ tone: "bad", msg: "必须是 JSON 对象" });
			} else {
				Object.keys(parsed).forEach((k) => {
					if (MANAGED_KEYS.includes(k)) {
						problems.push({ tone: "bad", msg: `拒绝：${k} 由平台管理，请改用上方对应字段` });
					} else if (!KNOWN_PLUGIN_KEYS.includes(k)) {
						problems.push({ tone: "warn", msg: `未知键：${k} —— 该插件文档中不存在，将原样下发` });
					}
				});
			}
		} catch {
			problems.push({ tone: "bad", msg: "JSON 解析失败" });
		}
	}

	const box = el("div", { class: "raw-box" }, [
		el("textarea", {
			class: "ctl mono", rows: 3, value: text,
			placeholder: '留空即可。示例：{"autoPk":true}',
			oninput: (e) => { values[field.name] = e.target.value; onChange(); },
		}),
	]);

	problems.forEach((p) => box.appendChild(
		el("div", { class: `raw-msg ${p.tone}` }, p.msg),
	));

	if (parsed && !problems.length) {
		box.appendChild(el("div", { class: "raw-msg warn" }, "生效后将记入接入变更记录，并在运行日志中标注为人工覆盖"));
	}

	return box;
}

/**
 * 渲染整份 schema。
 * @param {Array} schema  连接器配置契约
 * @param {Object} values 当前取值（会被就地更新）
 * @param {Function} onChange 任一字段变化后的回调（用于重渲染条件字段）
 */
function renderSchemaForm(schema, values, onChange) {
	const root = el("div", { class: "schema-form" });

	(schema || []).forEach((group) => {
		const visible = group.fields.filter((f) => matchesWhen(f, values));
		if (!visible.length) return;

		const body = el("div", { class: "form-grid" });
		visible.forEach((field) => {
			const item = el("div", {
				class: `form-item span-${field.span || 1}${field.type === "kv" || field.type === "raw" ? " span-3" : ""}`,
			}, [
				el("label", { class: "form-label" }, [
					field.label,
					field.required ? el("span", { class: "req" }, "*") : null,
				]),
				renderControl(field, values, onChange),
				field.hint ? el("div", { class: "form-hint" }, field.hint) : null,
			]);
			body.appendChild(item);
		});

		const isCollapsible = Boolean(group.collapsed);
		const section = el("section", { class: `form-group${isCollapsible ? " collapsible" : ""}` });
		const collapsedNow = isCollapsible && values.__collapsed?.[group.group] !== false;

		section.appendChild(el("div", {
			class: "form-group-head",
			onclick: isCollapsible
				? () => {
						values.__collapsed = values.__collapsed || {};
						values.__collapsed[group.group] = !collapsedNow ? true : false;
						onChange();
					}
				: undefined,
		}, [
			el("span", { class: "form-group-title" }, group.group),
			isCollapsible ? el("span", { class: "caret" }, collapsedNow ? "展开 ▾" : "收起 ▴") : null,
		]));

		if (!collapsedNow) section.appendChild(body);
		root.appendChild(section);
	});

	return root;
}

window.SchemaForm = { el, renderSchemaForm, defaultsOf, matchesWhen };

})();
