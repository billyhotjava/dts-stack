import assert from "node:assert/strict";
import test from "node:test";
import { buildTransformCreateDraftPayload } from "./transformCreateDraft.helpers";

test("buildTransformCreateDraftPayload builds manual-selection database draft payload", () => {
	const payload = buildTransformCreateDraftPayload({
		name: "pg_sync_task",
		description: "sync pg tables",
		owner: "opadmin",
		isFileDraft: false,
		sourceDataSourceId: "source-1",
		resolvedReaderType: "postgresqlreader",
		readerConfig: { jdbcUrl: "jdbc:postgresql://pg/demo" },
		writerConfig: { writeMode: "insert", table: ["ods_demo"] },
		syncMode: "full_refresh",
		syncSchedule: { type: "manual" },
		syncPrefix: "ods_",
		selectionMode: "manual",
		includeTables: ["public.demo"],
		excludeTables: ["ignored"],
		readerSchema: "public",
		readerTablePattern: "demo%",
		airflowEnabled: true,
		dbtModelSelector: "model:ods_demo",
		dbtDagSelector: "tag:etl",
		jobConfig: { speed: { channel: 1 } },
		governanceSyncFields: { maxConcurrentRuns: 2 },
	});

	assert.equal(payload.draft, true);
	assert.equal(payload.name, "pg_sync_task");
	assert.equal(payload.description, "sync pg tables");
	assert.equal(payload.owner, "opadmin");
	assert.deepEqual(payload.source, {
		dataSourceId: "source-1",
		type: "postgresqlreader",
		config: { jdbcUrl: "jdbc:postgresql://pg/demo" },
	});
	assert.deepEqual(payload.sync, {
		mode: "full_refresh",
		schedule: { type: "manual" },
		prefix: "ods_",
		incrementalColumn: undefined,
		incrementalType: undefined,
		initialWatermark: undefined,
		maxConcurrentRuns: 2,
	});
	assert.deepEqual(payload.streams, {
		selection: "manual",
		include: ["public.demo"],
		exclude: undefined,
		schema: "public",
		tablePattern: "demo%",
	});
	assert.deepEqual(payload.airflow, { enabled: true });
	assert.deepEqual(payload.dbt, { modelSelector: "model:ods_demo", dagSelector: "tag:etl" });
	assert.deepEqual(payload.jobConfig, { speed: { channel: 1 } });
	assert.deepEqual(payload.destination, {
		usePlatformDefault: true,
		config: { writeMode: "insert", table: ["ods_demo"] },
	});
});

test("buildTransformCreateDraftPayload omits destination and source datasource for file drafts", () => {
	const payload = buildTransformCreateDraftPayload({
		name: "excel_sync_task",
		owner: "opadmin",
		isFileDraft: true,
		sourceDataSourceId: "ignored",
		resolvedReaderType: "txtfilereader",
		readerConfig: { _filePath: "/tmp/demo.csv" },
		syncMode: "incremental",
		syncSchedule: { type: "cron", cron: "0 0 * * *" },
		selectionMode: "all",
		excludeTables: ["ods_old"],
		airflowEnabled: false,
	});

	assert.deepEqual(payload.source, {
		dataSourceId: undefined,
		type: "txtfilereader",
		config: { _filePath: "/tmp/demo.csv" },
	});
	assert.deepEqual(payload.streams, {
		selection: "all",
		include: undefined,
		exclude: ["ods_old"],
		schema: undefined,
		tablePattern: undefined,
	});
	assert.equal("destination" in payload, false);
	assert.deepEqual(payload.airflow, { enabled: false });
	assert.deepEqual(payload.dbt, { modelSelector: undefined, dagSelector: undefined });
});
