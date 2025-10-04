export interface KeycloakUser {
  id?: string;
  username?: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  fullName?: string;
  enabled?: boolean;
  emailVerified?: boolean;
  attributes?: Record<string, string[]>;
  groups?: string[];
  createdTimestamp?: number;
  realmRoles?: string[];
  clientRoles?: Record<string, string[]>;
}

export interface KeycloakRole {
  id?: string;
  name: string;
  description?: string;
  composite?: boolean;
  clientRole?: boolean;
  attributes?: Record<string, string>;
}

export interface KeycloakGroup {
  id?: string;
  name?: string;
  path?: string;
}

export interface UserProfileConfig {
  [key: string]: any;
}

export interface CreateUserRequest {
  username: string;
  email?: string;
  firstName?: string;
  fullName?: string;
  enabled?: boolean;
  emailVerified?: boolean;
  attributes?: Record<string, string[]>;
  groups?: string[];
}

export interface UpdateUserRequest extends CreateUserRequest {
  id: string;
}

export interface ApprovalRequest {
  id: number;
  status: string;
  reason?: string;
  items?: any[];
}
