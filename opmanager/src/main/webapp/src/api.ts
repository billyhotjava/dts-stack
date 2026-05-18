import type {
  ConfigApplyAction,
  ConfigApplyResult,
  ConfigPrecheckResponse,
  DockerContainersResponse,
  JobEvent,
  PackageRegistration,
  RuntimeStatus,
  UpgradeJob,
  UploadResult,
  WorkspaceOperationResult,
  WorkspaceStatus
} from "./types";

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: options?.body instanceof FormData ? undefined : { "Content-Type": "application/json" },
    ...options
  });
  if (!response.ok) {
    let message = response.statusText;
    try {
      const error = (await response.json()) as { message?: string };
      message = error.message || message;
    } catch {
      // Keep the HTTP status text when the response is not JSON.
    }
    throw new Error(message);
  }
  return (await response.json()) as T;
}

export function getRuntime(): Promise<RuntimeStatus> {
  return request<RuntimeStatus>("/api/opmanager/runtime");
}

export function listPackages(): Promise<PackageRegistration[]> {
  return request<PackageRegistration[]>("/api/opmanager/packages");
}

export function registerPackagePath(path: string): Promise<PackageRegistration> {
  return request<PackageRegistration>("/api/opmanager/packages/register-path", {
    method: "POST",
    body: JSON.stringify({ path })
  });
}

export function uploadPackage(file: File): Promise<UploadResult> {
  const form = new FormData();
  form.append("file", file);
  return request<UploadResult>("/api/opmanager/packages/upload", {
    method: "POST",
    body: form
  });
}

export function listJobs(): Promise<UpgradeJob[]> {
  return request<UpgradeJob[]>("/api/opmanager/jobs");
}

export function createPlan(packageRegistrationId: string, note: string): Promise<UpgradeJob> {
  return request<UpgradeJob>("/api/opmanager/jobs/plan", {
    method: "POST",
    body: JSON.stringify({ packageRegistrationId, note })
  });
}

export function listJobEvents(id: string): Promise<JobEvent[]> {
  return request<JobEvent[]>(`/api/opmanager/jobs/${encodeURIComponent(id)}/events`);
}

export function listContainers(): Promise<DockerContainersResponse> {
  return request<DockerContainersResponse>("/api/opmanager/containers");
}

export function precheckConfig(packageRegistrationId: string): Promise<ConfigPrecheckResponse> {
  return request<ConfigPrecheckResponse>("/api/opmanager/config/precheck", {
    method: "POST",
    body: JSON.stringify({ packageRegistrationId })
  });
}

export function applyConfigAction(packageRegistrationId: string, path: string, action: ConfigApplyAction): Promise<ConfigApplyResult> {
  return request<ConfigApplyResult>("/api/opmanager/config/apply", {
    method: "POST",
    body: JSON.stringify({ packageRegistrationId, path, action })
  });
}

export function getWorkspaceStatus(): Promise<WorkspaceStatus> {
  return request<WorkspaceStatus>("/api/opmanager/workspace");
}

export function loadWorkspaceImages(): Promise<WorkspaceOperationResult> {
  return request<WorkspaceOperationResult>("/api/opmanager/workspace/load-images", {
    method: "POST"
  });
}

export function recreateWorkspaceContainers(): Promise<WorkspaceOperationResult> {
  return request<WorkspaceOperationResult>("/api/opmanager/workspace/recreate-containers", {
    method: "POST"
  });
}
