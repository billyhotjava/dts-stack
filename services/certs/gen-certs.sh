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

cleanup_placeholders() {
  local item
  local -a expected_files=(
    "ca.crt" "ca.key" "server.key" "server.csr" "server.crt" "server.only.crt" "server.p12" "keystore.p12" "truststore.jks" "truststore.p12"
  )
  for item in "${expected_files[@]}"; do
    if [[ -d "${CERT_DIR}/${item}" ]]; then
      rm -rf "${CERT_DIR:?}/${item}"
    fi
  done
}

validate_ca() {
  [[ -f "${CERT_DIR}/ca.crt" ]] || return 1
  local bc_count
  bc_count=$(openssl x509 -in "${CERT_DIR}/ca.crt" -noout -text 2>/dev/null | grep -c "X509v3 Basic Constraints") || bc_count=0
  [[ "${bc_count}" -eq 1 ]]
}

gen_ca_clean() {
  log "Creating clean root CA with minimal OpenSSL config ..."
  local cfg
  cfg="${CERT_DIR}/openssl-ca.cnf"
  cat >"${cfg}" <<EOF
[ req ]
default_bits       = 2048
prompt             = no
default_md         = sha256
distinguished_name = dn
x509_extensions    = v3_ca

[ dn ]
CN = DTS Local CA
O  = ${SUBJECT_O}

[ v3_ca ]
basicConstraints       = critical,CA:TRUE
keyUsage               = critical,keyCertSign,cRLSign
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid:always,issuer
EOF

  openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
    -config "${cfg}" \
    -keyout "${CERT_DIR}/ca.key" -out "${CERT_DIR}/ca.crt" >/dev/null 2>&1
  rm -f "${cfg}" 2>/dev/null || true
}

cleanup_placeholders() {
  local item
  local -a expected_files=(
    "ca.crt"
    "ca.key"
    "server.key"
    "server.csr"
    "server.crt"
    "server.only.crt"
    "server.p12"
    "keystore.p12"
    "truststore.jks"
    "truststore.p12"
  )
  for item in "${expected_files[@]}"; do
    if [[ -d "${CERT_DIR}/${item}" ]]; then
      rm -rf "${CERT_DIR:?}/${item}"
    fi
  done
}

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
    gen_ca_clean
  else
    if validate_ca; then
      log "Using existing CA at ${CERT_DIR}/ca.crt"
    else
      log "Existing CA has duplicate/invalid extensions; reissuing clean CA ..."
      gen_ca_clean
    fi
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

# Execute keytool either from host or via a small Java container (Temurin JRE)
keytool_exec() {
  local image="${KEYTOOL_IMAGE:-eclipse-temurin:21-jre}"
  if find_keytool; then
    "${KEYTOOL_BIN}" "$@"
    return $?
  fi
  if command -v docker >/dev/null 2>&1; then
    docker run --rm --security-opt seccomp=unconfined -v "${CERT_DIR}:/certs:z" -w /certs "${image}" keytool "$@"
    return $?
  fi
  return 1
}

generate_truststores() {
  # Always produce PKCS12 via keytool; optionally also JKS. Abort if keytool is unavailable.
  rm -f "${CERT_DIR}/truststore.p12" "${CERT_DIR}/truststore.jks"
  local ca_pem="${CERT_DIR}/ca.crt"

  if ! keytool_exec -importcert -trustcacerts -noprompt \
      -alias dts-ca -file "${ca_pem}" \
      -keystore "${CERT_DIR}/truststore.p12" -storetype PKCS12 -storepass "${TRUSTSTORE_PASSWORD}" >/dev/null 2>&1; then
    log "ERROR: failed to generate truststore.p12 with keytool (host or container)."; return 1
  fi
  chmod 0644 "${CERT_DIR}/truststore.p12" 2>/dev/null || true

  # Verify PKCS12 contains at least one entry
  if ! keytool_exec -list -keystore "${CERT_DIR}/truststore.p12" -storetype PKCS12 -storepass "${TRUSTSTORE_PASSWORD}" >/dev/null 2>&1; then
    log "ERROR: generated truststore.p12 is not readable by Java keytool."; return 1
  fi

  # Also produce JKS for environments that prefer it
  if keytool_exec -importkeystore -noprompt \
      -srckeystore "${CERT_DIR}/truststore.p12" -srcstoretype PKCS12 -srcstorepass "${TRUSTSTORE_PASSWORD}" \
      -destkeystore "${CERT_DIR}/truststore.jks" -deststoretype JKS -deststorepass "${TRUSTSTORE_PASSWORD}" \
      -srcalias dts-ca -destalias dts-ca >/dev/null 2>&1; then
    chmod 0644 "${CERT_DIR}/truststore.jks" 2>/dev/null || true
    log "generated truststore.p12 and truststore.jks (CA only). Password: ${TRUSTSTORE_PASSWORD}"
  else
    log "WARNING: could not generate truststore.jks; proceeding with truststore.p12 only."
  fi
}

main() {
  cleanup_placeholders

  if have_valid_existing; then
    log "TLS certificates already valid for *.${BASE_DOMAIN}; keeping existing files."
    if [[ ! -f "${CERT_DIR}/truststore.p12" ]]; then
      log "Truststore missing; regenerating truststore artifacts..."
      if ! generate_truststores; then
        log "ERROR: Failed to generate truststores."
        exit 1
      fi
    fi
    return 0
  fi

  log "Generating local CA (if missing) and server certificate for *.${BASE_DOMAIN} ..."
  gen_ca_if_missing
  gen_server_cert
  if ! generate_truststores; then
    log "ERROR: Failed to generate truststores."
    exit 1
  fi
  log "generated server.key, server.crt (full chain), ca.crt, server.p12"
  # Show quick summary for debugging
  openssl x509 -in "${CERT_DIR}/server.crt" -noout -subject -issuer -ext subjectAltName | sed 's/^/[certs] /'
}

main "$@"
