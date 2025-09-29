#!/usr/bin/env bash
set -euo pipefail

# Generate a CA-signed server certificate with a full chain (server + CA) and a PKCS#12 for Java.
# Outputs in this directory:
# - server.key (PEM private key)
# - server.crt (PEM full chain: server + CA)
# - ca.crt     (PEM CA certificate)
# - server.p12 (PKCS#12 with key + chain; password P12_PASSWORD or 'changeit')

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${SCRIPT_DIR}"
BASE_DOMAIN="${BASE_DOMAIN:-dts.local}"
SUBJECT_O="${SUBJECT_O:-DTS}"
P12_PASSWORD="${P12_PASSWORD:-changeit}"

mkdir -p "${CERT_DIR}"

log() { echo "[certs] $*"; }

have_valid_existing() {
  # Must have cert and key
  [[ -f "${CERT_DIR}/server.crt" && -f "${CERT_DIR}/server.key" ]] || return 1
  # SAN must include *.BASE_DOMAIN
  openssl x509 -in "${CERT_DIR}/server.crt" -noout -ext subjectAltName 2>/dev/null | \
    grep -F "DNS:*.${BASE_DOMAIN}" >/dev/null || return 1
  # Key must be readable and modulus must match
  openssl pkey -in "${CERT_DIR}/server.key" -noout >/dev/null 2>&1 || return 1
  local cert_mod key_mod
  cert_mod=$(openssl x509 -in "${CERT_DIR}/server.crt" -noout -modulus 2>/dev/null || true)
  key_mod=$(openssl rsa  -in "${CERT_DIR}/server.key" -noout -modulus 2>/dev/null || true)
  [[ -n "${cert_mod}" && -n "${key_mod}" && "${cert_mod}" == "${key_mod}" ]] || return 1
  return 0
}

gen_ca_if_missing() {
  if [[ ! -f "${CERT_DIR}/ca.key" || ! -f "${CERT_DIR}/ca.crt" ]]; then
    log "Creating root CA ..."
    openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 3650 \
      -subj "/CN=DTS Local CA/O=${SUBJECT_O}" \
      -addext "basicConstraints=critical,CA:TRUE" \
      -addext "keyUsage=critical,keyCertSign,cRLSign" \
      -keyout "${CERT_DIR}/ca.key" -out "${CERT_DIR}/ca.crt" >/dev/null 2>&1
  else
    log "Using existing CA at ${CERT_DIR}/ca.crt"
  fi
}

gen_server_cert() {
  log "Creating server key and CSR for *.${BASE_DOMAIN} ..."
  openssl req -new -newkey rsa:2048 -nodes -sha256 \
    -subj "/CN=*.${BASE_DOMAIN}/O=${SUBJECT_O}" \
    -addext "subjectAltName=DNS:*.${BASE_DOMAIN},DNS:${BASE_DOMAIN}" \
    -addext "keyUsage=digitalSignature,keyEncipherment" \
    -addext "extendedKeyUsage=serverAuth" \
    -keyout "${CERT_DIR}/server.key" -out "${CERT_DIR}/server.csr" >/dev/null 2>&1

  log "Signing server certificate with local CA ..."
  local extf
  extf="${CERT_DIR}/openssl.ext"
  cat >"${extf}" <<EOF
[ v3_req ]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:*.${BASE_DOMAIN},DNS:${BASE_DOMAIN}
EOF
  openssl x509 -req -in "${CERT_DIR}/server.csr" -CA "${CERT_DIR}/ca.crt" -CAkey "${CERT_DIR}/ca.key" \
    -CAcreateserial -out "${CERT_DIR}/server.only.crt" -days 825 -sha256 -extfile "${extf}" -extensions v3_req >/dev/null 2>&1

  # Full chain for Traefik
  cat "${CERT_DIR}/server.only.crt" "${CERT_DIR}/ca.crt" > "${CERT_DIR}/server.crt"

  # PKCS#12 for Java
  openssl pkcs12 -export -inkey "${CERT_DIR}/server.key" -in "${CERT_DIR}/server.only.crt" -certfile "${CERT_DIR}/ca.crt" \
    -name "dts-server" -out "${CERT_DIR}/server.p12" -passout pass:"${P12_PASSWORD}" >/dev/null 2>&1 || true

  rm -f "${CERT_DIR}/server.csr" "${CERT_DIR}/server.srl" "${extf}" 2>/dev/null || true
}

main() {
  if have_valid_existing; then
    log "TLS certificates already valid for *.${BASE_DOMAIN}; keeping existing files."
    return 0
  fi

  log "Generating local CA (if missing) and server certificate for *.${BASE_DOMAIN} ..."
  gen_ca_if_missing
  gen_server_cert
  log "generated server.key, server.crt (full chain), ca.crt, server.p12"
  # Show quick summary for debugging
  openssl x509 -in "${CERT_DIR}/server.crt" -noout -subject -issuer -ext subjectAltName | sed 's/^/[certs] /'
}

main "$@"

