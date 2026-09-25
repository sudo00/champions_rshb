#!/usr/bin/env bash
# Install this machine's API reply route using an ignored local configuration.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
unit_path="$project_root/build_env/network/wine-api-routing.service"
network_env="$project_root/.env.network"
rules_file="$(mktemp)"
trap 'rm -f -- "$rules_file"' EXIT
if [[ ! -f "$network_env" ]]; then
    echo 'Create .env.network from build_env/network.env.example and set the LAN IPv4.' >&2
    exit 1
fi
# Parse, do not execute, the local file before installing it as root.
python3 - "$network_env" "$rules_file" <<'PY'
import ipaddress
import json
from pathlib import Path
import subprocess
import sys
lines = [line.strip() for line in Path(sys.argv[1]).read_text().splitlines()
         if line.strip() and not line.lstrip().startswith('#')]
values = {}
for line in lines:
    key, separator, value = line.partition('=')
    if not separator or key not in ('WINE_API_SOURCE_IP', 'WINE_API_VPN_BYPASS_MARK') or key in values:
        raise SystemExit('Expected WINE_API_SOURCE_IP and optional WINE_API_VPN_BYPASS_MARK only')
    values[key] = value
source = str(ipaddress.IPv4Address(values.get('WINE_API_SOURCE_IP', '')))
mark = values.get('WINE_API_VPN_BYPASS_MARK')
if not mark:
    rules = json.loads(subprocess.check_output(['ip', '-j', '-4', 'rule', 'show']))
    marks = {str(r['fwmark']) for r in rules if 'not' in r and 'fwmark' in r
             and r.get('src') == 'all' and int(r.get('priority', 32766)) <= 1}
    if len(marks) > 1:
        raise SystemExit('Several VPN bypass marks found; specify WINE_API_VPN_BYPASS_MARK explicitly')
    mark = next(iter(marks), None)
commands = ['add table ip wine_api_routing', 'flush table ip wine_api_routing',
            'add chain ip wine_api_routing output { type route hook output priority mangle; policy accept; }']
if mark:
    number = int(mark, 0)
    if not 0 < number <= 0xffffffff:
        raise SystemExit('VPN bypass mark must be a nonzero 32-bit number')
    commands.append(f'add rule ip wine_api_routing output ip saddr {source} tcp sport 8000 counter meta mark set {hex(number)}')
    print(f'API TCP 8000 replies will use VPN bypass mark {hex(number)}; other traffic is unchanged.')
else:
    print('No priority-0/1 VPN mark detected; using source-port route only. Reinstall with VPN enabled if needed.')
Path(sys.argv[2]).write_text('\n'.join(commands) + '\n')
PY
systemd-analyze verify "$unit_path"
sudo nft --check --file "$rules_file"
sudo install -m 0600 "$network_env" /etc/wine-api-network.env
sudo install -m 0600 "$rules_file" /etc/wine-api-routing.nft
sudo install -m 0644 "$unit_path" /etc/systemd/system/wine-api-routing.service
sudo systemctl daemon-reload
sudo systemctl enable wine-api-routing.service
sudo systemctl restart wine-api-routing.service
systemctl is-enabled wine-api-routing.service
systemctl is-active wine-api-routing.service
ip -4 rule show priority 1
sudo nft list table ip wine_api_routing
