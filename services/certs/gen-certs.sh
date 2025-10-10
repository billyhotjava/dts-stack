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
TRUSTSTORE_PASSWORD="${TRUSTSTORE_PASSWORD:-changeit}"

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

  # Also provide a conventional name for Java app servers
  cp -f "${CERT_DIR}/server.p12" "${CERT_DIR}/keystore.p12" 2>/dev/null || true

  rm -f "${CERT_DIR}/server.csr" "${CERT_DIR}/server.srl" "${extf}" 2>/dev/null || true
}

find_keytool() {
  if command -v keytool >/dev/null 2>&1; then
    KEYTOOL_BIN="$(command -v keytool)"
    return 0
  fi
  local candidate
  for candidate in /usr/lib/jvm/*/bin/keytool /usr/lib/jvm/*/jre/bin/keytool /opt/java/openjdk*/bin/keytool /opt/java/openjdk*/jre/bin/keytool; do
    if [[ -x "${candidate}" ]]; then
      KEYTOOL_BIN="${candidate}"
      return 0
    fi
  done
  return 1
}

generate_truststores() {
  # Generate JKS and PKCS12 truststores containing only the CA (for clients)
  if find_keytool; then
    # Docker may leave directory placeholders when the file is bind-mounted.
    rm -rf "${CERT_DIR}/truststore.jks" "${CERT_DIR}/truststore.p12"

    local ca_pem="${CERT_DIR}/ca.crt"

    # Always produce a PKCS12 truststore via OpenSSL (works reliably across distros)
    if ! openssl pkcs12 -export -nokeys -in "${ca_pem}" -name "dts-ca" \
      -out "${CERT_DIR}/truststore.p12" -passout pass:"${TRUSTSTORE_PASSWORD}" >/dev/null 2>&1; then
      log "ERROR: openssl failed to generate truststore.p12"
      return 1
    fi

    # First try to convert PKCS12 -> JKS using keytool (importkeystore path)
    if "${KEYTOOL_BIN}" -importkeystore -noprompt \
      -srckeystore "${CERT_DIR}/truststore.p12" -srcstoretype PKCS12 -srcstorepass "${TRUSTSTORE_PASSWORD}" \
      -destkeystore "${CERT_DIR}/truststore.jks" -deststoretype JKS -deststorepass "${TRUSTSTORE_PASSWORD}" \
      -srcalias dts-ca -destalias dts-ca >/dev/null 2>&1; then
      log "generated truststore.jks and truststore.p12 (CA only). Password: ${TRUSTSTORE_PASSWORD}"
      return 0
    fi

    log "WARNING: keytool importkeystore failed; attempting DER import fallback..."

    local tmp_der
    tmp_der="$(mktemp "${CERT_DIR}/ca.XXXXXX.der")"
    if ! openssl x509 -in "${ca_pem}" -outform der -out "${tmp_der}" >/dev/null 2>&1; then
      log "ERROR: openssl failed to convert CA certificate to DER format"
      rm -f "${tmp_der}"
      return 1
    fi

    if "${KEYTOOL_BIN}" -importcert -noprompt -alias dts-ca -file "${tmp_der}" \
      -keystore "${CERT_DIR}/truststore.jks" -storetype JKS -storepass "${TRUSTSTORE_PASSWORD}" >/dev/null 2>&1; then
      rm -f "${tmp_der}"
      if [[ ! -f "${CERT_DIR}/truststore.jks" ]]; then
        log "ERROR: truststore.jks not created after fallback import"
        return 1
      fi
      log "generated truststore.jks (fallback) and truststore.p12 (PKCS12). Password: ${TRUSTSTORE_PASSWORD}"
      return 0
    fi
    rm -f "${tmp_der}"
    log "ERROR: keytool failed to generate truststore.jks; truststore.p12 is available as fallback."
    return 1
  else
    log "keytool not found; skipped truststore generation. Install a JDK or supply truststores manually."
  fi
}

main() {
  if have_valid_existing; then
    log "TLS certificates already valid for *.${BASE_DOMAIN}; keeping existing files."
    return 0
  fi

  log "Generating local CA (if missing) and server certificate for *.${BASE_DOMAIN} ..."
  gen_ca_if_missing
  gen_server_cert
  generate_truststores
  log "generated server.key, server.crt (full chain), ca.crt, server.p12"
  # Show quick summary for debugging
  openssl x509 -in "${CERT_DIR}/server.crt" -noout -subject -issuer -ext subjectAltName | sed 's/^/[certs] /'
}

main "$@"
