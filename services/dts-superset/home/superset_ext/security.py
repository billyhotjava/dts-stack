import logging
from typing import Any, Dict, Iterable, List, Optional

from flask_appbuilder.security.manager import AUTH_OID
from superset.security.manager import SupersetSecurityManager

LOG = logging.getLogger(__name__)


class CustomSsm(SupersetSecurityManager):
    """
    OIDC Security Manager to map Keycloak claims to Superset users/roles and stash extra attributes.
    """

    ROLE_MAP = {
        # legacy admin roles
        "SYSADMIN": "Admin",
        "AUTHADMIN": "Admin",
        "AUDITADMIN": "Alpha",
        "OPADMIN": "Alpha",
        "EMPLOYEE": "Gamma",
        # platform roles (ROLE_*)
        "ROLE_ADMIN": "Admin",
        "ROLE_OP_ADMIN": "Admin",
        "ROLE_INST_DATA_OWNER": "Alpha",
        "ROLE_INST_DATA_DEV": "Alpha",
        "ROLE_INST_DATA_VIEWER": "Gamma",
        "ROLE_INST_LEADER": "Alpha",
        "ROLE_DEPT_DATA_OWNER": "Alpha",
        "ROLE_DEPT_DATA_DEV": "Alpha",
        "ROLE_DEPT_DATA_VIEWER": "Gamma",
        "ROLE_DEPT_LEADER": "Alpha",
        "ROLE_EMPLOYEE": "Gamma",
    }
    EXCLUDED_ROLES = {"offline_access", "uma_authorization"}

    def oauth_user_info(self, provider: str, response: Dict[str, Any]) -> Dict[str, Any]:
        if provider != "oidc":
            return {}
        data = response or {}
        username = self._first(data, "preferred_username", "sub")
        email = self._first(data, "email")
        first_name = self._first(data, "given_name", "name", "preferred_username")
        last_name = self._first(data, "family_name")
        dept_code = self._first(data, "dept_code")
        person_level_raw = self._first(data, "person_security_level")
        raw_roles = self._roles_from_claims(data)
        roles = self._map_roles(raw_roles)

        person_level = self._parse_int(person_level_raw, default=0)
        max_data_security_level = 3 if person_level >= 1 else 2

        # dept scope:
        # - institute roles can view all departments
        # - others default to own department
        upper_raw_roles = {self._normalize_role(r) for r in raw_roles if r}
        scope_all_dept = self._has_institute_scope(upper_raw_roles)

        return {
            "username": username,
            "name": username or email,
            "email": email,
            "first_name": first_name or username,
            "last_name": last_name,
            "roles": roles,
            "extra": {
                "dept_code": dept_code,
                "person_security_level": person_level,
                "max_data_security_level": max_data_security_level,
                "scope_all_dept": scope_all_dept,
            },
        }

    def auth_user_oauth(self, userinfo: Dict[str, Any]) -> Optional[Any]:
        """
        Persist extra attributes (dept_code, person_security_level) to user.extra for RLS templates.
        """
        user = super().auth_user_oauth(userinfo)
        if not user:
            return None
        extra = userinfo.get("extra") or {}
        if not extra:
            return user
        changed = False
        current_extra = user.extra or {}
        for key, value in extra.items():
            if value and current_extra.get(key) != value:
                current_extra[key] = value
                changed = True
        if changed:
            user.extra = current_extra
            session = self.get_session
            try:
                session.commit()
            except Exception as exc:  # pragma: no cover - defensive
                LOG.warning("Failed to persist user extra attributes: %s", exc)
                session.rollback()
        return user

    def _roles_from_claims(self, data: Dict[str, Any]) -> List[str]:
        raw = []
        for key in ("roles", "role", "groups"):
            val = data.get(key)
            if isinstance(val, (list, tuple, set)):
                raw.extend(val)
        realm_roles = data.get("realm_access", {}).get("roles") if isinstance(data.get("realm_access"), dict) else []
        if isinstance(realm_roles, (list, tuple, set)):
            raw.extend(realm_roles)
        return [self._normalize_role(r) for r in raw if r]

    def _map_roles(self, roles: Iterable[str]) -> List[str]:
        mapped: List[str] = []
        for role in roles:
            if not role or role.lower() in self.EXCLUDED_ROLES or role.lower().startswith("default-roles-"):
                continue
            target = self.ROLE_MAP.get(role) or self.ROLE_MAP.get(role.upper()) or role
            if target not in mapped:
                mapped.append(target)
        if not mapped:
            default_role = self.auth_user_registration_role or "Gamma"
            mapped.append(default_role)
        return mapped

    def _first(self, data: Dict[str, Any], *keys: str) -> Optional[str]:
        for key in keys:
            value = data.get(key)
            if isinstance(value, str):
                val = value.strip()
                if val:
                    return val
        return None

    def _normalize_role(self, role: str) -> str:
        return str(role).strip().upper()

    def _parse_int(self, value: Optional[str], default: int = 0) -> int:
        if value is None:
            return default
        try:
            return int(str(value).strip())
        except Exception:
            return default

    def _has_institute_scope(self, roles: Iterable[str]) -> bool:
        inst_roles = {
            "ROLE_ADMIN",
            "ROLE_OP_ADMIN",
            "ROLE_INST_DATA_DEV",
            "ROLE_INST_DATA_OWNER",
            "ROLE_INST_LEADER",
        }
        return any(r in inst_roles for r in roles)


# Keep importable constant for config
AUTH_TYPE = AUTH_OID
