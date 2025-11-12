#!/usr/bin/env bash
# Kunpeng (aarch64) + Kylin V10 friendly, offline-safe
# Purpose: Diagnose and fix Docker bridge egress (FORWARD/NAT) so DTS containers can reach Inceptor/Kerberos.

set -euo pipefail

MODE="status"           # status|apply|rollback
NETWORK_NAME="dts-core" # Docker network name
TARGET_HOST=""          # Target IP/host to detect egress interface (e.g., 10.10.131.134)

usage() {
  cat <<'EOF'
Usage: netfix.sh [--mode status|apply|rollback] [--network dts-core] --target <ip-or-host>

Examples
  # Show status using Inceptor host to infer egress interface
  sudo bash tools/netfix.sh --mode status --network dts-core --target 10.10.131.134

  # Apply nftables fixes (preferred) or iptables if nft is unavailable
  sudo bash tools/netfix.sh --mode apply --network dts-core --target 10.10.131.134

  # Rollback rules previously added by this script (nft via comment markers; iptables via matching specs)
  sudo bash tools/netfix.sh --mode rollback --network dts-core --target 10.10.131.134

Notes
  - Run as root (or with sudo). Offline friendly; no package installs.
  - The script adds precise FORWARD + MASQUERADE rules for the Docker subnet to the detected egress interface.
  - nftables is preferred on Kylin V10. Falls back to iptables if nft is missing.
  - Rules are tagged with comment "dts-netfix" for idempotency and rollback (nft). iptables rollback matches rule specs.
EOF
}

log() { echo "[netfix] $*"; }
err() { echo "[netfix][ERROR] $*" >&2; }

have_cmd() { command -v "$1" >/dev/null 2>&1; }

require_root() {
  if [ "${EUID:-$(id -u)}" -ne 0 ]; then
    err "Please run as root (sudo)."
    exit 1
  fi
}

parse_args() {
  while [ $# -gt 0 ]; do
    case "$1" in
      --mode)
        MODE="${2:-}"; shift 2;;
      --network)
        NETWORK_NAME="${2:-}"; shift 2;;
      --target)
        TARGET_HOST="${2:-}"; shift 2;;
      -h|--help)
        usage; exit 0;;
      *)
        err "Unknown arg: $1"; usage; exit 2;;
    esac
  done
  case "$MODE" in
    status|apply|rollback) :;;
    *) err "Invalid --mode: $MODE"; usage; exit 2;;
  esac
  if [ -z "$TARGET_HOST" ]; then
    err "--target <ip-or-host> is required (e.g., Inceptor HS2 host IP)."; usage; exit 2
  fi
}

