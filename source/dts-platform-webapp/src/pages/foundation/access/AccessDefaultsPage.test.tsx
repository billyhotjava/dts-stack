// @vitest-environment jsdom
import assert from "node:assert/strict";
import { describe, it } from "vitest";
import type {
	ApiConnectorContractDTO,
	DefaultDestinationStatus,
	IngestionAccessDefaultPolicyDTO,
	IngestionConnectorCapabilityDTO,
} from "../../../api/ingestion";
import {
	type AccessDefaultsDependencies,
	isExplicitlyEnabled,
	loadAccessDefaultsSnapshot,
	redactSensitiveConfiguration,
} from "./AccessDefaultsPage";

const destination: DefaultDestinationStatus = {
	available: true,
	writerTypeReady: true,
	writerConfigReady: true,
	destinationName: "数仓（biadmin）",
	writerType: "postgresqlwriter",
	dataSourceId: "lake-1",
};

const policy: IngestionAccessDefaultPolicyDTO = {
	policyKey: "GLOBAL",
	version: 3,
	status: "ACTIVE",
	defaults: { channel: 2, apiToken: "must-not-leak" },
	checksum: "policy-checksum",
	activatedAt: "2026-07-31T00:00:00Z",
};

const capabilities: IngestionConnectorCapabilityDTO[] = [
	{
		connectorType: "mysql",
		connectorVersion: "8.0",
		capabilities: ["SCHEMA", "INCREMENTAL"],
		constraints: { maxTables: 100, accessToken: "must-not-leak" },
		enabled: true,
	},
];

const apiContract: ApiConnectorContractDTO = {
	contractVersion: "1",
	connectorType: "http",
	sourceTypes: ["api"],
	defaultReaderType: "httpreader",
	syncModes: ["full", "incremental"],
	authProviders: [],
};

const dependencies = (overrides: Partial<AccessDefaultsDependencies> = {}): AccessDefaultsDependencies => ({
	getAccessDefaultPolicy: async () => policy,
	getDefaultDestinationStatus: async () => destination,
	getConnectorCapabilities: async () => capabilities,
	getApiConnectorContract: async () => apiContract,
	...overrides,
});

describe("AccessDefaultsPage data contract", () => {
	it("loads one read-only snapshot from the versioned policy and runtime contracts", async () => {
		const snapshot = await loadAccessDefaultsSnapshot(dependencies());

		assert.equal(snapshot.policy.version, 3);
		assert.equal(snapshot.policy.defaults.apiToken, "[已隐藏]");
		assert.deepEqual(snapshot.destination, destination);
		assert.equal(snapshot.capabilities.length, 1);
		assert.deepEqual(snapshot.apiContract, apiContract);
		assert.equal(snapshot.capabilities[0].constraints?.accessToken, "[已隐藏]");
	});

	it("fails closed instead of showing a partial snapshot when a contract request fails", async () => {
		await assert.rejects(
			loadAccessDefaultsSnapshot(
				dependencies({
					getConnectorCapabilities: async () => {
						throw new Error("capability service unavailable");
					},
				}),
			),
			/capability service unavailable/,
		);
	});

	it("redacts nested credential values without mutating the API response", () => {
		const input = {
			endpoint: "https://reader:plain-password@example.test/orders?api_key=plain-key&page=1",
			jdbcUrl: "jdbc:mysql://reader:db-password@db.test/demo?token=plain-token",
			username: "reader",
			credentials: {
				clientSecret: "secret-value",
				headers: [{ Authorization: "Bearer token" }],
			},
			notes: [
				"Authorization: Bearer free-token",
				"credential: raw-credential",
				"client_secret=raw-secret",
				"accessKey=raw-access-key",
			],
		};

		const redacted = redactSensitiveConfiguration(input);

		assert.deepEqual(redacted, {
			endpoint: "https://[已隐藏]@example.test/orders?api_key=[已隐藏]&page=1",
			jdbcUrl: "jdbc:mysql://[已隐藏]@db.test/demo?token=[已隐藏]",
			username: "reader",
			credentials: "[已隐藏]",
			notes: [
				"Authorization: [已隐藏]",
				"credential: [已隐藏]",
				"client_secret=[已隐藏]",
				"accessKey=[已隐藏]",
			],
		});
		assert.equal(input.credentials.clientSecret, "secret-value");
	});

	it("treats only an explicit enabled flag as ready", () => {
		assert.equal(isExplicitlyEnabled(true), true);
		assert.equal(isExplicitlyEnabled(false), false);
		assert.equal(isExplicitlyEnabled(undefined), false);
		assert.equal(isExplicitlyEnabled("true"), false);
	});

	it("rejects malformed capability entries", async () => {
		await assert.rejects(
			loadAccessDefaultsSnapshot(
				dependencies({
					getConnectorCapabilities: async () => [{ connectorType: "mysql" } as IngestionConnectorCapabilityDTO],
				}),
			),
			/默认配置条目无效/,
		);
	});

	it("rejects malformed nested API connector contract arrays", async () => {
		await assert.rejects(
			loadAccessDefaultsSnapshot(
				dependencies({
					getApiConnectorContract: async () =>
						({ sourceTypes: "api", syncModes: {}, authProviders: [{ id: "basic" }] }) as unknown as ApiConnectorContractDTO,
				}),
			),
			/API 接入契约响应无效/,
		);
	});
});
