#!/usr/bin/env bash
# Install this machine's API reply route using an ignored local configuration.
set -euo pipefail
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"
unit_path="$project_root/build_env/network/wine-api-routing.service"
network_env="$project_root/.env.network"
if [[ ! -f "$network_env" ]]; then
    echo 'Create .env.network from build_env/network.env.example and set the LAN IPv4.' >&2
    exit 1
fi
# Parse, do not execute, the local file before installing it as root.
python3 - "$network_env" <<'PY'
import ipaddress
from pathlib import Path
import sys
lines = [line.strip() for line in Path(sys.argv[1]).read_text().splitlines()
         if line.strip() and not line.lstrip().startswith('#')]
if len(lines) != 1 or not lines[0].startswith('WINE_API_SOURCE_IP='):
    raise SystemExit('Expected only WINE_API_SOURCE_IP=<LAN IPv4> in .env.network')
ipaddress.IPv4Address(lines[0].split('=', 1)[1])
PY
systemd-analyze verify "$unit_path"
sudo install -m 0600 "$network_env" /etc/wine-api-network.env
sudo install -m 0644 "$unit_path" /etc/systemd/system/wine-api-routing.service
sudo systemctl daemon-reload
sudo systemctl enable wine-api-routing.service
sudo systemctl restart wine-api-routing.service
systemctl is-enabled wine-api-routing.service
systemctl is-active wine-api-routing.service
ip -4 rule show priority 10000
