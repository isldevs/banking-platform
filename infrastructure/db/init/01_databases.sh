#!/usr/bin/env bash
set -euo pipefail
for service in config auth customer account transfer notification audit; do
  upper="${service^^}"
  pass_var="DB_PASSWORD_${upper}"
  pass="${!pass_var:?missing database secret}"
  role="${service}_user"
  psql --username "$POSTGRES_USER" --dbname postgres -v ON_ERROR_STOP=1 -v role="$role" -v pass="$pass" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'role', :'pass') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'role') \gexec
SQL
  psql --username "$POSTGRES_USER" --dbname postgres -v ON_ERROR_STOP=1 -v role="$role" -v db="$service" <<'SQL'
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'role') WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
SQL
done
