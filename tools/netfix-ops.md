# DTS → Inceptor Connectivity Operations Guide (Kunpeng + Kylin V10)

## Purpose
- Provide an operator-friendly, offline procedure to diagnose and fix container timeouts from dts-admin/dts-platform to Inceptor (HS2, e.g., 10000) and Kerberos KDC (e.g., 1088).
- Validated for Kunpeng (aarch64) and Kylin V10 where nftables is the default firewall backend; falls back to iptables if needed.

## Quick Start
1) Ensure root and IP forwarding
- `sysctl -n net.ipv4.ip_forward` → expect `1`
- Run all steps with `sudo` or as root.

2) Discover Docker subnet and egress interface (replace target with your Inceptor host/IP)
- `docker network inspect dts-core -f '{{(index .IPAM.Config 0).Subnet}}'` (example: `172.30.1.0/24`)
- `ip route get 10.10.131.134` → note the `dev <IFACE>` (example: `eth5`)

3) Use the helper script
- Make executable once: `chmod +x tools/netfix.sh`
- Status only: `sudo bash tools/netfix.sh --mode status --network dts-core --target 10.10.131.134`
- Apply fixes: `sudo bash tools/netfix.sh --mode apply --network dts-core --target 10.10.131.134`
- Rollback rules: `sudo bash tools/netfix.sh --mode rollback --network dts-core --target 10.10.131.134`

4) Verify from containers
- `docker compose -f docker-compose.legacy.yml exec -T dts-admin bash -lc 'timeout 3 bash -lc ": </dev/tcp/tdh01/10000" && echo OK || echo FAIL'`
- `docker compose -f docker-compose.legacy.yml exec -T dts-admin bash -lc 'timeout 3 bash -lc ": </dev/tcp/tdh02/1088" && echo OK || echo FAIL'`
- Repeat for `dts-platform`.

## What the script does
- Detects Docker subnet from `dts-core` and detects egress interface via route to `--target`.
- nftables (preferred):
  - Creates `ip nat`/`postrouting` chain if missing; adds MASQUERADE for `${SUBNET} → ${IFACE}`.
  - Creates `inet filter`/`forward` chain if missing; allows new connections from `${SUBNET}` to `${IFACE}` and allows RELATED,ESTABLISHED return.
  - Tags rules with `comment "dts-netfix …"` for idempotency and rollback.
- iptables (fallback):
  - Inserts equivalent rules into `FORWARD`, `DOCKER-USER`, and `nat POSTROUTING` with `MASQUERADE`.
- Sets `net.ipv4.ip_forward=1` if needed.

## Persisting changes on Kylin V10 (nftables)
1) Create a file (adjust subnet/iface): `/etc/nftables.d/docker-dts.nft`
```
# DTS bridge egress (generated manually)
add table ip nat
add chain ip nat postrouting { type nat hook postrouting priority 100; }
add rule ip nat postrouting ip saddr 172.30.1.0/24 oifname "eth5" masquerade comment "dts-netfix nat 172.30.1.0/24->eth5"

add table inet filter
add chain inet filter forward { type filter hook forward priority 0; }
add rule inet filter forward ct state related,established accept comment "dts-netfix forward established"
add rule inet filter forward ip saddr 172.30.1.0/24 oifname "eth5" accept comment "dts-netfix forward egress 172.30.1.0/24->eth5"
add rule inet filter forward iifname "eth5" ip daddr 172.30.1.0/24 ct state related,established accept comment "dts-netfix forward return eth5->172.30.1.0/24"
```
2) Ensure main config includes the directory (typical): `/etc/nftables.conf` contains
```
include "/etc/nftables.d/*.nft"
```
3) Enable service: `systemctl enable --now nftables`
4) Verify: `nft list chain inet filter forward` and `nft list chain ip nat postrouting`

## Rollback
- Script: `sudo bash tools/netfix.sh --mode rollback --network dts-core --target 10.10.131.134`
- Manual (nftables): use `nft -a list ruleset` to show handles, then `nft delete rule <family> <table> <chain> handle <N>` for rules containing `dts-netfix`.
- Manual (iptables): mirror the `-I/-A` operations with `-D` using the same specs.

## Name resolution (extra_hosts)
- Ensure these exist for both services if DNS is not usable:
```
dts-admin:
  extra_hosts:
    - "tdh01:10.10.131.134"
    - "tdh02:10.10.131.135"
    - "tdh03:10.10.131.136"
dts-platform:
  extra_hosts:
    - "tdh01:10.10.131.134"
    - "tdh02:10.10.131.135"
    - "tdh03:10.10.131.136"
```
- Apply only these services: `docker compose -f docker-compose.legacy.yml up -d dts-admin dts-platform`

## Kerberos notes
- krb5.conf and keytab/password are uploaded via admin UI; the platform writes krb5.conf to a temp path and sets `java.security.krb5.conf` internally.
- With `dns_lookup_kdc=false`, containers must reach `tdh02/1088` and `tdh03/1088`.
- Optional temporary debug: append `-Dsun.security.krb5.debug=true` to `JAVA_TOOL_OPTIONS` (remove afterwards).

## Zero-impact validation
- Host-network probe (proves bridge/NAT issue if OK while containers fail):
- `docker run --rm --network host alpine:3.20 sh -lc "timeout 3 sh -c ': </dev/tcp/10.10.131.134/10000' && echo OK || echo FAIL"`

## Common pitfalls
- Misspelling `FORWARD` as `FORWORD` (no effect). Use nftables commands on Kylin V10 unless iptables is confirmed and not nft-backed.
- Forgetting MASQUERADE: without SNAT, replies never return to containers.
- Wide allow rules: keep rules scoped to the Docker subnet and specific egress interface used to reach Inceptor/KDC.
- Recreated Docker network: subnet may change; re-run the script.

## Support data to collect (offline)
- Host: `sysctl -n net.ipv4.ip_forward`, `docker network inspect dts-core`, `ip route get <inceptor-ip>`, `nft list chain inet filter forward`, `nft list chain ip nat postrouting` (or iptables equivalents).
- Container: `/etc/hosts` entries for tdh01/02/03, `getent hosts tdh01 tdh02 tdh03`, `/dev/tcp` probes to 10000/1088.

