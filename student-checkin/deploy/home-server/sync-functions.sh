#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SOURCE_DIR="${REPO_ROOT}/supabase/functions"
TARGET_DIR="${1:-${SUPABASE_FUNCTIONS_DIR:-}}"

if [ -z "${TARGET_DIR}" ]; then
  echo "Usage: $0 /path/to/supabase/docker/volumes/functions"
  exit 1
fi

if [ ! -d "${SOURCE_DIR}" ]; then
  echo "functions source directory not found: ${SOURCE_DIR}"
  exit 1
fi

if [ "$(basename "${TARGET_DIR}")" = "main" ] && [ -d "$(dirname "${TARGET_DIR}")" ]; then
  echo "Detected legacy target ending with /main, normalizing to $(dirname "${TARGET_DIR}")"
  TARGET_DIR="$(dirname "${TARGET_DIR}")"
fi

mkdir -p "${TARGET_DIR}"

managed_functions=(
  "create-child-account"
  "daily-rollover"
  "manage-child-account"
  "recalculate-reports"
  "send-notifications"
)

for function_name in "${managed_functions[@]}"; do
  source_path="${SOURCE_DIR}/${function_name}"
  target_path="${TARGET_DIR}/${function_name}"

  if [ ! -d "${source_path}" ]; then
    echo "skipping missing source function: ${source_path}"
    continue
  fi

  rm -rf "${target_path}"

  if command -v rsync >/dev/null 2>&1; then
    rsync -av "${source_path}/" "${target_path}/"
  else
    mkdir -p "${target_path}"
    cp -R "${source_path}/." "${target_path}/"
  fi
done

echo "Functions synced to ${TARGET_DIR}"
