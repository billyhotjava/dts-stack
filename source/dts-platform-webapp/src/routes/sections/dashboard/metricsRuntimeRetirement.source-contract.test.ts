import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const APP_COMPOSE = readFileSync(new URL("../../../../../../docker-compose-app.yml", import.meta.url), "utf8");
const LEGACY_COMPOSE = readFileSync(new URL("../../../../../../docker-compose.legacy.yml", import.meta.url), "utf8");
const INIT_SH = readFileSync(new URL("../../../../../../init.sh", import.meta.url), "utf8");
const BUILD_SH = readFileSync(new URL("../../../../../../builds/dts-build.sh", import.meta.url), "utf8");
const IMGVERSION = readFileSync(new URL("../../../../../../imgversion.conf", import.meta.url), "utf8");
const IMGVERSION_SOURCE = readFileSync(new URL("../../../../../../imgversion.dts-source.conf", import.meta.url), "utf8");
const PLATFORM_APPLICATION_YML = readFileSync(
	new URL("../../../../../dts-platform/src/main/resources/config/application.yml", import.meta.url),
	"utf8",
);

const sliceBetween = (source: string, startMarker: string, endMarker: string) => {
	const start = source.indexOf(startMarker);
	const end = source.indexOf(endMarker, start + startMarker.length);
	assert.notEqual(start, -1, `missing start marker: ${startMarker}`);
	assert.notEqual(end, -1, `missing end marker: ${endMarker}`);
	return source.slice(start, end);
};

test("default app compose no longer starts or routes dts-metrics", () => {
	assert.doesNotMatch(APP_COMPOSE, /^\s{2}dts-metrics:/m);
	assert.doesNotMatch(APP_COMPOSE, /dts-metrics-api|dts-metrics-ui/);
	assert.doesNotMatch(APP_COMPOSE, /IMAGE_DTS_METRICS/);
	assert.doesNotMatch(APP_COMPOSE, /PG_DB_METRICS|PG_USER_METRICS|PG_PWD_METRICS/);
	assert.doesNotMatch(APP_COMPOSE, /DTS_INBOUND_FROM_METRICS|DTS_METRICS_TO_PLATFORM/);
	assert.doesNotMatch(APP_COMPOSE, /DTS_METRICS_API_BASE_PATH|DTS_METRICS_SERVICE_NAME/);
	assert.doesNotMatch(APP_COMPOSE, /!PathPrefix\(`\/metrics`\)/);
});

test("legacy compose keeps the explicit dts-metrics rollback surface", () => {
	assert.match(LEGACY_COMPOSE, /^\s{2}dts-metrics:/m);
	assert.match(LEGACY_COMPOSE, /dts-metrics-api|dts-metrics-ui/);
	assert.match(LEGACY_COMPOSE, /DTS_METRICS_TO_PLATFORM/);
	assert.match(LEGACY_COMPOSE, /DTS_PLATFORM_INBOUND_TRUSTED_SERVICES/);
});

test("init default env template does not emit dts-metrics variables", () => {
	const defaultEnvTemplate = sliceBetween(INIT_SH, "cat > .env <<EOF", "Legacy dts-metrics");
	assert.match(defaultEnvTemplate, /DTS_LEGACY_METRICS_ENABLED=\$\{DTS_LEGACY_METRICS_ENABLED\}/);
	for (const key of [
		"PG_DB_METRICS",
		"PG_USER_METRICS",
		"PG_PWD_METRICS",
		"DTS_INBOUND_FROM_METRICS",
		"DTS_METRICS_TO_PLATFORM",
		"DTS_METRICS_SERVICE_NAME",
		"DTS_METRICS_API_BASE_PATH",
		"IMAGE_DTS_METRICS",
	]) {
		assert.doesNotMatch(defaultEnvTemplate, new RegExp(`^${key}=`, "m"));
	}
	assert.match(INIT_SH, /DTS_LEGACY_METRICS_ENABLED.*LEGACY_STACK/);
	assert.match(INIT_SH, /Legacy dts-metrics \(disabled in default app stack\)/);
});

test("default image version files exclude the dts-metrics image", () => {
	assert.doesNotMatch(IMGVERSION, /^IMAGE_DTS_METRICS=/m);
	assert.doesNotMatch(IMGVERSION_SOURCE, /^IMAGE_DTS_METRICS=/m);
	assert.match(IMGVERSION_SOURCE, /legacy-only/);
});

test("default build-all path excludes dts-metrics while explicit and legacy paths remain", () => {
	const normalAll = sliceBetween(BUILD_SH, "build_all_normal() {", "build_all_legacy() {");
	const legacyAll = sliceBetween(BUILD_SH, "build_all_legacy() {", "build_single_image() {");
	const singleImage = sliceBetween(BUILD_SH, "build_single_image() {", "pack_deployment() {");

	assert.doesNotMatch(normalAll, /dts-metrics|build_metrics_webapp|IMAGE_DTS_METRICS/);
	assert.match(legacyAll, /dts-metrics/);
	assert.match(singleImage, /dts-metrics/);
	assert.match(BUILD_SH, /explicit legacy metrics rollback image/);
});

test("platform inbound defaults do not trust dts-metrics unless legacy compose opts in", () => {
	assert.doesNotMatch(PLATFORM_APPLICATION_YML, /dts-admin,dts-ingestion,dts-airflow,dts-analytics,dts-metrics/);
	assert.doesNotMatch(PLATFORM_APPLICATION_YML, /^\s+dts-metrics:/m);
	assert.match(LEGACY_COMPOSE, /dts-admin,dts-ingestion,dts-airflow,dts-analytics,dts-metrics/);
});
