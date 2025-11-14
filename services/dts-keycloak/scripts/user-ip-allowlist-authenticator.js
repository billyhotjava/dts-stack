/**
 * Keycloak Script Authenticator: Per-user IP allowlist
 *
 * Purpose
 *  - Enforce login source IP for specific administrative users.
 *  - Minimal blast radius: only applies to the configured usernames
 *    (default: sysadmin, authadmin, auditadmin). Other users pass through.
 *
 * How it works
 *  - Reads client IP from X-Forwarded-For (first item) or remoteAddr.
 *  - For protected users, reads user attributes:
 *      - "allowed-ip" (single value)
 *      - "allowed-ips" (comma-separated list)
 *  - If no allowed IP configured for a protected user, deny.
 *  - Accepts exact IPv4 string matches (e.g., 203.0.113.5). CIDR is not
 *    validated in this minimal version; keep values as plain IPs.
 */

var AuthenticationFlowError = Java.type("org.keycloak.authentication.AuthenticationFlowError");

// Customize here if needed
var PROTECTED_USERNAMES = {
  "sysadmin": true,
  "authadmin": true,
  "auditadmin": true
};
var SINGLE_ATTR_KEY = "allowed-ip";
var MULTI_ATTR_KEY = "allowed-ips"; // comma-separated

function getClientIp(context) {
  try {
    var httpReq = context.getHttpRequest();
    var headers = httpReq.getHttpHeaders();
    var xff = headers.getRequestHeader("X-Forwarded-For");
    if (xff && !xff.isEmpty()) {
      var first = String(xff.get(0));
      if (first) {
        return first.split(",")[0].trim();
      }
    }
  } catch (e) {}
  try {
    return context.getSession().getContext().getConnection().getRemoteAddr();
  } catch (e2) {}
  return "";
}

function listAllowedIps(user) {
  var ips = [];
  try {
    var a = user.getFirstAttribute(SINGLE_ATTR_KEY);
    if (a) ips.push(String(a).trim());
  } catch (e) {}
  try {
    var b = user.getFirstAttribute(MULTI_ATTR_KEY);
    if (b) {
      String(b).split(",").forEach(function (s) {
        var v = String(s).trim();
        if (v) ips.push(v);
      });
    }
  } catch (e2) {}
  // de-dup and keep non-empty
  var seen = {};
  var out = [];
  for (var i = 0; i < ips.length; i++) {
    var ip = ips[i];
    if (!ip) continue;
    if (seen[ip]) continue;
    seen[ip] = true;
    out.push(ip);
  }
  return out;
}

function authenticate(context) {
  var user = context.getUser();
  if (user == null) {
    context.attempted();
    return;
  }
  var username = String(user.getUsername() || "");
  if (!PROTECTED_USERNAMES[username]) {
    context.success();
    return;
  }

  var allowed = listAllowedIps(user);
  if (!allowed || allowed.length === 0) {
    // No allowlist set for a protected user -> deny by policy
    context.failure(AuthenticationFlowError.ACCESS_DENIED);
    return;
  }

  var clientIp = getClientIp(context);
  var ok = false;
  for (var i = 0; i < allowed.length; i++) {
    if (clientIp === allowed[i]) { ok = true; break; }
  }

  if (ok) context.success();
  else context.failure(AuthenticationFlowError.ACCESS_DENIED);
}

// Optional stubs for completeness in the Script Authenticator lifecycle
function requiresUser() { return true; }
function configuredFor(context) { return true; }
function setRequiredActions(context) {}
function close() {}

