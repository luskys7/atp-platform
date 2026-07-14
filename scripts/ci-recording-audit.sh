#!/usr/bin/env bash
# P0 录屏批量 API 审计（本地/CI 等效）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -z "${ATP_API_BASE:-}" || -z "${ATP_TOKEN:-}" ]]; then
  echo "请设置 ATP_API_BASE 与 ATP_TOKEN 环境变量"
  exit 1
fi

export ATP_BATCH_AUDIT=1
export ATP_BATCH_LIMIT="${ATP_BATCH_LIMIT:-10}"
python scripts/verify-recording-p0.py