resolve_ipv4() {
  local name="$1"
  # If already an IPv4 literal, return it
  if [[ "$name" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; then
    echo "$name"; return 0
  fi
  if have_cmd getent; then
    getent ahostsv4 "$name" | awk '{print $1; exit}'
    return ${PIPESTATUS[0]}
  fi
  # Fallback: try ping -c1 (may not exist)
  if have_cmd ping; then
    ping -c1 -4 "$name" 2>/dev/null | awk -F'[() ]' '/PING/{print $4; exit}'
    return ${PIPESTATUS[0]}
  fi
  return 1
}

discover() {
  # Docker subnet
  if ! have_cmd docker; then
    err "docker is required to inspect network $NETWORK_NAME"; exit 1
  fi
  local subnet
  subnet=$(docker network inspect "$NETWORK_NAME" -f '{{(index .IPAM.Config 0).Subnet}}' 2>/dev/null || true)
  if [ -z "$subnet" ]; then
    err "Unable to get subnet for network $NETWORK_NAME. Is it created?"; exit 1
  fi
  DOCKER_SUBNET="$subnet"

  # Egress interface via route to target
  local target_ip
  target_ip=$(resolve_ipv4 "$TARGET_HOST" || true)
  if [ -z "$target_ip" ]; then
    err "Unable to resolve target to IPv4: $TARGET_HOST"; exit 1
  fi
  local route
  route=$(ip route get "$target_ip" 2>/dev/null || true)
  if [ -z "$route" ]; then
    err "ip route get failed for $target_ip"; exit 1
  fi
  local dev
  dev=$(awk '/ dev /{for(i=1;i<=NF;i++) if($i=="dev"){print $(i+1); exit}}' <<<"$route")
  if [ -z "$dev" ]; then
    err "Unable to determine egress interface from route: $route"; exit 1
  fi
  EGRESS_IF="$dev"
  TARGET_IP="$target_ip"
}

print_status() {
  local ipf="unknown"
  if have_cmd sysctl; then
    ipf=$(sysctl -n net.ipv4.ip_forward 2>/dev/null || true)
  fi
  log "Mode=$MODE Network=$NETWORK_NAME Subnet=${DOCKER_SUBNET} Target=${TARGET_HOST}(${TARGET_IP}) IFACE=${EGRESS_IF} ip_forward=${ipf}"
  if have_cmd nft; then
    echo "--- nftables forward (inet) ---"
    nft list chain inet filter forward 2>/dev/null || true
    echo "--- nftables postrouting (ip nat) ---"
    nft list chain ip nat postrouting 2>/dev/null || nft list chain inet nat postrouting 2>/dev/null || true
    echo "--- nftables dts-netfix markers ---"
    nft -a list ruleset 2>/dev/null | grep -n "dts-netfix" || true
  fi
  if have_cmd iptables; then
    echo "--- iptables FORWARD ---"; iptables -S FORWARD 2>/dev/null || true
    echo "--- iptables DOCKER-USER ---"; iptables -S DOCKER-USER 2>/dev/null || true
    echo "--- iptables nat POSTROUTING ---"; iptables -t nat -S POSTROUTING 2>/dev/null || true
  fi
}

ensure_ip_forward() {
  if ! have_cmd sysctl; then return; fi
  local cur
  cur=$(sysctl -n net.ipv4.ip_forward 2>/dev/null || echo 0)
  if [ "$cur" != "1" ]; then
    log "Enabling net.ipv4.ip_forward=1"
    sysctl -w net.ipv4.ip_forward=1 >/dev/null
  fi
}

# ---------- nftables path ----------
nft_has_rule() {
  # $1: grep pattern
  nft -a list ruleset 2>/dev/null | grep -F "$1" >/dev/null 2>&1
}

nft_apply() {
  # Create tables/chains if missing
  nft list table ip nat >/dev/null 2>&1 || nft add table ip nat || true
  nft list chain ip nat postrouting >/dev/null 2>&1 || nft add chain ip nat postrouting '{ type nat hook postrouting priority 100; }' || true
  nft list table inet filter >/dev/null 2>&1 || nft add table inet filter || true
  nft list chain inet filter forward >/dev/null 2>&1 || nft add chain inet filter forward '{ type filter hook forward priority 0; }' || true

  local mark_nat="dts-netfix nat ${DOCKER_SUBNET}->${EGRESS_IF}"
  local mark_est="dts-netfix forward established"
  local mark_eg="dts-netfix forward egress ${DOCKER_SUBNET}->${EGRESS_IF}"
  local mark_ret="dts-netfix forward return ${EGRESS_IF}->${DOCKER_SUBNET}"

  nft_has_rule "$mark_nat" || nft add rule ip nat postrouting ip saddr ${DOCKER_SUBNET} oifname "${EGRESS_IF}" masquerade comment "$mark_nat" || true
  nft_has_rule "$mark_est" || nft add rule inet filter forward ct state related,established accept comment "$mark_est" || true
  nft_has_rule "$mark_eg"  || nft add rule inet filter forward ip saddr ${DOCKER_SUBNET} oifname "${EGRESS_IF}" accept comment "$mark_eg" || true
  nft_has_rule "$mark_ret" || nft add rule inet filter forward iifname "${EGRESS_IF}" ip daddr ${DOCKER_SUBNET} ct state related,established accept comment "$mark_ret" || true
}

nft_rollback() {
  # Delete rules bearing our comment markers
  local handles
  handles=$(nft -a list ruleset 2>/dev/null | awk '/dts-netfix/ {print $1, $2, $3, $NF}' || true)
  # Expected fields like: table ip nat handle 30
  # We need family, table, chain, handle. Parse with a more robust approach:
  nft -a list ruleset 2>/dev/null | while read -r line; do
    if echo "$line" | grep -q 'dts-netfix'; then
      # Example line:  rule ip nat postrouting handle 24 ... comment "dts-netfix ..."
      local family table chain handle
      family=$(awk '{print $2}' <<<"$line")
      table=$(awk '{print $3}' <<<"$line")
      chain=$(awk '{print $4}' <<<"$line")
      handle=$(awk '{for(i=1;i<=NF;i++) if($i=="handle"){print $(i+1); break}}' <<<"$line")
      if [ -n "$family" ] && [ -n "$table" ] && [ -n "$chain" ] && [ -n "$handle" ]; then
        log "Deleting nft rule: $family $table $chain handle $handle"
        nft delete rule "$family" "$table" "$chain" handle "$handle" || true
      fi
    fi
  done
}

# ---------- iptables path ----------
ipt_apply() {
  # Allow forward (egress + return) in both FORWARD and DOCKER-USER chains
  iptables -C FORWARD -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j ACCEPT 2>/dev/null || iptables -I FORWARD -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j ACCEPT
  iptables -C FORWARD -i "$EGRESS_IF" -d "$DOCKER_SUBNET" -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || iptables -I FORWARD -i "$EGRESS_IF" -d "$DOCKER_SUBNET" -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
  # DOCKER-USER early allow
  iptables -C DOCKER-USER -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j ACCEPT 2>/dev/null || iptables -I DOCKER-USER 1 -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j ACCEPT
  iptables -C DOCKER-USER -i "$EGRESS_IF" -d "$DOCKER_SUBNET" -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || iptables -I DOCKER-USER 2 -i "$EGRESS_IF" -d "$DOCKER_SUBNET" -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
  # Ensure SNAT
  iptables -t nat -C POSTROUTING -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j MASQUERADE 2>/dev/null || iptables -t nat -A POSTROUTING -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j MASQUERADE
}

ipt_rollback() {
  # Attempt to delete rules by spec (order-insensitive). Ignore failures.
  iptables -D FORWARD -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j ACCEPT 2>/dev/null || true
  iptables -D FORWARD -i "$EGRESS_IF" -d "$DOCKER_SUBNET" -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || true
  iptables -D DOCKER-USER -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j ACCEPT 2>/dev/null || true
  iptables -D DOCKER-USER -i "$EGRESS_IF" -d "$DOCKER_SUBNET" -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || true
  iptables -t nat -D POSTROUTING -s "$DOCKER_SUBNET" -o "$EGRESS_IF" -j MASQUERADE 2>/dev/null || true
}

main() {
  parse_args "$@"
  require_root
  discover

  print_status

  case "$MODE" in
    status)
      log "Status printed above. No changes made."
      ;;
    apply)
      ensure_ip_forward
      if have_cmd nft; then
        log "Applying nftables rules for ${DOCKER_SUBNET} via ${EGRESS_IF}"
        nft_apply
      elif have_cmd iptables; then
        log "Applying iptables rules for ${DOCKER_SUBNET} via ${EGRESS_IF}"
        ipt_apply
      else
        err "Neither nft nor iptables found. Cannot apply rules."; exit 1
      fi
      log "Apply completed. Re-run with --mode status or test from containers."
      ;;
    rollback)
      if have_cmd nft; then
        log "Rolling back nftables rules tagged dts-netfix"
        nft_rollback
      fi
      if have_cmd iptables; then
        log "Rolling back iptables rules by spec"
        ipt_rollback || true
      fi
      log "Rollback completed."
      ;;
  esac
}

main "$@"

