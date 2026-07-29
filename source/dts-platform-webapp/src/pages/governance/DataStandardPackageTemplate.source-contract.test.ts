import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const MODELING_RESOURCE = readFileSync(
	new URL(
		"../../../../dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingResource.java",
		import.meta.url,
	),
	"utf8",
);
const PLATFORM_API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");
const PACKAGE_ACTIONS = readFileSync(new URL("./StandardPackageActions.tsx", import.meta.url), "utf8");

test("metadata standard template endpoint ships a full data standard package", () => {
	assert.match(MODELING_RESOURCE, /data-standard-package-template\.zip/);
	assert.match(MODELING_RESOURCE, /01-business-terms\.csv/);
	assert.match(MODELING_RESOURCE, /02-data-elements\.csv/);
	assert.match(MODELING_RESOURCE, /03-reference-code-directories\.csv/);
	assert.match(MODELING_RESOURCE, /04-reference-code-items\.csv/);
	assert.match(MODELING_RESOURCE, /05-reference-code-mappings\.csv/);
	assert.match(MODELING_RESOURCE, /06-measurement-units\.csv/);
	assert.match(MODELING_RESOURCE, /README-data-standard-package\.txt/);

	assert.match(
		MODELING_RESOURCE,
		/term_code,term_name,aliases,definition,domain,owner_dept,owner,tags,version,status,version_notes/,
	);
	assert.match(
		MODELING_RESOURCE,
		/field_name_cn,field_name_en,data_type,data_length,data_precision,data_scale,nullable,domain,description,source_system,code_set,default_value,is_pk,security_level/,
	);
	assert.match(
		MODELING_RESOURCE,
		/code_type_id,code_type_code,code_type_name,std_level,biz_catalog,data_type,status,owner_dept,version/,
	);
	assert.match(MODELING_RESOURCE, /code_type_code,code_value,code_name,description,sort_num,parent_code,is_default/);
	assert.match(MODELING_RESOURCE, /code_type_code,source_system,source_code,standard_code/);
	assert.match(MODELING_RESOURCE, /code,name,symbol,quantity_kind,conversion_factor,base_unit_code,precision,status/);
	assert.match(MODELING_RESOURCE, /业务术语 -> 数据元 -> 公共码表 -> 计量单位 -> SQL\/dbt 字段落标/);
});

test("all standard owners share a real standard-package download action", () => {
	assert.match(PLATFORM_API, /downloadDataStandardPackageTemplate/);
	assert.match(PLATFORM_API, /\/modeling\/metadata-standards\/template/);
	assert.match(PLATFORM_API, /responseType:\s*"blob"/);

	assert.match(PACKAGE_ACTIONS, /downloadDataStandardPackageTemplate/);
	assert.match(PACKAGE_ACTIONS, /DownloadOutlined/);
	assert.match(PACKAGE_ACTIONS, /下载标准包模板/);
	assert.match(PACKAGE_ACTIONS, /data-standard-package-template\.zip/);
	assert.match(PACKAGE_ACTIONS, /governance-\$\{source\}-template-download/);
});

test("standard owners route uploads into the shared package wizard", () => {
	assert.match(PACKAGE_ACTIONS, /导入标准包/);
	assert.match(PACKAGE_ACTIONS, /governance-\$\{source\}-standard-package-import/);
	assert.match(PACKAGE_ACTIONS, /buildStandardPackageImportRoute\(searchParams, source\)/);
	assert.doesNotMatch(PACKAGE_ACTIONS, /importMetadataStandards/);
	assert.doesNotMatch(PACKAGE_ACTIONS, /Upload\.|<Upload/);
});
