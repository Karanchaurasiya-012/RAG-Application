#!/usr/bin/env bash
# Creates document_db + pgvector on a normal (non-sandbox) Postgres.
# Default: localhost:5432. Override with PGHOST/PGPORT/PGUSER.
set -euo pipefail
export PGHOST="${PGHOST:-127.0.0.1}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
psql -d postgres -v ON_ERROR_STOP=1 <<'SQL'
SELECT 'CREATE DATABASE document_db'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'document_db')\gexec
\c document_db
CREATE EXTENSION IF NOT EXISTS vector;
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
SQL
echo "document_db ready on ${PGHOST}:${PGPORT}"
