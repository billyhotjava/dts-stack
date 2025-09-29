#!/usr/bin/env bash
set -euo pipefail

# Generate a CA-signed server certificate with a full chain suitable for Traefik and Java.
# Outputs:
# - server.key (PEM)
# - server.crt (PEM, full chain: server + CA)
# - ca.crt (PEM, root CA)
# - server.p12 (PKCS#12 with key + chain; password P12_PASSWORD or 'changeit')

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CERT_DIR="${SCRIPT_DIR}"
BASE_DOMAIN="${BASE_DOMAIN:-dts.local}"
P12_PASSWORD="${P12_PASSWORD:-changeit}"

mkdir -p "${CERT_DIR}"

log() { echo "[certs] $*"; }

tmp_conf() {
  local san_dns="DNS:*.${BASE_DOMAIN},DNS:${BASE_DOMAIN}"
  cat <<EOF
[ req ]
default_bits       = 2048
distinguished_name = dn
prompt             = no
req_extensions     = v3_req

[ dn ]
CN = *.${BASE_DOMAIN}
O  = DTS

[ v3_req ]
basicConstraints = CA:FALSE
keyUsage = digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = ${san_dns}
EOF
}

ca_conf() {
  cat <<EOF
[ req ]
default_bits       = 2048
distinguished_name = dn
prompt             = no
req_extensions     = v3_ca

[ dn ]
CN = DTS Local CA
O  = DTS

[ v3_ca ]
basicConstraints = critical,CA:TRUE
keyUsage = critical, keyCertSign, cRLSign
subjectKeyIdentifier = hash
EOF
}

need_regen=true

# If existing server.crt matches SAN and is signed by our CA (if present), we can keep it.
if [[ -f "${CERT_DIR}/server.crt" ]]; then
  if openssl x509 -in "${CERT_DIR}/server.crt" -noout -ext subjectAltName 2>/dev/null | grep -F "DNS:*.${BASE_DOMAIN}" >/dev/null; then
    if [[ -f "${CERT_DIR}/ca.crt" ]]; then
      iss_subj="$(openssl x509 -in "${CERT_DIR}/server.crt" -noout -issuer 2>/dev/null || true)"
      ca_subj="$(openssl x509 -in "${CERT_DIR}/ca.crt" -noout -subject 2>/dev/null || true)"
      if [[ -n "${iss_subj}" && -n "${ca_subj}" && "${iss_subj#issuer=}" == "${ca_subj#subject=}" ]]; then
        need_regen=false
      fi
    fi
  fi
fi

if [[ "${need_regen}" == true ]]; then
  log "Generating local CA (if missing) and server certificate for *.${BASE_DOMAIN} ..."

  # Root CA (self-signed)
  if [[ ! -f "${CERT_DIR}/ca.key" || ! -f "${CERT_DIR}/ca.crt" ]]; then
    log "Creating root CA ..."
    ca_conf | openssl req -x509 -newkey rsa:2048 -nodes -sha256 -days 3650 \
      -keyout "${CERT_DIR}/ca.key" -out "${CERT_DIR}/ca.crt" -extensions v3_ca >/dev/null 2>&1
  else
    log "Using existing CA at ${CERT_DIR}/ca.crt"
  fi

  # Server key + CSR
  log "Creating server key and CSR ..."
  tmp_conf | openssl req -new -newkey rsa:2048 -nodes -sha256 \
    -keyout "${CERT_DIR}/server.key" -out "${CERT_DIR}/server.csr" >/dev/null 2>&1

  # Sign server CSR with CA
  log "Signing server certificate with local CA ..."
  tmp_conf >"${CERT_DIR}/openssl.cnf"
  openssl x509 -req -in "${CERT_DIR}/server.csr" -CA "${CERT_DIR}/ca.crt" -CAkey "${CERT_DIR}/ca.key" \
    -CAcreateserial -out "${CERT_DIR}/server.only.crt" -days 825 -sha256 -extfile "${CERT_DIR}/openssl.cnf" -extensions v3_req >/dev/null 2>&1

  # Full chain for Traefik (server first, then CA). Traefik will serve the chain.
  cat "${CERT_DIR}/server.only.crt" "${CERT_DIR}/ca.crt" > "${CERT_DIR}/server.crt"

  # PKCS#12 for Java (includes chain)
  openssl pkcs12 -export -inkey "${CERT_DIR}/server.key" -in "${CERT_DIR}/server.only.crt" -certfile "${CERT_DIR}/ca.crt" \
    -name "dts-server" -out "${CERT_DIR}/server.p12" -passout pass:"${P12_PASSWORD}" >/dev/null 2>&1 || true

  rm -f "${CERT_DIR}/server.csr" "${CERT_DIR}/openssl.cnf" "${CERT_DIR}/server.srl" 2>/dev/null || true
  log "generated server.key, server.crt (full chain), ca.crt, server.p12"
else
  log "TLS certificates already valid for *.${BASE_DOMAIN}; keeping existing files."
fi

# Helpful hints for Java truststore (optional, requires keytool)
if command -v keytool >/dev/null 2>&1; then
  TRUSTSTORE_PASSWORD="${TRUSTSTORE_PASSWORD:-changeit}"
  if [[ ! -f "${CERT_DIR}/truststore.jks" ]]; then
    keytool -importcert -noprompt -alias dts-ca -file "${CERT_DIR}/ca.crt" \
      -keystore "${CERT_DIR}/truststore.jks" -storepass "${TRUSTSTORE_PASSWORD}" >/dev/null 2>&1 || true
    log "generated truststore.jks (contains ca.crt). Password: ${TRUSTSTORE_PASSWORD}"
  fi
fi
