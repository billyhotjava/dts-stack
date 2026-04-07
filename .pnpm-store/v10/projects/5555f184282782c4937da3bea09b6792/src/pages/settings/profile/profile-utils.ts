import type { KeycloakUser } from "#/keycloak";

type AttributeMap = Record<string, string[]> | undefined;

const ATTRIBUTE_NAME_KEYS = [
  "fullName",
  "fullname",
  "displayName",
  "nameZh",
  "name_cn",
  "name",
  "full_name",
];

const normalize = (value: string | null | undefined): string => {
  if (!value) return "";
  return value.trim().replace(/\s+/g, " ");
};

const composeName = (first?: string | null, last?: string | null): string => {
  const parts = [first, last].map(normalize).filter(Boolean);
  return parts.join(" ");
};

type ResolveProfileNameParams = {
  detail?: KeycloakUser | null;
  storeFullName?: string | null;
  storeFirstName?: string | null;
  username?: string | null;
  fallbackName?: string | null;
  attributeSources?: AttributeMap[];
  pickAttributeValue: (attributes: AttributeMap, keys: string[]) => string;
};

export function resolveProfileName(params: ResolveProfileNameParams): string {
  const {
    detail,
    storeFullName,
    storeFirstName,
    username,
    fallbackName,
    attributeSources,
    pickAttributeValue,
  } = params;

  const normalizedUsername = normalize(username ?? "");
  const usernameLower = normalizedUsername.toLowerCase();

  const attributeCandidates =
    attributeSources?.reduce<string[]>((acc, source) => {
      if (!source) return acc;
      const value = pickAttributeValue(source, ATTRIBUTE_NAME_KEYS);
      if (value) {
        acc.push(value);
      }
      return acc;
    }, []) ?? [];

  const composedDetailName = composeName(detail?.firstName, detail?.lastName);
  const candidates = [
    detail?.fullName,
    ...attributeCandidates,
    storeFullName,
    composedDetailName,
    detail?.firstName,
    storeFirstName,
    fallbackName,
  ];

  const seen = new Set<string>();
  for (const candidate of candidates) {
    const normalized = normalize(candidate);
    if (!normalized) continue;
    const lowered = normalized.toLowerCase();
    if (lowered && lowered === usernameLower) continue;
    if (seen.has(lowered)) continue;
    seen.add(lowered);
    return normalized;
  }

  return normalizedUsername || "-";
}
