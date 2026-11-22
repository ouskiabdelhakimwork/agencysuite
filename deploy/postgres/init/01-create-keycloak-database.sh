#!/bin/bash
# Cree la base et l'utilisateur de Keycloak au premier demarrage de PostgreSQL.
# (La base de l'application, "agencysuite", est creee par l'image via POSTGRES_DB.)
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE USER keycloak WITH PASSWORD '${KEYCLOAK_DB_PASSWORD}';
	CREATE DATABASE keycloak OWNER keycloak;
EOSQL
