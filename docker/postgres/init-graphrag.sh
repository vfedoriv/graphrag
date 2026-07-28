#!/usr/bin/env bash
set -euo pipefail

graphrag_database="${GRAPHRAG_POSTGRES_DATABASE:-graphrag}"
graphrag_user="${GRAPHRAG_POSTGRES_USER:-graphrag}"
graphrag_password="${GRAPHRAG_POSTGRES_PASSWORD:?GRAPHRAG_POSTGRES_PASSWORD must be set}"
graphrag_schema="${GRAPHRAG_POSTGRES_SCHEMA:-app}"
postgres_admin_database="${POSTGRES_DB:-postgres}"
postgres_admin_user="${POSTGRES_USER:-postgres}"

for identifier in "$graphrag_database" "$graphrag_user" "$graphrag_schema"; do
    if [[ ! "$identifier" =~ ^[a-z_][a-z0-9_]*$ ]]; then
        echo "PostgreSQL identifier '$identifier' is invalid" >&2
        exit 1
    fi
done

role_exists="$(
    psql --username "$postgres_admin_user" --dbname "$postgres_admin_database" \
        --tuples-only --no-align \
        --set=graphrag_user="$graphrag_user" <<'SQL'
SELECT 1 FROM pg_roles WHERE rolname = :'graphrag_user';
SQL
)"
if [[ "$role_exists" != "1" ]]; then
    psql --username "$postgres_admin_user" --dbname "$postgres_admin_database" \
        --set=graphrag_user="$graphrag_user" \
        --set=graphrag_password="$graphrag_password" <<'SQL'
SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION',
    :'graphrag_user',
    :'graphrag_password'
)
\gexec
SQL
fi

database_exists="$(
    psql --username "$postgres_admin_user" --dbname "$postgres_admin_database" \
        --tuples-only --no-align \
        --set=graphrag_database="$graphrag_database" <<'SQL'
SELECT 1 FROM pg_database WHERE datname = :'graphrag_database';
SQL
)"
if [[ "$database_exists" != "1" ]]; then
    psql --username "$postgres_admin_user" --dbname "$postgres_admin_database" \
        --set=graphrag_database="$graphrag_database" \
        --set=graphrag_user="$graphrag_user" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'graphrag_database', :'graphrag_user')
\gexec
SQL
fi

psql --username "$postgres_admin_user" --dbname "$graphrag_database" \
    --set=graphrag_schema="$graphrag_schema" \
    --set=graphrag_user="$graphrag_user" <<'SQL'
SELECT format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION %I', :'graphrag_schema', :'graphrag_user')
\gexec
SELECT format('ALTER SCHEMA %I OWNER TO %I', :'graphrag_schema', :'graphrag_user')
\gexec
SELECT format('REVOKE ALL ON SCHEMA public FROM %I', :'graphrag_user')
\gexec
SELECT format('ALTER ROLE %I IN DATABASE %I SET search_path TO %I', :'graphrag_user', current_database(), :'graphrag_schema')
\gexec
SQL

echo "GraphRAG PostgreSQL database '$graphrag_database' and schema '$graphrag_schema' are ready"
