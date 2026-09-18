#!/usr/bin/env bash
# Placeholder for the organizer eval script.
# Sends one image to POST /api/eval and prints {"slug":"..."}.
set -euo pipefail

IMAGE="${1:?usage: scripts/eval.example.sh path/to/photo.jpg}"
BASE="${SCAN_API_URL:-http://127.0.0.1:3000}"

curl -sS -X POST -F "image=@${IMAGE}" "${BASE}/api/eval"
echo
