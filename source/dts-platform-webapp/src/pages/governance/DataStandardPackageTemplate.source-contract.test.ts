import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const MODELING_RESOURCE = readFileSync(
	new URL("../../../../dts-platform/src/main/java/com/yuzhi/dts/platform/web/rest/ModelingResource.java", import.meta.url),
	"utf8",
);
const PLATFORM_API = readFileSync(new URL("../../api/platformApi.ts", import.meta.url), "utf8");
const ELEMENTS_PAGE = readFileSync(new URL("./ElementsPage.tsx", import.meta.url), "utf8");

test("metadata standard template endpoint ships a full data standard package", () => {
	assert.match(MODELING_RESOURCE, /data-standard-package-template\.zip/);
	assert.match(MODELING_RESOURCE, /01-business-terms\.csv/);
	assert.match(MODELING_RESOURCE, /02-data-elements\.csv/);
	assert.match(MODELING_RESOURCE, /03-reference-code-directories\.csv/);
	assert.match(MODELING_RESOURCE, /04-reference-code-items\.csv/);
	assert.match(MODELING_RESOURCE, /05-reference-code-mappings\.csv/);
	assert.match(MODELING_RESOURCE, /README-data-standard-package\.txt/);

	assert.match(MODELING_RESOURCE, /term_code,term_name,aliases,definition,domain,owner_dept,owner,tags,version,status,version_notes/);
	assert.match(MODELING_RESOURCE, /field_name_cn,field_name_en,data_type,data_length,data_precision,data_scale,nullable,domain,description,source_system,code_set,default_value,is_pk,security_level/);
	assert.match(MODELING_RESOURCE, /code_type_id,code_type_code,code_type_name,std_level,biz_catalog,data_type,status,owner_dept,version/);
	assert.match(MODELING_RESOURCE, /code_type_code,code_value,code_name,description,sort_num,parent_code,is_default/);
	assert.match(MODELING_RESOURCE, /code_type_code,source_system,source_code,standard_code/);
	assert.match(MODELING_RESOURCE, /业务术语 -> 数据元 -> 公共码表 -> SQL\/dbt 字段落标/);
});

test("data element page exposes the standard package download as a real blob action", () => {
	assert.match(PLATFORM_API, /downloadDataStandardPackageTemplate/);
	assert.match(PLATFORM_API, /\/modeling\/metadata-standards\/template/);
	assert.match(PLATFORM_API, /responseType:\s*"blob"/);

	assert.match(ELEMENTS_PAGE, /downloadDataStandardPackageTemplate/);
	assert.match(ELEMENTS_PAGE, /DownloadOutlined/);
	assert.match(ELEMENTS_PAGE, /下载标准包模板/);
	assert.match(ELEMENTS_PAGE, /data-standard-package-template\.zip/);
	assert.match(ELEMENTS_PAGE, /governance-elements-template-download/);
});

test("data element page routes imports into the package wizard instead of single CSV upload", () => {
	assert.match(ELEMENTS_PAGE, /导入标准包/);
	assert.match(ELEMENTS_PAGE, /governance-elements-standard-package-import/);
	assert.match(ELEMENTS_PAGE, /navigate\("\/foundation\/standard-package\?from=elements"\)/);
	assert.doesNotMatch(ELEMENTS_PAGE, /importMetadataStandards/);
	assert.doesNotMatch(ELEMENTS_PAGE, /Upload\.|<Upload/);
});
