export type RuntimeStatus = {
  osName: string;
  osArch: string;
  javaVersion: string;
  dataDir: string;
  targetStackDir: string;
  dockerEnabled: boolean;
  dockerAvailable: boolean;
  dockerVersion: string;
  dockerMessage: string;
  composeAvailable: boolean;
  composeVersion: string;
  composeMessage: string;
  portainerUrl: string;
};

export type PackageValidationResult = {
  valid: boolean;
  packageId: string | null;
  product: string | null;
  version: string | null;
  targetArch: string | null;
  sourcePath: string;
  messages: string[];
};

export type PackageRegistration = {
  id: string;
  sourcePath: string;
  registeredAt: string;
  validation: PackageValidationResult;
};

export type UpgradeJob = {
  id: string;
  packageRegistrationId: string;
  packageId: string;
  version: string;
  state: string;
  createdAt: string;
  updatedAt: string;
};

export type JobEvent = {
  jobId: string;
  timestamp: string;
  state: string;
  message: string;
};

export type DockerContainer = {
  id: string;
  name: string;
  image: string;
  state: string;
  status: string;
};

export type DockerContainersResponse = {
  available: boolean;
  message: string;
  containers: DockerContainer[];
};

export type UploadResult = {
  fileName: string;
  storedPath: string;
  size: number;
  message: string;
};

export type ConfigApplyAction = "KEEP_LOCAL" | "MERGE_ENV_ADD_KEYS" | "WRITE_PACKAGE_COPY" | "USE_PACKAGE";

export type ConfigFileStatus = "UNCHANGED" | "MODIFIED" | "LOCAL_ONLY" | "PACKAGE_ONLY" | "MISSING";

export type ConfigRisk = "LOW" | "MEDIUM" | "HIGH";

export type ConfigCategory = "ENV" | "COMPOSE" | "MDM" | "OTHER";

export type ConfigFileReview = {
  path: string;
  category: ConfigCategory;
  status: ConfigFileStatus;
  risk: ConfigRisk;
  localExists: boolean;
  packageExists: boolean;
  localSize: number;
  packageSize: number;
  localLines: string[];
  packageLines: string[];
  contentOmitted: boolean;
  message: string;
  allowedActions: ConfigApplyAction[];
};

export type ConfigPrecheckResponse = {
  packageRegistrationId: string;
  packageId: string;
  targetStackDir: string;
  packageStackDir: string;
  total: number;
  changed: number;
  highRisk: number;
  files: ConfigFileReview[];
};

export type ConfigApplyResult = {
  changed: boolean;
  path: string;
  action: ConfigApplyAction;
  message: string;
  backupPath: string;
  writtenPath: string;
};

export type WorkspaceImage = {
  fileName: string;
  path: string;
  size: number;
};

export type WorkspaceStatus = {
  packageRoot: string;
  imagesDir: string;
  stackDir: string;
  miscDir: string;
  packageRootExists: boolean;
  imagesDirExists: boolean;
  stackDirExists: boolean;
  miscDirExists: boolean;
  images: WorkspaceImage[];
};

export type WorkspaceCommandOutput = {
  command: string[];
  success: boolean;
  message: string;
};

export type WorkspaceOperationResult = {
  success: boolean;
  message: string;
  commands: WorkspaceCommandOutput[];
};
