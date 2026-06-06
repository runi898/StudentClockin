#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
MIGRATIONS_DIR="${REPO_ROOT}/supabase/migrations"
DB_CONTAINER="${SUPABASE_DB_CONTAINER:-supabase-db}"
DB_NAME="${SUPABASE_DB_NAME:-postgres}"
DB_USER="${SUPABASE_DB_USER:-postgres}"

if ! command -v docker >/dev/null 2>&1; then
  echo "docker not found"
  exit 1
fi

if [ ! -d "${MIGRATIONS_DIR}" ]; then
  echo "migrations directory not found: ${MIGRATIONS_DIR}"
  exit 1
fi

echo "Applying migrations from ${MIGRATIONS_DIR} to container ${DB_CONTAINER}..."

for migration in "${MIGRATIONS_DIR}"/*.sql; do
  echo "-> $(basename "${migration}")"
  docker exec -i "${DB_CONTAINER}" psql -v ON_ERROR_STOP=1 -U "${DB_USER}" -d "${DB_NAME}" < "${migration}"
done

echo "All migrations applied."
